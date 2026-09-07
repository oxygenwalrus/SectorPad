package sectorpad.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable logical bindings. Contains no game objects and is safe to persist outside saves. */
public final class BindingProfile {
    public static final int SCHEMA_VERSION = 2;
    public static final List<String> CONTEXTS = List.of("UI", "CAMPAIGN", "COMBAT", "MAP", "TACTICAL", "DEPLOYMENT", "REFIT");
    public static final List<String> BUTTONS = List.of("A", "B", "X", "Y", "LB", "RB", "LT", "RT", "VIEW", "MENU", "L3", "R3", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT");
    public static final List<String> STICKS = List.of("LEFT_STICK", "RIGHT_STICK");
    public static final String NONE = "NONE";
    private static final Map<String, Map<String, String>> DEFAULTS = defaultBindings();

    public final String id;
    public final String displayName;
    private final Map<String, Map<String, String>> contexts;

    public BindingProfile(String id, String displayName, Map<String, ? extends Map<String, String>> contexts) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Profile ID must use 1-48 lower-case letters, digits, hyphens or underscores.");
        if (displayName == null || displayName.isBlank() || displayName.length() > 80 || displayName.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Profile name must contain 1-80 visible characters.");
        this.id = id;
        this.displayName = displayName;
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(contexts, "contexts");
        // JSON object iteration is not an ordering contract. Keep the same action rows
        // after a restart/import, while retaining unknown keys for validation below.
        List<String> contextOrder=new ArrayList<>(CONTEXTS);
        for(String context:contexts.keySet())if(!contextOrder.contains(context))contextOrder.add(context);
        for(String context:contextOrder){
            Map<String,String> bindings=contexts.get(context);if(bindings==null)continue;
            Map<String,String> ordered=new LinkedHashMap<>();
            Map<String,String> defaults=DEFAULTS.get(context);
            if(defaults!=null)for(String action:defaults.keySet())if(bindings.containsKey(action))ordered.put(action,bindings.get(action));
            bindings.forEach(ordered::putIfAbsent);
            copy.put(context,Collections.unmodifiableMap(ordered));
        }
        this.contexts = Collections.unmodifiableMap(copy);
        List<String> errors = validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));
    }

    public static BindingProfile defaults() { return new BindingProfile("default", "Standard", DEFAULTS); }
    public static BindingProfile southpaw() {
        Map<String, Map<String, String>> bindings = defaults().mutableCopy();
        bindings.values().forEach(context -> context.replaceAll((action, input) -> input.equals("LEFT_STICK") ? "RIGHT_STICK" : input.equals("RIGHT_STICK") ? "LEFT_STICK" : input));
        return new BindingProfile("southpaw", "Southpaw", bindings);
    }
    public static Map<String, String> defaultsFor(String context) { return DEFAULTS.get(normalizeContext(context)); }
    public static String normalizeContext(String context) {
        String value = Objects.requireNonNull(context, "context").toUpperCase(Locale.ROOT);
        if (!CONTEXTS.contains(value)) throw new IllegalArgumentException("Unknown context: " + context);
        return value;
    }
    public static boolean isVectorAction(String context, String action) {
        String binding = defaultsFor(context).get(action);
        return binding != null && STICKS.contains(binding);
    }
    public static String settingId(String context, String action) { return "sp_bind_" + normalizeContext(context).toLowerCase(Locale.ROOT) + "_" + action.replace('.', '_'); }
    public Map<String, Map<String, String>> contexts() { return contexts; }
    public Map<String, String> bindings(String context) { return contexts.get(normalizeContext(context)); }
    public String binding(String context, String action) { return bindings(context).getOrDefault(action, NONE); }
    public BindingProfile renamed(String id, String name) { return new BindingProfile(id, name, contexts); }
    public BindingProfile resetContext(String context) {
        String key = normalizeContext(context); Map<String, Map<String, String>> next = mutableCopy();
        next.put(key, new LinkedHashMap<>(defaultsFor(key))); return new BindingProfile(id, displayName, next);
    }

    /** Returns the action using a control in this context, or null. Reuse in other contexts is legal. */
    public String conflict(String context, String action, String control) {
        if (NONE.equals(control)) return null;
        for (Map.Entry<String, String> entry : bindings(context).entrySet()) {
            if (!entry.getKey().equals(action) && entry.getValue().equals(control)) return entry.getKey();
        }
        return null;
    }
    public BindingProfile withBinding(String context, String action, String control) {
        Map<String, Map<String, String>> next = mutableCopy();
        next.get(normalizeContext(context)).put(action, control);
        return new BindingProfile(id, displayName, next);
    }
    /** Moves the selected action's old binding to the conflicting action as one validated edit. */
    public BindingProfile withSwap(String context, String action, String control) {
        String key = normalizeContext(context);
        Map<String, Map<String, String>> next = mutableCopy();
        Map<String, String> binding = next.get(key);
        String old = binding.get(action);
        if (old == null) throw new IllegalArgumentException("Unknown action: " + action);
        String conflict = conflict(key, action, control);
        binding.put(action, control);
        if (conflict != null) binding.put(conflict, old);
        return new BindingProfile(id, displayName, next);
    }
    public Map<String, Map<String, String>> mutableCopy() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        contexts.forEach((context, values) -> result.put(context, new LinkedHashMap<>(values)));
        return result;
    }
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (!contexts.keySet().equals(new LinkedHashSet<>(CONTEXTS))) errors.add("The profile must define every supported context exactly once.");
        for (String context : CONTEXTS) {
            Map<String, String> actual = contexts.get(context);
            if (actual == null) continue;
            Map<String, String> defaults = DEFAULTS.get(context);
            if (!actual.keySet().equals(defaults.keySet())) { errors.add(context + " contains missing or unknown actions."); continue; }
            Map<String, String> owners = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : actual.entrySet()) {
                String action = entry.getKey(), control = entry.getValue();
                List<String> supported = isVectorAction(context, action) ? STICKS : BUTTONS;
                if (control == null || (!NONE.equals(control) && !supported.contains(control))) { errors.add(context + ": unsupported control for " + action + "."); continue; }
                if (!NONE.equals(control)) {
                    String previous = owners.put(control, action);
                    if (previous != null) errors.add(context + ": " + previous + " and " + action + " both use " + control + ". Swap them or unassign one.");
                }
                if (required(action) && NONE.equals(control)) errors.add(context + ": " + action + " is required for navigation and recovery.");
            }
        }
        return Collections.unmodifiableList(errors);
    }
    public static boolean required(String action) {
        return Set.of("ui.confirm", "ui.cancel", "ui.pointer", "hub.open", "game.menu").contains(action);
    }
    public static String actionLabel(String action) {
        int dot = action.indexOf('.');
        String label = action.substring(dot + 1).replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
    @Override public boolean equals(Object other) {
        return other instanceof BindingProfile profile && id.equals(profile.id) && displayName.equals(profile.displayName) && contexts.equals(profile.contexts);
    }
    @Override public int hashCode() { return Objects.hash(id, displayName, contexts); }

    private static Map<String, Map<String, String>> defaultBindings() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        result.put("UI", ordered("ui.confirm=A", "ui.cancel=B", "ui.secondary=X", "ui.actions=Y", "ui.previousTab=LB", "ui.nextTab=RB", "ui.previousSubtab=LT", "ui.nextSubtab=RT", "ui.precision=L3", "ui.tooltip=R3", "ui.up=DPAD_UP", "ui.down=DPAD_DOWN", "ui.left=DPAD_LEFT", "ui.right=DPAD_RIGHT", "ui.pointer=LEFT_STICK", "ui.scroll=RIGHT_STICK", "hub.open=VIEW", "game.menu=MENU"));
        result.put("CAMPAIGN", ordered("campaign.interact=A", "campaign.cancelCourse=B", "campaign.pause=X", "campaign.map=Y", "campaign.abilities=LB", "campaign.fastForward=RB", "campaign.zoomOut=LT", "campaign.zoomIn=RT", "campaign.previousTarget=DPAD_LEFT", "campaign.nextTarget=DPAD_RIGHT", "campaign.intel=DPAD_UP", "campaign.fleet=DPAD_DOWN", "campaign.pointerMode=L3", "campaign.recenter=R3", "campaign.move=LEFT_STICK", "campaign.pointer=RIGHT_STICK", "hub.open=VIEW", "game.menu=MENU"));
        result.put("COMBAT", ordered("combat.target=A", "combat.vent=B", "combat.autofire=X", "combat.tactical=Y", "combat.weapons=LB", "combat.system=RB", "combat.shield=LT", "combat.fire=RT", "combat.previousGroup=DPAD_LEFT", "combat.nextGroup=DPAD_RIGHT", "combat.fighters=DPAD_UP", "combat.autopilot=DPAD_DOWN", "combat.brake=L3", "combat.targetLock=R3", "combat.move=LEFT_STICK", "combat.aim=RIGHT_STICK", "hub.open=VIEW", "game.menu=MENU"));
        for (String context : List.of("MAP", "TACTICAL", "DEPLOYMENT", "REFIT")) result.put(context, result.get("UI"));
        return Collections.unmodifiableMap(result);
    }
    private static Map<String, String> ordered(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String value : values) { String[] pair = value.split("=", 2); result.put(pair[0], pair[1]); }
        return Collections.unmodifiableMap(result);
    }
}
