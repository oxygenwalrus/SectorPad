package sectorpad.core;

public final class TouchAndGyroTests {
    private static int checks;
    public static void main(String[] args){
        TouchGesture touch=new TouchGesture();
        touch.begin(10,10,0);check(touch.end(18,16,100_000_000L).result()==TouchGesture.Result.TAP,"small touch is a release-time tap");
        touch.begin(10,10,0);check(touch.end(10,10,500_000_000L).result()==TouchGesture.Result.LONG_PRESS,"stationary hold is secondary gesture");
        touch.begin(10,10,0);check(touch.move(10,60).scrollSteps()==1,"drag converts to bounded list steps");
        check(touch.end(10,60,600_000_000L).result()==TouchGesture.Result.NONE,"drag never also activates a button");
        touch.begin(0,0,0);var releaseDrag=touch.end(0,50,10_000_000L);
        check(releaseDrag.result()==TouchGesture.Result.NONE&&releaseDrag.scrollSteps()==1,"movement first observed at release scrolls and is not a tap");
        touch.begin(0,0,0);touch.cancel();check(touch.end(0,0,1).result()==TouchGesture.Result.NONE,"context loss cancels touch ownership");
        check(touch.end(0,0,2).result()==TouchGesture.Result.NONE,"release without ownership is ignored");
        GyroAim gyro=new GyroAim();
        check(close(gyro.blend(0,0,true).x(),0),"idle gyro does not seize aim");
        var stick=gyro.blend(0,1,true);check(close(stick.x(),0)&&close(stick.y(),1),"stick establishes broad aim");
        gyro.motion(20,0,.01f,0,false,false);var fine=gyro.blend(0,0,true);
        check(fine.x()>0&&fine.y()>.9f,"gyro refines the retained stick direction");
        gyro.motion(Float.NaN,Float.POSITIVE_INFINITY,.01f,0,false,false);var finite=gyro.blend(0,0,true);
        check(Float.isFinite(finite.x())&&Float.isFinite(finite.y()),"bad deltas cannot poison aim");
        gyro.recenter(-1,0);var centre=gyro.blend(0,0,true);check(close(centre.x(),0),"recenter waits for fresh input");
        gyro.motion(10,0,.01f,0,true,false);check(gyro.blend(0,0,true).x()<0,"axis inversion is applied before integration");
        System.out.println("TouchAndGyroTests: "+checks+" touch release, drag and gyro blend checks passed");
    }
    private static boolean close(float a,float b){return Math.abs(a-b)<.001f;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
