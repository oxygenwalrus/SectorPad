package sectorpad.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

/** Stateless transient campaign clock: no UI, input-device, or runtime object is serialized in saves. */
public final class SectorPadCampaignFrame implements EveryFrameScript {
    public static void install(SectorAPI sector) {
        if (sector == null) return;
        sector.removeTransientScriptsOfClass(SectorPadCampaignFrame.class);
        sector.addTransientScript(new SectorPadCampaignFrame());
    }
    @Override public boolean isDone() { return !RuntimeHooks.isEnabled(); }
    @Override public boolean runWhilePaused() { return true; }
    @Override public void advance(float amount) {
        // Campaign simulation time may be paused or fast-forwarded. The runtime owns its real-time clock.
        advanceIfActive(RuntimeHooks.isEnabled(), Global.getCurrentState(), RuntimeHooks::advanceFrame);
    }
    static void advanceIfActive(boolean enabled, GameState state, Runnable advance) {
        if (enabled && state == GameState.CAMPAIGN) advance.run();
    }
}
