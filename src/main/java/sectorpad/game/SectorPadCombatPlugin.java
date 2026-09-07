package sectorpad.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.List;

/** Also runs on the normal title-screen background combat engine. */
public final class SectorPadCombatPlugin extends BaseEveryFrameCombatPlugin {
    @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        if (Global.getCurrentState() == GameState.TITLE || Global.getCurrentState() == GameState.COMBAT) RuntimeHooks.beforeInput(events);
        if (Global.getCurrentState() == GameState.COMBAT) OverlayHost.get().renderCombatAboveHud(Global.getCombatEngine());
    }
    @Override public void renderInUICoords(ViewportAPI viewport) {
        // In installed 0.98a this callback precedes widgets and target/weapon labels. The public
        // pre-core callback follows their draw in the same frame, including while combat is paused.
        // Arm once here and paint only there. Title rendering remains on the added title panel.
        if (Global.getCurrentState() == GameState.COMBAT) OverlayHost.get().beginCombatFrame(Global.getCombatEngine());
    }
}
