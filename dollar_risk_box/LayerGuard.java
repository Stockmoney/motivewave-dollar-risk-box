package dollar_risk_box;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps a study's overlay above the user's drawings (SPEC 17).
 *
 * The platform hands a mouse click to the LAST layer of the chart pane that claims the point, and a drawing the user adds
 * is put last: a new line lies over the overlay, and where lines are many the buttons and handles under them cannot be
 * pressed. Once the user sent the drawings back by hand (the chart's "Move Back"); the next new line undid it.
 *
 * Every {@link #PERIOD_MS} a daemon thread reads the pane's layer order (ChartLayers.read - read only). When a drawing lies
 * after this overlay it asks the JavaFX thread to do what the chart's own "Move Front" menu item does for this overlay
 * (ChartLayers.moveToFront), then reads again: the move counts only if no drawing is above afterwards. It never moves
 * while the overlay is being dragged or placed, gives up after repeated moves that did not take, and does nothing when
 * the setting is off or the platform's layers do not look as expected.
 */
public final class LayerGuard {

    public static final long PERIOD_MS = 250;
    private static final int MAX_FAILS = 3;

    /*
     * Which loaded copy of this class is the current one. When a new jar replaces the study, MotiveWave makes new study
     * instances but does not call destroy() on the old ones (seen 9 Nov 2026), so the guard thread of an old instance would
     * run on for ever. A JVM-wide property (keyed by this class's name, so each product has its own) says which copy started
     * a guard last; a guard of an older copy sees that and stops.
     */
    private static final String LOADED_KEY = "com.zentrader.loaded." + LayerGuard.class.getName();
    private static final String LOADED_ID = Integer.toHexString(System.identityHashCode(LayerGuard.class)) + "@" + System.nanoTime();

    /** This copy of the class takes over: guards of older copies stop at their next check. */
    public static void claimCurrent() { System.setProperty(LOADED_KEY, LOADED_ID); }

    /** False once a newer copy of this class (a newer jar) has started a guard. */
    public static boolean isCurrentCopy() {
        String v = System.getProperty(LOADED_KEY);
        return v == null || v.equals(LOADED_ID);
    }

    /** Tests only: the property this class watches. */
    static String loadedKey() { return LOADED_KEY; }

    private final Object study;
    private final Supplier<Object> dataContext;
    private final BooleanSupplier enabled, busy;
    private final Consumer<String> log;
    private volatile Object layer;
    private volatile boolean running, gaveUp;
    private volatile long movedAt;
    private int fails, unreadable;
    private long moves, lastLogged;
    /**
     * Kinds of drawing the platform keeps above every study whatever "Move Front" does (seen 10 and 12 Nov 2026: the
     * Price Guide, class "cx(drawing)"). After {@link #MAX_FAILS} moves that leave the same kinds above, they are learnt
     * here and ignored from then on - instead of switching the whole guard off, which left the overlay unprotected from
     * the ordinary lines too.
     */
    private final Set<String> pinned = ConcurrentHashMap.newKeySet();
    /** The kinds that stayed above in EVERY failed move since the last success (JavaFX thread only); only these are pinned. */
    private Set<String> stuckEveryTime;
    private Thread thread;

    /**
     * @param enabled the setting "keep above my drawings"
     * @param busy    the overlay is being dragged or placed: never moved then
     * @param log     one line per event for the journal
     */
    public LayerGuard(Object study, Supplier<Object> dataContext, BooleanSupplier enabled, BooleanSupplier busy, Consumer<String> log) {
        this.study = study;
        this.dataContext = dataContext;
        this.enabled = enabled;
        this.busy = busy;
        this.log = log;
    }

    public synchronized void start() {
        if (running) return;
        claimCurrent();
        pinned.clear();
        running = true;
        gaveUp = false;
        fails = 0;
        thread = new Thread(this::loop, "overlay-layer-guard");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        layer = null;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(PERIOD_MS);
                if (!isCurrentCopy()) {
                    running = false;
                    log.accept("layer guard: a newer copy of the indicator is loaded - this old guard stops");
                    return;
                }
                if (gaveUp || !enabled.getAsBoolean()) continue;
                check();
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException | LinkageError e) {
                if (++unreadable == 1) log.accept("layer guard error: " + e);
                if (unreadable >= 5) gaveUp = true;
            }
        }
    }

    private void check() {
        Object l = layer;
        if (l == null || ChartLayers.destroyed(l)) {
            l = ChartLayers.layerOf(study, dataContext.get());
            layer = l;
            if (l == null) return;
        }
        ChartLayers.Order o = ChartLayers.read(l);
        if (o == null) {
            if (++unreadable >= 5) {
                gaveUp = true;
                log.accept("layer guard: the chart's layers cannot be read on this platform build - switched off");
            }
            return;
        }
        unreadable = 0;
        if (o.ours() < 0 || movableAbove(o, pinned) == 0 || busy.getAsBoolean()) return;
        long now = System.currentTimeMillis();
        if (now - movedAt < PERIOD_MS) return;
        movedAt = now;
        final Object target = l;
        final int above = movableAbove(o, pinned);
        javafx.application.Platform.runLater(() -> moveNow(target, above));
    }

    /** The drawings above our layer that a move can still do something about (kinds the platform pins on top excluded). */
    public static int movableAbove(ChartLayers.Order o, Set<String> pinned) {
        if (o == null || o.ours() < 0) return 0;
        int n = 0;
        for (int i = o.ours() + 1; i < o.layers().size(); i++) {
            ChartLayers.Layer l = o.layers().get(i);
            if (l.drawing() && !pinned.contains(l.name())) n++;
        }
        return n;
    }

    /** The kinds (class names) of the drawings above our layer that are not pinned yet. */
    public static Set<String> kindsAbove(ChartLayers.Order o, Set<String> pinned) {
        Set<String> out = new TreeSet<>();
        if (o == null || o.ours() < 0) return out;
        for (int i = o.ours() + 1; i < o.layers().size(); i++) {
            ChartLayers.Layer l = o.layers().get(i);
            if (l.drawing() && !pinned.contains(l.name())) out.add(l.name());
        }
        return out;
    }

    /** On the JavaFX thread. */
    private void moveNow(Object target, int above) {
        try {
            if (!running || busy.getAsBoolean() || ChartLayers.destroyed(target)) return;
            ChartLayers.Order before = ChartLayers.read(target);
            if (before == null || before.ours() < 0 || movableAbove(before, pinned) == 0) return;     // it was put right meanwhile
            boolean called = ChartLayers.moveToFront(target);
            ChartLayers.Order after = ChartLayers.read(target);
            boolean ok = called && after != null && movableAbove(after, pinned) == 0;
            if (ok) {
                fails = 0;
                stuckEveryTime = null;
                moves++;
                long now = System.currentTimeMillis();
                if (moves <= 5 || now - lastLogged > 60_000) {
                    lastLogged = now;
                    log.accept("moved to front: " + movableAbove(before, pinned) + " drawing(s) lay over this overlay (move " + moves + ")");
                }
            } else {
                // a kind is learnt only if it stayed above in each of the failed moves (not one that just appeared)
                Set<String> now = called && after != null ? kindsAbove(after, pinned) : Set.<String>of();
                if (stuckEveryTime == null) stuckEveryTime = new TreeSet<>(now); else stuckEveryTime.retainAll(now);
                if (++fails < MAX_FAILS) return;
                Set<String> stuck = new TreeSet<>(stuckEveryTime);
                stuckEveryTime = null;
                if (!stuck.isEmpty()) {
                    // the platform keeps these on top: ignore them, keep guarding against every other drawing
                    pinned.addAll(stuck);
                    fails = 0;
                    log.accept("layer guard: " + stuck + " stay above the overlay whatever is done (the platform keeps them on top) - "
                            + "ignored from now on, the guard keeps working for the other drawings. " + after.describe());
                    return;
                }
                gaveUp = true;
                log.accept("layer guard: " + MAX_FAILS + " moves did not put the overlay above the drawings (" + (called ? "order unchanged" : "call failed")
                        + ") - switched off. " + (after == null ? "" : after.describe()));
            }
        } catch (RuntimeException | LinkageError e) {
            if (++fails >= MAX_FAILS) {
                gaveUp = true;
                log.accept("layer guard: failed " + e + " - switched off");
            }
        }
    }
}
