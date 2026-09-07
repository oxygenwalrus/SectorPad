package sectorpad.refit;

/** Pure lifecycle policy. Nothing here owns a ship, game save, or input device. */
public final class RefitSession {
    public enum Mode { OUTSIDE, READY, WORKSPACE, NATIVE, HANDOFF, SUSPENDED }
    private Object visit;
    private Mode mode=Mode.OUTSIDE;
    private boolean dialogSeen;
    private boolean expectsDialog;
    private long handoffStarted;
    public Mode mode(){return mode;}
    public boolean visible(){return mode==Mode.WORKSPACE;}
    public void observe(Object owner,boolean blocked,boolean controllerActive,boolean safe,boolean simulation,long now){
        if(owner==null){
            if(simulation&&(mode==Mode.HANDOFF||mode==Mode.SUSPENDED)){mode=Mode.SUSPENDED;return;}
            visit=null;mode=Mode.OUTSIDE;return;
        }
        if(mode==Mode.SUSPENDED){visit=owner;mode=Mode.HANDOFF;handoffStarted=now;dialogSeen=true;}
        if(visit!=owner){visit=owner;mode=Mode.READY;dialogSeen=false;}
        if(!safe){suspend();return;}
        if(mode==Mode.WORKSPACE&&blocked){mode=Mode.HANDOFF;handoffStarted=now;dialogSeen=true;}
        if(mode==Mode.READY&&!blocked&&controllerActive)mode=Mode.WORKSPACE;
        if(mode==Mode.HANDOFF){
            dialogSeen|=blocked;
            if(!blocked&&(dialogSeen||!expectsDialog&&now-handoffStarted>800_000_000L))mode=Mode.WORKSPACE;
            // A picker that never appeared must never be covered by a timed reopen.
            if(!blocked&&!dialogSeen&&expectsDialog&&now-handoffStarted>3_000_000_000L)mode=Mode.NATIVE;
        }
    }
    public void open(){if(visit!=null)mode=Mode.WORKSPACE;}
    public void nativeMode(){if(visit!=null)mode=Mode.NATIVE;}
    public void handoff(long now,boolean dialog){mode=Mode.HANDOFF;handoffStarted=now;dialogSeen=false;expectsDialog=dialog;}
    public void suspend(){if(mode==Mode.WORKSPACE||mode==Mode.HANDOFF||mode==Mode.SUSPENDED)mode=Mode.NATIVE;}
    public void reset(){visit=null;mode=Mode.OUTSIDE;dialogSeen=false;}
}
