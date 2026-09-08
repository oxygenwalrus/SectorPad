package sectorpad.input;

import sectorpad.core.PadFrame;
import java.util.Set;

public final class ControllerBackendTests {
    private static int checks;
    private static final class Port implements ControllerBackend.Port {
        PadFrame frame=PadFrame.disconnected();int polls,reconnects,closes,index;
        public PadFrame poll(long now,int slot){polls++;index=slot;return frame;}
        public void reconnect(){reconnects++;}
        public String status(){return "fixture";}
        public void close(){closes++;}
        void connect(String id){frame=new PadFrame(true,id,id,true,0,0,0,0,0,0,Set.of());}
    }
    public static void main(String[] args){
        System.setProperty("log4j.defaultInitOverride", "true");
        org.apache.log4j.Logger.getRootLogger().addAppender(new org.apache.log4j.varia.NullAppender());
        Port sd=new Port(),xi=new Port();sd.connect("sdl");xi.connect("xi");
        ControllerBackend nativeWindows=new ControllerBackend(sd,xi,true,()->false);
        check(nativeWindows.poll(0).deviceId().equals("sdl"),"native Windows preserves SDL preference");
        check(xi.polls==0,"one input source; unused XInput is not polled");
        sd.frame=PadFrame.disconnected();
        check(nativeWindows.poll(1).deviceId().equals("xi"),"Windows discovers XInput when SDL has no controller");
        sd.connect("sdl");
        check(nativeWindows.poll(2).deviceId().equals("xi"),"late SDL pad does not steal active XInput ownership");
        nativeWindows.requestReconnect();
        check(!nativeWindows.poll(3).connected(),"manual reconnect guarantees a disconnected release frame");
        check(sd.reconnects==1&&xi.reconnects==1,"reconnect resets both discovery APIs");
        check(nativeWindows.poll(4).deviceId().equals("sdl"),"reconnect restores discovery preference");
        ControllerBackend wine=new ControllerBackend(sd,xi,true,()->true);
        check(wine.poll(0).deviceId().equals("xi"),"Wine uses XInput from startup even if SDL looks connected");
        wine.configure("SDL",-1);check(!wine.poll(1).connected(),"backend setting change releases first");
        check(wine.poll(2).deviceId().equals("sdl"),"explicit SDL remains available in Wine");
        wine.configure("XInput",2);wine.poll(3);
        check(wine.poll(4).deviceId().equals("xi")&&xi.index==2,"explicit XInput slot reaches selected API");
        xi.frame=PadFrame.disconnected();int before=sd.polls;
        check(!wine.poll(5).connected()&&sd.polls==before,"explicit backend never silently falls back");
        wine.configure("Automatic",2);wine.poll(6);before=sd.polls;
        check(!wine.poll(7).connected()&&sd.polls==before,"explicit slot never maps to another API's device");
        wine.configure("Automatic",-1);wine.poll(8);
        check(wine.poll(9).deviceId().equals("sdl"),"Wine automatic slot falls back to SDL");
        ControllerBackend linux=new ControllerBackend(sd,xi,false,()->{throw new AssertionError("Wine DLL probe on Linux");});
        check(linux.poll(0).deviceId().equals("sdl"),"Linux does not probe Windows libraries");
        linux.configure("XInput",-1);linux.poll(1);
        check(!linux.poll(2).connected()&&linux.status().contains("requires"),"unsupported native Linux selection has actionable status");
        linux.configure("invalid",100);linux.poll(3);
        check(linux.poll(4).connected()&&sd.index==-1,"invalid configuration returns to automatic safely");
        linux.close();check(sd.closes==1&&xi.closes==1,"both sources close");
        Port testSd=new Port(),testXi=new Port();testSd.connect("test-sdl");testXi.connect("test-xinput");
        ControllerBackend discovery=new ControllerBackend(testSd,testXi,true,()->false);
        discovery.startDiscoveryTest(100);
        check(!discovery.poll(100).connected(),"discovery test begins behind a release boundary");
        check(discovery.poll(101).connected()&&discovery.status().contains("connected / idle"),"test observes neutral devices through both APIs");
        testXi.frame=new PadFrame(true,"test-xinput","test-xinput",true,0,0,0,0,0,0,Set.of("A"));
        discovery.poll(102);
        check(discovery.status().contains("XInput input seen"),"test records input activity without merging it into SDL gameplay");
        discovery.poll(10_000_000_100L);
        check(discovery.status().startsWith("Last test:")&&discovery.status().contains("XInput input seen"),"completed discovery result remains visible");
        discovery.poll(10_000_000_101L);
        check(discovery.status().startsWith("Last test:"),"later gameplay polls retain the last discovery result");
        discovery.startDiscoveryTest(Long.MAX_VALUE-5);discovery.poll(Long.MAX_VALUE-5);discovery.poll(Long.MIN_VALUE+100);
        check(discovery.status().startsWith("Discovery test:"),"discovery timer survives nanoTime wraparound");
        discovery.close();
        BackendRecovery recovery=new BackendRecovery();
        check(!recovery.restartDue(0,false),"missing device starts grace period");
        check(!recovery.restartDue(9_999_999_999L,false),"no rapid restart loop");
        check(recovery.restartDue(10_000_000_000L,false),"first stale enumeration restarts after 10 seconds");
        check(!recovery.restartDue(24_999_999_999L,false)&&recovery.restartDue(25_000_000_000L,false),"subsequent restarts back off");
        check(!recovery.restartDue(100_000_000_000L,true),"any connected slot suppresses global restart");
        check(!recovery.restartDue(101_000_000_000L,false)&&recovery.restartDue(111_000_000_000L,false),"disconnection starts a fresh grace period");
        System.out.println("ControllerBackendTests: "+checks+" Wine routing, single-owner recovery and rescan checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
