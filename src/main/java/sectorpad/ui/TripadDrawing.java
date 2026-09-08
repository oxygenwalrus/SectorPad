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
        plate(x+4*s,y-6*s,w,h,16*s,alpha(SHADOW,210),null);
        plate(x,y,w,h,16*s,FIELD,STEEL);
        plate(x+3*s,y+3*s,w-6*s,h-6*s,14*s,PANEL,alpha(STEEL,95));
        // Inset top rail supplies depth; light is reserved for the frame's leading edge.
        gradient(x+7*s,y+h-Math.min(64*s,h*.3f),w-28*s,Math.min(55*s,h*.24f),alpha(KEY,0),alpha(KEY,170));
        float light=Math.min(w*.18f,118*s);
        line(x+18*s,y+h-2*s,x+18*s+light,y+h-2*s,alpha(CYAN,22),6*s);
        line(x+18*s,y+h-2*s,x+18*s+light,y+h-2*s,CYAN,1*s);
        line(x+w-16*s,y+h-3*s,x+w-4*s,y+h-15*s,alpha(CYAN,155),1*s);
        line(x+w-54*s,y+3*s,x+w-18*s,y+3*s,alpha(CYAN,110),1*s);
    }
    public static void control(float x,float y,float w,float h,float s,boolean selected){
        control(x,y,w,h,s,selected,false,1);
    }
    /** Shared with Luna elements; opacity follows the host's normal fade/deferred-render state. */
    public static void control(float x,float y,float w,float h,float s,boolean selected,boolean hovered,float opacity){
        if(!Float.isFinite(opacity)||opacity<=0)return;opacity=Math.min(1,opacity);
        Color edge=selected?FOCUS:hovered?CYAN:STEEL;
        plate(x,y,w,h,6*s,alpha(selected||hovered?SELECTED:KEY,(int)(255*opacity)),alpha(edge,(int)((selected||hovered?175:115)*opacity)));
        line(x+7*s,y+h-2*s,x+w-9*s,y+h-2*s,alpha(selected?FOCUS:CYAN,(int)((selected?100:28)*opacity)),1);
        line(x+7*s,y+1*s,x+w-2*s,y+1*s,alpha(SHADOW,(int)(170*opacity)),1);
        if(selected){
            rect(x+1*s,y+6*s,2*s,h-12*s,alpha(FOCUS,(int)(255*opacity)));
            line(x+w-6*s,y+h-1*s,x+w-1*s,y+h-6*s,alpha(FOCUS,(int)(255*opacity)),1);
        }
    }
    /** Quiet vertical lighting across a rectangular interior, with no texture or animation. */
    public static void gradient(float x,float y,float w,float h,Color bottom,Color top){
        if(w<=0||h<=0)return;
        int shade=GL11.glGetInteger(GL11.GL_SHADE_MODEL);GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(bottom.getRed()/255f,bottom.getGreen()/255f,bottom.getBlue()/255f,bottom.getAlpha()/255f);
        GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);
        GL11.glColor4f(top.getRed()/255f,top.getGreen()/255f,top.getBlue()/255f,top.getAlpha()/255f);
        GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();GL11.glShadeModel(shade);
    }
    /** Static partial orbit. Gaps keep instrument reticles legible over game content. */
    public static void arc(float x,float y,float r,float fromDegrees,float toDegrees,Color c,float stroke){
        if(r<=0)return;color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_STRIP);
        int steps=Math.max(4,(int)(Math.abs(toDegrees-fromDegrees)/4));
        for(int i=0;i<=steps;i++){double a=Math.toRadians(fromDegrees+(toDegrees-fromDegrees)*i/steps);GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();
    }
    public static void orbit(float x,float y,float r,Color c,float stroke){
        color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<64;i++){double a=i*Math.PI/32;GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();
    }
    /** Small functional symbols, shared by hub actions and refit controls. */
    public static void glyph(String id,float x,float y,float r,Color c){
        if(id==null)id="";float t=Math.max(1,r*.12f);
        if(id.contains("numeric")||id.contains("number")||id.contains("quantity")||id.contains("cargo")){
            // Cargo quantities and numeric entry share a count grid, distinct from the keyboard.
            for(int row=-1;row<=1;row++)for(int col=-1;col<=1;col++)
                rect(x+col*r*.7f-r*.14f,y+row*r*.7f-r*.14f,r*.28f,r*.28f,c);
        }else if(id.contains("keyboard")){
            line(x-r,y-r*.6f,x+r,y-r*.6f,c,t);line(x-r,y+r*.6f,x+r,y+r*.6f,c,t);
            line(x-r,y-r*.6f,x-r,y+r*.6f,c,t);line(x+r,y-r*.6f,x+r,y+r*.6f,c,t);
            for(int i=-1;i<=1;i++)rect(x+i*r*.55f-r*.08f,y,r*.16f,r*.16f,c);
            line(x-r*.5f,y-r*.3f,x+r*.5f,y-r*.3f,c,t);
        }else if(id.contains("pointer")||id.contains("right")&&!id.contains("target")){
            line(x-r*.5f,y+r,x-r*.5f,y-r*.85f,c,t);line(x-r*.5f,y+r,x+r*.7f,y-r*.2f,c,t);
            line(x+r*.7f,y-r*.2f,x+r*.05f,y-r*.2f,c,t);line(x+r*.05f,y-r*.2f,x+r*.45f,y-r,c,t);
        }else if(id.contains("map")||id.contains("intel")){
            float[] p={x-r,y-r*.7f,x-r,y+r*.7f,x-r*.3f,y+r,x+r*.3f,y+r*.65f,x+r,y+r,x+r,y-r*.6f,x+r*.3f,y-r,x-r*.3f,y-r*.6f,x-r,y-r*.7f};
            for(int i=0;i<p.length-2;i+=2)line(p[i],p[i+1],p[i+2],p[i+3],c,t);
            line(x-r*.3f,y-r*.6f,x-r*.3f,y+r,c,t);line(x+r*.3f,y-r,x+r*.3f,y+r*.65f,c,t);
        }else if(id.contains("cancel")||id.contains("close")){
            line(x-r*.7f,y-r*.7f,x+r*.7f,y+r*.7f,c,t);line(x-r*.7f,y+r*.7f,x+r*.7f,y-r*.7f,c,t);
        }else if(id.contains("multi")||id.contains("shift")){
            plate(x-r,y-r*.4f,r*1.35f,r*1.35f,0,alpha(c,0),c);
            line(x-r*.3f,y-r,x+r,y-r,c,t);line(x+r,y-r,x+r,y+r*.3f,c,t);
        }else if(id.contains("scroll")){
            boolean down=id.contains("down");float flip=down?-1:1;
            line(x,y-r*flip,x,y+r*flip,c,t);line(x,y+r*flip,x-r*.65f,y+r*.35f*flip,c,t);line(x,y+r*flip,x+r*.65f,y+r*.35f*flip,c,t);
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
