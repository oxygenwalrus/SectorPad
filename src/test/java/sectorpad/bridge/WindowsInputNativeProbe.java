package sectorpad.bridge;

import sectorpad.input.ModNativeLoader;

/** Opt-in native lifecycle probe: does not observe active input, emit input, or open a game. */
public final class WindowsInputNativeProbe {
    public static void main(String[] args) {
        if(args.length!=1)throw new IllegalArgumentException("Provide the packaged native root");
        ModNativeLoader.loadFromDirectory(args[0],WindowsInputOutput.LIBRARY);
        for(int attempt=0;attempt<3;attempt++) {
            try(WindowsInputOutput output=new WindowsInputOutput(true)) {
                for(int key=1;key<256;key++) if(output.isPhysicalKeyDown(key))throw new AssertionError("Inactive observer must not retain physical state");
                if(!output.supportsUnicode() || !output.preservesPhysicalHolds())throw new AssertionError("Native capabilities unavailable");
            }
        }
        System.out.println("WindowsInputNativeProbe: ABI and 3 passive open/close cycles passed; no input emitted");
    }
}
