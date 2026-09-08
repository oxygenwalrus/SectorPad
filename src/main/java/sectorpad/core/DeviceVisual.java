package sectorpad.core;

import java.util.Locale;

/** Presentation family only. A visual override never changes normalized bindings or device selection. */
public enum DeviceVisual {
    XBOX("Xbox controller"), STEAM_DECK("Steam Deck"), ROG_ALLY("ROG Ally"), GENERIC("Generic controller");
    private final String displayName;
    DeviceVisual(String displayName){this.displayName=displayName;}
    public String displayName(){return displayName;}
    public static DeviceVisual resolve(String style,String deviceName){
        if("Xbox".equals(style))return XBOX;
        if("Steam Deck".equals(style))return STEAM_DECK;
        if("ROG Ally".equals(style))return ROG_ALLY;
        if("Generic".equals(style))return GENERIC;
        String name=deviceName==null?"":deviceName.toLowerCase(Locale.ROOT);
        if(name.contains("steam deck")||name.contains("steamdeck"))return STEAM_DECK;
        if(name.contains("rog ally")||name.contains("rog_ally"))return ROG_ALLY;
        if(name.contains("xbox")||name.contains("x-box"))return XBOX;
        // Steam Virtual Gamepad and XInput describe transport/layout, not physical hardware.
        return GENERIC;
    }
    public String label(String control){
        if(control==null||control.equals("NONE"))return "Unbound";
        if(this==STEAM_DECK)return switch(control){
            case "LB"->"L1";case "RB"->"R1";case "LT"->"L2";case "RT"->"R2";default->common(control);
        };
        if(this==GENERIC)return switch(control){
            case "A"->"South";case "B"->"East";case "X"->"West";case "Y"->"North";default->common(control);
        };
        return common(control);
    }
    private static String common(String control){return switch(control){
        case "VIEW"->"View";case "MENU"->"Menu";case "LEFT_STICK"->"Left stick";case "RIGHT_STICK"->"Right stick";
        case "DPAD_UP"->"D-pad up";case "DPAD_DOWN"->"D-pad down";case "DPAD_LEFT"->"D-pad left";case "DPAD_RIGHT"->"D-pad right";
        default->control;
    };}
}
