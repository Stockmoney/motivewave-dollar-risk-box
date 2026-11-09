package dollar_risk_box;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * The platform's layers of one chart pane, read through reflection - and one sanctioned write: {@link #moveToFront}.
 *
 * MotiveWave decides which layer gets a mouse click in {@code ui.draw.graph.v.a(Point2D)}: it walks the pane's layers from the
 * LAST to the first and hands the click to the first layer whose {@code c(Point2D)} says the point is its own. The user's
 * drawings (classes extending {@code bj.d}) sit after the indicators in that order, so a drawing under the cursor takes the
 * click away from an indicator's overlay - the Risk Arm panel, a box handle. This class finds the pane of a study and reads
 * the order, and asks the platform itself who would get a click at a point.
 *
 * Platform classes are obfuscated and are named here as the MotiveWave jars have them. The obfuscator renames packages between
 * releases, so the names come in generations (see {@link #GENS}): 7.1.1 / 7.1.2 - bj.g (layer), bj.d (drawing), bs.f (figure),
 * bs.g (figure model), bp.b (indicator layer), bp.am (strategy layer); 7.1.3 - bi.h, bi.d, br.f, br.g, bo.b, bo.am (read from its
 * bytecode on 30.10.2026). ui.draw.graph.v (pane) and ui.draw.graph.x (plot) kept their names. The generation is read off the layer
 * itself, never assumed: in 7.1.3 the old name bj.g belongs to another class (the execution markers). Anything that does not match
 * returns null: the caller then does nothing.
 */
public final class ChartLayers {

    private ChartLayers() { }

    private static final String PANE = "com.motivewave.platform.ui.draw.graph.v";

    /** One release family's names: the layer base class (Figure), the drawing base (Component), the figure, the figure model (GraphModel), the study layers' package. */
    private record Gen(String layer, String drawing, String figure, String model, String host) { }

    private static final Gen[] GENS = {
            new Gen("bj.g", "bj.d", "bs.f", "bs.g", "bp."),     // 7.1.1, 7.1.2
            new Gen("bi.h", "bi.d", "br.f", "br.g", "bo."),     // 7.1.3
    };

    /** The generation whose layer class this object is, or null. */
    private static Gen genOf(Object layer) {
        for (Gen g : GENS) if (isA(layer.getClass(), g.layer())) return g;
        return null;
    }

    private static boolean isHost(String className) {
        for (Gen g : GENS) if (className.startsWith(g.host())) return true;
        return false;
    }

    /** One layer of the pane, as read. */
    public record Layer(Object object, String name, boolean drawing, boolean ours) { }

    /** The pane's layers in hit order: the LAST one is asked first. */
    public record Order(Object pane, Object ourLayer, List<Layer> layers, int ours, int drawingsAfterUs) {
        public String describe() {
            StringBuilder sb = new StringBuilder("pane=").append(pane.getClass().getName()).append(" layers=").append(layers.size())
                    .append(" ours=").append(ours).append(" drawingsAfterUs=").append(drawingsAfterUs).append(" order(first->last, last is asked first)=[");
            int n = layers.size();
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(", ");
                Layer l = layers.get(i);
                sb.append(l.ours() ? "*" : "").append(l.name());
                if (i >= 90) { sb.append(", ...").append(n - i - 1).append(" more"); break; }
            }
            return sb.append(']').toString();
        }
    }

    private static boolean isA(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) if (k.getName().equals(name)) return true;
        return false;
    }

    private static List<Object> fieldValues(Object o) {
        List<Object> out = new ArrayList<>();
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v != null) out.add(v);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // an unreadable field is simply not a candidate
                }
            }
        }
        return out;
    }

    /**
     * The platform layer that carries this study: an object that is a bj.g and holds the study itself, found among the objects
     * the study's data context points to (two levels deep). Null when it is not found.
     */
    public static Object layerOf(Object study, Object dataContext) {
        if (study == null || dataContext == null) return null;
        try {
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            List<Object> level = fieldValues(dataContext);
            for (int depth = 0; depth < 3 && !level.isEmpty(); depth++) {
                List<Object> next = new ArrayList<>();
                for (Object cand : level) {
                    if (seen.put(cand, true) != null) continue;
                    if (genOf(cand) != null) {
                        for (Object v : fieldValues(cand)) if (v == study) return cand;
                    }
                    if (depth < 2 && !(cand instanceof CharSequence) && !(cand instanceof Number) && !(cand instanceof java.util.Collection)
                            && isHost(cand.getClass().getName())) next.addAll(fieldValues(cand));
                }
                level = next;
            }
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    /** The pane of a layer: its public no-argument method that returns the pane class. */
    public static Object paneOf(Object layer) {
        try {
            for (Method m : layer.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().equals(PANE)) return m.invoke(layer);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }

    /** Reads the pane's layers in hit order (the platform's own list: its two holders, one after the other). */
    public static Order read(Object layer) {
        Object pane = paneOf(layer);
        Gen gen = genOf(layer);
        if (pane == null || gen == null) return null;
        try {
            // the pane's own protected c(): its two holders one after the other - exactly the list its hit test walks (reversed)
            List<Object> all = new ArrayList<>();
            Method concat = null;
            for (Class<?> c = pane.getClass(); c != null && concat == null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("c") && m.getParameterCount() == 0 && m.getReturnType() == List.class) { concat = m; break; }
                }
            }
            if (concat == null) return null;
            concat.setAccessible(true);
            Object list = concat.invoke(pane);
            if (list instanceof List<?> l) all.addAll(l);
            List<Layer> out = new ArrayList<>();
            int ours = -1, drawingsAfter = 0;
            for (Object o : all) {
                boolean mine = o == layer;
                boolean drawing = isA(o.getClass(), gen.drawing());
                String name = o.getClass().getName();
                name = name.substring(name.lastIndexOf('.') + 1) + (drawing ? "(drawing)" : "");
                out.add(new Layer(o, name, drawing, mine));
                if (mine) ours = out.size() - 1;
            }
            if (ours < 0) return new Order(pane, layer, out, -1, 0);
            for (int i = ours + 1; i < out.size(); i++) if (out.get(i).drawing()) drawingsAfter++;
            return new Order(pane, layer, out, ours, drawingsAfter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** A name of some generation's layer class (the pane has one such hit test; the generation's other classes never come back from it). */
    private static boolean isLayerType(String name) {
        for (Gen g : GENS) if (g.layer().equals(name)) return true;
        return false;
    }

    private static Object byReturnType(Object target, String typeName, String preferredName) throws ReflectiveOperationException {
        Method first = null;
        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || !m.getReturnType().getName().equals(typeName)) continue;
            if (m.getName().equals(preferredName)) return m.invoke(target);
            if (first == null) first = m;
        }
        return first == null ? null : first.invoke(target);
    }

    private static Method voidNoArg(Object target, String name) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0 && m.getReturnType() == void.class
                    && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) return m;
        }
        return null;
    }

    /**
     * The platform's own "Move Front" (the chart's context menu, ui.draw.graph.ChartMouseTool): in the chart's figure model
     * ({@code bs.g} / {@code br.g}) the figure of this layer is taken out and put last, and the pane re-orders its layers. The same three calls
     * the menu item makes - begin update, move, end update - so the new order is the one the workspace saves. Must run on the
     * JavaFX thread, like the menu action does.
     *
     * @return true when the calls were made
     */
    public static boolean moveToFront(Object layer) {
        try {
            Gen gen = genOf(layer);
            if (gen == null) return false;
            Object figure = byReturnType(layer, gen.figure(), "a");
            Object plot = byReturnType(layer, "com.motivewave.platform.ui.draw.graph.x", "a");
            if (figure == null || plot == null) return false;
            Object model = byReturnType(plot, gen.model(), "a");
            if (model == null) return false;
            Method front = null;
            for (Method m : model.getClass().getMethods()) {
                if (m.getName().equals("f") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals(gen.figure())) front = m;
            }
            if (front == null) return false;
            Method begin = voidNoArg(model, "b"), end = voidNoArg(model, "c");
            if (begin != null) begin.invoke(model);
            try {
                front.invoke(model, figure);
            } finally {
                if (end != null) end.invoke(model);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** True when the platform has dropped this layer (the study was removed or re-created). */
    public static boolean destroyed(Object layer) {
        try {
            for (Method m : layer.getClass().getMethods()) {
                if (m.getName().equals("isDestroyed") && m.getParameterCount() == 0) return (Boolean) m.invoke(layer);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
        return false;
    }

    /**
     * Who would get a click at this point, according to the platform's own hit test on the pane (ui.draw.graph.v.a(Point2D)).
     * The point is in the same coordinates the study's onClick receives. Null when it cannot be asked.
     */
    public static Object hitAt(Object pane, double x, double y) {
        try {
            for (Method m : pane.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == java.awt.geom.Point2D.class
                        && isLayerType(m.getReturnType().getName())) return m.invoke(pane, new java.awt.geom.Point2D.Double(x, y));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
        return null;
    }
}
