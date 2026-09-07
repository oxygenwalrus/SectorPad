package sectorpad.diagnostics;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Bounded, game-independent diagnostics. Callers supply fixed codes and non-user-data tokens only. */
public final class DiagnosticJournal {
    public interface Sink { void log(String message, Throwable error); }
    public interface ExportWriter { void write(String document) throws Exception; }
    public static final int MAX_EVENTS = 128;
    public static final int MAX_KEYS = 64;
    public static final long REPEAT_MILLIS = 30_000;
    private static final int LOGS_PER_WINDOW = 60;
    private static final int ERROR_LOGS_PER_WINDOW = 12;
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_.+\\-]{1,64}");
    private record Event(long elapsedMillis, String code, String value, String errorType) { }
    private static final class Counter {
        long occurrences;
        long loggedAt = Long.MIN_VALUE;
        long errorLoggedAt = Long.MIN_VALUE;
        long suppressed;
    }
    private final Sink sink;
    private final LongSupplier clock;
    private final long started;
    private final ArrayDeque<Event> recent = new ArrayDeque<>();
    private final LinkedHashMap<String, String> state = new LinkedHashMap<>();
    private final LinkedHashMap<String, Counter> counts = new LinkedHashMap<>();
    private long lastTime;
    private long windowStarted;
    private long totalEvents;
    private long suppressedLogs;
    private long sinkFailures;
    private int windowLogs;
    private int windowErrorLogs;

    public DiagnosticJournal(Sink sink, LongSupplier clock) {
        this.sink = Objects.requireNonNull(sink);
        this.clock = Objects.requireNonNull(clock);
        this.started = clock.getAsLong();
    }

    /** Rejects arbitrary text, including paths, control characters and long values. */
    public static String token(String value) {
        return value != null && value.length() <= 64 && SAFE_TOKEN.matcher(value).matches() ? value : "unavailable";
    }

    public synchronized void state(String key, String value) {
        key = token(key); value = token(value);
        if (key.equals("unavailable") || value.equals(state.get(key))) return;
        trim(state, key);
        state.put(key, value);
        record("state." + key, value, null);
    }

    public synchronized void event(String code, Throwable error) {
        record(code, "", error);
    }

    private void record(String code, String value, Throwable error) {
        code = token(code);
        long now = elapsed();
        totalEvents = increment(totalEvents);
        if (recent.size() == MAX_EVENTS) recent.removeFirst();
        String errorType = error == null ? "" : token(error.getClass().getName());
        recent.addLast(new Event(now, code, value, errorType));
        trim(counts, code);
        Counter counter = counts.computeIfAbsent(code, ignored -> new Counter());
        counter.occurrences = increment(counter.occurrences);
        if (now - windowStarted >= REPEAT_MILLIS) { windowStarted = now; windowLogs = 0; windowErrorLogs = 0; }
        long lastLog = error == null ? counter.loggedAt : counter.errorLoggedAt;
        // state() already removes unchanged samples; preserve meaningful state transitions in the game log.
        boolean repeated = value.isEmpty() && lastLog != Long.MIN_VALUE && now - lastLog < REPEAT_MILLIS;
        // Normal state/menu activity cannot consume the trace budget needed to diagnose a later failure.
        boolean budgetFull = error == null ? windowLogs >= LOGS_PER_WINDOW : windowErrorLogs >= ERROR_LOGS_PER_WINDOW;
        if (repeated || budgetFull) {
            counter.suppressed = increment(counter.suppressed);
            suppressedLogs = increment(suppressedLogs);
            return;
        }
        String message = "SectorPad diagnostics: " + code + "; occurrences=" + counter.occurrences
                + "; suppressedSincePrevious=" + counter.suppressed + (value.isEmpty() ? "" : "; value=" + value);
        if (error == null) { counter.loggedAt = now; windowLogs++; }
        else { counter.errorLoggedAt = now; windowErrorLogs++; }
        counter.suppressed = 0;
        try { sink.log(message, error); }
        catch (RuntimeException | LinkageError failed) { sinkFailures = increment(sinkFailures); }
    }

    /** Snapshot contains no Throwable messages, stack frames, raw input, profile names or device identifiers. */
    public synchronized String snapshot() {
        StringBuilder out = new StringBuilder(32_768);
        out.append("{\"schemaVersion\":1,\"elapsedMillis\":").append(elapsed())
                .append(",\"totalEvents\":").append(totalEvents)
                .append(",\"suppressedLogs\":").append(suppressedLogs)
                .append(",\"sinkFailures\":").append(sinkFailures).append(",\"state\":{");
        boolean first = true;
        for (var entry : state.entrySet()) {
            if (!first) out.append(','); first = false;
            quote(out, entry.getKey()); out.append(':'); quote(out, entry.getValue());
        }
        out.append("},\"counts\":{"); first = true;
        for (var entry : counts.entrySet()) {
            if (!first) out.append(','); first = false;
            quote(out, entry.getKey()); out.append(':').append(entry.getValue().occurrences);
        }
        out.append("},\"recentEvents\":["); first = true;
        for (Event event : recent) {
            if (!first) out.append(','); first = false;
            out.append("{\"elapsedMillis\":").append(event.elapsedMillis).append(",\"code\":");
            quote(out, event.code); out.append(",\"value\":"); quote(out, event.value);
            out.append(",\"errorType\":"); quote(out, event.errorType); out.append('}');
        }
        return out.append("]}\n").toString();
    }

    /** Explicit support export or failure-boundary snapshot; one fixed report is overwritten. */
    public boolean export(ExportWriter writer) {
        try { writer.write(snapshot()); event("report.exported", null); return true; }
        catch (Exception | LinkageError failure) { event("report.export_failed", failure); return false; }
    }

    private long elapsed() {
        try { lastTime = Math.max(lastTime, Math.max(0, clock.getAsLong() - started)); }
        catch (RuntimeException | LinkageError ignored) { /* Preserve the last usable clock sample. */ }
        return lastTime;
    }
    private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }
    private static <T> void trim(LinkedHashMap<String, T> entries, String key) {
        if (!entries.containsKey(key) && entries.size() >= MAX_KEYS) entries.remove(entries.keySet().iterator().next());
    }
    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        out.append('"');
    }
}
