package sectorpad.settings;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure capture state machine. Entry requires 200 ms neutral input; capture finishes on release. */
public final class RemapCapture {
    public enum Stage { IDLE, WAIT_NEUTRAL, CAPTURING, WAIT_RELEASE, CONFLICT, READY, CANCELLED }
    private static final long NEUTRAL_NANOS = 200_000_000L, TIMEOUT_NANOS = 15_000_000_000L;
    private Stage stage = Stage.IDLE;
    private BindingProfile profile, result;
    private String context, action, control, conflict, message = "Select an action to remap.";
    private long started, neutralSince;
    private boolean neutralSeen;
    public void begin(BindingProfile profile, String context, String action, long nowNanos) {
        if (!profile.bindings(context).containsKey(action)) throw new IllegalArgumentException("Unknown remapping action.");
        this.profile = profile; this.context = BindingProfile.normalizeContext(context); this.action = action;
        started = nowNanos; neutralSeen = false; result = null; control = null; conflict = null;
        stage = Stage.WAIT_NEUTRAL; message = "Release all controls and center both sticks.";
    }
    public Stage stage() { return stage; }
    public String message() { return message; }
    public String control() { return control; }
    public String conflict() { return conflict; }
    public BindingProfile result() { return result; }
    public boolean isCapturing() { return stage == Stage.WAIT_NEUTRAL || stage == Stage.CAPTURING || stage == Stage.WAIT_RELEASE; }
    public void sample(Set<String> down, float leftX, float leftY, float rightX, float rightY, float leftTrigger, float rightTrigger, long nowNanos) {
        if (!isCapturing()) return;
        if (nowNanos - started >= TIMEOUT_NANOS) { cancel("Capture timed out. Existing binding kept."); return; }
        if (!(Float.isFinite(leftX) && Float.isFinite(leftY) && Float.isFinite(rightX) && Float.isFinite(rightY) && Float.isFinite(leftTrigger) && Float.isFinite(rightTrigger))) { cancel("Invalid controller sample. Existing binding kept."); return; }
        Set<String> buttons = new LinkedHashSet<>(down);
        if (leftTrigger > .55f) buttons.add("LT");
        if (rightTrigger > .55f) buttons.add("RT");
        boolean neutral = buttons.isEmpty() && Math.hypot(leftX, leftY) < .22 && Math.hypot(rightX, rightY) < .22 && leftTrigger < .15 && rightTrigger < .15;
        if (stage == Stage.WAIT_NEUTRAL) {
            if (!neutral) { neutralSeen = false; return; }
            if (!neutralSeen) { neutralSeen = true; neutralSince = nowNanos; }
            if (nowNanos - neutralSince >= NEUTRAL_NANOS) { stage = Stage.CAPTURING; message = BindingProfile.isVectorAction(context, action) ? "Move one stick, then release it." : "Press one button or trigger, then release it."; }
            return;
        }
        if (stage == Stage.WAIT_RELEASE) { if (neutral) finishControl(); return; }
        if (BindingProfile.isVectorAction(context, action)) {
            boolean left = Math.hypot(leftX, leftY) >= .6, right = Math.hypot(rightX, rightY) >= .6;
            if (left ^ right && buttons.isEmpty()) control = left ? "LEFT_STICK" : "RIGHT_STICK";
            else if (left && right) message = "Move only one stick, then release it.";
        } else if (buttons.size() == 1) {
            String pressed = buttons.iterator().next();
            if (BindingProfile.BUTTONS.contains(pressed)) control = pressed;
        } else if (buttons.size() > 1) message = "Press only one control. The recovery chord stays reserved.";
        if (control != null) { stage = Stage.WAIT_RELEASE; message = "Detected " + control + ". Release all controls."; }
    }
    private void finishControl() {
        conflict = profile.conflict(context, action, control);
        if (conflict != null) { stage = Stage.CONFLICT; message = control + " already controls " + BindingProfile.actionLabel(conflict) + ". Swap these two actions or cancel."; return; }
        try { result = profile.withBinding(context, action, control); stage = Stage.READY; message = "Draft binding changed. Preview before saving."; }
        catch (IllegalArgumentException invalid) { cancel(invalid.getMessage()); }
    }
    public void swapConflict() {
        if (stage != Stage.CONFLICT) return;
        try { result = profile.withSwap(context, action, control); stage = Stage.READY; message = "Draft bindings swapped. Preview before saving."; }
        catch (IllegalArgumentException invalid) { cancel(invalid.getMessage()); }
    }
    public void cancel(String reason) { stage = Stage.CANCELLED; result = null; message = reason; }
    public void reset() { stage = Stage.IDLE; result = null; message = "Select an action to remap."; }
}
