package sectorpad.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Headless regression tests; does not initialize Starsector, Luna UI, or native controllers. */
public final class SettingsTests {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        // Failure fixtures exercise diagnostics without opening the game's file appender.
        System.setProperty("log4j.defaultInitOverride", "true");
        org.apache.log4j.Logger.getRootLogger().addAppender(new org.apache.log4j.varia.NullAppender());
        profiles(); tuning(); capture(); calibration(); persistence(); commonStorage(); preview(); wheelLayouts(); sectionResets(); refitMigration();
        if (args.length > 0) csv(Path.of(args[0]));
        System.out.println("SettingsTests: " + assertions + " assertions passed (headless; no game/controller claim).");
    }
    private static void profiles() {
        BindingProfile standard = BindingProfile.defaults();
        check(standard.contexts().size() == 7, "All contexts available");
        check(standard.binding("UI", "ui.confirm").equals("A"), "Standard confirm");
        check(standard.binding("COMBAT", "combat.fire").equals("RT"), "Standard fire");
        check(standard.contexts().values().stream().mapToInt(Map::size).sum() == 126, "Complete action catalog");
        Map<String,Map<String,String>> unordered=new HashMap<>();standard.contexts().forEach((context,bindings)->unordered.put(context,new HashMap<>(bindings)));
        BindingProfile loadedOrder=new BindingProfile("loaded","Loaded",unordered);
        for(String context:BindingProfile.CONTEXTS)check(new ArrayList<>(loadedOrder.bindings(context).keySet()).equals(new ArrayList<>(standard.bindings(context).keySet())),"Native remapping rows keep their order after unordered JSON imports");
        BindingProfile swapped = standard.withSwap("UI", "ui.confirm", "B");
        check(swapped.binding("UI", "ui.confirm").equals("B") && swapped.binding("UI", "ui.cancel").equals("A"), "Conflicting swap is atomic");
        check(swapped.binding("MAP", "ui.confirm").equals("A"), "Reuse and remapping are context-local");
        expect(IllegalArgumentException.class, () -> standard.withBinding("UI", "ui.confirm", "B"));
        expect(IllegalArgumentException.class, () -> standard.withBinding("UI", "ui.cancel", "NONE"));
        expect(IllegalArgumentException.class, () -> standard.withBinding("UI", "ui.pointer", "A"));
        expect(IllegalArgumentException.class, () -> standard.withBinding("COMBAT", "combat.fire", "GUIDE"));
        expect(IllegalArgumentException.class, () -> standard.withBinding("COMBAT", "fake.action", "NONE"));
        expect(IllegalArgumentException.class, () -> standard.renamed("../escape", "Escape"));
        expect(IllegalArgumentException.class, () -> standard.renamed("bad", "new\nline"));
        expect(UnsupportedOperationException.class, () -> standard.bindings("UI").put("ui.confirm", "X"));
        expect(UnsupportedOperationException.class, () -> standard.contexts().clear());
        check(BindingProfile.southpaw().binding("COMBAT", "combat.move").equals("RIGHT_STICK"), "Southpaw vector profile");
        check(standard.withSwap("UI", "ui.pointer", "RIGHT_STICK").binding("UI", "ui.scroll").equals("LEFT_STICK"), "Vector swap preserves both axes");
        Map<String, Map<String, String>> incomplete = standard.mutableCopy(); incomplete.remove("DEPLOYMENT");
        expect(IllegalArgumentException.class, () -> new BindingProfile("bad", "Bad", incomplete));
    }
    private static void refitMigration() throws Exception {
        Path dir=Files.createTempDirectory("sectorpad-refit-migration-");ProfileStore store=new ProfileStore(dir);
        BindingProfile custom=BindingProfile.southpaw().withSwap("UI","ui.confirm","B").renamed("custom","Custom");
        store.save(ProfileStore.State.defaults().withActive(custom,ProfileStore.fingerprint(BindingProfile.defaults())));
        Path file=dir.resolve("profiles.json");var document=new org.json.JSONObject(Files.readString(file));
        var profiles=document.getJSONObject("profiles");
        for(String id:List.of("default","southpaw","custom")){var profile=profiles.getJSONObject(id);profile.put("schemaVersion",1);profile.getJSONObject("contexts").remove("REFIT");}
        Files.writeString(file,document.toString());String original=Files.readString(file);
        var migrated=store.load();
        check(migrated.activeId.equals("custom"),"Migration retains active profile");
        for(var profile:migrated.profiles.values())check(profile.bindings("REFIT").equals(profile.bindings("UI")),"Each existing profile inherits its own UI bindings");
        check(migrated.active().binding("REFIT","ui.confirm").equals("B"),"Custom confirm preserved in refit");
        check(migrated.active().binding("COMBAT","combat.move").equals(custom.binding("COMBAT","combat.move")),"Migration leaves combat mapping intact");
        check(Files.readString(file).equals(original),"Read migration preserves original storage");
        SettingsService service=new SettingsService(store,new MutableSource(),()->1L);service.initialize();
        check(!service.isPreviewing()&&service.activeProfile().equals(migrated.active()),"New default Luna context does not reset existing custom profile");service.close();
        MutableSource nondefault=new MutableSource();nondefault.bindings=BindingProfile.defaults().withSwap("UI","ui.confirm","Y");
        SettingsService existingLuna=new SettingsService(store,nondefault,()->1L);existingLuna.initialize();
        check(!existingLuna.isPreviewing()&&existingLuna.activeProfile().equals(migrated.active()),"Existing nondefault Luna values do not overwrite migrated profiles");existingLuna.close();
        Path exported=store.exportProfile(migrated.active());
        check(store.importProfile(exported).equals(migrated.active()),"Migrated v2 export round trip");
        var future=new org.json.JSONObject(Files.readString(exported));future.put("schemaVersion",3);Files.writeString(exported,future.toString());
        expect(ProfileStore.FutureSchemaException.class,()->store.importProfile(exported));
    }
    private static void tuning() {
        Values values = new Values(); values.numbers.put("sp_pointer_speed", Double.NaN); values.numbers.put("sp_pointer_gamma", 999d);
        values.numbers.put("sp_trigger_press", .2); values.numbers.put("sp_trigger_release", .9); values.ints.put("sp_controller_index", 100);
        values.strings.put("sp_glyphs", "PlayStation"); values.strings.put("sp_wheel_slots", "7");
        ControllerSettings settings = ControllerSettings.from(values);
        check(settings.controllerBackend.equals("Automatic"), "Missing backend preference preserves automatic discovery");
        values.strings.put("sp_controller_backend", "XInput");
        check(ControllerSettings.from(values).controllerBackend.equals("XInput"), "Wine compatibility backend persists in Luna settings");
        values.strings.put("sp_controller_backend", "bad");
        check(ControllerSettings.from(values).controllerBackend.equals("Automatic"), "Unknown backend values safely default");
        values.strings.put("sp_gyro_mode", "Aim + pointer"); values.strings.put("sp_gyro_activation", "Always");
        values.numbers.put("sp_gyro_sensitivity", .02); values.numbers.put("sp_gyro_smoothing", .7);
        ControllerSettings gyro=ControllerSettings.from(values);
        check(gyro.gyroMode.equals("Aim + pointer")&&gyro.gyroActivation.equals("Always"), "Gyro mode and activation are validated");
        check(Math.abs(gyro.gyroSensitivity-.02f)<.0001f&&Math.abs(gyro.gyroSmoothing-.7f)<.0001f, "Gyro response tuning is bounded");
        values.strings.put("sp_gyro_mode", "invalid"); values.strings.put("sp_gyro_activation", "invalid");
        ControllerSettings invalidGyro=ControllerSettings.from(values);
        check(invalidGyro.gyroMode.equals("Off")&&invalidGyro.gyroActivation.equals("Hold LT"), "Invalid gyro choices fail closed");
        check(settings.pointerSpeed == 900 && settings.pointerGamma == 3, "Nonfinite tuning defaults and ranges clamp");
        check(settings.triggerRelease <= settings.triggerPress - .049f, "Trigger hysteresis is always maintained");
        check(settings.controllerIndex == 7 && settings.glyphStyle.equals("Automatic") && settings.wheelSlots == 8, "Unsupported selectors fall back safely");
        check(ControllerSettings.bindingsFrom(values).contexts().equals(BindingProfile.defaults().contexts()), "Missing native rows use canonical defaults");
        check(settings.hubKeycode == 66 && settings.keyboardKeycode == 67 && settings.remapKeycode == 68, "Keyboard hub, keyboard and setup defaults are F8, F9 and F10");
        values.ints.put("sp_hub_key", 0); values.ints.put("sp_remap_key", 0); values.ints.put("sp_keyboard_key", 0);
        ControllerSettings cleared = ControllerSettings.from(values);
        check(cleared.hubKeycode == 0 && cleared.remapKeycode == 0 && cleared.keyboardKeycode == 0, "Clearing native shortcuts does not bind Escape");
        values.strings.put(BindingProfile.settingId("UI", "ui.confirm"), "B");
        expect(IllegalArgumentException.class, () -> ControllerSettings.bindingsFrom(values));
    }
    private static void capture() {
        RemapCapture capture = new RemapCapture();
        capture.begin(BindingProfile.defaults(), "UI", "ui.confirm", 0);
        sample(capture, Set.of("A"), 0, 0, 100_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_NEUTRAL, "Opening button cannot become a binding");
        sample(capture, Set.of(), 0, 0, 200_000_000L); sample(capture, Set.of(), 0, 0, 399_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_NEUTRAL, "Neutral state must remain stable");
        sample(capture, Set.of(), 0, 0, 400_000_000L); sample(capture, Set.of("B"), 0, 0, 500_000_000L);
        check(capture.stage() == RemapCapture.Stage.WAIT_RELEASE, "Button capture waits for release");
        sample(capture, Set.of(), 0, 0, 600_000_000L);
        check(capture.stage() == RemapCapture.Stage.CONFLICT && capture.conflict().equals("ui.cancel"), "Conflict identifies same-context owner");
        capture.swapConflict(); check(capture.result().binding("UI", "ui.confirm").equals("B"), "Conflict can be deliberately swapped");
        capture.begin(BindingProfile.defaults(), "UI", "ui.pointer", 0);
        sample(capture, Set.of(), 0, 0, 0); sample(capture, Set.of(), 0, 0, 200_000_000L);
        sample(capture, Set.of(), 0, .8f, 300_000_000L); sample(capture, Set.of(), 0, 0, 400_000_000L);
        check(capture.stage() == RemapCapture.Stage.CONFLICT && capture.control().equals("RIGHT_STICK"), "Vector input captures the intended stick");
        capture.begin(BindingProfile.defaults(), "COMBAT", "combat.fire", 0);
        sample(capture, Set.of(), 0, 0, 0); sample(capture, Set.of(), 0, 0, 200_000_000L);
        sample(capture, Set.of("GUIDE"), 0, 0, 300_000_000L);
        check(capture.stage() == RemapCapture.Stage.CAPTURING, "Guide is never captured");
        sample(capture, Set.of(), 0, 0, 15_000_000_000L); check(capture.stage() == RemapCapture.Stage.CANCELLED, "Capture timeout keeps existing mapping");
        capture.begin(BindingProfile.defaults(), "UI", "ui.confirm", 0); sample(capture, Set.of(), Float.NaN, 0, 100);
        check(capture.stage() == RemapCapture.Stage.CANCELLED, "Invalid controller samples cancel safely");
        capture.begin(BindingProfile.defaults(), "COMBAT", "combat.fire", 0);
        sample(capture, Set.of(), 0, 0, 0); sample(capture, Set.of(), 0, 0, 200_000_000L);
        capture.sample(Set.of(), 0, 0, 0, 0, 0, 1, 300_000_000L);
        capture.sample(Set.of(), 0, 0, 0, 0, 0, 0, 400_000_000L);
        check(capture.stage() == RemapCapture.Stage.READY && capture.result().binding("COMBAT", "combat.fire").equals("RT"), "Raw trigger capture works without a synthesized button");
    }
    private static void sample(RemapCapture capture, Set<String> down, float leftX, float rightX, long now) { capture.sample(down, leftX, 0, rightX, 0, 0, 0, now); }
    private static void calibration() {
        DeviceCalibration.Stick stick = new DeviceCalibration.Stick(.05f, -.03f, .12f, .98f, 1.6f, false, false);
        float[] atRest = stick.apply(.05f, -.03f); check(atRest[0] == 0 && atRest[1] == 0, "Center correction removes resting drift");
        Random random = new Random(14);
        for (int i = 0; i < 2000; i++) {
            float[] value = stick.apply(random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2);
            check(Float.isFinite(value[0]) && Float.isFinite(value[1]) && Math.hypot(value[0], value[1]) <= 1.000001, "Radial output stays finite and within unit circle");
        }
        check(stick.apply(Float.POSITIVE_INFINITY, 0)[0] == 0, "Bad axes are neutral");
        DeviceCalibration.Trigger trigger = new DeviceCalibration.Trigger(.1f, .9f);
        check(trigger.apply(.1f) == 0 && trigger.apply(.9f) == 1 && Math.abs(trigger.apply(.5f) - .5f) < .00001, "Trigger calibration normalizes endpoints and midpoint");
        expect(IllegalArgumentException.class, () -> new DeviceCalibration.Stick(0, 0, .8f, .5f, 1, false, false));
    }
    private static void persistence() throws Exception {
        Path dir = Files.createTempDirectory("sectorpad-settings-test-"); ProfileStore store = new ProfileStore(dir);
        ProfileStore.State original = store.load(); check(original.activeId.equals("default"), "New user gets standard profile");
        String nativeStamp = ProfileStore.fingerprint(BindingProfile.defaults()); store.save(original.withFingerprint(nativeStamp));
        BindingProfile custom = BindingProfile.defaults().withSwap("UI", "ui.confirm", "B").renamed("custom", "My controls");
        ProfileStore.State changed = original.withActive(custom, nativeStamp); store.save(changed);
        check(store.load().active().equals(custom), "Named profile round trip");
        check(Files.exists(dir.resolve("profiles.json.bak")), "Last known-good backup exists");
        Files.writeString(dir.resolve("profiles.json"), "{broken", StandardCharsets.UTF_8);
        check(store.load().activeId.equals("default") && store.warning().contains("backup"), "Corrupt primary recovers valid backup");
        check(Files.readString(dir.resolve("profiles.json")).equals("{broken"), "Recovery read preserves original bytes");
        store.save(changed); check(store.load().active().equals(custom), "Explicit commit can recover damaged storage");
        try (var files = Files.list(dir)) { check(files.anyMatch(file -> file.getFileName().toString().contains("rejected-")), "Damaged file preserved before replacement"); }
        Path exported = store.exportProfile(custom); check(store.importProfile(exported).equals(custom), "Standalone profile import/export round trip");
        Files.createDirectories(dir.resolve("imports")); Files.copy(exported, dir.resolve("imports/import-one.json")); Files.copy(exported, dir.resolve("imports/not a profile.json"));
        check(store.importableFiles().size() == 1 && store.importNamed("import-one").equals(custom), "Controller import list is limited to supported file names");
        byte[] beforeBusyWrite = Files.readAllBytes(dir.resolve("profiles.json"));
        try (var channel = java.nio.channels.FileChannel.open(dir.resolve("settings.lock"), java.nio.file.StandardOpenOption.WRITE); var held = channel.lock()) {
            check(held.isValid(), "Test owns settings lock"); expect(IOException.class, () -> store.save(changed));
        }
        check(java.util.Arrays.equals(beforeBusyWrite, Files.readAllBytes(dir.resolve("profiles.json"))), "Busy writer fails promptly without changing configuration");
        expect(IOException.class, () -> store.importProfile(dir.resolve("missing.json")));
        Files.writeString(dir.resolve("profiles.json"), "{\"schemaVersion\":2}"); byte[] future = Files.readAllBytes(dir.resolve("profiles.json"));
        expect(ProfileStore.FutureSchemaException.class, store::load);
        expect(ProfileStore.FutureSchemaException.class, () -> store.save(changed));
        check(java.util.Arrays.equals(future, Files.readAllBytes(dir.resolve("profiles.json"))), "Future profile schema is never overwritten");
        store.saveCalibration("controller identity with serial 1234", DeviceCalibration.defaults());
        DeviceCalibration loaded = store.calibrationFor("controller identity with serial 1234", null); check(loaded != null && loaded.left.inner == .12f, "Calibration is keyed per device");
        check(!Files.readString(dir.resolve("calibration.json")).contains("serial"), "Stored calibration omits raw device identity");
        Files.writeString(dir.resolve("calibration.json"), "{\"schemaVersion\":99}");
        expect(ProfileStore.FutureSchemaException.class, () -> store.saveCalibration("other", DeviceCalibration.defaults()));
        Path oversized = dir.resolve("oversized.json"); Files.write(oversized, new byte[(int) ProfileStore.MAX_FILE_BYTES + 1]);
        expect(IOException.class, () -> store.importProfile(oversized));
        expect(IllegalArgumentException.class, () -> store.importNamed("../outside"));
    }
    private static void preview() throws Exception {
        Path dir = Files.createTempDirectory("sectorpad-preview-test-"); ProfileStore store = new ProfileStore(dir); MutableSource source = new MutableSource(); long[] time = {1};
        SettingsService service = new SettingsService(store, source, () -> time[0]); int[] barriers = {0}; service.setListener((settings, profile) -> barriers[0]++); service.initialize();
        check(!service.isPreviewing(), "First startup does not preview unchanged defaults");
        BindingProfile change = BindingProfile.defaults().withSwap("UI", "ui.confirm", "B");
        service.preview(change); time[0] += 14_000_000_000L;
        check(service.confirm() && store.load().active().equals(change), "Confirmed preview commits exactly the tested mapping");
        service.preview(BindingProfile.southpaw()); time[0] += 15_000_000_000L;
        check(!service.confirm() && service.activeProfile().equals(change), "Expired preview cannot be confirmed late");
        source.bindings = BindingProfile.defaults().withSwap("COMBAT", "combat.target", "B"); service.refresh();
        check(service.isPreviewing(), "Native Luna binding edits start a preview");
        time[0] += 15_000_000_000L; service.advance();
        check(!service.isPreviewing() && service.committedProfile().equals(change), "Native preview timeout restores committed profile");
        SettingsService restarted = new SettingsService(store, source, () -> time[0]); restarted.initialize();
        check(!restarted.isPreviewing() && restarted.activeProfile().equals(change), "Rejected native proposal does not reapply after restart");
        service.preview(BindingProfile.southpaw()); service.preview(BindingProfile.defaults()); service.revert("focus lost");
        check(service.activeProfile().equals(change), "Nested previews always revert to committed controls");
        check(barriers[0] >= 8, "Every effective mapping transition notifies the release/rearm owner");
        service.preview(BindingProfile.southpaw()); service.setDevice("xbox-controller");
        check(!service.isPreviewing(), "Device replacement cancels active preview");
        int[] reconnects={0},discoveryTests={0};
        service.setListener(new SettingsService.Listener(){
            public void changed(ControllerSettings prefs,BindingProfile profile){barriers[0]++;}
            public void reconnectController(){check(!service.isPreviewing(), "Reconnect rolls back previews before resetting device");reconnects[0]++;}
            public void testControllerDiscovery(){check(!service.isPreviewing(), "Discovery test rolls back previews before observing devices");discoveryTests[0]++;}
        });
        service.preview(BindingProfile.southpaw()); service.reconnectController();
        check(reconnects[0]==1 && service.activeProfile().equals(change), "Manual discovery restart preserves committed mappings");
        service.preview(BindingProfile.southpaw()); service.testControllerDiscovery();
        check(discoveryTests[0]==1 && service.activeProfile().equals(change), "Discovery test preserves committed mappings");
        service.preview(BindingProfile.southpaw()); service.close(); check(!service.isPreviewing(), "Shutdown cancels uncommitted mapping");
        SettingsService brokenClose = new SettingsService(new ProfileStore(Files.createTempDirectory("sectorpad-close-test-")), new MutableSource(), () -> time[0]);
        brokenClose.initialize(); brokenClose.preview(BindingProfile.southpaw());
        brokenClose.setListener((prefs, profile) -> { throw new IllegalStateException("Failing input owner"); });
        try { brokenClose.close(); throw new AssertionError("Expected failing rollback owner"); }
        catch (IllegalStateException expected) { check(!brokenClose.isPreviewing(), "Failed shutdown callback still clears the preview"); }
        brokenClose.preview(BindingProfile.southpaw()); brokenClose.close();
        check(!brokenClose.isPreviewing(), "Failed close detaches the broken callback before later cleanup");
        Path occupied = Files.createTempFile("sectorpad-storage-failure-", ".file"); SettingsService failing = new SettingsService(new ProfileStore(occupied), new MutableSource(), () -> time[0]); failing.initialize();
        failing.preview(change); check(!failing.confirm() && failing.committedProfile().equals(BindingProfile.defaults()), "Write failure never changes committed controls");
        time[0] += SettingsService.PREVIEW_NANOS; failing.advance(); check(!failing.isPreviewing(), "Failed commit still times out safely");
    }
    private static void commonStorage() throws Exception {
        CommonMemory files = new CommonMemory(); CommonStorage storage = new CommonStorage(files); ProfileStore store = new ProfileStore(storage);
        ProfileStore.State original = ProfileStore.State.defaults().withFingerprint(ProfileStore.fingerprint(BindingProfile.defaults()));
        BindingProfile custom = BindingProfile.southpaw().renamed("custom", "Test common storage"); ProfileStore.State changed = original.withActive(custom, original.nativeBindingFingerprint);
        store.save(original); check(store.load().activeId.equals("default"), "Supported common API round trip");
        check(!store.usedAtomicReplacement(), "Common API never claims atomic filesystem replacement");
        files.failAt = "SectorPad/profiles.json.slot-b"; files.partial = true;
        expect(IOException.class, () -> store.save(changed));
        check(store.load().activeId.equals("default"), "Partial inactive-slot write cannot become active");
        files.failAt = "SectorPad/profiles.json"; files.partial = true;
        expect(IOException.class, () -> store.save(changed));
        check(store.load().activeId.equals("default"), "Partial commit-marker write recovers previously committed state");
        check(store.warning().contains("commit backup"), "Common journal recovery is visible to the owner");
        store.save(changed); check(store.load().active().equals(custom), "Common store recovers after an interrupted commit");
        String marker = files.docs.get("SectorPad/profiles.json");
        String activeSlot = new org.json.JSONObject(marker).getString("slot");
        files.docs.put("SectorPad/profiles.json.slot-" + activeSlot, "{\"generation\":1,\"payload\":\"tampered\",\"sha256\":\"bad\"}");
        check(store.load().activeId.equals("default"), "Snapshot checksum rejects damaged committed payload");
        store.save(changed);
        files.failAt = "SectorPad/profiles.json"; files.partial = false;
        store.save(original); check(store.load().activeId.equals("default"), "An uncertain API return is accepted only after exact commit verification");
        Path exported = store.exportProfile(custom); check(store.importProfile(exported).equals(custom), "Common exports are portable plain profiles");
        String exportedJson = files.docs.get("SectorPad/exports/custom.json"); check(new org.json.JSONObject(exportedJson).has("contexts"), "Export does not expose journal internals");
        files.docs.put("SectorPad/imports/sample.json", exportedJson); files.docs.put("SectorPad/imports/index.json", "{\"files\":[\"sample.json\"]}");
        check(store.importableFiles().size() == 1 && store.importNamed("sample").equals(custom), "Public-API import index supports controller selection");
        expect(IOException.class, () -> store.importProfile(Path.of("outside.json")));
        store.saveCalibration("Xbox common identity", DeviceCalibration.defaults()); check(store.calibrationFor("Xbox common identity", null) != null, "Calibration uses the supported common backend");
        try (Storage.Lease lease = storage.acquire()) { expect(IOException.class, () -> store.save(original)); }
        files.docs.put("SectorPad/profiles.json", "{\"sectorpadJournalVersion\":2}"); String future = files.docs.get("SectorPad/profiles.json");
        expect(ProfileStore.FutureSchemaException.class, store::load); expect(ProfileStore.FutureSchemaException.class, () -> store.save(original));
        check(future.equals(files.docs.get("SectorPad/profiles.json")), "Future common-journal version cannot be overwritten");
        CommonMemory initial = new CommonMemory(); ProfileStore first = new ProfileStore(new CommonStorage(initial)); initial.failAt = "SectorPad/profiles.json"; initial.partial = true;
        expect(IOException.class, () -> first.save(changed)); check(first.load().activeId.equals("default"), "An uncommitted first snapshot is not recovered as active");
    }
    private static final class CommonMemory implements CommonStorage.CommonFiles {
        final Map<String, String> docs = new HashMap<>(); String failAt; boolean partial;
        public boolean exists(String name) { return docs.containsKey(name); }
        public String read(String name) throws IOException { if (!docs.containsKey(name)) throw new IOException("Missing common file"); return docs.get(name); }
        public void write(String name, String text) throws IOException {
            if (name.equals(failAt)) { failAt = null; docs.put(name, partial ? text.substring(0, Math.min(7, text.length())) : text); throw new IOException("Injected common API interruption"); }
            docs.put(name, text);
        }
    }
    private record WheelCommand(String id, String label) { }
    private static void wheelLayouts() throws Exception {
        List<String> catalog = List.of("a", "b", "c", "new");
        WheelLayout layout = WheelLayout.defaults().withOrder("default", "UI:hub", List.of("missing", "c", "a", "b"));
        check(layout.apply("default", "UI:hub", catalog, value -> value).equals(List.of("c", "a", "b", "new")), "Unavailable commands do not reorder known commands and new entries append");
        check(layout.apply("default", "UI:hub", List.of("a", "missing", "c"), value -> value).equals(List.of("missing", "c", "a")), "Returning commands retain their stored positions");
        check(layout.apply("southpaw", "UI:hub", catalog, value -> value).equals(catalog), "Wheel order is profile-local");
        check(layout.apply("default", "COMBAT:hub", catalog, value -> value).equals(catalog), "Wheel order is context-local");
        check(layout.reset("default", "UI:hub").equals(WheelLayout.defaults()), "Reset removes only the chosen wheel override");
        expect(IllegalArgumentException.class, () -> layout.withOrder("default", "UI:hub", List.of("a", "a")));
        expect(IllegalArgumentException.class, () -> layout.withOrder("default", "../bad", List.of("a")));
        expect(IllegalArgumentException.class, () -> layout.withOrder("default", "UI:hub", List.of("bad\ncommand")));
        expect(IllegalArgumentException.class, () -> layout.apply("default", "UI:hub", List.of("a", "a"), value -> value));
        expect(UnsupportedOperationException.class, () -> layout.order("default", "UI:hub").add("extra"));
        CommonMemory files = new CommonMemory(); ProfileStore store = new ProfileStore(new CommonStorage(files));
        store.saveWheelLayout(layout); check(store.loadWheelLayout().equals(layout), "Versioned wheel layout round-trips through supported common storage");
        SettingsService service = new SettingsService(store, new MutableSource(), () -> 1L); service.initialize();
        List<WheelCommand> entries = List.of(new WheelCommand("a", "Alpha"), new WheelCommand("b", "Beta"), new WheelCommand("c", "Gamma"));
        check(service.orderWheel("UI:hub", "Hub", entries, WheelCommand::id, WheelCommand::label).get(0).id().equals("c"), "Runtime API applies the loaded layout");
        check(service.wheelEntries("UI:hub").stream().anyMatch(item -> item.id().equals("missing") && !item.available()), "Editor retains absent IDs and marks availability");
        check(service.wheelCatalogs().get(0).label().contains("UI:hub"), "Editor distinguishes stable context/catalog IDs");
        check(!service.saveWheelOrder("UI:hub", List.of("a", "b", "c")), "Editor cannot drop an unavailable stored command");
        List<String> revised = List.of("a", "missing", "b", "c");
        check(service.saveWheelOrder("UI:hub", revised), "Explicit order save commits a complete permutation");
        check(store.loadWheelLayout().order("default", "UI:hub").equals(revised), "Saved order persists independently of profile bindings");
        service.preview(BindingProfile.southpaw()); check(!service.saveWheelOrder("UI:hub", revised), "Unconfirmed profile cannot accidentally receive wheel writes"); service.revert("test");
        files.failAt = "SectorPad/wheel-layout.json"; files.partial = true;
        check(!service.saveWheelOrder("UI:hub", List.of("c", "missing", "b", "a")), "Interrupted order save reports failure");
        check(service.orderWheel("UI:hub", "Hub", entries, WheelCommand::id, WheelCommand::label).get(0).id().equals("a"), "Failed order save keeps prior runtime layout");
        check(store.loadWheelLayout().order("default", "UI:hub").equals(revised), "Interrupted wheel commit recovers previous committed order");
        check(service.resetWheelOrder("UI:hub") && service.orderWheel("UI:hub", "Hub", entries, WheelCommand::id, WheelCommand::label).equals(entries), "Explicit wheel reset restores catalog order");
        service.preview(BindingProfile.defaults().renamed("wheel-user", "Wheel user")); check(service.confirm(), "A named profile can own a separate wheel layout");
        check(service.saveWheelOrder("UI:hub", List.of("c", "b", "a")), "The named profile saves its own ordering");
        service.preview(BindingProfile.defaults()); check(service.confirm(), "Switching back restores the standard profile");
        check(service.deleteProfile("wheel-user") && !store.loadWheelLayout().profiles().containsKey("wheel-user"), "Deleting a profile removes its inactive wheel ordering");
        files.docs.put("SectorPad/wheel-layout.json", "{\"schemaVersion\":2}");
        expect(ProfileStore.FutureSchemaException.class, store::loadWheelLayout); expect(ProfileStore.FutureSchemaException.class, () -> store.saveWheelLayout(layout));
        check(files.docs.get("SectorPad/wheel-layout.json").equals("{\"schemaVersion\":2}"), "Future wheel schema is preserved");
        SettingsService future = new SettingsService(store, new MutableSource(), () -> 1L); future.initialize();
        check(!future.resetAllWheelOrders(), "Unknown future wheel schema blocks destructive resets");
        ProfileStore disk = new ProfileStore(Files.createTempDirectory("sectorpad-wheel-test-")); disk.saveWheelLayout(layout);
        check(disk.loadWheelLayout().equals(layout), "Headless wheel layout backend matches the runtime schema");
    }
    private static void sectionResets() throws Exception {
        BindingProfile customized = BindingProfile.defaults().withSwap("UI", "ui.confirm", "B").withSwap("COMBAT", "combat.target", "B").renamed("personal", "Personal");
        BindingProfile uiReset = customized.resetContext("UI");
        check(uiReset.id.equals("personal") && uiReset.bindings("UI").equals(BindingProfile.defaultsFor("UI")), "Binding section reset preserves profile identity");
        check(uiReset.bindings("COMBAT").equals(customized.bindings("COMBAT")), "Binding section reset preserves other context edits");
        DeviceCalibration customCalibration = new DeviceCalibration(new DeviceCalibration.Stick(.2f, 0, .3f, .8f, 2, true, false), new DeviceCalibration.Stick(-.1f, 0, .2f, .9f, 1.5f, false, true), new DeviceCalibration.Trigger(.2f, .8f), new DeviceCalibration.Trigger(.1f, .9f));
        DeviceCalibration leftReset = customCalibration.reset(DeviceCalibration.Section.LEFT_STICK);
        check(leftReset.left.centerX == 0 && leftReset.left.inner == .12f && leftReset.right == customCalibration.right, "Left-stick reset preserves right-stick calibration");
        check(leftReset.leftTrigger == customCalibration.leftTrigger && leftReset.rightTrigger == customCalibration.rightTrigger, "Stick reset preserves both triggers");
        DeviceCalibration triggersReset = customCalibration.reset(DeviceCalibration.Section.TRIGGERS);
        check(triggersReset.left == customCalibration.left && triggersReset.leftTrigger.min == 0 && triggersReset.rightTrigger.max == 1, "Trigger reset preserves both stick calibrations");
        Values values = new Values(); values.numbers.put("sp_pointer_speed", 1700d); values.numbers.put("sp_scroll_speed", 24d); values.numbers.put("sp_aim_range", 2500d);
        ControllerSettings nativeSettings = ControllerSettings.from(values);
        PreferenceResets reset = PreferenceResets.defaults().reset(PreferenceResets.Section.POINTER, nativeSettings);
        ControllerSettings effective = reset.apply(nativeSettings);
        check(effective.pointerSpeed == 900 && effective.scrollSpeed == 24 && effective.aimRange == 2500, "Preference reset affects only its named comfort section");
        check(nativeSettings.pointerSpeed == 1700, "Reset never modifies the native Luna snapshot");
        values.numbers.put("sp_scroll_speed", 30d); ControllerSettings unrelated = ControllerSettings.from(values);
        check(reset.reconcile(unrelated).sections().contains(PreferenceResets.Section.POINTER), "Unrelated native edits preserve section resets");
        values.numbers.put("sp_pointer_speed", 1500d); ControllerSettings changed = ControllerSettings.from(values);
        check(reset.reconcile(changed).sections().isEmpty() && reset.apply(changed).pointerSpeed == 1500, "Editing a reset section returns control to Luna values");
        java.util.Set<String> resetFields = new java.util.HashSet<>();
        for (PreferenceResets.Section section : PreferenceResets.Section.values()) for (String field : section.fields) check(resetFields.add(field), "Comfort reset sections do not overlap");
        expect(IllegalArgumentException.class, () -> new PreferenceResets(Map.of("UNKNOWN", ProfileStore.digest("test"))));
        expect(IllegalArgumentException.class, () -> new PreferenceResets(Map.of("POINTER", "bad")));
        CommonMemory files = new CommonMemory(); ProfileStore store = new ProfileStore(new CommonStorage(files));
        store.savePreferenceResets(reset); check(store.loadPreferenceResets().equals(reset), "Preference reset markers round-trip through the common journal");
        MutableSource source = new MutableSource(); source.settings = nativeSettings;
        SettingsService service = new SettingsService(store, source, () -> 1L); service.initialize();
        check(service.settings().pointerSpeed == 900 && service.settings().scrollSpeed == 24, "Saved section defaults survive restart");
        check(service.useLunaPreferenceSection(PreferenceResets.Section.POINTER) && service.settings().pointerSpeed == 1700, "Explicit Use Luna values clears the override");
        files.failAt = "SectorPad/preference-resets.json"; files.partial = true;
        check(!service.resetPreferenceSection(PreferenceResets.Section.POINTER) && service.settings().pointerSpeed == 1700, "Failed reset write preserves effective preferences");
        check(service.resetPreferenceSection(PreferenceResets.Section.POINTER), "Section reset recovers after an interrupted commit");
        source.settings = changed; service.refresh();
        check(service.activePreferenceResets().isEmpty() && service.settings().pointerSpeed == 1500 && store.loadPreferenceResets().sections().isEmpty(), "Native edit clears and persists obsolete reset markers");
        source.settings = nativeSettings; service.refresh();
        check(service.settings().pointerSpeed == 1700, "Returning to an older native value cannot resurrect a cleared reset");
        service.preview(customized); check(service.confirm(), "Customized binding profile committed for section-reset test");
        service.previewResetBindings("UI"); check(service.activeProfile().equals(uiReset) && service.committedProfile().equals(customized), "Binding reset uses the normal uncommitted preview");
        service.revert("test"); check(service.activeProfile().equals(customized), "Unconfirmed section reset fully rolls back");
        service.setDevice("controller-one"); service.saveCalibration(customCalibration); service.resetCalibration(DeviceCalibration.Section.LEFT_STICK);
        DeviceCalibration stored = store.calibrationFor("controller-one", null);
        check(stored.left.centerX == 0 && stored.right.centerX == -.1f, "Device reset persists only its selected calibration section");
        service.setDevice("controller-two"); check(service.calibration().right.centerX == 0, "Section reset does not copy calibration to another device");
        files.docs.put("SectorPad/preference-resets.json", "{\"schemaVersion\":2}");
        expect(ProfileStore.FutureSchemaException.class, store::loadPreferenceResets); expect(ProfileStore.FutureSchemaException.class, () -> store.savePreferenceResets(reset));
        check(files.docs.get("SectorPad/preference-resets.json").equals("{\"schemaVersion\":2}"), "Future preference-reset schema is preserved");
    }
    private static void csv(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8); Map<String, List<String>> rows = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) { List<String> row = csvRow(lines.get(i)); check(row.size() == 9, "CSV row width"); check(rows.put(row.get(0), row) == null, "No duplicate native field IDs"); }
        for (String context : BindingProfile.CONTEXTS) for (Map.Entry<String, String> binding : BindingProfile.defaultsFor(context).entrySet()) {
            List<String> row = rows.get(BindingProfile.settingId(context, binding.getKey()));
            check(row != null && row.get(2).equals("Radio") && row.get(3).equals(binding.getValue()), "Native remapping field matches runtime default");
            check(!row.get(4).contains("GUIDE"), "Guide absent from native choices");
        }
        check(!Files.readString(file).contains("PlayStation") && !Files.readString(file).contains("Nintendo"), "Native menu is scoped to Xbox and Steam Deck");
        Path root = file.toAbsolutePath().getParent().getParent().getParent().getParent();
        String code = Files.readString(root.resolve("src/main/java/sectorpad/settings/ControllerSettings.java"));
        var keys = java.util.regex.Pattern.compile("\"(sp_[a-z0-9_]+)\"").matcher(code);
        while (keys.find()) check(rows.containsKey(keys.group(1)), "Every runtime setting has a native Luna field");
    }
    private static List<String> csvRow(String line) {
        List<String> result = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append(c); i++; } else quoted = !quoted; } else if (c == ',' && !quoted) { result.add(value.toString()); value.setLength(0); } else value.append(c); }
        result.add(value.toString()); return result;
    }
    private static class Values implements ControllerSettings.Values {
        final Map<String, Boolean> bools = new HashMap<>(); final Map<String, Integer> ints = new HashMap<>(); final Map<String, Double> numbers = new HashMap<>(); final Map<String, String> strings = new HashMap<>();
        public Boolean booleanValue(String key) { return bools.get(key); } public Integer intValue(String key) { return ints.get(key); } public Double doubleValue(String key) { return numbers.get(key); } public String stringValue(String key) { return strings.get(key); }
    }
    private static final class MutableSource implements SettingsService.Source {
        BindingProfile bindings = BindingProfile.defaults(); ControllerSettings settings = ControllerSettings.defaults();
        public ControllerSettings settings() { return settings; } public BindingProfile bindings() { return bindings; }
    }
    private interface Throwing { void run() throws Exception; }
    private static void expect(Class<? extends Throwable> type, Throwing action) {
        try { action.run(); throw new AssertionError("Expected " + type.getSimpleName()); }
        catch (Throwable actual) { if (!type.isInstance(actual)) throw new AssertionError("Expected " + type.getSimpleName() + ", got " + actual, actual); assertions++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
