package sectorpad.input;

import sectorpad.core.PadFrame;

public final class WindowsXInputTests {
    public static void main(String[] args){
        Fake io=new Fake();WindowsXInputBackend backend=new WindowsXInputBackend(io,false);
        check(!backend.poll(-20, -1).connected() && io.reads==4,"Startup probes every XInput slot even with negative monotonic time");
        io.frames[2]=new int[7];
        check(!backend.poll(900_000_000L,-1).connected() && io.reads==4,"Disconnected scans are bounded to one per second");
        PadFrame first=backend.poll(1_000_000_000L,-1);
        check(first.connected() && first.deviceId().startsWith("xinput:2:"),"Late runner device appears on next scan");
        io.frames[0]=new int[7];
        check(backend.poll(1_000_000_001L,-1).deviceId().equals(first.deviceId()),"Automatic mode preserves current slot when a lower slot appears");
        io.frames[2]=null;
        check(!backend.poll(1_000_000_002L,-1).connected(),"Disconnect is exposed before another slot takes over");
        check(backend.poll(1_000_000_003L,-1).deviceId().startsWith("xinput:0:"),"Other slot discovers after neutral disconnect frame");
        check(!backend.poll(1_000_000_004L,3).connected(),"Explicit selection never falls back to another connected slot");
        int reads=io.reads;
        check(!backend.poll(1_000_000_005L,7).connected() && io.reads==reads,"SDL-only indices cannot accidentally select XInput slot zero");

        io.frames[1]=new int[]{0xf3ff,-32768,32767,16384,-16384,255,255};
        PadFrame full=backend.poll(2_000_000_000L,1);
        check(full.lx()==-1 && full.ly()==1 && full.rx()>0 && full.ry()<0,"Signed axes preserve native upward-positive Y and asymmetric endpoints");
        check(full.independentTriggers() && full.lt()==1 && full.rt()==1 && full.buttons().size()==16,"Both triggers and all 14 documented buttons coexist");
        io.frames[1][5]=110;io.frames[1][6]=80;
        PadFrame hysteresis=backend.poll(2_000_000_001L,1);
        check(hysteresis.down("LT") && !hysteresis.down("RT"),"Trigger release hysteresis is independent");
        backend.requestReconnect();
        PadFrame fresh=backend.poll(2_000_000_002L,1);
        check(!fresh.down("LT") && !fresh.deviceId().equals(full.deviceId()),"Explicit reconnect clears hysteresis and changes connection identity");
        check(io.closes==1 && io.opens==2,"Reconnect closes and reopens the system reader once");
        backend.close();backend.close();check(io.closes==2,"Close is idempotent");

        Fake failed=new Fake();failed.openFailure=true;
        WindowsXInputBackend retry=new WindowsXInputBackend(failed,false);
        check(!retry.poll(-6_000_000_000L,-1).connected(),"Unavailable XInput is contained");
        retry.poll(-1_000_000_001L,-1);check(failed.opens==1,"Native load failure respects bounded retry across negative times");
        failed.openFailure=false;failed.frames[0]=new int[7];
        check(retry.poll(-1_000_000_000L,-1).connected() && failed.opens==2,"Native API recovery reopens after five seconds");
        failed.readFailure=true;
        check(!retry.poll(0,-1).connected() && failed.closes==1,"Poll linkage failure closes native reader before retry");
        retry.close();

        Fake wine=new Fake();wine.wine=true;WindowsXInputBackend runtime=new WindowsXInputBackend(wine,false);
        check(runtime.isWine() && runtime.isWine() && wine.runtimeReads==1 && wine.opens==0,"Wine feature probe is cached and does not open a controller reader");
        Fake unavailable=new Fake();unavailable.runtimeFailure=true;
        check(!new WindowsXInputBackend(unavailable,false).isWine(),"Wine probe failure is contained");
        System.out.println("WindowsXInputTests: 20 selection, snapshot, reconnect, failure and runtime checks passed");
    }
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    private static final class Fake implements WindowsXInputBackend.Access {
        final int[][] frames=new int[4][];
        int opens,closes,reads,runtimeReads;
        boolean openFailure,readFailure,wine,runtimeFailure;
        public long open(){opens++;if(openFailure)throw new UnsatisfiedLinkError("Injected load failure");return opens;}
        public int read(long handle,int slot,int[] values){
            reads++;if(readFailure)throw new UnsatisfiedLinkError("Injected read failure");
            if(frames[slot]==null)return 1167;
            System.arraycopy(frames[slot],0,values,0,7);return 0;
        }
        public void close(long handle){closes++;}
        public boolean isWine(){runtimeReads++;if(runtimeFailure)throw new UnsatisfiedLinkError("Injected runtime probe failure");return wine;}
    }
}
