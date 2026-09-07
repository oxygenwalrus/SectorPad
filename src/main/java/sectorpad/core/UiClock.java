package sectorpad.core;

/** Simulation pause does not stop input. Resume gaps discard elapsed time. */
public final class UiClock {
    private long previous;
    private boolean interrupted;
    public float tick(long now) {
        if (previous == 0) { previous = now; return 0; }
        long elapsed = now - previous;
        previous = now;
        interrupted = elapsed < 0 || elapsed > 250_000_000L;
        return interrupted ? 0 : Math.min(elapsed/1_000_000_000f, 0.05f);
    }
    public boolean interrupted() { return interrupted; }
    public void reset() { previous = 0; interrupted = false; }
}
