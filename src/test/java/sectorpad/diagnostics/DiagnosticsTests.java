package sectorpad.diagnostics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Runs without the game, native libraries, logger configuration or filesystem. */
public final class DiagnosticsTests {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static int occurrences(String text, String value) {
        int count = 0, offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) { count++; offset += value.length(); }
        return count;
    }
    public static void main(String[] args) {
        AtomicLong time = new AtomicLong(1_000);
        ArrayList<String> logs = new ArrayList<>();
        ArrayList<Throwable> errors = new ArrayList<>();
        DiagnosticJournal journal = new DiagnosticJournal((message, error) -> { logs.add(message); errors.add(error); }, time::get);
        RuntimeException fault = new RuntimeException("PRIVATE typed text C:\\Users\\private\\secret");
        journal.event("runtime.input", fault);
        for (int i = 0; i < 500; i++) journal.event("runtime.input", fault);
        check(logs.size() == 1, "Repeated errors must not flood game log");
        check(errors.get(0) == fault, "First error retains original trace for game logger");
        String snapshot = journal.snapshot();
        check(!snapshot.contains("PRIVATE") && !snapshot.contains("secret"), "Support report excludes exception messages and stack content");
        check(snapshot.contains("java.lang.RuntimeException"), "Support report retains useful error type");
        check(snapshot.contains("\"runtime.input\":501"), "Occurrence totals survive suppression");
        check(snapshot.contains("\"suppressedLogs\":500"), "Suppression total is explicit");
        check(occurrences(snapshot, "\"errorType\"") == DiagnosticJournal.MAX_EVENTS, "Recent event ring is bounded");
        time.addAndGet(DiagnosticJournal.REPEAT_MILLIS - 1);
        journal.event("runtime.input", fault);
        check(logs.size() == 1, "Rate window does not expire early");
        time.incrementAndGet(); journal.event("runtime.input", fault);
        check(logs.size() == 2, "Rate window expiry permits a new trace");
        check(logs.get(1).contains("suppressedSincePrevious=501"), "Resumed log summarizes suppressed errors");
        journal.state("controller_enabled", "true");
        String before = journal.snapshot();
        journal.state("controller_enabled", "true");
        check(before.equals(journal.snapshot()), "Unchanged state creates no event or log");
        journal.state("controller_enabled", "false");
        check(journal.snapshot().contains("\"controller_enabled\":\"false\""), "Latest state retained");
        check(journal.snapshot().contains("\"code\":\"state.controller_enabled\",\"value\":\"true\""), "Recent events retain prior safe state values");
        check(logs.stream().anyMatch(line -> line.contains("state.controller_enabled") && line.contains("value=true")), "Game log identifies safe state value");
        check(logs.stream().anyMatch(line -> line.contains("state.controller_enabled") && line.contains("value=false")), "Changed state within thirty seconds remains visible in the game log");
        journal.state("bad\nkey", "secret");
        journal.state("context", "Text\"\\\nprivate");
        check(!journal.snapshot().contains("secret") && !journal.snapshot().contains("private"), "Malformed arbitrary text is rejected");
        check(journal.snapshot().contains("\"context\":\"unavailable\""), "Invalid state value fails closed");
        check(DiagnosticJournal.token("a".repeat(65)).equals("unavailable"), "Token size bounded");
        check(DiagnosticJournal.token("1.0.1-RC8_x64+17").equals("1.0.1-RC8_x64+17"), "Version and fixed-code tokens accepted");
        time.set(-10);
        check(journal.snapshot().contains("\"elapsedMillis\":30000"), "Clock reversal cannot regress elapsed diagnostics time");

        AtomicLong frozen = new AtomicLong();
        ArrayList<String> boundedLogs = new ArrayList<>();
        DiagnosticJournal bounded = new DiagnosticJournal((message, error) -> boundedLogs.add(message), frozen::get);
        for (int i = 0; i < 1_000; i++) { bounded.state("key" + i, "v"); bounded.event("event" + i, null); }
        check(boundedLogs.size() == 60, "Global budget limits distinct-code bursts");
        String boundedSnapshot = bounded.snapshot();
        check(!boundedSnapshot.contains("\"key0\"") && boundedSnapshot.contains("\"key999\""), "Old state keys evicted");
        check(!boundedSnapshot.contains("\"event0\"") && boundedSnapshot.contains("\"event999\""), "Old counter keys evicted");
        check(occurrences(boundedSnapshot, "\"errorType\"") == 128, "Distinct events also obey ring limit");
        check(boundedSnapshot.length() < 65_536, "Worst-case normal report fits far below common-data limit");
        bounded.event("fatal.after_normal_flood", fault);
        check(boundedLogs.size() == 61 && boundedLogs.get(60).contains("fatal.after_normal_flood"), "Normal event flood cannot suppress the first fatal trace");
        for (int i = 0; i < 30; i++) bounded.event("failure" + i, fault);
        check(boundedLogs.size() == 72, "Reserved error budget remains bounded during distinct failure floods");
        frozen.addAndGet(30_000); bounded.event("after.window", null);
        check(boundedLogs.size() == 73, "Global budget resets with elapsed window");
        bounded.event("fatal.after_normal_flood", fault);
        check(boundedLogs.size() == 74, "Reserved error budget resets with elapsed window");
        DiagnosticJournal sameCode = new DiagnosticJournal((message, error) -> errors.add(error), () -> 0);
        int errorBefore = errors.size();
        sameCode.event("same.code", null); sameCode.event("same.code", fault);
        check(errors.size() == errorBefore + 2 && errors.get(errors.size() - 1) == fault, "Earlier normal event cannot suppress later error under the same code");
        ArrayList<String> transitionLogs = new ArrayList<>();
        DiagnosticJournal transitions = new DiagnosticJournal((message, error) -> transitionLogs.add(message), () -> 0);
        for (int i = 0; i < 1_000; i++) transitions.state("paused", Boolean.toString(i % 2 == 0));
        check(transitionLogs.size() == 60, "Rapid state toggles still respect the bounded normal log budget");
        check(transitionLogs.get(0).contains("value=true") && transitionLogs.get(1).contains("value=false"), "Opposite state transitions log in observed order");

        DiagnosticJournal broken = new DiagnosticJournal((message, error) -> { throw new IllegalStateException("Broken logger"); }, () -> 0);
        broken.event("runtime.failed", fault);
        check(broken.snapshot().contains("\"sinkFailures\":1"), "Logger failure is bounded and does not escape");
        DiagnosticJournal missingLogger = new DiagnosticJournal((message, error) -> { throw new NoClassDefFoundError("Logger missing"); }, () -> 0);
        missingLogger.event("runtime.failed", fault);
        check(missingLogger.snapshot().contains("\"sinkFailures\":1"), "Logger linkage failure does not escape");
        String[] exported = { null };
        check(journal.export(text -> exported[0] = text), "Explicit export succeeds");
        check(exported[0].startsWith("{\"schemaVersion\":1,") && exported[0].endsWith("]}\n"), "Export is a standalone JSON document");
        check(journal.snapshot().contains("report.exported"), "Successful export records outcome");
        check(!journal.export(text -> { throw new IOException("Private directory name"); }), "Storage failure returns false");
        check(!journal.snapshot().contains("Private directory name"), "Export failure does not leak paths into report");
        check(journal.snapshot().contains("report.export_failed"), "Storage failure recorded");
        check(!journal.export(text -> { throw new NoClassDefFoundError("Missing game API"); }), "Export linkage failure returns false");
        System.out.println("DiagnosticsTests: " + checks + " checks passed");
    }
}
