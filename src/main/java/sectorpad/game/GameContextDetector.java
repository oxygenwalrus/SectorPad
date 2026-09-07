package sectorpad.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import sectorpad.bridge.ReadOnlyUiNavigator;
import sectorpad.diagnostics.Diagnostics;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class GameContextDetector {
    private final Supplier<Object> nativeState;
    private final Supplier<Object> nativeModalOwner;
    private final Function<CampaignUIAPI, Object> coreOwner;
    private final Predicate<Object> collapsedCore;

    public GameContextDetector() {
        this(StateAccess::currentState, () -> ReadOnlyUiNavigator.modalIdentity(StateAccess.uiRoot()), NativeGameUi::currentCore);
    }

    GameContextDetector(Supplier<Object> nativeState, Supplier<Object> nativeModalOwner, Function<CampaignUIAPI, Object> coreOwner) {
        this(nativeState,nativeModalOwner,coreOwner,GameContextDetector::isCollapsedCampaignCore);
    }

    GameContextDetector(Supplier<Object> nativeState, Supplier<Object> nativeModalOwner, Function<CampaignUIAPI, Object> coreOwner, Predicate<Object> collapsedCore) {
        this.nativeState = Objects.requireNonNull(nativeState);
        this.nativeModalOwner = Objects.requireNonNull(nativeModalOwner);
        this.coreOwner = Objects.requireNonNull(coreOwner);
        this.collapsedCore = Objects.requireNonNull(collapsedCore);
    }

    public GameContext detect() {
        GameState game = Global.getCurrentState();
        Object state = nativeState.get();
        String stateIdentity = StateAccess.identity(state);
        boolean modal = StateAccess.flag(state, "isShowingDialog");
        boolean codex = StateAccess.flag(state, "isShowingCodex");
        String modalIdentity = String.valueOf(StateAccess.read(state, "getDialogType"));
        Object nativeModal = nativeModalOwner.get();
        modalIdentity += ":" + StateAccess.identity(nativeModal);
        if (game == GameState.CAMPAIGN) {
            SectorAPI sector = Global.getSector();
            if (sector == null || sector.getCampaignUI() == null) return unavailable(stateIdentity);
            CampaignUIAPI ui = sector.getCampaignUI();
            CoreUITabId tab = ui.getCurrentCoreTab();
            Object dialog = ui.getCurrentInteractionDialog();
            Object core = coreOwner.apply(ui);
            boolean hudOnly = core != null && collapsedCore.test(core);
            boolean uiDialog = ui.isShowingDialog(), menu = ui.isShowingMenu();
            Diagnostics.state("game.ui_dialog", Boolean.toString(uiDialog));
            Diagnostics.state("game.state_dialog", Boolean.toString(modal));
            Diagnostics.state("game.menu", Boolean.toString(menu));
            Diagnostics.state("game.native_modal", nativeModal == null ? "none" : nativeModal == core ? "core" : "other");
            Diagnostics.state("game.core_tab", tab == null ? "none" : tab.name());
            Diagnostics.state("game.interaction", Boolean.toString(dialog != null));
            Diagnostics.state("game.core_collapsed", Boolean.toString(hudOnly));
            String identity = stateIdentity + ":" + StateAccess.identity(sector) + ":" + tab + ":"
                    + StateAccess.identity(sector.getPlayerFleet()) + ":" + StateAccess.identity(dialog) + ":" + modalIdentity
                    + ":" + StateAccess.identity(core)
                    + ":" + uiDialog + ":" + menu + ":" + codex + ":" + hudOnly;
            // The permanent campaign HUD is an oo0O too, so the generic modal scanner finds it
            // even with every core tab closed. Only exempt that exact, verified collapsed owner;
            // state/API dialog flags, encounters and any nested modal still retain UI ownership.
            boolean free = dialog == null && !uiDialog && !menu && tab == null && !modal && !codex
                    && (nativeModal == null || nativeModal == core && hudOnly);
            if (free) return new GameContext("CAMPAIGN", identity, true, sector.isPaused(), "Campaign");
            if (tab == CoreUITabId.MAP && (dialog == null || core != null) && !menu && !codex
                    && (nativeModal == null || nativeModal == core)) {
                return new GameContext("MAP", identity, false, sector.isPaused(), "Sector map");
            }
            return new GameContext("UI", identity, false, sector.isPaused(),
                    codex ? "Codex" : tab == null ? "Campaign menu" : display(tab.name()));
        }
        if (game == GameState.COMBAT) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.getCombatUI() == null) return unavailable(stateIdentity);
            CombatUIAPI ui = engine.getCombatUI();
            ShipAPI ship = engine.getPlayerShip();
            String identity = stateIdentity + ":" + StateAccess.identity(engine) + ":" + StateAccess.identity(ship)
                    + ":" + modalIdentity + ":" + codex + ":" + ui.isShowingCommandUI() + ":" + ui.isShowingDeploymentDialog()
                    + ":" + StateAccess.identity(engine.getShipPlayerIsTransferringCommandFrom())
                    + ":" + (ship != null && ship.isAlive() && !ship.isHulk() && !ship.isShuttlePod());
            if (ui.isShowingDeploymentDialog()) return new GameContext("DEPLOYMENT", identity, false, engine.isPaused(), "Deployment");
            if (modal || codex || nativeModal != null) return new GameContext("UI", identity, false, engine.isPaused(), codex ? "Codex" : "Combat menu");
            if (ui.isShowingCommandUI()) return new GameContext("TACTICAL", identity, false, engine.isPaused(), "Tactical map");
            boolean control = ship != null && ship.isAlive() && !ship.isHulk() && !ship.isShuttlePod()
                    && engine.getShipPlayerIsTransferringCommandFrom() == null;
            return new GameContext(control ? "COMBAT" : "UI", identity, control, engine.isPaused(), control ? "Combat" : "Spectating");
        }
        return new GameContext("UI", stateIdentity + ":" + modalIdentity + ":" + codex, false, false,
                game == GameState.TITLE ? (codex ? "Codex" : "Title screen") : "Loading");
    }

    static boolean isCollapsedCampaignCore(Object core) {
        try {
            return core instanceof com.fs.starfarer.ui.newui.L nativeCore && nativeCore.isCampaignUI()
                    && nativeCore.getCurrentTabId() == null && nativeCore.getCurrentTab() == null;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    private GameContext unavailable(String identity) { return new GameContext("UI", identity, false, true, "Loading"); }
    private static String display(String value) { return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT); }
}
