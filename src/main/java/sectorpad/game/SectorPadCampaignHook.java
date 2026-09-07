package sectorpad.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.List;

/** Transient pre-core input/render listener; SectorPadCampaignFrame also supplies independent paused ticks. */
public final class SectorPadCampaignHook implements CampaignInputListener, CampaignUIRenderingListener {
    @Override public int getListenerInputPriority() { return 50000; }
    @Override public void processCampaignInputPreCore(List<InputEventAPI> events) {
        if (Global.getCurrentState() == GameState.CAMPAIGN) RuntimeHooks.beforeInput(events);
    }
    @Override public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {}
    @Override public void processCampaignInputPostCore(List<InputEventAPI> events) {}
    @Override public void renderInUICoordsBelowUI(ViewportAPI viewport) {}
    @Override public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {}
    @Override public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        if (Global.getCurrentState() == GameState.CAMPAIGN) RuntimeHooks.render();
    }
}
