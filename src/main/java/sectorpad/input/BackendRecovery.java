package sectorpad.input;

/** Restarts a stale enumeration only while no usable controllers exist, with bounded backoff. */
final class BackendRecovery {
    private long since;
    private boolean waiting;
    private int attempts;
    boolean restartDue(long now,boolean anyConnected){
        if(anyConnected){reset();return false;}
        if(!waiting){since=now;waiting=true;return false;}
        long delay=Math.min(30,10+attempts*5)*1_000_000_000L;
        if(now-since<delay)return false;
        since=now;attempts=Math.min(4,attempts+1);return true;
    }
    void reset(){waiting=false;attempts=0;}
}
