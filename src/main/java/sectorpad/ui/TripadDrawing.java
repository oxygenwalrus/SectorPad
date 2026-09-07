package sectorpad.ui;

import java.awt.Color;
import org.lwjgl.opengl.GL11;
import static sectorpad.ui.TripadTheme.*;

/** Shared original vector chrome. Caller owns and restores GL state. */
public final class TripadDrawing {
    private TripadDrawing() { }
    private static void color(Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    public static void rect(float x,float y,float w,float h,Color c){
        if(w<=0||h<=0)return;color(c);GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();
    }
    public static void line(float x,float y,float tx,float ty,Color c,float stroke){
        color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINES);GL11.glVertex2f(x,y);GL11.glVertex2f(tx,ty);GL11.glEnd();
    }
    public static void plate(float x,float y,float w,float h,float cut,Color fill,Color edge){
        cut=Math.max(0,Math.min(cut,Math.min(w,h)/3));
        float[] p={x+cut,y,x+w,y,x+w,y+h-cut,x+w-cut,y+h,x,y+h,x,y+cut};
        color(fill);GL11.glBegin(GL11.GL_POLYGON);for(int i=0;i<p.length;i+=2)GL11.glVertex2f(p[i],p[i+1]);GL11.glEnd();
        if(edge!=null){color(edge);GL11.glLineWidth(1);GL11.glBegin(GL11.GL_LINE_LOOP);for(int i=0;i<p.length;i+=2)GL11.glVertex2f(p[i],p[i+1]);GL11.glEnd();}
    }
    public static void frame(float x,float y,float w,float h,float s){
        plate(x+4*s,y-5*s,w,h,14*s,alpha(SHADOW,210),null);
        plate(x,y,w,h,14*s,PANEL,STEEL);
        line(x+18*s,y+h-1*s,x+Math.min(w*.2f,126*s),y+h-1*s,CYAN,2*s);
        line(x+w-32*s,y+1*s,x+w-12*s,y+1*s,alpha(CYAN,130),2*s);
    }
    public static void control(float x,float y,float w,float h,float s,boolean selected){
        plate(x,y,w,h,6*s,selected?SELECTED:KEY,selected?alpha(FOCUS,180):alpha(STEEL,120));
        if(selected)rect(x,y+5*s,3*s,h-10*s,FOCUS);
    }
    public static void orbit(float x,float y,float r,Color c,float stroke){
        color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<64;i++){double a=i*Math.PI/32;GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();
    }
    /** Small functional symbols, shared by hub actions and refit controls. */
    public static void glyph(String id,float x,float y,float r,Color c){
        if(id==null)id="";float t=Math.max(1,r*.12f);
        if(id.contains("keyboard")||id.contains("numeric")||id.contains("number")){
            line(x-r,y-r*.6f,x+r,y-r*.6f,c,t);line(x-r,y+r*.6f,x+r,y+r*.6f,c,t);
            line(x-r,y-r*.6f,x-r,y+r*.6f,c,t);line(x+r,y-r*.6f,x+r,y+r*.6f,c,t);
            for(int i=-1;i<=1;i++)rect(x+i*r*.55f-r*.08f,y,r*.16f,r*.16f,c);
            line(x-r*.5f,y-r*.3f,x+r*.5f,y-r*.3f,c,t);
        }else if(id.contains("setup")||id.contains("settings")||id.contains("tool")){
            for(int i=-1;i<=1;i++){float yy=y+i*r*.65f,xx=x+i*r*.35f;line(x-r,yy,x+r,yy,c,t);rect(xx-r*.13f,yy-r*.22f,r*.26f,r*.44f,c);}
        }else if(id.contains("help")||id.contains("diagnostic")){
            orbit(x,y,r,c,t);rect(x-r*.09f,y-r*.45f,r*.18f,r*.65f,c);rect(x-r*.09f,y+r*.4f,r*.18f,r*.18f,c);
        }else if(id.contains("console")){
            line(x-r,y+r*.6f,x-r*.25f,y,c,t);line(x-r*.25f,y,x-r,y-r*.6f,c,t);line(x,y-r*.6f,x+r,y-r*.6f,c,t);
        }else if(id.contains("ship")||id.contains("refit")||id.contains("fleet")||id.contains("simulation")){
            line(x,y+r,x-r*.75f,y-r,c,t);line(x-r*.75f,y-r,x,y-r*.5f,c,t);line(x,y-r*.5f,x+r*.75f,y-r,c,t);line(x+r*.75f,y-r,x,y+r,c,t);
        }else if(id.contains("pause")){
            rect(x-r*.55f,y-r*.75f,r*.3f,r*1.5f,c);rect(x+r*.25f,y-r*.75f,r*.3f,r*1.5f,c);
        }else{
            orbit(x,y,r*.7f,c,t);line(x-r,y,x-r*.35f,y,c,t);line(x+r*.35f,y,x+r,y,c,t);
            line(x,y-r,x,y-r*.35f,c,t);line(x,y+r*.35f,x,y+r,c,t);
        }
    }
}
