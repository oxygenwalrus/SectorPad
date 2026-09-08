package sectorpad.input;

/** Opt-in quick system XInput snapshot. Never vibrates, enables devices or emits input. */
public final class WindowsXInputBackendProbe {
    public static void main(String[] args){
        if(args.length!=1)throw new IllegalArgumentException("Provide packaged native root");
        try(WindowsXInputBackend backend=new WindowsXInputBackend(args[0])){
            System.out.println("XInput runtime: "+(backend.isWine()?"Wine":"Windows"));
            for(int slot=0;slot<4;slot++){
                var frame=backend.poll(System.nanoTime(),slot);
                System.out.println("XInput slot "+(slot+1)+": "+backend.status()+"; connected="+frame.connected()
                        +(frame.connected()?"; axes="+frame.lx()+","+frame.ly()+","+frame.rx()+","+frame.ry()
                        +"; triggers="+frame.lt()+","+frame.rt()+"; buttons="+frame.buttons():""));
            }
        }
        System.out.println("WindowsXInputBackendProbe: passive snapshot completed; no input emitted");
    }
}
