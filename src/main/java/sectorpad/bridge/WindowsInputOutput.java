package sectorpad.bridge;

import sectorpad.input.ModNativeLoader;

/** Mod-local JNI SendInput; passive physical state never includes injected events or text. */
final class WindowsInputOutput implements DesktopInputBridge.Output {
    static final String LIBRARY="sectorpad-input-windows-x86_64.dll";
    private final int[] virtualKeys=new int[256];
    private long handle;
    private boolean observing;
    WindowsInputOutput() { this(false); }
    WindowsInputOutput(boolean alreadyLoaded) {
        if(!alreadyLoaded) ModNativeLoader.load(LIBRARY);
        if(nativeAbiVersion()!=1) throw new IllegalStateException("Unsupported SectorPad native input ABI");
        for(int key=0;key<virtualKeys.length;key++) virtualKeys[key]=KeyCodes.windows(key);
        handle=nativeOpen();
        if(handle==0) throw new IllegalStateException("Windows physical input observer could not start");
    }
    public void observe(boolean active) {
        ensureOpen();
        if(active==observing) return;
        if(!nativeObserve(handle,active,virtualKeys)) throw new IllegalStateException("Windows physical input observer stopped");
        observing=active;
    }
    public boolean isPhysicalKeyDown(int key) {ensureOpen();return key>0 && key<256 && nativeKeyHeld(handle,key);}
    public boolean isPhysicalMouseDown(int button) {ensureOpen();return button>=0 && button<3 && nativeButtonHeld(handle,button);}
    public long keyReleaseSequence(int key) {ensureOpen();return key>0 && key<256 ? nativeKeyRelease(handle,key) : 0;}
    public long mouseReleaseSequence(int button) {ensureOpen();return button>=0 && button<3 ? nativeButtonRelease(handle,button) : 0;}
    public boolean preservesPhysicalHolds() {return true;}
    public void key(int key,boolean down) {
        ensureOpen(); if(!KeyCodes.supported(key)) throw new IllegalArgumentException("Unsupported key "+key);
        checked(nativeKey(handle,key,down));
    }
    public void mouse(int button,boolean down) {
        ensureOpen(); if(button<0 || button>2) throw new IllegalArgumentException("Unsupported button "+button);
        checked(nativeMouse(handle,button,down));
    }
    public void wheel(int notches) {ensureOpen();checked(nativeWheel(handle,Math.max(-12,Math.min(12,notches))));}
    public boolean supportsUnicode() {return true;}
    public void unicode(char character) {ensureOpen();checked(nativeUnicode(handle,character));}
    public String description() {return "Windows native input; Unicode; physical holds preserved";}
    private void ensureOpen() {if(handle==0 || !nativeAlive(handle))throw new IllegalStateException("Windows input observer is unavailable");}
    private static void checked(int error) {if(error!=0)throw new IllegalStateException("Windows input failed or game lost focus (code "+error+")");}
    @Override public void close() {if(handle!=0){long closing=handle;handle=0;observing=false;nativeClose(closing);}}

    private static native int nativeAbiVersion();
    private static native long nativeOpen();
    private static native boolean nativeAlive(long handle);
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
