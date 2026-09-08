package sectorpad.bridge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone headless tests; never construct Robot, native hooks, or a game window. */
public final class BridgeTests {
    public static void main(String[] args) {
        // Injected failures exercise production diagnostics, without activating the game's file appender.
        System.setProperty("log4j.defaultInitOverride", "true");
        org.apache.log4j.Logger.getRootLogger().addAppender(new org.apache.log4j.varia.NullAppender());
        pacedTaps();
        tapAndDirectOwnership();
        physicalOverlap();
        queuedPhysicalRelease();
        queuedPhysicalTimeout();
        queuedPhysicalCancellation();
        queuedTapOwnership();
        physicalReleaseWhileModHeld();
        focusAndContext();
        pointerTransform();
        fractionalPointerAndHandoff();
        unsupportedAndCapacity();
        failedReleaseIsolation();
        failedOutputClose();
        authoritativeForeground();
        splitFocusDiagnostics();
        observerRetry();
        stoppedObserverRecovery();
        System.out.println("BridgeTests: 18 lifecycle and coordinate scenarios passed");
    }

    private static void pacedTaps() {
        Fixture f = new Fixture();
        f.bridge.keyTap(30); f.bridge.keyTap(31);
        check(f.output.events.isEmpty(), "Taps must not emit synchronously");
        f.pump(); eq(f.output.events,List.of("k30+"));
        f.now.set(20_000_000); f.pump(); eq(f.output.events,List.of("k30+"));
        f.now.set(40_000_000); f.pump(); eq(f.output.events,List.of("k30+","k30-"));
        f.pump(); eq(f.output.events,List.of("k30+","k30-","k31+"));
        f.bridge.releaseAll(); eq(f.output.events,List.of("k30+","k30-","k31+","k31-"));
    }

    private static void physicalOverlap() {
        Fixture f = new Fixture();
        f.output.physicalKeys.add(30); f.bridge.keyDown(30); f.bridge.keyUp(30);
        check(f.output.events.isEmpty(), "An already physical hold must not be pressed/released by mod");
        f.bridge.keyDown(31); f.output.physicalKeys.add(31); f.bridge.keyUp(31);
        eq(f.output.events,List.of("k31+"));
        f.bridge.mouseDown(0); f.output.physicalButtons.add(0); f.bridge.mouseUp(0);
        eq(f.output.events,List.of("k31+","m0+"));
        check(f.bridge.heldCount()==0,"Physical takeover must drop mod ownership");
    }

    private static void tapAndDirectOwnership() {
        Fixture f=new Fixture();
        f.bridge.mouseClick(0); f.pump(); f.bridge.mouseUp(0);
        eq(f.output.events,List.of("m0+"));
        f.now.set(40_000_000); f.pump(); eq(f.output.events,List.of("m0+","m0-"));
        f.bridge.keyTap(30); f.pump(); f.bridge.keyDown(30);
        eq(f.output.events,List.of("m0+","m0-","k30+"));
        f.now.set(80_000_000); f.pump();
        eq(f.output.events,List.of("m0+","m0-","k30+"));
        f.bridge.keyUp(30); eq(f.output.events,List.of("m0+","m0-","k30+","k30-"));
    }

    private static void queuedPhysicalRelease() {
        Fixture f=new Fixture();
        f.output.physicalButtons.add(0); f.output.physicalKeys.add(30);
        f.bridge.mouseClick(0); f.bridge.keyTap(30);
        f.pump(); f.now.set(100_000_000); f.pump();
        check(f.bridge.pendingCount()==2 && f.bridge.heldCount()==0,"Physical holds must leave taps queued without acquiring ownership");
        check(f.output.events.isEmpty(),"Waiting cannot duplicate a physical down or release it");
        f.output.physicalButtons.remove(0); f.output.mouseRelease[0]++; f.pump(); f.pump();
        eq(f.output.events,List.of("m0+"));
        check(f.bridge.hasOwnedMouseHold(),"A dispatched queued click must retain its paced hold");
        f.now.set(140_000_000); f.pump(); f.pump();
        eq(f.output.events,List.of("m0+","m0-"));
        check(f.bridge.pendingCount()==1,"The next queued key must wait for its own physical release");
        f.output.physicalKeys.remove(30); f.output.keyRelease[30]++; f.pump(); f.pump();
        eq(f.output.events,List.of("m0+","m0-","k30+"));
        f.now.set(180_000_000); f.pump();
        eq(f.output.events,List.of("m0+","m0-","k30+","k30-"));
        check(f.bridge.pendingCount()==0 && f.bridge.heldCount()==0,"Released physical taps must finish exactly once");
    }

    private static void queuedPhysicalTimeout() {
        Fixture f=new Fixture(); f.output.physicalButtons.add(0);
        f.bridge.mouseClick(0); f.bridge.keyTap(31); f.pump();
        f.now.set(1_999_999_999L); f.pump();
        check(f.bridge.pendingCount()==2,"Physical wait must remain bounded without dropping an early release");
        f.now.set(2_000_000_000L); f.pump();
        check(f.bridge.pendingCount()==1 && f.output.events.isEmpty(),"An expired physical wait must cancel without output");
        check(f.output.physicalButtons.contains(0),"Timeout cannot release the physical hold");
        f.output.physicalButtons.remove(0); f.pump();
        eq(f.output.events,List.of("k31+"));
        f.now.set(2_040_000_000L); f.pump(); f.pump();
        eq(f.output.events,List.of("k31+","k31-"));
        check(f.bridge.pendingCount()==0,"An expired click cannot reappear after physical release");

        Fixture late=new Fixture(); late.output.physicalKeys.add(30);
        late.bridge.keyTap(30); late.pump();
        late.now.set(2_100_000_000L); late.output.physicalKeys.remove(30); late.pump();
        check(late.bridge.pendingCount()==0 && late.output.events.isEmpty(),"A release between pumps cannot revive a timed-out tap");
    }

    private static void queuedPhysicalCancellation() {
        for(int reason=0;reason<4;reason++) {
            Fixture f=new Fixture(); f.output.physicalButtons.add(0); f.output.physicalKeys.add(30);
            f.bridge.mouseClick(0); f.bridge.keyTap(30); f.pump();
            if(reason==0) { f.surface.focused=false; f.pump(); f.surface.focused=true; f.pump(); }
            else if(reason==1) { f.bridge.pump(true,"modal"); f.bridge.pump(true,"modal"); }
            else if(reason==2) { f.bridge.pump(false,"ui"); f.pump(); }
            else { f.bridge.cancel(); f.pump(); }
            check(f.bridge.pendingCount()==0 && f.bridge.heldCount()==0,"Focus, context, disable, and explicit cancellation must clear waiting taps");
            check(f.output.physicalButtons.contains(0) && f.output.physicalKeys.contains(30),"Cancellation cannot change physical ownership");
            f.output.physicalButtons.clear(); f.output.physicalKeys.clear();
            if(reason==1) f.bridge.pump(true,"modal"); else f.pump();
            check(f.output.events.isEmpty(),"Cancelled taps cannot emit after focus/context rearm");
        }
    }

    private static void queuedTapOwnership() {
        Fixture own=new Fixture(); own.bridge.keyDown(30); own.bridge.mouseDown(0);
        own.bridge.keyTap(30); own.bridge.mouseClick(0); own.pump(); own.pump();
        eq(own.output.events,List.of("k30+","m0+"));
        check(own.bridge.pendingCount()==0 && own.bridge.heldCount()==2 && own.bridge.hasOwnedMouseHold(),"Queued taps cannot duplicate or shorten continuous mod holds");
        own.bridge.releaseAll(); eq(own.output.events,List.of("k30+","m0+","m0-","k30-"));

        Fixture f=new Fixture(); f.output.physicalButtons.add(0); f.bridge.mouseClick(0); f.pump();
        f.output.physicalButtons.remove(0); f.pump(); f.output.physicalButtons.add(0);
        f.now.set(40_000_000); f.pump();
        eq(f.output.events,List.of("m0+"));
        check(!f.bridge.hasOwnedMouseHold() && f.output.disownedButtons.contains(0),"Physical mouse takeover must relinquish a delayed tap without releasing the physical hold");
        f.output.physicalKeys.add(30); f.bridge.keyTap(30); f.pump();
        f.output.physicalKeys.remove(30); f.pump(); f.output.physicalKeys.add(30);
        f.now.set(80_000_000); f.pump();
        eq(f.output.events,List.of("m0+","k30+"));
        check(f.bridge.heldCount()==0,"Physical key takeover must relinquish a delayed tap without emitting key-up");
    }

    private static void physicalReleaseWhileModHeld() {
        Fixture f=new Fixture();
        f.output.physicalKeys.add(30); f.bridge.keyDown(30);
        f.output.physicalKeys.remove(30); f.output.keyRelease[30]++; f.pump();
        eq(f.output.events,List.of("k30+"));
        f.output.physicalKeys.add(30); f.pump();
        f.output.physicalKeys.remove(30); f.output.keyRelease[30]++; f.pump();
        eq(f.output.events,List.of("k30+","k30+"));
        f.bridge.keyUp(30); eq(f.output.events,List.of("k30+","k30+","k30-"));
    }

    private static void focusAndContext() {
        Fixture f=new Fixture(); f.bridge.keyDown(30); f.bridge.mouseDown(0); f.bridge.keyTap(31);
        f.surface.focused=false; check(!f.pump(),"Focus loss must suspend");
        eq(f.output.events,List.of("k30+","m0+","m0-","k30-"));
        f.bridge.keyDown(32); f.bridge.scroll(2); f.bridge.movePointer(10,20);
        check(f.bridge.pendingCount()==0 && f.bridge.heldCount()==0,"Focus loss clears pending and holds");
        f.surface.focused=true; f.pump(); f.bridge.mouseDown(1);
        check(!f.bridge.pump(true,"modal"),"Context switch must insert a neutral frame");
        check(f.bridge.heldCount()==0,"Context switch releases holds");
        check(f.bridge.pump(true,"modal"),"Next context frame can become active");
        f.bridge.close(); check(!f.bridge.pump(true,"modal"),"Closed bridge cannot rearm");
    }

    private static void pointerTransform() {
        Fixture f=new Fixture(); f.bridge.movePointer(-10,2000);
        check(f.surface.x==0 && f.surface.y==719,"Pointer must clamp in logical UI units");
        f.bridge.movePointer(Float.NaN,Float.POSITIVE_INFINITY);
        check(f.surface.x==0 && f.surface.y==0,"Non-finite input cannot escape clamp");
        check(PointerCoordinates.toPhysical(600,1.5f,1920)==900,"UI scale must transform to physical client pixels");
        check(PointerCoordinates.toPhysical(1280,1.5f,1920)==1919,"Physical edge clamps");
    }

    private static void unsupportedAndCapacity() {
        Fixture f=new Fixture(); f.bridge.keyTap(219); f.bridge.keyTap(-1); f.bridge.mouseClick(8);
        check(f.bridge.pendingCount()==0,"OS meta and invalid keys are excluded");
        for(int i=0;i<400;i++) f.bridge.keyTap(30);
        check(f.bridge.pendingCount()==256,"Output queue is bounded");
        check(!f.bridge.typeText("overflow"),"Text cannot overrun output capacity");
        f.bridge.releaseAll(); check(f.bridge.typeText("\u03A9"),"Unicode can be queued without a keyboard-layout guess");
        f.pump(); eq(f.output.events,List.of("u937"));
    }

    private static void fractionalPointerAndHandoff() {
        AtomicLong now=new AtomicLong(); FakeSurface surface=new FakeSurface();surface.quantized=true;
        DesktopInputBridge bridge=new DesktopInputBridge(surface,new FakeOutput(),now::get);bridge.pump(true,"ui");
        for(int i=0;i<20;i++)bridge.movePointerBy(.1f,0);
        check(Math.abs(surface.x-642)<.01f,"Subpixel joystick deltas must accumulate across frames");
        surface.x=900;bridge.movePointerBy(.2f,0);
        check(surface.x==900,"Physical pointer takeover must reset the old accumulated target");
        surface.revision++;surface.x=300;bridge.movePointerBy(.2f,0);
        check(surface.x==300,"Scale/coordinate changes must reset accumulated motion");
        bridge.movePointerBy(.2f,0);bridge.pump(true,"modal");bridge.pump(true,"modal");bridge.movePointerBy(.2f,0);
        check(surface.x==300,"A context transition cannot retain fractions from the preceding UI");
    }

    private static void failedReleaseIsolation() {
        for (boolean linkage : new boolean[] { false, true }) {
            Fixture f = new Fixture();
            f.bridge.keyDown(30); f.bridge.keyDown(31); f.bridge.mouseDown(0); f.bridge.mouseDown(1);
            f.output.failedReleaseKey = 30; f.output.failedReleaseMouse = 0; f.output.linkageFailure = linkage;
            f.bridge.releaseAll();
            check(f.output.events.contains("m1-") && f.output.events.contains("k31-"), "Failed mouse/key release must not skip other owned releases");
            check(f.bridge.heldCount() == 2, "Failed release remains owned for cleanup retry");
            f.bridge.close();
            check(f.output.closeCalls == 1, "Native output close must still run after failed release retries");
            check(!f.bridge.pump(true, "ui"), "Failed cleanup cannot rearm a closed bridge");
            f.bridge.close();
            check(f.output.closeCalls == 1, "Closing a failed bridge is idempotent");
        }
    }

    private static void failedOutputClose() {
        for (boolean linkage : new boolean[] { false, true }) {
            Fixture f = new Fixture(); f.bridge.keyDown(30);
            f.output.closeFailure = true; f.output.linkageFailure = linkage;
            f.bridge.close();
            check(f.output.events.contains("k30-"), "Input releases precede an output close failure");
            check(!f.bridge.pump(true, "ui") && !f.bridge.isAvailable(), "Output close failure is contained and leaves bridge closed");
            f.bridge.close(); check(f.output.closeCalls == 1, "Failed output close does not recur each frame");
        }
    }

    private static void authoritativeForeground() {
        Fixture f = new Fixture(); f.bridge.keyDown(30); f.bridge.mouseDown(0);
        check(f.bridge.hasGameFocus(), "Direct game actions accept matching surface and native foreground");
        f.output.acceptsFocus = false;
        check(!f.bridge.hasGameFocus(), "Direct game actions reject native foreground mismatch before a bridge pump");
        check(!f.pump(), "Native foreground mismatch must suspend even while LWJGL reports active");
        check(f.bridge.heldCount() == 0, "Authoritative foreground loss releases held input");
        int activations = f.output.activationCalls;
        for (int i = 0; i < 1_000; i++) f.pump();
        check(f.output.activationCalls == activations, "Foreground mismatch must not repeatedly activate observer");
        f.bridge.mouseClick(0); f.bridge.keyDown(31);
        check(f.bridge.pendingCount() == 0 && !f.output.events.contains("k31+"), "Native foreground loss cannot queue or emit input");
        f.output.acceptsFocus = true;
        check(f.pump(), "Returning Windows foreground can rearm without process restart");
        check(f.bridge.hasGameFocus(), "Direct game actions recover their authoritative focus gate");
        f.surface.focused = false;
        check(!f.bridge.hasGameFocus(), "Direct game actions also reject ordinary surface focus loss");
        f.surface.focused = true; f.bridge.close();
        check(!f.bridge.hasGameFocus(), "A closed bridge cannot authorize direct game actions");
    }

    private static void splitFocusDiagnostics() {
        Fixture f=new Fixture();f.output.acceptsFocus=false;
        check(!f.bridge.hasGameFocus(),"Native mismatch stays rejected while recording focus diagnostics");
        String nativeMismatch=sectorpad.diagnostics.Diagnostics.snapshot();
        check(nativeMismatch.contains("\"bridge.surface_focus\":\"true\"") && nativeMismatch.contains("\"bridge.native_focus\":\"false\""),"Reports distinguish LWJGL focus from native foreground mismatch");
        f.surface.focused=false;f.bridge.hasGameFocus();
        String surfaceMismatch=sectorpad.diagnostics.Diagnostics.snapshot();
        check(surfaceMismatch.contains("\"bridge.surface_focus\":\"false\"") && surfaceMismatch.contains("\"bridge.native_focus\":\"not_queried\""),"Surface focus loss cannot report stale native focus");
        f.bridge.close();
    }

    private static void observerRetry() {
        Fixture f = new Fixture();
        f.surface.focused = false; f.pump(); f.surface.focused = true;
        f.output.observeFailure = true;
        check(!f.pump(), "Observer activation failure is contained");
        int attempts = f.output.activationCalls;
        for (int i = 0; i < 1_000; i++) f.pump();
        check(f.output.activationCalls == attempts, "Same-frame pump duplicates do not retry failed observer");
        f.now.set(499_999_999L); f.pump();
        check(f.output.activationCalls == attempts, "Failed observer activation backs off for half a second");
        f.now.set(500_000_000L); f.pump();
        check(f.output.activationCalls == attempts + 1, "A bounded observer retry occurs after delay");
        f.output.observeFailure = false; f.now.set(1_000_000_000L);
        check(f.pump(), "Recovered observer can resume after neutral cleanup and retry delay");
        check(f.bridge.getStatus().indexOf("Input suspended") < 0, "Successful retry clears stale failure status");
    }

    private static void stoppedObserverRecovery() {
        Fixture f = new Fixture(); f.output.operational = false;
        check(!f.pump(), "Stopped observer is detected during an idle frame without waiting for an input action");
        int attempts = f.output.activationCalls;
        f.pump(); check(f.output.activationCalls == attempts, "Stopped observer recovery respects backoff");
        f.now.set(500_000_000L);
        check(f.pump() && f.output.operational, "Next throttled activation restarts a stopped observer");
    }

    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void eq(Object actual,Object expected) { check(actual.equals(expected),"Expected "+expected+" but got "+actual); }
    private static final class Fixture {
        final AtomicLong now=new AtomicLong(); final FakeSurface surface=new FakeSurface(); final FakeOutput output=new FakeOutput();
        final DesktopInputBridge bridge=new DesktopInputBridge(surface,output,now::get);
        Fixture(){ bridge.pump(true,"ui"); }
        boolean pump(){ return bridge.pump(true,"ui"); }
    }
    private static final class FakeSurface implements DesktopInputBridge.Surface {
        boolean focused=true,quantized; float x=640,y=360;long revision;
        public boolean focused(){return focused;} public float pointerX(){return x;} public float pointerY(){return y;}
        public float width(){return 1280;} public float height(){return 720;} public long coordinateRevision(){return revision;}
        public void move(float x,float y){this.x=quantized?Math.round(x):x;this.y=quantized?Math.round(y):y;}
    }
    private static final class FakeOutput implements DesktopInputBridge.Output {
        int failedReleaseKey = -1, failedReleaseMouse = -1, closeCalls, activationCalls;
        boolean linkageFailure, closeFailure, observeFailure, acceptsFocus = true, operational = true;
        final List<String> events=new ArrayList<>(); final Set<Integer> physicalKeys=new HashSet<>(), physicalButtons=new HashSet<>();
        final Set<Integer> disownedButtons=new HashSet<>();
        final long[] keyRelease=new long[256],mouseRelease=new long[8];
        public boolean acceptsFocus(){return acceptsFocus;}
        public boolean isOperational(){return operational;}
        public void observe(boolean active){if(active)activationCalls++; if(observeFailure)fail(); if(active)operational=true;} public boolean isPhysicalKeyDown(int key){return physicalKeys.contains(key);}
        public boolean isPhysicalMouseDown(int button){return physicalButtons.contains(button);} public boolean preservesPhysicalHolds(){return true;}
        public long keyReleaseSequence(int key){return keyRelease[key];}
        public long mouseReleaseSequence(int button){return mouseRelease[button];}
        public void disownMouse(int button){disownedButtons.add(button);}
        public void key(int key,boolean down){if (!down && key == failedReleaseKey) fail(); events.add("k"+key+(down?"+":"-"));}
        public void mouse(int button,boolean down){if (!down && button == failedReleaseMouse) fail(); events.add("m"+button+(down?"+":"-"));}
        public void wheel(int notches){events.add("w"+notches);} public boolean supportsUnicode(){return true;}
        public void unicode(char c){events.add("u"+(int)c);} public String description(){return "test";}
        public void close(){closeCalls++; if (closeFailure) fail();}
        private void fail(){if (linkageFailure) throw new UnsatisfiedLinkError("Injected native failure"); throw new IllegalStateException("Injected output failure");}
    }
}
