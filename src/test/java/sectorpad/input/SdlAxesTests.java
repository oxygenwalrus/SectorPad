package sectorpad.input;

/** Checks the native SDL coordinate convention at the actual backend conversion boundary. */
public final class SdlAxesTests {
    public static void main(String[] args){
        check(SdlBackend.upAxis(-1)==1,"SDL up becomes positive world/UI Y");
        check(SdlBackend.upAxis(1)==-1,"SDL down becomes negative world/UI Y");
        check(SdlBackend.upAxis(-32768f/32767f)==1,"asymmetric native minimum remains bounded");
        check(SdlBackend.upAxis(.25f)==-.25f,"analog magnitude is preserved before calibration");
        check(SdlBackend.upAxis(Float.NaN)==0,"invalid axis is neutral");
        check(SdlBackend.upAxis(Float.POSITIVE_INFINITY)==0,"infinite axis is neutral");
        System.out.println("SdlAxesTests: 6 native coordinate contract checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
