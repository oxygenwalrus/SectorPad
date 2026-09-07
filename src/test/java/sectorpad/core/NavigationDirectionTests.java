package sectorpad.core;

import java.util.Set;

public final class NavigationDirectionTests {
    public static void main(String[] args) {
        NavigationDirection direction=new NavigationDirection();
        check(direction.choose(Set.of("ui.right")).equals("ui.right"),"Right begins immediately");
        check(direction.choose(Set.of("ui.up","ui.right")).equals("ui.right"),"Rolling into a diagonal retains held direction");
        check(direction.choose(Set.of("ui.up")).equals("ui.up"),"Releasing previous direction selects remaining axis");
        check(direction.choose(Set.of("ui.up","ui.down")).isEmpty(),"Opposed directions cancel");
        check(direction.choose(Set.of("ui.left","ui.right")).isEmpty(),"Horizontal opposites cancel");
        check(direction.choose(Set.of("ui.down","ui.left")).equals("ui.down"),"Fresh diagonal has deterministic priority");
        check(direction.choose(Set.of()).isEmpty(),"Neutral resets direction");
        direction.choose(Set.of("ui.right"));direction.reset();
        check(direction.choose(Set.of("ui.up","ui.right")).equals("ui.up"),"Context reset clears prior direction");
        RepeatKey repeat=new RepeatKey();direction.reset();
        check(repeat.pulse(direction.choose(Set.of("ui.right")),0,.4,.1),"First step fires");
        check(!repeat.pulse(direction.choose(Set.of("ui.right","ui.up")),20_000_000,.4,.1),"Diagonal roll cannot create a second immediate step");
        check(repeat.pulse(direction.choose(Set.of("ui.right","ui.up")),400_000_000,.4,.1),"Held direction repeats after delay");
        System.out.println("NavigationDirectionTests: 11 checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
