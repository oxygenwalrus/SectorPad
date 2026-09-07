package sectorpad.bridge;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class NavigationTests {
    public static void main(String[] args) {
        modalAndVisibility();
        directionalGeometry();
        stableRowsAndEdges();
        textCommitValidation();
        System.out.println("NavigationTests: 4 modal, geometry, stable navigation and text scenarios passed");
    }

    private static void modalAndVisibility() {
        AtomicInteger writes=new AtomicInteger();
        Panel root=new Panel(); root.children.add(button("Behind",0,0,true,writes));
        Modal modal=new Modal(); modal.children.add(button("Disabled",100,100,false,writes));
        ButtonAPI allowed=button("Allowed",140,100,true,writes); modal.children.add(allowed);
        root.children.add(modal);
        check(ReadOnlyUiNavigator.modalIdentity(root)==modal,"Top native modal identity is exposed without changing it");
        ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator();
        List<ReadOnlyUiNavigator.Candidate> targets=navigator.refresh(root);
        check(targets.size()==1 && targets.get(0).component()==allowed,"Modal navigation cannot select behind modal or disabled widgets");
        navigator.move(1,0,20,100);
        check(writes.get()==0,"Discovery and selection must not mutate vanilla widget APIs");
        modal.opacity=0; targets=navigator.refresh(root);
        check(ReadOnlyUiNavigator.modalIdentity(root)==null,"Hidden native modal does not change ownership");
        check(targets.size()==1 && targets.get(0).label().equals("Behind"),"Invisible modal must not trap focus");
    }

    private static void directionalGeometry() {
        check(ReadOnlyUiNavigator.directionScore(10,10,0,10,1,0)==Double.POSITIVE_INFINITY,"Opposite direction excluded");
        double straight=ReadOnlyUiNavigator.directionScore(10,10,80,10,1,0);
        double diagonal=ReadOnlyUiNavigator.directionScore(10,10,50,90,1,0);
        check(straight<diagonal,"Navigation favors the same row over a nearer unrelated column");
        AtomicInteger writes=new AtomicInteger(); Panel root=new Panel();
        ButtonAPI top=button("Top",0,100,true,writes), bottom=button("Bottom",0,40,true,writes);
        root.children.add(top); root.children.add(bottom);
        ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator(); navigator.refresh(root);
        check(navigator.move(0,-1,10,110).component()==bottom,"Dpad down follows bottom-left UI coordinates");
    }

    private static void stableRowsAndEdges() {
        AtomicInteger writes=new AtomicInteger();Panel root=new Panel();
        ButtonAPI top=button("Top",0,200,true,writes), middle=button("Middle",0,130,true,writes), bottom=button("Bottom",0,60,true,writes);
        ButtonAPI beside=button("Other column",50,180,true,writes);
        root.children.addAll(List.of(beside,bottom,top,middle));
        ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator();navigator.refresh(root);
        check(navigator.move(0,-1,20,210).component()==middle,"Down stays in its column rather than jumping to a closer diagonal");
        navigator.refresh(root);
        check(navigator.move(0,-1,20,210).component()==bottom,"A pending cursor update cannot reset logical focus to the previous row");
        check(navigator.move(0,-1,20,70).component()==bottom,"A menu edge retains focus rather than falling through to native arrows");
        check(navigator.move(0,1,20,70).component()==middle,"Up reverses along the same column");
        check(navigator.move(0,-1,70,190).component()==middle,"Explicit pointer movement outside focus transfers navigation origin");
        root.children.remove(bottom);navigator.refresh(root);
        check(navigator.getCandidates().stream().noneMatch(c->c.component()==bottom),"Removed controls cannot remain navigation targets");
        check(writes.get()==0,"Directional focus leaves native controls unchanged");
    }

    private static void textCommitValidation() {
        Panel root=new Panel(); String[] value={"old"}; boolean[] focus={true}; AtomicInteger writes=new AtomicInteger();
        TextFieldAPI field=(TextFieldAPI)Proxy.newProxyInstance(NavigationTests.class.getClassLoader(),new Class[]{TextFieldAPI.class},(proxy,method,args)->switch(method.getName()) {
            case "getPosition" -> position(0,0,240,30); case "getOpacity" -> 1f; case "hasFocus" -> focus[0];
            case "getText" -> value[0]; case "getMaxChars" -> 8; case "isLimitByStringWidth" -> false;
            case "isValidChar" -> ((Character)args[0])!='!';
            case "setText" -> { value[0]=(String)args[0]; writes.incrementAndGet(); yield null; }
            case "equals" -> proxy==args[0]; case "hashCode" -> System.identityHashCode(proxy); default -> defaultValue(method.getReturnType());
        });
        root.children.add(field); ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator(); navigator.refresh(root);
        check(navigator.focusedTextField()==field,"Only focused textfield is exposed");
        check(!navigator.commitText(field,"123456789") && !navigator.commitText(field,"bad!"),"Max chars and native character rules validated before write");
        check(value[0].equals("old") && writes.get()==0,"Invalid text leaves original text untouched");
        check(navigator.commitText(field,"\u03A9 name") && value[0].equals("\u03A9 name"),"Unicode uses the existing field API");
        ReadOnlyUiNavigator.TextTarget captured=navigator.captureText();
        focus[0]=false; check(!navigator.commitText(field,"later"),"Lost focus cannot receive staged text");
        check(navigator.commitCapturedText(captured,"staged"),"An explicitly captured field remains valid while the modal keyboard owns focus");
        check(!navigator.commitCapturedText(captured,"again"),"An external or earlier text change rejects a stale modal commit");
        focus[0]=true; captured=navigator.captureText();
        Panel replacement=new Panel();replacement.children.add(field);navigator.refresh(replacement);
        check(!navigator.commitCapturedText(captured,"wrong"),"The same field moved to a replacement root cannot receive a stale commit");
        navigator.refresh(root);captured=navigator.captureText();
        focus[0]=true; root.children.clear(); navigator.refresh(root); check(!navigator.commitText(field,"later"),"Removed textfield cannot receive stale commit");
        check(!navigator.commitCapturedText(captured,"gone"),"A removed captured field cannot receive staged text");
    }

    private static ButtonAPI button(String label,float x,float y,boolean enabled,AtomicInteger writes) {
        return (ButtonAPI)Proxy.newProxyInstance(NavigationTests.class.getClassLoader(),new Class[]{ButtonAPI.class},(proxy,method,args)->switch(method.getName()) {
            case "getPosition" -> position(x,y,40,20); case "getOpacity" -> 1f; case "getText" -> label; case "isEnabled" -> enabled;
            case "equals" -> proxy==args[0]; case "hashCode" -> System.identityHashCode(proxy);
            default -> { if(method.getName().startsWith("set") || method.getName().equals("highlight")) writes.incrementAndGet(); yield defaultValue(method.getReturnType()); }
        });
    }
    private static PositionAPI position(float x,float y,float width,float height) {
        return (PositionAPI)Proxy.newProxyInstance(NavigationTests.class.getClassLoader(),new Class[]{PositionAPI.class},(proxy,method,args)->switch(method.getName()) {
            case "getX" -> x; case "getY" -> y; case "getWidth" -> width; case "getHeight" -> height;
            case "getCenterX" -> x+width/2; case "getCenterY" -> y+height/2; default -> defaultValue(method.getReturnType());
        });
    }
    private static Object defaultValue(Class<?> type) { if(type==boolean.class)return false; if(type==float.class)return 0f; if(type==double.class)return 0d; if(type==int.class)return 0; if(type==long.class)return 0L; if(type==char.class)return '\0'; return null; }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static class Panel implements UIComponentAPI {
        public final List<Object> children=new ArrayList<>(); float opacity=1f;
        public List<Object> getChildrenCopy(){return new ArrayList<>(children);} public PositionAPI getPosition(){return position(0,0,1280,720);}
        public float getOpacity(){return opacity;} public void setOpacity(float value){opacity=value;} public void render(float amount){}
        public void processInput(List<InputEventAPI> events){} public void advance(float amount){}
    }
    public static final class Modal extends Panel {
        public float getBackgroundDimAmount(){return 0.5f;} public UIComponentAPI getDialogParent(){return this;}
    }
}
