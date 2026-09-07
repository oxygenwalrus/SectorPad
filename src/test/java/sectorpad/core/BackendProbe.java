package sectorpad.core;

import sectorpad.input.SdlBackend;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Read-only native smoke test. This never initializes any output bridge. */
public final class BackendProbe {
    public static void main(String[] args)throws InterruptedException {
        if(args.length!=1&&args.length!=2)throw new IllegalArgumentException("Pass the packaged native directory and optional observation seconds (1-300)");
        int seconds=args.length==2?Integer.parseInt(args[1]):0;
        if(args.length==2&&(seconds<1||seconds>300))throw new IllegalArgumentException("Observation must last 1-300 seconds");
        try(SdlBackend backend=new SdlBackend(args[0])){
            PadFrame frame=backend.poll(System.nanoTime());
            System.out.println("Native backend: "+backend.status());
            System.out.println("Controller connected: "+frame.connected());
            System.out.println("Independent trigger axes advertised: "+frame.independentTriggers());
            if(backend.status().startsWith("Controller backend unavailable"))throw new AssertionError(backend.status());
            if(seconds>0)observe(backend,frame,seconds);
        }
    }
    private static void observe(SdlBackend backend,PadFrame initial,int seconds)throws InterruptedException {
        System.out.println("Read-only observation: "+seconds+" seconds. No mouse, keyboard, rumble or gameplay output is created.");
        System.out.println("Operate both sticks, triggers and buttons; optionally disconnect/reconnect. Keyboard input is never recorded.");
        long start=System.nanoTime(),duration=seconds*1_000_000_000L;
        PadFrame previous=initial;
        float[] min=new float[6],max=new float[6];Arrays.fill(min,Float.POSITIVE_INFINITY);Arrays.fill(max,Float.NEGATIVE_INFINITY);
        Set<String> observed=new TreeSet<>();int connectionChanges=0,samples=0;boolean simultaneousTriggers=false;
        do {
            long now=System.nanoTime();PadFrame f=backend.poll(now);
            if(f.connected()!=previous.connected()||!f.deviceId().equals(previous.deviceId())){
                connectionChanges++;System.out.printf(Locale.ROOT,"%.3fs device: %s%n",(now-start)/1e9,backend.status());
            }
            Set<String> pressed=new TreeSet<>(f.buttons());pressed.removeAll(previous.buttons());
            Set<String> released=new TreeSet<>(previous.buttons());released.removeAll(f.buttons());
            if(!pressed.isEmpty()||!released.isEmpty())System.out.printf(Locale.ROOT,"%.3fs pressed=%s released=%s%n",(now-start)/1e9,pressed,released);
            if(f.connected()){
                float[] axes={f.lx(),f.ly(),f.rx(),f.ry(),f.lt(),f.rt()};
                for(int i=0;i<axes.length;i++){min[i]=Math.min(min[i],axes[i]);max[i]=Math.max(max[i],axes[i]);}
                samples++;observed.addAll(f.buttons());simultaneousTriggers|=f.lt()>.55f&&f.rt()>.55f;
            }
            previous=f;Thread.sleep(16);
        }while(System.nanoTime()-start<duration);
        System.out.println("Observed buttons: "+observed+"; connection changes: "+connectionChanges+"; connected samples: "+samples);
        if(samples>0){
            String[] labels={"LX","LY","RX","RY","LT","RT"};
            for(int i=0;i<labels.length;i++)System.out.printf(Locale.ROOT,"%s range: %.3f to %.3f%n",labels[i],min[i],max[i]);
        }
        System.out.println("Both trigger axes above 0.55 simultaneously observed: "+simultaneousTriggers);
        System.out.println("Final backend: "+backend.status());
    }
}
