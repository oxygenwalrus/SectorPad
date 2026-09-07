package sectorpad.diagnostics;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.Locale;

/** Existing game logger plus an explicit, bounded common-data support report. No private filesystem access. */
public final class Diagnostics {
    public static final String REPORT_PATH = "SectorPad/exports/diagnostics.json";
    private static final DiagnosticJournal JOURNAL = new DiagnosticJournal((message, error) -> {
        var logger = Global.getLogger(Diagnostics.class);
        if (error == null) logger.info(message); else logger.error(message, error);
    }, () -> System.nanoTime() / 1_000_000L);
    private static boolean started;
    private Diagnostics() { }

    /** Only curated platform/build versions are collected; no installed-mod inventory or device names. */
    public static synchronized void startSession() {
        if (started) return;
        started = true;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            state("os", os.contains("windows") ? "windows" : os.contains("linux") ? "linux" : "other");
            state("architecture", System.getProperty("os.arch", "unavailable"));
            state("java_version", System.getProperty("java.version", "unavailable"));
            state("jamepad_version", "2.30.0.0-sectorpad");
            // The public API documents the version token (e.g. 0.95a-RC12); getVersionString is a display label.
            state("game_version", Global.getSettings().getGameVersion());
            for (String id : new String[] { "sectorpad", "lunalib", "lw_lazylib", "lw_console" }) {
                var manager = Global.getSettings().getModManager();
                state(id + "_version", manager.isModEnabled(id) ? manager.getModSpec(id).getVersion() : "disabled");
            }
            event("session.started");
        } catch (RuntimeException | LinkageError failure) { error("session.metadata_failed", failure); }
    }

    /** Codes and state values must be developer-defined tokens, never user-entered text. */
    public static void event(String code) {
        try { JOURNAL.event(code, null); } catch (RuntimeException | LinkageError ignored) { }
    }
    public static void error(String code, Throwable failure) {
        try { JOURNAL.event(code, failure); } catch (RuntimeException | LinkageError ignored) { }
    }
    public static void state(String key, String safeValue) {
        try { JOURNAL.state(key, safeValue); } catch (RuntimeException | LinkageError ignored) { }
    }
    public static String snapshot() {
        try { return JOURNAL.snapshot(); }
        catch (RuntimeException | LinkageError ignored) { return "{\"schemaVersion\":1,\"status\":\"unavailable\"}\n"; }
    }
    public static String exportReport() {
        try {
            boolean ok = JOURNAL.export(document -> {
                var settings = Global.getSettings();
                settings.writeTextFileToCommon(REPORT_PATH, document);
                if (!document.equals(settings.readTextFileFromCommon(REPORT_PATH))) throw new IOException("Diagnostics report verification failed");
            });
            return ok ? "Diagnostics saved to common data: " + REPORT_PATH
                    : "Diagnostics export failed. Check starsector.log; native controls remain available.";
        } catch (RuntimeException | LinkageError failure) {
            error("report.export_failed", failure);
            return "Diagnostics export unavailable. Check starsector.log.";
        }
    }
}
