package sectorpad.core;

public final class ScrollAccumulator {
    private String owner="";
    private double remainder;
    public int advance(String pane, double velocity, double dt) {
        if (!owner.equals(pane)) { owner=pane; remainder=0; }
        if (!Double.isFinite(velocity) || !Double.isFinite(dt) || dt<=0 || dt>0.25) return 0;
        remainder += velocity*dt;
        int notches=(int)remainder;
        notches=Math.max(-4,Math.min(4,notches));
        remainder-=notches;
        return notches;
    }
    public void reset() { owner=""; remainder=0; }
    public double remainder() { return remainder; }
}
