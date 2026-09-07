package sectorpad.ui;

import java.awt.Color;

/** Native-style tokens used only by SectorPad-owned drawing. */
public final class TripadTheme {
    public static final Color INK = new Color(220,220,220);
    public static final Color MUTED = new Color(164,190,201);
    public static final Color CYAN = new Color(70,222,255);
    public static final Color FOCUS = new Color(255,210,0);
    public static final Color STEEL = new Color(159,174,174);
    public static final Color PANEL = new Color(13,32,44,248);
    public static final Color KEY = new Color(23,62,76);
    public static final Color SELECTED = new Color(43,86,97);
    public static final Color FIELD = new Color(7,21,29,252);
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

