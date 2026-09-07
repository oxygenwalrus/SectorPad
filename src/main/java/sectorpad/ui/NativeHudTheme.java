package sectorpad.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import sectorpad.diagnostics.Diagnostics;

/** Session-local native UI palette, paired with the mod's original frame textures. */
public final class NativeHudTheme {
    private NativeHudTheme() { }
    public static void apply(){
        try {
            var player=Global.getSettings().getFactionSpec(Factions.PLAYER);
            player.setDarkUIColor(TripadTheme.alpha(TripadTheme.KEY,190));
            player.setBaseUIColor(TripadTheme.CYAN);
            player.setBrightUIColor(TripadTheme.INK);
            player.setGridUIColor(TripadTheme.STEEL);
            Diagnostics.event("theme.native_applied");
            if(Global.getSettings().getModManager().isModEnabled("aotd_ashpad"))
                Global.getLogger(NativeHudTheme.class).warn("SectorPad and AashPad both provide a native UI skin. Enable one skin for consistent visuals; their textures and UI palette can conflict.");
        }catch(RuntimeException|LinkageError failure){Diagnostics.error("theme.native_unavailable",failure);}
    }
}
