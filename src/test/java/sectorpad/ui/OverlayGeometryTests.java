package sectorpad.ui;

import sectorpad.core.TextEntryModel;
import java.awt.Color;

/** Meaningful layout invariants and text-hit behavior, independent of a GL context. */
public final class OverlayGeometryTests {
    private static int assertions;
    public static void main(String[] args){
        float[][] screens={{1280,800},{1280,720},{1024,720},{1920,1080},{853.3333f,480}};
        float[] scales={.75f,1,1.35f,1.75f,2.5f,Float.NaN,-1,Float.POSITIVE_INFINITY};
        for(float[] screen:screens)for(float scale:scales)for(boolean docked:new boolean[]{false,true})for(boolean numeric:new boolean[]{false,true}){
            TextEntryModel model=new TextEntryModel();model.open("",numeric,200);
            String[] rows=model.rows();OverlayLayout.Keyboard layout=OverlayLayout.keyboard(screen[0],screen[1],scale,rows,docked);
            OverlayLayout.Rect panel=layout.panel();
            check(panel.valid()&&panel.x()>=-.01f&&panel.y()>=-.01f&&panel.right()<=screen[0]+.01f&&panel.top()<=screen[1]+.01f,"Panel stays within viewport");
            inside(layout.field(),panel,"Field remains inside keyboard");
            if(docked){
                close(panel.top(),OverlayLayout.dockedKeyboardTop(screen[0],screen[1],scale,rows.length),"Console clearance and keyboard are identical");
                check(screen[1]-panel.top()+.02f>=Math.min(170,screen[1]*.35f),"Dock retains room for the native field and log");
                close(panel.height(),OverlayLayout.keyboardHeight(screen[0],screen[1],scale,rows.length),"Dock height helper agrees");
            }
            int expected=5;for(String row:rows)expected+=row.length();check(layout.keys().size()==expected,"Every existing key remains present");
            for(OverlayLayout.Key key:layout.keys()){
                OverlayLayout.Rect r=key.bounds();inside(r,panel,"Key remains inside panel");
                float x=r.x()+r.width()/2,y=r.y()+r.height()/2;
                check(layout.keyAt(x,y)==key,"Visible key centre resolves to that exact key");
                int containing=0;for(OverlayLayout.Key other:layout.keys())if(other.bounds().contains(x,y))containing++;
                check(containing==1,"A pointer press cannot hit two keys");
                if(key.row()<rows.length){
                    model.open("",numeric,200);model.move(key.column(),key.row());model.select();
                    check(model.text().equals(String.valueOf(rows[key.row()].charAt(key.column()))),"Hit row and column preserve actual typed character");
                }
                if(scale==1&&screen[0]>=1024&&screen[1]>=720)check(r.height()>=44&&r.width()>=44,"Reference-size keys meet the 44-pixel target");
            }
            OverlayLayout.Key first=layout.keys().get(0);
            check(layout.keyAt(first.bounds().right()+2*layout.scale(),first.bounds().y()+first.bounds().height()/2)==null,"Key gap never types an adjacent character");
            check(layout.keyAt(panel.x()-1,panel.y()-1)==null,"Outside keyboard cannot type");
            check(layout.keyAt(Float.NaN,100)==null,"Invalid input coordinates cannot type");
        }
        for(float h:new float[]{720,800})for(float scale:new float[]{.75f,1,1.35f,1.75f,2.5f}){
            OverlayLayout.Wheel wheel=OverlayLayout.wheel(1280,h,scale);
            check(wheel.x()-wheel.radius()>0&&wheel.x()+wheel.radius()<1280,"Wheel is horizontally visible");
            check(wheel.titleY()<h&&wheel.footerY()-40*wheel.scale()>0,"Wheel heading and footer stay on-screen");
            check(wheel.contains(wheel.x(),wheel.y())&&!wheel.contains(wheel.x()+wheel.radius()+1,wheel.y()),"Wheel hit boundary matches its ring");
        }
        for(float[] p:new float[][]{{0,0},{1279,799},{640,400},{12,9}}){
            OverlayLayout.AimPosition a=OverlayLayout.aim(1280,800,1,p[0],p[1]);
            check(a!=null&&!a.offscreen()&&a.x()==p[0]&&a.y()==p[1],"On-screen aiming is never clamped away from the world point");
        }
        for(float[] p:new float[][]{{-100,400},{1380,400},{640,-100},{640,900},{Float.MAX_VALUE,Float.MAX_VALUE}}){
            OverlayLayout.AimPosition a=OverlayLayout.aim(1280,800,1,p[0],p[1]);
            check(a!=null&&a.offscreen()&&Float.isFinite(a.x())&&Float.isFinite(a.y()),"Off-screen point has a finite distinct edge cue");
            check(a.x()>=0&&a.x()<=1280&&a.y()>=0&&a.y()<=800,"Edge cue is visible");
            check(Math.abs(Math.hypot(a.directionX(),a.directionY())-1)<.0001,"Edge cue points toward the true world point");
        }
        check(OverlayLayout.aim(1280,800,1,Float.NaN,0)==null,"Invalid aim is omitted");
        OverlayLayout.Rect clipped=OverlayLayout.clip(new OverlayLayout.Rect(-20,10,100,80),new OverlayLayout.Rect(0,0,50,50));
        check(clipped!=null&&clipped.x()==0&&clipped.y()==10&&clipped.width()==50&&clipped.height()==40,"Focus clipping intersects the visible viewport");
        check(OverlayLayout.clip(new OverlayLayout.Rect(80,80,10,10),new OverlayLayout.Rect(0,0,50,50))==null,"Hidden focus never draws");
        check(OverlayLayout.keyboard(0,720,1,new String[]{"abc"},true).keys().isEmpty(),"Unavailable screen has no hit targets");
        check(OverlayLayout.dockedKeyboardTop(0,720,1,5)==0,"Unavailable screen has no dock reservation");
        check(!new OverlayRenderer.Aim(1,2,"",false).precision(),"Existing Aim constructor preserves manual mode");
        check(new OverlayRenderer.Aim(1,2,"",false,true).precision(),"Precision is an explicit reticle state");
        check(contrast(TripadTheme.INK,TripadTheme.KEY)>=4.5,"Key text contrast");
        check(contrast(TripadTheme.MUTED,TripadTheme.PANEL)>=4.5,"Secondary text contrast");
        check(contrast(TripadTheme.FOCUS,TripadTheme.SELECTED)>=4.5,"Selected-key text contrast");
        check(contrast(TripadTheme.CYAN,TripadTheme.FIELD)>=4.5,"Prompt text contrast");
        System.out.println("OverlayGeometryTests: "+assertions+" assertions passed across 720p, 800p, UI scaling, text hits, dock clearance, focus and reticle states");
    }
    private static void inside(OverlayLayout.Rect inner,OverlayLayout.Rect outer,String message){
        check(inner.valid()&&inner.x()>=outer.x()-.01f&&inner.y()>=outer.y()-.01f&&inner.right()<=outer.right()+.01f&&inner.top()<=outer.top()+.01f,message);
    }
    private static void close(float a,float b,String message){check(Math.abs(a-b)<.03f,message);}
    private static double luminance(Color c){return .2126*channel(c.getRed())+.7152*channel(c.getGreen())+.0722*channel(c.getBlue());}
    private static double channel(int component){double value=component/255d;return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);}
    private static double contrast(Color a,Color b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
}
