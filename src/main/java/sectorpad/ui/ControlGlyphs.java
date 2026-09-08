package sectorpad.ui;

import java.awt.Color;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import sectorpad.core.DeviceVisual;
import static sectorpad.ui.TripadTheme.*;

/** Licensed controller prompt artwork with original vector fallbacks. Restores host OpenGL state. */
public final class ControlGlyphs {
    private static LazyFont.DrawableString label;
    private static boolean fontAttempted;
    private ControlGlyphs() { }

    public static float width(String control,DeviceVisual visual,float height) {
        if(!PromptRenderer.recognized(control)||!Float.isFinite(height)||height<=0)return 0;
        return height*artworkAspect(control,visual);
    }

    public static void draw(String control,DeviceVisual visual,float cx,float cy,float height,boolean active,float opacity) {
        if(!PromptRenderer.recognized(control)||!Float.isFinite(cx)||!Float.isFinite(cy)||!Float.isFinite(height)
                ||height<=0||!Float.isFinite(opacity)||opacity<=0)return;
        if(visual==null)visual=DeviceVisual.GENERIC;
        opacity=Math.min(1,opacity);
        Color artworkInk=active?FOCUS:INK;
        if(visual==DeviceVisual.XBOX&&!active)artworkInk=switch(control){
            case "A"->new Color(0xa9d98a);case "B"->new Color(0xf28d89);
            case "X"->new Color(0x83b8ed);case "Y"->new Color(0xedd181);default->INK;
        };
        if(UiArtwork.draw(artworkPath(control,visual),cx,cy,width(control,visual,height),height,fade(artworkInk,opacity)))return;
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float h=height,w=width(control,visual,h),r=h*.45f,t=Math.max(1,h*.065f);
            Color edge=fade(active?FOCUS:CYAN,opacity),fill=fade(active?SELECTED:FIELD,opacity),ink=fade(active?FOCUS:INK,opacity);
            if(control.equals("A")||control.equals("B")||control.equals("X")||control.equals("Y")) {
                if(visual==DeviceVisual.XBOX&&!active)ink=fade(switch(control){
                    case "A"->new Color(0xa9d98a);case "B"->new Color(0xf28d89);
                    case "X"->new Color(0x83b8ed);default->new Color(0xedd181);
                },opacity);
                disc(cx,cy,r,fill);TripadDrawing.orbit(cx,cy,r,active?edge:fade(STEEL,opacity),t);
                if(visual==DeviceVisual.GENERIC){
                    for(String direction:new String[]{"A","B","X","Y"}){
                        float dx=direction.equals("B")?1:direction.equals("X")?-1:0;
                        float dy=direction.equals("Y")?1:direction.equals("A")?-1:0;
                        disc(cx+dx*h*.22f,cy+dy*h*.22f,h*.08f,direction.equals(control)?ink:fade(STEEL,opacity));
                    }
                }else text(control,cx,cy,h*.76f,ink);
            } else if(control.equals("LB")||control.equals("RB")||control.equals("LT")||control.equals("RT")) {
                boolean trigger=control.endsWith("T");
                TripadDrawing.plate(cx-w*.48f,cy-h*.43f,w*.96f,h*.86f,trigger?h*.22f:h*.13f,fill,edge);
                if(trigger)TripadDrawing.line(cx-w*.27f,cy+h*.31f,cx+w*.22f,cy+h*.31f,fade(active?FOCUS:STEEL,opacity),t);
                text(visual.label(control),cx,cy-(trigger?h*.035f:0),h*.61f,ink);
            } else if(control.equals("MENU")||control.equals("VIEW")) {
                disc(cx,cy,r,fill);TripadDrawing.orbit(cx,cy,r,edge,t);
                if(control.equals("MENU"))for(int row=-1;row<=1;row++)
                    TripadDrawing.line(cx-h*.2f,cy+row*h*.14f,cx+h*.2f,cy+row*h*.14f,ink,t);
                else {
                    box(cx-h*.23f,cy-h*.03f,h*.31f,h*.25f,ink,t);
                    TripadDrawing.rect(cx-h*.06f,cy-h*.19f,h*.32f,h*.27f,fill);
                    box(cx-h*.06f,cy-h*.19f,h*.32f,h*.27f,ink,t);
                }
            } else if(control.equals("LEFT_STICK")||control.equals("RIGHT_STICK")||control.equals("L3")||control.equals("R3")) {
                boolean press=control.endsWith("3"),left=control.startsWith("L");
                disc(cx,cy,r,fill);TripadDrawing.orbit(cx,cy,r,edge,t);
                if(press)TripadDrawing.orbit(cx,cy,r*.78f,fade(active?FOCUS:STEEL,opacity),Math.max(1,t*.7f));
                text(left?"L":"R",cx,cy+(press?h*.035f:0),h*.68f,ink);
                if(press){
                    TripadDrawing.line(cx-h*.12f,cy-h*.25f,cx,cy-h*.33f,ink,t);
                    TripadDrawing.line(cx,cy-h*.33f,cx+h*.12f,cy-h*.25f,ink,t);
                }
            } else if(control.startsWith("DPAD")) {
                float a=h*.15f,b=h*.43f;
                float[] p={cx-a,cy+b,cx+a,cy+b,cx+a,cy+a,cx+b,cy+a,cx+b,cy-a,cx+a,cy-a,
                    cx+a,cy-b,cx-a,cy-b,cx-a,cy-a,cx-b,cy-a,cx-b,cy+a,cx-a,cy+a};
                TripadDrawing.rect(cx-a,cy-b,2*a,2*b,fill);TripadDrawing.rect(cx-b,cy-a,2*b,2*a,fill);
                polygon(p,edge,true,t);
                int dx=control.endsWith("LEFT")?-1:control.endsWith("RIGHT")?1:0;
                int dy=control.endsWith("DOWN")?-1:control.endsWith("UP")?1:0;
                if(dx!=0||dy!=0){
                    float tx=cx+dx*h*.34f,ty=cy+dy*h*.34f,bx=cx+dx*h*.18f,by=cy+dy*h*.18f;
                    polygon(new float[]{tx,ty,bx-dy*h*.085f,by+dx*h*.085f,bx+dy*h*.085f,by-dx*h*.085f},ink,false,t);
                }else disc(cx,cy,h*.07f,ink);
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }
    // BEGIN GENERATED ARTWORK MAPPING
    public static String artworkPath(String control,DeviceVisual visual) {
        if(!PromptRenderer.recognized(control))return null;
        if(visual==null)visual=DeviceVisual.GENERIC;
        String family=switch(visual){case XBOX->"xbox";case STEAM_DECK->"steam-deck";case ROG_ALLY->"rog-ally";default->"generic";};
        return "graphics/sectorpad/controls/glyphs/"+family+"/"+control.toLowerCase(java.util.Locale.ROOT)+".png";
    }
    private static float artworkAspect(String control,DeviceVisual visual) {
        if(visual==null)visual=DeviceVisual.GENERIC;
        return switch(visual){
            case XBOX -> switch(control){
                case "A" -> 180f/180f;
                case "B" -> 180f/180f;
                case "X" -> 180f/180f;
                case "Y" -> 180f/180f;
                case "LB" -> 220f/132f;
                case "RB" -> 220f/132f;
                case "LT" -> 172f/178f;
                case "RT" -> 172f/178f;
                case "L3" -> 211f/165f;
                case "R3" -> 211f/165f;
                case "MENU" -> 180f/180f;
                case "VIEW" -> 180f/180f;
                case "LEFT_STICK" -> 188f/188f;
                case "RIGHT_STICK" -> 188f/188f;
                case "DPAD" -> 186f/186f;
                case "DPAD_UP" -> 186f/186f;
                case "DPAD_DOWN" -> 186f/186f;
                case "DPAD_LEFT" -> 186f/186f;
                case "DPAD_RIGHT" -> 186f/186f;
                default -> 1f;
            };
            case STEAM_DECK -> switch(control){
                case "A" -> 98f/98f;
                case "B" -> 98f/98f;
                case "X" -> 98f/98f;
                case "Y" -> 98f/98f;
                case "LB" -> 98f/98f;
                case "RB" -> 98f/98f;
                case "LT" -> 98f/98f;
                case "RT" -> 98f/98f;
                case "L3" -> 98f/106f;
                case "R3" -> 98f/106f;
                case "MENU" -> 82f/42f;
                case "VIEW" -> 82f/42f;
                case "LEFT_STICK" -> 98f/98f;
                case "RIGHT_STICK" -> 98f/98f;
                case "DPAD" -> 98f/98f;
                case "DPAD_UP" -> 98f/98f;
                case "DPAD_DOWN" -> 98f/98f;
                case "DPAD_LEFT" -> 98f/98f;
                case "DPAD_RIGHT" -> 98f/98f;
                default -> 1f;
            };
            case ROG_ALLY -> switch(control){
                case "A" -> 98f/98f;
                case "B" -> 98f/98f;
                case "X" -> 98f/98f;
                case "Y" -> 98f/98f;
                case "LB" -> 98f/66f;
                case "RB" -> 98f/66f;
                case "LT" -> 98f/90f;
                case "RT" -> 98f/90f;
                case "L3" -> 98f/106f;
                case "R3" -> 98f/106f;
                case "MENU" -> 98f/98f;
                case "VIEW" -> 98f/98f;
                case "LEFT_STICK" -> 98f/98f;
                case "RIGHT_STICK" -> 98f/98f;
                case "DPAD" -> 98f/98f;
                case "DPAD_UP" -> 98f/98f;
                case "DPAD_DOWN" -> 98f/98f;
                case "DPAD_LEFT" -> 98f/98f;
                case "DPAD_RIGHT" -> 98f/98f;
                default -> 1f;
            };
            case GENERIC -> switch(control){
                case "A" -> 98f/98f;
                case "B" -> 98f/98f;
                case "X" -> 98f/98f;
                case "Y" -> 98f/98f;
                case "LB" -> 98f/66f;
                case "RB" -> 98f/66f;
                case "LT" -> 98f/90f;
                case "RT" -> 98f/90f;
                case "L3" -> 98f/106f;
                case "R3" -> 98f/106f;
                case "MENU" -> 98f/98f;
                case "VIEW" -> 98f/98f;
                case "LEFT_STICK" -> 98f/98f;
                case "RIGHT_STICK" -> 98f/98f;
                case "DPAD" -> 98f/98f;
                case "DPAD_UP" -> 98f/98f;
                case "DPAD_DOWN" -> 98f/98f;
                case "DPAD_LEFT" -> 98f/98f;
                case "DPAD_RIGHT" -> 98f/98f;
                default -> 1f;
            };
        };
    }
    // END GENERATED ARTWORK MAPPING
    private static Color fade(Color c,float opacity){return alpha(c,Math.round(c.getAlpha()*opacity));}
    private static void color(Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void disc(float x,float y,float radius,Color c){
        color(c);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(x,y);
        for(int i=0;i<=48;i++){double a=i*Math.PI/24;GL11.glVertex2f(x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius);}GL11.glEnd();
    }
    private static void polygon(float[] xy,Color c,boolean outline,float stroke){
        color(c);GL11.glLineWidth(stroke);GL11.glBegin(outline?GL11.GL_LINE_LOOP:GL11.GL_POLYGON);
        for(int i=0;i<xy.length;i+=2)GL11.glVertex2f(xy[i],xy[i+1]);GL11.glEnd();
    }
    private static void box(float x,float y,float w,float h,Color c,float t){
        polygon(new float[]{x,y,x+w,y,x+w,y+h,x,y+h},c,true,t);
    }
    private static void text(String value,float cx,float cy,float size,Color ink){
        if(!fontAttempted){fontAttempted=true;try{label=LazyFont.loadFont("graphics/fonts/insignia17LTaa.fnt").createText();}catch(Exception ignored){}}
        if(label==null)return;
        label.setFontSize(size);label.setMaxWidth(1000);label.setMaxHeight(1000);label.setBaseColor(ink);label.setText(value);
        label.draw(cx-label.getWidth()/2,cy+label.getHeight()/2);
    }
}
