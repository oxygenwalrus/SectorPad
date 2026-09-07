package sectorpad.core;

import java.util.function.Consumer;

/** Failure in one addon cleanup operation must not skip the remaining input releases. */
public final class FailureCleanup {
    private FailureCleanup() { }
    public static void run(Consumer<Throwable> report, Runnable... operations) {
        for (Runnable operation : operations) {
            try { operation.run(); }
            catch (RuntimeException | LinkageError failure) {
                try { report.accept(failure); }
                catch (RuntimeException | LinkageError ignored) { /* Cleanup is still mandatory. */ }
            }
        }
    }
}
