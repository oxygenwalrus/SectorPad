package sectorpad.settings;

import lunalib.lunaSettings.LunaSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A bounded snapshot of Luna's native preferences. Read on the game thread after Save. */
public final class ControllerSettings {
    public final boolean refitAutoOpen;
    public static final int SCHEMA_VERSION = 1;
    public static final String MOD_ID = "sectorpad";
    public interface Values {
        Boolean booleanValue(String field);
        Integer intValue(String field);
        Double doubleValue(String field);
        String stringValue(String field);
    }
    public static final Values LUNA = new Values() {
        public Boolean booleanValue(String field) { return LunaSettings.getBoolean(MOD_ID, field); }
        public Integer intValue(String field) { return LunaSettings.getInt(MOD_ID, field); }
        public Double doubleValue(String field) { return LunaSettings.getDouble(MOD_ID, field); }
        public String stringValue(String field) { return LunaSettings.getString(MOD_ID, field); }
    };
    private static final Values EMPTY = new Values() {
        public Boolean booleanValue(String field) { return null; }
        public Integer intValue(String field) { return null; }
        public Double doubleValue(String field) { return null; }
        public String stringValue(String field) { return null; }
    };
    public final boolean enabled, hintsEnabled, diagnosticsEnabled, pauseOnDisconnect;
    public final boolean consoleTopLeft;
    public final boolean wheelHoldMode, pauseWheels, shieldToggle, invertScroll, latchedDrag, reducedMotion;
    public final int controllerIndex, hubKeycode, remapKeycode, keyboardKeycode, wheelSlots;
    public final String glyphStyle, profileId, steeringMode;
    public final float pointerSpeed, pointerGamma, pointerDeadzone, pointerOuter, precisionMultiplier;
    public final float scrollSpeed, scrollGamma, repeatDelay, repeatInterval, radialDeadzone, radialHoldSeconds, uiScale;
    public final float mapPanSpeed, zoomSpeed, aimRange;
    public final float leftDeadzone, rightDeadzone, leftOuter, rightOuter, leftGamma, rightGamma;
    public final float leftCenterX, leftCenterY, rightCenterX, rightCenterY;
    public final float leftTriggerMin, leftTriggerMax, rightTriggerMin, rightTriggerMax, triggerPress, triggerRelease;
    public final boolean invertLeftX, invertLeftY, invertRightX, invertRightY;
    private final Map<String, Object> original;

    private ControllerSettings(Values values) {
        RecordingValues recorded = new RecordingValues(values); values = recorded;
        enabled = bool(values, "sp_enabled", true);
        consoleTopLeft = bool(values, "sp_console_top_left", true);
        hintsEnabled = bool(values, "sp_hints", true);
        diagnosticsEnabled = bool(values, "sp_diagnostics", false);
        pauseOnDisconnect = bool(values, "sp_pause_disconnect", true);
        controllerIndex = integer(values, "sp_controller_index", -1, -1, 7);
        // Luna uses KEY_NONE (0) when a Keycode field is cleared; never turn that into Escape (1).
        hubKeycode = integer(values, "sp_hub_key", 66, 0, 255);
        remapKeycode = integer(values, "sp_remap_key", 68, 0, 255);
        keyboardKeycode = integer(values, "sp_keyboard_key", 67, 0, 255);
        glyphStyle = choice(values, "sp_glyphs", "Automatic", List.of("Automatic", "Xbox", "Steam Deck", "Generic"));
        String profile = values.stringValue("sp_profile");
        profileId = profile != null && profile.matches("[a-z0-9][a-z0-9_-]{0,47}") ? profile : "default";
        pointerSpeed = number(values, "sp_pointer_speed", 900f, 100f, 3000f);
        pointerGamma = number(values, "sp_pointer_gamma", 1.6f, 0.5f, 3f);
        pointerDeadzone = number(values, "sp_pointer_deadzone", .12f, 0f, .4f);
        pointerOuter = number(values, "sp_pointer_outer", .98f, .5f, 1f);
        precisionMultiplier = number(values, "sp_precision", .25f, .05f, 1f);
        scrollSpeed = number(values, "sp_scroll_speed", 12f, 1f, 40f);
        scrollGamma = number(values, "sp_scroll_gamma", 1.6f, .5f, 3f);
        invertScroll = bool(values, "sp_invert_scroll", false);
        repeatDelay = number(values, "sp_repeat_delay", .35f, .15f, 1f);
        repeatInterval = number(values, "sp_repeat_interval", .09f, .04f, .5f);
        radialDeadzone = number(values, "sp_radial_deadzone", .35f, .1f, .8f);
        radialHoldSeconds = number(values, "sp_radial_hold", .25f, .1f, .75f);
        refitAutoOpen = bool(values, "sp_refit_auto_open", true);
        uiScale = number(values, "sp_ui_scale", 1f, .75f, 1.8f);
        wheelSlots = Integer.parseInt(choice(values, "sp_wheel_slots", "8", List.of("4", "6", "8")));
        wheelHoldMode = bool(values, "sp_wheel_hold_mode", true);
        pauseWheels = bool(values, "sp_pause_wheels", true);
        shieldToggle = bool(values, "sp_shield_toggle", false);
        latchedDrag = bool(values, "sp_latched_drag", false);
        reducedMotion = bool(values, "sp_reduced_motion", false);
        mapPanSpeed = number(values, "sp_map_pan_speed", 900f, 100f, 3000f);
        zoomSpeed = number(values, "sp_zoom_speed", 5f, 1f, 20f);
        aimRange = number(values, "sp_aim_range", 1400f, 250f, 4000f);
        steeringMode = choice(values, "sp_steering_mode", "TwinStick", List.of("TwinStick", "Directional", "Orbital"));
        leftDeadzone = number(values, "sp_left_deadzone", .12f, 0f, .4f);
        rightDeadzone = number(values, "sp_right_deadzone", .12f, 0f, .4f);
        leftOuter = number(values, "sp_left_outer", .98f, .5f, 1f);
        rightOuter = number(values, "sp_right_outer", .98f, .5f, 1f);
        leftGamma = number(values, "sp_left_gamma", 1f, .5f, 3f);
        rightGamma = number(values, "sp_right_gamma", 1f, .5f, 3f);
        leftCenterX = number(values, "sp_left_center_x", 0f, -.3f, .3f);
        leftCenterY = number(values, "sp_left_center_y", 0f, -.3f, .3f);
        rightCenterX = number(values, "sp_right_center_x", 0f, -.3f, .3f);
        rightCenterY = number(values, "sp_right_center_y", 0f, -.3f, .3f);
        invertLeftX = bool(values, "sp_left_invert_x", false);
        invertLeftY = bool(values, "sp_left_invert_y", false);
        invertRightX = bool(values, "sp_right_invert_x", false);
        invertRightY = bool(values, "sp_right_invert_y", false);
        leftTriggerMin = number(values, "sp_lt_min", 0f, 0f, .4f);
        rightTriggerMin = number(values, "sp_rt_min", 0f, 0f, .4f);
        leftTriggerMax = number(values, "sp_lt_max", 1f, .6f, 1f);
        rightTriggerMax = number(values, "sp_rt_max", 1f, .6f, 1f);
        triggerPress = number(values, "sp_trigger_press", .55f, .2f, .95f);
        triggerRelease = Math.min(number(values, "sp_trigger_release", .4f, .05f, .9f), triggerPress - .05f);
        original = Map.copyOf(recorded.values);
    }
    private static final class RecordingValues implements Values {
        private final Values source; final Map<String, Object> values = new LinkedHashMap<>();
        RecordingValues(Values source) { this.source = java.util.Objects.requireNonNull(source); }
        private <T> T record(String key, T value) { if (value != null) values.put(key, value); return value; }
        public Boolean booleanValue(String key) { return record(key, source.booleanValue(key)); }
        public Integer intValue(String key) { return record(key, source.intValue(key)); }
        public Double doubleValue(String key) { return record(key, source.doubleValue(key)); }
        public String stringValue(String key) { return record(key, source.stringValue(key)); }
    }
    Values originalValues() {
        return new Values() {
            public Boolean booleanValue(String key) { return (Boolean) original.get(key); }
            public Integer intValue(String key) { return (Integer) original.get(key); }
            public Double doubleValue(String key) { return (Double) original.get(key); }
            public String stringValue(String key) { return (String) original.get(key); }
        };
    }
    String sectionFingerprint(PreferenceResets.Section section) {
        StringBuilder values = new StringBuilder();
        for (String key : section.fields) { String value = String.valueOf(original.get(key)); values.append(key).append('=').append(value.length()).append(':').append(value).append('\n'); }
        return ProfileStore.digest(values.toString());
    }
    public static ControllerSettings defaults() { return new ControllerSettings(EMPTY); }
    public static ControllerSettings load() { return from(LUNA); }
    public static ControllerSettings from(Values values) { return new ControllerSettings(values); }
    public static BindingProfile loadBindings() { return bindingsFrom(LUNA); }
    public static BindingProfile bindingsFrom(Values values) {
        Map<String, Map<String, String>> bindings = new LinkedHashMap<>();
        for (String context : BindingProfile.CONTEXTS) {
            Map<String, String> entries = new LinkedHashMap<>();
            BindingProfile.defaultsFor(context).forEach((action, fallback) -> {
                String value = values.stringValue(BindingProfile.settingId(context, action));
                entries.put(action, value == null ? fallback : value.trim());
            });
            bindings.put(context, entries);
        }
        return new BindingProfile("luna-custom", "Luna settings", bindings);
    }
    public DeviceCalibration calibrationDefaults() {
        return new DeviceCalibration(new DeviceCalibration.Stick(leftCenterX, leftCenterY, leftDeadzone, leftOuter, leftGamma, invertLeftX, invertLeftY),
                new DeviceCalibration.Stick(rightCenterX, rightCenterY, rightDeadzone, rightOuter, rightGamma, invertRightX, invertRightY),
                new DeviceCalibration.Trigger(leftTriggerMin, leftTriggerMax), new DeviceCalibration.Trigger(rightTriggerMin, rightTriggerMax));
    }
    private static boolean bool(Values values, String field, boolean fallback) { Boolean value = values.booleanValue(field); return value == null ? fallback : value; }
    private static int integer(Values values, String field, int fallback, int min, int max) { Integer value = values.intValue(field); return value == null ? fallback : Math.max(min, Math.min(max, value)); }
    private static float number(Values values, String field, float fallback, float min, float max) { Double value = values.doubleValue(field); return value == null || !Double.isFinite(value) ? fallback : (float) Math.max(min, Math.min(max, value)); }
    private static String choice(Values values, String field, String fallback, List<String> choices) { String value = values.stringValue(field); return value != null && choices.contains(value) ? value : fallback; }
}
