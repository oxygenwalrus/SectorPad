package sectorpad.ui;

import java.util.ArrayList;
import java.util.List;

/** Logical-screen geometry shared by drawing, hit tests and dock clearance. No GL access. */
public final class OverlayLayout {
    private OverlayLayout() { }
    public record Rect(float x,float y,float width,float height) {
        public float right(){return x+width;}
        public float top(){return y+height;}
        public boolean contains(float px,float py){
            return valid()&&Float.isFinite(px)&&Float.isFinite(py)&&px>=x&&px<=right()&&py>=y&&py<=top();
        }
        public boolean valid(){return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(width)&&Float.isFinite(height)&&width>0&&height>0;}
    }
    public record Key(Rect bounds,int row,int column) { }
    public record Keyboard(Rect panel,Rect field,float scale,float titleY,float footerY,List<Key> keys) {
        public Key keyAt(float x,float y){for(Key key:keys)if(key.bounds().contains(x,y))return key;return null;}
    }
    public record Wheel(float x,float y,float radius,float inner,float scale,float titleY,float pageY,float descriptionY,float footerY) {
        public boolean contains(float px,float py){return Float.isFinite(px)&&Float.isFinite(py)&&Math.hypot(px-x,py-y)<=radius;}
    }
    public record AimPosition(float x,float y,boolean offscreen,float directionX,float directionY) { }

    public static boolean validScreen(float width,float height){return Float.isFinite(width)&&Float.isFinite(height)&&width>0&&height>0;}
    private static float margin(float width,float height,float scale){return Math.min(16*TripadTheme.requestedScale(scale),Math.min(width,height)*.04f);}
    private static float fit(float width,float height,float requested,float baseWidth,float baseHeight) {
        if(!validScreen(width,height))return 0;
        float m=margin(width,height,requested);
        return Math.max(0,Math.min(TripadTheme.requestedScale(requested),Math.min((width-2*m)/baseWidth,(height-2*m)/baseHeight)));
    }
    private static float keyboardScale(float width,float height,float requested,float baseWidth,float baseHeight,boolean docked) {
        float s=fit(width,height,requested,baseWidth,baseHeight);
        if(docked&&validScreen(width,height))s=Math.min(s,Math.max(0,(height-margin(width,height,requested)-Math.min(170,height*.35f))/baseHeight));
        return s;
    }

    public static Keyboard keyboard(float width,float height,float requested,String[] rows,boolean docked) {
        if(rows==null||rows.length==0||!validScreen(width,height))return new Keyboard(new Rect(0,0,0,0),new Rect(0,0,0,0),0,0,0,List.of());
        int columns=0;for(String row:rows)columns=Math.max(columns,row==null?0:row.length());
        if(columns==0)return new Keyboard(new Rect(0,0,0,0),new Rect(0,0,0,0),0,0,0,List.of());
        float nominalWidth=columns>5?968:620;
        float nominalHeight=160+(rows.length+1)*50;
        float s=keyboardScale(width,height,requested,nominalWidth,nominalHeight,docked);
        float w=nominalWidth*s,h=nominalHeight*s,x=(width-w)/2,y=docked?margin(width,height,requested):(height-h)/2;
        Rect panel=new Rect(x,y,w,h);
        Rect field=new Rect(x+20*s,panel.top()-94*s,w-40*s,40*s);
        List<Key> keys=new ArrayList<>();
        float gridTop=panel.top()-110*s;
        for(int row=0;row<=rows.length;row++){
            int count=row==rows.length?5:rows[row]==null?0:rows[row].length();
            if(count==0)continue;
            float pitch=(w-40*s)/count;
            for(int column=0;column<count;column++){
                Rect bounds=new Rect(x+20*s+column*pitch+2*s,gridTop-(row+1)*50*s+2*s,pitch-4*s,46*s);
                keys.add(new Key(bounds,row,column));
            }
        }
        return new Keyboard(panel,field,s,panel.top()-17*s,y+38*s,List.copyOf(keys));
    }

    /** Docked height of the built-in letter (5 rows) or numeric (4 rows) keyboard. */
    public static float keyboardHeight(float width,float height,float requested,int characterRows) {
        if(characterRows<1||!validScreen(width,height))return 0;
        float nominalHeight=160+(characterRows+1)*50;
        return nominalHeight*keyboardScale(width,height,requested,characterRows==4?620:968,nominalHeight,true);
    }
    /** Clearance from the lower screen edge, including the keyboard's bottom margin. */
    public static float dockedKeyboardTop(float width,float height,float requested,int characterRows) {
        if(!validScreen(width,height)||characterRows<1)return 0;
        return margin(width,height,requested)+keyboardHeight(width,height,requested,characterRows);
    }

    public static Wheel wheel(float width,float height,float requested) {
        float s=fit(width,height,requested,1000,680),r=220*s,x=width/2,y=height/2+15*s;
        return new Wheel(x,y,r,r*.42f,s,y+r+65*s,y+r+32*s,y-r-22*s,y-r-76*s);
    }
    public static Rect panel(float width,float height,float requested,float baseWidth,float baseHeight) {
        float s=fit(width,height,requested,baseWidth,baseHeight);
        float w=baseWidth*s,h=baseHeight*s;
        return new Rect((width-w)/2,(height-h)/2,w,h);
    }
    public static Rect clip(Rect rect,Rect clip) {
        if(rect==null||clip==null||!rect.valid()||!clip.valid())return null;
        float x=Math.max(rect.x(),clip.x()),y=Math.max(rect.y(),clip.y());
        float right=Math.min(rect.right(),clip.right()),top=Math.min(rect.top(),clip.top());
        return right>x&&top>y?new Rect(x,y,right-x,top-y):null;
    }
    public static AimPosition aim(float width,float height,float requested,float x,float y) {
        if(!validScreen(width,height)||!Float.isFinite(x)||!Float.isFinite(y))return null;
        if(x>=0&&x<=width&&y>=0&&y<=height)return new AimPosition(x,y,false,0,0);
        double cx=width/2d,cy=height/2d,dx=x-cx,dy=y-cy,length=Math.hypot(dx,dy);
        float pad=Math.min(22*TripadTheme.requestedScale(requested),Math.min(width,height)*.15f);
        double factor=Math.min(Math.abs(dx)>0?(cx-pad)/Math.abs(dx):Double.POSITIVE_INFINITY,
                               Math.abs(dy)>0?(cy-pad)/Math.abs(dy):Double.POSITIVE_INFINITY);
        return new AimPosition((float)(cx+dx*factor),(float)(cy+dy*factor),true,(float)(dx/length),(float)(dy/length));
    }
}
