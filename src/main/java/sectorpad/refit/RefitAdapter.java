package sectorpad.refit;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.coreui.refit.U;
import com.fs.starfarer.ui.n;
import sectorpad.bridge.DesktopInputBridge;
import sectorpad.bridge.ReadOnlyUiNavigator;
import sectorpad.game.StateAccess;
import sectorpad.diagnostics.Diagnostics;
import java.lang.invoke.*;
import java.util.*;

/** 0.98a-RC8 public native getters only. All writes are ordinary clicks on live native controls. */
public final class RefitAdapter {
    public record Item(String id,String label,String detail,String action,float x,float y,boolean mount) { }
    public record Action(String id,String label,boolean enabled,String reason,boolean dialog) { }
    public record Snapshot(Object owner,Object member,Object variant,Object modal,String revision,String shipId,
        String shipName,String hullName,String sprite,int unusedOP,int totalOP,int vents,int capacitors,
        List<Item> ships,List<Item> mounts,List<Item> hullmods,List<Item> groups,List<String> stats,
        List<Action> actions,String capability) {
        public Snapshot { ships=List.copyOf(ships);mounts=List.copyOf(mounts);hullmods=List.copyOf(hullmods);
            groups=List.copyOf(groups);stats=List.copyOf(stats);actions=List.copyOf(actions); }
        public Action action(String id){return actions.stream().filter(a->a.id().equals(id)).findFirst().orElse(null);}
        public boolean blocked(){return modal!=null;}
    }
    public record Intent(Object owner,Object member,Object variant,Object modal,String revision,String action) { }
    private record Target(UIComponentAPI component,boolean dialog) { }
    private final Map<String,Target> targets=new LinkedHashMap<>();
    private final ReadOnlyUiNavigator navigation=new ReadOnlyUiNavigator();
    private Snapshot current;
    private String status="Open the native refit screen first.";
    private static final MethodHandle SLOT=slotGetter();
    private final Map<String,float[]> origins=new LinkedHashMap<>();
    private float[] diagramOrigin(ShipHullSpecAPI hull){
        if(origins.size()>64)origins.clear();
        return origins.computeIfAbsent(hull.getHullId(),id->{
            try{var data=Global.getSettings().loadJSON(hull.getShipFilePath());var center=data.getJSONArray("center");
                return new float[]{(float)(center.getDouble(1)-data.getDouble("height")/2),(float)(data.getDouble("width")/2-center.getDouble(0))};
            }catch(Exception unavailable){return new float[]{0,0};}
        });
    }
    private static MethodHandle slotGetter(){
        try{return MethodHandles.publicLookup().findVirtual(com.fs.starfarer.coreui.refit.returnsuper.class,
            "\u00d600000",MethodType.methodType(com.fs.starfarer.loading.specs.nullsuper.class));}
        catch(ReflectiveOperationException|LinkageError unavailable){return null;}
    }
    public String status(){return status;}
    public Snapshot current(){return current;}
    public Snapshot refresh(){
        current=null;targets.clear();
        try{
            if(Global.getCurrentState()!=GameState.CAMPAIGN||Global.getSector()==null||Global.getSector().getCampaignUI().getCurrentCoreTab()!=CoreUITabId.REFIT)return null;
            String version=Global.getSettings().getGameVersion();
            if(!supports(version)){status="Refit adapter requires Starsector 0.98a-RC8. Native refit remains available.";return null;}
            var root=StateAccess.uiRoot();
            U panel=tree(root).stream().filter(U.class::isInstance).map(U.class::cast).findFirst().orElse(null);
            if(panel==null||panel.getMember()==null||panel.getShipDisplay().getCurrentVariant()==null){status="Native refit is still loading.";return null;}
            var tab=panel.getRefitTab();var shipDisplay=panel.getShipDisplay();
            FleetMemberAPI member=panel.getMember();ShipVariantAPI variant=shipDisplay.getCurrentVariant();
            var ship=shipDisplay.getShip();var hull=variant.getHullSpec();
            float[] origin=diagramOrigin(hull);
            if(ship!=null&&ship.getSpriteAPI()!=null){var sprite=ship.getSpriteAPI();origin=new float[]{sprite.getCenterY()-sprite.getHeight()/2,sprite.getWidth()/2-sprite.getCenterX()};}
            Object modal=ReadOnlyUiNavigator.modalIdentity(root);
            // The core frame is itself a native dialog; only nested dialogs block the workspace.
            if(modal==panel.getCoreUI()||modal==root)modal=null;
            if(shipDisplay.isShowingDialog()&&modal==null)modal=shipDisplay;
            List<Item> ships=new ArrayList<>(),mounts=new ArrayList<>(),mods=new ArrayList<>(),groups=new ArrayList<>();
            List<Action> actions=new ArrayList<>();
            tab.getButtonToMember().entrySet().stream().sorted(Comparator.comparingDouble(e->-e.getKey().getPosition().getY())).forEach(e->{
                String id="ship:"+e.getValue().getId();String name=Objects.toString(e.getValue().getShipName(),e.getValue().getHullSpec().getHullName());add(actions,id,name,e.getKey(),false);
                ships.add(new Item(id,name,e.getValue().getHullSpec().getHullName(),id,0,0,false));
            });
            Map<String,n> buttons=new HashMap<>();
            for(Object node:tree(shipDisplay))if(node instanceof n button){
                WeaponSlotAPI slot=slot(button);if(slot!=null)buttons.put(slot.getId(),button);
            }
            for(WeaponSlotAPI slot:hull.getAllWeaponSlotsCopy()){
                if(slot.isDecorative()||slot.isSystemSlot()||slot.isHidden()||slot.isStationModule())continue;
                String id="slot:"+slot.getId();var spec=variant.getWeaponSpec(slot.getId());
                boolean module=slot.isStationModule();String label=module?"Module "+slot.getId():spec==null?"Empty "+slot.getSlotSize().toString().toLowerCase()+" mount":spec.getWeaponName();
                String detail=slot.getId()+" · "+slot.getSlotSize()+" "+slot.getWeaponType()+" · "+(slot.isHardpoint()?"Hardpoint":"Turret")+" · Arc "+(int)slot.getArc()+"°";
                if(slot.isBuiltIn())detail+=" · Built in";
                add(actions,id,label,slot.isBuiltIn()?null:buttons.get(slot.getId()),true);
                mounts.add(new Item(id,label,detail,id,slot.getLocation().x+origin[0],slot.getLocation().y+origin[1],true));
            }
            List<n> fighterButtons=panel.getFightersDisplay().getButtons();
            for(int i=0;i<fighterButtons.size();i++){
                String id="bay:"+i;var wing=variant.getWing(i);String label="Bay "+(i+1)+": "+(wing==null?"Empty":wing.getWingName());
                add(actions,id,label,fighterButtons.get(i),true);mounts.add(new Item(id,label,"Choose a fighter wing in the native picker",id,0,0,false));
            }
            for(n button:shipDisplay.getModuleButtons()){
                if(!(button.getButtonPanel() instanceof com.fs.starfarer.coreui.refit.OOOo renderer)||renderer.getSlot()==null)continue;
                String id="module:"+renderer.getSlot().getId();String label="Module "+renderer.getSlot().getId();
                add(actions,id,label,renderer.isSelectable()?button:null,false);
                var location=renderer.getSlot().getLocation();
                mounts.add(new Item(id,label,"Open the module's native refit controls",id,location.x+origin[0],location.y+origin[1],true));
            }
            for(String id:variant.getHullMods()){
                var spec=Global.getSettings().getHullModSpec(id);
                String kind=variant.getSMods().contains(id)||variant.getSModdedBuiltIns().contains(id)?"S-mod":spec.hasTag("dmod")?"D-mod":hull.getBuiltInMods().contains(id)?"Built in":"Installed";
                mods.add(new Item("mod:"+id,spec.getDisplayName(),kind+" · "+id,"hullmods",0,0,false));
            }
            int g=0;for(var group:variant.getWeaponGroups()){
                List<String> names=new ArrayList<>();for(String slot:group.getSlots()){var w=variant.getWeaponSpec(slot);if(w!=null)names.add(w.getWeaponName());}
                groups.add(new Item("group:"+g,"Group "+(++g)+" · "+group.getType(),(group.isAutofireOnByDefault()?"Autofire":"Manual")+" · "+String.join(", ",names),"groups",0,0,false));
            }
            var design=panel.getDesignDisplay();var modDisplay=panel.getModDisplay();
            add(actions,"vents-","Remove vent",modDisplay.getVents().getDown(),false);add(actions,"vents+","Add vent",modDisplay.getVents().getUp(),false);
            add(actions,"caps-","Remove capacitor",modDisplay.getCapacitors().getDown(),false);add(actions,"caps+","Add capacitor",modDisplay.getCapacitors().getUp(),false);
            add(actions,"hullmods","Add / inspect hullmods",modDisplay.getMods().getAdd(),true);add(actions,"smods","Build in hullmod",modDisplay.getMods().getPerm(),true);
            add(actions,"groups","Edit weapon groups",design.getGroupsButton(),true);add(actions,"autofit","Autofit / manage variants",design.getManageButton(),true);
            add(actions,"save","Save fitting",design.getSaveButton(),false);add(actions,"undo","Undo fitting changes",design.getUndoButton(),false);
            add(actions,"simulation","Run simulation",design.getRunSimulationButton(),true);
            add(actions,"name","Ship name",design.getShipNameTextField(),false);add(actions,"variant-name","Variant name",design.getVariantTextField(),false);
            add(actions,"strip","Strip fitting",textButton(design,"Strip"),false);add(actions,"restore","Restore ship",textButton(design,"Restore"),true);
            add(actions,"next-op","Next ship with free OP",textButton(design,"Next w/ free OP"),false);
            add(actions,"additional","Additional mod options",textButton(root,"Additional Options"),true);
            var officerButtons=tree(panel.getOfficerAndCRDisplay()).stream().filter(n.class::isInstance).map(n.class::cast).filter(n::isEnabled).toList();
            add(actions,"officer","Officer controls",officerButtons.size()==1?officerButtons.get(0):null,true);
            List<String> stats=new ArrayList<>();stats.add(tab.getParentData()!=null?"Module · readiness follows parent ship":"Combat readiness: "+Math.round(member.getRepairTracker().getCR()*100)+"%");
            stats.add("Captain: "+member.getCaptain().getNameString());
            if(ship!=null){stats.add("Hull: "+Math.round(ship.getMaxHitpoints())+"   Armor: "+Math.round(ship.getArmorGrid().getArmorRating()));
                stats.add("Flux capacity: "+Math.round(ship.getMaxFlux()));stats.add("Dissipation: "+Math.round(ship.getMutableStats().getFluxDissipation().getModifiedValue()));
                stats.add("Speed: "+Math.round(ship.getMaxSpeed()));}
            String revision=revision(variant)+":"+cargoRevision(Global.getSector().getPlayerFleet().getCargo());
            if(panel.getOtherEntity()!=null&&panel.getOtherEntity().getMarket()!=null)
                for(var submarket:panel.getOtherEntity().getMarket().getSubmarketsCopy())revision+=":"+cargoRevision(submarket.getCargo());
            status=SLOT==null?"Weapon slot lookup unavailable; native pointer access remains available.":"Native refit controls · 0.98a-RC8";
            current=new Snapshot(tab,member,variant,modal,revision,Objects.toString(member.getId(),hull.getHullId()),Objects.toString(member.getShipName(),hull.getHullName()),hull.getHullName(),Objects.toString(hull.getSpriteName(),""),variant.getUnusedOP(panel.getStats()),hull.getOrdnancePoints(panel.getStats()),variant.getNumFluxVents(),variant.getNumFluxCapacitors(),ships,mounts,mods,groups,stats,actions,status);
            Diagnostics.state("refit.capability",SLOT==null?"slots_unavailable":"rc8_available");
            Diagnostics.state("refit.modal",modal==null?"none":modal==shipDisplay?"ship_picker":modal.getClass().getSimpleName());return current;
        }catch(RuntimeException|LinkageError unavailable){status="Refit adapter unavailable: "+unavailable.getClass().getSimpleName()+". Use native refit.";Diagnostics.error("refit.capability_failed",unavailable);return null;}
    }
    public static boolean supports(String version){return "0.98a-RC8".equals(version);}
    public static String nativeRowLabel(Object scope,Object node){
        try{
            if(scope instanceof com.fs.starfarer.coreui.refit.ModPickerDialogV3 dialog && node instanceof com.fs.starfarer.coreui.refit.L row){
                if(!supports(Global.getSettings().getGameVersion()))return null;
                var spec=row.getSpec();var ship=dialog.getRefitPanel().getShipDisplay().getShip();
                if(spec!=null&&ship!=null){
                    var v=ship.getVariant();String id=spec.getId();
                    boolean removable=!dialog.isPermMode()&&v.hasHullMod(id)&&!row.isBuiltIn(spec,ship)&&!v.getSMods().contains(id);
                    if(removable||dialog.canInstall(spec,ship)&&dialog.canAfford(spec,ship))return spec.getDisplayName();
                }
            }
        }catch(RuntimeException|LinkageError unavailable){Diagnostics.event("refit.focus_unavailable");}
        return null;
    }
    private static String revision(ShipVariantAPI v){
        return v.getHullVariantId()+":"+v.getNumFluxVents()+":"+v.getNumFluxCapacitors()+":"+v.getHullMods()+":"+v.getSMods()+":"+v.getWings()+":"+v.getFittedWeaponSlots().stream().sorted().map(s->s+"="+v.getWeaponId(s)).toList()+":"+v.getWeaponGroups().stream().map(g->g.getSlots()+":"+g.getType()+":"+g.isAutofireOnByDefault()).toList();
    }
    private void add(List<Action> actions,String id,String label,UIComponentAPI component,boolean dialog){
        // Weapon buttons are hover-gated by native oOOO, not disabled by a fitting rule.
        // Treat them as navigation targets, then require native isEnabled after ordinary hover.
        boolean enabled=component!=null&&(id.startsWith("slot:")||!(component instanceof ButtonAPI b)||b.isEnabled())&&component.getOpacity()>0;
        if(component!=null)targets.put(id,new Target(component,dialog));
        actions.add(new Action(id,label,enabled,component==null?"Native target unavailable; use native refit":enabled?"":"Unavailable under the current game's rules",dialog));
    }
    private static WeaponSlotAPI slot(n button){
        if(button.getRenderer() instanceof com.fs.starfarer.coreui.refit.returnsuper renderer&&SLOT!=null){
            try{return (WeaponSlotAPI)SLOT.invoke(renderer);}catch(Throwable unavailable){if(unavailable instanceof VirtualMachineError error)throw error;if(unavailable instanceof ThreadDeath fatal)throw fatal;return null;}}
        return null;
    }
    private static n textButton(Object root,String text){
        var matches=tree(root).stream().filter(n.class::isInstance).map(n.class::cast).filter(b->b.getText()!=null&&b.getText().startsWith(text)).toList();return matches.size()==1?matches.get(0):null;
    }
    private static List<Object> tree(Object root){
        List<Object> out=new ArrayList<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());Deque<Object> queue=new ArrayDeque<>();if(root!=null)queue.add(root);
        while(!queue.isEmpty()&&out.size()<5000){Object node=queue.removeFirst();if(!seen.add(node))continue;
            if(node instanceof CustomPanelAPI p&&p.getPlugin()!=null&&p.getPlugin().getClass().getName().startsWith("sectorpad."))continue;
            if(node instanceof UIComponentAPI c&&c.getOpacity()<=.01f)continue;out.add(node);
            Object children=StateAccess.read(node,"getChildrenCopy");if(children instanceof List<?> list)for(Object child:list)if(child!=null)queue.addLast(child);
        }return out;
    }
    public Intent capture(String action){Snapshot s=refresh();Action a=s==null?null:s.action(action);
        if(a==null)return null;if(!a.enabled()){status=a.reason();return null;}if(s.blocked()){status="Finish the native dialog before selecting another refit action.";return null;}
        return new Intent(s.owner(),s.member(),s.variant(),s.modal(),s.revision(),action);
    }
    private static String cargoRevision(com.fs.starfarer.api.campaign.CargoAPI cargo){return cargo==null?"none":cargo.getCredits().get()+":"+cargo.getStacksCopy().stream().map(s->s.getType()+":"+s.getData()+":"+s.getSize()).toList();}

    public static boolean valid(Intent i,Snapshot s){return i!=null&&s!=null&&i.owner()==s.owner()&&i.member()==s.member()&&i.variant()==s.variant()&&i.modal()==s.modal()&&!s.blocked()&&i.revision().equals(s.revision())&&s.action(i.action())!=null&&s.action(i.action()).enabled();}
    public boolean prepare(Intent intent,DesktopInputBridge bridge){
        Snapshot s=refresh();if(!valid(intent,s)||!bridge.isActive()){status="Refit changed; select the action again.";return false;}
        Target t=targets.get(intent.action());if(t==null)return false;
        navigation.refresh(StateAccess.uiRoot());
        // Reveal an existing native scroll target before moving the pointer. No native widget is changed.
        var candidate=navigation.getCandidates().stream().filter(c->c.component()==t.component()).findFirst().orElse(null);
        var p=t.component().getPosition();if(p==null)return false;
        float x=p.getCenterX(),y=p.getCenterY();
        float reveal=candidate==null?0:navigation.reveal(candidate);
        y+=reveal;
        if(!Float.isFinite(x+y)||x<0||y<0||x>Global.getSettings().getScreenWidth()||y>Global.getSettings().getScreenHeight()){status="Native control is outside the visible screen. Use native refit.";return false;}
        bridge.movePointer(x,y);
        return true;
    }
    public boolean activate(Intent intent,DesktopInputBridge bridge){
        if(!valid(intent,refresh())||!bridge.isActive()){status="Refit changed; select the action again.";return false;}
        Target t=targets.get(intent.action());
        // Native precise hit testing reads the last polled pointer, so prepare runs in an earlier frame.
        if(t==null||t.component() instanceof ButtonAPI b&&!b.isEnabled()){
            status="Native target is not ready. Use native refit or select it again.";return false;
        }
        var p=t.component().getPosition();
        if(Math.abs(bridge.getPointerX()-p.getCenterX())>3||Math.abs(bridge.getPointerY()-p.getCenterY())>3){status="Native layout or pointer moved; select the action again.";return false;}
        bridge.mouseClick(0);Diagnostics.event("refit.action."+(intent.action().contains(":")?intent.action().substring(0,intent.action().indexOf(':')):intent.action()));return true;
    }
}
