package sectorpad.settings;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Versioned, bounded JSON storage. No dependency on Starsector or campaign-save data. */
public final class ProfileStore {
    public static final int SCHEMA_VERSION = 1;
    public static final long MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_PROFILES = 64, MAX_DEVICES = 64;
    private final Storage storage;
    private String warning = "";
    private interface Decoder<T> { T decode(JSONObject json) throws IOException, JSONException; }

    /** Explicit headless/tooling storage. The runtime uses supported game common-data APIs. */
    public ProfileStore(Path directory) { this(new NioStorage(directory)); }
    public ProfileStore(Storage storage) { this.storage = Objects.requireNonNull(storage); }
    /** Kept as the runtime factory for source compatibility; storage is game-owned common data. */
    public static ProfileStore inUserDirectory() { return new ProfileStore(new CommonStorage()); }
    public static ProfileStore inCommonData() { return new ProfileStore(new CommonStorage()); }
    public Path directory() { return Path.of(storage.location()); }
    public String storageDescription() { return storage instanceof CommonStorage ? "Game common data: SectorPad" : storage.location(); }
    public synchronized String warning() { return warning; }
    public synchronized boolean usedAtomicReplacement() { return storage.supportsAtomicReplacement(); }

    public static final class State {
        public final Map<String, BindingProfile> profiles;
        public final String activeId, nativeBindingFingerprint;
        public State(Map<String, BindingProfile> profiles, String activeId, String nativeBindingFingerprint) {
            if (profiles == null || profiles.isEmpty() || profiles.size() > MAX_PROFILES || !profiles.containsKey(activeId)) throw new IllegalArgumentException("Invalid active profile or profile count.");
            profiles.forEach((id, profile) -> { if (!id.equals(profile.id)) throw new IllegalArgumentException("Profile ID mismatch."); });
            if (nativeBindingFingerprint == null || !(nativeBindingFingerprint.isEmpty() || nativeBindingFingerprint.matches("[0-9a-f]{64}"))) throw new IllegalArgumentException("Invalid native settings fingerprint.");
            this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles)); this.activeId = activeId; this.nativeBindingFingerprint = nativeBindingFingerprint;
        }
        public static State defaults() { return new State(Map.of("default", BindingProfile.defaults(), "southpaw", BindingProfile.southpaw()), "default", ""); }
        public BindingProfile active() { return profiles.get(activeId); }
        public State withActive(BindingProfile profile, String fingerprint) {
            Map<String, BindingProfile> next = new LinkedHashMap<>(profiles); next.put(profile.id, profile);
            return new State(next, profile.id, fingerprint);
        }
        public State withFingerprint(String fingerprint) { return new State(profiles, activeId, fingerprint); }
        public State without(String id) {
            if (id.equals(activeId) || id.equals("default") || id.equals("southpaw")) throw new IllegalArgumentException("The active or built-in profile cannot be deleted.");
            Map<String, BindingProfile> next = new LinkedHashMap<>(profiles); next.remove(id); return new State(next, activeId, nativeBindingFingerprint);
        }
    }
    public static final class FutureSchemaException extends IOException {
        public FutureSchemaException(String document, int version) { super(document + " uses newer schema " + version + "; it was left unchanged. Update SectorPad to read it."); }
    }
    public synchronized State load() throws IOException { return readWithBackup(path("profiles.json"), ProfileStore::decodeState, State.defaults()); }
    public synchronized void save(State state) throws IOException { write(path("profiles.json"), encodeState(state), ProfileStore::decodeState); }
    public synchronized WheelLayout loadWheelLayout() throws IOException { return readWithBackup("wheel-layout.json", ProfileStore::decodeWheelLayout, WheelLayout.defaults()); }
    public synchronized void saveWheelLayout(WheelLayout layout) throws IOException {
        write("wheel-layout.json", new JSONObject(Map.of("schemaVersion", WheelLayout.SCHEMA_VERSION, "profiles", layout.profiles())), ProfileStore::decodeWheelLayout);
    }
    public synchronized PreferenceResets loadPreferenceResets() throws IOException { return readWithBackup("preference-resets.json", ProfileStore::decodePreferenceResets, PreferenceResets.defaults()); }
    public synchronized void savePreferenceResets(PreferenceResets resets) throws IOException {
        write("preference-resets.json", new JSONObject(Map.of("schemaVersion", PreferenceResets.SCHEMA_VERSION, "sections", resets.baselines())), ProfileStore::decodePreferenceResets);
    }
    public synchronized Map<String, DeviceCalibration> loadCalibrations() throws IOException {
        return readWithBackup(path("calibration.json"), ProfileStore::decodeCalibrations, Map.of());
    }
    public synchronized void saveCalibration(String deviceId, DeviceCalibration calibration) throws IOException {
        String key = deviceKey(deviceId);
        Map<String, DeviceCalibration> next = new LinkedHashMap<>(loadCalibrations()); next.put(key, Objects.requireNonNull(calibration));
        if (next.size() > MAX_DEVICES) throw new IOException("There are already " + MAX_DEVICES + " saved device calibrations.");
        Map<String, Object> devices = new LinkedHashMap<>(); next.forEach((id, value) -> devices.put(id, encodeCalibration(value)));
        write(path("calibration.json"), new JSONObject(Map.of("schemaVersion", SCHEMA_VERSION, "devices", devices)), ProfileStore::decodeCalibrations);
    }
    public synchronized DeviceCalibration calibrationFor(String deviceId, DeviceCalibration fallback) throws IOException { return loadCalibrations().getOrDefault(deviceKey(deviceId), fallback); }
    public static String deviceKey(String identity) {
        if (identity == null || identity.isBlank() || identity.length() > 1024) throw new IllegalArgumentException("A bounded, non-empty controller identity is required.");
        return digest(identity);
    }
    public static String fingerprint(BindingProfile profile) {
        StringBuilder source = new StringBuilder();
        for (String context : BindingProfile.CONTEXTS) for (String action : BindingProfile.defaultsFor(context).keySet()) source.append(context).append('/').append(action).append('=').append(profile.binding(context, action)).append('\n');
        return digest(source.toString());
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public synchronized BindingProfile importProfile(Path source) throws IOException { return decodeText(storage.readExternal(source.toString()), ProfileStore::decodeProfile); }
    public synchronized Path exportProfile(BindingProfile profile) throws IOException {
        String target = "exports/" + profile.id + ".json";
        write(target, new JSONObject(encodeProfile(profile)), ProfileStore::decodeProfile); return directory().resolve(target);
    }
    public synchronized BindingProfile importNamed(String id) throws IOException {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid import profile ID.");
        return importProfile(directory().resolve("imports").resolve(id + ".json"));
    }
    public synchronized List<Path> importableFiles() throws IOException {
        return storage.list("imports").stream().map(name -> directory().resolve(name)).toList();
    }
    private static String path(String file) { return file; }
    private <T> T readWithBackup(String source, Decoder<T> decoder, T fallback) throws IOException {
        warning = "";
        if (!storage.exists(source)) {
            if (storage.exists(source + ".bak")) { T recovered = decodeText(storage.read(source + ".bak"), decoder); warning = "Recovered missing " + source + " from its last known-good backup."; return recovered; }
            return fallback;
        }
        try { T result = decodeText(storage.read(source), decoder); warning = storage.warning(); return result; }
        catch (FutureSchemaException future) { throw future; }
        catch (IOException invalid) {
            if (storage.exists(source + ".bak")) {
                try { T recovered = decodeText(storage.read(source + ".bak"), decoder); warning = "Recovered " + source + " from its backup; the damaged original is preserved."; return recovered; }
                catch (FutureSchemaException future) { throw future; }
                catch (IOException noBackup) { invalid.addSuppressed(noBackup); }
            }
            warning = "Could not read " + source + "; safe defaults are active and the original is preserved. " + invalid.getMessage();
            return fallback;
        }
    }
    private static <T> T decodeText(String text, Decoder<T> decoder) throws IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("Invalid or oversized configuration document.");
        try { return decoder.decode(new JSONObject(text)); }
        catch (JSONException | IllegalArgumentException invalid) { throw new IOException("Invalid configuration: " + invalid.getMessage(), invalid); }
    }
    /** Updates only a valid backup. Runtime common storage verifies a dual-slot commit before returning. */
    private <T> void write(String target, JSONObject json, Decoder<T> decoder) throws IOException {
        try (Storage.Lease ignored = storage.acquire()) {
            String text = json.toString(); decodeText(text, decoder);
            if (storage.exists(target)) {
                String prior = null;
                try { prior = storage.read(target); decodeText(prior, decoder); }
                catch (FutureSchemaException future) { throw future; }
                catch (IOException invalid) {
                    prior = null;
                    storage.write(target + ".rejected-" + UUID.randomUUID(), storage.readRaw(target));
                }
                if (prior != null) { storage.write(target + ".bak", prior); if (!prior.equals(storage.read(target + ".bak"))) throw new IOException("Backup verification failed."); }
            }
            storage.write(target, text);
            if (!text.equals(storage.read(target))) throw new IOException("Settings commit did not verify.");
        }
    }
    private static JSONObject encodeState(State state) {
        Map<String, Object> profiles = new LinkedHashMap<>(); state.profiles.forEach((id, profile) -> profiles.put(id, encodeProfile(profile)));
        return new JSONObject(Map.of("schemaVersion", SCHEMA_VERSION, "activeProfileId", state.activeId, "nativeBindingFingerprint", state.nativeBindingFingerprint, "profiles", profiles));
    }
    private static Map<String, Object> encodeProfile(BindingProfile profile) { return Map.of("schemaVersion", BindingProfile.SCHEMA_VERSION, "id", profile.id, "displayName", profile.displayName, "contexts", profile.contexts()); }
    private static State decodeState(JSONObject json) throws IOException, JSONException {
        schema(json, SCHEMA_VERSION, "Profile collection");
        JSONObject entries = json.getJSONObject("profiles");
        if (entries.length() == 0 || entries.length() > MAX_PROFILES) throw new IOException("Invalid profile count.");
        Map<String, BindingProfile> profiles = new LinkedHashMap<>();
        for (String id : names(entries)) profiles.put(id, decodeProfile(entries.getJSONObject(id)));
        return new State(profiles, text(json, "activeProfileId"), text(json, "nativeBindingFingerprint"));
    }
    private static BindingProfile decodeProfile(JSONObject json) throws IOException, JSONException {
        schema(json, BindingProfile.SCHEMA_VERSION, "Action profile");
        JSONObject rawContexts = json.getJSONObject("contexts");
        Map<String, Map<String, String>> contexts = new LinkedHashMap<>();
        for (String context : names(rawContexts)) {
            JSONObject rawBindings = rawContexts.getJSONObject(context); Map<String, String> bindings = new LinkedHashMap<>();
            for (String action : names(rawBindings)) bindings.put(action, text(rawBindings, action));
            contexts.put(context, bindings);
        }
        return new BindingProfile(text(json, "id"), text(json, "displayName"), contexts);
    }
    private static Map<String, DeviceCalibration> decodeCalibrations(JSONObject json) throws IOException, JSONException {
        schema(json, SCHEMA_VERSION, "Device collection"); JSONObject entries = json.getJSONObject("devices");
        if (entries.length() > MAX_DEVICES) throw new IOException("Too many calibrated devices.");
        Map<String, DeviceCalibration> result = new LinkedHashMap<>();
        for (String id : names(entries)) {
            if (!id.matches("[0-9a-f]{64}")) throw new IOException("Invalid device key.");
            JSONObject value = entries.getJSONObject(id); schema(value, DeviceCalibration.SCHEMA_VERSION, "Device calibration");
            result.put(id, new DeviceCalibration(decodeStick(value.getJSONObject("left")), decodeStick(value.getJSONObject("right")), decodeTrigger(value.getJSONObject("leftTrigger")), decodeTrigger(value.getJSONObject("rightTrigger"))));
        }
        return Collections.unmodifiableMap(result);
    }
    private static Map<String, Object> encodeCalibration(DeviceCalibration value) {
        return Map.of("schemaVersion", DeviceCalibration.SCHEMA_VERSION, "left", encodeStick(value.left), "right", encodeStick(value.right), "leftTrigger", Map.of("min", value.leftTrigger.min, "max", value.leftTrigger.max), "rightTrigger", Map.of("min", value.rightTrigger.min, "max", value.rightTrigger.max));
    }
    private static WheelLayout decodeWheelLayout(JSONObject json) throws IOException, JSONException {
        schema(json, WheelLayout.SCHEMA_VERSION, "Wheel layout");
        JSONObject rawProfiles = json.getJSONObject("profiles");
        Map<String, Map<String, List<String>>> profiles = new LinkedHashMap<>();
        if (rawProfiles.length() > MAX_PROFILES) throw new IOException("Too many wheel profiles.");
        for (String profile : names(rawProfiles)) {
            JSONObject rawWheels = rawProfiles.getJSONObject(profile); Map<String, List<String>> wheels = new LinkedHashMap<>();
            if (rawWheels.length() > WheelLayout.MAX_WHEELS) throw new IOException("Too many saved wheels.");
            for (String wheel : names(rawWheels)) {
                JSONArray rawIds = rawWheels.getJSONArray(wheel); java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                if (rawIds.length() > WheelLayout.MAX_ENTRIES) throw new IOException("Too many wheel commands.");
                for (int i = 0; i < rawIds.length(); i++) { Object id = rawIds.get(i); if (!(id instanceof String)) throw new IOException("Expected a stable command ID."); ids.add((String) id); }
                wheels.put(wheel, ids);
            }
            profiles.put(profile, wheels);
        }
        return new WheelLayout(profiles);
    }
    private static PreferenceResets decodePreferenceResets(JSONObject json) throws IOException, JSONException {
        schema(json, PreferenceResets.SCHEMA_VERSION, "Preference resets"); JSONObject raw = json.getJSONObject("sections"); Map<String, String> values = new LinkedHashMap<>();
        for (String section : names(raw)) values.put(section, text(raw, section)); return new PreferenceResets(values);
    }
    private static Map<String, Object> encodeStick(DeviceCalibration.Stick value) {
        return Map.of("centerX", value.centerX, "centerY", value.centerY, "inner", value.inner, "outer", value.outer, "gamma", value.gamma, "invertX", value.invertX, "invertY", value.invertY);
    }
    private static DeviceCalibration.Stick decodeStick(JSONObject value) throws JSONException {
        return new DeviceCalibration.Stick((float) value.getDouble("centerX"), (float) value.getDouble("centerY"), (float) value.getDouble("inner"), (float) value.getDouble("outer"), (float) value.getDouble("gamma"), value.getBoolean("invertX"), value.getBoolean("invertY"));
    }
    private static DeviceCalibration.Trigger decodeTrigger(JSONObject value) throws JSONException { return new DeviceCalibration.Trigger((float) value.getDouble("min"), (float) value.getDouble("max")); }
    private static void schema(JSONObject json, int supported, String document) throws IOException, JSONException {
        Object raw = json.get("schemaVersion");
        if (!(raw instanceof Number) || !Double.isFinite(((Number) raw).doubleValue()) || ((Number) raw).doubleValue() != ((Number) raw).intValue()) throw new IOException(document + " has an invalid schema version.");
        int version = ((Number) raw).intValue();
        if (version > supported) throw new FutureSchemaException(document, version);
        if (version != supported) throw new IOException(document + " uses unsupported schema " + version + ".");
    }
    private static String text(JSONObject json, String name) throws JSONException, IOException {
        Object value = json.get(name); if (!(value instanceof String)) throw new IOException("Expected text for " + name + "."); return (String) value;
    }
    private static String[] names(JSONObject json) { String[] names = JSONObject.getNames(json); return names == null ? new String[0] : names; }
}
