package sectorpad.refit;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import sectorpad.ui.OverlayLayout;
import sectorpad.ui.TripadDrawing;
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
            rect(0,0,width,height,DIM);TripadDrawing.frame(x,y,w,h,s);
            TripadDrawing.glyph("refit",x+39*s,y+h-42*s,17*s,CYAN);
            text(snapshot.shipName(),x+72*s,y+h-17*s,28*s,INK,w-395*s,38*s);
            text(snapshot.hullName()+"  /  Refit",x+73*s,y+h-53*s,16*s,MUTED,w-395*s,24*s);
            float opX=x+w-266*s;
            text(Integer.toString(snapshot.unusedOP()),opX,y+h-18*s,32*s,snapshot.unusedOP()<0?FOCUS:INK,78*s,40*s);
            text("OP available",opX+83*s,y+h-20*s,16*s,MUTED,162*s,23*s);
            text("of "+snapshot.totalOP()+" total",opX+83*s,y+h-43*s,14*s,MUTED,162*s,21*s);
            rect(opX,y+h-72*s,242*s,3*s,KEY);
            rect(opX,y+h-72*s,242*s*Math.max(0,Math.min(1,snapshot.unusedOP()/(float)Math.max(1,snapshot.totalOP()))),3*s,FOCUS);
            float tabY=y+h-116*s,tabW=(w-32*s)/5;
            for(int i=0;i<5;i++){
                float tx=x+16*s+i*tabW;boolean active=i==section&&!ships;
                rect(tx,tabY,tabW-5*s,32*s,active?SELECTED:FIELD);
                if(active)rect(tx,tabY,tabW-5*s,2*s,CYAN);
                text(SECTIONS.get(i),tx+12*s,tabY+25*s,16*s,active?INK:MUTED,tabW-25*s,24*s);
                hits.add(new Hit(new OverlayLayout.Rect(tx,tabY,tabW-5*s,32*s),"tab:"+i));
            }
            float bodyY=y+90*s,bodyH=tabY-bodyY-16*s,leftW=w*.32f;
            button("ships",ships?"Back to fitting":"Browse fleet",x+16*s,bodyY+bodyH-34*s,leftW-24*s,32*s,KEY,s);
            int start=(selected/pageSize)*pageSize;float rowTop=bodyY+bodyH-44*s;
            for(int i=start;i<Math.min(rows.size(),start+pageSize);i++){
                Row row=rows.get(i);float ry=rowTop-(i-start+1)*54*s;
                rect(x+16*s,ry,leftW-24*s,49*s,i==selected?SELECTED:FIELD);
                if(i==selected){rect(x+16*s,ry+5*s,3*s,39*s,FOCUS);TripadDrawing.line(x+22*s,ry,leftW+x-8*s,ry,alpha(FOCUS,90),1);}
                text(row.label(),x+30*s,ry+42*s,18*s,i==selected?INK:row.enabled()?INK:MUTED,leftW-54*s,25*s);
                text(row.enabled()?shortDetail(row.detail()):"Inspect only",x+30*s,ry+19*s,13*s,MUTED,leftW-54*s,17*s);
                hits.add(new Hit(new OverlayLayout.Rect(x+16*s,ry,leftW-24*s,49*s),row.id()));
            }
            if(rows.isEmpty())text("No items in this section",x+26*s,rowTop-25*s,19*s,MUTED,leftW-46*s,50*s);
            button("prev","Previous",x+16*s,bodyY-24*s,110*s,26*s,KEY,s);
            button("next","Next",x+134*s,bodyY-24*s,85*s,26*s,KEY,s);
            text(rows.isEmpty()?"0 / 0":(selected+1)+" / "+rows.size(),x+235*s,bodyY-5*s,16*s,MUTED,100*s,24*s);
            float rightX=x+leftW+18*s,rightW=w-leftW-42*s;
            TripadDrawing.line(x+leftW+4*s,bodyY,x+leftW+4*s,bodyY+bodyH,alpha(STEEL,130),1);
            if(section==0&&!ships&&!details){
                float diagramW=rightW*.48f;drawShip(rightX,bodyY+28*s,diagramW,bodyH-28*s,s);
                drawDetails(rightX+diagramW+22*s,bodyY,rightW-diagramW-22*s,bodyH,s);
            }else drawDetails(rightX,bodyY,rightW,bodyH,s);
            TripadDrawing.line(x+16*s,y+64*s,x+w-16*s,y+64*s,STEEL,1);
            button("native","Native refit",x+16*s,y+18*s,155*s,32*s,KEY,s);
            button("hub","Command hub",x+181*s,y+18*s,165*s,32*s,KEY,s);
            text(footer,x+362*s,y+44*s,15*s,CYAN,w-382*s,40*s);
        }finally{GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();}
    }
    private String shortDetail(String value){return value.length()>74?value.substring(0,71)+"...":value;}
    private void drawDetails(float x,float y,float w,float h,float s){
        Row row=selected();float top=y+h-8*s;
        text(row==null?SECTIONS.get(section):row.label(),x,top,24*s,INK,w,62*s);
        text(row==null?"":row.detail(),x,top-69*s,17*s,MUTED,w,95*s);
        if(ships){
            TripadDrawing.glyph("ship",x+26*s,top-202*s,24*s,CYAN);
            text("Select a ship to open its fitting.",x+68*s,top-186*s,20*s,INK,w-68*s,55*s);
            text("Equipment and ship statistics update after selection.",x+68*s,top-241*s,17*s,MUTED,w-68*s,70*s);
            return;
        }
        float sy=top-181*s,half=(w-10*s)/2;
        rect(x,sy-52*s,half,56*s,FIELD);rect(x+half+10*s,sy-52*s,half,56*s,FIELD);
        text("Vents",x+10*s,sy-5*s,14*s,MUTED,half-20*s,20*s);
        text(Integer.toString(snapshot.vents()),x+10*s,sy-24*s,23*s,CYAN,half-20*s,30*s);
        text("Capacitors",x+half+20*s,sy-5*s,14*s,MUTED,half-20*s,20*s);
        text(Integer.toString(snapshot.capacitors()),x+half+20*s,sy-24*s,23*s,CYAN,half-20*s,30*s);
        float statY=sy-75*s;
        for(String stat:snapshot.stats()){
            if(statY<y+61*s)break;
            text(stat,x,statY,16*s,INK,w,24*s);statY-=27*s;
        }
        button("details",details?"Close details":"Expand details",x,y+8*s,Math.min(w,180*s),30*s,KEY,s);
    }
    private void drawShip(float x,float y,float w,float h,float s){
        if(!spritePath.equals(snapshot.sprite())){spritePath=snapshot.sprite();try{sprite=Global.getSettings().getSprite(spritePath);spriteWidth=sprite.getWidth();spriteHeight=sprite.getHeight();}catch(RuntimeException failure){sprite=null;}}
        float spanX=Math.max(1,spriteWidth),spanY=Math.max(1,spriteHeight);
        for(var item:snapshot.mounts())if(item.mount()){spanX=Math.max(spanX,Math.abs(item.y())*2+20);spanY=Math.max(spanY,Math.abs(item.x())*2+20);}
        float scale=Math.min((w-28*s)/spanX,(h-28*s)/spanY),cx=x+w/2,cy=y+h/2;
        TripadDrawing.plate(x,y,w,h,10*s,FIELD,alpha(STEEL,90));
        float radius=Math.min(w,h)*.39f;
        TripadDrawing.orbit(cx,cy,radius,alpha(STEEL,100),1);
        TripadDrawing.orbit(cx,cy,radius*.67f,alpha(STEEL,55),1);
        TripadDrawing.line(cx,y+15*s,cx,y+h-15*s,alpha(STEEL,65),1);
        TripadDrawing.line(x+15*s,cy,x+w-15*s,cy,alpha(STEEL,65),1);
        if(sprite!=null){float oldW=sprite.getWidth(),oldH=sprite.getHeight(),oldAngle=sprite.getAngle(),oldAlpha=sprite.getAlphaMult();
            try{sprite.setSize(spriteWidth*scale,spriteHeight*scale);sprite.setAngle(0);sprite.setAlphaMult(.9f);sprite.renderAtCenter(cx,cy);}
            finally{sprite.setSize(oldW,oldH);sprite.setAngle(oldAngle);sprite.setAlphaMult(oldAlpha);}}
        for(var item:snapshot.mounts())if(item.mount()){
            float mx=cx-item.y()*scale,my=cy+item.x()*scale;boolean focus=item.id().equals(selectedId);
            if(focus)TripadDrawing.orbit(mx,my,13*s,alpha(FOCUS,160),1);
            TripadDrawing.plate(mx-6*s,my-6*s,12*s,12*s,3*s,focus?FOCUS:FIELD,focus?FOCUS:CYAN);
            hits.add(new Hit(new OverlayLayout.Rect(mx-10*s,my-10*s,20*s,20*s),item.id()));
        }
    }
    private void button(String id,String label,float x,float y,float w,float h,Color color,float s){TripadDrawing.control(x,y,w,h,s,color==SELECTED);text(label,x+12*s,y+h-7*s,16*s,INK,w-24*s,h-4*s);hits.add(new Hit(new OverlayLayout.Rect(x,y,w,h),id));}
    private void text(String value,float x,float y,float size,Color c,float w,float h){if(value==null||value.isEmpty()||w<=0||h<=0)return;if(textIndex==labels.size())labels.add(font.createText());var t=labels.get(textIndex++);t.setFontSize(size);t.setBaseColor(c);t.setMaxWidth(w);t.setMaxHeight(h);t.setText(value);t.draw(x,y);}
    private static void color(Color c){GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void rect(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
    private static void border(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(1.5f);GL11.glBegin(GL11.GL_LINE_LOOP);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
}
