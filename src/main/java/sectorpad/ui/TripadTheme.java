package sectorpad.ui;

import java.awt.Color;

/** Native-style tokens used only by SectorPad-owned drawing. */
public final class TripadTheme {
    public static final Color INK = new Color(0xe5edf2);
    public static final Color MUTED = new Color(0xa7bbc8);
    public static final Color CYAN = new Color(0x6bdde7);
    public static final Color FOCUS = new Color(0xffc878);
    public static final Color STEEL = new Color(0x3a5366);
    public static final Color PANEL = new Color(16,29,43,252);
    public static final Color KEY = new Color(0x192e3d);
    public static final Color SELECTED = new Color(0x254858);
    public static final Color FIELD = new Color(9,18,29,252);
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
