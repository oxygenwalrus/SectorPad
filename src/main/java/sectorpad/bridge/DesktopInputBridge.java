package sectorpad.bridge;

import com.fs.starfarer.api.Global;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import sectorpad.diagnostics.Diagnostics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Additive OS input. Never replaces an LWJGL implementation or a game UI node.
 * Call on the game thread and call pump before accepting input each frame.
 */
public final class DesktopInputBridge implements AutoCloseable {
    public interface Output extends AutoCloseable {
        default boolean acceptsFocus() { return true; }
        default boolean isOperational() { return true; }
        void observe(boolean active);
        boolean isPhysicalKeyDown(int key);
        boolean isPhysicalMouseDown(int button);
        default long keyReleaseSequence(int key) { return 0; }
        default long mouseReleaseSequence(int button) { return 0; }
        boolean preservesPhysicalHolds();
        void key(int lwjglKey, boolean down);
        void mouse(int button, boolean down);
        default void disownMouse(int button) { }
        default void modifiers(boolean shift,boolean ctrl,boolean alt) { }
        void wheel(int notches);
        default void pointerMoved(float dx,float dy) { }
        boolean supportsUnicode();
        void unicode(char character);
        String description();
        @Override void close();
    }

    public interface Surface {
        boolean focused();
        float pointerX();
        float pointerY();
        float width();
        float height();
        void move(float x, float y);
        default long coordinateRevision() { return 0L; }
    }

    private static final long TAP_NANOS = 35_000_000L;
    private static final long PHYSICAL_TAP_WAIT_NANOS = 2_000_000_000L;
    private static final int MAX_PENDING = 256;
    private final Surface surface;
    private final LongSupplier clock;
    private Output output;
    private boolean initialized, active, closed;
    private boolean retryPending;
    private long retryAt;
    private String context;
    private String failure = "Input initializes when the game is focused";
    private final Set<Integer> wantedKeys = new LinkedHashSet<>();
    private final Set<Integer> ownedKeys = new LinkedHashSet<>();
    private final Set<Integer> wantedButtons = new LinkedHashSet<>();
    private final Set<Integer> ownedButtons = new LinkedHashSet<>();
    private final long[] keyReleaseSequences = new long[256];
    private final long[] mouseReleaseSequences = new long[8];
    private final ArrayDeque<Tap> pending = new ArrayDeque<>();
    private Tap running;
    private long releaseAt;
    private boolean pointerTracked;
    private float pointerTargetX,pointerTargetY,pointerObservedX,pointerObservedY;
    private long pointerRevision;

    public DesktopInputBridge() { this(new GameSurface(), null, System::nanoTime); }

    /** Dependency injection for headless lifecycle tests; never creates an OS backend. */
    public DesktopInputBridge(Surface surface, Output output, LongSupplier clock) {
        this.surface = surface;
        this.output = output;
        this.clock = clock;
        this.initialized = output != null;
    }

    /** Returns false when focus, platform capability, or caller ownership blocks input. */
    public synchronized boolean pump(boolean enabled, String nextContext) {
        if (closed) return false;
        try { return pumpOwned(enabled, nextContext); }
        catch (RuntimeException | LinkageError ex) { fail(ex); return false; }
    }

    private boolean pumpOwned(boolean enabled, String nextContext) {
        if (retryPending && clock.getAsLong() - retryAt < 0) return false;
        retryPending = false;
        boolean focused = enabled && hasGameFocus();
        boolean changed = context != null && !context.equals(nextContext);
        if (!focused || changed) {
            releaseAll();
            if (output != null) output.observe(false);
            active = false;
            context = nextContext;
            // A context transition always has one neutral frame.
            if (!focused || changed) return false;
        }
        context = nextContext;
        if (!initialized) initialize();
        if (output == null) return false;
        if (!output.acceptsFocus()) return false;
        try {
            if (active && !output.isOperational()) throw new IllegalStateException("Desktop observer stopped; input recovery pending");
            if (!active) output.observe(true);
            active = true;
            failure = "";
            maintainHolds();
            long now = clock.getAsLong();
            if (running != null && now >= releaseAt) {
                Tap completed = running;
                running = null;
                finishTap(completed);
                // At least one pump boundary separates one tap from the next.
                return true;
            }
            if (running == null && !pending.isEmpty()) {
                Tap next = pending.peekFirst();
                if (next.character == null && !alreadyWanted(next) && physicallyHeld(next)) {
                    next.waitedForPhysical = true;
                    // A physical click may have selected a wheel action. Wait for its
                    // release before issuing the queued native click, without owning it.
                    if (now - next.queuedAt < PHYSICAL_TAP_WAIT_NANOS) return true;
                }
                pending.removeFirst();
                // Bound the wait from enqueue time, including a release between pumps.
                if (next.waitedForPhysical && now - next.queuedAt >= PHYSICAL_TAP_WAIT_NANOS) return true;
                if(next.mouse&&Boolean.getBoolean("sectorpad.debugInput"))Global.getLogger(DesktopInputBridge.class).info("SectorPad queued mouse: button="+next.code+" wanted="+alreadyWanted(next)+" physical="+physicallyHeld(next));
                if (next.character != null) {
                    output.unicode(next.character);
                } else if (!alreadyWanted(next) && !physicallyHeld(next)) {
                    running = next;
                    if (next.mouse) {
                        mouseDown(next.code);
                        wantedButtons.remove(next.code);
                    } else {
                        keyDown(next.code);
                        wantedKeys.remove(next.code);
                    }
                    releaseAt = now + TAP_NANOS;
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            fail(ex);
        }
        return active;
    }

    private void initialize() {
        initialized = true;
        try {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                output = new WindowsInputOutput();
            } else {
                output = new RobotInputOutput();
            }
            if(surface instanceof GameSurface)output=new NativeUiMouseOutput(output,surface);
            failure = "";
        } catch (Exception | LinkageError ex) {
            failure = "Desktop input unavailable: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
            output = null;
            initialized = false;
            retryAt = clock.getAsLong() + 5_000_000_000L;
            retryPending = true;
            Diagnostics.error("bridge.initialize", ex);
        }
    }

    private void maintainHolds() {
        Set<Integer> activeKeys = new HashSet<>(wantedKeys);
        Set<Integer> activeButtons = new HashSet<>(wantedButtons);
        if (running != null && running.character == null) {
            if (running.mouse) activeButtons.add(running.code); else activeKeys.add(running.code);
        }
        for (int key : activeKeys) {
            long released = output.keyReleaseSequence(key);
            boolean physical = output.isPhysicalKeyDown(key);
            if (!physical && (!ownedKeys.contains(key) || released != keyReleaseSequences[key])) {
                output.key(key, true);
                ownedKeys.add(key);
            }
            keyReleaseSequences[key] = released;
        }
        for (int button : activeButtons) {
            long released = output.mouseReleaseSequence(button);
            boolean physical = output.isPhysicalMouseDown(button);
            if (!physical && (!ownedButtons.contains(button) || released != mouseReleaseSequences[button])) {
                if(released!=mouseReleaseSequences[button])output.disownMouse(button);
                syncModifiers();output.mouse(button, true);
                ownedButtons.add(button);
            }
            mouseReleaseSequences[button] = released;
        }
    }

    private boolean ready() { return active && !closed && output != null && surface.focused() && output.acceptsFocus(); }

    /** Authoritative focus gate for both bridged input and direct game API actions. */
    public synchronized boolean hasGameFocus() {
        if(closed)return false;
        boolean surfaceFocused=surface.focused();
        // Keep both independent gates; a Wine report can now distinguish window focus from native foreground.
        boolean nativeFocused=surfaceFocused && (output==null || output.acceptsFocus());
        Diagnostics.state("bridge.surface_focus",Boolean.toString(surfaceFocused));
        Diagnostics.state("bridge.native_focus",output==null?"uninitialized":!surfaceFocused?"not_queried":Boolean.toString(nativeFocused));
        return nativeFocused;
    }

    public synchronized void movePointer(float x, float y) {
        if (!ready()) return;
        pointerTargetX=PointerCoordinates.clamp(x,surface.width());
        pointerTargetY=PointerCoordinates.clamp(y,surface.height());
        writePointer();
    }

    public synchronized void movePointerBy(float dx, float dy) {
        if(!ready() || !Float.isFinite(dx) || !Float.isFinite(dy)) return;
        float observedX=surface.pointerX(), observedY=surface.pointerY();
        long revision=surface.coordinateRevision();
        // Keep fractional motion until it reaches a physical pixel, but yield immediately
        // when real mouse input or a changed coordinate system moves the observed pointer.
        if(!pointerTracked || revision!=pointerRevision || observedX!=pointerObservedX || observedY!=pointerObservedY) {
            pointerTargetX=observedX;pointerTargetY=observedY;
        }
        pointerTargetX=PointerCoordinates.clamp(pointerTargetX+dx,surface.width());
        pointerTargetY=PointerCoordinates.clamp(pointerTargetY+dy,surface.height());
        writePointer();
    }
    private void writePointer() {
        float previousX=surface.pointerX(),previousY=surface.pointerY();
        surface.move(pointerTargetX,pointerTargetY);
        pointerObservedX=surface.pointerX();pointerObservedY=surface.pointerY();
        syncModifiers();output.pointerMoved(pointerObservedX-previousX,pointerObservedY-previousY);
        pointerRevision=surface.coordinateRevision();pointerTracked=true;
    }
    public synchronized void moveCursor(float x, float y) { movePointer(x, y); }
    public synchronized float getPointerX() { return surface.pointerX(); }
    public synchronized float getPointerY() { return surface.pointerY(); }

    public synchronized void keyDown(int key) {
        if (!ready() || !KeyCodes.supported(key) || !wantedKeys.add(key)) return;
        keyReleaseSequences[key] = output.keyReleaseSequence(key);
        if (!ownedKeys.contains(key) && !output.isPhysicalKeyDown(key)) {
            try { output.key(key, true); ownedKeys.add(key); } catch (RuntimeException ex) { fail(ex); }
        }
    }

    public synchronized void keyUp(int key) {
        wantedKeys.remove(key);
        if (running != null && !running.mouse && running.code == key) return;
        releaseKey(key);
    }

    private void releaseKey(int key) {
        if (!ownedKeys.contains(key) || output == null) return;
        // When real input has taken over, its eventual key-up owns the release.
        if (!output.preservesPhysicalHolds() || !output.isPhysicalKeyDown(key)) output.key(key, false);
        ownedKeys.remove(key);
    }

    public synchronized void mouseDown(int button) {
        if (!ready() || button < 0 || button > 2 || !wantedButtons.add(button)) return;
        mouseReleaseSequences[button] = output.mouseReleaseSequence(button);
        if (!ownedButtons.contains(button) && !output.isPhysicalMouseDown(button)) {
            try { syncModifiers();output.mouse(button, true); ownedButtons.add(button); } catch (RuntimeException ex) { fail(ex); }
        }
    }

    public synchronized void mouseUp(int button) {
        wantedButtons.remove(button);
        if (running != null && running.mouse && running.code == button) return;
        releaseButton(button);
    }

    private void releaseButton(int button) {
        if (!ownedButtons.contains(button) || output == null) return;
        if (!output.preservesPhysicalHolds() || !output.isPhysicalMouseDown(button)) {syncModifiers();output.mouse(button, false);}
        else output.disownMouse(button);
        ownedButtons.remove(button);
    }

    public synchronized void keyTap(int key) {
        if (ready() && KeyCodes.supported(key) && pending.size() < MAX_PENDING) pending.addLast(new Tap(false, key, null, clock.getAsLong()));
    }

    public synchronized void mouseClick(int button) {
        if(Boolean.getBoolean("sectorpad.debugInput"))Global.getLogger(DesktopInputBridge.class).info("SectorPad queue click: button="+button+" ready="+ready()+" pending="+pending.size());
        if (ready() && button >= 0 && button <= 2 && pending.size() < MAX_PENDING) pending.addLast(new Tap(true, button, null, clock.getAsLong()));
    }

    public synchronized void primaryDown() { mouseDown(0); }
    public synchronized void primaryUp() { mouseUp(0); }
    public synchronized void secondaryDown() { mouseDown(1); }
    public synchronized void secondaryUp() { mouseUp(1); }

    /** Positive notches scroll up, matching the LWJGL/game convention. */
    public synchronized void scroll(int notches) {
        if (ready() && notches != 0) {
            try { syncModifiers();output.wheel(Math.max(-12, Math.min(12, notches))); } catch (RuntimeException ex) { fail(ex); }
        }
    }

    private boolean modifierDown(int left,int right){return wantedKeys.contains(left)||wantedKeys.contains(right)
            ||(running!=null&&!running.mouse&&(running.code==left||running.code==right))
            ||output.isPhysicalKeyDown(left)||output.isPhysicalKeyDown(right);}
    private void syncModifiers(){output.modifiers(modifierDown(Keyboard.KEY_LSHIFT,Keyboard.KEY_RSHIFT),
            modifierDown(Keyboard.KEY_LCONTROL,Keyboard.KEY_RCONTROL),modifierDown(Keyboard.KEY_LMENU,Keyboard.KEY_RMENU));}

    /** Layout-independent Windows Unicode packets; prefer committing a verified TextFieldAPI. */
    public synchronized boolean typeText(String text) {
        if (!ready() || text == null || !output.supportsUnicode() || text.length() > MAX_PENDING - pending.size()) return false;
        long queuedAt = clock.getAsLong();
        for (int i = 0; i < text.length(); i++) pending.addLast(new Tap(false, 0, text.charAt(i), queuedAt));
        return true;
    }

    public synchronized boolean typeCharacter(char value) { return typeText(String.valueOf(value)); }

    private boolean alreadyWanted(Tap tap) { return tap.mouse ? wantedButtons.contains(tap.code) : wantedKeys.contains(tap.code); }
    private boolean physicallyHeld(Tap tap) { return tap.mouse ? output.isPhysicalMouseDown(tap.code) : output.isPhysicalKeyDown(tap.code); }
    private void finishTap(Tap tap) {
        if (tap.mouse) { if (!wantedButtons.contains(tap.code)) releaseButton(tap.code); }
        else if (!wantedKeys.contains(tap.code)) releaseKey(tap.code);
    }

    public synchronized void releaseAll() {
        pointerTracked=false;
        pending.clear();
        running = null;
        // Finish a mouse chord while its owned modifier is still down.
        for (int button : new HashSet<>(ownedButtons)) {
            try { mouseUp(button); }
            catch (RuntimeException | LinkageError ex) {
                failure = "Input release failed: " + ex.getClass().getSimpleName();
                Diagnostics.error("bridge.release_mouse", ex);
            }
        }
        for (int key : new HashSet<>(ownedKeys)) {
            try { keyUp(key); }
            catch (RuntimeException | LinkageError ex) {
                failure = "Input release failed: " + ex.getClass().getSimpleName();
                Diagnostics.error("bridge.release_key", ex);
            }
        }
        wantedKeys.clear();
        wantedButtons.clear();
    }

    public synchronized void cancel() { releaseAll(); }
    public synchronized boolean isAvailable() { return output != null && !closed; }
    public synchronized boolean isActive() { return ready(); }
    public synchronized int pendingCount() { return pending.size() + (running == null ? 0 : 1); }
    public synchronized int heldCount() { return ownedKeys.size() + ownedButtons.size(); }
    public synchronized boolean hasOwnedMouseHold() { return !ownedButtons.isEmpty(); }
    public synchronized String getStatus() { return capabilitySummary(); }
    public synchronized String capabilitySummary() { return output == null ? failure : output.description() + (active ? "; game focused" : "; input suspended") + (failure.isEmpty() ? "" : "; " + failure); }

    private void fail(Throwable ex) {
        Diagnostics.error("bridge.input", ex);
        failure = "Input suspended: " + ex.getClass().getSimpleName();
        active = false;
        retryAt = clock.getAsLong() + 500_000_000L;
        retryPending = true;
        releaseAll();
        if (output != null) {
            try { output.observe(false); }
            catch (RuntimeException | LinkageError cleanup) { Diagnostics.error("bridge.observer_suspend", cleanup); }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        try { releaseAll(); }
        finally {
            active = false;
            closed = true;
            if (output != null) {
                try { output.close(); }
                catch (RuntimeException | LinkageError ex) { Diagnostics.error("bridge.close", ex); }
            }
        }
    }

    private static final class Tap {
        final boolean mouse;
        final int code;
        final Character character;
        final long queuedAt;
        boolean waitedForPhysical;
        Tap(boolean mouse, int code, Character character, long queuedAt) {
            this.mouse = mouse;
            this.code = code;
            this.character = character;
            this.queuedAt = queuedAt;
        }
    }

    private static final class GameSurface implements Surface {
        private float scale() {
            try { float value = Global.getSettings().getScreenScaleMult(); return value > 0f && Float.isFinite(value) ? value : 1f; }
            catch (RuntimeException ex) { return 1f; }
        }
        public boolean focused() { return Display.isCreated() && Display.isActive() && Mouse.isCreated() && Keyboard.isCreated(); }
        public float pointerX() { return Mouse.isCreated() ? Mouse.getX() / scale() : 0f; }
        public float pointerY() { return Mouse.isCreated() ? Mouse.getY() / scale() : 0f; }
        public float width() { return Display.isCreated() ? Display.getWidth() / scale() : 1f; }
        public float height() { return Display.isCreated() ? Display.getHeight() / scale() : 1f; }
        public void move(float x, float y) {
            // LWJGL performs client-to-desktop conversion itself, including Display position.
            Mouse.setCursorPosition(PointerCoordinates.toPhysical(x, scale(), Display.getWidth()), PointerCoordinates.toPhysical(y, scale(), Display.getHeight()));
        }
        public long coordinateRevision() {
            return ((long)Float.floatToIntBits(scale())<<32) ^ ((long)Display.getWidth()<<16) ^ Display.getHeight();
        }
    }
}
