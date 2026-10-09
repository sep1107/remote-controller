package fun.hpqq.kindleremote;

import java.util.HashMap;
import java.util.Map;

/** Independent presses do not share state, including two paired controller halves. */
public final class InputGate {
    private final Map<String, Long> started = new HashMap<>();
    private final Map<String, Long> fired = new HashMap<>();

    public boolean press(String id, int action, long now) {
        boolean first = !started.containsKey(id);
        if (first) started.put(id, now);
        if (action == 4) {
            if (now - started.get(id) < 1000 || fired.containsKey(id)) return false;
        } else if (action < 2) {
            if (!first) return false;
        } else if (!first && now - fired.getOrDefault(id, now) < 250) return false;
        fired.put(id, now);
        return true;
    }

    public void release(String id) { started.remove(id); fired.remove(id); }
    public void clear() { started.clear(); fired.clear(); }
}
