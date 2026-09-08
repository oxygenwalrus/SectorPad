package sectorpad.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Section-scoped factory-default overrides. Luna remains untouched and resumes ownership after an edit. */
public final class PreferenceResets {
    public static final int SCHEMA_VERSION = 1;
    public enum Section {
        GENERAL("Device and shortcuts", "sp_enabled", "sp_controller_index", "sp_controller_backend", "sp_glyphs", "sp_pause_disconnect", "sp_hub_key", "sp_keyboard_key", "sp_remap_key"),
        POINTER("Pointer", "sp_pointer_speed", "sp_pointer_gamma", "sp_pointer_deadzone", "sp_pointer_outer", "sp_precision", "sp_latched_drag"),
        SCROLL("Scrolling", "sp_scroll_speed", "sp_scroll_gamma", "sp_invert_scroll"),
        MAP("Map movement", "sp_map_pan_speed", "sp_zoom_speed"),
        NAVIGATION("Navigation and refit", "sp_repeat_delay", "sp_repeat_interval", "sp_refit_auto_open"),
        WHEELS("Wheel behavior", "sp_wheel_slots", "sp_wheel_hold_mode", "sp_pause_wheels", "sp_radial_deadzone", "sp_radial_hold"),
        COMBAT("Combat steering and aim", "sp_steering_mode", "sp_aim_range", "sp_shield_toggle",
                "sp_gyro_mode", "sp_gyro_activation", "sp_gyro_sensitivity", "sp_gyro_smoothing", "sp_gyro_invert_x", "sp_gyro_invert_y"),
        TRIGGER_BUTTONS("Trigger button thresholds", "sp_trigger_press", "sp_trigger_release"),
        OVERLAY("Overlay appearance", "sp_hints", "sp_diagnostics", "sp_reduced_motion", "sp_ui_scale", "sp_console_top_left");
        public final String label;
        public final List<String> fields;
        Section(String label, String... fields) { this.label = label; this.fields = List.of(fields); }
    }
    private final Map<String, String> baselines;
    public PreferenceResets(Map<String, String> baselines) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (baselines.size() > Section.values().length) throw new IllegalArgumentException("Too many preference reset sections.");
        baselines.forEach((key, fingerprint) -> {
            Section.valueOf(key);
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid preference reset fingerprint.");
            copy.put(key, fingerprint);
        });
        this.baselines = Collections.unmodifiableMap(copy);
    }
    public static PreferenceResets defaults() { return new PreferenceResets(Map.of()); }
    public Map<String, String> baselines() { return baselines; }
    public Set<Section> sections() { Set<Section> result = new LinkedHashSet<>(); for (Section section : Section.values()) if (baselines.containsKey(section.name())) result.add(section); return Collections.unmodifiableSet(result); }
    public PreferenceResets reset(Section section, ControllerSettings nativeSettings) {
        Map<String, String> next = new LinkedHashMap<>(baselines); next.put(section.name(), nativeSettings.sectionFingerprint(section)); return new PreferenceResets(next);
    }
    public PreferenceResets clear(Section section) { Map<String, String> next = new LinkedHashMap<>(baselines); next.remove(section.name()); return new PreferenceResets(next); }
    public PreferenceResets reconcile(ControllerSettings nativeSettings) {
        Map<String, String> next = new LinkedHashMap<>(baselines);
        next.entrySet().removeIf(entry -> !entry.getValue().equals(nativeSettings.sectionFingerprint(Section.valueOf(entry.getKey()))));
        return new PreferenceResets(next);
    }
    public ControllerSettings apply(ControllerSettings nativeSettings) {
        Set<String> resetFields = new LinkedHashSet<>();
        reconcile(nativeSettings).sections().forEach(section -> resetFields.addAll(section.fields));
        ControllerSettings.Values source = nativeSettings.originalValues();
        return ControllerSettings.from(new ControllerSettings.Values() {
            public Boolean booleanValue(String key) { return resetFields.contains(key) ? null : source.booleanValue(key); }
            public Integer intValue(String key) { return resetFields.contains(key) ? null : source.intValue(key); }
            public Double doubleValue(String key) { return resetFields.contains(key) ? null : source.doubleValue(key); }
            public String stringValue(String key) { return resetFields.contains(key) ? null : source.stringValue(key); }
        });
    }
    @Override public boolean equals(Object other) { return other instanceof PreferenceResets resets && baselines.equals(resets.baselines); }
    @Override public int hashCode() { return baselines.hashCode(); }
}
