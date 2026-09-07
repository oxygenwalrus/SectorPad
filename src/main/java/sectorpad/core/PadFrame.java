package sectorpad.core;

import java.util.Set;

/** One immutable, coherent device snapshot. Coordinates use positive Y upwards. */
public record PadFrame(boolean connected, String deviceId, String deviceName,
                       boolean independentTriggers, float lx, float ly, float rx, float ry,
                       float lt, float rt, Set<String> buttons) {
    public PadFrame { buttons = Set.copyOf(buttons); }
    public static PadFrame disconnected() {
        return new PadFrame(false, "", "No controller", false, 0,0,0,0,0,0,Set.of());
    }
    public boolean down(String control) { return control != null && buttons.contains(control); }
    public boolean neutral(float deadzone) {
        return buttons.isEmpty() && Math.hypot(lx,ly) <= deadzone && Math.hypot(rx,ry) <= deadzone
                && lt < 0.15f && rt < 0.15f;
    }
}
