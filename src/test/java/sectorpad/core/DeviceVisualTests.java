package sectorpad.core;

public final class DeviceVisualTests {
    private static int checks;
    public static void main(String[] args){
        check(DeviceVisual.resolve("Automatic","Xbox Wireless Controller")==DeviceVisual.XBOX,"Xbox identity");
        check(DeviceVisual.resolve("Automatic","Valve Steam Deck Controller")==DeviceVisual.STEAM_DECK,"Deck identity");
        check(DeviceVisual.resolve("Automatic","ASUS ROG Ally X")==DeviceVisual.ROG_ALLY,"Ally identity");
        check(DeviceVisual.resolve("Automatic","Steam Virtual Gamepad")==DeviceVisual.GENERIC,"virtual Steam transport cannot identify a Deck");
        check(DeviceVisual.resolve("Automatic","XInput controller 1")==DeviceVisual.GENERIC,"XInput cannot identify handheld hardware");
        check(DeviceVisual.resolve("ROG Ally","XInput controller 1")==DeviceVisual.ROG_ALLY,"appearance override resolves masked handheld");
        check(DeviceVisual.resolve("Steam Deck",null)==DeviceVisual.STEAM_DECK,"offline schema uses explicit appearance");
        check(DeviceVisual.resolve(null,null)==DeviceVisual.GENERIC,"missing identity safe fallback");
        check(ButtonLabels.label("LT","Automatic","Steam Deck").equals("L2"),"Deck trigger marking");
        check(ButtonLabels.label("LT","Automatic","ROG Ally").equals("LT"),"Ally trigger marking");
        check(ButtonLabels.label("NONE","Xbox","any").equals("Unbound"),"unbound is not a physical control");
        check(ButtonLabels.label("A","Generic",null).equals("South"),"generic direction labeling");
        System.out.println("DeviceVisualTests: "+checks+" identity and appearance fallback checks passed");
    }
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
}
