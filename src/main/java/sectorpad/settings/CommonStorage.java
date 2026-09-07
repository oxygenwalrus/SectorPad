package sectorpad.settings;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/** Supported runtime persistence through SettingsAPI common data, with checked dual-slot commits. */
public final class CommonStorage implements Storage {
    public interface CommonFiles {
        boolean exists(String name);
        String read(String name) throws IOException;
        void write(String name, String text) throws IOException;
    }
    private static final Semaphore WRITER = new Semaphore(1);
    private static final String PREFIX = "SectorPad/";
    private final CommonFiles files;
    private String warning = "";
    public CommonStorage() {
        this(new CommonFiles() {
            public boolean exists(String name) { return Global.getSettings().fileExistsInCommon(name); }
            public String read(String name) throws IOException { return Global.getSettings().readTextFileFromCommon(name); }
            public void write(String name, String text) throws IOException { Global.getSettings().writeTextFileToCommon(name, text); }
        });
    }
    public CommonStorage(CommonFiles files) { this.files = java.util.Objects.requireNonNull(files); }
    private static String key(String name) throws IOException {
        if (name == null || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.contains("..") || !name.matches("[a-zA-Z0-9_./-]+")) throw new IOException("Invalid common-data document name.");
        return PREFIX + name;
    }
    public boolean exists(String name) throws IOException { String key = key(name); return files.exists(key) || files.exists(key + ".commit-bak"); }
    public String read(String name) throws IOException {
        warning = "";
        String key = key(name); IOException failure = null;
        if (files.exists(key)) {
            try { return readMarker(key, files.read(key)); }
            catch (ProfileStore.FutureSchemaException future) { throw future; }
            catch (IOException invalid) { failure = invalid; }
        }
        if (files.exists(key + ".commit-bak")) { String recovered = readMarker(key, files.read(key + ".commit-bak")); warning = "Recovered " + name + " from its checked common-data commit backup."; return recovered; }
        if (failure != null) throw failure;
        throw new IOException("Common-data document not found: " + name);
    }
    public String readRaw(String name) throws IOException { return files.read(key(name)); }
    public String warning() { return warning; }
    private String readMarker(String key, String text) throws IOException {
        bounded(text);
        try {
            JSONObject marker = new JSONObject(text);
            if (!marker.has("sectorpadJournalVersion")) return text; // Plain exported/imported or legacy documents.
            int version = marker.getInt("sectorpadJournalVersion");
            if (version > 1) throw new ProfileStore.FutureSchemaException("Common-data journal", version);
            if (version != 1) throw new IOException("Unsupported common-data journal.");
            String slot = marker.getString("slot"), digest = marker.getString("sha256");
            if (!(slot.equals("a") || slot.equals("b"))) throw new IOException("Invalid journal slot.");
            String snapshot = files.read(key + ".slot-" + slot); bounded(snapshot);
            JSONObject value = new JSONObject(snapshot);
            String payload = value.getString("payload");
            if (value.getLong("generation") != marker.getLong("generation") || !digest.equals(value.getString("sha256")) || !digest.equals(ProfileStore.digest(payload))) throw new IOException("Journal checksum or generation mismatch.");
            bounded(payload); return payload;
        } catch (JSONException invalid) { throw new IOException("Invalid common-data journal.", invalid); }
    }
    public void write(String name, String text) throws IOException {
        String key = key(name); bounded(text);
        if (name.startsWith("exports/") || name.startsWith("imports/") || name.contains(".rejected-")) { verifiedWrite(key, text); return; }
        String prior = null; String activeSlot = "b"; long generation = 0;
        if (files.exists(key) || files.exists(key + ".commit-bak")) {
            String possible = files.exists(key) ? files.read(key) : null;
            try {
                if (possible == null) throw new IOException("Missing current commit.");
                readMarker(key, possible); prior = possible;
            } catch (ProfileStore.FutureSchemaException future) { throw future; }
            catch (IOException invalid) {
                if (files.exists(key + ".commit-bak")) { possible = files.read(key + ".commit-bak"); readMarker(key, possible); prior = possible; }
                // No valid prior commit: ProfileStore preserves damaged originals before an explicit save.
            }
            if (prior != null) {
                try { JSONObject marker = new JSONObject(prior); activeSlot = marker.optString("slot", "b"); generation = marker.optLong("generation", 0); }
                catch (JSONException invalid) { throw new IOException("Invalid prior common-data commit.", invalid); }
            }
        }
        if (generation == Long.MAX_VALUE) throw new IOException("Common-data generation limit reached.");
        String nextSlot = activeSlot.equals("a") ? "b" : "a";
        String digest = ProfileStore.digest(text);
        String snapshot = new JSONObject(Map.of("generation", generation + 1, "sha256", digest, "payload", text)).toString();
        String marker = new JSONObject(Map.of("sectorpadJournalVersion", 1, "slot", nextSlot, "generation", generation + 1, "sha256", digest)).toString();
        bounded(snapshot);
        verifiedWrite(key + ".slot-" + nextSlot, snapshot);
        if (prior != null) verifiedWrite(key + ".commit-bak", prior);
        try { verifiedWrite(key, marker); }
        catch (IOException uncertain) {
            // A write API may report an error after writing. Accept only an exactly verified committed value.
            try { if (files.exists(key) && marker.equals(files.read(key)) && text.equals(readMarker(key, marker))) return; }
            catch (IOException verificationFailure) { uncertain.addSuppressed(verificationFailure); }
            throw uncertain;
        }
        if (!text.equals(readMarker(key, marker))) throw new IOException("Common-data commit failed verification.");
    }
    private void verifiedWrite(String key, String text) throws IOException {
        bounded(text); files.write(key, text);
        if (!text.equals(files.read(key))) throw new IOException("Common-data write did not verify: " + key.substring(PREFIX.length()));
    }
    private static void bounded(String text) throws IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > ProfileStore.MAX_FILE_BYTES) throw new IOException("Common-data document exceeds the 1 MiB API limit.");
    }
    public List<String> list(String directory) throws IOException {
        if (!directory.equals("imports")) return List.of();
        String index = key("imports/index.json"); if (!files.exists(index)) return List.of();
        try {
            String text = files.read(index); bounded(text); JSONArray entries = new JSONObject(text).getJSONArray("files");
            if (entries.length() > ProfileStore.MAX_PROFILES) throw new IOException("Too many imported profiles in the index.");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) { String file = entries.getString(i); if (!file.matches("[a-z0-9][a-z0-9_-]{0,47}\\.json") || file.equals("index.json")) throw new IOException("Invalid profile import index entry."); if (files.exists(key("imports/" + file))) result.add("imports/" + file); }
            return List.copyOf(result);
        } catch (JSONException invalid) { throw new IOException("Invalid profile import index.", invalid); }
    }
    public String readExternal(String location) throws IOException {
        String name = location.replace('\\', '/'); if (name.startsWith(PREFIX)) name = name.substring(PREFIX.length());
        if (!(name.matches("(?:imports|exports)/[a-z0-9][a-z0-9_-]{0,47}\\.json"))) throw new IOException("Runtime imports must use SectorPad common-data imports/exports.");
        return read(name);
    }
    public String location() { return "SectorPad"; }
    public boolean supportsAtomicReplacement() { return false; }
    public Lease acquire() throws IOException { if (!WRITER.tryAcquire()) throw new IOException("Another SectorPad save is in progress. Try Save again."); return WRITER::release; }
}
