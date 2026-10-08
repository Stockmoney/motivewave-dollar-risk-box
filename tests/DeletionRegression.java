import dollar_risk_box.DollarRiskBox;
import com.motivewave.platform.sdk.common.Settings;
import java.lang.reflect.*;
import java.util.*;

/** Regression coverage against the real MotiveWave SDK's shallow Study.clone(). */
public class DeletionRegression {
    static int checks;
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static Object get(Object owner, String name) throws Exception {
        return field(owner.getClass(), name).get(owner);
    }
    static void set(Object owner, String name, Object value) throws Exception {
        field(owner.getClass(), name).set(owner, value);
    }
    static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(owner, args);
    }
    static List<?> boxes(DollarRiskBox study) throws Exception { return (List<?>)get(study, "boxes"); }
    static DollarRiskBox study(String encoded) throws Exception {
        DollarRiskBox st = new DollarRiskBox();
        Class<?> boxType = Arrays.stream(DollarRiskBox.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Box")).findFirst().orElseThrow();
        Constructor<?> ctor = boxType.getDeclaredConstructor(DollarRiskBox.class); ctor.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> list = (List<Object>) boxes(st);
        for (String part : encoded.split(";")) {
            String[] values = part.split(","); Object box = ctor.newInstance(st);
            set(box, "entry", Double.parseDouble(values[0])); set(box, "stop", Double.parseDouble(values[1]));
            set(box, "target", Double.parseDouble(values[2])); set(box, "start", Long.parseLong(values[3]));
            set(box, "end", Long.parseLong(values[4])); list.add(box);
        }
        return st;
    }
    public static void main(String[] args) throws Exception {
        DollarRiskBox original = study("100.0,90.0,120.0,10000,20000;200.0,210.0,180.0,30000,40000");
        Object originalBox = boxes(original).get(0);
        set(original, "armed", Boolean.TRUE);
        set(original, "studySelected", true);
        set(original, "lastTouched", originalBox);
        Method addFigure = com.motivewave.platform.sdk.study.Study.class.getDeclaredMethod("addFigure", com.motivewave.platform.sdk.draw.Figure.class);
        addFigure.setAccessible(true);
        addFigure.invoke(original, get(originalBox, "fig"));
        DollarRiskBox copy = original.clone();
        check(copy != original, "clone returns a distinct study");
        check(boxes(copy) != boxes(original), "box lists must be independent");
        check(boxes(copy).size() == 2, "copy preserves all boxes");
        Object copyBox = boxes(copy).get(0);
        check(copyBox != originalBox, "each box must be rebuilt");
        check(get(copyBox, "this$0") == copy, "copied box belongs to copied study");
        check(get(get(copyBox, "fig"), "this$0") == copy, "copied figure belongs to copied study");
        check(get(get(copyBox, "bodyRP"), "this$0") == copy, "copied handle belongs to copied study");
        check(get(copyBox, "entry").equals(get(originalBox, "entry")), "copy keeps price");
        check(get(copyBox, "start").equals(get(originalBox, "start")), "copy keeps time");
        set(copyBox, "entry", 101.0);
        check((double)get(originalBox, "entry") == 100.0, "editing copy cannot change original");
        check(copy.getFigures().isEmpty(), "copy clears inherited figures");
        check(original.getFigures().size() == 1, "clearing copy keeps original figures");
        check(get(copy, "armed") == null, "copy is not armed");
        check(!(boolean)get(copy, "studySelected"), "copy has no inherited selection");
        check(get(copy, "lastTouched") == null, "copy has no handle from original");
        boxes(copy).clear();
        check(boxes(original).size() == 2, "deleting copied boxes preserves originals");

        System.out.println("Deletion regression: " + checks + " checks passed");
    }
}
