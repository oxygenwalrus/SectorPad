package sectorpad.settings;

/** Independently versioned per-device calibration in normalized SDL coordinates. */
public final class DeviceCalibration {
    public static final int SCHEMA_VERSION = 1;
    public final Stick left, right;
    public final Trigger leftTrigger, rightTrigger;
    public DeviceCalibration(Stick left, Stick right, Trigger leftTrigger, Trigger rightTrigger) {
        this.left = java.util.Objects.requireNonNull(left);
        this.right = java.util.Objects.requireNonNull(right);
        this.leftTrigger = java.util.Objects.requireNonNull(leftTrigger);
        this.rightTrigger = java.util.Objects.requireNonNull(rightTrigger);
    }
    public static DeviceCalibration defaults() { return ControllerSettings.defaults().calibrationDefaults(); }
    public enum Section { LEFT_STICK, RIGHT_STICK, TRIGGERS, ALL }
    public DeviceCalibration reset(Section section) {
        DeviceCalibration defaults = defaults();
        return switch (java.util.Objects.requireNonNull(section)) {
            case LEFT_STICK -> new DeviceCalibration(defaults.left, right, leftTrigger, rightTrigger);
            case RIGHT_STICK -> new DeviceCalibration(left, defaults.right, leftTrigger, rightTrigger);
            case TRIGGERS -> new DeviceCalibration(left, right, defaults.leftTrigger, defaults.rightTrigger);
            case ALL -> defaults;
        };
    }
    public static final class Stick {
        public final float centerX, centerY, inner, outer, gamma;
        public final boolean invertX, invertY;
        public Stick(float centerX, float centerY, float inner, float outer, float gamma, boolean invertX, boolean invertY) {
            range(centerX, -.3f, .3f, "center X"); range(centerY, -.3f, .3f, "center Y");
            range(inner, 0f, .4f, "inner deadzone"); range(outer, .5f, 1f, "outer deadzone"); range(gamma, .5f, 3f, "response gamma");
            this.centerX = centerX; this.centerY = centerY; this.inner = inner; this.outer = outer; this.gamma = gamma; this.invertX = invertX; this.invertY = invertY;
        }
        /** Radial deadzone and unit-circle clamping preserve diagonal direction and speed. */
        public float[] apply(float rawX, float rawY) {
            if (!Float.isFinite(rawX) || !Float.isFinite(rawY)) return new float[] {0f, 0f};
            float x = Math.max(-1f, Math.min(1f, rawX)) - centerX, y = Math.max(-1f, Math.min(1f, rawY)) - centerY;
            double length = Math.hypot(x, y);
            if (length <= inner) return new float[] {0f, 0f};
            double magnitude = Math.pow(Math.min(1d, (length - inner) / (outer - inner)), gamma);
            return new float[] {(float) (x / length * magnitude * (invertX ? -1 : 1)), (float) (y / length * magnitude * (invertY ? -1 : 1))};
        }
    }
    public static final class Trigger {
        public final float min, max;
        public Trigger(float min, float max) {
            range(min, 0f, .4f, "trigger rest"); range(max, .6f, 1f, "trigger maximum"); this.min = min; this.max = max;
        }
        public float apply(float raw) { return !Float.isFinite(raw) ? 0f : Math.max(0f, Math.min(1f, (raw - min) / (max - min))); }
    }
    private static void range(float value, float min, float max, String field) {
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid calibration " + field + ".");
    }
}
