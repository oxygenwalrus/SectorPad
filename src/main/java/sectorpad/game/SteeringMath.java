package sectorpad.game;

/** Pure controller-to-command math. World coordinates use positive Y up. */
public final class SteeringMath {
    private SteeringMath() {}
    public record LocalMovement(float forward, float left) {}

    public static LocalMovement local(float worldX, float worldY, float facingDegrees) {
        if (!Float.isFinite(worldX) || !Float.isFinite(worldY) || !Float.isFinite(facingDegrees)) return new LocalMovement(0, 0);
        double facing = Math.toRadians(facingDegrees);
        return new LocalMovement((float) (worldX * Math.cos(facing) + worldY * Math.sin(facing)),
                (float) (-worldX * Math.sin(facing) + worldY * Math.cos(facing)));
    }

    public static float angleError(float targetDegrees, float facingDegrees) {
        if (!Float.isFinite(targetDegrees) || !Float.isFinite(facingDegrees)) return 0;
        float delta = (targetDegrees - facingDegrees) % 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return delta;
    }

    /** -1 clockwise, +1 counterclockwise, 0 inside the stopping band. */
    public static int turn(float targetDegrees, float facingDegrees, float angularVelocity,
                           float deceleration, float seconds) {
        float error = angleError(targetDegrees, facingDegrees);
        if (!Float.isFinite(angularVelocity)) angularVelocity = 0;
        if (!Float.isFinite(deceleration) || deceleration <= 0) deceleration = 1;
        if (!Float.isFinite(seconds)) seconds = 0;
        float dt = Math.min(Math.max(seconds, 0), .05f);
        float stopping = angularVelocity * Math.abs(angularVelocity) / (2f * deceleration);
        float predicted = error - stopping - angularVelocity * dt;
        if (Math.abs(error) < .65f && Math.abs(angularVelocity) < 1f) return 0;
        return predicted > .15f ? 1 : predicted < -.15f ? -1 : 0;
    }
}
