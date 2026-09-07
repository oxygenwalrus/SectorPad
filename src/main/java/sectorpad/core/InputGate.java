package sectorpad.core;

import java.util.HashSet;
import java.util.Set;

/** Context epochs prevent opening/closing overlays from reusing a held input. */
public final class InputGate {
    private String owner = "";
    private boolean armed;
    private long generation;
    private Set<String> previous = Set.of();
    public record Edges(Set<String> held, Set<String> pressed, Set<String> released, long generation) {
        public boolean pressed(String action) { return pressed.contains(action); }
    }
    public boolean updateOwner(String next) {
        if (!owner.equals(next)) { owner=next; disarm(); return true; }
        return false;
    }
    public void disarm() { armed=false; previous=Set.of(); generation++; }
    public boolean armWhenNeutral(PadFrame frame, float deadzone) {
        if (!armed && frame.connected() && frame.neutral(deadzone)) armed=true;
        return armed;
    }
    public boolean armed() { return armed; }
    public Edges edges(Set<String> held) {
        if (!armed) return new Edges(Set.of(),Set.of(),Set.of(),generation);
        Set<String> pressed=new HashSet<>(held); pressed.removeAll(previous);
        Set<String> released=new HashSet<>(previous); released.removeAll(held);
        previous=Set.copyOf(held);
        return new Edges(previous,Set.copyOf(pressed),Set.copyOf(released),generation);
    }
}
