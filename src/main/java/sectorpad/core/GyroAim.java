package sectorpad.core;

/** Integrates Steam Input mouse-style gyro deltas into a bounded 2D combat aim vector. */
public final class GyroAim {
    private float x=1,y,filteredX,filteredY;
    private boolean moved;
    public void motion(float dx,float dy,float sensitivity,float smoothing,boolean invertX,boolean invertY){
        if(!Float.isFinite(dx)||!Float.isFinite(dy))return;
        float keep=InputMath.finite(smoothing,0,.95f),take=1-keep;
        filteredX=filteredX*keep+dx*take;filteredY=filteredY*keep+dy*take;
        float scale=InputMath.finite(sensitivity,.0005f,.05f);
        x+=(invertX?-filteredX:filteredX)*scale;
        y+=(invertY?-filteredY:filteredY)*scale;
        float length=(float)Math.hypot(x,y);
        if(length>1){x/=length;y/=length;}
        moved|=Math.abs(filteredX)+Math.abs(filteredY)>.001f;
    }
    public InputMath.Vector blend(float stickX,float stickY,boolean enabled){
        float length=(float)Math.hypot(stickX,stickY);
        if(length>.2f){x=stickX/length;y=stickY/length;moved=false;}
        if(!enabled)return new InputMath.Vector(stickX,stickY);
        return moved||length>.2f?new InputMath.Vector(x,y):new InputMath.Vector(0,0);
    }
    public void recenter(float x,float y){
        float length=(float)Math.hypot(x,y);
        this.x=length>.1f?x/length:1;this.y=length>.1f?y/length:0;
        filteredX=filteredY=0;moved=false;
    }
}
