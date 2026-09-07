package sectorpad.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Stable command IDs only. Catalog objects, availability, labels and executable actions are never persisted. */
public final class WheelLayout {
    public static final int SCHEMA_VERSION = 1, MAX_WHEELS = 64, MAX_ENTRIES = 256;
    private final Map<String, Map<String, List<String>>> profiles;

    public WheelLayout(Map<String, ? extends Map<String, ? extends List<String>>> profiles) {
        Objects.requireNonNull(profiles);
        if (profiles.size() > ProfileStore.MAX_PROFILES) throw new IllegalArgumentException("Too many wheel profiles.");
        Map<String, Map<String, List<String>>> copy = new LinkedHashMap<>();
        profiles.forEach((profile, wheels) -> {
            if (profile == null || !profile.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid wheel profile ID.");
            if (wheels == null || wheels.size() > MAX_WHEELS) throw new IllegalArgumentException("Too many saved wheels.");
            Map<String, List<String>> values = new LinkedHashMap<>();
            wheels.forEach((wheel, entries) -> { validateWheelId(wheel); values.put(wheel, validatedEntries(entries)); });
            copy.put(profile, Collections.unmodifiableMap(values));
        });
        this.profiles = Collections.unmodifiableMap(copy);
    }
    public static WheelLayout defaults() { return new WheelLayout(Map.of()); }
    public Map<String, Map<String, List<String>>> profiles() { return profiles; }
    public Map<String, List<String>> wheels(String profile) { return profiles.getOrDefault(profile, Map.of()); }
    public List<String> order(String profile, String wheel) { return wheels(profile).getOrDefault(wheel, List.of()); }
    public WheelLayout withOrder(String profile, String wheel, List<String> order) {
        Map<String, Map<String, List<String>>> next = copy();
        Map<String, List<String>> wheels = new LinkedHashMap<>(wheels(profile)); wheels.put(wheel, order); next.put(profile, wheels);
        return new WheelLayout(next);
    }
    public WheelLayout reset(String profile, String wheel) {
        Map<String, Map<String, List<String>>> next = copy();
        Map<String, List<String>> wheels = new LinkedHashMap<>(wheels(profile)); wheels.remove(wheel);
        if (wheels.isEmpty()) next.remove(profile); else next.put(profile, wheels);
        return new WheelLayout(next);
    }
    public WheelLayout resetProfile(String profile) { Map<String, Map<String, List<String>>> next = copy(); next.remove(profile); return new WheelLayout(next); }
    private Map<String, Map<String, List<String>>> copy() { return new LinkedHashMap<>(profiles); }
    public static void validateWheelId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,79}")) throw new IllegalArgumentException("Invalid stable wheel ID.");
    }
    public static List<String> validatedEntries(List<String> entries) {
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many wheel commands.");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : entries) {
            if (id == null || id.isBlank() || id.length() > 160 || id.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid stable command ID.");
            if (!unique.add(id)) throw new IllegalArgumentException("Duplicate command in wheel order: " + id);
        }
        return List.copyOf(unique);
    }
    /** Saved missing IDs retain their place when they return; new IDs append in the current catalog order. */
    public <T> List<T> apply(String profile, String wheel, List<T> entries, Function<T, String> id) {
        validateWheelId(wheel);
        Map<String, T> available = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>();
        for (T entry : entries) { String key = id.apply(entry); ids.add(key); available.put(key, entry); }
        validatedEntries(ids);
        List<T> result = new ArrayList<>();
        for (String key : order(profile, wheel)) { T entry = available.remove(key); if (entry != null) result.add(entry); }
        result.addAll(available.values()); return List.copyOf(result);
    }
    @Override public boolean equals(Object other) { return other instanceof WheelLayout layout && profiles.equals(layout.profiles); }
    @Override public int hashCode() { return profiles.hashCode(); }
}
