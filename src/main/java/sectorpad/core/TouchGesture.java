package sectorpad.core;

/** Mouse-compatible touch recognition for SectorPad-owned panels. */
public final class TouchGesture {
    public enum Result { NONE, TAP, LONG_PRESS }
    public record Update(Result result, int scrollSteps, float x, float y) { }
    private static final float TAP_SLOP=14f, SCROLL_STEP=34f;
    private static final long LONG_PRESS_NANOS=500_000_000L;
    private boolean down,dragged;
    private float startX,startY,lastY,scrollRemainder;
    private long started;

    public void begin(float x,float y,long now){
        down=true;dragged=false;startX=x;startY=y;lastY=y;scrollRemainder=0;started=now;
    }
    public Update move(float x,float y){
        if(!down)return new Update(Result.NONE,0,x,y);
        if(Math.hypot(x-startX,y-startY)>TAP_SLOP)dragged=true;
        int steps=0;
        if(dragged){
            scrollRemainder+=y-lastY;
            steps=(int)(scrollRemainder/SCROLL_STEP);
            scrollRemainder-=steps*SCROLL_STEP;
        }
        lastY=y;return new Update(Result.NONE,steps,x,y);
    }
    public Update end(float x,float y,long now){
        if(!down)return new Update(Result.NONE,0,x,y);
        Update movement=move(x,y);down=false;
        Result result=dragged?Result.NONE:now-started>=LONG_PRESS_NANOS?Result.LONG_PRESS:Result.TAP;
        return new Update(result,movement.scrollSteps(),x,y);
    }
    public boolean active(){return down;}
    public void cancel(){down=false;dragged=false;scrollRemainder=0;}
}
