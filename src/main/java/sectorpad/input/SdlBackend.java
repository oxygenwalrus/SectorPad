package sectorpad.input;

import com.studiohartman.jamepad.*;
import sectorpad.core.PadFrame;
import sectorpad.core.InputMath;
import sectorpad.diagnostics.Diagnostics;
import java.util.HashSet;
import java.util.Set;

/** SDL/Jamepad is mod-local; never touches Starsector's global JInput/LWJGL controller state. */
public final class SdlBackend implements AutoCloseable {
    private ControllerManager manager;
    private int selected=-1;
    private int instance=-1;
    private boolean ltDown,rtDown;
    private String status="Not initialized";
    private long retryAt;
    private boolean retryPending;
    private int requestedIndex=-1;
    private final ControllerDiscovery discovery=new ControllerDiscovery();
    private final ControllerDiscovery.Slots slots=new ControllerDiscovery.Slots(){
        public boolean connected(int slot){return manager.getControllerIndex(slot).isConnected();}
        public int instance(int slot)throws ControllerUnpluggedException{return manager.getControllerIndex(slot).getDeviceInstanceID();}
        public void reconnect(int slot){manager.getControllerIndex(slot).reconnectController();}
    };
    private final String standaloneNativeRoot;
    public SdlBackend(){this(null);}
    public SdlBackend(String standaloneNativeRoot){this.standaloneNativeRoot=standaloneNativeRoot;}
    private static final ControllerButton[] SDL_BUTTONS={ControllerButton.A,ControllerButton.B,ControllerButton.X,
        ControllerButton.Y,ControllerButton.LEFTBUMPER,ControllerButton.RIGHTBUMPER,ControllerButton.BACK,
        ControllerButton.START,ControllerButton.LEFTSTICK,ControllerButton.RIGHTSTICK,ControllerButton.DPAD_UP,
        ControllerButton.DPAD_DOWN,ControllerButton.DPAD_LEFT,ControllerButton.DPAD_RIGHT};
    private static final String[] BUTTON_NAMES={"A","B","X","Y","LB","RB","VIEW","MENU","L3","R3",
        "DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT"};
    public synchronized void selectDevice(int index){
        int valid=index>=0&&index<ControllerDiscovery.LIMIT?index:-1;
        if(valid!=requestedIndex){requestedIndex=valid;selected=-1;instance=-1;ltDown=rtDown=false;discovery.reset();}
    }
    public synchronized PadFrame poll(long now) {
        try {
            if(manager==null) {
                if(retryPending&&now-retryAt<0)return PadFrame.disconnected();
                if(standaloneNativeRoot==null)ModNativeLoader.load(ModNativeLoader.sdlFile());
                else ModNativeLoader.loadFromDirectory(standaloneNativeRoot,ModNativeLoader.sdlFile());
                Configuration c=new Configuration();c.useRawInput=false;c.maxNumControllers=ControllerDiscovery.LIMIT;
                c.loadNativeLibrary=false;c.loadDatabaseInMemory=true;
                ControllerManager candidate=new ControllerManager(c,"/sectorpad/gamecontrollerdb.txt");
                manager=candidate;candidate.initSDLGamepad();
                retryPending=false;
                if(standaloneNativeRoot==null)Diagnostics.event("backend.ready");
            }
            manager.update();
            selected=discovery.select(now,requestedIndex,slots);
            if(selected<0){
                instance=-1;ltDown=rtDown=false;
                status=requestedIndex<0?"SDL ready; waiting for a controller (automatic discovery)":
                    "SDL ready; waiting for selected device "+(requestedIndex+1);
                return PadFrame.disconnected();
            }
            ControllerIndex c=manager.getControllerIndex(selected);
            int id=c.getDeviceInstanceID();
            if(id!=instance){instance=id;ltDown=rtDown=false;}
            // SDL reports down as positive Y; the rest of SectorPad uses positive Y upwards.
            float lx=axis(c,ControllerAxis.LEFTX),ly=upAxis(axis(c,ControllerAxis.LEFTY));
            float rx=axis(c,ControllerAxis.RIGHTX),ry=upAxis(axis(c,ControllerAxis.RIGHTY));
            float lt=Math.max(0,axis(c,ControllerAxis.TRIGGERLEFT)),rt=Math.max(0,axis(c,ControllerAxis.TRIGGERRIGHT));
            Set<String> buttons=new HashSet<>();
            for(int i=0;i<SDL_BUTTONS.length;i++)if(c.isButtonPressed(SDL_BUTTONS[i]))buttons.add(BUTTON_NAMES[i]);
            ltDown=ltDown?lt>.35f:lt>.55f;rtDown=rtDown?rt>.35f:rt>.55f;
            if(ltDown)buttons.add("LT");if(rtDown)buttons.add("RT");
            boolean independent=c.isAxisAvailable(ControllerAxis.TRIGGERLEFT)&&c.isAxisAvailable(ControllerAxis.TRIGGERRIGHT);
            String name=c.getName();status="SDL / "+name+" / device "+(selected+1);
            return new PadFrame(true,"sdl:"+name+":"+id,name,independent,lx,ly,rx,ry,lt,rt,buttons);
        } catch(ControllerUnpluggedException ex) {
            selected=-1;instance=-1;ltDown=rtDown=false;discovery.reset();
            status="Controller disconnected; automatic discovery continues";return PadFrame.disconnected();
        } catch(LinkageError | RuntimeException ex) {
            status="Controller backend unavailable: "+ex.getClass().getSimpleName()+": "+ex.getMessage();
            if(standaloneNativeRoot==null)Diagnostics.error("backend.failed",ex);
            close();retryAt=now+5_000_000_000L;retryPending=true;
            return PadFrame.disconnected();
        }
    }
    private static float axis(ControllerIndex c,ControllerAxis axis)throws ControllerUnpluggedException {
        return c.isAxisAvailable(axis)?InputMath.finite(c.getAxisState(axis),-1,1):0;
    }
    public String status(){return status;}
    public static float upAxis(float nativeValue){return -InputMath.finite(nativeValue,-1,1);}
    @Override public synchronized void close(){
        if(manager!=null){
            try{manager.quitSDLGamepad();}
            catch(RuntimeException|LinkageError failure){if(standaloneNativeRoot==null)Diagnostics.error("backend.close_failed",failure);}
            manager=null;
        }
        selected=-1;instance=-1;ltDown=rtDown=false;retryPending=false;discovery.reset();
    }
}
