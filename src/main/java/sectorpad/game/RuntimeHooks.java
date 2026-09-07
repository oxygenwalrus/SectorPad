package sectorpad.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import sectorpad.SectorPadRuntime;
import sectorpad.diagnostics.Diagnostics;
import java.util.List;

/** Shared boundary for lifecycle errors. A failed addon must leave the original input path available. */
public final class RuntimeHooks {
    private static boolean enabled = true;
    private static String unavailableReason = "";
    interface Boundary {
        void advance();
        void ensureAttached();
        void processInput(List<InputEventAPI> events);
    }
    private static final Boundary LIVE = new Boundary() {
        public void advance() { SectorPadRuntime.get().advance(); }
        public void ensureAttached() { OverlayHost.get().ensureAttached(); }
        public void processInput(List<InputEventAPI> events) { SectorPadRuntime.get().processInput(events); }
    };
    private RuntimeHooks() {}
    public static boolean isEnabled() { return enabled; }
    public static String unavailableReason() { return unavailableReason; }

    public static void disable(String reason) {
        enabled = false;
        unavailableReason = reason;
        Diagnostics.state("runtime", "disabled");
        Diagnostics.event("runtime.disabled");
        try { Global.getLogger(RuntimeHooks.class).warn("SectorPad disabled: " + reason); }
        catch (RuntimeException | LinkageError ignored) { }
    }

    public static void beforeInput(List<InputEventAPI> events) {
        if (!enabled) return;
        if (events == null) events = List.of();
        // Controller overlay selection does not emit native clicks/keys. Any such event can be an
        // independent pause or modal intention; conservatively give up automatic resume ownership.
        try {
        if (events != null) for (InputEventAPI event : events) {
            if (!event.isConsumed() && (event.isKeyDownEvent() || event.isMouseDownEvent())) {
                GameActions.notifyExternalPauseIntent();
                break;
            }
        }
            dispatchInput(LIVE, events);
        } catch (RuntimeException | LinkageError failure) {
            fail("runtime.input", failure);
        }
    }

    /** Synchronize ownership first; otherwise the first shortcut on a new native screen cancels itself. */
    static void dispatchInput(Boundary boundary, List<InputEventAPI> events) {
        boundary.advance();
        boundary.ensureAttached();
        boundary.processInput(events == null ? List.of() : events);
    }

    /** Public additive frame callback. Runtime's monotonic guard suppresses adjacent hook duplicates. */
    public static void advanceFrame() {
        if (!enabled) return;
        try { LIVE.advance(); LIVE.ensureAttached(); }
        catch (RuntimeException | LinkageError failure) {
            fail("runtime.frame", failure);
        }
    }

    public static void render() {
        render(false, null);
    }

    static void renderCombat(Runnable ownPanel) {
        render(true, ownPanel);
    }

    private static void render(boolean combat, Runnable ownPanel) {
        if (!enabled) return;
        try {
            if (ownPanel != null) OverlayHost.inUiCoordinates(ownPanel);
            SectorPadRuntime.get().render();
            if (combat && SectorPadRuntime.get().hasModal()) OverlayHost.inUiCoordinates(NativeCombatCursor::redrawIfSoftwareActive);
        }
        catch (RuntimeException | LinkageError failure) {
            fail("runtime.render", failure);
        }
    }

    private static void stopAfterFailure() {
        try { SectorPadRuntime.get().emergencyStop(); }
        catch (RuntimeException | LinkageError cleanupFailure) { Diagnostics.error("runtime.cleanup_boundary", cleanupFailure); }
    }

    /** Only SectorPad callbacks are isolated; no global JVM handler or another mod is replaced. */
    public static void fail(String phase, Throwable failure) {
        if (!enabled) return;
        disable("Controller addon stopped after an error; check starsector.log and SectorPad diagnostics.");
        Diagnostics.error(phase, failure);
        stopAfterFailure();
        Diagnostics.exportReport();
    }
}
