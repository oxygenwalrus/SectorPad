package sectorpad.bridge;

/** Coordinates stay in Starsector's scaled, bottom-left-origin UI space. */
public final class PointerCoordinates {
    private PointerCoordinates() { }

    public static float clamp(float value, float maximum) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(value, Math.max(0f, maximum - 1f)));
    }

    public static int toPhysical(float logical, float scale, int pixels) {
        float safeScale = Float.isFinite(scale) && scale > 0f ? scale : 1f;
        return Math.max(0, Math.min(Math.round(logical * safeScale), Math.max(0, pixels - 1)));
    }
}
