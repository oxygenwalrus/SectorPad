package sectorpad.game;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/** API contract test doubles, not a running-game or hardware certification. */
public final class GameIntegrationTests {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .001f, message + ": " + actual); }

    public static void main(String[] args) {
        near(SteeringMath.angleError(2, 358), 4, "heading wraps across zero");
        near(SteeringMath.angleError(358, 2), -4, "heading wraps in reverse");
        near(SteeringMath.local(1, 0, 90).left(), -1, "world right is local right when facing up");
        near(SteeringMath.local(0, 1, 90).forward(), 1, "world up is forward when facing up");
        check(SteeringMath.turn(10, 0, 50, 50, .016f) < 0, "counter-thrust before heading overshoot");
        check(SteeringMath.turn(0, 0, 0, 50, .016f) == 0, "no heading jitter when aligned");
        near(SteeringMath.local(Float.NaN, 1, 0).forward(), 0, "non-finite movement rejected");
        testShipCommands();
        testPrecisionTargeting();
        testDefenseOwnership();
        testPauseOwnership();
        testCampaignActions();
        testNativeCommandRules();
        testEncounterCoreNavigation();
        testEncounterMapContext();
        testSelectedTacticalOrders();
        try { NativeGameUi.verifySignatures(); check(true, "native public command, core, cargo, and warroom signatures resolve"); }
        catch (ReflectiveOperationException unavailable) { throw new AssertionError("Installed native command signatures changed", unavailable); }
        Global.setSettings(null); Global.setCombatEngine(null); Global.setSector(null);
        System.out.println("GameIntegrationTests: " + checks + " checks passed (API doubles; no game launch)");
    }

    private static void testShipCommands() {
        Fixture f = new Fixture();
        f.install();
        GameActions game = new GameActions(f.uiActions::add, () -> true);
        game.dispatch(Set.of(), Set.of(), Set.of(), .25f, 0, 0, 0, .016f);
        check(f.has(ShipCommand.ACCELERATE), "partial stick accelerates towards requested velocity");
        f.commands.clear(); f.velocity.set(25, 0);
        game.dispatch(Set.of(), Set.of(), Set.of(), .25f, 0, 0, 0, .016f);
        check(!f.has(ShipCommand.ACCELERATE) && !f.has(ShipCommand.ACCELERATE_BACKWARDS), "partial stick stops thrust at requested speed");
        f.commands.clear(); f.velocity.set(45, 0);
        game.dispatch(Set.of(), Set.of(), Set.of(), .25f, 0, 0, 0, .016f);
        check(f.has(ShipCommand.ACCELERATE_BACKWARDS), "partial stick brakes an excess forward velocity");
        f.commands.clear();
        game.dispatch(Set.of(), Set.of(), Set.of(), 0, 0, 0, 0, .016f);
        check(f.has(ShipCommand.DECELERATE), "stick release decelerates controller-owned travel");
        game.neutralize(); f.commands.clear(); f.disableControls = 0;
        game.dispatch(Set.of(), Set.of(), Set.of(), 0, 0, 0, 0, .016f);
        check(f.commands.isEmpty() && f.disableControls == 0, "neutral ownership leaves normal keyboard controls available");
        game.configure("Directional", false, 700);
        game.dispatch(Set.of(), Set.of(), Set.of(), 0, 1, 1, 0, .016f);
        check(f.has(ShipCommand.TURN_LEFT), "directional preset steers the hull from left stick");
        near(game.getAimPoint().x, 700, "aim range is configurable");
        near(game.getAimPoint().y, 0, "directional preset retains independent right-stick aim");
        game.neutralize(); f.commands.clear(); f.velocity.set(0, 0); f.target = f.enemy;
        game.configure("Orbital", false, 1000);
        game.dispatch(Set.of(), Set.of(), Set.of(), 1, 0, 0, 0, .016f);
        check(f.has(ShipCommand.STRAFE_RIGHT), "orbital preset turns horizontal input into tangent travel");
        f.commands.clear(); f.autopilot = true;
        game.dispatch(Set.of("combat.fire"), Set.of(), Set.of(), 1, 0, 1, 0, .016f);
        check(f.commands.isEmpty(), "controller thrust and fire do not fight autopilot");
        f.autopilot = false; f.visible = false;
        game.execute("combat.target");
        check(f.target == null, "target selection never reveals hidden enemy ships");
        f.visible = true;
        game.execute("combat.target");
        check(f.target == f.enemy, "visible hostile can be selected");
        GameAction captured = game.actions("combat").get(0);
        f.activeShip = null;
        captured.execute.run();
        check(f.target == f.enemy, "stale wheel does not act on a replacement player ship");
        check(game.context().is("UI"), "spectating restores pointer and native menu bindings");
    }

    private static void testDefenseOwnership() {
        Fixture f = new Fixture(); f.install();
        GameActions game = new GameActions(f.uiActions::add, () -> true);
        game.dispatch(Set.of("combat.shield"), Set.of("combat.shield"), Set.of(), 0, 0, 0, 0, .016f);
        check(f.shieldOn, "hold press raises defense");
        game.dispatch(Set.of(), Set.of(), Set.of("combat.shield"), 0, 0, 0, 0, .016f);
        check(!f.shieldOn, "hold release lowers controller-owned defense");
        f.shieldOn = true;
        game.dispatch(Set.of("combat.shield"), Set.of("combat.shield"), Set.of(), 0, 0, 0, 0, .016f);
        game.neutralize();
        check(f.shieldOn, "neutralizing does not lower a pre-existing shield");
        f.shieldOn = false; game.configure("TwinStick", true, 1000);
        game.dispatch(Set.of("combat.shield"), Set.of("combat.shield"), Set.of(), 0, 0, 0, 0, .016f);
        game.dispatch(Set.of(), Set.of(), Set.of("combat.shield"), 0, 0, 0, 0, .016f);
        check(f.shieldOn, "toggle preset retains raised defense after release");
        game.neutralize();
        check(f.shieldOn, "toggle defense is not treated as a transient hold");
    }

    private static void testPrecisionTargeting() {
        Fixture f = new Fixture(); f.install();
        GameActions game = new GameActions(f.uiActions::add, () -> true);
        game.configure("TwinStick", false, 1000);
        game.execute("combat.precisionTarget");
        check(game.isPrecisionTargeting(), "precision aim is reachable through a game action");
        near(game.getAimPoint().x, 1000, "precision aim starts at the current aiming point");
        game.dispatch(Set.of(), Set.of(), Set.of(), 0, 0, -1, 0, .05f);
        near(game.getAimPoint().x, 962.5f, "precision stick moves an exact world point");
        game.execute("combat.system");
        Command command = f.commands.stream().filter(c -> c.type() == ShipCommand.USE_SYSTEM).findFirst().orElseThrow();
        near(((Vector2f) command.point()).x, 962.5f, "normal system command receives precise target point");
        game.neutralize();
        check(game.isPrecisionTargeting(), "opening a mod-owned overlay retains the selected aiming mode");
        game.execute("combat.precisionTarget");
        check(!game.isPrecisionTargeting(), "precision aim exits through its normal toggle action");
        game.execute("combat.precisionTarget");
        game.yieldToNativeInput(); f.disableControls = 0; f.commands.clear();
        game.dispatch(Set.of(), Set.of(), Set.of(), 0, 0, 0, 0, .016f);
        check(!game.isPrecisionTargeting() && f.disableControls == 0 && f.commands.isEmpty(), "native input takeover releases precise aim and piloting ownership");
        game.execute("combat.precisionTarget");
        f.commandUi = true; game.neutralize();
        check(!game.isPrecisionTargeting(), "native tactical transition clears precision input ownership");
        f.commandUi = false;
        ShipAPI farEnemy = proxy(ShipAPI.class, call -> switch(call.name()) {
            case "isAlive", "isTargetable" -> true;
            case "getOwner" -> 1;
            case "getLocation" -> new Vector2f(900, 0);
            default -> null;
        });
        f.ships = List.of(f.ship, f.enemy, farEnemy);
        game.execute("combat.target");
        check(f.target == farEnemy, "direct target chooses nearest reticle contact rather than nearest own-ship contact");
        game.execute("combat.nextTarget");
        check(f.target == f.enemy, "separate next-target action retains distance cycling");
    }

    private static void testPauseOwnership() {
        Fixture f = new Fixture(); f.install();
        GameActions game = new GameActions(f.uiActions::add, () -> true);
        var first = game.acquirePause();
        check(f.paused, "wheel acquires pause");
        var nested = game.acquirePause(); first.close();
        check(f.paused, "closing outer lease keeps nested modal paused");
        nested.close();
        check(!f.paused, "last owned lease restores running state");
        f.paused = true; game.acquirePause().close();
        check(f.paused, "pre-existing pause survives wheel close");
        f.paused = false; var external = game.acquirePause(); GameActions.notifyExternalPauseIntent(); external.close();
        check(f.paused, "independent native input forfeits automatic resume");
        f.paused = false; var disconnected = game.acquirePause(); game.requestPause(); disconnected.close();
        check(f.paused, "disconnect pause survives modal cleanup");
        f.paused = false; var oldShip = game.acquirePause(); f.activeShip = null; oldShip.close();
        check(f.paused, "changing player ship forfeits auto-resume");
        f.activeShip = f.ship; f.paused = false;
        GameActions unfocused = new GameActions(f.uiActions::add, () -> false);
        unfocused.acquirePause().close();
        check(f.paused, "focus loss never auto-resumes");
    }

    private static void testCampaignActions() {
        Fixture f = new Fixture(); f.state = GameState.CAMPAIGN; f.install();
        GameActions game = new GameActions(f.uiActions::add, () -> true, () -> false);
        game.dispatch(Set.of(), Set.of(), Set.of(), .5f, 0, 0, 0, .016f);
        check(f.destination != null && f.destination.x > 0 && f.followingDirect, "campaign movement uses public destination override");
        game.neutralize();
        near(f.destination.x, 0, "campaign movement stops at neutral");
        check(!f.followingDirect, "neutral clears controller-owned direct course state");
        game.execute("campaign.fleet");
        check(f.tab == CoreUITabId.FLEET, "fleet action opens normal core tab");
        f.dialogShowing = true; f.tab = null;
        game.dispatch(Set.of(), Set.of("campaign.fleet"), Set.of(), 1, 0, 0, 0, .016f);
        check(f.tab == null, "gameplay dispatch does not switch a modal's tab");
        game.execute("campaign.refit");
        check(f.tab == null, "direct core actions preserve a native dialog");
        check(game.actions("hub").stream().filter(a -> a.id.equals("campaign.refit")).noneMatch(a -> a.enabled),
                "hub explains unavailable core actions while a dialog owns input");
        f.dialogShowing = false;
        f.zoom = 1;
        game.configureCampaign(5);
        game.dispatch(Set.of("campaign.zoomIn"), Set.of("campaign.zoomIn"), Set.of(), 0, 0, 0, 0, .05f);
        float firstZoom = f.zoom;
        game.dispatch(Set.of("campaign.zoomIn"), Set.of(), Set.of(), 0, 0, 0, 0, .05f);
        check(f.zoom < firstZoom && firstZoom < 1f, "campaign zoom continues while held");
        float releasedZoom = f.zoom;
        game.dispatch(Set.of(), Set.of(), Set.of("campaign.zoomIn"), 0, 0, 0, 0, .05f);
        near(f.zoom, releasedZoom, "campaign zoom stops immediately on release");
        game.execute("campaign.recenter");
        check(f.recentered == 1, "campaign recenter uses normal camera reset");
        game.execute("campaign.fastForward");
        check(f.uiActions.contains("campaign.fastForward.down"), "native hold mode becomes a controller toggle");
        game.neutralize();
        check(f.uiActions.get(f.uiActions.size() - 1).equals("campaign.fastForward.up"), "neutralization releases owned fast-forward key");
        GameActions nativeToggle = new GameActions(f.uiActions::add, () -> true, () -> true);
        nativeToggle.execute("campaign.fastForward");
        check(f.uiActions.get(f.uiActions.size() - 1).equals("campaign.fastForward.toggle"), "native toggle mode receives one normal key tap");
        int nativeTaps = f.uiActions.size(); nativeToggle.neutralize();
        check(f.uiActions.size() == nativeTaps, "native toggled state is not a mod-owned held key");
        game.execute("ability:test");
        check(f.abilityPresses == 1, "ability invokes its own normal button path");
        f.abilityUsable = false; game.execute("ability:test");
        check(f.abilityPresses == 1, "disabled ability is not activated");
    }

    private static void testNativeCommandRules() {
        Fixture f = new Fixture(); f.install(); f.commandUi = true; f.target = f.enemy;
        NativeFixture nativeUi = new NativeFixture(f.member);
        GameActions game = new GameActions(f.uiActions::add, () -> true, () -> false, nativeUi);
        f.commandPoints = 0;
        check(game.actions("tactical").stream().filter(a -> a.id.equals("tactical.engage")).allMatch(a -> a.enabled),
                "zero command points does not override native assignment permission during an open channel");
        game.execute("tactical.engage");
        check(f.assignments == 1 && f.assignmentUsesCost, "native assignment entry point owns command charging");
        nativeUi.create = false; f.commandPoints = 10;
        game.execute("tactical.engage");
        check(f.assignments == 1, "positive command points does not bypass a native assignment rejection");
        nativeUi.direct = true; f.commandPoints = 0;
        game.execute("tactical.retreatShip");
        check(f.retreats == 1 && f.retreatUsesCost, "open-channel retreat uses the normal charging path at zero command points");
        nativeUi.direct = false; nativeUi.freeRetreat = false; f.commandPoints = 10;
        game.execute("tactical.retreatShip");
        check(f.retreats == 1, "retreat honors native direct-order rejection despite positive points");
        nativeUi.freeRetreat = true; f.commandPoints = 0;
        game.execute("tactical.retreatShip");
        check(f.retreats == 2, "native free-retreat allowance remains available without a command point");
        f.fullRetreat = true; nativeUi.create = true; nativeUi.direct = true;
        game.execute("tactical.engage"); game.execute("tactical.retreatShip");
        check(f.assignments == 1 && f.retreats == 2, "full retreat blocks new conflicting controller orders");
    }

    private static void testEncounterCoreNavigation() {
        Fixture f = new Fixture(); f.state = GameState.CAMPAIGN; f.install();
        f.dialogShowing = true; f.dialog = proxy(InteractionDialogAPI.class, call -> null); f.tab = CoreUITabId.CARGO;
        NativeFixture nativeUi = new NativeFixture(f.member);
        nativeUi.core = new Object(); nativeUi.modal = nativeUi.core;
        nativeUi.tabs = Set.of(CoreUITabId.CARGO, CoreUITabId.INTEL);
        GameActions game = new GameActions(f.uiActions::add, () -> true, () -> false, nativeUi);
        check(game.canChangeCoreTab(), "an encounter-owned core permits normal tab navigation");
        check(game.cycleCoreTab(1) && f.tab == CoreUITabId.INTEL, "encounter navigation skips disabled native tabs");
        check(game.cycleCoreTab(-1) && f.tab == CoreUITabId.CARGO, "reverse navigation preserves encounter core ownership");
        game.execute("campaign.refit");
        check(f.tab == CoreUITabId.CARGO, "direct hub commands cannot open a disabled native tab");
        nativeUi.modal = new Object();
        check(!game.canChangeCoreTab() && !game.cycleCoreTab(1), "nested native dialogs block tab switching");
        game.execute("campaign.intel");
        check(f.tab == CoreUITabId.CARGO, "direct hub navigation also preserves a nested dialog");
        nativeUi.modal = nativeUi.core; nativeUi.operation = true;
        check(game.nativeOperationActive() && !game.canChangeCoreTab() && !game.cycleCoreTab(1),
                "an active native pickup blocks cycling before the core UI can be replaced");
        nativeUi.operation = false; nativeUi.tabs = Set.of(CoreUITabId.CARGO);
        check(!game.canChangeCoreTab() && !game.cycleCoreTab(1), "one enabled current tab produces no false successful navigation");
        nativeUi.core = null; nativeUi.modal = null; nativeUi.tabs = Set.of(CoreUITabId.INTEL);
        check(!game.canChangeCoreTab(), "an interaction without a native core stays an interaction");
        f.dialog = null; f.dialogShowing = false; f.tab = null;
        check(!game.cycleCoreTab(1), "a closed core is not opened by a tab-cycle request");
    }

    private static void testSelectedTacticalOrders() {
        Fixture f = new Fixture(); f.install(); f.commandUi = true;
        NativeFixture nativeUi = new NativeFixture(f.member);
        GameActions game = new GameActions(f.uiActions::add, () -> true, () -> false, nativeUi);
        GameAction captured = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        check(captured.enabled, "selected-unit orders have a discoverable tactical wheel entry");
        check(game.actions("hub").stream().anyMatch(a -> a.id.equals("tactical.orderSelectedHere")), "the tactical hub exposes the same selected-unit order path");
        nativeUi.pointer.set(400, 400); nativeUi.pan = 20;
        captured.execute.run();
        check(nativeUi.orders == 1, "the original selected-unit handler receives one accepted order");
        near(nativeUi.lastX, 80, "camera movement preserves the world destination captured before the wheel");
        near(nativeUi.lastY, 80, "pointer movement inside the wheel does not move the command destination");
        GameAction staleSelection = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        nativeUi.members = List.of(proxy(DeployedFleetMemberAPI.class, call -> call.name().equals("canBeGivenOrders") ? true : null));
        staleSelection.execute.run();
        check(nativeUi.orders == 1, "a stale wheel does not issue orders to a replacement selection");
        nativeUi.members = List.of(f.member); nativeUi.pointer.set(100, 100);
        GameAction stalePermission = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        nativeUi.direct = false; stalePermission.execute.run();
        check(nativeUi.orders == 1, "native order permission is checked again at execution");
        nativeUi.direct = true;
        GameAction staleScreen = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        nativeUi.warroomOwner = new Object(); staleScreen.execute.run();
        check(nativeUi.orders == 1, "a replaced warroom rejects the captured selected-unit order");
        GameAction outside = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        nativeUi.pan = 1000; outside.execute.run();
        check(nativeUi.orders == 1, "an off-screen captured destination is not reinterpreted at another position");
        nativeUi.pan = 0;
        GameAction modal = game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).findFirst().orElseThrow();
        f.commandUi = false; modal.execute.run();
        check(nativeUi.orders == 1, "closing the tactical map invalidates a captured order");
        f.commandUi = true;
        GameActions unfocused = new GameActions(f.uiActions::add, () -> false, () -> false, nativeUi);
        unfocused.execute("tactical.orderSelectedHere");
        check(nativeUi.orders == 1, "focus loss prevents native tactical input delivery");
        nativeUi.operation = true;
        game.execute("tactical.orderSelectedHere");
        check(nativeUi.orders == 1, "an in-progress native operation prevents selected-unit orders");
        nativeUi.operation = false;
        nativeUi.members = List.of(proxy(DeployedFleetMemberAPI.class, call -> switch (call.name()) {
            case "canBeGivenOrders", "isAlly" -> true;
            default -> null;
        }));
        check(game.actions("tactical").stream().filter(a -> a.id.equals("tactical.orderSelectedHere")).noneMatch(a -> a.enabled),
                "an all-allied selection is not presented as commandable");
    }

    private static void testEncounterMapContext() {
        Fixture f = new Fixture(); f.state = GameState.CAMPAIGN; f.install();
        f.dialogShowing = true; f.dialog = proxy(InteractionDialogAPI.class, call -> null); f.tab = CoreUITabId.MAP;
        NativeFixture nativeUi = new NativeFixture(f.member);
        nativeUi.core = new Object(); nativeUi.modal = nativeUi.core;
        GameContextDetector detector = new GameContextDetector(() -> null, () -> nativeUi.modal, ui -> nativeUi.core);
        GameContext encounterMap = detector.detect();
        check(encounterMap.is("MAP") && !encounterMap.gameplayAllowed(), "encounter-owned maps receive map controls without fleet movement");
        nativeUi.modal = new Object();
        GameContext nested = detector.detect();
        check(nested.is("UI") && !nested.identity().equals(encounterMap.identity()), "a nested dialog changes ownership and suspends map controls");
        nativeUi.modal = null;
        String before = detector.detect().identity(); nativeUi.core = new Object();
        check(!before.equals(detector.detect().identity()), "replacing the encounter core changes identity even without a top modal");
        nativeUi.core = null;
        check(detector.detect().is("UI"), "an encounter reporting an underlying map without its own core does not receive map input");
        f.dialog = null; f.dialogShowing = false;
        check(detector.detect().is("MAP"), "normal campaign maps retain their existing context");
        f.tab = CoreUITabId.CARGO;
        check(detector.detect().is("UI"), "other core tabs retain menu controls");
    }

    private static final class NativeFixture extends NativeGameUi {
        boolean create = true, direct = true, freeRetreat, operation;
        Object core, modal, warroomOwner = new Object();
        Set<CoreUITabId> tabs = Set.of();
        List<DeployedFleetMemberAPI> members;
        Vector2f pointer = new Vector2f(100, 100);
        float pan, lastX, lastY;
        int orders;
        NativeFixture(DeployedFleetMemberAPI member) { members = List.of(member); }
        @Override boolean canCreateAssignments(CombatTaskManagerAPI manager) { return create; }
        @Override boolean canIssueDirectOrders(CombatTaskManagerAPI manager) { return direct; }
        @Override boolean isRetreatFree(CombatTaskManagerAPI manager, DeployedFleetMemberAPI member) { return freeRetreat; }
        @Override CoreState coreState(CampaignUIAPI ui, com.fs.starfarer.api.ui.UIPanelAPI root) { return new CoreState(core, modal, tabs); }
        @Override boolean operationActive(com.fs.starfarer.api.ui.UIPanelAPI root) { return operation; }
        @Override Vector2f pointer() { return new Vector2f(pointer); }
        @Override WarroomPort warroom(com.fs.starfarer.api.ui.UIPanelAPI root) {
            return new WarroomPort() {
                public Object owner() { return warroomOwner; }
                public List<DeployedFleetMemberAPI> selected() { return members; }
                public Vector2f worldAt(float x, float y) { return new Vector2f(x + pan, y + pan); }
                public Vector2f screenAt(Vector2f world) { return new Vector2f(world.x - pan, world.y - pan); }
                public boolean contains(float x, float y) { return x >= 0 && y >= 0 && x <= 500 && y <= 500; }
                public void orderAt(int x, int y) { orders++; lastX = x; lastY = y; }
            };
        }
    }

    private record Command(ShipCommand type, Object point, int group) {}
    private static final class Fixture {
        GameState state = GameState.COMBAT;
        boolean paused, autopilot, shieldOn, dialogShowing, followingDirect, commandUi, fullRetreat;
        boolean assignmentUsesCost, retreatUsesCost;
        boolean visible = true, abilityUsable = true;
        int disableControls, abilityPresses, recentered;
        int commandPoints, assignments, retreats;
        InteractionDialogAPI dialog;
        float zoom = 1;
        CoreUITabId tab;
        Vector2f location = new Vector2f(), velocity = new Vector2f(), mouseTarget = new Vector2f();
        Vector2f destination;
        ShipAPI target, activeShip;
        List<ShipAPI> ships;
        final List<Command> commands = new ArrayList<>();
        final List<String> uiActions = new ArrayList<>();
        final ShieldAPI shield = proxy(ShieldAPI.class, call -> switch(call.name()) {
            case "isOn" -> shieldOn;
            case "isOff" -> !shieldOn;
            case "getType" -> ShieldAPI.ShieldType.OMNI;
            case "toggleOff" -> { shieldOn = false; yield null; }
            default -> null;
        });
        final ShipAPI enemy = proxy(ShipAPI.class, call -> switch(call.name()) {
            case "isAlive", "isTargetable" -> true;
            case "getOwner" -> 1;
            case "getName" -> "Visible enemy";
            case "getLocation" -> new Vector2f(100, 0);
            default -> null;
        });
        final ShipSystemAPI system = proxy(ShipSystemAPI.class, call -> switch(call.name()) {
            case "canBeActivated" -> true;
            case "getDisplayName" -> "Test system";
            default -> null;
        });
        final ShipAPI ship = proxy(ShipAPI.class, call -> switch(call.name()) {
            case "getName" -> "Player";
            case "getMouseTarget" -> mouseTarget;
            case "getLocation" -> location;
            case "getVelocity" -> velocity;
            case "isAlive", "isTargetable" -> true;
            case "getMaxSpeed", "getAcceleration", "getDeceleration", "getTurnDeceleration" -> 100f;
            case "getShield" -> shield;
            case "getSystem" -> system;
            case "getShipTarget" -> target;
            case "setShipTarget" -> { target = (ShipAPI) call.args()[0]; yield null; }
            case "getWeaponGroupsCopy", "getAllWings" -> List.of();
            case "giveCommand" -> {
                ShipCommand command = (ShipCommand) call.args()[0];
                commands.add(new Command(command, call.args()[1], (Integer) call.args()[2]));
                if (command == ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK) shieldOn = !shieldOn;
                yield null;
            }
            default -> null;
        });
        final CombatUIAPI combatUI = proxy(CombatUIAPI.class, call -> switch(call.name()) {
            case "isAutopilotOn" -> autopilot;
            case "isShowingCommandUI" -> commandUi;
            case "setDisablePlayerShipControlOneFrame" -> { disableControls++; yield null; }
            default -> null;
        });
        final CombatEngineAPI engine = proxy(CombatEngineAPI.class, call -> switch(call.name()) {
            case "getPlayerShip" -> activeShip;
            case "getCombatUI" -> combatUI;
            case "getShips" -> ships;
            case "isPaused" -> paused;
            case "setPaused" -> { paused = (Boolean) call.args()[0]; yield null; }
            case "isEntityInPlay" -> true;
            case "getFogOfWar" -> proxy(FogOfWarAPI.class, nested -> nested.name().equals("isVisible") ? visible : null);
            case "getFleetManager" -> this.fleetManager;
            default -> null;
        });
        final DeployedFleetMemberAPI member = proxy(DeployedFleetMemberAPI.class, call -> switch(call.name()) {
            case "getShip" -> ship;
            case "canBeGivenOrders", "canBeGivenRetreatOrders" -> true;
            default -> null;
        });
        final DeployedFleetMemberAPI enemyMember = proxy(DeployedFleetMemberAPI.class, call -> call.name().equals("getShip") ? enemy : null);
        final CombatTaskManagerAPI tasks = proxy(CombatTaskManagerAPI.class, call -> switch(call.name()) {
            case "getCommandPointsLeft" -> commandPoints;
            case "isInFullRetreat" -> fullRetreat;
            case "createAssignment" -> { assignments++; assignmentUsesCost = (Boolean) call.args()[2]; yield null; }
            case "orderRetreat" -> { retreats++; retreatUsesCost = (Boolean) call.args()[1]; yield null; }
            default -> null;
        });
        final CombatFleetManagerAPI fleetManager = proxy(CombatFleetManagerAPI.class, call -> switch(call.name()) {
            case "getTaskManager" -> tasks;
            case "getDeployedFleetMember" -> call.args()[0] == enemy ? enemyMember : member;
            default -> null;
        });
        final AbilitySpecAPI abilitySpec = proxy(AbilitySpecAPI.class, call -> switch(call.name()) {
            case "getName" -> "Test ability";
            default -> null;
        });
        final AbilityPlugin ability = proxy(AbilityPlugin.class, call -> switch(call.name()) {
            case "getId" -> "test";
            case "isUsable" -> abilityUsable;
            case "getSpec" -> abilitySpec;
            case "pressButton" -> { abilityPresses++; yield null; }
            default -> null;
        });
        final CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, call -> switch(call.name()) {
            case "getLocation" -> location;
            case "getTravelSpeed", "getAcceleration" -> 100f;
            case "setMoveDestinationOverride" -> { destination = new Vector2f((Float) call.args()[0], (Float) call.args()[1]); yield null; }
            case "getAbilities" -> Map.of("test", ability);
            case "getAbility" -> call.args()[0].equals("test") ? ability : null;
            default -> null;
        });
        final CampaignUIAPI campaignUI = proxy(CampaignUIAPI.class, call -> switch(call.name()) {
            case "getCurrentCoreTab" -> tab;
            case "getCurrentInteractionDialog" -> dialog;
            case "showCoreUITab" -> { tab = (CoreUITabId) call.args()[0]; yield null; }
            case "isShowingDialog" -> dialogShowing;
            case "setFollowingDirectCommand" -> { followingDirect = (Boolean) call.args()[0]; yield null; }
            case "getZoomFactor" -> zoom;
            case "getMinZoomFactor" -> .1f;
            case "getMaxZoomFactor" -> 10f;
            case "setZoomFactor" -> { zoom = (Float) call.args()[0]; yield null; }
            case "resetViewOffset" -> { recentered++; yield null; }
            default -> null;
        });
        final SectorAPI sector = proxy(SectorAPI.class, call -> switch(call.name()) {
            case "getPlayerFleet" -> fleet;
            case "getCampaignUI" -> campaignUI;
            case "isPaused" -> paused;
            case "setPaused" -> { paused = (Boolean) call.args()[0]; yield null; }
            default -> null;
        });
        Fixture() { activeShip = ship; ships = List.of(ship, enemy); }
        void install() {
            Global.setSettings(proxy(SettingsAPI.class, call -> call.name().equals("getCurrentState") ? state : null));
            Global.setCombatEngine(engine); Global.setSector(sector);
        }
        boolean has(ShipCommand command) { return commands.stream().anyMatch(c -> c.type() == command); }
    }

    private record Call(String name, Object[] args) {}
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Function<Call,Object> implementation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch(method.getName()) {
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> type.getSimpleName() + "TestDouble";
            };
            Object value = implementation.apply(new Call(method.getName(), args == null ? new Object[0] : args));
            if (value != null || !method.getReturnType().isPrimitive()) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == double.class) return 0d;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == char.class) return '\0';
            if (method.getReturnType() == void.class) return null;
            return 0;
        });
    }
}
