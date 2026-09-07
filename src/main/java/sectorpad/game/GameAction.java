package sectorpad.game;

import java.util.Objects;

/** One currently available command. Rebuild catalogs when the wheel opens. */
public final class GameAction {
    public final String id;
    public final String label;
    public final String description;
    public final boolean enabled;
    public final String disabledReason;
    public final boolean dangerous;
    public final Runnable execute;

    public GameAction(String id, String label, String description, boolean enabled,
                      String disabledReason, boolean dangerous, Runnable execute) {
        this.id = Objects.requireNonNull(id);
        this.label = Objects.requireNonNull(label);
        this.description = Objects.requireNonNullElse(description, "");
        this.enabled = enabled;
        this.disabledReason = enabled ? "" : Objects.requireNonNullElse(disabledReason, "Unavailable here");
        this.dangerous = dangerous;
        this.execute = Objects.requireNonNull(execute);
    }

    public String id() { return id; }
    public String label() { return label; }
    public String description() { return description; }
    public boolean enabled() { return enabled; }
    public String disabledReason() { return disabledReason; }
    public boolean dangerous() { return dangerous; }
    public Runnable execute() { return execute; }
}
