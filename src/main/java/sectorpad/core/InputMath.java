package sectorpad.core;

public final class InputMath {
    private InputMath() {}
    public record Vector(float x, float y) {}
    public static Vector shape(float x, float y, double inner, double outer, double gamma) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Double.isFinite(inner)
                || !Double.isFinite(outer) || !Double.isFinite(gamma)
                || inner < 0 || outer <= inner || gamma <= 0) return new Vector(0,0);
        double magnitude = Math.hypot(x,y);
        if (magnitude <= inner) return new Vector(0,0);
        double effort = Math.pow(Math.min(1, (magnitude-inner)/(outer-inner)),gamma);
        return new Vector((float)(x/magnitude*effort),(float)(y/magnitude*effort));
    }
    public static float finite(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min,Math.min(max,value)) : 0;
    }
    public static double angleDistance(double a, double b) {
        double d = (a-b) % (Math.PI*2);
        if (d > Math.PI) d -= Math.PI*2;
        if (d < -Math.PI) d += Math.PI*2;
        return Math.abs(d);
    }
}
