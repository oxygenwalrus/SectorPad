package sectorpad.game;

import com.fs.starfarer.api.Global;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Public native draw entry point only; never changes the chosen cursor or enables software mode. */
final class NativeCombatCursor {
    // Exact installed 0.98a class. Its package is a Java keyword, so public method handles are used.
    private static final String TYPE = "com.fs.starfarer.return.Oo" + "O".repeat(254);
    private static MethodHandle active, draw;
    private static boolean unavailable;

    private NativeCombatCursor() {}

    static void redrawIfSoftwareActive() {
        if (unavailable || Global.getSettings() == null || !Global.getSettings().getBoolean("useSoftwareMouseCursor")) return;
        try {
            resolve();
            // Without this guard the native method can activate software mode. A prior native draw
            // in this same frame has already set this flag; our call then performs only its paint.
            if ((boolean) active.invokeExact()) draw.invokeExact();
        } catch (Throwable failure) {
            if (failure instanceof ThreadDeath death) throw death;
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            unavailable = true;
            Global.getLogger(NativeCombatCursor.class).warn("SectorPad could not redraw the existing software cursor above its combat modal", failure);
        }
    }

    static void verifySignatures() throws ReflectiveOperationException { resolve(); }

    private static void resolve() throws ReflectiveOperationException {
        if (draw != null) return;
        Class<?> type = Class.forName(TYPE, false, NativeCombatCursor.class.getClassLoader());
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle activeMethod = lookup.findStaticGetter(type, "\u00d300000", boolean.class);
        MethodHandle drawMethod = lookup.findStatic(type, "\u00d400000", MethodType.methodType(void.class));
        active = activeMethod;
        draw = drawMethod;
    }
}
