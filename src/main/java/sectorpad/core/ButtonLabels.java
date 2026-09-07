package sectorpad.core;

import java.util.Locale;

/** Display labels only; persisted bindings always keep the normalized input identifiers. */
public final class ButtonLabels {
    private ButtonLabels() { }
    public static String label(String control,String style,String deviceName) {
        if(control==null||control.equals("NONE"))return "Unbound";
        String device=deviceName==null?"":deviceName.toLowerCase(Locale.ROOT);
        boolean deck="Steam Deck".equals(style)||("Automatic".equals(style)&&(device.contains("steam")||device.contains("valve")));
        if(deck)return switch(control){case "LB"->"L1";case "RB"->"R1";case "LT"->"L2";case "RT"->"R2";default->common(control);};
        if("Generic".equals(style))return switch(control){case "A"->"South";case "B"->"East";case "X"->"West";case "Y"->"North";default->common(control);};
        return common(control);
    }
    private static String common(String control){return switch(control){
        case "VIEW"->"View";case "MENU"->"Menu";case "LEFT_STICK"->"Left stick";case "RIGHT_STICK"->"Right stick";
        case "DPAD_UP"->"D-pad up";case "DPAD_DOWN"->"D-pad down";case "DPAD_LEFT"->"D-pad left";case "DPAD_RIGHT"->"D-pad right";
        default->control;
    };}
}
