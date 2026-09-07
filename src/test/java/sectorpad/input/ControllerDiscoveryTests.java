package sectorpad.input;

import java.util.Arrays;

/** Simulates mode changes and missed notifications without requiring hardware or emitting input. */
public final class ControllerDiscoveryTests {
    private static int checks;
    private static final class Slots implements ControllerDiscovery.Slots {
        final int[] exposed=new int[8],opened=new int[8],reopens=new int[8];
        Slots(){Arrays.fill(exposed,-1);Arrays.fill(opened,-1);}
        public boolean connected(int slot){return opened[slot]>=0;}
        public int instance(int slot){return opened[slot];}
        public void reconnect(int slot){opened[slot]=exposed[slot];reopens[slot]++;}
    }
    public static void main(String[] args)throws Exception {
        ControllerDiscovery d=new ControllerDiscovery();Slots s=new Slots();
        s.exposed[3]=44;
        check(d.select(0,-1,s)==3,"startup discovers an idle controller without an input event");
        check(s.reopens[3]==1,"startup opens exposed controller");
        check(d.select(1,-1,s)==3,"connected selection remains stable");
        check(s.reopens[0]==1,"no per-frame reopen loop");
        d.select(ControllerDiscovery.RESCAN_NANOS,-1,s);
        check(s.reopens[3]==1,"periodic recovery never reopens the active device");
        check(s.reopens[0]==2,"only disconnected slots are periodically retried");
        s.exposed[3]=s.opened[3]=-1;
        check(d.select(ControllerDiscovery.RESCAN_NANOS+1,-1,s)==-1,"unplug is observed immediately");
        s.exposed[1]=55;
        check(d.select(ControllerDiscovery.RESCAN_NANOS+2,-1,s)==-1,"missed event respects bounded retry");
        check(d.select(2*ControllerDiscovery.RESCAN_NANOS,-1,s)==1,"desktop-to-gamepad mode is found without event");
        s.exposed[0]=s.opened[0]=66;
        check(d.select(2*ControllerDiscovery.RESCAN_NANOS+1,-1,s)==1,"new lower-index pad does not steal selection");
        s.exposed[0]=s.opened[0]=55;s.exposed[1]=s.opened[1]=66;
        check(d.select(2*ControllerDiscovery.RESCAN_NANOS+2,-1,s)==0,"active physical instance survives SDL reindexing");
        check(d.select(2*ControllerDiscovery.RESCAN_NANOS+3,7,s)==-1,"explicit disconnected device never silently switches");
        s.exposed[7]=77;
        check(d.select(3*ControllerDiscovery.RESCAN_NANOS+3,7,s)==7,"selected device can appear later");
        check(d.select(3*ControllerDiscovery.RESCAN_NANOS+4,100,s)==0,"invalid selection safely returns to automatic mode");
        d.reset();Arrays.fill(s.exposed,-1);Arrays.fill(s.opened,-1);s.exposed[5]=88;
        check(d.select(4,-1,s)==5,"new backend lifecycle rescans immediately with a new clock epoch");
        System.out.println("ControllerDiscoveryTests: "+checks+" startup, hotplug, selection and bounded recovery checks passed");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
