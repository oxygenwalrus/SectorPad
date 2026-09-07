package sectorpad.bridge;

import com.fs.starfarer.api.input.InputEventType;
import java.util.ArrayList;
import java.util.List;

public final class NativeUiMouseTests {
    public static void main(String[] args)throws Exception{
        List<String> events=new ArrayList<>();FakeSurface surface=new FakeSurface();FakeOutput keyboard=new FakeOutput();
        final String[] owner={"first"};
        final NativeUiMouseOutput.Modifiers[] modifiers={null};
        NativeUiMouseOutput.Port port=new NativeUiMouseOutput.Port(){
            public Object current(){return owner[0];}
            public void send(Object target,InputEventType type,int value,int dx,int dy,int x,int y,NativeUiMouseOutput.Modifiers flags){modifiers[0]=flags;events.add(target+":"+type+":"+value+":"+x+":"+y);}
        };
        NativeUiMouseOutput output=new NativeUiMouseOutput(keyboard,surface,port);
        output.mouse(0,true);output.mouse(0,true);output.pointerMoved(1,2);output.mouse(0,false);
        check(events.size()==3&&events.get(0).equals("first:MOUSE_DOWN:0:120:85")&&events.get(2).contains("MOUSE_UP"),"Balanced original UI mouse and drag events");
        output.modifiers(true,true,false);output.mouse(0,true);check(modifiers[0].shift()&&modifiers[0].ctrl()&&!modifiers[0].alt(),"Mouse events retain logical and physical modifiers");
        events.clear();output.disownMouse(0);output.mouse(0,true);output.mouse(0,false);check(events.size()==2,"Physical handoff does not leave a stale virtual button owner");
        events.clear();output.mouse(1,true);owner[0]="second";output.mouse(1,false);check(events.size()==1,"A release cannot enter a different modal or root");
        events.clear();output.wheel(1000);check(events.size()==12,"Wheel bursts are bounded");
        events.clear();surface.focused=false;output.mouse(0,true);output.wheel(1);check(events.isEmpty(),"Focus loss emits no UI action");
        surface.focused=true;output.mouse(0,true);events.clear();Thread shutdown=new Thread(output::close);shutdown.start();shutdown.join();check(events.isEmpty()&&keyboard.closed,"Shutdown does not call game UI from another thread");
        check(keyboard.mouseCalls==0,"Local UI delivery never also injects an OS mouse event");
        events.clear();surface.focused=true;
        NativeUiMouseOutput hover=new NativeUiMouseOutput(keyboard,surface,port);hover.pointerMoved(3,4);
        check(events.size()==1&&events.get(0).contains("MOUSE_MOVE"),"Unheld pointer movement reaches native hover-gated refit controls");
        surface.focused=false;hover.pointerMoved(3,4);check(events.size()==1,"Focus loss blocks native hover");
        System.out.println("NativeUiMouseTests: 8 ownership, modifier, handoff, drag, focus, bounded-wheel and shutdown scenarios passed (API port doubles)");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class FakeSurface implements DesktopInputBridge.Surface{
        boolean focused=true;public boolean focused(){return focused;}public float pointerX(){return 120;}public float pointerY(){return 85;}public float width(){return 1280;}public float height(){return 800;}public void move(float x,float y){}
    }
    private static final class FakeOutput implements DesktopInputBridge.Output{
        int mouseCalls;boolean closed;public void observe(boolean a){}public boolean isPhysicalKeyDown(int k){return false;}public boolean isPhysicalMouseDown(int b){return false;}public boolean preservesPhysicalHolds(){return true;}public void key(int k,boolean d){}public void mouse(int b,boolean d){mouseCalls++;}public void wheel(int n){}public boolean supportsUnicode(){return true;}public void unicode(char c){}public String description(){return "test";}public void close(){closed=true;}
    }
}
