package dollar_risk_box;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The commission per side a box counts. A plain study cannot see the trading account, so "Auto" looks at what the
 * platform itself wrote: the server the platform last connected to (its log says "Connecting to server: Lucid Trading
 * (Frankfurt)"). On Lucid the firm's published table applies; anywhere else, and for a product not in the table, the
 * typed value does.
 */
public final class Costs {

    private Costs() { }

    /**
     * Lucid Trading, commission per side in USD (support.lucidtrading.com "Approved Products and Commissions",
     * 26 Aug 2026). Exchange/NFA fees are not stated there.
     */
    public static final Map<String, Double> LUCID = Map.ofEntries(
            Map.entry("ES", 1.75), Map.entry("MES", 0.50), Map.entry("NQ", 1.75), Map.entry("MNQ", 0.50),
            Map.entry("RTY", 1.75), Map.entry("M2K", 0.50), Map.entry("NKD", 1.75), Map.entry("YM", 1.75), Map.entry("MYM", 0.50),
            Map.entry("6A", 2.40), Map.entry("6B", 2.40), Map.entry("6C", 2.40), Map.entry("6E", 2.40), Map.entry("6J", 2.40),
            Map.entry("6S", 2.40), Map.entry("6N", 2.40),
            Map.entry("CL", 2.00), Map.entry("MCL", 0.50), Map.entry("QM", 2.00), Map.entry("QG", 1.30), Map.entry("NG", 2.00),
            Map.entry("PL", 2.30), Map.entry("HG", 2.30), Map.entry("GC", 2.30), Map.entry("MGC", 0.80), Map.entry("SI", 2.30),
            Map.entry("SIL", 1.60),
            Map.entry("HE", 2.80), Map.entry("LE", 2.80), Map.entry("ZS", 2.80), Map.entry("ZC", 2.80), Map.entry("ZL", 2.80),
            Map.entry("ZM", 2.80), Map.entry("ZW", 2.80));

    private static final Pattern CONTRACT = Pattern.compile("^([A-Z0-9]+?)[FGHJKMNQUVXZ]\\d{1,2}$");

    /** The product root of a contract symbol: "MNQZ6" -> "MNQ", "6EZ6" -> "6E", "MGCZ26" -> "MGC"; anything else as is. */
    public static String root(String symbol) {
        if (symbol == null) return "";
        String s = symbol.toUpperCase(Locale.ROOT);
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        Matcher m = CONTRACT.matcher(s);
        return m.matches() ? m.group(1) : s;
    }

    /** What a box uses and why: the value per side, and a short word for the label ("Lucid", "manual"). */
    public record Commission(double perSide, String source) { }

    /** Where the commission comes from: the platform's connection (Lucid when it is Lucid), always Lucid's table, or the typed value. */
    public enum Mode { AUTO, LUCID, MANUAL;
        public static Mode parse(String s) {
            try { return valueOf(s); } catch (RuntimeException e) { return AUTO; }
        }
    }

    /**
     * @param server    the server the platform last connected to, or null when unknown
     * @param manual    the typed value per side
     */
    public static Commission of(Mode mode, String server, String symbol, double manual) {
        double typed = manual > 0 ? manual : 0;
        if (mode == Mode.MANUAL) return new Commission(typed, "manual");
        if (mode == Mode.LUCID || server != null && server.toLowerCase(Locale.ROOT).contains("lucid")) {
            Double v = LUCID.get(root(symbol));
            if (v != null) return new Commission(v, "Lucid");
            return new Commission(typed, "manual (" + root(symbol) + " not in Lucid's list)");
        }
        return new Commission(typed, "manual");
    }

    private static volatile String cachedServer;
    private static volatile long cachedAt;
    private static final AtomicBoolean READING = new AtomicBoolean();

    /**
     * The platform's last server, read at most once a minute on a background thread (never on the chart's thread);
     * null until the first read is done or when it cannot be found (another OS, another log format): then the typed
     * value applies.
     */
    public static String platformServer() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > 60_000 && READING.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try {
                    cachedServer = serverFromLogs(Path.of(System.getProperty("user.home", "."), "Library", "MotiveWave", "output"));
                } finally {
                    cachedAt = System.currentTimeMillis();
                    READING.set(false);
                }
            }, "DollarRiskBox-server");
            t.setDaemon(true);
            t.start();
        }
        return cachedServer;
    }

    private static final Pattern SERVER = Pattern.compile("Connecting to server: (.+?)(?: username:|$)");

    /** The last server named in a log text, or null. */
    public static String lastServer(String log) {
        if (log == null) return null;
        String found = null;
        Matcher m = SERVER.matcher(log);
        while (m.find()) found = m.group(1).trim();
        return found;
    }

    /**
     * The server the platform last connected to, read from the newest "output (...).txt" in its log folder (the last
     * 256 KB is enough: the connection is written at start-up and again on every reconnect). Null when not found.
     */
    public static String serverFromLogs(Path outputDir) {
        if (outputDir == null || !Files.isDirectory(outputDir)) return null;
        try (Stream<Path> files = Files.list(outputDir)) {
            var newest = files.filter(p -> p.getFileName().toString().startsWith("output") && p.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
            if (newest.isEmpty()) return null;
            try (RandomAccessFile f = new RandomAccessFile(newest.get().toFile(), "r")) {
                long len = f.length(), from = Math.max(0, len - 256 * 1024);
                byte[] b = new byte[(int) (len - from)];
                f.seek(from);
                f.readFully(b);
                String server = lastServer(new String(b, StandardCharsets.UTF_8));
                if (server != null || from == 0) return server;
                // the connection line is near the top of the file: read the start too
                byte[] head = new byte[(int) Math.min(len, 256 * 1024)];
                f.seek(0);
                f.readFully(head);
                return lastServer(new String(head, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
