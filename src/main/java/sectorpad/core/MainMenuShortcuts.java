package sectorpad.core;

import java.util.Set;

/** Fixed main-menu access remains available even when editable gameplay controls are disabled. */
public final class MainMenuShortcuts {
    public enum Action { NONE, SETUP, LUNA, KEYBOARD }
    private final InputGate gate = new InputGate();
    public Action update(PadFrame frame, boolean mainMenu) {
        if (!mainMenu || !frame.connected()) { gate.disarm(); return Action.NONE; }
        if (!gate.armWhenNeutral(frame, .22f)) return Action.NONE;
        Set<String> pressed = gate.edges(frame.buttons()).pressed();
        if (pressed.contains("X")) return Action.SETUP;
        if (pressed.contains("Y")) return Action.LUNA;
        if (pressed.contains("R3")) return Action.KEYBOARD;
        return Action.NONE;
    }
}
