package sectorpad.settings;

import sectorpad.core.PadFrame;
import java.util.HashSet;
import java.util.Set;

/** Pure setup-input regressions; no Luna panel, SDL, native output, or running game is initialized. */
public final class SetupInputTests {
    private static int checks;
    public static void main(String[] args) {
        neutralAndDiagnostics(); navigation(); capture(); compatibility(); setupGeometry();
        System.out.println("SetupInputTests: " + checks + " checks passed (headless; no game/controller claim)");
    }
    private static PadFrame frame(Set<String> buttons, float lx, float ly, float rx, float ry, float lt, float rt) {
        return new PadFrame(true, "device-id", "Raw device", true, lx, ly, rx, ry, lt, rt, buttons);
    }
    private static PadFrame zero() { return frame(Set.of(), 0, 0, 0, 0, 0, 0); }
    private static LunaRemappingPanel.InputState paired(PadFrame normalized, PadFrame raw) {
        return LunaRemappingPanel.InputState.fromFrames(normalized, raw, true);
    }
    private static void neutralAndDiagnostics() {
        DeviceCalibration.Trigger trigger = new DeviceCalibration.Trigger(.2f, .6f);
        PadFrame raw = frame(Set.of(), .28f, -.21f, -.25f, .27f, .2f, .2f);
        var rest = paired(frame(Set.of(), 0, 0, 0, 0, trigger.apply(raw.lt()), trigger.apply(raw.rt())), raw);
        check(rest.rawFrame==raw,"Diagram receives the unmodified device snapshot");
        check(!rest.held("LEFT_STICK")&&!rest.held("RIGHT_STICK"),"Raw drift does not report an effective mapped action");
        check(paired(frame(Set.of(),.8f,0,0,0,0,0),raw).held("LEFT_STICK"),"Effective left-stick action is reported");
        check(paired(frame(Set.of(),0,0,0,.8f,0,0),raw).held("RIGHT_STICK"),"Effective right-stick action is reported");
        check(!paired(frame(Set.of("A"),1,0,0,0,0,0),PadFrame.disconnected()).held("A"),"Disconnected setup cannot report a held action");
        check(rest.neutral(), "Valid trigger rest offset and centered sticks can arm setup");
        check(rest.leftTrigger == 0 && rest.rightTrigger == 0 && rest.buttons.isEmpty(), "Raw offsets cannot create calibrated trigger presses");
        check(rest.rawLeftTrigger == .2f && rest.rawRightTrigger == .2f, "Diagnostic trigger rest values remain raw");
        check(rest.rawLeftX == .28f && rest.rawLeftY == -.21f && rest.rawRightX == -.25f && rest.rawRightY == .27f, "Both raw stick samples remain available for calibration");
        var rawPress = paired(zero(), frame(Set.of("LT", "RT"), 0, 0, 0, 0, .8f, .9f));
        check(rawPress.neutral() && rawPress.buttons.isEmpty(), "Uncalibrated trigger thresholds cannot synthesize setup input");
        check(rawPress.rawButtons.equals(Set.of("LT", "RT")) && rawPress.rawRightTrigger == .9f, "Raw digital diagnostics remain distinct from effective controls");
        var normalizedPress = paired(frame(Set.of("RT"), 0, 0, 0, 0, 0, .3f), zero());
        check(!normalizedPress.neutral() && normalizedPress.buttons.contains("RT"), "Calibrated held trigger survives a lower hysteresis value");
        check(paired(frame(Set.of(), 0, 0, 0, 0, 0, .2f), zero()).neutral() == false, "Partly deflected normalized trigger still blocks neutral arming");
    }
    private static void navigation() {
        check(paired(zero(), frame(Set.of("DPAD_UP"), 0, 1, 0, 0, 0, 0)).navigationDirection().isEmpty(), "Raw input cannot navigate setup");
        check(paired(frame(Set.of(), 0, .8f, 0, 0, 0, 0), frame(Set.of(), 0, -.8f, 0, 0, 0, 0)).navigationDirection().equals("UP"), "Calibrated stick inversion controls navigation direction");
        check(paired(frame(Set.of("DPAD_DOWN"), 0, 0, 0, 0, 0, 0), zero()).navigationDirection().equals("DOWN"), "Normalized D-pad navigation remains available");
        check(paired(frame(Set.of(), 0, .5f, 0, 1, 0, 0), zero()).navigationDirection().isEmpty(), "Subthreshold left stick and right stick cannot move setup selection");
    }
    private static void capture() {
        PadFrame rawRest = frame(Set.of(), .25f, -.2f, -.25f, .2f, .2f, .2f);
        var rest = paired(zero(), rawRest);
        RemapCapture capture = new RemapCapture();
        capture.begin(BindingProfile.defaults(), "COMBAT", "combat.fire", 0);
        rest.sampleCapture(capture, 0); rest.sampleCapture(capture, 200_000_000L);
        check(capture.stage() == RemapCapture.Stage.CAPTURING, "Capture becomes ready with nonzero raw rest values");
        paired(frame(Set.of(), 0, 0, 0, 0, 0, .65f), frame(Set.of("RT"), 0, 0, 0, 0, 0, .65f)).sampleCapture(capture, 300_000_000L);
        check(capture.stage() == RemapCapture.Stage.CAPTURING, "Capture cannot override a configured press threshold with its legacy .55 threshold");
        DeviceCalibration.Trigger trigger = new DeviceCalibration.Trigger(.2f, .6f);
        paired(frame(Set.of("RT"), 0, 0, 0, 0, 0, trigger.apply(.5f)), frame(Set.of(), 0, 0, 0, 0, .2f, .5f)).sampleCapture(capture, 400_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_RELEASE && capture.control().equals("RT"), "Reduced calibrated range captures the runtime trigger even below raw .55");
        paired(frame(Set.of("RT"), 0, 0, 0, 0, 0, .1f), rawRest).sampleCapture(capture, 500_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_RELEASE, "Calibrated trigger hysteresis holds capture until the button releases");
        rest.sampleCapture(capture, 600_000_000L);
        check(capture.stage() == RemapCapture.Stage.READY && capture.result().binding("COMBAT", "combat.fire").equals("RT"), "Normalized release finishes capture despite raw offsets");

        capture.begin(BindingProfile.defaults(), "UI", "ui.pointer", 0);
        rest.sampleCapture(capture, 0); rest.sampleCapture(capture, 200_000_000L);
        paired(frame(Set.of(), 0, 0, .8f, 0, 0, 0), frame(Set.of(), .9f, 0, .3f, 0, .2f, .2f)).sampleCapture(capture, 300_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_RELEASE && capture.control().equals("RIGHT_STICK"), "Vector capture follows calibrated stick values instead of raw motion");
        rest.sampleCapture(capture, 400_000_000L);
        check(capture.stage() == RemapCapture.Stage.CONFLICT && capture.conflict().equals("ui.scroll"), "Vector capture retains normal conflict handling after calibrated release");

        capture.begin(BindingProfile.defaults(), "COMBAT", "combat.fire", 0);
        paired(frame(Set.of(), 0, 0, 0, 0, Float.NaN, 0), zero()).sampleCapture(capture, 1);
        check(capture.stage() == RemapCapture.Stage.CANCELLED, "Invalid normalized trigger samples remain rejected");
        check(paired(zero(), frame(Set.of(), Float.NaN, 0, 0, 0, Float.NaN, 0)).neutral(), "Raw diagnostic nonfinite values cannot replace a valid normalized sample");
    }
    private static void compatibility() {
        Set<String> mutable = new HashSet<>(Set.of("A", "GUIDE"));
        var legacy = new LunaRemappingPanel.InputState(mutable, .1f, .2f, -.1f, -.2f, .8f, 0, true, false, null);
        mutable.clear();
        check(legacy.buttons.equals(Set.of("A", "LT")) && legacy.rawButtons.equals(legacy.buttons), "Legacy constructor preserves trigger synthesis and defensive button filtering");
        check(legacy.leftX == legacy.rawLeftX && legacy.leftY == legacy.rawLeftY && legacy.rightX == legacy.rawRightX && legacy.rightY == legacy.rawRightY && legacy.leftTrigger == legacy.rawLeftTrigger, "Legacy axes remain shared normalized and diagnostic values");
        check(legacy.connected && !legacy.focused && legacy.deviceName.equals("Controller"), "Compatibility constructor retains focus, connectivity, and device-name fallback");
        try { legacy.buttons.add("B"); throw new AssertionError("Expected immutable normalized buttons"); }
        catch (UnsupportedOperationException expected) { check(true, "Normalized button snapshot is immutable"); }
        try { legacy.rawButtons.clear(); throw new AssertionError("Expected immutable raw buttons"); }
        catch (UnsupportedOperationException expected) { check(true, "Raw button snapshot is immutable"); }
        check(!paired(zero(), PadFrame.disconnected()).connected && !paired(PadFrame.disconnected(), zero()).connected, "Either disconnected frame disables setup input");
        check(!LunaRemappingPanel.InputState.fromFrames(zero(), zero(), false).focused, "Factory preserves physical focus state");
        check(!LunaRemappingPanel.InputState.disconnected().connected, "Disconnected compatibility factory remains available");
    }
    private static void setupGeometry(){
        for(float screenHeight:new float[]{480,720,800,1080}){
            float height=Math.min(screenHeight-40,760)-20;
            var layout=LunaRemappingPanel.setupLayout(height);
            check(layout.pageSize()>=1,"At least one binding is visible");
            check(layout.listTop()+layout.pageSize()*29<=layout.footer(),"Binding rows do not overlap footer at "+screenHeight);
            check(62+layout.diagramHeight()<layout.listTop(),"Diagram does not overlap bindings");
            check(layout.footer()+63<=height,"Both footer rows remain inside setup");
        }
        check(LunaRemappingPanel.setupLayout(420).compact(),"Scaled 480-high screen uses compact information bands");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
