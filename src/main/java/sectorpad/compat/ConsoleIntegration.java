package sectorpad.compat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.ui.interfacenew;
import sectorpad.ui.OverlayLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/** Optional Console Commands 4.x integration using its public live-panel API only. */
public final class ConsoleIntegration {
    private static final String PANEL="org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel";
    private static final String FIELD="org.lazywizard.console.overlay.v2.elements.ConsoleTextfield";
    private static final String ELEMENT="org.lazywizard.console.overlay.v2.elements.BaseConsoleElement";
    private static final String LOG="org.lazywizard.console.overlay.v2.elements.ConsoleTextElement";
    private Class<?> type;
    private MethodHandle instanceGetter,parentGetter,recreate,close,inputGetter,inputSetter,cursorSetter,dirtySetter,logGetter;
    private MethodHandle matchesGetter,suggestionsBuilder,elementParentGetter;
    private Object instance;
    private CustomPanelAPI field;
    private boolean checked,available,controllerSession,requestControllerLayout,layoutApplied,layoutFailed;
    private boolean previousKeyboard;
    private float previousHeight,previousWidth,previousScale;
    private String status="Console Commands is not enabled";

    public boolean available(){
        if(checked)return available;
        if(Global.getSettings()==null)return false;
        checked=true;
        if(!Global.getSettings().getModManager().isModEnabled("lw_console"))return false;
        try{
            type=Class.forName(PANEL,false,getClass().getClassLoader());
            var lookup=MethodHandles.publicLookup();
            instanceGetter=lookup.findStatic(type,"getInstance",MethodType.methodType(type));
            parentGetter=lookup.findVirtual(type,"getParent",MethodType.methodType(CustomPanelAPI.class));
            recreate=lookup.findVirtual(type,"recreatePanel",MethodType.methodType(void.class));
            close=lookup.findVirtual(type,"close",MethodType.methodType(void.class));
            inputGetter=lookup.findVirtual(type,"getInput",MethodType.methodType(String.class));
            inputSetter=lookup.findVirtual(type,"setInput",MethodType.methodType(void.class,String.class));
            cursorSetter=lookup.findVirtual(type,"setCursorIndex",MethodType.methodType(void.class,int.class));
            dirtySetter=lookup.findVirtual(type,"setRequiresRecreation",MethodType.methodType(void.class,boolean.class));
            logGetter=lookup.findVirtual(type,"getLogElement",MethodType.methodType(TooltipMakerAPI.class));
            matchesGetter=lookup.findVirtual(type,"getLastMatchesDisplayed",MethodType.methodType(ArrayList.class));
            suggestionsBuilder=lookup.findVirtual(type,"addSuggestionWidget",MethodType.methodType(void.class,List.class,TooltipMakerAPI.class,float.class,float.class));
            Class<?> element=Class.forName(ELEMENT,false,getClass().getClassLoader());
            elementParentGetter=lookup.findVirtual(element,"getParentElement",MethodType.methodType(TooltipMakerAPI.class));
            available=true;status="Console Commands integration ready";
        }catch(ReflectiveOperationException|LinkageError|SecurityException failure){status="This Console Commands version does not expose the supported panel API";}
        return available;
    }
    public Object activeInstance(){
        if(!available())return null;
        try{return instanceGetter.invoke();}catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}catch(Throwable failure){return null;}
    }
    public boolean isOpen(){return activeInstance()!=null;}
    public boolean controllerLayout(){return layoutApplied;}
    public String status(){return status;}

    /** Explicitly entering Console from SectorPad selects its controller layout for this session. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public boolean openControllerConsole(){
        if(!available())return false;
        try{
            Object current=activeInstance();
            if(current==null){
                Class<?> contextType=Class.forName("org.lazywizard.console.BaseCommand$CommandContext",false,getClass().getClassLoader());
                String name="MAIN_MENU";
                if(Global.getCurrentState()==GameState.CAMPAIGN)name=Global.getSector().getCampaignUI().getCurrentInteractionDialog()!=null&&Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget()!=null&&Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getMarket()!=null?"CAMPAIGN_MARKET":"CAMPAIGN_MAP";
                else if(Global.getCurrentState()==GameState.COMBAT){var engine=Global.getCombatEngine();name=engine.isSimulation()?"COMBAT_SIMULATION":engine.isInCampaign()?"COMBAT_CAMPAIGN":engine.getMissionId()!=null?"COMBAT_MISSION":"MAIN_MENU";}
                Object context=Enum.valueOf((Class)contextType,name);
                MethodHandles.publicLookup().findConstructor(type,MethodType.methodType(void.class,contextType)).invoke(context);
            }
            requestControllerLayout=current==null;controllerSession=true;return true;
        }catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}
        catch(Throwable failure){status="Console could not open: "+failure.getClass().getSimpleName();return false;}
    }
    public String input(Object expected){
        if(expected==null||activeInstance()!=expected)return null;
        try{return (String)inputGetter.invoke(expected);}catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}catch(Throwable failure){return null;}
    }
    /** Stages text only. Execution remains a separate deliberate native Enter action. */
    public boolean applyText(Object expected,String value){
        if(value==null||value.length()>4096||expected==null||activeInstance()!=expected)return false;
        try{inputSetter.invoke(expected,value);cursorSetter.invoke(expected,value.length());dirtySetter.invoke(expected,true);return true;}
        catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}
        catch(Throwable failure){status="Console text was not applied";return false;}
    }
    public void closeConsole(){
        Object current=activeInstance();if(current==null)return;
        try{close.invoke(current);}catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}catch(Throwable failure){status="Use Console Commands' normal close control";}
        forget();
    }
    public void update(boolean controllerUsed,boolean focused,boolean enabled,boolean keyboardDocked){
        update(controllerUsed,focused,enabled,keyboardDocked,1f);
    }
    public void update(boolean controllerUsed,boolean focused,boolean enabled,boolean keyboardDocked,float overlayScale){
        Object current=activeInstance();
        if(current!=instance){forget();instance=current;controllerSession=requestControllerLayout||controllerUsed;requestControllerLayout=false;}
        if(instance==null)return;
        if(controllerUsed)controllerSession=true;
        if(!enabled||!focused||!controllerSession){restore();return;}
        if(layoutFailed)return;
        try{
            CustomPanelAPI parent=(CustomPanelAPI)parentGetter.invoke(instance);
            CustomPanelAPI liveField=findField(parent,Collections.newSetFromMap(new IdentityHashMap<>()),0);
            if(liveField==null){status="Console input panel is not available yet";return;}
            float height=parent.getPosition().getHeight(),width=parent.getPosition().getWidth();
            if(field==liveField&&previousKeyboard==keyboardDocked&&previousHeight==height&&previousWidth==width&&previousScale==overlayScale&&layoutApplied)return;
            if(field==liveField&&layoutApplied){recreate.invoke(instance);liveField=findField(parent,Collections.newSetFromMap(new IdentityHashMap<>()),0);if(liveField==null)return;}
            field=liveField;previousKeyboard=keyboardDocked;previousHeight=height;previousWidth=width;previousScale=overlayScale;
            layoutApplied=true; // Any failure from here must restore the original builder.
            float top=44,fieldHeight=field.getPosition().getHeight();
            field.getPosition().inTL(25,top);
            // Let the original field renderer receive its new public coordinates immediately.
            field.getPlugin().positionChanged(field.getPosition());
            float suggestionHeight=relocateSuggestions(parent,top+fieldHeight+8,keyboardDocked);
            TooltipMakerAPI log=(TooltipMakerAPI)logGetter.invoke(instance);
            if(log!=null){
                // The tooltip is wrapped by a native scroller. Move its outer custom panel,
                // keeping the original content height so scrollback is never truncated.
                CustomPanelAPI holder=null;
                for(Object child:children(parent))if(child instanceof CustomPanelAPI panel&&!findPanels(panel,LOG).isEmpty()){holder=panel;break;}
                float logTop=top+fieldHeight+14+suggestionHeight;
                float visible=Math.min(log.getPosition().getHeight(),Math.max(40,availableLogHeight(width,height,fieldHeight,keyboardDocked,overlayScale)-suggestionHeight));
                if(holder==null)throw new IllegalStateException("Console log holder unavailable");
                holder.getPosition().setSize(holder.getPosition().getWidth(),visible);holder.getPosition().inTL(25,logTop);
                if(log.getExternalScroller()!=null){
                    var scroller=log.getExternalScroller();
                    float oldHeight=scroller.getPosition().getHeight(),offset=scroller.getYOffset();
                    scroller.getPosition().setSize(scroller.getPosition().getWidth(),visible);
                    scroller.getPosition().inTL(0,0);
                    // Keep the bottom of the same log window visible when docking shrinks it.
                    scroller.setYOffset(Math.max(0,offset+oldHeight-visible));
                }
            }
            layoutApplied=true;status="Controller console at top left";
        }catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}
        catch(Throwable failure){restore();layoutFailed=true;status="Console layout unavailable: "+failure.getClass().getSimpleName();Global.getLogger(ConsoleIntegration.class).warn(status,failure);}
    }
    public static float availableLogHeight(float height,float fieldHeight,boolean keyboard){
        return availableLogHeight(1280,height,fieldHeight,keyboard,1f);
    }
    public static float availableLogHeight(float width,float height,float fieldHeight,boolean keyboard,float overlayScale){
        float reserved=keyboard?OverlayLayout.dockedKeyboardTop(width,height,overlayScale,5):20;
        return Math.max(40,height-reserved-44-fieldHeight-34);
    }
    private float relocateSuggestions(CustomPanelAPI parent,float top,boolean keyboard) throws Throwable {
        List<CustomPanelAPI> panels=findPanels(parent,ELEMENT);
        if(panels.isEmpty())return 0;
        Object matches=matchesGetter.invoke(instance);
        if(!(matches instanceof List<?> list)||list.isEmpty())return 0;
        // Console's suggestion text captures creation coordinates. Ask its own public
        // factory to rebuild these widgets at the new location; retain its renderer,
        // completion state, colours and original command matching implementation.
        TooltipMakerAPI owner=(TooltipMakerAPI)elementParentGetter.invoke(panels.get(0).getPlugin());
        float suggestionHeight=panels.get(0).getPosition().getHeight();
        float x=panels.get(0).getPosition().getX()+10;
        List<UIPanelAPI> parents=new ArrayList<>();
        for(CustomPanelAPI panel:panels){
            TooltipMakerAPI actual=(TooltipMakerAPI)elementParentGetter.invoke(panel.getPlugin());
            if(actual!=owner)throw new IllegalStateException("Unexpected Console suggestion owner");
            UIPanelAPI actualParent=findDirectParent(owner,panel,Collections.newSetFromMap(new IdentityHashMap<>()));
            if(actualParent==null)throw new IllegalStateException("Console suggestion parent unavailable");
            parents.add(actualParent);
        }
        for(int i=0;i<panels.size();i++)parents.get(i).removeComponent(panels.get(i));
        // While a separate staged editor owns input, native completions are inactive.
        // Recreate them on closing that editor, leaving room for the output log here.
        if(keyboard)return 0;
        suggestionsBuilder.invoke(instance,list,owner,x,top+suggestionHeight);
        return suggestionHeight+8;
    }
    public void restore(){
        if(layoutApplied&&instance!=null&&activeInstance()==instance)try{recreate.invoke(instance);}catch(ThreadDeath|VirtualMachineError fatal){throw fatal;}catch(Throwable ignored){}
        layoutApplied=false;field=null;
    }
    private void forget(){instance=null;field=null;layoutApplied=false;controllerSession=false;layoutFailed=false;}
    private static List<?> children(Object node){
        List<Object> result=new ArrayList<>();
        if(node instanceof interfacenew panel)result.addAll(panel.getChildrenCopy());
        if(node instanceof com.fs.starfarer.ui.g scroller&&scroller.getContentContainer()!=null)result.add(scroller.getContentContainer());
        return result;
    }
    private static List<CustomPanelAPI> findPanels(Object root,String pluginName){
        List<CustomPanelAPI> found=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Object> pending=new ArrayDeque<>();pending.add(root);
        while(!pending.isEmpty()&&seen.size()<5000){
            Object node=pending.removeFirst();if(!seen.add(node))continue;
            if(node instanceof CustomPanelAPI panel&&panel.getPlugin()!=null&&panel.getPlugin().getClass().getName().equals(pluginName))found.add(panel);
            for(Object child:children(node))if(child!=null)pending.add(child);
        }
        return found;
    }
    private static UIPanelAPI findDirectParent(Object node,Object target,Set<Object> seen){
        if(node==null||seen.size()>5000||!seen.add(node))return null;
        for(Object child:children(node)){
            if(child==target&&node instanceof UIPanelAPI panel)return panel;
            UIPanelAPI parent=findDirectParent(child,target,seen);if(parent!=null)return parent;
        }
        return null;
    }
    private static CustomPanelAPI findField(Object node,Set<Object> seen,int depth){
        if(node==null||depth>40||seen.size()>5000||!seen.add(node))return null;
        if(node instanceof CustomPanelAPI panel&&panel.getPlugin()!=null&&panel.getPlugin().getClass().getName().equals(FIELD))return panel;
        for(Object child:children(node)){var found=findField(child,seen,depth+1);if(found!=null)return found;}
        return null;
    }
}
