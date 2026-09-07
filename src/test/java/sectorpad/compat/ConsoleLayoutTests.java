package sectorpad.compat;

import sectorpad.core.TextEntryModel;
import sectorpad.ui.OverlayLayout;

/** Checks the console's actual remaining log area against the shared docked keyboard geometry. */
public final class ConsoleLayoutTests {
    public static void main(String[] args) {
        TextEntryModel keyboard=new TextEntryModel();keyboard.open("",false,4096);keyboard.docked(true);
        int checks=0;
        for(float[] screen:new float[][]{{1280,720},{1280,800},{1920,1080},{1024,640}}){
            for(float scale:new float[]{.75f,1f,1.35f,1.8f}){
                var layout=OverlayLayout.keyboard(screen[0],screen[1],scale,keyboard.rows(),true);
                float log=ConsoleIntegration.availableLogHeight(screen[0],screen[1],32,true,scale);
                float logBottom=screen[1]-(44+32+14+log);
                if(log<40||logBottom<layout.panel().top()+19.9f)
                    throw new AssertionError("Console log overlaps keyboard at "+screen[0]+"x"+screen[1]+" scale "+scale);
                checks++;
            }
        }
        System.out.println("ConsoleLayoutTests: "+checks+" viewport/scale clearances passed (geometry, no game rendering)");
    }
}
