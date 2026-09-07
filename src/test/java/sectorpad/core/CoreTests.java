package sectorpad.core;

import java.util.List;
import java.util.Set;

public final class CoreTests {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void near(double a,double b,double tolerance,String message){check(Math.abs(a-b)<tolerance,message+": "+a+" / "+b);}
    private static PadFrame pad(Set<String> buttons){return new PadFrame(true,"test","test",true,0,0,0,0,0,0,buttons);}
    public static void main(String[] args){
        var zero=InputMath.shape(.05f,.05f,.12,.98,1.6);check(zero.x()==0&&zero.y()==0,"inner deadzone");
        var full=InputMath.shape(1,1,.12,.98,1.6);near(Math.hypot(full.x(),full.y()),1,.00001,"bounded diagonal");
        check(InputMath.shape(Float.NaN,1,.12,.98,1).x()==0,"invalid input finite");
        check(InputMath.shape(1,1,.98,.12,1).x()==0,"invalid shape fails closed");
        near(InputMath.shape(.5f,0,.1,1,1).x(),.4/.9,.00001,"rescaled deadzone");
        for(int hz:new int[]{30,60,120}){
            double distance=0;for(int i=0;i<hz*4;i++)distance+=900d/hz;
            near(distance,3600,.001,"frame-rate independent integration");
            ScrollAccumulator scroll=new ScrollAccumulator();int notches=0;
            for(int i=0;i<hz*4;i++)notches+=scroll.advance("pane",.6,1d/hz);
            near(notches+scroll.remainder(),2.4,.00001,"fractional scroll "+hz);
        }
        ScrollAccumulator scroll=new ScrollAccumulator();scroll.advance("a",.8,.1);scroll.advance("b",0,.1);
        near(scroll.remainder(),0,.000001,"pane transition removes remainder");
        UiClock clock=new UiClock();clock.tick(1_000_000_000L);near(clock.tick(1_016_000_000L),.016,.000001,"monotonic paused UI time");
        check(clock.tick(3_000_000_000L)==0&&clock.interrupted(),"resume gap is discarded");
        InputGate gate=new InputGate();gate.updateOwner("combat");check(!gate.armWhenNeutral(pad(Set.of("RT")),.12f),"held trigger blocks rearm");
        check(gate.armWhenNeutral(pad(Set.of()),.12f),"neutral rearms");
        check(gate.edges(Set.of("fire")).pressed("fire"),"down edge");
        check(gate.edges(Set.of("fire")).pressed().isEmpty(),"held input is not repeated click");
        check(gate.edges(Set.of()).released().contains("fire"),"balanced up edge");
        gate.updateOwner("wheel");check(gate.edges(Set.of("fire")).held().isEmpty(),"context disarms");
        List<RadialModel.Entry> entries=List.of(new RadialModel.Entry("a","A","",true,"",false,()->{}),
            new RadialModel.Entry("b","B","",false,"Unavailable",false,()->{}),
            new RadialModel.Entry("c","C","",true,"",false,()->{}),new RadialModel.Entry("d","D","",true,"",false,()->{}));
        RadialModel wheel=new RadialModel();wheel.open("test",entries,true,"LB",4);wheel.point(0,1);
        check(wheel.highlighted().id().equals("a"),"up selects slot zero");wheel.point(0,0);
        check(wheel.commit()==null,"centre cancel does not execute");
        wheel.open("test",entries,true,"LB",4);wheel.point(1,0);check(wheel.commit()==null,"disabled never executes");
        wheel.open("test",entries,false,"VIEW",4);wheel.navigate(-1);check(wheel.highlighted().id().equals("d"),"dpad wrap");
        wheel.close();check(!wheel.isOpen(),"cancel suppresses later release");
        TextEntryModel text=new TextEntryModel();text.open("ab",false,4);text.caret(-1);text.insert('X');check(text.text().equals("aXb"),"caret insertion");
        text.backspace();check(text.text().equals("ab"),"backspace");text.insert('c');text.insert('d');text.insert('e');check(text.text().length()==4,"text bound");
        text.close();check(!text.isOpen(),"text cancellation local only");
        text.open("",true,4);text.insert('x');text.insert(' ');text.insert('\n');text.insert('4');text.insert('2');
        check(text.text().equals("42"),"numeric entry rejects keyboard letters and control characters");
        text.caret(-1);text.delete();check(text.text().equals("4"),"physical forward delete edits staged text");
        text.open("original",false,80);text.insert('\n');check(text.text().equals("original"),"Enter cannot leak a control character into a name");
        RepeatKey repeat=new RepeatKey();check(repeat.pulse("up",1_000_000_000L,.3,.1),"initial navigation");
        check(!repeat.pulse("up",1_100_000_000L,.3,.1),"repeat delay");check(repeat.pulse("up",5_000_000_000L,.3,.1),"stall produces one pulse");
        check(!repeat.pulse("up",5_000_000_001L,.3,.1),"no catch-up burst");
        System.out.println("CoreTests: "+checks+" checks passed");
    }
}
