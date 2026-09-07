package sectorpad.game;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Game-thread adapter. Uses normal ship commands and campaign UI entry points;
 * cargo, refit, save dialogs, map widgets and colonies retain vanilla ownership.
 */
public final class GameActions {
    private final GameContextDetector detector = new GameContextDetector();
    private final Consumer<String> ordinaryUiAction;
    private final BooleanSupplier gameFocused;
    private final BooleanSupplier nativeFastForwardToggle;
    private final NativeGameUi nativeUi;
    private ShipAPI controlledShip;
    private ShipAPI shieldOwner;
    private CampaignFleetAPI movedFleet;
    private SectorEntityToken selectedCampaignTarget;
    private boolean targetLocked;
    private float aimAngle = Float.NaN;
    private Vector2f lastAimPoint;
    private ShipAPI precisionOwner;
    private Vector2f precisionPoint;
    private String steeringMode = "TwinStick";
    private boolean shieldToggle;
    private float configuredAimRange = 1000f;
    private float campaignZoomSpeed = 5f;
    private boolean fastForwardOwned;
    private boolean movementOwned;
    private static long externalPauseEpoch;
    private Object pauseOwner;
    private String pauseContext;
    private boolean pauseWasAlreadySet;
    private int pauseDepth;
    private long pauseGeneration;
    private long pauseExternalEpoch;
    private boolean resumeForbidden;

    public GameActions(Consumer<String> ordinaryUiAction) {
        this(ordinaryUiAction, () -> Display.isCreated() && Display.isActive());
    }

    GameActions(Consumer<String> ordinaryUiAction, BooleanSupplier gameFocused) {
        this(ordinaryUiAction, gameFocused, StateAccess::usesToggleFastForward);
    }

    GameActions(Consumer<String> ordinaryUiAction, BooleanSupplier gameFocused, BooleanSupplier nativeFastForwardToggle) {
        this(ordinaryUiAction, gameFocused, nativeFastForwardToggle, new NativeGameUi());
    }

    GameActions(Consumer<String> ordinaryUiAction, BooleanSupplier gameFocused, BooleanSupplier nativeFastForwardToggle, NativeGameUi nativeUi) {
        this.ordinaryUiAction = Objects.requireNonNull(ordinaryUiAction);
        this.gameFocused = Objects.requireNonNull(gameFocused);
        this.nativeFastForwardToggle = Objects.requireNonNull(nativeFastForwardToggle);
        this.nativeUi = Objects.requireNonNull(nativeUi);
    }

    public GameContext context() { return detector.detect(); }
    public UIPanelAPI uiRoot() { return StateAccess.uiRoot(); }
    public boolean isTargetLocked() { return targetLocked; }
    public boolean isPrecisionTargeting() { return precisionOwner != null && precisionOwner == playerShip() && precisionPoint != null; }
    public boolean isAutopilotOn() { CombatEngineAPI engine = combat(); return engine != null && engine.getCombatUI() != null && engine.getCombatUI().isAutopilotOn(); }
    public Vector2f getAimPoint() { return lastAimPoint == null ? null : new Vector2f(lastAimPoint); }
    public boolean isFastForward() { CampaignUIAPI ui = campaignUI(); return ui != null && ui.isFastForward(); }
    public void configureCampaign(float zoomSpeed) { campaignZoomSpeed = Float.isFinite(zoomSpeed) ? Math.max(1f, Math.min(20f, zoomSpeed)) : 5f; }

    public void configure(String steeringMode, boolean shieldToggle, float aimRange) {
        this.steeringMode = steeringMode != null && Set.of("TwinStick", "Directional", "Orbital").contains(steeringMode) ? steeringMode : "TwinStick";
        if (this.shieldToggle != shieldToggle) releaseShield();
        this.shieldToggle = shieldToggle;
        this.configuredAimRange = Float.isFinite(aimRange) ? Math.max(100f, Math.min(10000f, aimRange)) : 1000f;
    }

    public String targetLabel() {
        if (Global.getCurrentState() == GameState.COMBAT) {
            ShipAPI ship = playerShip();
            ShipAPI target = ship == null ? null : ship.getShipTarget();
            return target == null ? "No target" : target.getName();
        }
        return validCampaignTarget(selectedCampaignTarget) ? selectedCampaignTarget.getName() : "No target";
    }

    /** Axes are already calibrated/shaped, with positive X right and positive Y up. */
    public void dispatch(Set<String> held, Set<String> pressed, Set<String> released,
                         float moveX, float moveY, float aimX, float aimY, float seconds) {
        GameContext context = context();
        if (!context.gameplayAllowed()) {
            neutralize();
            return;
        }
        if (context.is("COMBAT")) {
            if (movedFleet != null) stopCampaignMovement();
            releaseFastForward();
            dispatchCombat(held, pressed, released, finite(moveX), finite(moveY), finite(aimX), finite(aimY), seconds);
        } else if (context.is("CAMPAIGN")) {
            releaseShield();
            controlledShip = null;
            if (!context.paused()) moveCampaign(finite(moveX), finite(moveY));
            else { stopCampaignMovement(); releaseFastForward(); }
            int zoom = (held.contains("campaign.zoomIn") ? 1 : 0) - (held.contains("campaign.zoomOut") ? 1 : 0);
            if (zoom != 0) zoomCampaign(zoom, seconds);
            if (fastForwardOwned) ordinaryUiAction.accept("campaign.fastForward.down");
        }
        for (String action : pressed) {
            if (!Set.of("combat.shield", "combat.fire", "combat.brake", "campaign.zoomIn", "campaign.zoomOut").contains(action)) execute(action);
        }
    }

    private void dispatchCombat(Set<String> held, Set<String> pressed, Set<String> released,
                                float moveX, float moveY, float aimX, float aimY, float seconds) {
        CombatEngineAPI engine = combat();
        ShipAPI ship = playerShip();
        if (ship != controlledShip) {
            releaseShield();
            controlledShip = ship;
            aimAngle = ship == null ? Float.NaN : ship.getFacing();
            targetLocked = false;
            movementOwned = false;
            lastAimPoint = null;
            if (precisionOwner != ship) { precisionOwner = null; precisionPoint = null; }
        }
        if (ship == null || engine.isPaused() || engine.getCombatUI().isAutopilotOn()) {
            releaseShield();
            return;
        }
        boolean moving = moveX * moveX + moveY * moveY > .0001f;
        boolean aiming = aimX * aimX + aimY * aimY > .0001f;
        boolean precise = isPrecisionTargeting();
        boolean control = moving || movementOwned || aiming || targetLocked || precise || held.stream().anyMatch(a -> a.startsWith("combat."))
                || pressed.stream().anyMatch(a -> a.startsWith("combat.")) || released.contains("combat.shield");
        if (!control) return;
        // This public one-frame flag keeps the vanilla ship controller from overwriting our commands.
        engine.getCombatUI().setDisablePlayerShipControlOneFrame(true);
        ShipAPI target = ship.getShipTarget();
        if (targetLocked && !validHostile(ship, target)) targetLocked = false;
        float hullAngle = Float.NaN;
        if (steeringMode.equals("Directional") && moving) hullAngle = (float) Math.toDegrees(Math.atan2(moveY, moveX));
        if (steeringMode.equals("Orbital") && validHostile(ship, target)) {
            float dx = target.getLocation().x - ship.getLocation().x;
            float dy = target.getLocation().y - ship.getLocation().y;
            float length = (float) Math.hypot(dx, dy);
            if (length > 1f) {
                float radialX = dx / length, radialY = dy / length;
                float orbitX = radialY * moveX + radialX * moveY;
                float orbitY = -radialX * moveX + radialY * moveY;
                moveX = orbitX;
                moveY = orbitY;
                hullAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
            }
        }
        if (held.contains("combat.brake")) {
            command(ship, ShipCommand.DECELERATE, null, 0);
            movementOwned = false;
        } else if (moving || movementOwned) {
            movementOwned = moving || ship.getVelocity().lengthSquared() > 4f;
            if (!moving) command(ship, ShipCommand.DECELERATE, null, 0);
            else {
                // Closed-loop normal thruster commands retain the ship's acceleration and speed limits.
                // Partial stick deflection requests a partial velocity, rather than full thrust forever.
                float magnitude = (float) Math.hypot(moveX, moveY);
                if (magnitude > 1f) { moveX /= magnitude; moveY /= magnitude; }
                float speed = Math.max(0f, ship.getMaxSpeed());
                SteeringMath.LocalMovement error = SteeringMath.local(moveX * speed - ship.getVelocity().x,
                        moveY * speed - ship.getVelocity().y, ship.getFacing());
                float dt = Float.isFinite(seconds) ? Math.max(0f, Math.min(.05f, seconds)) : 0f;
                float band = Math.max(1.5f, ship.getAcceleration() * dt * .6f);
                if (error.forward() > band) command(ship, ShipCommand.ACCELERATE, null, 0);
                if (error.forward() < -band) command(ship, ShipCommand.ACCELERATE_BACKWARDS, null, 0);
                if (error.left() > band) command(ship, ShipCommand.STRAFE_LEFT, null, 0);
                if (error.left() < -band) command(ship, ShipCommand.STRAFE_RIGHT, null, 0);
            }
        }
        if (precise) {
            float dt = Float.isFinite(seconds) ? Math.max(0f, Math.min(.05f, seconds)) : 0f;
            precisionPoint.translate(aimX * configuredAimRange * .75f * dt, aimY * configuredAimRange * .75f * dt);
            float dx = precisionPoint.x - ship.getLocation().x, dy = precisionPoint.y - ship.getLocation().y;
            float distance = (float) Math.hypot(dx, dy);
            if (distance > configuredAimRange) precisionPoint.set(ship.getLocation().x + dx / distance * configuredAimRange,
                    ship.getLocation().y + dy / distance * configuredAimRange);
            aimAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
        } else if (aiming) aimAngle = (float) Math.toDegrees(Math.atan2(aimY, aimX));
        Vector2f aim = aimPoint(ship);
        if (targetLocked) {
            aim.set(target.getLocation());
            aimAngle = (float) Math.toDegrees(Math.atan2(aim.y - ship.getLocation().y, aim.x - ship.getLocation().x));
        }
        if (!Float.isFinite(hullAngle) && (aiming || targetLocked || precise)) hullAngle = aimAngle;
        if (Float.isFinite(hullAngle)) {
            int turn = SteeringMath.turn(hullAngle, ship.getFacing(), ship.getAngularVelocity(), ship.getTurnDeceleration(), seconds);
            if (turn > 0) command(ship, ShipCommand.TURN_LEFT, null, 0);
            if (turn < 0) command(ship, ShipCommand.TURN_RIGHT, null, 0);
        }
        ship.getMouseTarget().set(aim);
        lastAimPoint = new Vector2f(aim);
        ship.setShieldTargetOverride(aim.x, aim.y);
        if (held.contains("combat.fire")) command(ship, ShipCommand.FIRE, aim, selectedGroup(ship));
        if (held.contains("combat.shield") && pressed.contains("combat.shield") && hasDefense(ship) && (shieldToggle || !shieldIsOn(ship))) {
            command(ship, ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, aim, 0);
            if (!shieldToggle) shieldOwner = ship;
        }
        if (!shieldToggle && (released.contains("combat.shield") || !held.contains("combat.shield"))) releaseShield();
    }

    /** Return true when this adapter recognizes the action; unavailable actions have no effect. */
    public boolean execute(String action) {
        if (action == null) return false;
        if (action.startsWith("ability:")) { pressAbility(action.substring(8)); return true; }
        if (action.startsWith("weapon.select:")) { selectGroup(parseIndex(action.substring(14))); return true; }
        if (action.startsWith("weapon.autofire:")) { toggleGroup(parseIndex(action.substring(16))); return true; }
        switch (action) {
            case "campaign.pause", "combat.pause" -> togglePause();
            case "campaign.map" -> openTab(CoreUITabId.MAP);
            case "campaign.intel" -> openTab(CoreUITabId.INTEL);
            case "campaign.fleet" -> openTab(CoreUITabId.FLEET);
            case "campaign.refit" -> openTab(CoreUITabId.REFIT);
            case "campaign.cargo" -> openTab(CoreUITabId.CARGO);
            case "campaign.character" -> openTab(CoreUITabId.CHARACTER);
            case "campaign.colonies" -> openTab(CoreUITabId.OUTPOSTS);
            case "campaign.cancelCourse" -> cancelCourse();
            case "campaign.recenter" -> { if (context().is("CAMPAIGN")) campaignUI().resetViewOffset(); }
            case "campaign.fastForward" -> toggleFastForward();
            case "campaign.nextTarget" -> cycleCampaignTarget(1);
            case "campaign.previousTarget" -> cycleCampaignTarget(-1);
            case "campaign.interact" -> interact();
            case "campaign.zoomIn" -> zoomCampaign(1);
            case "campaign.zoomOut" -> zoomCampaign(-1);
            case "combat.target" -> selectCombatTargetAtAim();
            case "combat.nextTarget" -> cycleCombatTarget();
            case "combat.precisionTarget" -> togglePrecisionTarget();
            case "combat.targetLock" -> { if (combatControlPossible()) { if (playerShip().getShipTarget() == null) selectCombatTargetAtAim(); targetLocked = !targetLocked && validHostile(playerShip(), playerShip().getShipTarget()); if (targetLocked) { precisionOwner = null; precisionPoint = null; } } }
            case "combat.vent" -> shipCommand(ShipCommand.VENT_FLUX);
            case "combat.system" -> { ShipAPI ship = playerShip(); if (ship != null && ship.getSystem() != null && ship.getSystem().canBeActivated()) shipCommand(ShipCommand.USE_SYSTEM); }
            case "combat.fighters" -> shipCommand(ShipCommand.PULL_BACK_FIGHTERS);
            case "combat.autofire" -> { ShipAPI ship = playerShip(); if (ship != null) toggleGroup(selectedGroup(ship)); }
            case "combat.previousGroup" -> cycleGroup(-1);
            case "combat.nextGroup" -> cycleGroup(1);
            case "tactical.engage" -> assignTarget(CombatAssignmentType.ENGAGE);
            case "tactical.avoid" -> assignTarget(CombatAssignmentType.AVOID);
            case "tactical.orderSelectedHere" -> orderSelected(nativeUi.captureSelectedOrder(uiRoot()));
            case "tactical.fullAssault", "tactical.searchDestroy", "tactical.fleetOrders" -> { if (combat() != null) ordinaryUiAction.accept("tactical.fleetOrders"); }
            case "tactical.retreatShip" -> retreatPlayerShip();
            case "tactical.fullRetreat" -> { CombatTaskManagerAPI manager = taskManager(); if (manager != null && !manager.isPreventFullRetreat() && !manager.isInFullRetreat()) ordinaryUiAction.accept("tactical.fullRetreat"); }
            case "tactical.cancelAssignment" -> { if (combat() != null) ordinaryUiAction.accept("tactical.cancelAssignment"); }
            case "combat.autopilot", "combat.tactical", "tactical.deployment", "game.menu", "game.codex", "game.settings", "ui.confirm", "ui.cancel", "ui.secondary", "ui.nextTab", "ui.previousTab", "ui.keyboard" -> ordinaryUiAction.accept(action);
            default -> { return false; }
        }
        return true;
    }

    public List<GameAction> actions(String catalog) {
        return switch (Objects.requireNonNullElse(catalog, "hub")) {
            case "abilities" -> abilityActions();
            case "weapons" -> weaponActions();
            case "tactical" -> tacticalActions();
            case "combat" -> combatActions();
            case "item", "ui" -> uiActions();
            default -> hubActions();
        };
    }

    private List<GameAction> hubActions() {
        if (Global.getCurrentState() == GameState.COMBAT) return context().is("TACTICAL") ? tacticalActions() : combatActions();
        if (Global.getCurrentState() != GameState.CAMPAIGN) return uiActions();
        Set<CoreUITabId> tabs = availableCoreTabs();
        String unavailable = "This native tab is unavailable, or an operation/dialog owns input";
        return List.of(action("campaign.map", "Map", "Open the sector map.", tabs.contains(CoreUITabId.MAP), unavailable, false),
                action("campaign.intel", "Intel", "Open intel, missions and contacts.", tabs.contains(CoreUITabId.INTEL), unavailable, false),
                action("campaign.fleet", "Fleet", "Open your fleet screen.", tabs.contains(CoreUITabId.FLEET), unavailable, false),
                action("campaign.refit", "Refit", "Open the normal ship refit screen.", tabs.contains(CoreUITabId.REFIT), unavailable, false),
                action("campaign.cargo", "Cargo", "Open your cargo hold.", tabs.contains(CoreUITabId.CARGO), unavailable, false),
                action("campaign.character", "Character", "Open skills and character details.", tabs.contains(CoreUITabId.CHARACTER), unavailable, false),
                action("campaign.colonies", "Colonies", "Open colony and outpost management.", tabs.contains(CoreUITabId.OUTPOSTS), unavailable, false),
                action("game.menu", "Game menu", "Open the original save, load and settings menu.", true, "", false));
    }

    private List<GameAction> uiActions() {
        return List.of(action("ui.confirm", "Select", "Primary click at the pointer.", true, "", false),
                action("ui.secondary", "Secondary", "Secondary click at the pointer.", true, "", false),
                action("ui.keyboard", "Keyboard", "Open controller text entry for the focused field.", true, "", false),
                action("ui.previousTab", "Previous tab", "Use the normal previous-tab input.", true, "", false),
                action("ui.nextTab", "Next tab", "Use the normal next-tab input.", true, "", false),
                action("ui.cancel", "Back", "Use the normal back input.", true, "", false),
                action("game.menu", "Game menu", "Open the original game menu.", true, "", false));
    }

    private List<GameAction> combatActions() {
        ShipAPI ship = playerShip();
        boolean alive = combatControlPossible();
        String unavailable = "Take control of an active ship first";
        boolean system = alive && ship.getSystem() != null && ship.getSystem().canBeActivated();
        return List.of(action("combat.target", "Target at aim", "Select the visible hostile ship nearest your aiming point.", alive, unavailable, false),
                action("combat.nextTarget", "Next target", "Cycle visible hostile ships by distance from your ship.", alive, unavailable, false),
                action("combat.targetLock", targetLocked ? "Unlock aim" : "Lock aim", "Track your selected visible target with aim and facing.", alive, unavailable, false),
                action("combat.precisionTarget", isPrecisionTargeting() ? "Exit precise aim" : "Precision target", "Move an exact world target with the right stick, within your configured aim range. Select this action again to return to directional aim.", alive && context().is("COMBAT") && !isAutopilotOn(), "Return to manual piloting first", false),
                action("combat.vent", "Vent flux", "Issue the standard vent-flux command.", alive, unavailable, false),
                action("combat.system", ship != null && ship.getSystem() != null ? ship.getSystem().getDisplayName() : "Ship system", "Activate the ship system when available.", system, "System unavailable, cooling down, or out of charges", false),
                action("combat.fighters", "Fighters", "Toggle the normal fighter regroup command.", alive && !ship.getAllWings().isEmpty(), "This ship has no fighter wings", false),
                action("combat.autopilot", "Autopilot", "Toggle the normal player autopilot control.", alive, unavailable, false),
                action("combat.tactical", "Tactical map", "Open the normal tactical command interface.", combat() != null, "No active battle", false),
                action("game.menu", "Game menu", "Open the original battle menu.", true, "", false));
    }

    private List<GameAction> weaponActions() {
        ShipAPI ship = playerShip();
        if (ship == null) return List.of(action("combat.nextGroup", "No weapon groups", "Take control of an active ship first.", false, "No active ship", false));
        List<GameAction> actions = new ArrayList<>();
        List<WeaponGroupAPI> groups = ship.getWeaponGroupsCopy();
        for (int i = 0; i < groups.size(); i++) {
            WeaponGroupAPI group = groups.get(i);
            String weapons = String.join(", ", group.getWeaponsCopy().stream().map(w -> w.getDisplayName()).distinct().limit(3).toList());
            boolean enabled = combatControlPossible() && !group.getWeaponsCopy().isEmpty();
            String state = (i == selectedGroup(ship) ? "Selected. " : "") + (group.isAutofiring() ? "Autofire on. " : "Autofire off. ");
            actions.add(action("weapon.select:" + i, "Group " + (i + 1), state + weapons, enabled, "This group is empty", false));
            actions.add(action("weapon.autofire:" + i, "Auto " + (i + 1), "Toggle autofire for group " + (i + 1) + ". " + weapons, enabled, "This group is empty", false));
        }
        if (actions.isEmpty()) actions.add(action("combat.nextGroup", "No weapon groups", "This ship has no weapon groups.", false, "No weapons", false));
        return List.copyOf(actions);
    }

    private List<GameAction> abilityActions() {
        CampaignFleetAPI fleet = fleet();
        if (fleet == null) return List.of(action("campaign.abilities", "No abilities", "Load a campaign to use fleet abilities.", false, "No player fleet", false));
        List<AbilityPlugin> abilities = new ArrayList<>(fleet.getAbilities().values());
        abilities.sort(Comparator.comparingInt((AbilityPlugin a) -> a.getSpec().getSortOrder()).thenComparing(a -> a.getSpec().getName()));
        List<GameAction> result = new ArrayList<>();
        for (AbilityPlugin ability : abilities) {
            boolean usable = context().is("CAMPAIGN") && ability.isUsable();
            String description = ability.isActiveOrInProgress() ? "Currently active. Press its normal ability button." : "Press this fleet ability's normal button.";
            String unavailable = ability.isOnCooldown() ? "Ability is cooling down" : "Ability cannot be used in the current situation";
            result.add(action("ability:" + ability.getId(), ability.getSpec().getName(), description, usable, unavailable, false));
        }
        return List.copyOf(result);
    }

    private List<GameAction> tacticalActions() {
        CombatTaskManagerAPI manager = taskManager();
        boolean valid = manager != null && !manager.isInFullRetreat();
        boolean assignmentAllowed = valid && nativeUi.canCreateAssignments(manager);
        ShipAPI ship = playerShip();
        boolean target = ship != null && validHostile(ship, ship.getShipTarget());
        DeployedFleetMemberAPI own = deployedPlayer();
        boolean retreatAllowed = valid && own != null && own.canBeGivenRetreatOrders()
                && (nativeUi.canIssueDirectOrders(manager) || nativeUi.isRetreatFree(manager, own));
        NativeGameUi.SelectedOrder selected = context().is("TACTICAL") ? nativeUi.captureSelectedOrder(uiRoot()) : null;
        boolean selectedAllowed = valid && selected != null && nativeUi.canIssueDirectOrders(manager);
        String identity = context().identity();
        GameAction selectedAction = new GameAction("tactical.orderSelectedHere", "Order selected here",
                "Give the selected ships the normal move/escort/attack order at the map point where this wheel opened. Native command costs and permissions apply.",
                selectedAllowed, selected == null ? "Select your ships on the tactical map, then point at a location or icon" : "Native direct orders are unavailable",
                false, () -> { if (selectedAllowed && identity.equals(context().identity())) orderSelected(selected); });
        return List.of(selectedAction,
                action("tactical.deployment", "Deployment", "Open the normal deployment dialog.", combat() != null, "No active battle", false),
                action("tactical.engage", "Engage target", "Add a fleet engage objective on your piloted ship's current hostile target. Native command costs apply; the AI assigns units.", assignmentAllowed && target, assignmentAllowed ? "Select a visible hostile target" : "Native assignments are unavailable", false),
                action("tactical.avoid", "Avoid target", "Add a fleet avoid objective on your piloted ship's current hostile target. Native command costs apply.", assignmentAllowed && target, assignmentAllowed ? "Select a visible hostile target" : "Native assignments are unavailable", false),
                action("tactical.fleetOrders", "Fleet orders", "Open the original search-and-destroy and full-assault dialog.", valid, "Orders are unavailable during full retreat", false),
                action("tactical.cancelAssignment", "Cancel order", "Use the native cancel-assignment control for the tactical map's current selection.", valid && context().is("TACTICAL"), "Open the tactical map and select an assignment", false),
                action("tactical.retreatShip", "Retreat ship", "Order your currently piloted ship to retreat using the native command-cost and free-retreat rules.", retreatAllowed, "The piloted ship cannot retreat or native orders are unavailable", true),
                action("tactical.fullRetreat", "Full retreat", "Open the original full-retreat confirmation. A confirmed full retreat cannot be cancelled.", valid && !manager.isPreventFullRetreat(), "Full retreat is unavailable", false),
                action("combat.tactical", "Tactical map", "Open or close the original tactical map.", combat() != null, "No active battle", false));
    }

    private GameAction action(String id, String label, String description, boolean enabled, String reason, boolean dangerous) {
        String identity = context().identity();
        return new GameAction(id, label, description, enabled, reason, dangerous, () -> {
            // A wheel must never act on a new ship, new encounter, or replacement modal.
            if (enabled && identity.equals(context().identity())) execute(id);
        });
    }

    private void moveCampaign(float x, float y) {
        CampaignFleetAPI fleet = fleet();
        if (fleet == null || fleet.isInHyperspaceTransition()) { stopCampaignMovement(); return; }
        float length = (float) Math.hypot(x, y);
        if (length <= .01f) { stopCampaignMovement(); return; }
        if (movedFleet != fleet) {
            stopCampaignMovement();
            campaignUI().clearLaidInCourse();
            movedFleet = fleet;
        }
        float desiredSpeed = Math.max(0f, fleet.getTravelSpeed()) * Math.min(1f, length);
        float distance = Math.max(8f, desiredSpeed * desiredSpeed / (2f * Math.max(1f, fleet.getAcceleration())) + desiredSpeed * .08f);
        Vector2f loc = fleet.getLocation();
        campaignUI().setFollowingDirectCommand(true);
        fleet.setMoveDestinationOverride(loc.x + x / length * distance, loc.y + y / length * distance);
    }

    private void stopCampaignMovement() {
        CampaignFleetAPI previous = movedFleet;
        movedFleet = null;
        if (previous != null && previous == fleet()) {
            Vector2f loc = previous.getLocation();
            previous.setMoveDestinationOverride(loc.x, loc.y);
            campaignUI().setFollowingDirectCommand(false);
        }
    }

    private void interact() {
        if (!context().is("CAMPAIGN")) return;
        SectorEntityToken target = validCampaignTarget(selectedCampaignTarget) ? selectedCampaignTarget : Global.getSector().getMousedOverEntity();
        if (validCampaignTarget(target)) {
            stopCampaignMovement();
            campaignUI().layInCourseForNextStep(target);
        } else ordinaryUiAction.accept("campaign.interact");
    }

    private void cycleCampaignTarget(int direction) {
        if (!context().is("CAMPAIGN") || fleet() == null) return;
        List<SectorEntityToken> entities = new ArrayList<>(Global.getSector().getCurrentLocation().getAllEntities());
        entities.removeIf(e -> !validCampaignTarget(e));
        Vector2f here = fleet().getLocation();
        entities.sort(Comparator.comparingDouble(e -> distanceSquared(e.getLocation(), here)));
        if (entities.isEmpty()) { selectedCampaignTarget = null; return; }
        int index = entities.indexOf(selectedCampaignTarget);
        selectedCampaignTarget = entities.get(index < 0 ? (direction > 0 ? 0 : entities.size() - 1) : Math.floorMod(index + direction, entities.size()));
        campaignUI().addMessage("SectorPad target: " + selectedCampaignTarget.getName());
    }

    private boolean validCampaignTarget(SectorEntityToken target) {
        CampaignFleetAPI fleet = fleet();
        return target != null && fleet != null && target != fleet && target.getContainingLocation() == fleet.getContainingLocation()
                && target.getName() != null && !target.getName().isBlank() && target.isVisibleToPlayerFleet();
    }

    private void cancelCourse() { if (context().is("CAMPAIGN")) { stopCampaignMovement(); campaignUI().clearLaidInCourse(); selectedCampaignTarget = null; } }
    private void zoomCampaign(int direction) {
        if (!context().is("CAMPAIGN")) return;
        CampaignUIAPI ui = campaignUI();
        float next = ui.getZoomFactor() * (direction > 0 ? .9f : 1.1f);
        ui.setZoomFactor(Math.max(ui.getMinZoomFactor(), Math.min(ui.getMaxZoomFactor(), next)));
    }
    private void zoomCampaign(int direction, float seconds) {
        if (!context().is("CAMPAIGN") || !Float.isFinite(seconds) || seconds <= 0) return;
        CampaignUIAPI ui = campaignUI();
        float next = ui.getZoomFactor() * (float) Math.exp(-direction * campaignZoomSpeed * .1f * Math.min(.05f, seconds));
        ui.setZoomFactor(Math.max(ui.getMinZoomFactor(), Math.min(ui.getMaxZoomFactor(), next)));
    }
    private void toggleFastForward() {
        if (!context().is("CAMPAIGN") || context().paused()) return;
        if (nativeFastForwardToggle.getAsBoolean()) {
            releaseFastForward();
            ordinaryUiAction.accept("campaign.fastForward.toggle");
        } else {
            fastForwardOwned = !fastForwardOwned;
            ordinaryUiAction.accept(fastForwardOwned ? "campaign.fastForward.down" : "campaign.fastForward.up");
        }
    }
    private void releaseFastForward() {
        if (fastForwardOwned) ordinaryUiAction.accept("campaign.fastForward.up");
        fastForwardOwned = false;
    }
    private void openTab(CoreUITabId tab) { if (availableCoreTabs().contains(tab)) { stopCampaignMovement(); campaignUI().showCoreUITab(tab); } }
    private Set<CoreUITabId> availableCoreTabs() {
        CampaignUIAPI ui = campaignUI();
        if (ui == null) return Set.of();
        NativeGameUi.CoreState core = nativeUi.coreState(ui, uiRoot());
        if (!canOpenCore(ui, core)) return Set.of();
        return core.owner() == null ? EnumSet.allOf(CoreUITabId.class) : core.enabledTabs();
    }
    /** Known native cargo pickups and refit pickers; does not inspect private placement state. */
    public boolean nativeOperationActive() { return nativeUi.operationActive(uiRoot()); }

    /** Existing visible core owner, no nested modal/pickup, and a selectable native tab. */
    public boolean canChangeCoreTab() {
        CampaignUIAPI ui = campaignUI();
        if (ui == null || ui.getCurrentCoreTab() == null) return false;
        NativeGameUi.CoreState core = nativeUi.coreState(ui, uiRoot());
        return canOpenCore(ui, core) && core.owner() != null && core.enabledTabs().stream().anyMatch(tab -> tab != ui.getCurrentCoreTab());
    }
    public boolean cycleCoreTab(int direction) {
        CampaignUIAPI ui = campaignUI();
        if (direction == 0 || ui == null || ui.getCurrentCoreTab() == null) return false;
        NativeGameUi.CoreState core = nativeUi.coreState(ui, uiRoot());
        if (core.owner() == null || !canOpenCore(ui, core)) return false;
        List<CoreUITabId> tabs = List.of(CoreUITabId.CHARACTER, CoreUITabId.FLEET, CoreUITabId.REFIT,
                CoreUITabId.CARGO, CoreUITabId.MAP, CoreUITabId.INTEL, CoreUITabId.OUTPOSTS);
        int index = tabs.indexOf(ui.getCurrentCoreTab());
        if (index < 0) return false;
        for (int step = 1; step < tabs.size(); step++) {
            CoreUITabId next = tabs.get(Math.floorMod(index + Integer.signum(direction) * step, tabs.size()));
            if (core.enabledTabs().contains(next)) { ui.showCoreUITab(next); return true; }
        }
        return false;
    }
    private boolean canOpenCore(CampaignUIAPI ui, NativeGameUi.CoreState core) {
        if (ui == null || ui.isShowingMenu()
                || ui.isShowingDialog() && ui.getCurrentCoreTab() == null) return false;
        Object state = StateAccess.currentState();
        if (StateAccess.flag(state, "isShowingCodex")) return false;
        if (ui.getCurrentInteractionDialog() != null && (core.owner() == null || ui.getCurrentCoreTab() == null)) return false;
        if (nativeOperationActive()) return false;
        // The core itself may be a native dialog; a nested picker or confirmation owns input.
        return core.modal() == null || core.modal() == core.owner();
    }

    private void pressAbility(String id) {
        if (!context().is("CAMPAIGN") || fleet() == null) return;
        AbilityPlugin ability = fleet().getAbility(id);
        if (ability != null && ability.isUsable()) ability.pressButton();
    }

    private void cycleCombatTarget() {
        ShipAPI ship = playerShip();
        if (!combatControlPossible()) return;
        List<ShipAPI> targets = new ArrayList<>(combat().getShips());
        targets.removeIf(other -> !validHostile(ship, other));
        targets.sort(Comparator.comparingDouble(other -> distanceSquared(ship.getLocation(), other.getLocation())));
        if (targets.isEmpty()) { ship.setShipTarget(null); targetLocked = false; return; }
        ship.setShipTarget(targets.get(Math.floorMod(targets.indexOf(ship.getShipTarget()) + 1, targets.size())));
    }

    private void selectCombatTargetAtAim() {
        ShipAPI ship = playerShip();
        if (!combatControlPossible()) return;
        Vector2f point = aimPoint(ship);
        ShipAPI nearest = combat().getShips().stream().filter(target -> validHostile(ship, target))
                .min(Comparator.comparingDouble(target -> distanceSquared(point, target.getLocation()))).orElse(null);
        ship.setShipTarget(nearest);
        if (nearest == null) targetLocked = false;
    }

    private void togglePrecisionTarget() {
        if (!combatControlPossible() || !context().is("COMBAT") || isAutopilotOn()) return;
        if (isPrecisionTargeting()) { precisionOwner = null; precisionPoint = null; }
        else {
            precisionPoint = aimPoint(playerShip());
            precisionOwner = playerShip();
            lastAimPoint = new Vector2f(precisionPoint);
            targetLocked = false;
        }
    }

    private boolean validHostile(ShipAPI ship, ShipAPI target) {
        CombatEngineAPI engine = combat();
        return engine != null && ship != null && target != null && target != ship && target.isAlive() && !target.isHulk()
                && target.getOwner() != 100 && target.getOwner() != ship.getOwner() && target.isTargetable()
                && engine.isEntityInPlay(target) && (engine.getFogOfWar(ship.getOwner()) == null || engine.getFogOfWar(ship.getOwner()).isVisible(target));
    }

    private Vector2f aimPoint(ShipAPI ship) {
        if (precisionOwner == ship && precisionPoint != null) return new Vector2f(precisionPoint);
        float angle = Float.isFinite(aimAngle) ? aimAngle : ship.getFacing();
        float range = configuredAimRange;
        double radians = Math.toRadians(angle);
        return new Vector2f(ship.getLocation().x + (float) Math.cos(radians) * range,
                ship.getLocation().y + (float) Math.sin(radians) * range);
    }

    private void shipCommand(ShipCommand command) {
        ShipAPI ship = playerShip();
        if (combatControlPossible()) command(ship, command, aimPoint(ship), selectedGroup(ship));
    }
    private void selectGroup(int index) {
        ShipAPI ship = playerShip();
        if (combatControlPossible() && index >= 0 && index < ship.getWeaponGroupsCopy().size()) command(ship, ShipCommand.SELECT_GROUP, null, index);
    }
    private void toggleGroup(int index) {
        ShipAPI ship = playerShip();
        if (combatControlPossible() && index >= 0 && index < ship.getWeaponGroupsCopy().size()) command(ship, ShipCommand.TOGGLE_AUTOFIRE, null, index);
    }
    private void cycleGroup(int direction) {
        ShipAPI ship = playerShip();
        if (!combatControlPossible()) return;
        List<WeaponGroupAPI> groups = ship.getWeaponGroupsCopy();
        if (groups.isEmpty()) return;
        int current = selectedGroup(ship);
        for (int count = 1; count <= groups.size(); count++) {
            int next = Math.floorMod(current + direction * count, groups.size());
            if (!groups.get(next).getWeaponsCopy().isEmpty()) { selectGroup(next); return; }
        }
    }

    private void assignTarget(CombatAssignmentType type) {
        CombatTaskManagerAPI manager = taskManager();
        ShipAPI ship = playerShip();
        if (manager == null || manager.isInFullRetreat() || !nativeUi.canCreateAssignments(manager) || ship == null || !validHostile(ship, ship.getShipTarget())) return;
        ShipAPI target = ship.getShipTarget();
        DeployedFleetMemberAPI member = combat().getFleetManager(target.getOwner()).getDeployedFleetMember(target);
        if (member != null) manager.createAssignment(type, member, true);
    }
    private void retreatPlayerShip() {
        CombatTaskManagerAPI manager = taskManager();
        DeployedFleetMemberAPI member = deployedPlayer();
        if (manager != null && !manager.isInFullRetreat() && member != null && member.canBeGivenRetreatOrders()
                && (nativeUi.canIssueDirectOrders(manager) || nativeUi.isRetreatFree(manager, member))) manager.orderRetreat(member, true, true);
    }
    private void orderSelected(NativeGameUi.SelectedOrder captured) {
        CombatTaskManagerAPI manager = taskManager();
        if (captured == null || !gameFocused.getAsBoolean() || !context().is("TACTICAL") || manager == null
                || manager.isInFullRetreat() || !nativeUi.canIssueDirectOrders(manager) || nativeOperationActive()) return;
        nativeUi.orderSelected(captured, uiRoot());
    }
    private DeployedFleetMemberAPI deployedPlayer() {
        ShipAPI ship = playerShip();
        return ship == null ? null : combat().getFleetManager(ship.getOwner()).getDeployedFleetMember(ship);
    }
    private CombatTaskManagerAPI taskManager() {
        CombatEngineAPI engine = combat();
        ShipAPI ship = playerShip();
        return engine == null ? null : engine.getFleetManager(ship == null ? 0 : ship.getOwner()).getTaskManager(false);
    }

    /** Controller-owned transient holds only; never clear physical input or persistent game state. */
    public void neutralize() {
        releaseShield(); stopCampaignMovement(); releaseFastForward(); controlledShip = null;
        targetLocked = false; aimAngle = Float.NaN; movementOwned = false; lastAimPoint = null;
        if (precisionOwner != playerShip() || !context().is("COMBAT")) { precisionOwner = null; precisionPoint = null; }
    }
    /** Called when deliberate ordinary input takes ownership; the runtime must also rearm its pad gate. */
    public void yieldToNativeInput() {
        precisionOwner = null; precisionPoint = null;
        neutralize();
        externalPauseIntent();
    }
    private void releaseShield() {
        ShipAPI ship = shieldOwner;
        shieldOwner = null;
        if (ship == null || !ship.isAlive()) return;
        // Drop only a defense activated by this controller hold, including paused/focus-loss cleanup.
        if (ship.getShield() != null && ship.getShield().isOn()) ship.getShield().toggleOff();
        if (ship.getPhaseCloak() != null && ship.getPhaseCloak().isOn()) ship.getPhaseCloak().deactivate();
    }

    public PauseLease acquirePause() {
        Object owner = pauseTarget();
        String identity = context().identity();
        if (owner == null) return new PauseLease(-1);
        if (pauseDepth == 0 || pauseOwner != owner || !Objects.equals(pauseContext, identity)) {
            pauseGeneration++;
            pauseOwner = owner;
            pauseContext = identity;
            pauseWasAlreadySet = paused(owner);
            pauseExternalEpoch = externalPauseEpoch;
            resumeForbidden = false;
            pauseDepth = 0;
        }
        pauseDepth++;
        if (!paused(owner)) setPaused(owner, true);
        return new PauseLease(pauseGeneration);
    }

    public final class PauseLease implements AutoCloseable {
        private final long generation;
        private boolean closed;
        private PauseLease(long generation) { this.generation = generation; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (generation != pauseGeneration || pauseDepth == 0) return;
            if (--pauseDepth != 0) return;
            if (!pauseWasAlreadySet && !resumeForbidden && pauseExternalEpoch == externalPauseEpoch
                    && pauseOwner == pauseTarget() && Objects.equals(pauseContext, context().identity())
                    && !context().is("UI") && gameFocused.getAsBoolean()) setPaused(pauseOwner, false);
            pauseOwner = null;
            pauseContext = null;
        }
    }

    public void requestPause() { invalidatePauseResume(); Object target = pauseTarget(); if (target != null) setPaused(target, true); }
    public void invalidatePauseResume() { resumeForbidden = true; }
    public void externalPauseIntent() { invalidatePauseResume(); notifyExternalPauseIntent(); }
    public static void notifyExternalPauseIntent() { externalPauseEpoch++; }
    private void togglePause() { invalidatePauseResume(); Object target = pauseTarget(); if (target != null && !context().is("UI")) setPaused(target, !paused(target)); }
    private Object pauseTarget() { return Global.getCurrentState() == GameState.CAMPAIGN ? Global.getSector() : combat(); }
    private static boolean paused(Object target) { return target instanceof SectorAPI sector ? sector.isPaused() : target instanceof CombatEngineAPI engine && engine.isPaused(); }
    private static void setPaused(Object target, boolean paused) { if (target instanceof SectorAPI sector) sector.setPaused(paused); else if (target instanceof CombatEngineAPI engine) engine.setPaused(paused); }
    private CampaignFleetAPI fleet() { SectorAPI sector = Global.getCurrentState() == GameState.CAMPAIGN ? Global.getSector() : null; return sector == null ? null : sector.getPlayerFleet(); }
    private CampaignUIAPI campaignUI() { SectorAPI sector = Global.getCurrentState() == GameState.CAMPAIGN ? Global.getSector() : null; return sector == null ? null : sector.getCampaignUI(); }
    private CombatEngineAPI combat() { return Global.getCurrentState() == GameState.COMBAT ? Global.getCombatEngine() : null; }
    private ShipAPI playerShip() { CombatEngineAPI engine = combat(); return engine == null ? null : engine.getPlayerShip(); }
    private boolean combatControlPossible() { ShipAPI ship = playerShip(); return ship != null && ship.isAlive() && !ship.isHulk() && !ship.isShuttlePod() && !context().is("UI") && !context().is("DEPLOYMENT"); }
    private static int selectedGroup(ShipAPI ship) {
        WeaponGroupAPI selected = ship.getSelectedGroupAPI();
        return selected == null ? 0 : Math.max(0, ship.getWeaponGroupsCopy().indexOf(selected));
    }
    private static void command(ShipAPI ship, ShipCommand command, Object point, int group) { ship.giveCommand(command, point, group); }
    private static boolean shieldIsOn(ShipAPI ship) { return ship.getShield() != null && ship.getShield().isOn() || ship.getPhaseCloak() != null && ship.getPhaseCloak().isOn(); }
    private static boolean hasDefense(ShipAPI ship) { return ship.getShield() != null && ship.getShield().getType() != ShieldAPI.ShieldType.NONE || ship.getPhaseCloak() != null; }
    private static float finite(float value) { return Float.isFinite(value) ? Math.max(-1, Math.min(1, value)) : 0; }
    private static double distanceSquared(Vector2f a, Vector2f b) { double x = (double) a.x - b.x, y = (double) a.y - b.y; return x * x + y * y; }
    private static int parseIndex(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException invalid) { return -1; } }
}
