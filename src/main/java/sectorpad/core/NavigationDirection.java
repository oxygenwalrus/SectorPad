package sectorpad.core;

import java.util.Set;

/** Keeps one cardinal direction while a D-pad rolls through a diagonal. */
public final class NavigationDirection {
    private String previous="";
    public String choose(Set<String> held) {
        boolean vertical=held.contains("ui.up")!=held.contains("ui.down");
        boolean horizontal=held.contains("ui.left")!=held.contains("ui.right");
        if(held.contains(previous) && (previous.equals("ui.up")||previous.equals("ui.down")?vertical:horizontal))return previous;
        previous=vertical?(held.contains("ui.up")?"ui.up":"ui.down"):
                horizontal?(held.contains("ui.left")?"ui.left":"ui.right"):"";
        return previous;
    }
    public void reset(){previous="";}
}
