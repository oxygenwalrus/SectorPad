package sectorpad.ui;

import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import sectorpad.core.RadialModel;
import sectorpad.core.TextEntryModel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import static sectorpad.ui.TripadTheme.*;

/** Draws SectorPad-owned overlays and restores the caller's GL state. */
public final class OverlayRenderer {
    private LazyFont smallFont,largeFont;
    private final List<LazyFont.DrawableString> smallLabels=new ArrayList<>(),largeLabels=new ArrayList<>();
    private int smallIndex,largeIndex;
    private boolean fontsAttempted;
    private String fontError;
    private OverlayLayout.Keyboard keyboardLayout;
    private OverlayLayout.Wheel wheelLayout;
    private Pointer pointer;
    private Focus focus;

    public record KeyHit(float x,float y,float width,float height,int row,int column) { }
    /** Position is the original native cursor hotspot. No replacement cursor is drawn. */
    public record Pointer(float x,float y,boolean precision,boolean dragging,boolean latched) { }
    /** Bounds must already be clipped to the visible pane and validated against the top modal. */
    public record Focus(float x,float y,float width,float height,boolean multiSelect) { }
    public record Aim(float x,float y,String label,boolean locked,boolean precision) {
        public Aim(float x,float y,String label,boolean locked){this(x,y,label,locked,false);}
    }
    public void setNavigation(Pointer pointer,Focus focus){
        this.pointer=pointer!=null&&Float.isFinite(pointer.x())&&Float.isFinite(pointer.y())?pointer:null;
        this.focus=focus!=null&&new OverlayLayout.Rect(focus.x(),focus.y(),focus.width(),focus.height()).valid()?focus:null;
    }
    public void clearInputLayout(){keyboardLayout=null;wheelLayout=null;}
    public OverlayLayout.Rect keyboardBounds(){return keyboardLayout==null?null:keyboardLayout.panel();}
    public KeyHit keyAt(float x,float y){
        OverlayLayout.Key hit=keyboardLayout==null?null:keyboardLayout.keyAt(x,y);
        if(hit==null)return null;
        OverlayLayout.Rect r=hit.bounds();return new KeyHit(r.x(),r.y(),r.width(),r.height(),hit.row(),hit.column());
    }
    public boolean pointWheel(RadialModel wheel,float x,float y){
        if(wheelLayout==null||!wheel.isOpen()||!wheelLayout.contains(x,y))return false;
        if(Math.hypot(x-wheelLayout.x(),y-wheelLayout.y())<wheelLayout.inner())wheel.point(0,0);
        else wheel.point((x-wheelLayout.x())/wheelLayout.radius(),(y-wheelLayout.y())/wheelLayout.radius());
        return true;
    }
    public void render(float width,float height,float scale,RadialModel wheel,TextEntryModel keyboard,
                       String banner,String footer,String dialogTitle,String dialogBody,List<String> diagnostics,Aim aim) {
        clearInputLayout();if(!OverlayLayout.validScreen(width,height))return;loadFonts();
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,width,0,height,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            smallIndex=largeIndex=0;
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float s=TripadTheme.requestedScale(scale);
            if(aim!=null)drawAim(width,height,s,aim);
            if(!wheel.isOpen()&&!keyboard.isOpen()&&dialogTitle==null&&focus!=null)drawFocus(width,height,s,focus);
            if(wheel.isOpen())drawWheel(width,height,s,wheel,footer);
            else if(keyboard.isOpen())drawKeyboard(width,height,s,keyboard,footer);
            else if(dialogTitle!=null)drawDialog(width,height,s,dialogTitle,dialogBody,footer);
            else if(banner!=null&&!banner.isBlank())drawStatus(width,height,s,banner,footer);
            if(diagnostics!=null&&!diagnostics.isEmpty())drawDiagnostics(width,height,s,diagnostics);
            if(pointer!=null)drawPointer(width,height,s,pointer);
        }finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }
    private void loadFonts(){
        if(fontsAttempted)return;fontsAttempted=true;
        try{smallFont=LazyFont.loadFont("graphics/fonts/insignia17LTaa.fnt");}catch(Exception ex){fontError=ex.getMessage();}
        try{largeFont=LazyFont.loadFont("graphics/fonts/insignia21LTaa.fnt");}catch(Exception ex){if(smallFont==null)fontError=ex.getMessage();}
        if(smallFont==null)smallFont=largeFont;if(largeFont==null)largeFont=smallFont;if(smallFont!=null)fontError=null;
    }
    private void drawPointer(float width,float height,float scale,Pointer value){
        if(value.x()<0||value.x()>width||value.y()<0||value.y()>height)return;
        float s=Math.min(scale,Math.min(width,height)/200f),x=value.x()-10*s,y=value.y()-34*s,w=40*s,h=44*s;
        brackets(x,y,w,h,10*s,SHADOW,5*s);brackets(x,y,w,h,10*s,CYAN,2*s);
        if(value.precision()){
            float cx=x+w/2,cy=y+h/2;
            float[] m={x-7*s,cy,x-2*s,cy,x+w+2*s,cy,x+w+7*s,cy,cx,y-7*s,cx,y-2*s,cx,y+h+2*s,cx,y+h+7*s};
            segments(m,SHADOW,5*s);segments(m,FOCUS,2*s);
        }
        if(value.dragging()||value.latched()){
            segments(new float[]{x+12*s,y-6*s,x+28*s,y-6*s},SHADOW,6*s);
            segments(new float[]{x+12*s,y-6*s,x+28*s,y-6*s},FOCUS,3*s);
        }
        if(value.latched()){
            float lx=x+w+8*s,ly=y+2*s;
            outline(lx,ly,8*s,7*s,SHADOW,5*s);outline(lx,ly,8*s,7*s,FOCUS,2*s);
            float[] m={lx+2*s,ly+7*s,lx+2*s,ly+12*s,lx+2*s,ly+12*s,lx+6*s,ly+12*s,lx+6*s,ly+12*s,lx+6*s,ly+7*s};
            segments(m,SHADOW,5*s);segments(m,FOCUS,2*s);
        }
    }
    private void drawFocus(float width,float height,float scale,Focus value){
        OverlayLayout.Rect v=OverlayLayout.clip(new OverlayLayout.Rect(value.x(),value.y(),value.width(),value.height()),new OverlayLayout.Rect(1,1,width-2,height-2));
        if(v==null)return;
        float s=Math.min(scale,Math.min(width,height)/250f),out=3*s,x=Math.max(1,v.x()-out),y=Math.max(1,v.y()-out);
        float w=Math.min(width-1,v.right()+out)-x,h=Math.min(height-1,v.top()+out)-y,leg=Math.min(14*s,Math.min(w,h)*.3f);
        brackets(x,y,w,h,leg,SHADOW,6*s);brackets(x,y,w,h,leg,FOCUS,2*s);
        if(x>=10*s)triangle(x-9*s,y+h/2-6*s,x-3*s,y+h/2,x-9*s,y+h/2+6*s,FOCUS);
        if(value.multiSelect()&&x+w+20*s<width){
            float cx=x+w+12*s,cy=y+h/2;float[] m={cx-4*s,cy,cx+4*s,cy,cx,cy-4*s,cx,cy+4*s};
            segments(m,SHADOW,6*s);segments(m,FOCUS,2*s);
        }
    }
    private void drawAim(float width,float height,float scale,Aim aim){
        OverlayLayout.AimPosition p=OverlayLayout.aim(width,height,scale,aim.x(),aim.y());if(p==null)return;
        float s=Math.min(scale,Math.min(width,height)/220f),x=p.x(),y=p.y();Color ink=aim.locked()?FOCUS:CYAN;
        if(p.offscreen()){
            float dx=p.directionX(),dy=p.directionY(),sx=-dy,sy=dx;
            float[] m={x-dx*10*s+sx*7*s,y-dy*10*s+sy*7*s,x+dx*3*s,y+dy*3*s,x+dx*3*s,y+dy*3*s,x-dx*10*s-sx*7*s,y-dy*10*s-sy*7*s};
            segments(m,SHADOW,6*s);segments(m,ink,2*s);
            aimLabel(width,height,s,x,y,aim.locked()?"Locked target off-screen":"Aim point off-screen",ink);return;
        }
        float r=17*s,gap=7*s;float[] m={x-r,y,x-gap,y,x+gap,y,x+r,y,x,y-r,x,y-gap,x,y+gap,x,y+r};
        segments(m,SHADOW,5*s);segments(m,ink,2*s);
        if(aim.precision()){
            brackets(x-11*s,y-11*s,22*s,22*s,4*s,SHADOW,4*s);brackets(x-11*s,y-11*s,22*s,22*s,4*s,ink,1.5f*s);
            circle(x,y,2.5f*s,SHADOW);circle(x,y,1.25f*s,INK);
        }
        if(aim.locked()){
            diamond(x,y+24*s,5*s,SHADOW,5*s);diamond(x,y+24*s,5*s,FOCUS,2*s);
            aimLabel(width,height,s,x,y,"Locked"+(aim.label()==null||aim.label().isBlank()?"":" · "+aim.label()),FOCUS);
        }else if(aim.precision())aimLabel(width,height,s,x,y,"Exact point",CYAN);
    }
    private void aimLabel(float width,float height,float s,float x,float y,String value,Color ink){
        float w=Math.min(235*s,width-24),tx=Math.max(12,Math.min(x+23*s,width-w-12));
        float ty=Math.max(42*s,Math.min(height-12,y<65*s?y+60*s:y-22*s));
        rect(tx-4*s,ty-35*s,w+8*s,39*s,alpha(FIELD,218));text(value,tx,ty,15*s,ink,w,32*s,false);
    }
    private void drawWheel(float width,float height,float scale,RadialModel wheel,String footer){
        wheelLayout=OverlayLayout.wheel(width,height,scale);
        float cx=wheelLayout.x(),cy=wheelLayout.y(),r=wheelLayout.radius(),inner=wheelLayout.inner(),s=wheelLayout.scale();
        rect(0,0,width,height,DIM);List<RadialModel.Entry> entries=wheel.visible();
        ring(cx,cy,r+7*s,alpha(STEEL,160),1);
        for(int i=0;i<wheel.slots();i++){
            boolean chosen=i==wheel.selected(),exists=i<entries.size(),enabled=exists&&entries.get(i).enabled();
            sector(cx,cy,inner+4*s,r,i,wheel.slots(),chosen?SELECTED:exists?PANEL:alpha(FIELD,100));
            sectorOutline(cx,cy,inner+4*s,r,i,wheel.slots(),chosen?alpha(FOCUS,120):alpha(STEEL,exists?150:40),Math.max(1,s));
            if(exists)sector(cx,cy,r-3*s,r,i,wheel.slots(),chosen?FOCUS:alpha(CYAN,enabled?100:35));
            double theta=(double)i/wheel.slots()*Math.PI*2;float sx=(float)Math.sin(theta),sy=(float)Math.cos(theta);
            if(chosen){
                float tx=cx+sx*(r-8*s),ty=cy+sy*(r-8*s),px=sy*5*s,py=-sx*5*s;
                triangle(tx+px,ty+py,tx-px,ty-py,cx+sx*(r-18*s),cy+sy*(r-18*s),FOCUS);
            }
            if(exists){
                float tx=cx+sx*r*.73f,ty=cy+sy*r*.73f;
                TripadDrawing.glyph(entries.get(i).id(),tx,ty+20*s,10*s,enabled?(chosen?FOCUS:CYAN):STEEL);
                text(entries.get(i).label(),tx,ty+2*s,17*s,enabled?INK:MUTED,r*.64f,43*s,true);
                if(!enabled)text("Unavailable",tx,ty-37*s,12*s,MUTED,r*.65f,16*s,true);
            }
        }
        circle(cx,cy,inner-3*s,FIELD);ring(cx,cy,inner-3*s,alpha(STEEL,180),Math.max(1,s));
        RadialModel.Entry selected=wheel.highlighted();
        TripadDrawing.glyph(selected==null?"hub":selected.id(),cx,cy+40*s,13*s,selected==null?CYAN:FOCUS);
        text(selected==null?"Choose an action":selected.label(),cx,cy+13*s,20*s,INK,inner*1.72f,47*s,true);
        text(selected==null?"Centre cancels":selected.enabled()?"Ready":"Unavailable",cx,cy-42*s,14*s,MUTED,inner*1.74f,26*s,true);
        text(wheel.title(),cx,wheelLayout.titleY(),28*s,INK,width-48,37*s,true);
        text("Page "+(wheel.page()+1)+" of "+wheel.pages(),cx,wheelLayout.pageY(),15*s,MUTED,width-48,21*s,true);
        String description=selected==null?"Right stick or D-pad to choose":!selected.enabled()&&selected.reason()!=null&&!selected.reason().isBlank()?selected.reason():selected.description();
        text(description,cx,wheelLayout.descriptionY(),17*s,INK,Math.min(width-48,770*s),41*s,true);
        float footW=Math.min(width-40*s,920*s);
        TripadDrawing.plate(cx-footW/2,wheelLayout.footerY()-34*s,footW,47*s,8*s,PANEL,alpha(STEEL,140));
        text(footer,cx,wheelLayout.footerY(),16*s,MUTED,footW-28*s,35*s,true);
    }
    private void drawKeyboard(float width,float height,float scale,TextEntryModel keyboard,String footer){
        String[] rows=keyboard.rows();keyboardLayout=OverlayLayout.keyboard(width,height,scale,rows,keyboard.docked());
        OverlayLayout.Rect p=keyboardLayout.panel(),field=keyboardLayout.field();float s=keyboardLayout.scale();if(!p.valid())return;
        if(!keyboard.docked())rect(0,0,width,height,alpha(DIM,174));panelFrame(p.x(),p.y(),p.width(),p.height(),s);
        text(keyboard.title(),p.x()+22*s,keyboardLayout.titleY(),24*s,INK,p.width()-44*s,30*s,false);
        rect(field.x(),field.y(),field.width(),field.height(),FIELD);outline(field.x(),field.y(),field.width(),field.height(),STEEL,Math.max(1,s));
        int capacity=Math.max(12,(int)((field.width()-28*s)/(12*s)));
        text(keyboard.visibleText(capacity),field.x()+13*s,field.top()-7*s,24*s,INK,field.width()-26*s,30*s,false);
        for(OverlayLayout.Key key:keyboardLayout.keys()){
            OverlayLayout.Rect r=key.bounds();boolean chosen=keyboard.row()==key.row()&&keyboard.column()==key.column();
            TripadDrawing.control(r.x(),r.y(),r.width(),r.height(),s,chosen);
            String label=key.row()==rows.length?new String[]{"Space","Erase","Shift","Accept","Cancel"}[key.column()]:String.valueOf(rows[key.row()].charAt(key.column()));
            if(label.equals(" ")){
                float cx=r.x()+r.width()/2,cy=r.y()+r.height()/2;
                segments(new float[]{cx-5*s,cy+3*s,cx-5*s,cy-3*s,cx-5*s,cy-3*s,cx+5*s,cy-3*s,cx+5*s,cy-3*s,cx+5*s,cy+3*s},INK,2*s);
            }else{
                Color ink=chosen?FOCUS:key.row()==rows.length&&key.column()==3?CYAN:INK;float size=(key.row()==rows.length?19:22)*s;
                text(label,r.x()+r.width()/2,r.y()+(r.height()+size)/2,size,ink,r.width()-10*s,size*1.4f,true);
            }
        }
        text(footer,p.x()+p.width()/2,keyboardLayout.footerY(),16*s,CYAN,p.width()-44*s,36*s,true);
    }
    private void drawDialog(float width,float height,float scale,String title,String body,String footer){
        float baseHeight=body==null?300:Math.min(540,300+Math.max(0,body.length()-230)/70*22);
        OverlayLayout.Rect p=OverlayLayout.panel(width,height,scale,780,baseHeight);float s=p.width()/780;
        rect(0,0,width,height,alpha(DIM,174));panelFrame(p.x(),p.y(),p.width(),p.height(),s);
        text(title,p.x()+28*s,p.top()-27*s,26*s,INK,p.width()-56*s,61*s,false);
        text(body,p.x()+28*s,p.top()-100*s,20*s,MUTED,p.width()-56*s,p.height()-172*s,false);
        text(footer,p.x()+28*s,p.y()+50*s,17*s,CYAN,p.width()-56*s,40*s,false);
    }
    private void drawStatus(float width,float height,float scale,String banner,String footer){
        float s=Math.max(.1f,Math.min(scale,Math.min((width-32)/710f,(height-32)/100f)));
        float w=Math.min(width-32,710*s),h=94*s,x=width-w-16,y=14;panelFrame(x,y,w,h,s);
        text(banner,x+14*s,y+h-12*s,18*s,INK,w-28*s,40*s,false);text(footer,x+14*s,y+38*s,16*s,MUTED,w-28*s,35*s,false);
    }
    private void drawDiagnostics(float width,float height,float scale,List<String> diagnostics){
        float s=Math.max(.1f,Math.min(scale,Math.min((width-32)/640f,(height-32)/(diagnostics.size()*23+32))));
        float w=Math.min(width-32,640*s),h=(diagnostics.size()*23+32)*s,x=16,y=height-h-16;panelFrame(x,y,w,h,s);
        for(int i=0;i<diagnostics.size();i++)text(diagnostics.get(i),x+14*s,y+h-14*s-i*23*s,16*s,INK,w-28*s,22*s,false);
    }
    private void text(String value,float x,float y,float size,Color color,float maxWidth,float maxHeight,boolean centered){
        if(smallFont==null||value==null||value.isBlank()||size<=0||maxWidth<=0||maxHeight<=0)return;
        boolean small=size<=18;List<LazyFont.DrawableString> labels=small?smallLabels:largeLabels;int index=small?smallIndex++:largeIndex++;
        if(index==labels.size())labels.add((small?smallFont:largeFont).createText());LazyFont.DrawableString label=labels.get(index);
        label.setFontSize(size);label.setMaxWidth(maxWidth);label.setMaxHeight(maxHeight);label.setBaseColor(color);label.setText(value);
        label.draw(centered?x-label.getWidth()/2:x,y);
    }
    private static void color(Color c){GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void rect(float x,float y,float w,float h,Color c){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();
    }
    private static void outline(float x,float y,float w,float h,Color c,float stroke){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();
    }
    private static void segments(float[] points,Color c,float stroke){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINES);
        for(int i=0;i+1<points.length;i+=2)GL11.glVertex2f(points[i],points[i+1]);GL11.glEnd();
    }
    private static void brackets(float x,float y,float w,float h,float leg,Color c,float stroke){
        segments(new float[]{x,y+leg,x,y,x,y,x+leg,y,x+w-leg,y,x+w,y,x+w,y,x+w,y+leg,
            x,y+h-leg,x,y+h,x,y+h,x+leg,y+h,x+w-leg,y+h,x+w,y+h,x+w,y+h,x+w,y+h-leg},c,stroke);
    }
    private static void triangle(float ax,float ay,float bx,float by,float cx,float cy,Color c){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(ax,ay);GL11.glVertex2f(bx,by);GL11.glVertex2f(cx,cy);GL11.glEnd();
    }
    private static void circle(float x,float y,float r,Color c){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y);
        for(int i=0;i<=48;i++){double a=i*Math.PI/24;GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();
    }
    private static void ring(float x,float y,float r,Color c,float stroke){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<48;i++){double a=i*Math.PI/24;GL11.glVertex2f(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r);}GL11.glEnd();
    }
    private static void diamond(float x,float y,float r,Color c,float stroke){segments(new float[]{x-r,y,x,y+r,x,y+r,x+r,y,x+r,y,x,y-r,x,y-r,x-r,y},c,stroke);}
    private static void panelFrame(float x,float y,float w,float h,float s){
        TripadDrawing.frame(x,y,w,h,s);
    }
    private static double start(int index,int slots){return index*Math.PI*2/slots-Math.PI/slots+.024;}
    private static double end(int index,int slots){return index*Math.PI*2/slots+Math.PI/slots-.024;}
    private static void sector(float x,float y,float inner,float outer,int index,int slots,Color c){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUAD_STRIP);double a0=start(index,slots),a1=end(index,slots);
        for(int i=0;i<=24;i++){double a=a0+(a1-a0)*i/24;float sx=(float)Math.sin(a),sy=(float)Math.cos(a);GL11.glVertex2f(x+sx*inner,y+sy*inner);GL11.glVertex2f(x+sx*outer,y+sy*outer);}GL11.glEnd();
    }
    private static void sectorOutline(float x,float y,float inner,float outer,int index,int slots,Color c,float stroke){
        GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(Math.max(1,stroke));GL11.glBegin(GL11.GL_LINE_LOOP);double a0=start(index,slots),a1=end(index,slots);
        for(int i=0;i<=24;i++){double a=a0+(a1-a0)*i/24;GL11.glVertex2f(x+(float)Math.sin(a)*outer,y+(float)Math.cos(a)*outer);}
        for(int i=24;i>=0;i--){double a=a0+(a1-a0)*i/24;GL11.glVertex2f(x+(float)Math.sin(a)*inner,y+(float)Math.cos(a)*inner);}GL11.glEnd();
    }
    public String error(){return fontError;}
}
