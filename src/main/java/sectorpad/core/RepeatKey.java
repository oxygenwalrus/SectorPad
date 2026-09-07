package sectorpad.core;

/** Bounded navigation repeat: never replay missed repeat intervals after a stall. */
public final class RepeatKey {
    private String key="";
    private long started, next;
    public boolean pulse(String current, long now, double initialSeconds, double repeatSeconds) {
        if (current == null || current.isEmpty()) { reset(); return false; }
        if (!current.equals(key)) {
            key=current; started=now; next=now+(long)(initialSeconds*1e9); return true;
        }
        if (now < next) return false;
        double interval=now-started>1_500_000_000L ? Math.max(0.05,repeatSeconds*0.6) : repeatSeconds;
        next=now+(long)(Math.max(0.03,interval)*1e9); return true;
    }
    public void reset() { key=""; started=next=0; }
}
