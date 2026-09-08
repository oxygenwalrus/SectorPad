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
            case 0 -> {nextRows.add(new Row("choose-ships","Choose ship...","Browse ships in your fleet","choose-ships",true));for(var i:next.mounts())nextRows.add(item(i));add(nextRows,"officer");add(nextRows,"name");}
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
        case "vents+","vents-" -> "Adjust flux vents by one point. Available ordnance and fitting limits apply.";
        case "caps+","caps-" -> "Adjust flux capacitors by one point. Available ordnance and fitting limits apply.";
        case "simulation" -> "Test this fitting in combat simulation. Return here when the simulation ends.";
        case "save" -> "Save the current fitting.";
        case "undo" -> "Revert changes to this fitting using the game's undo action.";
        case "strip" -> "Remove equipment using the game's strip fitting action.";
        case "restore" -> "Restore the ship using the game's restoration options and costs.";
        case "additional" -> "Browse additional refit options, including actions supplied by other mods.";
        case "hullmods","smods" -> "Browse hull modifications and their fitting requirements.";
        case "groups" -> "Arrange weapon groups and configure their firing behavior.";
        case "autofit" -> "Choose a loadout and review the available equipment before fitting.";
        case "officer" -> "Choose the officer commanding this ship.";
        case "name","variant-name" -> "Edit the name in the game's naming dialog.";
        default -> "Open the game's fitting options. Finish or cancel to return here.";
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
            TripadDrawing.glyph("refit",x+39*s,y+h-46*s,17*s,CYAN);
            text(snapshot.shipName(),x+72*s,y+h-23*s,28*s,INK,w-395*s,36*s);
            text(snapshot.hullName()+"  /  Refit",x+73*s,y+h-60*s,15*s,MUTED,w-395*s,21*s);
            float opX=x+w-266*s;
            text("Ordnance available",opX,y+h-15*s,14*s,MUTED,242*s,18*s);
            text(Integer.toString(snapshot.unusedOP()),opX,y+h-32*s,31*s,snapshot.unusedOP()<0?FOCUS:CYAN,86*s,38*s);
            text("/ "+snapshot.totalOP()+" OP",opX+91*s,y+h-44*s,17*s,INK,150*s,24*s);
            float remaining=Math.max(0,Math.min(1,snapshot.unusedOP()/(float)Math.max(1,snapshot.totalOP())));
            rect(opX,y+h-79*s,242*s,4*s,KEY);
            rect(opX,y+h-79*s,242*s*remaining,4*s,snapshot.unusedOP()<0?FOCUS:CYAN);
            for(int i=1;i<10;i++)rect(opX+242*s*i/10,y+h-79*s,2*s,4*s,PANEL);
            float tabY=y+h-124*s,tabW=(w-32*s)/5;
            TripadDrawing.line(x+16*s,tabY,x+w-16*s,tabY,alpha(STEEL,150),1);
            for(int i=0;i<5;i++){
                float tx=x+16*s+i*tabW;boolean active=i==section&&!ships;
                if(active){TripadDrawing.plate(tx,tabY,tabW-5*s,34*s,6*s,SELECTED,null);rect(tx+10*s,tabY,tabW-25*s,2*s,CYAN);}
                text(SECTIONS.get(i),tx+12*s,tabY+26*s,16*s,active?INK:MUTED,tabW-24*s,24*s);
                hits.add(new Hit(new OverlayLayout.Rect(tx,tabY,tabW-5*s,34*s),"tab:"+i));
            }
            float bodyY=y+90*s,bodyH=tabY-bodyY-16*s,leftW=w*.32f;
            button("ships",ships?"Back to fitting":"Browse fleet",x+16*s,bodyY+bodyH-34*s,leftW-24*s,32*s,KEY,s);
            int start=(selected/pageSize)*pageSize;float rowTop=bodyY+bodyH-44*s;
            for(int i=start;i<Math.min(rows.size(),start+pageSize);i++){
                Row row=rows.get(i);float ry=rowTop-(i-start+1)*54*s;
                if(i==selected){TripadDrawing.plate(x+16*s,ry,leftW-24*s,49*s,7*s,SELECTED,alpha(FOCUS,105));rect(x+16*s,ry+8*s,3*s,33*s,FOCUS);}
                else TripadDrawing.line(x+30*s,ry,x+leftW-12*s,ry,alpha(STEEL,70),1);
                text(row.label(),x+30*s,ry+42*s,18*s,row.enabled()?INK:MUTED,leftW-76*s,25*s);
                text(row.enabled()?shortDetail(row.detail()):"Unavailable",x+30*s,ry+19*s,13*s,MUTED,leftW-67*s,17*s);
                if(i==selected&&row.enabled()){
                    float ax=x+leftW-26*s;TripadDrawing.line(ax-3*s,ry+30*s,ax+2*s,ry+25*s,FOCUS,1.5f*s);TripadDrawing.line(ax+2*s,ry+25*s,ax-3*s,ry+20*s,FOCUS,1.5f*s);
                }
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
        text(ships?"Fleet selection":SECTIONS.get(section),x,top,14*s,CYAN,w,20*s);
        text(row==null?SECTIONS.get(section):row.label(),x,top-23*s,24*s,INK,w,58*s);
        TripadDrawing.line(x,top-85*s,x+w,top-85*s,alpha(STEEL,130),1);
        text(row==null?"":row.detail(),x,top-101*s,16*s,MUTED,w,64*s);
        if(ships){
            TripadDrawing.glyph("ship",x+26*s,top-202*s,24*s,CYAN);
            text("Select a ship to open its fitting.",x+68*s,top-186*s,20*s,INK,w-68*s,55*s);
            text("Equipment and ship statistics update after selection.",x+68*s,top-241*s,17*s,MUTED,w-68*s,70*s);
            return;
        }
        float sy=top-181*s,half=(w-10*s)/2;
        text("Flux vents",x,sy-5*s,14*s,MUTED,half-10*s,20*s);
        text(Integer.toString(snapshot.vents()),x,sy-24*s,27*s,CYAN,half-10*s,34*s);
        TripadDrawing.line(x+half,sy-51*s,x+half,sy-4*s,alpha(STEEL,130),1);
        text("Capacitors",x+half+18*s,sy-5*s,14*s,MUTED,half-18*s,20*s);
        text(Integer.toString(snapshot.capacitors()),x+half+18*s,sy-24*s,27*s,CYAN,half-18*s,34*s);
        float statY=sy-75*s;
        for(String stat:snapshot.stats()){
            if(statY<y+61*s)break;
            TripadDrawing.line(x,statY-23*s,x+w,statY-23*s,alpha(STEEL,55),1);
            int split=stat.indexOf(':');
            if(split>0&&!stat.contains("   ")){
                float labelW=Math.min(w*.45f,143*s);
                text(stat.substring(0,split),x,statY,14*s,MUTED,labelW-7*s,22*s);
                text(stat.substring(split+1).trim(),x+labelW,statY,16*s,INK,w-labelW,23*s);
            }else text(stat,x,statY,15*s,INK,w,23*s);
            statY-=28*s;
        }
        button("details",details?"Close details":"Expand details",x,y+8*s,Math.min(w,180*s),30*s,KEY,s);
    }
    private void drawShip(float x,float y,float w,float h,float s){
        if(!spritePath.equals(snapshot.sprite())){spritePath=snapshot.sprite();try{sprite=Global.getSettings().getSprite(spritePath);spriteWidth=sprite.getWidth();spriteHeight=sprite.getHeight();}catch(RuntimeException failure){sprite=null;}}
        float spanX=Math.max(1,spriteWidth),spanY=Math.max(1,spriteHeight);
        for(var item:snapshot.mounts())if(item.mount()){spanX=Math.max(spanX,Math.abs(item.y())*2+20);spanY=Math.max(spanY,Math.abs(item.x())*2+20);}
        float plotY=y+35*s,plotH=Math.max(1,h-85*s);
        float scale=Math.min((w-40*s)/spanX,(plotH-20*s)/spanY),cx=x+w/2,cy=plotY+plotH/2;
        TripadDrawing.plate(x,y,w,h,10*s,FIELD,alpha(STEEL,110));
        text("Ship schematic",x+15*s,y+h-13*s,14*s,CYAN,w-30*s,20*s);
        TripadDrawing.line(x+15*s,y+h-39*s,x+w-15*s,y+h-39*s,alpha(STEEL,120),1);
        for(int i=1;i<6;i++){
            float gx=x+w*i/6,gy=plotY+plotH*i/6;
            TripadDrawing.line(gx,plotY,gx,plotY+plotH,alpha(STEEL,24),1);
            TripadDrawing.line(x+15*s,gy,x+w-15*s,gy,alpha(STEEL,24),1);
        }
        float radius=Math.min(w-30*s,plotH)*.43f;
        TripadDrawing.orbit(cx,cy,radius,alpha(STEEL,75),1);
        TripadDrawing.line(cx,plotY,cx,plotY+plotH,alpha(STEEL,70),1);
        TripadDrawing.line(x+15*s,cy,x+w-15*s,cy,alpha(STEEL,70),1);
        for(int edge:new int[]{-1,1}){
            float ex=cx+edge*(w/2-15*s);
            TripadDrawing.line(ex,cy-18*s,ex,cy+18*s,alpha(CYAN,100),1);
            TripadDrawing.line(ex,cy,ex-edge*8*s,cy,alpha(CYAN,100),1);
        }
        if(sprite!=null){float oldW=sprite.getWidth(),oldH=sprite.getHeight(),oldAngle=sprite.getAngle(),oldAlpha=sprite.getAlphaMult();
            try{sprite.setSize(spriteWidth*scale,spriteHeight*scale);sprite.setAngle(0);sprite.setAlphaMult(.9f);sprite.renderAtCenter(cx,cy);}
            finally{sprite.setSize(oldW,oldH);sprite.setAngle(oldAngle);sprite.setAlphaMult(oldAlpha);}}
        int mountCount=0;
        for(var item:snapshot.mounts())if(item.mount()){
            mountCount++;
            float mx=cx-item.y()*scale,my=cy+item.x()*scale;boolean focus=item.id().equals(selectedId);
            if(focus){
                TripadDrawing.orbit(mx,my,17*s,alpha(FOCUS,45),3*s);
                TripadDrawing.orbit(mx,my,13*s,alpha(FOCUS,180),1);
                TripadDrawing.line(mx-21*s,my,mx-16*s,my,FOCUS,1);TripadDrawing.line(mx+16*s,my,mx+21*s,my,FOCUS,1);
            }
            TripadDrawing.plate(mx-6*s,my-6*s,12*s,12*s,3*s,focus?FOCUS:FIELD,focus?FOCUS:CYAN);
            hits.add(new Hit(new OverlayLayout.Rect(mx-10*s,my-10*s,20*s,20*s),item.id()));
        }
        TripadDrawing.line(x+15*s,y+30*s,x+w-15*s,y+30*s,alpha(STEEL,90),1);
        text(mountCount+" mounts  /  select to configure",x+15*s,y+21*s,12*s,MUTED,w-30*s,18*s);
    }
    private void button(String id,String label,float x,float y,float w,float h,Color color,float s){TripadDrawing.control(x,y,w,h,s,color==SELECTED);text(label,x+12*s,y+h-7*s,16*s,INK,w-24*s,h-4*s);hits.add(new Hit(new OverlayLayout.Rect(x,y,w,h),id));}
    private void text(String value,float x,float y,float size,Color c,float w,float h){if(value==null||value.isEmpty()||w<=0||h<=0)return;if(textIndex==labels.size())labels.add(font.createText());var t=labels.get(textIndex++);t.setFontSize(size);t.setBaseColor(c);t.setMaxWidth(w);t.setMaxHeight(h);t.setText(value);t.draw(x,y);}
    private static void color(Color c){GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f);}
    private static void rect(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
    private static void border(float x,float y,float w,float h,Color c){GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glLineWidth(1.5f);GL11.glBegin(GL11.GL_LINE_LOOP);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();}
}
