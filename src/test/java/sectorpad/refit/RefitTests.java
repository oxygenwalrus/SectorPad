package sectorpad.refit;

import java.util.*;
import static sectorpad.refit.RefitSession.Mode.*;

/** Pure lifecycle, immutable identity and layout checks. No live game or device claim. */
public final class RefitTests {
    private static int checks;
    public static void main(String[] args){
        lifecycle();identity();workspace();
        check(RefitAdapter.supports("0.98a-RC8"),"Inspected version supported");
        for(String v:new String[]{null,"0.98a-RC7","0.99a-RC8","0.98a-RC80"})check(!RefitAdapter.supports(v),"Unknown version fails closed");
        check(RefitAdapter.nativeRowLabel(new Object(),new Object())==null,"Unknown third-party rows retain native pointer access");
        System.out.println("RefitTests: "+checks+" lifecycle, stale identity, capability and layout checks passed (headless)");
    }
    private static void lifecycle(){
        Object owner=new Object();RefitSession s=new RefitSession();
        s.observe(owner,false,false,true,false,0);check(s.mode()==READY,"Idle controller does not open workspace");
        s.observe(owner,true,true,true,false,1);check(s.mode()==READY,"Top native modal prevents opening");
        s.observe(owner,false,true,true,false,2);check(s.visible(),"Active controller opens after modal ends");
        s.nativeMode();s.observe(owner,false,true,true,false,3);check(s.mode()==NATIVE,"Native choice suppresses rest of visit");
        s.observe(null,false,true,true,false,4);s.observe(owner,false,true,true,false,5);check(s.visible(),"New visit may auto-open");
        s.handoff(6,true);s.observe(owner,false,true,true,false,1_000_000_006L);check(s.mode()==HANDOFF,"Expected picker never gets timed overlay");
        s.observe(owner,true,true,true,false,1_100_000_006L);check(!s.visible(),"Native modal retains input");
        s.observe(owner,false,true,true,false,1_200_000_006L);check(s.visible(),"Cancel or completion restores workspace");
        s.handoff(2_000_000_000L,false);s.observe(owner,false,true,true,false,2_010_000_000L);check(!s.visible(),"One-shot action has input-release interval");
        s.observe(owner,false,true,true,false,3_000_000_000L);check(s.visible(),"One-shot action returns");
        s.handoff(4_000_000_000L,true);s.observe(owner,false,true,true,false,8_000_000_000L);check(s.mode()==NATIVE,"Missing picker falls back to native");
        s.open();s.handoff(9_000_000_000L,true);s.observe(null,false,true,true,true,10_000_000_000L);check(s.mode()==SUSPENDED,"Simulation suppresses opening");
        s.observe(new Object(),false,true,true,false,11_000_000_000L);check(s.visible(),"Simulation return can restore recreated native owner");
        s.handoff(12_000_000_000L,true);s.observe(owner,true,true,false,false,13_000_000_000L);check(s.mode()!=WORKSPACE,"Focus loss never opens window");
        s.open();s.handoff(14_000_000_000L,true);s.suspend();s.observe(owner,false,true,true,false,15_000_000_000L);check(s.mode()==NATIVE,"Disconnect cancels pending reopening");
        s.reset();check(s.mode()==OUTSIDE,"Reset forgets visit");s.open();check(!s.visible(),"Cannot open without owner");
    }
    private static RefitAdapter.Snapshot snapshot(Object owner,Object member,Object variant,Object modal,String revision,boolean enabled,List<RefitAdapter.Item> mounts){
        return new RefitAdapter.Snapshot(owner,member,variant,modal,revision,"ship-id","Ship","Hull","",5,100,10,20,List.of(),mounts,List.of(),List.of(),List.of(),List.of(new RefitAdapter.Action("slot:a","Mount",enabled,"unavailable",true)),"test");
    }
    private static void identity(){
        Object owner=new Object(),member=new Object(),variant=new Object();
        var i=new RefitAdapter.Intent(owner,member,variant,null,"cargo-and-variant","slot:a");
        var current=snapshot(owner,member,variant,null,"cargo-and-variant",true,List.of());
        check(RefitAdapter.valid(i,current),"Live exact identity is valid");
        check(!RefitAdapter.valid(i,snapshot(new Object(),member,variant,null,i.revision(),true,List.of())),"Core owner invalidation");
        check(!RefitAdapter.valid(i,snapshot(owner,new Object(),variant,null,i.revision(),true,List.of())),"Ship invalidation");
        check(!RefitAdapter.valid(i,snapshot(owner,member,new Object(),null,i.revision(),true,List.of())),"Module or variant replacement invalidation");
        check(!RefitAdapter.valid(i,snapshot(owner,member,variant,new Object(),i.revision(),true,List.of())),"Modal takes ownership");
        check(!RefitAdapter.valid(i,snapshot(owner,member,variant,null,"inventory-changed",true,List.of())),"Inventory or fitting invalidation");
        check(!RefitAdapter.valid(i,snapshot(owner,member,variant,null,i.revision(),false,List.of())),"Disabled target invalidation");
        check(!RefitAdapter.valid(i,null)&&!RefitAdapter.valid(null,current),"Missing capability invalidation");
        List<RefitAdapter.Item> items=new ArrayList<>();items.add(new RefitAdapter.Item("slot:a","A","Energy","slot:a",0,0,true));
        var immutable=snapshot(owner,member,variant,null,"r",true,items);items.clear();check(immutable.mounts().size()==1,"Snapshot does not retain mutable lists");
        try{immutable.actions().clear();throw new AssertionError("Mutable snapshot");}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void workspace(){
        var a=new RefitAdapter.Item("slot:a","A","Detail","slot:a",0,0,true);
        var b=new RefitAdapter.Item("slot:b","B","Detail","slot:a",10,20,true);
        Object o=new Object(),m=new Object(),v=new Object();var w=new RefitWorkspace();
        w.update(snapshot(o,m,v,null,"r",true,List.of(a,b)));
        check(w.activate().equals("choose-ships"),"Ship selector is controller focusable");
        w.move(2);check(w.selectedId().equals("slot:b"),"Ordered mount list");
        w.update(snapshot(o,m,v,null,"changed",true,List.of(b,a)));check(w.selectedId().equals("slot:b"),"Slot identity survives reordering and fitting edits");
        w.update(snapshot(o,m,new Object(),null,"changed",true,List.of(a,b)));check(w.selectedId().equals("choose-ships"),"Module replacement resets selection");
        w.details();check(w.back(),"Back closes details before yielding");check(!w.back(),"Next back yields native");
        w.section(-1);check(w.section()==4,"Sections wrap");w.section(1);check(w.section()==0,"Five sections wrap back");
        for(float width:new float[]{853,1280,1920})for(float height:new float[]{480,720,800,1080})for(float scale:new float[]{.75f,1,1.35f,1.8f}){
            var l=RefitWorkspace.layout(width,height,scale);
            check(l.x()>=0&&l.y()>=0&&l.x()+l.width()<=width&&l.y()+l.height()<=height,"Window stays in logical screen");
            check(l.pageSize()>=3&&l.pageSize()*54*l.scale()<=l.height()-250*l.scale(),"Paged rows clear footer");
        }
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
}
