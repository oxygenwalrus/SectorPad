package sectorpad.ui;

import java.util.List;
import sectorpad.core.DeviceVisual;

/** Inline prompt safety and sizing checks, without requiring a graphics context. */
public final class DeviceVisualTests {
    private static int assertions;
    public static void main(String[] args){
        for(String control:List.of("A","B","X","Y","LB","RB","LT","RT","L3","R3","MENU","VIEW",
                "LEFT_STICK","RIGHT_STICK","DPAD","DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT")){
            List<PromptRenderer.Part> parts=PromptRenderer.parse(PromptRenderer.token(control));
            check(parts.size()==1&&control.equals(parts.get(0).control()),"Canonical token preserves exact normalized binding: "+control);
            for(DeviceVisual visual:DeviceVisual.values())for(float size:new float[]{12,18,24,48}){
                float width=ControlGlyphs.width(control,visual,size);
                check(Float.isFinite(width)&&width>=size&&width<=size*1.5f,"Glyph advance fits its supported family: "+control);
            }
        }
        for(String text:List.of("A X Y B L1 RT Menu View", "ISS Xbox A-class", "{pad:UNKNOWN}","{pad:a}","{pad:A", "{keyboard:A}","   A\tB  ")){
            List<PromptRenderer.Part> parts=PromptRenderer.parse(text);
            check(parts.stream().noneMatch(p->p.control()!=null),"Ordinary or malformed text must not create controls: "+text);
            check(restore(parts).equals(text),"Unknown and ordinary text is preserved: "+text);
        }
        var adjacent=PromptRenderer.parse("Equip{pad:A}+{pad:RT}/done");
        check(adjacent.stream().filter(p->p.control()!=null).count()==2,"Adjacent exact tokens work without whitespace");
        check(restore(adjacent).equals("Equip{pad:A}+{pad:RT}/done"),"Token adjacency preserves punctuation and labels");
        var multiline=PromptRenderer.parse("{pad:A} Apply\r\n{pad:B} Back\rNext\nLast");
        check(multiline.stream().filter(PromptRenderer.Part::newline).count()==3,"CRLF is one break, CR and LF remain breaks");
        check(restore(multiline).equals("{pad:A} Apply\n{pad:B} Back\nNext\nLast"),"Line break normalization preserves text");
        check(PromptRenderer.parse(null).isEmpty()&&PromptRenderer.parse("").isEmpty(),"Empty prompts are empty");
        check(PromptRenderer.token(null).equals("Unbound")&&PromptRenderer.token("NONE").equals("Unbound"),"Unbound input is readable");
        check(PromptRenderer.token("F10").equals("F10"),"Keyboard shortcut stays ordinary text");
        check(!PromptRenderer.recognized("a")&&!PromptRenderer.recognized(null),"Token whitelist uses exact normalized identifiers");
        for(float invalid:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})
            check(ControlGlyphs.width("A",DeviceVisual.XBOX,invalid)==0,"Invalid glyph dimensions have zero advance");
        check(ControlGlyphs.width("UNKNOWN",DeviceVisual.XBOX,24)==0,"Unknown controls reserve no invisible glyph space");
        for(DeviceVisual visual:DeviceVisual.values()){
            var centers=DeviceDiagram.controlCenters(visual);
            check(centers.size()==18,"Every supported physical control has a center: "+visual);
            for(var entry:centers.entrySet()){
                var point=entry.getValue();
                check(Float.isFinite(point.x())&&Float.isFinite(point.y())&&point.x()>0&&point.y()>0
                    &&point.x()<DeviceDiagram.LOGICAL_WIDTH&&point.y()<DeviceDiagram.LOGICAL_HEIGHT,
                    "Control center stays inside logical device: "+visual+" "+entry.getKey());
            }
            check(centers.get("LEFT_STICK").equals(centers.get("L3"))&&centers.get("RIGHT_STICK").equals(centers.get("R3")),
                "Stick motion and click highlight the same physical location: "+visual);
            check(centers.get("A").y()<centers.get("Y").y()&&centers.get("X").x()<centers.get("B").x(),
                "Face-button topology matches normalized south/east/west/north controls: "+visual);
        }
        var deck=DeviceDiagram.controlCenters(DeviceVisual.STEAM_DECK);
        check(deck.get("LEFT_STICK").x()>deck.get("DPAD_UP").x(),"Deck left stick sits inside the outer D-pad");
        check(deck.get("RIGHT_STICK").x()<deck.get("A").x(),"Deck right stick sits inside the outer face buttons");
        System.out.println("DeviceVisualTests: "+assertions+" assertions passed");
    }
    private static String restore(List<PromptRenderer.Part> parts){
        StringBuilder text=new StringBuilder();for(var part:parts)text.append(part.newline()?"\n":part.control()==null?part.text():"{pad:"+part.control()+"}");return text.toString();
    }
    private static void check(boolean pass,String message){assertions++;if(!pass)throw new AssertionError(message);}
}
