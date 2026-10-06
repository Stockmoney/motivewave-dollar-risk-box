package dollar_risk_box;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.ColorDescriptor;
import com.motivewave.platform.sdk.common.desc.DiscreteDescriptor;
import com.motivewave.platform.sdk.common.desc.DoubleDescriptor;
import com.motivewave.platform.sdk.common.desc.EnabledDependency;
import com.motivewave.platform.sdk.common.desc.FontDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;
import com.motivewave.platform.sdk.common.menu.MenuDescriptor;
import com.motivewave.platform.sdk.common.menu.MenuItem;
import com.motivewave.platform.sdk.draw.Figure;
import com.motivewave.platform.sdk.draw.ResizePoint;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dollar Risk Box
 * ----------------
 * A copy of MotiveWave's built-in "PL 1 Target" drawing tool (risk/reward box) that shows
 * money in DOLLARS instead of points x quantity, and sizes the quantity from a $ or % risk.
 *
 * Display only: a plain Study, never a Strategy - it has no OrderContext and cannot send orders.
 *
 * The SDK has no API for custom drawing tools (left toolbar), so the study keeps its own
 * on-chart tool panel: press "Long" or "Short", then click the chart - a box is drawn at that
 * price/time. Any number of boxes; each is removed with right click -> Delete. The panel stays
 * and is dragged by its grip.
 */
@StudyHeader(
        namespace = "com.zentrader",
        id = "PL_DOLLAR_TARGET",       // historic id, kept so charts/templates saved earlier still load
        rb = "dollar_risk_box.nls.strings",
        name = "STUDY_NAME",
        desc = "STUDY_DESC",
        menu = "MENU_GENERAL",
        overlay = true,
        studyOverlay = true,
        requiresBarUpdates = true       // live P/L follows the last price
)
public class DollarRiskBox extends Study {

    // ==================== Settings keys ====================
    private static final String RISK_TYPE = "riskType";
    private static final String RISK_AMOUNT = "riskAmount";
    private static final String RISK_PERCENT = "riskPercent";
    private static final String BALANCE = "balance";
    private static final String FIXED_QTY_ENABLED = "fixedQtyEnabled";
    private static final String FIXED_QTY = "fixedQty";
    private static final String SHOW_PANEL = "showPanel";
    private static final String PLACE_MODE = "placeMode";
    private static final String BOX_WIDTH = "boxWidth";
    private static final String ESC_CANCELS = "escCancels";
    private static final String DEL_REMOVES = "delRemoves";
    private static final String PLACE_ONE = "one", PLACE_THREE = "three";
    // Hidden state (no descriptor)
    private static final String BOXES = "boxes";             // "entry,stop,target,start,end;..."
    private static final String PANEL_FX = "panelFx";        // panel position as a fraction of the chart
    private static final String PANEL_FY = "panelFy";
    // Format
    private static final String TARGET_FILL = "targetFill";
    private static final String STOP_FILL = "stopFill";
    private static final String TARGET_LINE = "targetLine";
    private static final String ENTRY_LINE = "entryLine";
    private static final String STOP_LINE = "stopLine";
    private static final String FONT = "font";
    private static final String TARGET_FG = "targetFg";
    private static final String TARGET_BG = "targetBg";
    private static final String ENTRY_FG = "entryFg";
    private static final String ENTRY_BG = "entryBg";
    private static final String STOP_FG = "stopFg";
    private static final String STOP_BG = "stopBg";
    private static final String TEXT_ALIGN = "textAlign";
    private static final String SHOW_LABELS = "showLabels";
    private static final String SHOW_CURRENT_PNL = "showCurrentPnl";
    private static final String SHOW_PERCENT = "showPercent";
    private static final String SHOW_BALANCE = "showBalance";
    private static final String LABEL_OPACITY = "labelOpacity";
    private static final String SHOW_QTY = "showQty";
    private static final String EXTEND_RIGHT = "extendRight";

    private static final String TYPE_FIXED = "fixed";
    private static final String TYPE_PERCENT = "percent";
    private static final String ALIGN_LEFT = "left";
    private static final String ALIGN_MIDDLE = "middle";
    private static final String ALIGN_RIGHT = "right";

    // Defaults mirror the built-in tool's look
    private static final Color DEF_TARGET_FILL = new Color(30, 110, 200, 60);
    private static final Color DEF_STOP_FILL = new Color(200, 40, 40, 60);
    private static final Color DEF_TARGET_COLOR = new Color(30, 144, 255);
    private static final Color DEF_STOP_COLOR = new Color(235, 50, 50);
    private static final Color DEF_LABEL_BG = new Color(25, 25, 25);   // dark plate under white text
    private static final Color DEF_ENTRY_LINE = new Color(255, 255, 255, 120);

    private static final int LABEL_PAD_X = 4;
    private static final int LABEL_PAD_Y = 2;
    private static final int DEF_BOX_BARS = 8;   // width of a new box, in bars on screen

    // Binary class name on purpose: the Enums class in mwave_sdk.jar has no InnerClasses
    // attribute, so javac cannot resolve the nested name Enums.ResizeType (same class at runtime).
    private static final com.motivewave.platform.sdk.common.Enums$ResizeType VERTICAL =
            com.motivewave.platform.sdk.common.Enums$ResizeType.VERTICAL;
    private static final com.motivewave.platform.sdk.common.Enums$ResizeType HORIZONTAL =
            com.motivewave.platform.sdk.common.Enums$ResizeType.HORIZONTAL;
    private static final com.motivewave.platform.sdk.common.Enums$ResizeType ALL =
            com.motivewave.platform.sdk.common.Enums$ResizeType.ALL;

    // ==================== State ====================
    private final List<Box> boxes = new ArrayList<>();
    private String loadedBoxes;             // last BOXES string applied, to skip no-op reloads
    private ResizePoint activeRP;           // the handle being dragged right now
    private boolean interacting;
    private long lastInteraction;           // ms; guards against a missed end-of-resize callback
    private boolean suppressClick;          // the mouse-up that ends a drag also fires onClick
    private java.awt.geom.Point2D lastHover;   // last mouse position over the chart (screen px)
    private volatile boolean studySelected;    // the platform painted this study as selected on its last repaint
    private Box lastTouched;                   // the box whose handle was dragged last (Delete falls back to it)
    // whole-box drag bookkeeping
    private double sEntry, sStop, sTarget;
    private long sStart, sEnd;
    private double sStartPx, sEndPx;
    private long loc0T;
    private double loc0V;
    private boolean bodyModeKnown, bodyUsesHover;
    private Boolean armed;                  // TRUE = the chart clicks that follow draw a long, FALSE = short
    // placing a box by three clicks: 0 = waiting for the entry, 1 = for the stop, 2 = for the target
    private int placeStep;
    private double placeEntry, placeStop;
    private long placeStart;
    private String placeHint;               // why the last click was not accepted

    private ButtonPanel panel;
    private ClickCatcher catcher;
    private PanelGrip grip;
    private Point panelDragPos;
    private DrawContext lastCtx;            // for turning a click point into price/time

    /** One risk/reward box with its own four drag handles. */
    private final class Box {
        double entry, stop, target;
        long start, end;
        final Handle bodyRP = new Handle(this, ALL);      // whole-box drag from any point of the body
        final Handle stopRP = new Handle(this, VERTICAL);
        final Handle targetRP = new Handle(this, VERTICAL);
        final Handle entryLeftRP = new Handle(this, ALL);
        final Handle entryRightRP = new Handle(this, HORIZONTAL);
        final BoxFigure fig = new BoxFigure(this);

        // bodyRP first: later figures win hit-tests, so the edge handles stay on top of it
        Handle[] handles() { return new Handle[]{bodyRP, stopRP, targetRP, entryLeftRP, entryRightRP}; }
        boolean valid() { return entry > 0 && stop > 0 && target > 0 && start > 0 && end > start; }
    }

    // ==================== Settings UI ====================
    @Override
    public void initialize(Defaults defaults) {
        var sd = createSD();

        var general = sd.addTab(get("TAB_GENERAL"));
        var risk = general.addGroup("");
        var typeOptions = new ArrayList<NVP>();
        typeOptions.add(new NVP(get("LBL_TYPE_FIXED"), TYPE_FIXED));
        typeOptions.add(new NVP(get("LBL_TYPE_PERCENT"), TYPE_PERCENT));
        risk.addRow(new DiscreteDescriptor(RISK_TYPE, get("LBL_RISK_TYPE"), TYPE_FIXED, typeOptions));
        risk.addRow(new DoubleDescriptor(RISK_AMOUNT, get("LBL_RISK_AMOUNT"), 200.0, 0.01, 10000000, 1));
        risk.addRow(new DoubleDescriptor(RISK_PERCENT, get("LBL_RISK_PERCENT"), 0.4, 0.01, 100, 0.05));
        risk.addRow(new DoubleDescriptor(BALANCE, get("LBL_BALANCE"), 50000.0, 0, 1000000000, 100));
        risk.addRow(new IntegerDescriptor(FIXED_QTY, get("LBL_FIXED_QTY"), 1, 1, 100000, 1),
                new BooleanDescriptor(FIXED_QTY_ENABLED, get("LBL_ENABLED"), false));
        var pnl = general.addGroup(get("LBL_PANEL"));
        pnl.addRow(new BooleanDescriptor(SHOW_PANEL, get("LBL_SHOW_PANEL"), true));
        var placeOptions = new ArrayList<NVP>();
        placeOptions.add(new NVP(get("LBL_PLACE_THREE"), PLACE_THREE));
        placeOptions.add(new NVP(get("LBL_PLACE_ONE"), PLACE_ONE));
        pnl.addRow(new DiscreteDescriptor(PLACE_MODE, get("LBL_PLACE_MODE"), PLACE_THREE, placeOptions));
        pnl.addRow(new IntegerDescriptor(BOX_WIDTH, get("LBL_BOX_WIDTH"), DEF_BOX_BARS, 2, 500, 1));
        pnl.addRow(new BooleanDescriptor(ESC_CANCELS, get("LBL_ESC_CANCELS"), true));
        pnl.addRow(new BooleanDescriptor(DEL_REMOVES, get("LBL_DEL_REMOVES"), true));

        sd.addDependency(new EnabledDependency(FIXED_QTY_ENABLED, FIXED_QTY));

        var format = sd.addTab(get("TAB_FORMAT"));
        var f = format.addGroup("");
        f.addRow(new ColorDescriptor(TARGET_FILL, get("LBL_TARGET_FILL"), DEF_TARGET_FILL));
        f.addRow(new ColorDescriptor(STOP_FILL, get("LBL_STOP_FILL"), DEF_STOP_FILL));
        f.addRow(new PathDescriptor(TARGET_LINE, get("LBL_TARGET_LINE"), DEF_TARGET_COLOR, 1.0f, null, true, true, true));
        f.addRow(new PathDescriptor(ENTRY_LINE, get("LBL_ENTRY_LINE"), DEF_ENTRY_LINE, 1.0f, null, true, true, true));
        f.addRow(new PathDescriptor(STOP_LINE, get("LBL_STOP_LINE"), DEF_STOP_COLOR, 1.0f, null, true, true, true));
        f.addRow(new FontDescriptor(FONT, get("LBL_FONT"), new Font("SansSerif", Font.PLAIN, 12)));
        f.addRow(new ColorDescriptor(TARGET_FG, get("LBL_TARGET_TEXT"), Color.WHITE),
                new ColorDescriptor(TARGET_BG, get("LBL_BG"), DEF_LABEL_BG));
        f.addRow(new ColorDescriptor(ENTRY_FG, get("LBL_ENTRY_TEXT"), Color.WHITE),
                new ColorDescriptor(ENTRY_BG, get("LBL_BG"), Color.BLACK));
        f.addRow(new ColorDescriptor(STOP_FG, get("LBL_STOP_TEXT"), Color.WHITE),
                new ColorDescriptor(STOP_BG, get("LBL_BG"), DEF_LABEL_BG));
        var alignOptions = new ArrayList<NVP>();
        alignOptions.add(new NVP(get("LBL_ALIGN_LEFT"), ALIGN_LEFT));
        alignOptions.add(new NVP(get("LBL_ALIGN_MIDDLE"), ALIGN_MIDDLE));
        alignOptions.add(new NVP(get("LBL_ALIGN_RIGHT"), ALIGN_RIGHT));
        f.addRow(new DiscreteDescriptor(TEXT_ALIGN, get("LBL_TEXT_ALIGN"), ALIGN_MIDDLE, alignOptions));
        f.addRow(new IntegerDescriptor(LABEL_OPACITY, get("LBL_LABEL_OPACITY"), 70, 0, 100, 5));
        var show = format.addGroup("");
        show.addRow(new BooleanDescriptor(SHOW_LABELS, get("LBL_SHOW_LABELS"), true),
                new BooleanDescriptor(SHOW_BALANCE, get("LBL_SHOW_BALANCE"), false));
        show.addRow(new BooleanDescriptor(SHOW_CURRENT_PNL, get("LBL_SHOW_CURRENT_PNL"), true),
                new BooleanDescriptor(SHOW_PERCENT, get("LBL_SHOW_PERCENT"), true));
        show.addRow(new BooleanDescriptor(SHOW_QTY, get("LBL_SHOW_QTY"), true),
                new BooleanDescriptor(EXTEND_RIGHT, get("LBL_EXTEND_RIGHT"), false));

        createRD();
        sd.addQuickSettings(RISK_TYPE, RISK_AMOUNT, RISK_PERCENT, BALANCE);
    }

    // ==================== Lifecycle ====================
    @Override
    public void clearState() {
        // Called by the platform as routine housekeeping (also after every settings write):
        // never drop the boxes here - they live in the settings. Figures are re-added below.
        super.clearState();
        clearFigures();
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        if (interacting && System.currentTimeMillis() - lastInteraction > 2000) {
            interacting = false;
            activeRP = null;
        }
        if (!interacting) loadFromSettings();
        syncFigures();
        notifyRedraw();
    }

    @Override
    public void onBarUpdate(DataContext ctx) {
        syncFigures();   // the platform may clear figures; put everything back
        notifyRedraw();  // live P/L on the entry labels
    }

    @Override
    public void onSettingsUpdated(DataContext ctx) {
        super.onSettingsUpdated(ctx);
        if (!interacting) loadFromSettings();
        syncFigures();
        notifyRedraw();
    }

    // ==================== Persistence ====================
    private void loadFromSettings() {
        String str = getSettings().getString(BOXES, "");
        if (str == null) str = "";
        if (str.equals(loadedBoxes)) return;
        loadedBoxes = str;
        clearFigures();
        boxes.clear();
        for (String part : str.split(";")) {
            String[] v = part.split(",");
            if (v.length != 5) continue;
            try {
                Box b = new Box();
                b.entry = Double.parseDouble(v[0]);
                b.stop = Double.parseDouble(v[1]);
                b.target = Double.parseDouble(v[2]);
                b.start = Long.parseLong(v[3]);
                b.end = Long.parseLong(v[4]);
                if (b.valid()) boxes.add(b);
            } catch (NumberFormatException ignored) { }
        }
    }

    private void saveBoxes() {
        StringBuilder sb = new StringBuilder();
        for (Box b : boxes) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.entry).append(',').append(b.stop).append(',').append(b.target)
              .append(',').append(b.start).append(',').append(b.end);
        }
        loadedBoxes = sb.toString();     // set first: the write below triggers a reload
        getSettings().setString(BOXES, loadedBoxes);
    }

    /** Make sure every figure we own is on the chart (the platform clears them freely). */
    private void syncFigures() {
        if (panel == null) {
            catcher = new ClickCatcher();
            panel = new ButtonPanel();
            grip = new PanelGrip();
        }
        List<?> figs = getFigures();
        if (!figs.contains(catcher)) addFigure(catcher);
        for (Box b : boxes) {
            if (!figs.contains(b.fig)) addFigure(b.fig);
            for (Handle h : b.handles()) if (!figs.contains(h)) addFigure(h);
        }
        if (!figs.contains(panel)) addFigure(panel);
        if (!figs.contains(grip)) addFigure(grip);
    }

    private void removeBox(Box b) {
        boxes.remove(b);
        removeFigure(b.fig);
        for (Handle h : b.handles()) removeFigure(h);
        saveBoxes();
        notifyRedraw();
    }

    // ==================== Placing a box ====================
    /** Draws a box at screen point (x,y): stop below for a long, above for a short, target at 2R. */
    private void placeBox(boolean isLong, double x, double y) {
        DrawContext ctx = lastCtx;
        DataContext dc = getDataContext();
        if (ctx == null || dc == null) return;
        var series = dc.getDataSeries();
        if (series == null || series.size() < 2) return;
        Instrument instr = dc.getInstrument();
        int last = series.size() - 1;

        double avgRange = 0;
        int n = Math.min(20, series.size());
        for (int i = last - n + 1; i <= last; i++) avgRange += series.getHigh(i) - series.getLow(i);
        avgRange /= n;
        double dist = Math.max(avgRange, instr.getTickSize() * 4);

        Box b = new Box();
        b.entry = instr.round(ctx.translate2Value(y));
        b.stop = instr.round(isLong ? b.entry - dist : b.entry + dist);
        b.target = instr.round(isLong ? b.entry + dist * 2 : b.entry - dist * 2);
        b.start = ctx.translate2Time(x);
        b.end = boxEnd(b.start);
        if (!b.valid()) return;
        boxes.add(b);
        saveBoxes();
        syncFigures();
        notifyRedraw();
    }

    /**
     * End time of a new box that starts at {@code start}: the width on SCREEN of that many bars.
     * Measured in pixels, not as "start + N bar durations": the time axis is not linear (session
     * gaps, the compressed empty area right of the last bar), so a time span can come out several
     * times wider than intended.
     */
    private long boxEnd(long start) {
        int bars = Math.max(1, getSettings().getInteger(BOX_WIDTH, DEF_BOX_BARS));
        DrawContext ctx = lastCtx;
        DataContext dc = getDataContext();
        var series = dc == null ? null : dc.getDataSeries();
        if (series == null || series.size() < 2) return start + 60_000L * bars;
        int last = series.size() - 1;
        if (ctx != null) {
            double perBar = ctx.translateTimeD(series.getStartTime(last)) - ctx.translateTimeD(series.getStartTime(last - 1));
            if (perBar >= 1) {
                long end = ctx.translate2Time(ctx.translateTimeD(start) + perBar * bars);
                if (end > start) return end;
            }
        }
        return start + Math.max(1, series.getStartTime(last) - series.getStartTime(last - 1)) * bars;
    }

    private void addBox(double entry, double stop, double target, long start) {
        DataContext dc = getDataContext();
        if (dc == null) return;
        Instrument instr = dc.getInstrument();
        Box b = new Box();
        b.entry = instr.round(entry);
        b.stop = instr.round(stop);
        b.target = instr.round(target);
        b.start = start;
        b.end = boxEnd(start);
        if (!b.valid()) return;
        boxes.add(b);
        saveBoxes();
        syncFigures();
        notifyRedraw();
    }

    private void stopPlacing() {
        PLACING.remove(this);
        armed = null;
        placeStep = 0;
        placeHint = null;
    }

    /**
     * A chart click while Long/Short is armed. One-click mode draws the box at once; three-click mode
     * takes the entry, then the stop, then the target. The side is the button's: a level clicked on
     * the wrong side of the entry is refused with a hint.
     */
    private void placeClick(Point p) {
        DrawContext ctx = lastCtx;
        DataContext dc = getDataContext();
        if (ctx == null || dc == null) {
            stopPlacing();
            return;
        }
        boolean isLong = armed;
        if (PLACE_ONE.equals(getSettings().getString(PLACE_MODE, PLACE_THREE))) {
            stopPlacing();
            placeBox(isLong, p.x, p.y);
            return;
        }
        Instrument instr = dc.getInstrument();
        double tick = instr.getTickSize(), eps = tick / 2;
        double price = instr.round(ctx.translate2Value(p.y));
        placeHint = null;
        if (placeStep == 0) {
            placeEntry = price;
            placeStart = ctx.translate2Time(p.x);
            placeStep = 1;
        } else if (placeStep == 1) {
            if (isLong ? price > placeEntry - tick + eps : price < placeEntry + tick - eps) {
                placeHint = get(isLong ? "HINT_STOP_BELOW" : "HINT_STOP_ABOVE");
            } else {
                placeStop = price;
                placeStep = 2;
            }
        } else {
            if (isLong ? price < placeEntry + tick - eps : price > placeEntry - tick + eps) {
                placeHint = get(isLong ? "HINT_TARGET_ABOVE" : "HINT_TARGET_BELOW");
            } else {
                double entry = placeEntry, stop = placeStop;
                long start = placeStart;
                stopPlacing();
                addBox(entry, stop, price, start);
                return;
            }
        }
        notifyRedraw();
    }

    /** What the next chart click will do, shown under the panel while a button is armed. */
    private String placePrompt() {
        if (placeHint != null) return placeHint;
        String what = PLACE_ONE.equals(getSettings().getString(PLACE_MODE, PLACE_THREE)) ? get("LBL_CLICK_CHART")
                : get(placeStep == 0 ? "HINT_ENTRY" : placeStep == 1 ? "HINT_STOP" : "HINT_TARGET");
        return getSettings().getBoolean(ESC_CANCELS, true) ? what + " (" + get("HINT_ESC") + ")" : what;
    }

    // ==================== Escape and Delete ====================
    // The SDK gives a study no keyboard events, but the platform is a JavaFX application: a key filter
    // on its windows sees the keys.
    //  - Escape acts only while a box is being placed here (a Long/Short button is lit); the key is not
    //    consumed, so the platform still handles it as it always did.
    //  - Delete / Backspace remove the selected box and are consumed, so the platform does not delete the
    //    whole indicator. They act only while this study is painted as selected AND a box is under the mouse
    //    (or was dragged last); otherwise the key goes to the platform untouched, and so does every key typed
    //    into a text field.
    private static final java.util.Set<DollarRiskBox> PLACING =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
    private static final java.util.Set<DollarRiskBox> INSTANCES =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    {
        INSTANCES.add(this);
    }

    private void trackPlacing() {
        PLACING.add(this);
        KeyFilter.install();
    }

    /** Escape (platform UI thread): drop the box being placed - nothing is left on the chart. */
    private void escapePressed() {
        if (armed == null || !getSettings().getBoolean(ESC_CANCELS, true)) return;
        stopPlacing();
        notifyRedraw();
    }

    /** The box a Delete press would remove, or null when this study should leave the key alone. */
    private Box deleteTarget() {
        if (!studySelected || boxes.isEmpty() || !getSettings().getBoolean(DEL_REMOVES, true)) return null;
        java.awt.geom.Point2D h = lastHover;
        if (h != null) {
            for (int i = boxes.size() - 1; i >= 0; i--) {
                if (boxes.get(i).fig.bodyContains(h.getX(), h.getY())) return boxes.get(i);
            }
        }
        return lastTouched != null && boxes.contains(lastTouched) ? lastTouched : null;
    }

    /**
     * The key filter: a JavaFX filter on the platform's windows (the platform's UI is pure JavaFX, nothing
     * else sees the keys). Kept in its own class so that a platform without JavaFX loses only these two
     * features, not the study.
     */
    private static final class KeyFilter {
        private static final String PROPERTY = "DollarRiskBox.escapeFilter";
        private static volatile long lastInstall;
        private static final javafx.event.EventHandler<javafx.scene.input.KeyEvent> FILTER = e -> {
            javafx.scene.input.KeyCode code = e.getCode();
            if (code == javafx.scene.input.KeyCode.ESCAPE) {
                DollarRiskBox[] studies;
                synchronized (PLACING) { studies = PLACING.toArray(new DollarRiskBox[0]); }
                if (studies.length == 0) return;
                // on the platform's UI thread; the key itself is not consumed
                javafx.application.Platform.runLater(() -> { for (DollarRiskBox st : studies) st.escapePressed(); });
                return;
            }
            if (code != javafx.scene.input.KeyCode.DELETE && code != javafx.scene.input.KeyCode.BACK_SPACE) return;
            if (e.isShortcutDown() || e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
            if (e.getTarget() instanceof javafx.scene.control.TextInputControl) return;      // typing text, not deleting a box
            DollarRiskBox[] studies;
            synchronized (INSTANCES) { studies = INSTANCES.toArray(new DollarRiskBox[0]); }
            for (DollarRiskBox st : studies) {
                DollarRiskBox.Box b = st.deleteTarget();
                if (b == null) continue;
                e.consume();                                   // the platform must not delete the whole indicator
                javafx.application.Platform.runLater(() -> st.removeBox(b));
                return;
            }
        };

        /** Idempotent; also replaces the filter of an earlier version of this study that was reloaded
         *  without restarting the platform. Put on the window itself: the first stop of every key event. */
        @SuppressWarnings("unchecked")
        static synchronized void install() {
            lastInstall = System.currentTimeMillis();
            try {
                for (javafx.stage.Window w : new ArrayList<>(javafx.stage.Window.getWindows())) {
                    Object old = w.getProperties().put(PROPERTY, FILTER);
                    if (old == FILTER) continue;
                    if (old instanceof javafx.event.EventHandler<?> h) {
                        w.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,
                                (javafx.event.EventHandler<javafx.scene.input.KeyEvent>) h);
                    }
                    w.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, FILTER);
                }
            } catch (Throwable ignored) { }     // no Escape / Delete keys, everything else works
        }

        /** Mouse moves are frequent and windows rarely appear: look again at most every few seconds. */
        static void installOccasionally() {
            if (System.currentTimeMillis() - lastInstall > 5000) install();
        }
    }

    // ==================== Interaction ====================
    @Override
    public boolean onClick(Point p, int flags) {
        if (suppressClick) {           // mouse-up of a drag, not a real click
            suppressClick = false;
            return true;
        }
        for (Box b : new ArrayList<>(boxes)) {
            if (b.fig.deleteRect != null && b.fig.deleteRect.contains(p)) {
                removeBox(b);
                return true;
            }
        }
        if (panel != null && panel.gripRect != null && panel.gripRect.contains(p)) return true;  // grip: drag only
        if (panel != null && panel.longRect != null && panel.longRect.contains(p)) {
            boolean off = Boolean.TRUE.equals(armed);      // pressing the lit button again cancels
            stopPlacing();
            if (!off) { armed = Boolean.TRUE; trackPlacing(); }
            notifyRedraw();
            return true;
        }
        if (panel != null && panel.shortRect != null && panel.shortRect.contains(p)) {
            boolean off = Boolean.FALSE.equals(armed);
            stopPlacing();
            if (!off) { armed = Boolean.FALSE; trackPlacing(); }
            notifyRedraw();
            return true;
        }
        if (armed != null) {
            placeClick(p);
            return true;
        }
        return false;
    }

    /** Puts the panel's top-left at the clicked point (stored as a fraction of the chart size). */
    private void movePanelTo(Point p) {
        if (lastCtx == null) return;
        Rectangle bd = lastCtx.getBounds();
        double fx = (p.x - bd.x) / (double) Math.max(1, bd.width);
        double fy = (p.y - bd.y) / (double) Math.max(1, bd.height);
        getSettings().setDouble(PANEL_FX, Math.max(0, Math.min(1, fx)));
        getSettings().setDouble(PANEL_FY, Math.max(0, Math.min(1, fy)));
        notifyRedraw();
    }

    @Override
    public void onHover(java.awt.geom.Point2D loc, int flags, DrawContext ctx) {
        lastHover = loc;
        lastCtx = ctx;
        KeyFilter.installOccasionally();
        if (armed != null && placeStep > 0) notifyRedraw();    // the preview follows the mouse
    }

    @Override
    public void onBeginResize(ResizePoint rp, DrawContext ctx) {
        if (rp instanceof Handle th) lastTouched = th.owner;
        if (rp instanceof Handle h && h == h.owner.bodyRP) {
            Box b = h.owner;
            sEntry = b.entry; sStop = b.stop; sTarget = b.target; sStart = b.start; sEnd = b.end;
            sStartPx = ctx.translateTimeD(sStart);
            sEndPx = ctx.translateTimeD(sEnd);
            loc0T = rp.getTime();
            loc0V = rp.getValue();
            bodyModeKnown = false;
        }
        activeRP = rp;
        interacting = true;
        lastInteraction = System.currentTimeMillis();
    }

    @Override
    public void onResize(ResizePoint rp, DrawContext ctx) {
        if (rp == null) return;
        activeRP = rp;
        interacting = true;
        lastInteraction = System.currentTimeMillis();

        if (rp == grip) {
            // Absolute handle (time/price) -> back to screen pixels for the screen-fixed panel.
            java.awt.geom.Point2D pt = ctx.translate(rp.getTime(), rp.getValue());
            panelDragPos = new Point((int) pt.getX() - ButtonPanel.GW / 2, (int) pt.getY() - ButtonPanel.BH / 2);
            notifyRedraw();
            return;
        }
        if (!(rp instanceof Handle h)) return;
        Box b = h.owner;
        Instrument instr = ctx.getDataContext().getInstrument();
        double v = instr.round(rp.getValue());
        long t = rp.getTime();
        if (h == b.bodyRP) {
            // The handle may be reported either at the mouse position or shifted with the mouse;
            // decide once, on the first event, by which reference it is closer to.
            java.awt.geom.Point2D now = ctx.translate(t, rp.getValue());
            if (!bodyModeKnown) {
                double dHover = lastHover == null ? Double.MAX_VALUE : now.distance(lastHover);
                double dLoc = now.distance(ctx.translate(loc0T, loc0V));
                bodyUsesHover = dHover < dLoc;
                bodyModeKnown = true;
            }
            // Horizontal in PIXELS: the time axis is not linear (session gaps), so a time delta
            // measured at the mouse is wrong for the box edges elsewhere on the axis.
            double baseX = bodyUsesHover ? lastHover.getX() : ctx.translate(loc0T, loc0V).getX();
            double baseV = bodyUsesHover ? ctx.translate2Value(lastHover.getY()) : loc0V;
            double dx = now.getX() - baseX;
            double dv = rp.getValue() - baseV;
            b.entry = instr.round(sEntry + dv);
            b.stop = instr.round(sStop + dv);
            b.target = instr.round(sTarget + dv);
            b.start = ctx.translate2Time(sStartPx + dx);
            b.end = ctx.translate2Time(sEndPx + dx);
            notifyRedraw();
            return;
        }
        if (h == b.stopRP) {
            b.stop = v;
        } else if (h == b.targetRP) {
            b.target = v;
        } else if (h == b.entryLeftRP) {
            // The entry handle moves the whole box (price and time), keeping stop/target distances.
            double dv = v - b.entry;
            b.stop = instr.round(b.stop + dv);
            b.target = instr.round(b.target + dv);
            b.entry = v;
            if (t > 0) {
                long dt = t - b.start;
                b.start += dt;
                b.end += dt;
            }
        } else if (h == b.entryRightRP) {
            if (t > b.start) b.end = t;
        }
        // NOT saved here: writing settings makes the platform call clearState(), which removes
        // the handles mid-drag and aborts the drag. Saved once in onEndResize().
        notifyRedraw();
    }

    @Override
    public void onEndResize(ResizePoint rp, DrawContext ctx) {
        if (rp == grip) {
            if (panelDragPos != null) movePanelTo(new Point(panelDragPos.x, panelDragPos.y));
            panelDragPos = null;
        } else {
            saveBoxes();
        }
        interacting = false;
        activeRP = null;
        suppressClick = true;
        syncFigures();
        notifyRedraw();
    }

    @Override
    public MenuDescriptor onMenu(String plotName, Point loc, DrawContext ctx) {
        var items = new ArrayList<MenuItem>();
        Box hit = null;
        for (int i = boxes.size() - 1; i >= 0; i--) {
            if (boxes.get(i).fig.bodyContains(loc.x, loc.y)) { hit = boxes.get(i); break; }
        }
        if (hit != null) {
            final Box b = hit;
            items.add(new MenuItem(get("LBL_DELETE_BOX"), () -> removeBox(b)));
            items.add(new MenuItem(get("LBL_FLIP"), () -> {
                b.stop = b.entry + (b.entry - b.stop);
                b.target = b.entry - (b.target - b.entry);
                saveBoxes();
                notifyRedraw();
            }));
        }
        if (!boxes.isEmpty()) {
            items.add(new MenuItem(get("LBL_DELETE_ALL"), () -> {
                for (Box b : new ArrayList<>(boxes)) {
                    removeFigure(b.fig);
                    for (Handle h : b.handles()) removeFigure(h);
                }
                boxes.clear();
                saveBoxes();
                notifyRedraw();
            }));
        }
        items.add(new MenuItem(get("LBL_RESET_PANEL"), () -> {
            getSettings().setDouble(PANEL_FX, -1.0);
            getSettings().setDouble(PANEL_FY, -1.0);
            notifyRedraw();
        }));
        // On a box: only our items - the platform's "Delete" would remove the whole study
        // (panel and every box). Elsewhere keep the platform's items (Properties etc.).
        return new MenuDescriptor(items, hit == null);
    }

    // ==================== Math ====================
    private record Calc(double rr, int qty, double loss, double profit, double balance, double currentPnl,
                        boolean qtyFromRisk, double riskPerContract) {}

    private Calc calc(DataContext ctx, Box b) {
        return calc(ctx, b.entry, b.stop, b.target);
    }

    private Calc calc(DataContext ctx, double entry, double stop, double target) {
        Instrument instr = ctx.getInstrument();
        Settings s = getSettings();
        double ps = instr.getPointSize() > 0 ? instr.getPointSize() : 1;
        double pv = instr.getPointValue();

        boolean isLong = stop < entry;
        double stopDist = Math.abs(entry - stop);
        double targetDist = isLong ? target - entry : entry - target;   // negative if on the wrong side
        double riskPerContract = stopDist / ps * pv;

        double balance = s.getDouble(BALANCE, 50000);
        boolean percent = TYPE_PERCENT.equals(s.getString(RISK_TYPE, TYPE_FIXED));
        double budget = percent ? balance * s.getDouble(RISK_PERCENT, 0.4) / 100.0 : s.getDouble(RISK_AMOUNT, 200);

        boolean fixed = s.getBoolean(FIXED_QTY_ENABLED, false);
        int qty = fixed ? s.getInteger(FIXED_QTY, 1)
                : (riskPerContract > 0 ? (int) Math.floor(budget / riskPerContract + 1e-9) : 0);

        double loss = qty * riskPerContract;
        double profit = qty * targetDist / ps * pv;
        var series = ctx.getDataSeries();
        double last = series.size() > 0 ? series.getClose(series.size() - 1) : entry;
        double currentPnl = qty * (isLong ? last - entry : entry - last) / ps * pv;
        double rr = stopDist > 0 ? targetDist / stopDist : 0;
        return new Calc(rr, qty, loss, profit, balance, currentPnl, !fixed, riskPerContract);
    }

    private static String money(double v) {
        String s = String.format(Locale.US, "%,.2f", Math.abs(v));
        return (v < -0.0049 ? "-$" : "$") + s;
    }

    private static String num(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    // ==================== Figures ====================
    /** Platform drag handle of one box. */
    private class Handle extends ResizePoint {
        final Box owner;

        Handle(Box owner, com.motivewave.platform.sdk.common.Enums$ResizeType type) {
            super(type, true);
            this.owner = owner;
            setFillColor(DEF_TARGET_COLOR);
            setOutlineColor(Color.WHITE);
        }

        @Override
        public void layout(DrawContext ctx) {
            // Every handle follows its box live; only the one under the mouse is left to the platform.
            if (this != activeRP) {
                if (this == owner.bodyRP) setLocation(owner.start + (owner.end - owner.start) / 2, owner.entry);
                else if (this == owner.stopRP) setLocation(owner.start, owner.stop);
                else if (this == owner.targetRP) setLocation(owner.start, owner.target);
                else if (this == owner.entryLeftRP) setLocation(owner.start, owner.entry);
                else if (this == owner.entryRightRP) setLocation(owner.end, owner.entry);
            }
            super.layout(ctx);
        }

        /** The platform still draws/hit-tests study handles only while the study is selected;
         *  this keeps them alive for every valid box. */
        @Override
        public boolean isVisible(DrawContext ctx) {
            return owner.valid();
        }

        /** The body handle is the whole box: hit anywhere on the fill/labels (not on the delete
         *  button); while it is being dragged it must claim every point or the platform drops it. */
        @Override
        public boolean contains(double x, double y, DrawContext ctx) {
            if (this != owner.bodyRP) return super.contains(x, y, ctx);
            if (activeRP == this) return true;
            BoxFigure f = owner.fig;
            if (!f.bodyContains(x, y) || (f.deleteRect != null && f.deleteRect.contains(x, y))) return false;
            // leave the neighbourhood of the four edge handles to those handles
            for (Handle h : owner.handles()) {
                if (h == this) continue;
                java.awt.geom.Point2D hp = ctx.translate(h.getTime(), h.getValue());
                if (Math.abs(x - hp.getX()) <= 10 && Math.abs(y - hp.getY()) <= 10) return false;
            }
            return true;
        }

        @Override
        public void draw(Graphics2D gc, DrawContext ctx) {
            if (this == owner.bodyRP) return;     // invisible
            java.awt.geom.Point2D pt = ctx.translate(getTime(), getValue());
            int r = 4;
            int x = (int) Math.round(pt.getX()), y = (int) Math.round(pt.getY());
            gc.setColor(DEF_TARGET_COLOR);
            gc.fillOval(x - r, y - r, r * 2, r * 2);
            gc.setColor(Color.WHITE);
            gc.setStroke(new BasicStroke(1f));
            gc.drawOval(x - r, y - r, r * 2, r * 2);
        }
    }

    /** Invisible, covers the chart only while Long/Short is armed, so the next click comes to us. */
    private class ClickCatcher extends Figure {
        @Override
        public void layout(DrawContext ctx) {
            lastCtx = ctx;
            setBounds(ctx.getBounds());
        }

        /** While a box is being placed click by click: the levels chosen so far and, under the mouse,
         *  what the next click would give (quantity and money at the stop, profit and R/R at the target). */
        @Override
        public void draw(Graphics2D gc, DrawContext ctx) {
            Boolean side = armed;
            java.awt.geom.Point2D hover = lastHover;
            if (side == null || placeStep == 0 || hover == null) return;
            DataContext dc = ctx.getDataContext();
            Instrument instr = dc.getInstrument();
            Settings s = getSettings();
            int xa = ctx.translateTime(placeStart), xb = Math.max(xa + 40, ctx.translateTime(boxEnd(placeStart)));
            int yE = ctx.translateValue(placeEntry);
            double price = instr.round(ctx.translate2Value(hover.getY()));
            int yH = ctx.translateValue(price);
            double stop = placeStep == 1 ? price : placeStop;
            int yS = ctx.translateValue(stop);

            gc.setColor(s.getColor(STOP_FILL, DEF_STOP_FILL));
            gc.fillRect(xa, Math.min(yE, yS), xb - xa, Math.abs(yS - yE));
            if (placeStep == 2) {
                gc.setColor(s.getColor(TARGET_FILL, DEF_TARGET_FILL));
                gc.fillRect(xa, Math.min(yE, yH), xb - xa, Math.abs(yH - yE));
            }
            gc.setColor(Color.WHITE);
            gc.setStroke(new BasicStroke(1f));
            gc.drawLine(xa, yE, xb, yE);

            Calc c = calc(dc, placeEntry, stop, placeStep == 2 ? price : placeEntry);
            String text = placeStep == 1
                    ? "S:" + instr.format(price) + " Q:" + c.qty() + " L:" + money(-c.loss())
                    : "T:" + instr.format(price) + " P:" + money(c.profit()) + " R/R:" + String.format(Locale.US, "%.2f", c.rr());
            FontInfo fi = s.getFont(FONT);
            Font font = fi != null ? fi.getFont() : new Font("SansSerif", Font.PLAIN, 12);
            gc.setFont(font);
            gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = gc.getFontMetrics();
            int tx = xa + 4, ty = yH < yE ? yH - 5 : yH + fm.getAscent() + 3;
            gc.setColor(new Color(25, 25, 25, 200));
            gc.fillRoundRect(tx - 3, ty - fm.getAscent() - 1, fm.stringWidth(text) + 6, fm.getHeight() + 2, 3, 3);
            gc.setColor(Color.WHITE);
            gc.drawString(text, tx, ty);
        }

        @Override
        public boolean contains(double x, double y, DrawContext ctx) {
            return armed != null;
        }

        @Override
        public boolean isVisible(DrawContext ctx) {
            return true;
        }
    }

    /** Tool panel: [grip] [Long] [Short]. Display only - nothing here sends orders. */
    private class ButtonPanel extends Figure {
        static final int GW = 14, BW = 72, BH = 44, GAP = 4, MARGIN = 10;
        Rectangle gripRect, longRect, shortRect;
        int px, py;
        private boolean visible;
        private Font font, hintFont;

        @Override
        public void layout(DrawContext ctx) {
            lastCtx = ctx;
            Settings s = getSettings();
            visible = s.getBoolean(SHOW_PANEL, true);
            if (!visible) {
                gripRect = longRect = shortRect = null;
                return;
            }
            Rectangle b = ctx.getBounds();
            int w = GW + BW * 2 + GAP * 2;
            double fx = s.getDouble(PANEL_FX, -1);
            double fy = s.getDouble(PANEL_FY, -1);
            if (panelDragPos != null) {        // live while the grip is dragged
                px = panelDragPos.x;
                py = panelDragPos.y;
            } else if (fx < 0 || fy < 0) {     // default: top-right
                px = b.x + b.width - w - MARGIN;
                py = b.y + MARGIN;
            } else {
                px = b.x + (int) Math.round(fx * b.width);
                py = b.y + (int) Math.round(fy * b.height);
            }
            px = Math.max(b.x, Math.min(px, b.x + b.width - w));
            py = Math.max(b.y, Math.min(py, b.y + b.height - BH));
            gripRect = new Rectangle(px, py, GW, BH);
            longRect = new Rectangle(px + GW + GAP, py, BW, BH);
            shortRect = new Rectangle(px + GW + GAP + BW + GAP, py, BW, BH);
            FontInfo fi = s.getFont(FONT);
            Font base = fi != null ? fi.getFont() : new Font("SansSerif", Font.PLAIN, 12);
            font = base.deriveFont(Font.BOLD, 15f);
            hintFont = base.deriveFont(Font.PLAIN, 11f);
            setBounds(new Rectangle(px, py, w, BH + 16));
        }

        @Override
        public void draw(Graphics2D gc, DrawContext ctx) {
            if (!visible) return;
            Settings s = getSettings();
            // grip: two columns of dots
            gc.setColor(new Color(60, 60, 60, 220));
            gc.fillRoundRect(gripRect.x, gripRect.y, gripRect.width, gripRect.height, 5, 5);
            gc.setColor(new Color(200, 200, 200));
            for (int i = 0; i < 3; i++) {
                gc.fillOval(gripRect.x + 3, gripRect.y + 5 + i * 5, 3, 3);
                gc.fillOval(gripRect.x + 8, gripRect.y + 5 + i * 5, 3, 3);
            }
            button(gc, longRect, get("BTN_LONG"), DEF_TARGET_COLOR, Boolean.TRUE.equals(armed));
            button(gc, shortRect, get("BTN_SHORT"), DEF_STOP_COLOR, Boolean.FALSE.equals(armed));
            if (armed != null) {
                gc.setFont(hintFont);
                gc.setColor(Color.WHITE);
                gc.drawString(placePrompt(), longRect.x, py + BH + 13);
            }
        }

        private void button(Graphics2D gc, Rectangle r, String text, Color color, boolean on) {
            gc.setColor(on ? color.brighter() : color);
            gc.fillRoundRect(r.x, r.y, r.width, r.height, 5, 5);
            if (on) {
                gc.setColor(Color.WHITE);
                gc.setStroke(new BasicStroke(2f));
                gc.drawRoundRect(r.x, r.y, r.width, r.height, 5, 5);
            }
            // two lines of the SAME font: the action word and "target" - so it reads as a target
            // box and can't be mistaken for the platform's Buy/Sell order buttons.
            gc.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gc.setColor(Color.WHITE);
            gc.setFont(font);
            FontMetrics fm = gc.getFontMetrics();
            String cap = get("BTN_CAPTION");
            // Centre on the VISIBLE glyph outlines (not the advance width, which includes side
            // bearings and shifts letters like "t" off-centre), horizontally per line and vertically
            // as a two-line block.
            var frc = new java.awt.font.FontRenderContext(null, true, true);
            java.awt.geom.Rectangle2D v1 = font.createGlyphVector(frc, text).getVisualBounds();
            java.awt.geom.Rectangle2D v2 = font.createGlyphVector(frc, cap).getVisualBounds();
            float lineGap = fm.getHeight();
            // block extent relative to the first baseline
            double top = v1.getY();
            double bottom = lineGap + v2.getMaxY();
            float y1 = (float) (r.getCenterY() - (top + bottom) / 2.0);
            float x1 = (float) (r.getCenterX() - v1.getWidth() / 2.0 - v1.getX());
            float x2 = (float) (r.getCenterX() - v2.getWidth() / 2.0 - v2.getX());
            // Filled glyph outlines instead of drawString: the platform's own text layout measures
            // glyph advances differently from AWT, which pushed the longer word off-centre.
            gc.fill(font.createGlyphVector(frc, text).getOutline(x1, y1));
            gc.fill(font.createGlyphVector(frc, cap).getOutline(x2, y1 + lineGap));
        }

        @Override
        public boolean contains(double x, double y, DrawContext ctx) {
            return (gripRect != null && gripRect.contains(x, y)) || (longRect != null && longRect.contains(x, y))
                    || (shortRect != null && shortRect.contains(x, y));
        }

        @Override
        public boolean isVisible(DrawContext ctx) {
            return getSettings().getBoolean(SHOW_PANEL, true);
        }
    }

    /** Drag handle for the panel. The platform reliably drags only ABSOLUTE (time/price)
     *  ResizePoints - a relative (pixel) one gets one onResize and then the chart takes the
     *  drag over. So the grip is absolute, re-anchored every layout to the panel's screen spot
     *  (translate2Time/Value), and onResize converts it back to pixels: the panel stays fixed
     *  on screen and does not scroll with the chart. */
    private class PanelGrip extends ResizePoint {
        PanelGrip() {
            super(ALL, true);
            setSnapToLocation(false);
            setFillColor(new Color(0, 0, 0, 0));
            setOutlineColor(new Color(0, 0, 0, 0));
        }

        @Override
        public void layout(DrawContext ctx) {
            if (activeRP != this && panel != null && panel.gripRect != null) {
                setLocation(ctx.translate2Time(panel.gripRect.getCenterX()), ctx.translate2Value(panel.gripRect.getCenterY()));
            }
            super.layout(ctx);
        }

        // NO contains() override: during a drag the platform keeps hit-testing the point at its
        // moving location; a custom rectangle there makes it drop the drag after one onResize.

        @Override
        public boolean isVisible(DrawContext ctx) {
            return getSettings().getBoolean(SHOW_PANEL, true);
        }
    }

    private static final class Label {
        String text;
        Color fg, bg;
        int x, y, w, h, ascent;
        Rectangle rect() { return new Rectangle(x, y, w, h); }
    }

    private class BoxFigure extends Figure {
        private final Box b;
        private boolean ok;
        private int x1, x2, yEntry, yStop, yTarget;
        private Color stopFill, targetFill;
        private PathInfo stopLine, entryLine, targetLine;
        private Font font;
        private final ArrayList<Label> labels = new ArrayList<>();
        Rectangle deleteRect;

        BoxFigure(Box b) {
            this.b = b;
        }

        @Override
        public void layout(DrawContext ctx) {
            labels.clear();
            deleteRect = null;
            ok = b.valid();
            if (!ok) return;
            Settings s = getSettings();
            DataContext dc = ctx.getDataContext();
            Instrument instr = dc.getInstrument();
            Rectangle bd = ctx.getBounds();

            x1 = ctx.translateTime(b.start);
            x2 = s.getBoolean(EXTEND_RIGHT, false) ? bd.x + bd.width : ctx.translateTime(b.end);
            if (x2 < x1 + 2) x2 = x1 + 2;
            yEntry = ctx.translateValue(b.entry);
            yStop = ctx.translateValue(b.stop);
            yTarget = ctx.translateValue(b.target);

            stopFill = s.getColor(STOP_FILL, DEF_STOP_FILL);
            targetFill = s.getColor(TARGET_FILL, DEF_TARGET_FILL);
            stopLine = s.getPath(STOP_LINE);
            entryLine = s.getPath(ENTRY_LINE);
            targetLine = s.getPath(TARGET_LINE);
            FontInfo fi = s.getFont(FONT);
            font = fi != null ? fi.getFont() : new Font("SansSerif", Font.PLAIN, 12);

            if (!s.getBoolean(SHOW_LABELS, true)) {
                setBounds(new Rectangle(x1, Math.min(yStop, yTarget), x2 - x1, Math.abs(yStop - yTarget)));
                return;
            }

            Calc c = calc(dc, b);
            boolean showPct = s.getBoolean(SHOW_PERCENT, true);
            boolean showBal = s.getBoolean(SHOW_BALANCE, false) && c.balance() > 0;
            boolean showQty = s.getBoolean(SHOW_QTY, true);
            boolean showPnl = s.getBoolean(SHOW_CURRENT_PNL, true);

            StringBuilder st = new StringBuilder("S:" + instr.format(b.stop) + " L:" + money(-c.loss()));
            if (showPct && c.balance() > 0) st.append(" (").append(num(c.loss() / c.balance() * 100)).append("%)");

            StringBuilder tt = new StringBuilder("T:" + instr.format(b.target) + " P:" + money(c.profit()));
            if (showPct && c.balance() > 0) tt.append(" (").append(num(c.profit() / c.balance() * 100)).append("%)");

            StringBuilder e1 = new StringBuilder("E:" + instr.format(b.entry));
            if (showPnl) e1.append(" P/L:").append(money(c.currentPnl()));
            StringBuilder e2 = new StringBuilder("R/R:" + String.format(Locale.US, "%.2f", c.rr()));
            if (showQty) {
                e2.append(" Q:").append(c.qty());
                if (c.qty() == 0 && c.qtyFromRisk()) e2.append(" (1 = ").append(money(c.riskPerContract())).append(")");
            }
            if (showBal) e2.append(" B:").append(num(c.balance()));

            FontMetrics fm = metricsFor(font);
            String align = s.getString(TEXT_ALIGN, ALIGN_MIDDLE);
            // The outer labels sit OUTSIDE the box - the upper one above the upper line, the lower
            // one below the lower line - so their text lies on the plain chart, not on the fill.
            boolean stopIsUpper = yStop < yTarget;
            labels.add(makeLabel(fm, new String[]{st.toString()}, yStop, align, stopIsUpper ? -1 : 1,
                    s.getColor(STOP_FG, Color.WHITE), s.getColor(STOP_BG, DEF_LABEL_BG)));
            labels.add(makeLabel(fm, new String[]{tt.toString()}, yTarget, align, stopIsUpper ? 1 : -1,
                    s.getColor(TARGET_FG, Color.WHITE), s.getColor(TARGET_BG, DEF_LABEL_BG)));
            labels.add(makeLabel(fm, new String[]{e1.toString(), e2.toString()}, yEntry, align, 0,
                    s.getColor(ENTRY_FG, Color.WHITE), s.getColor(ENTRY_BG, Color.BLACK)));

            // delete button glued to the right side of the entry label
            Label el = labels.get(labels.size() - 1);
            int ds = 16;
            deleteRect = new Rectangle(el.x + el.w + 2, el.y + (el.h - ds) / 2, ds, ds);

            Rectangle r = new Rectangle(x1, Math.min(yStop, yTarget), x2 - x1, Math.abs(yStop - yTarget));
            for (Label l : labels) r = r.union(l.rect());
            setBounds(r.union(deleteRect));
        }

        private Label makeLabel(FontMetrics fm, String[] lines, int yLine, String align, int place, Color fg, Color bg) {
            Label l = new Label();
            l.text = String.join("\n", lines);
            l.fg = fg;
            l.bg = withOpacity(bg, getSettings().getInteger(LABEL_OPACITY, 70));
            int w = 0;
            for (String line : lines) w = Math.max(w, fm.stringWidth(line));
            l.w = w + LABEL_PAD_X * 2;
            l.h = fm.getHeight() * lines.length + LABEL_PAD_Y * 2;
            l.ascent = fm.getAscent();
            if (ALIGN_LEFT.equals(align)) l.x = x1;
            else if (ALIGN_RIGHT.equals(align)) l.x = x2 - l.w;
            else l.x = (x1 + x2) / 2 - l.w / 2;
            // place: -1 = above the line, +1 = below it, 0 = centred on it
            l.y = place < 0 ? yLine - l.h - 2 : place > 0 ? yLine + 2 : yLine - l.h / 2;
            return l;
        }

        boolean bodyContains(double x, double y) {
            if (!ok) return false;
            if (x >= x1 && x <= x2 && y >= Math.min(yStop, yTarget) && y <= Math.max(yStop, yTarget)) return true;
            for (Label l : labels) if (l.rect().contains(x, y)) return true;
            return false;
        }

        /** Clicking a box selects the study (its handles appear, like the built-in tool).
         *  Protect the panel with Lock Figure: a locked study ignores the platform trash/Delete. */
        @Override
        public boolean contains(double x, double y, DrawContext ctx) {
            return bodyContains(x, y) || (deleteRect != null && deleteRect.contains(x, y));
        }

        @Override
        public boolean isVisible(DrawContext ctx) {
            return true;
        }

        @Override
        public void draw(Graphics2D gc, DrawContext ctx) {
            studySelected = ctx.isSelected();
            if (!ok) return;
            int w = x2 - x1;
            gc.setColor(stopFill);
            gc.fillRect(x1, Math.min(yEntry, yStop), w, Math.abs(yStop - yEntry));
            gc.setColor(targetFill);
            gc.fillRect(x1, Math.min(yEntry, yTarget), w, Math.abs(yTarget - yEntry));

            drawLine(gc, ctx, stopLine, yStop);
            drawLine(gc, ctx, targetLine, yTarget);
            drawLine(gc, ctx, entryLine, yEntry);

            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var frc = gc.getFontRenderContext();
            for (Label l : labels) {
                gc.setColor(l.bg);
                gc.fillRoundRect(l.x, l.y, l.w, l.h, 3, 3);
                String[] lines = l.text.split("\n");
                int lineH = (l.h - LABEL_PAD_Y * 2) / lines.length;
                float ty = l.y + LABEL_PAD_Y + l.ascent;
                for (String line : lines) {
                    java.awt.geom.Rectangle2D vb = font.createGlyphVector(frc, line).getVisualBounds();
                    float tx = (float) (l.x + (l.w - vb.getWidth()) / 2.0 - vb.getX());
                    Shape glyphs = font.createGlyphVector(frc, line).getOutline(tx, ty);
                    gc.setColor(l.fg);
                    gc.fill(glyphs);
                    ty += lineH;
                }
            }
            drawDelete(gc);
        }

        private void drawDelete(Graphics2D gc) {
            if (deleteRect == null) return;
            Rectangle d = deleteRect;
            gc.setColor(new Color(70, 70, 70, 230));
            gc.fillRoundRect(d.x, d.y, d.width, d.height, 4, 4);
            gc.setColor(Color.WHITE);
            gc.setStroke(new BasicStroke(1.6f));
            int m = 4;
            gc.drawLine(d.x + m, d.y + m, d.x + d.width - m, d.y + d.height - m);
            gc.drawLine(d.x + d.width - m, d.y + m, d.x + m, d.y + d.height - m);
        }

        private void drawLine(Graphics2D gc, DrawContext ctx, PathInfo p, int y) {
            if (p == null || !p.isEnabled()) return;
            gc.setColor(p.getColor());
            gc.setStroke(ctx.isSelected() ? p.getSelectedStroke() : p.getStroke());
            gc.drawLine(x1, y, x2, y);
        }
    }

    /** Scales a colour's own alpha by the label opacity setting (percent). */
    private static Color withOpacity(Color c, int percent) {
        int a = Math.round(c.getAlpha() * Math.max(0, Math.min(100, percent)) / 100f);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static FontMetrics metricsFor(Font font) {
        var img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.dispose();
        return fm;
    }
}
