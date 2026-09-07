package sectorpad.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RecoveryTests {
    private static int checks;
    public static void main(String[] args) {
        MainMenuShortcuts shortcuts = new MainMenuShortcuts();
        check(shortcuts.update(frame("X"), true) == MainMenuShortcuts.Action.NONE, "Held startup button is suppressed");
        shortcuts.update(frame(), true);
        check(shortcuts.update(frame("X"), true) == MainMenuShortcuts.Action.SETUP, "Setup opens from neutral");
        check(shortcuts.update(frame("X"), true) == MainMenuShortcuts.Action.NONE, "Held shortcut does not repeat");
        shortcuts.update(frame(), true);
        check(shortcuts.update(frame("Y"), true) == MainMenuShortcuts.Action.LUNA, "Luna shortcut");
        shortcuts.update(frame(), true);
        check(shortcuts.update(frame("R3"), true) == MainMenuShortcuts.Action.KEYBOARD, "Keyboard shortcut");
        check(shortcuts.update(frame("X"), false) == MainMenuShortcuts.Action.NONE, "Native dialogs retain mappings");
        check(shortcuts.update(frame("X"), true) == MainMenuShortcuts.Action.NONE, "Return to title requires release");
        shortcuts.update(PadFrame.disconnected(), true);
        check(shortcuts.update(frame("Y"), true) == MainMenuShortcuts.Action.NONE, "Reconnect requires neutral");
        shortcuts.update(frame(), true);
        check(shortcuts.update(frame("Y"), true) == MainMenuShortcuts.Action.LUNA, "No keyboard event required");
        List<Integer> cleaned = new ArrayList<>(); List<Throwable> failures = new ArrayList<>();
        FailureCleanup.run(failures::add, () -> { throw new IllegalStateException(); }, () -> cleaned.add(1),
                () -> { throw new NoClassDefFoundError(); }, () -> cleaned.add(2));
        check(cleaned.equals(List.of(1, 2)) && failures.size() == 2, "All cleanup paths run after failures");
        FailureCleanup.run(f -> { throw new IllegalStateException(); }, () -> { throw new IllegalStateException(); }, () -> cleaned.add(3));
        check(cleaned.contains(3), "A broken logger does not prevent release");
        System.out.println("RecoveryTests: " + checks + " checks passed");
    }
    private static PadFrame frame(String... buttons) { return new PadFrame(true, "test", "test", true, 0,0,0,0,0,0, Set.of(buttons)); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
