package sectorpad.input;

import sectorpad.core.PadFrame;
import sectorpad.diagnostics.Diagnostics;

/** One input source at a time; Wine can bypass SDL through its Windows XInput API. */
public final class ControllerBackend implements AutoCloseable {
    interface Port extends AutoCloseable {
        PadFrame poll(long now, int index);
        void reconnect();
        String status();
        void close();
    }
    private final Port sdl, xinput;
    private final java.util.function.BooleanSupplier wine;
    private final boolean windows;
    private Port active;
    private String mode="Automatic";
    private int index=-1;
    private boolean reconnect;
    private String status="Waiting for controller discovery";
    private static final long TEST_NANOS=10_000_000_000L;
    private long testStarted;
    private boolean testRunning;
    private PadFrame testSdl=PadFrame.disconnected(),testXinput=PadFrame.disconnected();
    private boolean testSdlActivity,testXinputActivity;
    private String lastDiscoveryResult="";

    public ControllerBackend(){
        SdlBackend sd=new SdlBackend(); WindowsXInputBackend xi=new WindowsXInputBackend();
        sdl=new Port(){
            public PadFrame poll(long now,int slot){sd.selectDevice(slot);return sd.poll(now);}
            public void reconnect(){sd.requestReconnect();}
            public String status(){return sd.status();}
            public void close(){sd.close();}
        };
        xinput=new Port(){
            public PadFrame poll(long now,int slot){return xi.poll(now,slot);}
            public void reconnect(){xi.requestReconnect();}
            public String status(){return xi.status();}
            public void close(){xi.close();}
        };
        windows=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows");
        wine=xi::isWine;
    }
    ControllerBackend(Port sdl,Port xinput,boolean windows,java.util.function.BooleanSupplier wine){
        this.sdl=sdl;this.xinput=xinput;this.windows=windows;this.wine=wine;
    }
    public void configure(String wanted,int slot){
        String valid="SDL".equals(wanted)||"XInput".equals(wanted)?wanted:"Automatic";
        slot=slot>=0&&slot<8?slot:-1;
        if(!mode.equals(valid)||index!=slot){mode=valid;index=slot;requestReconnect();}
    }
    public void requestReconnect(){reconnect=true;}
    public void startDiscoveryTest(long now){
        requestReconnect();testStarted=now;testRunning=true;
        testSdl=testXinput=PadFrame.disconnected();testSdlActivity=testXinputActivity=false;
        Diagnostics.event("backend.discovery_test_started");
    }
    public PadFrame poll(long now){
        if(reconnect){
            reconnect=false;active=null;sdl.reconnect();xinput.reconnect();
            status="Reconnecting controllers; release controls";
            Diagnostics.event("backend.manual_reconnect");
            return PadFrame.disconnected(); // A release boundary even if the same device immediately returns.
        }
        boolean underWine=windows&&wine.getAsBoolean();
        Diagnostics.state("wine_runtime",Boolean.toString(underWine));
        Diagnostics.state("backend_mode",mode);
        Diagnostics.state("requested_slot",Integer.toString(index));
        if("XInput".equals(mode)&&!windows){
            status="XInput requires a Windows/Wine runtime; choose Automatic or SDL";
            Diagnostics.state("active_backend","unsupported");return PadFrame.disconnected();
        }
        Port preferred="XInput".equals(mode)||(mode.equals("Automatic")&&underWine)?xinput:sdl;
        Port first=mode.equals("Automatic")&&active!=null?active:preferred;
        PadFrame frame=first.poll(now,index);
        // SDL and XInput slot numbers are not interchangeable. Explicit selection never falls
        // through to a different API's same-numbered physical device.
        if(!frame.connected()&&windows&&mode.equals("Automatic")&&index<0){
            Port other=first==sdl?xinput:sdl;PadFrame fallback=other.poll(now,index);
            if(fallback.connected()){first=other;frame=fallback;}
            else status="No controller exposed: "+first.status()+"; "+other.status();
        }else status=first.status();
        if(frame.connected()){active=first;status=first.status();}
        else active=null;
        Diagnostics.state("active_backend",frame.connected()?(first==sdl?"sdl":"xinput"):"none");
        long testElapsed=now-testStarted;
        if(testRunning&&testElapsed<TEST_NANOS){
            PadFrame observedSdl=first==sdl?frame:sdl.poll(now,-1);
            PadFrame observedXinput=windows?(first==xinput?frame:xinput.poll(now,-1)):PadFrame.disconnected();
            testSdlActivity|=changed(testSdl,observedSdl);testXinputActivity|=changed(testXinput,observedXinput);
            testSdl=observedSdl;testXinput=observedXinput;
            Diagnostics.state("discovery_test_sdl",testToken(observedSdl,testSdlActivity));
            Diagnostics.state("discovery_test_xinput",windows?testToken(observedXinput,testXinputActivity):"unsupported");
            status="Discovery test: SDL "+label(observedSdl,testSdlActivity)+"; XInput "+(windows?label(observedXinput,testXinputActivity):"unsupported")+
                    ". Move controls; "+Math.max(0,(TEST_NANOS-testElapsed+999_999_999L)/1_000_000_000L)+"s";
        }else if(testRunning){
            testRunning=false;Diagnostics.event("backend.discovery_test_completed");
            lastDiscoveryResult="Last test: SDL "+label(testSdl,testSdlActivity)+"; XInput "+(windows?label(testXinput,testXinputActivity):"unsupported");
            status=lastDiscoveryResult;
        }
        return frame;
    }
    private static boolean changed(PadFrame before,PadFrame after){
        if(!after.connected())return false;
        if(!before.connected())return !after.neutral(.03f);
        return !before.buttons().equals(after.buttons())||Math.abs(before.lx()-after.lx())>.03f||
                Math.abs(before.ly()-after.ly())>.03f||Math.abs(before.rx()-after.rx())>.03f||Math.abs(before.ry()-after.ry())>.03f||
                Math.abs(before.lt()-after.lt())>.03f||Math.abs(before.rt()-after.rt())>.03f;
    }
    private static String label(PadFrame frame,boolean activity){return !frame.connected()?"missing":activity?"input seen":"connected / idle";}
    private static String testToken(PadFrame frame,boolean activity){return !frame.connected()?"missing":activity?"active":"idle";}
    public String status(){
        if(testRunning||lastDiscoveryResult.isEmpty()||status.equals(lastDiscoveryResult))return status;
        return lastDiscoveryResult+" | "+status;
    }
    @Override public void close(){active=null;sdl.close();xinput.close();}
}
