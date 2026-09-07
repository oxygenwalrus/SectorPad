package sectorpad;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import sectorpad.game.RuntimeHooks;
import sectorpad.game.SectorPadCampaignHook;
import sectorpad.game.SectorPadCampaignFrame;
import java.util.Locale;

public final class SectorPadModPlugin extends BaseModPlugin {
    @Override public void onApplicationLoad() {
        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
            String id = mod.getId().toLowerCase(Locale.ROOT);
            if (id.equals("ssmscontroller") || id.equals("ssmscontrollerex") || id.equals("ssms_controller")) {
                RuntimeHooks.disable("Disable " + mod.getName() + " before enabling SectorPad; two controller input owners are not supported");
                return;
            }
        }
        SectorPadRuntime.get().initialize();
        Global.getLogger(SectorPadModPlugin.class).info("SectorPad additive controller integration loaded; native input initializes on first UI frame");
    }

    @Override public void onGameLoad(boolean newGame) {
        if (!RuntimeHooks.isEnabled()) return;
        SectorPadRuntime.get().onGameLoad();
        SectorPadCampaignFrame.install(Global.getSector());
        var listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(SectorPadCampaignHook.class);
        listeners.addListener(new SectorPadCampaignHook(), true);
    }
}
