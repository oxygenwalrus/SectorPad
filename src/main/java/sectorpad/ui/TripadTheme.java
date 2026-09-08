package sectorpad.ui;

import java.awt.Color;

/** Instrument-panel palette shared by SectorPad drawing and its mod-loaded HUD skin. */
public final class TripadTheme {
    public static final Color INK = new Color(0xe6f0f4);
    public static final Color MUTED = new Color(0xa1b8c8);
    public static final Color CYAN = new Color(0x80ddeb);
    public static final Color FOCUS = new Color(0xf6bf75);
    public static final Color STEEL = new Color(0x355264);
    public static final Color PANEL = new Color(12,24,36,252);
    public static final Color KEY = new Color(0x142938);
    public static final Color SELECTED = new Color(0x203e50);
    public static final Color FIELD = new Color(7,15,25,252);
    public static final Color SHADOW = new Color(2,5,7,238);
    public static final Color DIM = new Color(5,12,20,154);
    private TripadTheme() { }
    public static Color alpha(Color color,int alpha) {
        return new Color(color.getRed(),color.getGreen(),color.getBlue(),Math.max(0,Math.min(255,alpha)));
    }
    public static float requestedScale(float value) {
        return Float.isFinite(value)&&value>0?Math.max(.5f,Math.min(2.5f,value)):1f;
    }
}
