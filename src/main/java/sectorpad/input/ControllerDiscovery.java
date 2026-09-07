package sectorpad.input;

import com.studiohartman.jamepad.ControllerUnpluggedException;

/** Bounded recovery when a handheld exposes its gamepad after SDL's initial enumeration. */
final class ControllerDiscovery {
    static final int LIMIT=8;
    static final long RESCAN_NANOS=1_000_000_000L;
    interface Slots {
        boolean connected(int slot);
        int instance(int slot) throws ControllerUnpluggedException;
        void reconnect(int slot);
    }
    private long lastRescan;
    private boolean scanned;
    private int selected=-1,instance=-1,requested=-1;

    int select(long now,int requestedIndex,Slots slots) throws ControllerUnpluggedException {
        int wanted=requestedIndex>=0&&requestedIndex<LIMIT?requestedIndex:-1;
        if(wanted!=requested){selected=instance=-1;requested=wanted;scanned=false;}
        // Jamepad.update() normally handles hotplug immediately. Retry only closed/disconnected
        // slots as a fallback: never churn an active controller or require a button press/restart.
        if(!scanned||now-lastRescan>=RESCAN_NANOS){
            lastRescan=now;scanned=true;
            for(int i=0;i<LIMIT;i++)if(!slots.connected(i))slots.reconnect(i);
        }
        if(requested>=0){
            selected=slots.connected(requested)?requested:-1;
        } else if(selected<0||!slots.connected(selected)||slots.instance(selected)!=instance){
            selected=-1;
            // SDL indices can shift after unplugging a different pad. Keep the active physical
            // instance if it still exists, then fall back to the first available controller.
            int first=-1;
            for(int i=0;i<LIMIT;i++)if(slots.connected(i)){
                if(first<0)first=i;
                if(slots.instance(i)==instance){selected=i;break;}
            }
            if(selected<0)selected=first;
        }
        instance=selected<0?-1:slots.instance(selected);
        return selected;
    }
    void reset(){selected=instance=requested=-1;scanned=false;}
}
