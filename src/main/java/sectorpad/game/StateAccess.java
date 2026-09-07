package sectorpad.game;

import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.starfarer.combat.CombatState;
import com.fs.starfarer.title.TitleScreenState;
import com.fs.starfarer.settings.StarfarerSettings;
import com.fs.starfarer.ui.interfacenew;
import com.fs.state.AppDriver;

/** Narrow read-only adapter to verified public getters on the installed game's UI classes. */
public final class StateAccess {
    private StateAccess() {}

    public static Object currentState() {
        try {
            AppDriver driver = AppDriver.getInstance();
            return driver == null ? null : driver.getCurrentState();
        } catch (LinkageError | RuntimeException unavailable) {
            return null;
        }
    }

    public static Object read(Object target, String getter) {
        if (target == null) return null;
        try {
            if (target instanceof TitleScreenState title) return switch (getter) {
                case "isShowingDialog" -> title.isShowingDialog();
                case "isShowingCodex" -> title.isShowingCodex();
                case "getDialogType" -> title.getDialogType();
                case "getScreenPanel" -> title.getScreenPanel();
                case "getOverlayPanelForCodex" -> title.getOverlayPanelForCodex();
                default -> null;
            };
            if (target instanceof CampaignState campaign) return switch (getter) {
                case "isShowingDialog" -> campaign.isShowingDialog();
                case "isShowingCodex" -> campaign.isShowingCodex();
                case "getDialogType" -> campaign.getDialogType();
                case "getScreenPanel" -> campaign.getScreenPanel();
                case "getOverlayPanelForCodex" -> campaign.getOverlayPanelForCodex();
                case "getCore" -> campaign.getCore();
                default -> null;
            };
            if (target instanceof CombatState combat) return switch (getter) {
                case "isShowingDialog" -> combat.isShowingDialog();
                case "isShowingCodex" -> combat.isShowingCodex();
                case "getDialogType" -> combat.getDialogType();
                case "getScreenPanel" -> combat.getScreenPanel();
                case "getOverlayPanelForCodex" -> combat.getOverlayPanelForCodex();
                default -> null;
            };
            if (target instanceof interfacenew panel) return switch (getter) {
                case "getChildrenCopy", "getChildrenNonCopy" -> panel.getChildrenCopy();
                default -> null;
            };
            return null;
        } catch (LinkageError | RuntimeException unavailable) {
            return null;
        }
    }

    public static boolean flag(Object target, String getter) {
        return Boolean.TRUE.equals(read(target, getter));
    }

    /** Installed CampaignState uses this public setting to choose held versus toggled fast-forward. */
    public static boolean usesToggleFastForward() {
        try { return StarfarerSettings.Oo0000(); }
        catch (LinkageError | RuntimeException unavailable) { return false; }
    }

    public static UIPanelAPI uiRoot() {
        Object state = currentState();
        if (flag(state, "isShowingCodex")) {
            Object codex = read(state, "getOverlayPanelForCodex");
            if (codex instanceof UIPanelAPI panel) return panel;
        }
        Object panel = read(state, "getScreenPanel");
        return panel instanceof UIPanelAPI api ? api : null;
    }

    /** After normal panels even with Codex closed; combat target labels still render after this. */
    public static UIPanelAPI renderingRoot() {
        Object overlay = read(currentState(), "getOverlayPanelForCodex");
        return overlay instanceof UIPanelAPI panel ? panel : uiRoot();
    }

    public static String identity(Object value) {
        return value == null ? "none" : value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

}
