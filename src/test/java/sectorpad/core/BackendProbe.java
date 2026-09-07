package sectorpad.core;

import sectorpad.input.SdlBackend;

/** Read-only native smoke test. This never initializes any output bridge. */
public final class BackendProbe {
    public static void main(String[] args){
        if(args.length!=1)throw new IllegalArgumentException("Pass the packaged native directory");
        try(SdlBackend backend=new SdlBackend(args[0])){
            PadFrame frame=backend.poll(System.nanoTime());
            System.out.println("Native backend: "+backend.status());
            System.out.println("Controller connected: "+frame.connected());
            System.out.println("Independent trigger axes advertised: "+frame.independentTriggers());
            if(backend.status().startsWith("Controller backend unavailable"))throw new AssertionError(backend.status());
        }
    }
}
