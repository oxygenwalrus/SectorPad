package sectorpad.settings;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import sectorpad.diagnostics.Diagnostics;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Game-thread settings owner. Preview changes release/rearm through Listener before taking effect. */
public final class SettingsService implements LunaSettingsListener, AutoCloseable {
    public static final long PREVIEW_NANOS = 15_000_000_000L;
    public interface Source {
        ControllerSettings settings();
        BindingProfile bindings();
        default boolean usesLuna() { return false; }
    }
    public interface Listener {
        /** The owner must release all mod-held input and require neutral input on this callback. */
        void changed(ControllerSettings settings, BindingProfile profile);
        default void statusChanged(String status) { }
        default void reconnectController() { }
        default String controllerStatus() { return "Controller discovery unavailable"; }
    }
    private static final Source LUNA_SOURCE = new Source() {
        public ControllerSettings settings() { return ControllerSettings.load(); }
        public BindingProfile bindings() { return ControllerSettings.loadBindings(); }
        public boolean usesLuna() { return true; }
    };
    private final ProfileStore store;
    private final Source source;
    private final LongSupplier clock;
    private ControllerSettings settings = ControllerSettings.defaults();
    private ProfileStore.State state = ProfileStore.State.defaults();
    private BindingProfile preview;
    private long previewStarted;
    private String observedFingerprint = "", status = "Saved controls are ready.", deviceId;
    private DeviceCalibration calibration = DeviceCalibration.defaults();
    private WheelLayout wheelLayout = WheelLayout.defaults();
    private PreferenceResets preferenceResets = PreferenceResets.defaults();
    private ControllerSettings nativeSettings = ControllerSettings.defaults();
    private boolean preferenceStorageBlocked;
    private boolean wheelStorageBlocked;
    private final Map<String, RegisteredWheel> knownWheels = new LinkedHashMap<>();
    public record WheelCatalog(String id, String label) { }
    public record WheelItem(String id, String label, boolean available) { }
    private static final class RegisteredWheel {
        String label;
        final Map<String, String> entries = new LinkedHashMap<>();
        List<String> available = List.of();
    }
    private boolean initialized, attached, storageBlocked;
    private Listener listener = (prefs, profile) -> { };

    public SettingsService(ProfileStore store) { this(store, LUNA_SOURCE, System::nanoTime); }
    public SettingsService(ProfileStore store, Source source, LongSupplier clock) { this.store = Objects.requireNonNull(store); this.source = Objects.requireNonNull(source); this.clock = Objects.requireNonNull(clock); }
    public String controllerStatus() { return listener.controllerStatus(); }
    public void reconnectController() {
        revert("Reconnecting controller; saved controls retained.");
        listener.reconnectController();
        report("Controller discovery restarted. Release controls; then press A when prompted.");
    }
    public void setListener(Listener listener) { this.listener = Objects.requireNonNull(listener); }
    public void initialize() {
        if (initialized) return;
        initialized = true;
        try { state = store.load(); if (!store.warning().isEmpty()) report(store.warning()); }
        catch (IOException failure) { Diagnostics.error("settings.load_failed", failure); storageBlocked = true; report("Settings storage is unavailable: " + failure.getMessage()); }
        try { wheelLayout = store.loadWheelLayout(); if (!store.warning().isEmpty()) report(store.warning()); }
        catch (IOException failure) { Diagnostics.error("settings.wheel_load_failed", failure); wheelStorageBlocked = true; report("Saved wheel layout is unavailable: " + failure.getMessage()); }
        try { preferenceResets = store.loadPreferenceResets(); if (!store.warning().isEmpty()) report(store.warning()); }
        catch (IOException failure) { Diagnostics.error("settings.resets_load_failed", failure); preferenceStorageBlocked = true; report("Saved section resets are unavailable: " + failure.getMessage()); }
        observedFingerprint = state.nativeBindingFingerprint;
        // Adding a context changes the fingerprint even when Luna was untouched.
        // Preserve every migrated profile, including custom Luna mappings on old installations.
        if(state.migratedRefitBindings)try{observedFingerprint=ProfileStore.fingerprint(source.bindings());state=state.withFingerprint(observedFingerprint);}
        catch(IllegalArgumentException invalid){report("Saved refit bindings migrated; invalid Luna edits were left unapplied.");}
        nativeSettings = source.settings(); settings = preferenceResets.apply(nativeSettings);
        if (source.usesLuna()) { LunaSettings.addSettingsListener(this); attached = true; }
        refresh();
    }
    @Override public void settingsChanged(String modID) {
        if (!initialized || !ControllerSettings.MOD_ID.equals(modID)) return;
        try { refresh(); }
        catch (RuntimeException | LinkageError failure) { sectorpad.game.RuntimeHooks.fail("runtime.luna_settings", failure); }
    }
    /** Also call after loading/new-game creation, because Luna skips its listener in new-game configuration. */
    public void refresh() {
        ControllerSettings old = settings;
        nativeSettings = source.settings();
        PreferenceResets reconciled = preferenceResets.reconcile(nativeSettings);
        if (!reconciled.equals(preferenceResets)) {
            preferenceResets = reconciled;
            try { store.savePreferenceResets(reconciled); }
            catch (IOException failure) { Diagnostics.error("settings.reset_reconcile_failed", failure); report("Luna values resumed, but the reset markers could not be saved: " + failure.getMessage()); }
        }
        settings = preferenceResets.apply(nativeSettings);
        if (deviceId == null) calibration = settings.calibrationDefaults();
        else loadCalibration();
        listener.changed(settings, activeProfile());
        BindingProfile nativeBindings;
        try { nativeBindings = source.bindings(); }
        catch (IllegalArgumentException invalid) { report("Luna binding edits were not applied: " + invalid.getMessage()); return; }
        String fingerprint = ProfileStore.fingerprint(nativeBindings);
        boolean nativeChanged = !fingerprint.equals(observedFingerprint);
        boolean firstNativeRead = observedFingerprint.isEmpty();
        if (nativeChanged) {
            observedFingerprint = fingerprint;
            boolean matchesDefault = fingerprint.equals(ProfileStore.fingerprint(BindingProfile.defaults()));
            if (!(firstNativeRead && matchesDefault) && !fingerprint.equals(ProfileStore.fingerprint(activeProfile()))) {
                preview(nativeBindings.renamed(state.activeId, state.active().displayName), clock.getAsLong());
                report("Luna mapping preview: confirm within 15 seconds in Controller Setup. Unconfirmed edits revert.");
                return;
            }
        }
        if (!old.profileId.equals(settings.profileId)) {
            BindingProfile selected = state.profiles.get(settings.profileId);
            if (selected == null) report("No saved profile named '" + settings.profileId + "'. Select a saved profile in Controller Setup.");
            else if (!selected.equals(activeProfile())) preview(selected, clock.getAsLong());
        }
    }
    public ControllerSettings settings() { return settings; }
    public BindingProfile activeProfile() { return preview == null ? state.active() : preview; }
    public BindingProfile committedProfile() { return state.active(); }
    public List<BindingProfile> profiles() { return List.copyOf(state.profiles.values()); }
    public ProfileStore store() { return store; }
    public String status() { return status; }
    public boolean isPreviewing() { return preview != null; }
    public boolean isStorageBlocked() { return storageBlocked; }
    public double previewSecondsRemaining() { return preview == null ? 0 : Math.max(0, (PREVIEW_NANOS - (clock.getAsLong() - previewStarted)) / 1_000_000_000d); }
    public void preview(BindingProfile profile, long nowNanos) {
        Objects.requireNonNull(profile); preview = profile; previewStarted = nowNanos;
        listener.changed(settings, profile); report("Test " + profile.displayName + ". Confirm within 15 seconds to keep these controls.");
    }
    public void preview(BindingProfile profile) { preview(profile, clock.getAsLong()); }
    public void advance(long nowNanos) { if (preview != null && nowNanos - previewStarted >= PREVIEW_NANOS) revert("The 15-second preview expired. Previous controls restored."); }
    public void advance() { advance(clock.getAsLong()); }
    public boolean confirm() {
        advance(); if (preview == null) return false;
        ProfileStore.State next;
        try { next = state.withActive(preview, observedFingerprint); }
        catch (IllegalArgumentException invalid) { report("Settings were not committed: " + invalid.getMessage()); return false; }
        if (!persist(next)) return false;
        state = next; preview = null; listener.changed(settings, state.active()); report("Saved " + state.active().displayName + "."); return true;
    }
    public void revert(String reason) {
        boolean wasPreviewing = preview != null; preview = null;
        if (wasPreviewing) listener.changed(settings, state.active());
        if (!state.nativeBindingFingerprint.equals(observedFingerprint)) {
            ProfileStore.State next = state.withFingerprint(observedFingerprint);
            if (persist(next)) state = next;
        }
        if (wasPreviewing) report(reason);
    }
    public void restoreDefaults() { preview(BindingProfile.defaults()); }
    public void previewResetBindings(String context) { preview(activeProfile().resetContext(context)); }
    public boolean activateProfile(String id) {
        BindingProfile profile = state.profiles.get(id);
        if (profile == null) { report("That profile no longer exists."); return false; }
        preview(profile); return true;
    }
    public BindingProfile duplicateCurrentProfile() {
        String id; int number = 1;
        do { id = "custom-" + number++; } while (state.profiles.containsKey(id) || wheelLayout.profiles().containsKey(id));
        BindingProfile copy = activeProfile().renamed(id, "Custom " + (number - 1)); preview(copy); return copy;
    }
    public boolean deleteProfile(String id) {
        try {
            ProfileStore.State next = state.without(id); if (!persist(next)) return false; state = next;
            if (wheelLayout.profiles().containsKey(id)) {
                WheelLayout cleaned = wheelLayout.resetProfile(id);
                try { store.saveWheelLayout(cleaned); wheelLayout = cleaned; }
                catch (IOException failure) { Diagnostics.error("settings.profile_wheel_cleanup_failed", failure); report("Profile deleted. Its inactive wheel layout could not yet be removed: " + failure.getMessage()); return true; }
            }
            report("Profile deleted."); return true;
        }
        catch (IllegalArgumentException invalid) { report(invalid.getMessage()); return false; }
    }
    public boolean importProfile(Path source) {
        try { BindingProfile candidate = store.importProfile(source); preview(candidate); return true; }
        catch (IOException | IllegalArgumentException invalid) { if (invalid instanceof IOException) Diagnostics.error("settings.profile_import_failed", invalid); report("Profile import was not applied: " + invalid.getMessage()); return false; }
    }
    public Path exportCurrentProfile() {
        try { Path result = store.exportProfile(state.active()); report("Exported the committed profile to the SectorPad exports folder."); return result; }
        catch (IOException failure) { Diagnostics.error("settings.profile_export_failed", failure); report("Profile export failed: " + failure.getMessage()); return null; }
    }
    public void setDevice(String deviceId) {
        if (Objects.equals(this.deviceId, deviceId)) return;
        if (preview != null) revert("The active controller changed. Previous controls restored.");
        this.deviceId = deviceId; loadCalibration(); listener.changed(settings, activeProfile());
    }
    public DeviceCalibration calibration() { return calibration; }
    public boolean saveCalibration(DeviceCalibration candidate) {
        if (deviceId == null) { report("Connect a controller before saving device calibration."); return false; }
        try { store.saveCalibration(deviceId, candidate); calibration = candidate; listener.changed(settings, activeProfile()); report("Saved calibration for this controller."); return true; }
        catch (IOException failure) { Diagnostics.error("settings.calibration_save_failed", failure); report("Calibration could not be saved: " + failure.getMessage()); return false; }
    }
    public boolean applyLunaCalibrationToDevice() { return saveCalibration(settings.calibrationDefaults()); }
    public boolean resetCalibration(DeviceCalibration.Section section) { return saveCalibration(calibration.reset(section)); }
    public java.util.Set<PreferenceResets.Section> activePreferenceResets() { return preferenceResets.sections(); }
    public boolean resetPreferenceSection(PreferenceResets.Section section) { return persistPreferenceResets(preferenceResets.reset(section, nativeSettings), "Factory defaults are active for " + section.label + ". Edit that Luna section or choose Use Luna values to resume its fields."); }
    public boolean useLunaPreferenceSection(PreferenceResets.Section section) { return persistPreferenceResets(preferenceResets.clear(section), "Using Luna values for " + section.label + "."); }
    private boolean persistPreferenceResets(PreferenceResets candidate, String success) {
        if (preferenceStorageBlocked) { report("Section resets cannot be saved until their storage problem is resolved."); return false; }
        try {
            store.savePreferenceResets(candidate); preferenceResets = candidate; settings = candidate.apply(nativeSettings);
            listener.changed(settings, activeProfile()); report(success); return true;
        } catch (IOException failure) { Diagnostics.error("settings.resets_save_failed", failure); report("Section reset was not committed: " + failure.getMessage()); return false; }
    }
    /** Apply at wheel construction. Stable IDs include the logical context, never a transient screen/ship identity. */
    public <T> List<T> orderWheel(String stableWheelId, String label, List<T> entries, Function<T, String> id, Function<T, String> entryLabel) {
        Objects.requireNonNull(entries); Objects.requireNonNull(id); Objects.requireNonNull(entryLabel);
        try {
            List<T> ordered = wheelLayout.apply(activeProfile().id, stableWheelId, entries, id);
            RegisteredWheel registered = knownWheels.get(stableWheelId);
            if (registered == null) {
                if (knownWheels.size() >= WheelLayout.MAX_WHEELS) throw new IllegalArgumentException("Too many wheel catalogs.");
                registered = new RegisteredWheel(); knownWheels.put(stableWheelId, registered);
            }
            Map<String, String> observed = new LinkedHashMap<>(registered.entries);
            List<String> available = new ArrayList<>();
            for (T entry : entries) { String key = id.apply(entry); available.add(key); observed.put(key, safeLabel(entryLabel.apply(entry), key)); }
            if (observed.size() > WheelLayout.MAX_ENTRIES) throw new IllegalArgumentException("Too many commands in this wheel catalog.");
            registered.label = safeLabel(label, stableWheelId); registered.entries.clear(); registered.entries.putAll(observed); registered.available = List.copyOf(available);
            return ordered;
        } catch (IllegalArgumentException invalid) { report("Wheel order was not applied: " + invalid.getMessage()); return List.copyOf(entries); }
    }
    private static String safeLabel(String label, String fallback) { return label == null || label.isBlank() || label.length() > 160 || label.codePoints().anyMatch(Character::isISOControl) ? fallback : label; }
    public List<WheelCatalog> wheelCatalogs() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(knownWheels.keySet()); ids.addAll(wheelLayout.wheels(activeProfile().id).keySet());
        List<WheelCatalog> result = new ArrayList<>();
        for (String id : ids) { RegisteredWheel catalog = knownWheels.get(id); result.add(new WheelCatalog(id, catalog == null || catalog.label == null ? id : catalog.label + " [" + id + "]")); }
        return List.copyOf(result);
    }
    public List<WheelItem> wheelEntries(String wheelId) {
        WheelLayout.validateWheelId(wheelId); RegisteredWheel catalog = knownWheels.get(wheelId);
        LinkedHashSet<String> ids = new LinkedHashSet<>(wheelLayout.order(activeProfile().id, wheelId));
        if (catalog != null) ids.addAll(catalog.entries.keySet());
        List<WheelItem> result = new ArrayList<>();
        for (String id : ids) result.add(new WheelItem(id, catalog == null ? id : catalog.entries.getOrDefault(id, id), catalog != null && catalog.available.contains(id)));
        return List.copyOf(result);
    }
    public List<String> defaultWheelEntryIds(String wheelId) {
        WheelLayout.validateWheelId(wheelId); RegisteredWheel catalog = knownWheels.get(wheelId);
        return catalog == null ? List.of() : List.copyOf(catalog.entries.keySet());
    }
    public boolean saveWheelOrder(String wheelId, List<String> ids) {
        if (isPreviewing()) { report("Confirm or revert the profile preview before saving wheel order."); return false; }
        try {
            List<String> validated = WheelLayout.validatedEntries(ids);
            LinkedHashSet<String> existing = new LinkedHashSet<>(); wheelEntries(wheelId).forEach(item -> existing.add(item.id()));
            if (!existing.equals(new LinkedHashSet<>(validated))) throw new IllegalArgumentException("The wheel order must contain every known command exactly once.");
            return persistWheelLayout(wheelLayout.withOrder(state.activeId, wheelId, validated), "Saved this wheel order for " + state.active().displayName + ".");
        } catch (IllegalArgumentException invalid) { report("Wheel order was not saved: " + invalid.getMessage()); return false; }
    }
    public boolean resetWheelOrder(String wheelId) {
        if (isPreviewing()) { report("Confirm or revert the profile preview before resetting wheel order."); return false; }
        WheelLayout.validateWheelId(wheelId);
        return persistWheelLayout(wheelLayout.reset(state.activeId, wheelId), "Restored the catalog order for this wheel.");
    }
    public boolean resetAllWheelOrders() {
        if (isPreviewing()) { report("Confirm or revert the profile preview before resetting wheel order."); return false; }
        return persistWheelLayout(wheelLayout.resetProfile(state.activeId), "Restored all wheel orders for " + state.active().displayName + ".");
    }
    private boolean persistWheelLayout(WheelLayout candidate, String success) {
        if (wheelStorageBlocked) { report("Wheel order cannot be saved until its storage problem is resolved."); return false; }
        try { store.saveWheelLayout(candidate); wheelLayout = candidate; listener.changed(settings, activeProfile()); report(success); return true; }
        catch (IOException failure) { Diagnostics.error("settings.wheel_save_failed", failure); report("Wheel order was not committed: " + failure.getMessage()); return false; }
    }
    private void loadCalibration() {
        calibration = settings.calibrationDefaults();
        if (deviceId != null) {
            try { calibration = store.calibrationFor(deviceId, calibration); }
            catch (IOException failure) { Diagnostics.error("settings.calibration_load_failed", failure); report("Saved calibration could not be loaded; bounded Luna defaults are active. " + failure.getMessage()); }
        }
    }
    private boolean persist(ProfileStore.State candidate) {
        if (storageBlocked) { report("Settings cannot be saved until the storage problem is resolved. " + store.directory().getFileName()); return false; }
        try { store.save(candidate); return true; }
        catch (IOException failure) { Diagnostics.error("settings.save_failed", failure); report("Settings were not committed: " + failure.getMessage()); return false; }
    }
    public void report(String text) { status = text; listener.statusChanged(text); }
    @Override public void close() {
        try { if (preview != null) revert("Controller Setup closed. Previous controls restored."); }
        finally {
            try { if (attached) LunaSettings.removeSettingsListener(this); }
            finally { attached = false; initialized = false; listener = (prefs, profile) -> { }; }
        }
    }
}
