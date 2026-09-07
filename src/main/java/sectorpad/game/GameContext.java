package sectorpad.game;

/** Identity changes whenever a vanilla state, core tab, or interaction owner changes. */
public record GameContext(String name, String identity, boolean gameplayAllowed,
                          boolean paused, String label) {
    public boolean is(String expected) { return expected.equals(name); }
}
