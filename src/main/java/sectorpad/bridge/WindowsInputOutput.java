package sectorpad.bridge;

import sectorpad.input.ModNativeLoader;

/** Mod-local JNI SendInput; passive physical state never includes injected events or text. */
final class WindowsInputOutput implements DesktopInputBridge.Output {
    static final String LIBRARY="sectorpad-input-windows-x86_64.dll";
    private final int[] virtualKeys=new int[256];
    private long handle;
    private boolean observing;
    private boolean closed;
    WindowsInputOutput() { this(false); }
    WindowsInputOutput(boolean alreadyLoaded) {
        if(!alreadyLoaded) ModNativeLoader.load(LIBRARY);
        if(nativeAbiVersion()!=2) throw new IllegalStateException("Unsupported SectorPad native input ABI");
        for(int key=0;key<virtualKeys.length;key++) virtualKeys[key]=KeyCodes.windows(key);
        handle=nativeOpen();
        if(handle==0) throw new IllegalStateException("Windows physical input observer could not start");
    }
    public void observe(boolean active) {
        if (closed) throw new IllegalStateException("Windows input observer is closed");
        if (handle == 0 || !nativeAlive(handle)) {
            observing = false;
            if (!active) return;
            // The bridge rate-limits activation attempts. Recreate only a dead observer, never a focus race.
            if (handle != 0) { long previous = handle; handle = 0; nativeClose(previous); }
            handle = nativeOpen();
            if (handle == 0) throw new IllegalStateException("Windows input observer restart failed");
        }
        if(active==observing) return;
        if(!nativeObserve(handle,active,virtualKeys)) throw new IllegalStateException(nativeAlive(handle)
                ? "Windows foreground focus changed before observer activation" : "Windows physical input observer stopped");
        observing=active;
    }
    public boolean acceptsFocus() { return !closed && nativeFocused(); }
    public boolean isOperational() { return !closed && handle != 0 && nativeAlive(handle); }
    // Last known physical ownership remains readable while a stopped observer is released and closed.
    public boolean isPhysicalKeyDown(int key) {ensureHandle();return key>0 && key<256 && nativeKeyHeld(handle,key);}
    public boolean isPhysicalMouseDown(int button) {ensureHandle();return button>=0 && button<3 && nativeButtonHeld(handle,button);}
    public long keyReleaseSequence(int key) {ensureOpen();return key>0 && key<256 ? nativeKeyRelease(handle,key) : 0;}
    public long mouseReleaseSequence(int button) {ensureOpen();return button>=0 && button<3 ? nativeButtonRelease(handle,button) : 0;}
    public boolean preservesPhysicalHolds() {return true;}
    public void key(int key,boolean down) {
        if(down)ensureOpen();else ensureHandle(); if(!KeyCodes.supported(key)) throw new IllegalArgumentException("Unsupported key "+key);
        checked(nativeKey(handle,key,down));
    }
    public void mouse(int button,boolean down) {
        if(down)ensureOpen();else ensureHandle(); if(button<0 || button>2) throw new IllegalArgumentException("Unsupported button "+button);
        checked(nativeMouse(handle,button,down));
    }
    public void wheel(int notches) {ensureOpen();checked(nativeWheel(handle,Math.max(-12,Math.min(12,notches))));}
    public boolean supportsUnicode() {return true;}
    public void unicode(char character) {ensureOpen();checked(nativeUnicode(handle,character));}
    public String description() {return "Windows native input; Unicode; physical holds preserved";}
    private void ensureHandle() {if(closed || handle==0)throw new IllegalStateException("Windows input observer is unavailable");}
    private void ensureOpen() {ensureHandle();if(!nativeAlive(handle))throw new IllegalStateException("Windows input observer is unavailable");}
    private static void checked(int error) {if(error!=0)throw new IllegalStateException("Windows input failed or game lost focus (code "+error+")");}
    @Override public void close() {closed=true;if(handle!=0){long closing=handle;handle=0;observing=false;nativeClose(closing);}}

    private static native int nativeAbiVersion();
    private static native long nativeOpen();
    private static native boolean nativeAlive(long handle);
    private static native boolean nativeFocused();
    private static native boolean nativeObserve(long handle,boolean active,int[] virtualKeys);
    private static native boolean nativeKeyHeld(long handle,int key);
    private static native boolean nativeButtonHeld(long handle,int button);
    private static native long nativeKeyRelease(long handle,int key);
    private static native long nativeButtonRelease(long handle,int button);
    private static native int nativeKey(long handle,int key,boolean down);
    private static native int nativeMouse(long handle,int button,boolean down);
    private static native int nativeWheel(long handle,int notches);
    private static native int nativeUnicode(long handle,char character);
    private static native void nativeClose(long handle);
}
