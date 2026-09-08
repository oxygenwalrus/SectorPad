package sectorpad.core;

/** Display labels only; persisted bindings always keep the normalized input identifiers. */
public final class ButtonLabels {
    private ButtonLabels() { }
    public static String label(String control,String style,String deviceName) {
        return DeviceVisual.resolve(style,deviceName).label(control);
    }
}
