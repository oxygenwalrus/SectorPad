package sectorpad.game;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.campaign.ui.trade.CargoDataGridView;
import com.fs.starfarer.campaign.ui.trade.CargoItemStack;
import com.fs.starfarer.campaign.ui.trade.CargoStackView;
import com.fs.starfarer.campaign.ui.trade.F;
import com.fs.starfarer.ui.interfacenew;
import sectorpad.bridge.ReadOnlyUiNavigator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Selects an exact pickup quantity through the original cargo UI's public event handlers.
 * Never assigns cargo data, places the pickup, confirms a transaction, or accesses private fields.
 */
public final class CargoQuantityAdapter {
    public static final class Target {
        private final String name;
        private final int maximum;
        private final String context;
        private final NativePort port;
        private Target(String name, int maximum, String context, NativePort port) {
            this.name = name; this.maximum = maximum; this.context = context; this.port = port;
        }
        public String name() { return name; }
        public int maximum() { return maximum; }
    }

    private final ReadOnlyUiNavigator navigator = new ReadOnlyUiNavigator();
    private Job job;
    private Target activeTarget;
    private String status = "Point at a visible cargo stack to choose an exact pickup quantity.";
    private int current, requested;

    public Target capture(Object root, float pointerX, float pointerY, String contextIdentity) {
        if (isActive()) return null;
        try {
            navigator.refresh(root);
            CargoStackView view = navigator.getCandidates().stream()
                    .filter(c -> c.component() instanceof CargoStackView && c.contains(pointerX, pointerY))
                    .min(Comparator.comparingDouble(c -> c.width() * c.height()))
                    .map(c -> (CargoStackView) c.component()).orElse(null);
            if (view == null || view.getStack() == null) return unavailable("Point at a visible cargo stack first.");
            CargoItemStack stack = view.getStack();
            int maximum = wholeCount(stack.getSize());
            if (maximum < 1 || stack.isPickedUp()) return unavailable("This stack has no whole units available to pick up.");
            List<F> handlers = handlers(navigator.getFocusRoot());
            if (handlers.stream().anyMatch(handler -> handler.getPickedUpStack() != null)) return unavailable("Finish or cancel the current cargo pickup first.");
            for (F handler : handlers) {
                for (CargoDataGridView.o source : new CargoDataGridView.o[]{handler.getManifestOne(), handler.getManifestTwo()}) {
                    if (source == null || indexOf(source, stack) < 0 || !contains(source.getCargoDataView(), view, identitySet(), 0)) continue;
                    if (source.preventPickup(stack) || source.isInvalidDropTarget(stack)) continue;
                    NativePort port = new NativePort(handler, source, stack, maximum, pointerX, pointerY);
                    // Resolve only public event entry points before any pickup is changed.
                    port.events = new NativeEvents();
                    status = "Choose how many units of " + stack.getDisplayName() + " to pick up.";
                    return new Target(stack.getDisplayName(), maximum, contextIdentity, port);
                }
            }
            return unavailable("Finish the current pickup, or select a stack the normal cargo UI allows you to take.");
        } catch (RuntimeException | LinkageError unavailable) {
            return unavailable("The current cargo screen does not expose the supported quantity controls.");
        }
    }

    public boolean start(Target target, int amount, Object root, String contextIdentity, long now) {
        if (isActive() || target == null || amount < 1 || amount > target.maximum) {
            status = "Enter a whole quantity within the displayed available amount.";
            return false;
        }
        List<F> liveHandlers = handlers(root);
        if (!Objects.equals(target.context, contextIdentity) || !liveHandlers.contains(target.port.handler)
                || liveHandlers.stream().anyMatch(handler -> handler.getPickedUpStack() != null)) {
            status = "Quantity selection cancelled because the cargo screen changed.";
            return false;
        }
        activeTarget = target;
        job = new Job(target.port, target.maximum, amount, now);
        boolean started = job.start();
        updateStatus();
        return started;
    }

    public void advance(Object root, String contextIdentity, long now) {
        if (!isActive()) return;
        if (!Objects.equals(activeTarget.context, contextIdentity) || !handlers(root).contains(activeTarget.port.handler)) {
            cancel("Quantity selection cancelled because the cargo screen changed.");
            return;
        }
        job.advance(now);
        updateStatus();
    }

    public void cancel(String reason) {
        if (job != null && job.active) job.cancel(reason);
        updateStatus();
    }
    public boolean isActive() { return job != null && job.active; }
    public String status() { return status; }
    public int current() { return current; }
    public int requested() { return requested; }

    private Target unavailable(String reason) { status = reason; return null; }
    private void updateStatus() {
        if (job == null) return;
        status = job.status; current = job.current; requested = job.requested;
        if (!job.active) { job = null; activeTarget = null; }
    }

    interface Port {
        boolean valid();
        boolean pick(boolean entireStack);
        int picked();
        void increment();
        void cancel();
    }

    /** Bounded game-thread state machine, independently testable without native game objects. */
    static final class Job {
        final Port port;
        final int maximum, requested;
        final long deadline;
        int current;
        boolean active;
        String status = "Preparing quantity selection.";
        Job(Port port, int maximum, int requested, long now) {
            this.port = port; this.maximum = maximum; this.requested = requested;
            deadline = now + Math.min(300_000_000_000L, 10_000_000_000L + (long) requested * 1_000_000L);
        }
        boolean start() {
            try {
                if (requested < 1 || requested > maximum || !port.valid()) { status = "The source stack changed. No quantity was selected."; return false; }
                active = true;
                if (!port.pick(requested == maximum)) { cancel("The normal cargo UI rejected this pickup."); return false; }
                current = port.picked();
                if (current < 1 || current > requested) { cancel("The normal pickup changed unexpectedly; quantity selection cancelled."); return false; }
                finishIfReady();
                return true;
            } catch (RuntimeException | LinkageError failure) {
                active = true;
                cancel("Quantity selection failed and its pickup was cancelled.");
                return false;
            }
        }
        void advance(long now) {
            if (!active) return;
            if (now > deadline) { cancel("Quantity selection timed out; its pickup was cancelled."); return; }
            long frameDeadline = System.nanoTime() + 2_000_000L;
            try {
                for (int count = 0; count < 64 && current < requested; count++) {
                    if (!port.valid() || port.picked() != current) { cancel("The source or pickup changed; quantity selection cancelled."); return; }
                    port.increment();
                    int next = port.picked();
                    if (next != current + 1) { cancel("The normal cargo UI could not select the requested quantity."); return; }
                    current = next;
                    if (System.nanoTime() >= frameDeadline) break;
                }
                finishIfReady();
            } catch (RuntimeException | LinkageError failure) {
                cancel("Quantity selection failed and its pickup was cancelled.");
            }
        }
        void cancel(String reason) {
            if (!active) return;
            active = false;
            try { port.cancel(); }
            catch (RuntimeException | LinkageError failure) { reason += " Use the normal Back control if a pickup remains."; }
            status = reason;
        }
        private void finishIfReady() {
            if (current == requested) { active = false; status = current + " units picked up. Place them with the normal cargo control; the transaction is unconfirmed."; }
            else status = "Selecting quantity: " + current + " / " + requested + ". Cancel returns this pickup.";
        }
    }

    private static final class NativePort implements Port {
        final F handler;
        final CargoDataGridView.o source;
        final CargoItemStack original;
        final int maximum;
        final float x, y;
        CargoItemStack ownedPickup;
        NativeEvents events;
        NativePort(F handler, CargoDataGridView.o source, CargoItemStack original, int maximum, float x, float y) {
            this.handler = handler; this.source = source; this.original = original; this.maximum = maximum; this.x = x; this.y = y;
        }
        public boolean valid() {
            if (ownedPickup == null) return handler.getPickedUpStack() == null && indexOf(source, original) >= 0
                    && wholeCount(original.getSize()) == maximum && !source.preventPickup(original) && !source.isInvalidDropTarget(original);
            return handler.getPickedUpStack() == ownedPickup && handler.getOrigStackSource() == source
                    && indexOf(source, original) >= 0 && wholeCount(original.getSize()) + picked() == maximum;
        }
        public boolean pick(boolean entireStack) {
            int index = indexOf(source, original);
            if (index < 0) return false;
            events.click(handler, mouse(InputEventType.MOUSE_DOWN, 0, !entireStack), source, index);
            ownedPickup = handler.getPickedUpStack();
            if (ownedPickup == null) return false;
            // A fresh Shift pickup's native slider starts at one. Its normal release commits that pickup.
            events.send(handler, mouse(InputEventType.MOUSE_UP, 0, !entireStack));
            return handler.getPickedUpStack() == ownedPickup;
        }
        public int picked() { return ownedPickup != null && handler.getPickedUpStack() == ownedPickup ? wholeCount(ownedPickup.getSize()) : -1; }
        public void increment() {
            int index = indexOf(source, original);
            if (index < 0) throw new IllegalStateException("The source stack changed");
            events.click(handler, mouse(InputEventType.MOUSE_DOWN, 0, true), source, index);
        }
        public void cancel() {
            if (ownedPickup == null || handler.getPickedUpStack() != ownedPickup) return;
            // The original handler checks that it still owns the native pickup before processing Escape.
            events.send(handler, events.event(InputEventClass.KEYBOARD_EVENT, InputEventType.KEY_DOWN, 1, false, 0, 0));
        }
        private Object mouse(InputEventType type, int button, boolean shifted) {
            return events.event(InputEventClass.MOUSE_EVENT, type, button, shifted, x, y);
        }
    }

    private static final class NativeEvents {
        final MethodHandle create, process, createEvent, click, setX, setY, setShift;
        NativeEvents() {
            try {
                // Java-keyword and class/package name collisions prevent ordinary imports of these public types.
                Class<?> listType = Class.forName("com.fs.starfarer.util.A.new", false, F.class.getClassLoader());
                Class<?> eventType = Class.forName("com.fs.starfarer.util.A.C", false, F.class.getClassLoader());
                create = MethodHandles.publicLookup().findConstructor(listType, MethodType.methodType(void.class));
                process = MethodHandles.publicLookup().findVirtual(F.class, "processInput", MethodType.methodType(void.class, listType));
                createEvent = MethodHandles.publicLookup().findConstructor(eventType, MethodType.methodType(void.class,
                        InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class));
                click = MethodHandles.publicLookup().findVirtual(F.class, "cargoCellClicked", MethodType.methodType(void.class, eventType, CargoDataGridView.o.class, int.class));
                setX = MethodHandles.publicLookup().findVirtual(eventType, "setX", MethodType.methodType(void.class, int.class));
                setY = MethodHandles.publicLookup().findVirtual(eventType, "setY", MethodType.methodType(void.class, int.class));
                setShift = MethodHandles.publicLookup().findVirtual(eventType, "setShiftDown", MethodType.methodType(void.class, boolean.class));
            } catch (ReflectiveOperationException | LinkageError | SecurityException failure) {
                throw new IllegalStateException("Native cargo event entry points unavailable", failure);
            }
        }
        Object event(InputEventClass kind, InputEventType type, int value, boolean shifted, float x, float y) {
            try {
                Object event = createEvent.invoke(kind, type, 0, 0, value, '\0');
                setX.invoke(event, Math.round(x)); setY.invoke(event, Math.round(y)); setShift.invoke(event, shifted);
                return event;
            } catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable failure) { throw new IllegalStateException("Native cargo event failed", failure); }
        }
        void click(F handler, Object event, CargoDataGridView.o source, int index) {
            try { click.invoke(handler, event, source, index); }
            catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable failure) { throw new IllegalStateException("Native cargo click failed", failure); }
        }
        @SuppressWarnings("unchecked")
        void send(F handler, Object event) {
            try {
                Object list = create.invoke();
                ((List<Object>) list).add(event);
                process.invoke(handler, list);
            } catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable failure) { throw new IllegalStateException("Native cargo event failed", failure); }
        }
    }

    private static List<F> handlers(Object root) {
        List<F> found = new ArrayList<>();
        collectHandlers(root, found, identitySet(), 0);
        return found;
    }
    private static void collectHandlers(Object node, List<F> result, Set<Object> seen, int depth) {
        if (node == null || node instanceof CustomPanelAPI || depth > 48 || seen.size() >= 5000 || !seen.add(node)) return;
        F handler = null;
        if (node instanceof com.fs.starfarer.coreui.oOoO core) handler = core.getTransferHandler();
        else if (node instanceof com.fs.starfarer.campaign.ui.oOOO loot) handler = loot.getTransferHandler();
        else if (node.getClass().getName().equals("com.fs.starfarer.campaign.ui.class")) {
            try {
                handler = (F) MethodHandles.publicLookup().findVirtual(node.getClass(), "getTransferHandler", MethodType.methodType(F.class)).invoke(node);
            } catch (ThreadDeath | VirtualMachineError fatal) { throw fatal; }
            catch (Throwable unavailable) { /* This optional, public route is not exposed by this game build. */ }
        }
        if (handler != null && !result.contains(handler)) result.add(handler);
        if (node instanceof interfacenew panel) for (Object child : panel.getChildrenCopy()) collectHandlers(child, result, seen, depth + 1);
    }
    private static boolean contains(Object node, Object target, Set<Object> seen, int depth) {
        if (node == target) return true;
        if (node == null || depth > 48 || seen.size() >= 5000 || !seen.add(node)) return false;
        if (node instanceof interfacenew panel) for (Object child : panel.getChildrenCopy()) if (contains(child, target, seen, depth + 1)) return true;
        return false;
    }
    private static int indexOf(CargoDataGridView.o source, CargoItemStack stack) {
        if (source == null || source.getFilteredCargo() == null) return -1;
        List<CargoStackAPI> stacks = source.getFilteredCargo().getStacksCopy();
        for (int index = 0; index < stacks.size(); index++) if (stacks.get(index) == stack) return index;
        return -1;
    }
    private static int wholeCount(float size) { return Float.isFinite(size) && size >= 1 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(size)) : 0; }
    private static Set<Object> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
}
