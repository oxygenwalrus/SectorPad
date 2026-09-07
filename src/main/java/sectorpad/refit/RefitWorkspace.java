package sectorpad.refit;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import sectorpad.ui.OverlayLayout;
import java.awt.Color;
import java.util.*;
import java.util.List;
import static sectorpad.ui.TripadTheme.*;

/** A SectorPad-owned window painted by the existing additive overlay host. */
public final class RefitWorkspace {
    public static final List<String> SECTIONS=List.of("Ship & mounts","Hullmods","Flux & stats","Weapon groups","Designs & actions");
    public record Row(String id,String label,String detail,String action,boolean enabled) { }
    public record Hit(OverlayLayout.Rect bounds,String id) { }
    public record Layout(float x,float y,float width,float height,float scale,int pageSize) { }
    private RefitAdapter.Snapshot snapshot;
    private int section,selected,pageSize=7;
    private String selectedId="",shipId="";
    private Object variantOwner;
    private boolean ships,details;
    private List<Row> rows=List.of();
    private final List<Hit> hits=new ArrayList<>();
    private LazyFont font;
    private final List<LazyFont.DrawableString> labels=new ArrayList<>();
    private int textIndex;
    private String spritePath="";
    private SpriteAPI sprite;
    private float spriteWidth,spriteHeight;
    public int section(){return section;}
    public List<Row> rows(){return rows;}
    public String selectedId(){return selectedId;}
    public Row selected(){return rows.isEmpty()?null:rows.get(Math.min(selected,rows.size()-1));}
    public void update(RefitAdapter.Snapshot next){
        snapshot=next;if(next==null){rows=List.of();hits.clear();return;}
        if(!shipId.equals(next.shipId())||variantOwner!=next.variant()){
            if(ships&&!shipId.equals(next.shipId())){ships=false;section=0;}
            shipId=next.shipId();variantOwner=next.variant();selectedId="";selected=0;
        }
        List<Row> nextRows=new ArrayList<>();
        if(ships)for(var i:next.ships())nextRows.add(item(i));
        else switch(section){
            case 0 -> {nextRows.add(new Row("choose-ships","Choose ship...","Select a ship from the current native fleet list","choose-ships",true));for(var i:next.mounts())nextRows.add(item(i));add(nextRows,"officer");add(nextRows,"name");}
            case 1 -> {add(nextRows,"hullmods");add(nextRows,"smods");for(var i:next.hullmods())nextRows.add(item(i));}
            case 2 -> {add(nextRows,"vents+");add(nextRows,"vents-");add(nextRows,"caps+");add(nextRows,"caps-");}
            case 3 -> {add(nextRows,"groups");for(var i:next.groups())nextRows.add(item(i));}
            default -> {for(String id:List.of("autofit","save","undo","strip","restore","simulation","variant-name","next-op","additional"))add(nextRows,id);}
        }
        rows=List.copyOf(nextRows);int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(selectedId)){index=i;break;}
        selected=index>=0?index:Math.min(selected,Math.max(0,rows.size()-1));remember();
    }
    private Row item(RefitAdapter.Item i){var a=snapshot.action(i.action());return new Row(i.id(),i.label(),i.detail()+(a!=null&&!a.enabled()?"\n"+a.reason():""),i.action(),a!=null&&a.enabled());}
    private void add(List<Row> list,String id){var a=snapshot.action(id);if(a!=null)list.add(new Row(id,a.label(),a.enabled()?description(id):a.reason(),id,a.enabled()));}
    private static String description(String id){return switch(id){
        case "vents+","vents-","caps+","caps-" -> "Adjust one point through the original refit control. The game applies OP limits and fitting rules.";
        case "simulation" -> "Open the original simulator. The workspace returns when you exit simulation.";
        case "save","undo","strip","restore" -> "Use the original refit action. Native costs and confirmations apply.";
        case "additional" -> "Open additional options supplied by other mods in their original interface.";
        default -> "Open the original game controls. Finish or cancel there to return here.";
    };}
    private void remember(){selectedId=rows.isEmpty()?"":rows.get(selected).id();}
    public void move(int delta){if(rows.isEmpty())return;selected=Math.max(0,Math.min(rows.size()-1,selected+delta));remember();details=false;}
    public void section(int delta){section=Math.floorMod(section+delta,SECTIONS.size());ships=false;details=false;selected=0;selectedId="";update(snapshot);}
    public void actions(){section=4;ships=false;details=false;selected=0;selectedId="";update(snapshot);}
    public void chooseShips(){ships=!ships;details=false;selected=0;selectedId="";update(snapshot);}
    public void details(){details=!details;}
    public boolean back(){if(details){details=false;return true;}if(ships){chooseShips();return true;}return false;}
    public String activate(){Row row=selected();return row==null?"":row.enabled()?row.action():"unavailable";}
    public String click(float x,float y){
        for(Hit hit:hits)if(hit.bounds().contains(x,y)){
            String id=hit.id();if(id.startsWith("tab:")){section=Integer.parseInt(id.substring(4));ships=false;selectedId="";selected=0;update(snapshot);return "";}
            if(id.equals("ships")){chooseShips();return "";}if(id.equals("details")){details();return "";}
            if(id.equals("prev")){move(-pageSize);return "";}if(id.equals("next")){move(pageSize);return "";}
            if(id.equals("native")||id.equals("hub"))return id;
            for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(id)){selected=i;remember();return activate();}
        }return "";
    }
    public void point(float x,float y){for(Hit hit:hits)if(hit.bounds().contains(x,y))for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(hit.id())){selected=i;remember();return;}}
    public static Layout layout(float width,float height,float requested){
        float scale=Math.max(.5f,Math.min(Math.min(requested,1.35f),Math.min(width/1280f,height/720f)));
        float w=Math.min(width-24,1460*scale),h=Math.min(height-30,850*scale);
        return new Layout((width-w)/2,(height-h)/2,w,h,scale,Math.max(3,(int)((h-270*scale)/(54*scale))));
    }
    public void render(float width,float height,float requested,String footer){
        if(snapshot==null||width<320||height<240)return;
        if(font==null)try{font=LazyFont.loadFont("graphics/fonts/insignia21LTaa.fnt");}catch(Exception failure){throw new IllegalStateException("Refit font unavailable",failure);}
        Layout l=layout(width,height,requested);float x=l.x(),y=l.y(),w=l.width(),h=l.height(),s=l.scale();pageSize=l.pageSize();hits.clear();textIndex=0;
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,width,0,height,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            rect(0,0,width,height,DIM);rect(x,y,w,h,PANEL);border(x,y,w,h,CYAN);
            text("REFIT / "+snapshot.shipName(),x+20*s,y+h-18*s,24*s,INK,w-340*s,32*s);
            text(snapshot.hullName()+"  ·  "+(ships?"Choose a ship":SECTIONS.get(section)),x+20*s,y+h-50*s,17*s,MUTED,w-340*s,24*s);
            text(snapshot.unusedOP()+" OP remaining / "+snapshot.totalOP(),x+w-300*s,y+h-23*s,21*s,snapshot.unusedOP()<0?Color.ORANGE:FOCUS,285*s,32*s);
            float tabY=y+h-116*s,tabW=(w-32*s)/5;
            for(int i=0;i<5;i++)button("tab:"+i,SECTIONS.get(i),x+16*s+i*tabW,tabY,tabW-5*s,32*s,i==section&&!ships?SELECTED:KEY,s);
            float bodyY=y+90*s,bodyH=tabY-bodyY-16*s,leftW=w*.405f;
            button("ships",ships?"Back to fitting":"Choose ship...",x+16*s,bodyY+bodyH-34*s,leftW-24*s,32*s,KEY,s);
            int start=(selected/pageSize)*pageSize;float rowTop=bodyY+bodyH-44*s;
            for(int i=start;i<Math.min(rows.size(),start+pageSize);i++){
                Row row=rows.get(i);float ry=rowTop-(i-start+1)*54*s;
                rect(x+16*s,ry,leftW-24*s,49*s,i==selected?SELECTED:FIELD);if(i==selected)border(x+16*s,ry,leftW-24*s,49*s,FOCUS);
                text(row.label(),x+26*s,ry+42*s,18*s,row.enabled()?INK:MUTED,leftW-46*s,25*s);
                text(row.enabled()?shortDetail(row.detail()):"Inspect · native action unavailable",x+26*s,ry+19*s,13*s,MUTED,leftW-46*s,17*s);
                hits.add(new Hit(new OverlayLayout.Rect(x+16*s,ry,leftW-24*s,49*s),row.id()));
            }
            if(rows.isEmpty())text("No items in this section",x+26*s,rowTop-25*s,19*s,MUTED,leftW-46*s,50*s);
            button("prev","Previous",x+16*s,bodyY-24*s,110*s,26*s,KEY,s);
            button("next","Next",x+134*s,bodyY-24*s,85*s,26*s,KEY,s);
            text(rows.isEmpty()?"0 / 0":(selected+1)+" / "+rows.size(),x+235*s,bodyY-17*s,16*s,MUTED,100*s,24*s);
            float rightX=x+leftW+12*s,rightW=w-leftW-30*s;
            if(section==0&&!ships&&!details){
                float diagramW=rightW*.52f;drawShip(rightX,bodyY+55*s,diagramW,bodyH-60*s,s);
                drawDetails(rightX+diagramW+14*s,bodyY,rightW-diagramW-14*s,bodyH,s);
            }else drawDetails(rightX,bodyY,rightW,bodyH,s);
            button("native","Native refit",x+16*s,y+18*s,155*s,32*s,KEY,s);
            button("hub","Command hub",x+181*s,y+18*s,165*s,32*s,KEY,s);
            text(footer,x+362*s,y+44*s,15*s,CYAN,w-382*s,40*s);
        }finally{GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();}
    }
    private String shortDetail(String value){return value.length()>74?value.substring(0,71)+"...":value;}
    private void drawDetails(float x,float y,float w,float h,float s){
        Row row=selected();float top=y+h-8*s;
        text(row==null?SECTIONS.get(section):row.label(),x,top,24*s,INK,w,65*s);
        text(row==null?"":row.detail(),x,top-76*s,18*s,MUTED,w,125*s);
        float sy=top-215*s;text("Vents  "+snapshot.vents()+"    Capacitors  "+snapshot.capacitors(),x,sy,18*s,FOCUS,w,50*s);
        int i=0;for(String stat:snapshot.stats())text(stat,x,sy-54*s-i++*27*s,17*s,INK,w,25*s);
        button("details",details?"Close details":"Expand details",x,y+8*s,Math.min(w,180*s),30*s,KEY,s);
    }
    private void drawShip(float x,float y,float w,float h,float s){
        if(!spritePath.equals(snapshot.sprite())){spritePath=snapshot.sprite();try{sprite=Global.getSettings().getSprite(spritePath);spriteWidth=sprite.getWidth();spriteHeight=sprite.getHeight();}catch(RuntimeException failure){sprite=null;}}
        float spanX=Math.max(1,spriteWidth),spanY=Math.max(1,spriteHeight);
        for(var item:snapshot.mounts())if(item.mount()){spanX=Math.max(spanX,Math.abs(item.y())*2+20);spanY=Math.max(spanY,Math.abs(item.x())*2+20);}
        float scale=Math.min((w-28*s)/spanX,(h-28*s)/spanY),cx=x+w/2,cy=y+h/2;
        if(sprite!=null){float oldW=sprite.getWidth(),oldH=sprite.getHeight(),oldAngle=sprite.getAngle(),oldAlpha=sprite.getAlphaMult();
            try{sprite.setSize(spriteWidth*scale,spriteHeight*scale);sprite.setAngle(0);sprite.setAlphaMult(.9f);sprite.renderAtCenter(cx,cy);}
            finally{sprite.setSize(oldW,oldH);sprite.setAngle(oldAngle);sprite.setAlphaMult(oldAlpha);}}
        for(var item:snapshot.mounts())if(item.mount()){
            float mx=cx-item.y()*scale,my=cy+item.x()*scale;boolean focus=item.id().equals(selectedId);
            rect(mx-5*s,my-5*s,10*s,10*s,focus?FOCUS:KEY);border(mx-6*s,my-6*s,12*s,12*s,focus?FOCUS:CYAN);
            hits.add(new Hit(new OverlayLayout.Rect(mx-10*s,my-10*s,20*s,20*s),item.id()));
        }
    }
    private void button(String id,String label,float x,float y,float w,float h,Color color,float s){rect(x,y,w,h,color);text(label,x+9*s,y+h-7*s,16*s,INK,w-18*s,h-4*s);hits.add(new Hit(new OverlayLayout.Rect(x,y,w,h),id));}
    private void text(String value,float x,float y,float size,Color c,float w,float h){if(value==null||value.isEmpty()||w<=0||h<=0)return;if(textIndex==labels.size())labels.add(font.createText());var t=labels.get(textIndex++);t.setFontSize(size);t.setBaseColor(c);t.setMaxWidth(w);t.setMaxHeight(h);t.setText(value);t.draw(x,y);}
    private static void color(Color c){GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void rect(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
    private static void border(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(1.5f);GL11.glBegin(GL11.GL_LINE_LOOP);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
}
