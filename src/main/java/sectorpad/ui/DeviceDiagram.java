package sectorpad.ui;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import sectorpad.core.DeviceVisual;
import sectorpad.core.PadFrame;
import static sectorpad.ui.TripadTheme.*;

/** Original device silhouettes. Only controls present in the input snapshot report activity. */
public final class DeviceDiagram {
    public static final float LOGICAL_WIDTH=520, LOGICAL_HEIGHT=200;
    public record Point(float x,float y) { }
    private static final Map<String,Point> GAMEPAD=layout(false,false);
    private static final Map<String,Point> DECK=layout(true,true);
    private static final Map<String,Point> ALLY=layout(true,false);
    private float opacity;
    private boolean connected;

    /** Logical centers, shared by schema highlighting and geometry acceptance checks. */
    public static Map<String,Point> controlCenters(DeviceVisual visual) {
        return visual==DeviceVisual.STEAM_DECK?DECK:visual==DeviceVisual.ROG_ALLY?ALLY:GAMEPAD;
    }

    /** Draws inside the supplied bottom-left-origin bounds without changing the caller's projection. */
    public void render(float x,float y,float width,float height,DeviceVisual visual,PadFrame raw,
                       Set<String> mappedControls,String selectedControl,float opacity) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)
                ||!Float.isFinite(opacity)||width<=0||height<=0||opacity<=0)return;
        if(visual==null)visual=DeviceVisual.GENERIC;
        if(raw==null)raw=PadFrame.disconnected();
        if(mappedControls==null)mappedControls=Set.of();
        this.opacity=Math.min(1,opacity);connected=raw.connected();
        float scale=Math.min(width/LOGICAL_WIDTH,height/LOGICAL_HEIGHT);
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x+(width-LOGICAL_WIDTH*scale)/2,y+(height-LOGICAL_HEIGHT*scale)/2,0);
            GL11.glScalef(scale,scale,1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(visual==DeviceVisual.STEAM_DECK||visual==DeviceVisual.ROG_ALLY)handheld(visual);
            else gamepad(visual);
            Map<String,Point> points=controlCenters(visual);
            dpadBody(points);
            for(String control:new String[]{"LT","RT","LB","RB","VIEW","MENU","A","B","X","Y",
                    "DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT"}) {
                Point p=points.get(control);
                boolean trigger=control.equals("LT")||control.equals("RT");
                float value=trigger&&connected?unit(control.equals("LT")?raw.lt():raw.rt()):0;
                boolean active=connected&&(raw.down(control)||value>.15f);
                float size=control.startsWith("DPAD_")?16:control.equals("VIEW")||control.equals("MENU")?15:20;
                boolean selected=references(selectedControl,control),mapped=mappedControls.contains(control);
                if(trigger||control.equals("LB")||control.equals("RB"))shoulderHalo(p,selected,mapped,active);
                else halo(p,size,selected,mapped,active);
                if(control.startsWith("DPAD_"))dpadDirection(control,p,active);
                else ControlGlyphs.draw(control,visual,p.x,p.y,size,active,this.opacity*(connected?1:.5f));
                if(trigger) {
                    float bx=p.x-19,by=p.y+11;
                    TripadDrawing.rect(bx,by,38,3,ink(STEEL,145));
                    if(value>0)TripadDrawing.rect(bx,by,38*value,3,ink(active?FOCUS:CYAN,235));
                }
            }
            stick(points.get("LEFT_STICK"),"LEFT_STICK","L3",visual,raw.lx(),raw.ly(),raw,mappedControls,selectedControl);
            stick(points.get("RIGHT_STICK"),"RIGHT_STICK","R3",visual,raw.rx(),raw.ry(),raw,mappedControls,selectedControl);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }

    private void handheld(DeviceVisual visual) {
        boolean deck=visual==DeviceVisual.STEAM_DECK;
        polygon(new float[]{18,20,502,20,517,35,517,147,500,164,20,164,3,147,3,35},ink(PANEL,245),ink(STEEL,220));
        TripadDrawing.line(133,27,133,157,ink(STEEL,145),1);
        TripadDrawing.line(387,27,387,157,ink(STEEL,145),1);
        TripadDrawing.plate(145,34,230,117,6,ink(FIELD,255),ink(STEEL,195));
        TripadDrawing.plate(151,40,218,105,3,ink(KEY,90),ink(STEEL,70));
        // Static screen linework describes the display surface; it is not a sensor reading.
        TripadDrawing.line(161,135,209,135,ink(CYAN,70),1);
        TripadDrawing.line(311,50,359,50,ink(CYAN,70),1);
        TripadDrawing.line(153,43,162,52,ink(STEEL,100),1);
        TripadDrawing.line(358,134,367,143,ink(STEEL,100),1);
        if(deck) {
            trackpad(68,37);trackpad(412,37);
        } else {
            // Ally speaker slots, kept separate from input indicators.
            for(int i=0;i<4;i++) {
                TripadDrawing.line(34+i*5,40,34+i*5,49,ink(STEEL,160),1);
                TripadDrawing.line(472+i*5,40,472+i*5,49,ink(STEEL,160),1);
            }
            TripadDrawing.line(8,132,22,153,ink(CYAN,110),1);
            TripadDrawing.line(498,153,512,132,ink(CYAN,110),1);
        }
    }

    private void trackpad(float x,float y) {
        TripadDrawing.plate(x,y,40,37,4,ink(FIELD,170),ink(STEEL,145));
        TripadDrawing.line(x+8,y+3,x+32,y+3,ink(STEEL,110),1);
    }

    private void dpadBody(Map<String,Point> points) {
        Point up=points.get("DPAD_UP");float x=up.x,y=up.y-15,a=7,b=22;
        float[] p={x-a,y+b,x+a,y+b,x+a,y+a,x+b,y+a,x+b,y-a,x+a,y-a,
                x+a,y-b,x-a,y-b,x-a,y-a,x-b,y-a,x-b,y+a,x-a,y+a};
        color(ink(FIELD,245));GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y);
        for(int i=0;i<=p.length;i+=2){int j=i%p.length;GL11.glVertex2f(p[j],p[j+1]);}GL11.glEnd();
        color(ink(STEEL,175));GL11.glLineWidth(1);GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<p.length;i+=2)GL11.glVertex2f(p[i],p[i+1]);GL11.glEnd();
    }

    private void dpadDirection(String control,Point p,boolean active) {
        int dx=control.endsWith("LEFT")?-1:control.endsWith("RIGHT")?1:0;
        int dy=control.endsWith("DOWN")?-1:control.endsWith("UP")?1:0;
        if(active)TripadDrawing.rect(p.x-6,p.y-6,12,12,ink(CYAN,155));
        Color edge=ink(active?FOCUS:connected?INK:MUTED,active?255:connected?205:100);
        float tx=p.x+dx*3,ty=p.y+dy*3,bx=p.x-dx*2,by=p.y-dy*2;
        TripadDrawing.line(bx-dy*3,by+dx*3,tx,ty,edge,1.5f);
        TripadDrawing.line(tx,ty,bx+dy*3,by-dx*3,edge,1.5f);
    }

    private void gamepad(DeviceVisual visual) {
        float[] shape={99,22,109,9,132,7,157,28,186,60,334,60,363,28,388,7,411,9,421,22,
                419,56,409,103,395,143,379,160,351,168,169,168,141,160,125,143,111,103,101,56};
        polygon(shape,ink(PANEL,245),ink(STEEL,230));
        TripadDrawing.line(180,163,340,163,ink(CYAN,90),1);
        TripadDrawing.line(113,46,129,34,ink(STEEL,120),1);
        TripadDrawing.line(391,34,407,46,ink(STEEL,120),1);
        // An unbranded center inset keeps the outline original rather than copying a manufacturer logo.
        TripadDrawing.plate(248,139,24,13,3,ink(KEY,160),ink(STEEL,145));
        if(visual==DeviceVisual.XBOX) {
            TripadDrawing.arc(260,146,4,20,160,ink(CYAN,115),1);
        }
    }

    private void stick(Point p,String axis,String click,DeviceVisual visual,float dx,float dy,PadFrame raw,
                       Set<String> mapped,String selected) {
        dx=connected?signed(dx):0;dy=connected?signed(dy):0;
        float magnitude=(float)Math.hypot(dx,dy);
        if(magnitude>1){dx/=magnitude;dy/=magnitude;}
        boolean pressed=connected&&raw.down(click),moving=magnitude>.12f;
        boolean chosen=references(selected,axis)||references(selected,click);
        halo(p,35,chosen,mapped.contains(axis)||mapped.contains(click),moving||pressed);
        disc(p.x,p.y,16,ink(FIELD,250));
        TripadDrawing.orbit(p.x,p.y,16,ink(connected?STEEL:MUTED,connected?205:65),1);
        TripadDrawing.line(p.x-11,p.y,p.x+11,p.y,ink(STEEL,70),1);
        TripadDrawing.line(p.x,p.y-11,p.x,p.y+11,ink(STEEL,70),1);
        float kx=p.x+dx*9,ky=p.y+dy*9;
        if(moving)TripadDrawing.line(p.x,p.y,kx,ky,ink(CYAN,230),2);
        disc(kx,ky,10,ink(pressed?FOCUS:moving?SELECTED:KEY,245));
        TripadDrawing.orbit(kx,ky,10,ink(pressed?FOCUS:moving?CYAN:STEEL,230),1);
        ControlGlyphs.draw(click,visual,kx,ky,16,pressed,opacity*(connected?1:.5f));
    }

    private void halo(Point p,float size,boolean selected,boolean mapped,boolean active) {
        if(mapped)TripadDrawing.orbit(p.x,p.y,size*.5f+3,ink(connected?CYAN:MUTED,connected?105:55),1);
        if(active) {
            disc(p.x,p.y,size*.5f+4,ink(selected?FOCUS:CYAN,55));
            TripadDrawing.orbit(p.x,p.y,size*.5f+4,ink(selected?FOCUS:CYAN,245),2);
        }
        if(selected) {
            TripadDrawing.orbit(p.x,p.y,size*.5f+6,ink(FOCUS,245),1.5f);
            TripadDrawing.line(p.x-size*.5f-10,p.y,p.x-size*.5f-7,p.y,ink(FOCUS,245),2);
            TripadDrawing.line(p.x+size*.5f+7,p.y,p.x+size*.5f+10,p.y,ink(FOCUS,245),2);
        }
    }

    private void shoulderHalo(Point p,boolean selected,boolean mapped,boolean active) {
        if(mapped||active)TripadDrawing.plate(p.x-18,p.y-13,36,26,5,
                ink(active?CYAN:FIELD,active?55:0),ink(connected?CYAN:MUTED,active?245:connected?105:55));
        if(selected)TripadDrawing.plate(p.x-20,p.y-15,40,30,5,ink(FIELD,0),ink(FOCUS,245));
    }

    private Color ink(Color color,int alpha){return TripadTheme.alpha(color,Math.round(alpha*opacity));}
    private static float unit(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static float signed(float value){return Float.isFinite(value)?Math.max(-1,Math.min(1,value)):0;}
    private static boolean references(String binding,String control) {
        if(binding==null)return false;
        for(String part:binding.split("\\+"))if(part.trim().equals(control))return true;
        return false;
    }
    private static void color(Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void disc(float x,float y,float radius,Color color) {
        color(color);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y);
        for(int i=0;i<=40;i++){double a=i*Math.PI/20;GL11.glVertex2f(x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius);}GL11.glEnd();
    }
    private static void polygon(float[] points,Color fill,Color edge) {
        // These silhouettes are star-shaped around this center, including the gamepad's inward grip seam.
        color(fill);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(260,110);
        for(int i=0;i<=points.length;i+=2){int j=i%points.length;GL11.glVertex2f(points[j],points[j+1]);}GL11.glEnd();
        color(edge);GL11.glLineWidth(1.5f);GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<points.length;i+=2)GL11.glVertex2f(points[i],points[i+1]);GL11.glEnd();
    }
    private static Map<String,Point> layout(boolean handheld,boolean deck) {
        Map<String,Point> p=new LinkedHashMap<>();
        // Deck's sticks sit inward of the D-pad and ABXY clusters; Ally retains its asymmetric layout.
        float left=handheld?(deck?111:57):163,right=handheld?(deck?409:410):309;
        p.put("LEFT_STICK",new Point(left,123));p.put("L3",p.get("LEFT_STICK"));
        p.put("RIGHT_STICK",new Point(right,handheld&&deck?123:80));p.put("R3",p.get("RIGHT_STICK"));
        float fx=handheld?466:355,fy=handheld?123:123;
        p.put("A",new Point(fx,fy-21));p.put("B",new Point(fx+21,fy));
        p.put("X",new Point(fx-21,fy));p.put("Y",new Point(fx,fy+21));
        float px=handheld?(deck?54:100):215,py=handheld?(deck?123:72):83;
        p.put("DPAD_UP",new Point(px,py+15));p.put("DPAD_DOWN",new Point(px,py-15));
        p.put("DPAD_LEFT",new Point(px-15,py));p.put("DPAD_RIGHT",new Point(px+15,py));
        p.put("VIEW",new Point(handheld?119:239,handheld?153:123));
        p.put("MENU",new Point(handheld?400:281,handheld?153:123));
        p.put("LT",new Point(handheld?39:151,182));p.put("RT",new Point(handheld?481:369,182));
        p.put("LB",new Point(handheld?97:208,181));p.put("RB",new Point(handheld?423:312,181));
        return Map.copyOf(p);
    }
}
