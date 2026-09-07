package sectorpad.bridge;

import com.fs.starfarer.api.input.*;
import com.fs.starfarer.ui.interfacenew;
import sectorpad.game.GameContextDetector;
import sectorpad.game.StateAccess;
import java.util.*;

/** Sends original mouse events through the existing public UI processor; no UI/input replacement. */
final class NativeUiMouseOutput implements DesktopInputBridge.Output {
    interface Port {
        Object current();
        void send(Object target,InputEventType type,int value,int dx,int dy,int x,int y,Modifiers modifiers);
    }
    record Modifiers(boolean shift,boolean ctrl,boolean alt) { }
    private final DesktopInputBridge.Output keyboard;
    private final DesktopInputBridge.Surface surface;
    private final Port port;
    private final Thread gameThread=Thread.currentThread();
    private final Map<Integer,Object> held=new LinkedHashMap<>();
    private Modifiers modifiers=new Modifiers(false,false,false);
    NativeUiMouseOutput(DesktopInputBridge.Output keyboard,DesktopInputBridge.Surface surface){this(keyboard,surface,new GamePort());}
    NativeUiMouseOutput(DesktopInputBridge.Output keyboard,DesktopInputBridge.Surface surface,Port port){this.keyboard=keyboard;this.surface=surface;this.port=port;}
    @Override public void observe(boolean active){if(!active)releaseUi();keyboard.observe(active);}
    @Override public boolean isPhysicalKeyDown(int key){return keyboard.isPhysicalKeyDown(key);}
    @Override public boolean isPhysicalMouseDown(int button){return keyboard.isPhysicalMouseDown(button);}
    @Override public long keyReleaseSequence(int key){return keyboard.keyReleaseSequence(key);}
    @Override public long mouseReleaseSequence(int button){return keyboard.mouseReleaseSequence(button);}
    @Override public boolean preservesPhysicalHolds(){return keyboard.preservesPhysicalHolds();}
    @Override public void key(int key,boolean down){keyboard.key(key,down);}
    @Override public void disownMouse(int button){held.remove(button);}
    @Override public void modifiers(boolean shift,boolean ctrl,boolean alt){modifiers=new Modifiers(shift,ctrl,alt);}
    @Override public boolean supportsUnicode(){return keyboard.supportsUnicode();}
    @Override public void unicode(char character){keyboard.unicode(character);}
    @Override public String description(){return keyboard.description()+"; native UI mouse dispatch";}
    @Override public void mouse(int button,boolean down){
        if(button<0||button>2)throw new IllegalArgumentException("Unsupported mouse button");
        if(Thread.currentThread()!=gameThread){held.remove(button);return;}
        if(down){
            if(!surface.focused()||held.containsKey(button))return;
            Object target=port.current();if(target==null)throw new IllegalStateException("The current UI does not expose its input processor");
            held.put(button,target);
            send(target,InputEventType.MOUSE_DOWN,button,0,0);
        }else{
            Object target=held.remove(button);
            if(target!=null&&surface.focused()&&Objects.equals(target,port.current()))send(target,InputEventType.MOUSE_UP,button,0,0);
        }
    }
    @Override public void wheel(int notches){
        if(Thread.currentThread()!=gameThread||!surface.focused()||notches==0)return;
        Object target=port.current();if(target==null)throw new IllegalStateException("The current UI does not expose its input processor");
        for(int i=0;i<Math.min(12L,Math.abs((long)notches));i++){
            if(!Objects.equals(target,port.current()))break;
            send(target,InputEventType.MOUSE_SCROLL,notches>0?1:-1,0,0);
        }
    }
    @Override public void pointerMoved(float dx,float dy){
        if(Thread.currentThread()!=gameThread||!surface.focused()||held.isEmpty())return;
        Object target=port.current();
        if(target!=null&&held.containsValue(target))send(target,InputEventType.MOUSE_MOVE,-1,Math.round(dx),Math.round(dy));
    }
    private void send(Object target,InputEventType type,int value,int dx,int dy){port.send(target,type,value,dx,dy,Math.round(surface.pointerX()),Math.round(surface.pointerY()),modifiers);}
    private void releaseUi(){for(int button:new ArrayList<>(held.keySet()))mouse(button,false);held.clear();}
    @Override public void close(){releaseUi();keyboard.close();}

    private static final class GamePort implements Port {
        private final GameContextDetector contexts=new GameContextDetector();
        private record Target(interfacenew root,String identity) { }
        @Override public Object current(){
            Object root=StateAccess.uiRoot();
            return root instanceof interfacenew panel?new Target(panel,contexts.detect().identity()):null;
        }
        @Override public void send(Object owner,InputEventType type,int value,int dx,int dy,int x,int y,Modifiers modifiers){
            Target target=(Target)owner;
            try{
                Class<?> eventClass=PublicUiAccess.type("com.fs.starfarer.util.A.C");
                Object nativeEvent=PublicUiAccess.construct(eventClass,new Class<?>[]{InputEventClass.class,InputEventType.class,int.class,int.class,int.class,char.class},InputEventClass.MOUSE_EVENT,type,dx,dy,value,'\0');
                PublicUiAccess.invoke(nativeEvent,"setX",void.class,new Class<?>[]{int.class},x);
                PublicUiAccess.invoke(nativeEvent,"setY",void.class,new Class<?>[]{int.class},y);
                PublicUiAccess.invoke(nativeEvent,"setDX",void.class,new Class<?>[]{int.class},dx);
                PublicUiAccess.invoke(nativeEvent,"setDY",void.class,new Class<?>[]{int.class},dy);
                PublicUiAccess.invoke(nativeEvent,"setShiftDown",void.class,new Class<?>[]{boolean.class},modifiers.shift());
                PublicUiAccess.invoke(nativeEvent,"setCtrlDown",void.class,new Class<?>[]{boolean.class},modifiers.ctrl());
                PublicUiAccess.invoke(nativeEvent,"setAltDown",void.class,new Class<?>[]{boolean.class},modifiers.alt());
                // OOOo.processInput(List<InputEventAPI>) explicitly accepts original C events,
                // wraps them in the native list, then invokes the original child processors.
                target.root.processInput(List.of((InputEventAPI)nativeEvent));
                if(Boolean.getBoolean("sectorpad.debugInput"))com.fs.starfarer.api.Global.getLogger(NativeUiMouseOutput.class).info("SectorPad UI mouse: "+type+" button/value="+value+" x="+x+" y="+y+" consumed="+((InputEventAPI)nativeEvent).isConsumed()+" root="+target.root.getClass().getName());
            }catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}
            catch(Throwable failure){throw new IllegalStateException("Native UI mouse delivery failed",failure);}
        }
    }
}
