package sectorpad.bridge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/** Narrow adapters for verified 0.98a map controls, with no field assignments. */
final class NativeMapAdapter {
    static final String SECTOR_MAP="com.fs.starfarer.coreui.A.G";
    static final String WARROOM="com.fs.starfarer.combat.new.OoOO";
    private static final String ZOOM_TRACKER="com.fs.starfarer.util.A";
    record Target(Object owner,UIComponentAPI bounds,boolean warroom) { }

    static Target identify(Object node) {
        try {
            if(isType(node,SECTOR_MAP)) {
                Object scroller=read(node,"getScroller");
                if(scroller instanceof UIComponentAPI component) return new Target(node,component,false);
            }
            if(isType(node,WARROOM) && node instanceof UIComponentAPI component) return new Target(node,component,true);
        } catch(Throwable ignored) { }
        return null;
    }

    /** Positive dx/dy move map contents right/up by logical UI pixels. */
    static boolean pan(Target target,float dx,float dy) {
        if(!Float.isFinite(dx) || !Float.isFinite(dy)) return false;
        try {
            if(target.warroom()) {
                // Warroom's conversion uses the logical screen center, including its current zoom/offset.
                float centerX=Global.getSettings().getScreenWidth()/2f;
                float centerY=Global.getSettings().getScreenHeight()/2f;
                Object world=PublicUiAccess.invoke(target.owner(),"computeWorldLocation",Vector2f.class,new Class<?>[]{float.class,float.class},centerX-dx,centerY-dy);
                if(!(world instanceof Vector2f point) || !Float.isFinite(point.x+point.y)) return false;
                PublicUiAccess.invoke(target.owner(),"centerOn",void.class,new Class<?>[]{float.class,float.class},point.x,point.y);
            } else {
                Object scroller=read(target.owner(),"getScroller");
                float x=((Number)read(scroller,"getXOffset")).floatValue();
                float y=((Number)read(scroller,"getYOffset")).floatValue();
                // This is the same offset operation used by native right-button map dragging.
                PublicUiAccess.invoke(scroller,"setOffset",void.class,new Class<?>[]{float.class,float.class},x+dx,y+dy);
                PublicUiAccess.invoke(scroller,"clampAndRecompute",void.class,new Class<?>[0]);
            }
            return true;
        } catch(Throwable ignored) { return false; }
    }

    /** Feed only this map's normal zoom control, retaining its own limits and smoothing. */
    @SuppressWarnings("unchecked")
    static boolean zoom(Target target,int notches,float pointerX,float pointerY) {
        if(notches==0) return true;
        try {
            // Warroom's tracker is private; ordinary focused wheel output handles that UI.
            if(target.warroom()) return false;
            Object tracker=read(read(target.owner(),"getMap"),"getZoomTracker");
            if(!isType(tracker,ZOOM_TRACKER)) return false;
            Class<?> listType=PublicUiAccess.type("com.fs.starfarer.util.A.new");
            Class<?> eventType=PublicUiAccess.type("com.fs.starfarer.util.A.C");
            Object nativeEvents=PublicUiAccess.construct(listType,new Class<?>[0]);
            List<Object> events=(List<Object>)nativeEvents;
            List<InputEventAPI> submitted=new ArrayList<>();
            int count=(int)Math.min(12L,Math.abs((long)notches)), direction=notches>0 ? 1 : -1;
            for(int i=0;i<count;i++) {
                // Native zoom uses the sign of each wheel event, rather than its magnitude.
                Object event=PublicUiAccess.construct(eventType,new Class<?>[]{InputEventClass.class,InputEventType.class,int.class,int.class,int.class,char.class},InputEventClass.MOUSE_EVENT,InputEventType.MOUSE_SCROLL,0,0,direction,'\0');
                PublicUiAccess.invoke(event,"setX",void.class,new Class<?>[]{int.class},Math.round(pointerX));
                PublicUiAccess.invoke(event,"setY",void.class,new Class<?>[]{int.class},Math.round(pointerY));
                if(((InputEventAPI)event).getEventValue()!=direction)return false;
                events.add(event); submitted.add((InputEventAPI)event);
            }
            PublicUiAccess.invoke(tracker,"o00000",void.class,new Class<?>[]{listType},nativeEvents);
            // A user's remapped controls may reject a wheel event; allow the ordinary input fallback.
            return submitted.stream().anyMatch(InputEventAPI::isConsumed);
        } catch(Throwable ignored) { return false; }
    }
    static boolean isType(Object node,String name) {
        if(node==null) return false;
        for(Class<?> type=node.getClass();type!=null;type=type.getSuperclass()) if(type.getName().equals(name)) return true;
        return false;
    }
    private static Object read(Object owner,String name) throws Throwable {
        Class<?> result=switch(name) {
            case "getScroller" -> PublicUiAccess.type("com.fs.starfarer.coreui.A.N");
            case "getMap" -> PublicUiAccess.type("com.fs.starfarer.coreui.A.H");
            case "getZoomTracker" -> PublicUiAccess.type(ZOOM_TRACKER);
            case "getXOffset", "getYOffset" -> float.class;
            default -> null;
        };
        return PublicUiAccess.invoke(owner,name,result,new Class<?>[0]);
    }
}
