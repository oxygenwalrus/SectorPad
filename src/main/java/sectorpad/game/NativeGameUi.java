package sectorpad.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.campaign.CampaignState;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.combat.tasks.CombatTaskManager;
import com.fs.starfarer.campaign.ui.trade.F;
import com.fs.starfarer.ui.OOOo;
import com.fs.starfarer.ui.interfacenew;
import com.fs.starfarer.ui.newui.L;
import com.fs.starfarer.ui.newui.o0Oo;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;
import sectorpad.bridge.ReadOnlyUiNavigator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/** Verified public native rules and UI entry points; no private fields or replacement objects. */
class NativeGameUi {
    record CoreState(Object owner, Object modal, Set<CoreUITabId> enabledTabs) {
        CoreState { enabledTabs = Set.copyOf(enabledTabs); }
    }
    record SelectedOrder(Object owner, List<DeployedFleetMemberAPI> members, float worldX, float worldY) {
        SelectedOrder { members = List.copyOf(members); }
    }
    interface WarroomPort {
        Object owner();
        List<DeployedFleetMemberAPI> selected();
        Vector2f worldAt(float x, float y);
        Vector2f screenAt(Vector2f world);
        boolean contains(float x, float y);
        void orderAt(int x, int y);
    }

    boolean canCreateAssignments(CombatTaskManagerAPI manager) {
        return manager instanceof CombatTaskManager nativeManager && nativeManager.canCreateAssignments();
    }
    boolean canIssueDirectOrders(CombatTaskManagerAPI manager) {
        return manager instanceof CombatTaskManager nativeManager && nativeManager.canIssueDirectOrders();
    }
    boolean isRetreatFree(CombatTaskManagerAPI manager, DeployedFleetMemberAPI member) {
        return manager instanceof CombatTaskManager nativeManager && member != null
                && member.getShip() instanceof Ship ship && nativeManager.isRetreatFree(ship);
    }

    static L currentCore(CampaignUIAPI ui) {
        // CampaignState.showCoreUITab/getCurrentCoreTab use this same encounter-first ownership.
        if (ui != null && ui.getCurrentInteractionDialog() != null) {
            return ui.getCurrentInteractionDialog() instanceof o0Oo encounter ? encounter.getCoreUI() : null;
        }
        return StateAccess.currentState() instanceof CampaignState state ? state.getCore() : null;
    }

    CoreState coreState(CampaignUIAPI ui, UIPanelAPI root) {
        L core = currentCore(ui);
        Set<CoreUITabId> tabs = EnumSet.noneOf(CoreUITabId.class);
        if (core != null && core.getButtons() != null) {
            core.getButtons().getButtons().forEach((id, button) -> {
                if (id instanceof CoreUITabId tab && button != null && button.isEnabled()) tabs.add(tab);
            });
        }
        return new CoreState(core, ReadOnlyUiNavigator.modalIdentity(root), tabs);
    }

    /** Known public in-progress operations. Native modal ownership is checked separately. */
    boolean operationActive(UIPanelAPI root) {
        for (Object node : visibleTree(root)) {
            F handler = null;
            if (node instanceof com.fs.starfarer.coreui.oOoO core) handler = core.getTransferHandler();
            else if (node instanceof com.fs.starfarer.campaign.ui.oOOO loot) handler = loot.getTransferHandler();
            else if (node.getClass().getName().equals("com.fs.starfarer.campaign.ui.class")) {
                try { handler = (F) call(node, "getTransferHandler", F.class); }
                catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
                catch (Throwable unavailable) { return true; }
            }
            if (handler != null && handler.getPickedUpStack() != null) return true;
            if (node instanceof com.fs.starfarer.coreui.refit.oOOO refit && refit.isShowingDialog()) return true;
        }
        return false;
    }

    SelectedOrder captureSelectedOrder(UIPanelAPI root) {
        WarroomPort port = warroom(root);
        if (port == null) return null;
        List<DeployedFleetMemberAPI> selected = port.selected();
        Vector2f pointer = pointer();
        if (!commandable(selected) || !finite(pointer) || !port.contains(pointer.x, pointer.y)) return null;
        Vector2f world = port.worldAt(pointer.x, pointer.y);
        return finite(world) ? new SelectedOrder(port.owner(), selected, world.x, world.y) : null;
    }

    boolean orderSelected(SelectedOrder captured, UIPanelAPI root) {
        if (captured == null) return false;
        WarroomPort port = warroom(root);
        if (port == null || port.owner() != captured.owner()) return false;
        List<DeployedFleetMemberAPI> selected = port.selected();
        if (!commandable(selected) || !sameMembers(captured.members(), selected)) return false;
        // Preserve the chosen world location if the view moved while the wheel was open.
        Vector2f screen = port.screenAt(new Vector2f(captured.worldX(), captured.worldY()));
        if (!finite(screen) || !port.contains(screen.x, screen.y)) return false;
        port.orderAt(Math.round(screen.x), Math.round(screen.y));
        return true;
    }

    private static boolean commandable(List<DeployedFleetMemberAPI> selected) {
        return selected != null && selected.stream().anyMatch(member -> member != null && !member.isAlly() && member.canBeGivenOrders());
    }
    private static boolean sameMembers(List<DeployedFleetMemberAPI> first, List<DeployedFleetMemberAPI> second) {
        if (first.size() != second.size()) return false;
        Set<DeployedFleetMemberAPI> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(first);
        if (members.size() != first.size()) return false;
        for (DeployedFleetMemberAPI member : second) if (!members.remove(member)) return false;
        return members.isEmpty();
    }
    private static boolean finite(Vector2f point) { return point != null && Float.isFinite(point.x) && Float.isFinite(point.y); }

    Vector2f pointer() {
        if (!Mouse.isCreated()) return null;
        float scale = Global.getSettings().getScreenScaleMult();
        if (!Float.isFinite(scale) || scale <= 0) return null;
        return new Vector2f(Mouse.getX() / scale, Mouse.getY() / scale);
    }
    WarroomPort warroom(UIPanelAPI root) {
        List<Object> nodes = visibleTree(root);
        for (Object node : nodes) if (node.getClass().getName().equals("com.fs.starfarer.combat.new.OoOO")) {
            try { return new LiveWarroom(node, nodes); }
            catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { return null; }
        }
        return null;
    }

    private static final class LiveWarroom implements WarroomPort {
        private final Object owner, map, selection;
        private final List<Object> nodes;
        LiveWarroom(Object owner, List<Object> nodes) throws Throwable {
            this.owner = owner;
            this.nodes = nodes;
            map = call(owner, "getMapDisplay", type("com.fs.starfarer.combat.new.b"));
            selection = call(owner, "getSelectionManager", type("com.fs.starfarer.combat.new.interface"));
        }
        public Object owner() { return owner; }
        public List<DeployedFleetMemberAPI> selected() {
            try {
                List<?> raw = (List<?>) call(selection, "String", List.class);
                List<DeployedFleetMemberAPI> result = new ArrayList<>();
                for (Object item : raw) {
                    if (!(item instanceof DeployedFleetMemberAPI member)) return List.of();
                    result.add(member);
                }
                return List.copyOf(result);
            } catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { return List.of(); }
        }
        public Vector2f worldAt(float x, float y) {
            try { return (Vector2f) invoke(owner, "computeWorldLocation", Vector2f.class, new Class<?>[]{float.class, float.class}, x, y); }
            catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { return null; }
        }
        public Vector2f screenAt(Vector2f world) {
            try { return (Vector2f) invoke(map, "o00000", Vector2f.class, new Class<?>[]{Vector2f.class}, world); }
            catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { return null; }
        }
        public boolean contains(float x, float y) {
            if (!(owner instanceof UIComponentAPI bounds) || !inside(bounds, x, y)) return false;
            for (Object node : nodes) {
                if ((node instanceof ButtonAPI || node instanceof TextFieldAPI || node instanceof ScrollPanelAPI)
                        && node instanceof UIComponentAPI component && inside(component, x, y)) return false;
            }
            return true;
        }
        public void orderAt(int x, int y) {
            try {
                Class<?> event = type("com.fs.starfarer.util.A.C");
                MethodHandle constructor = MethodHandles.publicLookup().findConstructor(event,
                        MethodType.methodType(void.class, InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class));
                Object release = constructor.invoke(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_UP, 0, 0, 1, '\0');
                invoke(release, "setX", void.class, new Class<?>[]{int.class}, x);
                invoke(release, "setY", void.class, new Class<?>[]{int.class}, y);
                // The native handler chooses move/escort/attack, validates rules, creates waypoints,
                // and charges or refunds command points. We do not construct assignments here.
                invoke(owner, "rightClickReleased", void.class, new Class<?>[]{event, boolean.class}, release, false);
            } catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { throw new IllegalStateException("Native selected-unit order unavailable", unavailable); }
        }
    }

    static void verifySignatures() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        lookup.findVirtual(CombatTaskManager.class, "canCreateAssignments", MethodType.methodType(boolean.class));
        lookup.findVirtual(CombatTaskManager.class, "canIssueDirectOrders", MethodType.methodType(boolean.class));
        lookup.findVirtual(CombatTaskManager.class, "isRetreatFree", MethodType.methodType(boolean.class, Ship.class));
        Class<?> warroom = type("com.fs.starfarer.combat.new.OoOO"), map = type("com.fs.starfarer.combat.new.b");
        Class<?> selection = type("com.fs.starfarer.combat.new.interface"), event = type("com.fs.starfarer.util.A.C");
        lookup.findVirtual(warroom, "getSelectionManager", MethodType.methodType(selection));
        lookup.findVirtual(selection, "String", MethodType.methodType(List.class));
        lookup.findVirtual(warroom, "getMapDisplay", MethodType.methodType(map));
        lookup.findVirtual(warroom, "computeWorldLocation", MethodType.methodType(Vector2f.class, float.class, float.class));
        lookup.findVirtual(map, "o00000", MethodType.methodType(Vector2f.class, Vector2f.class));
        lookup.findVirtual(warroom, "rightClickReleased", MethodType.methodType(void.class, event, boolean.class));
        lookup.findConstructor(event, MethodType.methodType(void.class, InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class));
        lookup.findVirtual(o0Oo.class, "getCoreUI", MethodType.methodType(L.class));
        lookup.findVirtual(F.class, "getPickedUpStack", MethodType.methodType(com.fs.starfarer.campaign.ui.trade.CargoItemStack.class));
    }

    private static List<Object> visibleTree(Object root) {
        List<Object> found = new ArrayList<>();
        visit(root, found, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return found;
    }
    private static void visit(Object node, List<Object> found, Set<Object> seen, int depth) {
        if (node == null || node instanceof CustomPanelAPI || depth > 48 || seen.size() >= 5000 || !seen.add(node)) return;
        if (node instanceof UIComponentAPI component && component.getOpacity() <= .01f) return;
        if (node instanceof OOOo nativeComponent && nativeComponent.getFader() != null && nativeComponent.getFader().getBrightness() <= .01f) return;
        found.add(node);
        if (node instanceof interfacenew panel) for (Object child : panel.getChildrenCopy()) visit(child, found, seen, depth + 1);
    }
    private static boolean inside(UIComponentAPI component, float x, float y) {
        PositionAPI bounds = component.getPosition();
        return bounds != null && x >= bounds.getX() && y >= bounds.getY()
                && x <= bounds.getX() + bounds.getWidth() && y <= bounds.getY() + bounds.getHeight();
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, NativeGameUi.class.getClassLoader()); }
    private static Object call(Object target, String name, Class<?> result) throws Throwable { return invoke(target, name, result, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?> result, Class<?>[] parameters, Object... args) throws Throwable {
        MethodHandle method = MethodHandles.publicLookup().findVirtual(target.getClass(), name, MethodType.methodType(result, parameters));
        List<Object> values = new ArrayList<>(args.length + 1);
        values.add(target); Collections.addAll(values, args);
        return method.invokeWithArguments(values);
    }
}
