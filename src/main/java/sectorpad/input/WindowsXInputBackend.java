package sectorpad.input;

import sectorpad.core.PadFrame;
import sectorpad.diagnostics.Diagnostics;
import java.util.HashSet;
import java.util.Set;

/** Passive system XInput fallback. Never enables devices, vibrates, or emits desktop input. */
public final class WindowsXInputBackend implements AutoCloseable {
    private static final String LIBRARY="sectorpad-input-windows-x86_64.dll";
    private static final long DISCOVERY_NANOS=1_000_000_000L, RETRY_NANOS=5_000_000_000L;
    private static final int[] MASKS={1,2,4,8,16,32,64,128,256,512,4096,8192,16384,32768};
    private static final String[] NAMES={"DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT","MENU","VIEW","L3","R3","LB","RB","A","B","X","Y"};
    interface Access { long open(); int read(long handle,int slot,int[] values); void close(long handle); default boolean isWine(){return false;} }
    private final Access access;
    private final boolean diagnostics;
    private final int[] values=new int[7];
    private long handle,nextScan,retryAt,generation;
    private int selected=-1,requested=-2;
    private boolean scanScheduled,retryPending,ltDown,rtDown;
    private Boolean wine;
    private String status="XInput not initialized";

    public WindowsXInputBackend(){this((String)null);}
    public WindowsXInputBackend(String nativeRoot){
        this(new Access(){
            private void load(){
                if(nativeRoot==null)ModNativeLoader.load(LIBRARY);else ModNativeLoader.loadFromDirectory(nativeRoot,LIBRARY);
            }
            public long open(){load();return nativeOpen();}
            public boolean isWine(){load();return nativeIsWine();}
            public int read(long handle,int slot,int[] values){return nativeRead(handle,slot,values);}
            public void close(long handle){nativeClose(handle);}
        },nativeRoot==null);
    }
    WindowsXInputBackend(Access access,boolean diagnostics){this.access=access;this.diagnostics=diagnostics;}

    /** A feature probe, without collecting the Wine version or environment variables. */
    public synchronized boolean isWine(){
        if(wine==null)try{
            wine=access.isWine();
            if(diagnostics)Diagnostics.state("xinput.runtime",wine?"wine":"windows");
        }catch(RuntimeException|LinkageError failure){
            wine=false;
            if(diagnostics){Diagnostics.state("xinput.runtime","unavailable");Diagnostics.error("xinput.runtime_probe_failed",failure);}
        }
        return wine;
    }

    /** requestedIndex=-1 selects automatically; explicit slots outside XInput's 0..3 stay disconnected. */
    public synchronized PadFrame poll(long now,int requestedIndex){
        if(requestedIndex!=requested){requested=requestedIndex;resetSelection();}
        if(requestedIndex < -1 || requestedIndex > 3){status="XInput supports slot values 0 through 3; use -1 for automatic discovery";return PadFrame.disconnected();}
        try {
            if(handle==0){
                if(retryPending && now-retryAt<0)return PadFrame.disconnected();
                handle=access.open();
                if(handle==0)throw new IllegalStateException("System XInputGetState is unavailable");
                retryPending=false;
                if(diagnostics){Diagnostics.state("xinput.available","true");Diagnostics.event("xinput.ready");}
            }
            if(selected>=0){
                if(read(selected))return frame();
                resetSelection();
                // Expose one disconnected frame before another slot takes over, so the input gate rearms.
                status="XInput controller disconnected; automatic discovery continues";
                return PadFrame.disconnected();
            }
            if(scanScheduled && now-nextScan<0)return PadFrame.disconnected();
            nextScan=now+DISCOVERY_NANOS;scanScheduled=true;
            for(int slot=requestedIndex<0?0:requestedIndex;slot<(requestedIndex<0?4:requestedIndex+1);slot++){
                if(read(slot)){selected=slot;generation++;ltDown=rtDown=false;return frame();}
            }
            status=requestedIndex<0?"XInput ready; waiting for a controller":"XInput ready; waiting for selected slot "+(requestedIndex+1);
        }catch(RuntimeException|LinkageError failure){
            status="XInput unavailable: "+failure.getClass().getSimpleName();
            if(diagnostics){Diagnostics.state("xinput.available","false");Diagnostics.error("xinput.failed",failure);}
            close();retryPending=true;retryAt=now+RETRY_NANOS;
        }
        return PadFrame.disconnected();
    }
    private boolean read(int slot){
        int result=access.read(handle,slot,values);
        if(diagnostics)Diagnostics.state("xinput.result",Integer.toString(result));
        return result==0;
    }
    private PadFrame frame(){
        Set<String> buttons=new HashSet<>();
        for(int i=0;i<MASKS.length;i++)if((values[0]&MASKS[i])!=0)buttons.add(NAMES[i]);
        float lt=trigger(values[5]),rt=trigger(values[6]);
        ltDown=ltDown?lt>.35f:lt>.55f;rtDown=rtDown?rt>.35f:rt>.55f;
        if(ltDown)buttons.add("LT");if(rtDown)buttons.add("RT");
        status="Windows XInput / controller slot "+(selected+1);
        return new PadFrame(true,"xinput:"+selected+":"+generation,"XInput controller "+(selected+1),true,
                axis(values[1]),axis(values[2]),axis(values[3]),axis(values[4]),lt,rt,buttons);
    }
    private static float axis(int value){return Math.max(-1f,Math.min(1f,value/(value<0?32768f:32767f)));}
    private static float trigger(int value){return Math.max(0f,Math.min(1f,value/255f));}
    private void resetSelection(){selected=-1;scanScheduled=false;ltDown=rtDown=false;}
    public synchronized String status(){return status;}
    public synchronized void requestReconnect(){close();status="XInput reconnect requested";}
    @Override public synchronized void close(){
        long previous=handle;handle=0;resetSelection();retryPending=false;
        if(previous!=0)try{access.close(previous);}catch(RuntimeException|LinkageError failure){if(diagnostics)Diagnostics.error("xinput.close_failed",failure);}
    }
    private static native long nativeOpen();
    private static native boolean nativeIsWine();
    private static native int nativeRead(long handle,int slot,int[] values);
    private static native void nativeClose(long handle);
}
