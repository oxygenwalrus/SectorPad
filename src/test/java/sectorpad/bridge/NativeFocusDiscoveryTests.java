package sectorpad.bridge;

import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Native-tree-shaped fixtures: descriptions must never acquire focus merely by being list rows. */
public final class NativeFocusDiscoveryTests {
    private static int checks,writes;
    public interface WidgetFlags {
        boolean isActive(); boolean isClickable(); boolean isVisible(); boolean isBeingDismissed();
        boolean isSlidOut(); boolean isEditable(); boolean isReadOnly(); boolean isEnabled();
    }
    public interface ContentOwner { UIComponentAPI getContentContainer(); }
    private static final class State {
        boolean enabled=true,active=true,clickable=true,visible=true,editable=true,readOnly,dismissed,slidOut;
        float opacity=1f; String text="Action";
    }
    public static class Panel implements UIComponentAPI {
        public final List<Object> children=new ArrayList<>();
        float opacity=1;
        public List<Object> getChildrenCopy(){return List.copyOf(children);}
        public PositionAPI getPosition(){return position(0,0,1280,720);}
        public float getOpacity(){return opacity;}
        public void setOpacity(float value){writes++;opacity=value;}
        public void render(float amount){}
        public void processInput(List<InputEventAPI> events){}
        public void advance(float amount){}
    }
    /** Deliberately shares the old suffix heuristic; it is not a native actionable cargo view. */
    public static final class FakeCargoStackView extends Panel { }
    public static final class FadingPanel extends Panel {
        final Fader fader=new Fader(1f,.2f,.2f);
        public Fader getFader(){return fader;}
    }
    public static final class SlidingPanel extends Panel {
        public boolean isSlidOut(){return true;}
    }
    public static void main(String[] args)throws Exception {
        descriptionsAreNotActions();
        inactiveAndDisabledInputs();
        visibilityAndTransitions();
        nativeContracts();
        check(writes==0,"discovery and selection never mutate widgets, scroll offsets or input");
        System.out.println("NativeFocusDiscoveryTests: "+checks+" actionable-target and visibility checks passed");
    }
    private static void descriptionsAreNotActions(){
        Panel root=new Panel(),content=new Panel(),descriptionRow=new Panel(),actionRow=new Panel();
        State labelState=new State();labelState.text="This is explanatory text with no action";
        LabelAPI label=widget(LabelAPI.class,labelState,10,600);
        descriptionRow.children.add(label);
        ButtonAPI button=widget(ButtonAPI.class,new State(),10,500);
        actionRow.children.add(button);
        content.children.add(label);content.children.add(descriptionRow);content.children.add(new Panel());
        content.children.add(actionRow);content.children.add(new FakeCargoStackView());
        ScrollPanelAPI scroll=scroll(content);root.children.add(scroll);
        ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator();
        List<ReadOnlyUiNavigator.Candidate> targets=navigator.refresh(root);
        check(targets.size()==1&&targets.get(0).component()==button,
                "scroll labels, descriptions, empty rows and similarly named custom panels are not focus targets");
        check(targets.get(0).scroller()==scroll,"real controls preserve their scroll owner for reveal-on-navigation");
        check(navigator.move(0,-1,30,650).component()==button,"D-pad skips static text and reaches an actual control");
        check(navigator.hasScrollableAt(20,400),"removing guessed row targets does not remove user scrolling");
        content.children.clear();content.children.add(label);navigator.refresh(root);
        check(navigator.getCandidates().isEmpty()&&navigator.selected()==null,"removed controls lose focus; static rows do not inherit it");
    }
    private static void inactiveAndDisabledInputs(){
        Panel root=new Panel();ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator();
        State good=new State();ButtonAPI allowed=widget(ButtonAPI.class,good,10,600);root.children.add(allowed);
        State disabled=new State();disabled.enabled=false;root.children.add(widget(ButtonAPI.class,disabled,10,550));
        State inactive=new State();inactive.active=false;root.children.add(widget(ButtonAPI.class,inactive,10,500));
        State nonclick=new State();nonclick.clickable=false;root.children.add(widget(ButtonAPI.class,nonclick,10,450));
        State fieldState=new State();TextFieldAPI field=widget(TextFieldAPI.class,fieldState,10,400);root.children.add(field);
        State disabledField=new State();disabledField.enabled=false;root.children.add(widget(TextFieldAPI.class,disabledField,10,350));
        State fixedField=new State();fixedField.editable=false;root.children.add(widget(TextFieldAPI.class,fixedField,10,300));
        State readOnly=new State();readOnly.readOnly=true;root.children.add(widget(TextFieldAPI.class,readOnly,10,250));
        List<ReadOnlyUiNavigator.Candidate> targets=navigator.refresh(root);
        check(targets.size()==2,"disabled/inactive/nonclickable buttons and disabled/readonly fields are excluded");
        check(targets.stream().anyMatch(c->c.component()==allowed)&&targets.stream().anyMatch(c->c.component()==field),
                "enabled action and editable text input remain available");
        navigator.move(0,-1,20,650);good.enabled=false;navigator.refresh(root);
        check(navigator.selected()==null,"a newly disabled selected action cannot retain focus");
    }
    private static void visibilityAndTransitions(){
        Panel root=new Panel();ButtonAPI allowed=widget(ButtonAPI.class,new State(),10,600);root.children.add(allowed);
        State hidden=new State();hidden.visible=false;root.children.add(widget(ButtonAPI.class,hidden,10,550));
        State transparent=new State();transparent.opacity=0;root.children.add(widget(ButtonAPI.class,transparent,10,500));
        State nan=new State();nan.opacity=Float.NaN;root.children.add(widget(ButtonAPI.class,nan,10,450));
        State dismissed=new State();dismissed.dismissed=true;root.children.add(widget(ButtonAPI.class,dismissed,10,400));
        State slid=new State();slid.slidOut=true;root.children.add(widget(ButtonAPI.class,slid,10,350));
        Panel hiddenParent=new Panel();hiddenParent.opacity=0;hiddenParent.children.add(widget(ButtonAPI.class,new State(),10,300));root.children.add(hiddenParent);
        SlidingPanel sliding=new SlidingPanel();sliding.children.add(widget(ButtonAPI.class,new State(),10,250));root.children.add(sliding);
        FadingPanel fading=new FadingPanel();fading.fader.fadeOut();fading.children.add(widget(ButtonAPI.class,new State(),10,200));root.children.add(fading);
        List<ReadOnlyUiNavigator.Candidate> targets=new ReadOnlyUiNavigator().refresh(root);
        check(targets.size()==1&&targets.get(0).component()==allowed,
                "hidden, dismissed, slid-out, invalid-opacity and fading-out subtrees cannot receive focus");
    }
    private static void nativeContracts()throws Exception {
        check(com.fs.starfarer.ui.n.class.getMethod("isActive").getReturnType()==boolean.class,"native button active flag is a public boolean getter");
        check(com.fs.starfarer.ui.OOOo.class.getMethod("isSlidOut").getReturnType()==boolean.class,"native slide visibility is a public boolean getter");
        check(com.fs.starfarer.campaign.ui.trade.CargoStackView.class.getMethod("isExpired").getReturnType()==boolean.class,
                "known native cargo targets expose live/expired state");
    }
    private static ScrollPanelAPI scroll(Panel content){
        return (ScrollPanelAPI)Proxy.newProxyInstance(NativeFocusDiscoveryTests.class.getClassLoader(),new Class<?>[]{ScrollPanelAPI.class,ContentOwner.class},(proxy,method,args)->switch(method.getName()){
            case "getContentContainer" -> content;
            case "getPosition" -> position(0,0,800,700);
            case "getOpacity" -> 1f;
            case "equals" -> proxy==args[0];case "hashCode" -> System.identityHashCode(proxy);
            default -> {if(method.getName().startsWith("set"))writes++;yield empty(method.getReturnType());}
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T widget(Class<T> type,State state,float x,float y){
        return (T)Proxy.newProxyInstance(NativeFocusDiscoveryTests.class.getClassLoader(),new Class<?>[]{type,WidgetFlags.class},(proxy,method,args)->switch(method.getName()){
            case "getPosition" -> position(x,y,160,28);case "getOpacity" -> state.opacity;case "getText" -> state.text;
            case "isEnabled" -> state.enabled;case "isActive" -> state.active;case "isClickable" -> state.clickable;
            case "isVisible" -> state.visible;case "isBeingDismissed" -> state.dismissed;case "isSlidOut" -> state.slidOut;
            case "isEditable" -> state.editable;case "isReadOnly" -> state.readOnly;
            case "equals" -> proxy==args[0];case "hashCode" -> System.identityHashCode(proxy);
            default -> {if(method.getName().startsWith("set"))writes++;yield empty(method.getReturnType());}
        });
    }
    private static PositionAPI position(float x,float y,float width,float height){
        return (PositionAPI)Proxy.newProxyInstance(NativeFocusDiscoveryTests.class.getClassLoader(),new Class<?>[]{PositionAPI.class},(proxy,method,args)->switch(method.getName()){
            case "getX" -> x;case "getY" -> y;case "getWidth" -> width;case "getHeight" -> height;
            case "getCenterX" -> x+width/2;case "getCenterY" -> y+height/2;default -> empty(method.getReturnType());
        });
    }
    private static Object empty(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==float.class)return 0f;if(type==double.class)return 0d;if(type==long.class)return 0L;if(type==char.class)return '\0';return null;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
