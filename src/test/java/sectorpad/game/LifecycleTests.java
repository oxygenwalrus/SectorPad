package sectorpad.game;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Lifecycle policy/API doubles. Does not initialize SDL, the desktop bridge, or a running game. */
public final class LifecycleTests {
    private static int checks;
    public static void main(String[] args) {
        inputOrder(); campaignClock(); transientRegistration(); combatOverlayPass();
        System.out.println("LifecycleTests: " + checks + " checks passed (API doubles; no live game/controller claim)");
    }
    private static void inputOrder() {
        List<String> trace = new ArrayList<>();
        String[] context = {"campaign-travel"}, nativeScreen = {"cargo"}, wheelOwner = {null};
        RuntimeHooks.Boundary boundary = new RuntimeHooks.Boundary() {
            public void advance() { trace.add("advance"); if (!context[0].equals(nativeScreen[0])) { wheelOwner[0] = null; context[0] = nativeScreen[0]; } }
            public void ensureAttached() { trace.add("attach:" + context[0]); }
            public void processInput(List<InputEventAPI> events) { trace.add("shortcut:" + context[0]); wheelOwner[0] = context[0]; }
        };
        RuntimeHooks.dispatchInput(boundary, List.of());
        check(wheelOwner[0].equals("cargo"), "First physical shortcut opens against the new native screen");
        boundary.advance();
        check(wheelOwner[0].equals("cargo"), "A following frame does not immediately cancel the newly opened wheel");
        check(trace.subList(0, 3).equals(List.of("advance", "attach:cargo", "shortcut:cargo")), "Context and overlay attachment precede shortcut routing");
        boolean[] reachedInput = {false};
        try {
            RuntimeHooks.dispatchInput(new RuntimeHooks.Boundary() {
                public void advance() { throw new IllegalStateException("Simulated lifecycle failure"); }
                public void ensureAttached() { throw new AssertionError("Should not attach after failed update"); }
                public void processInput(List<InputEventAPI> events) { reachedInput[0] = true; }
            }, List.of());
            throw new AssertionError("Expected simulated failure");
        } catch (IllegalStateException expected) { check(!reachedInput[0], "Failed ownership refresh cannot route new modal input"); }
        RuntimeHooks.dispatchInput(new RuntimeHooks.Boundary() {
            public void advance() { }
            public void ensureAttached() { }
            public void processInput(List<InputEventAPI> events) { check(events != null && events.isEmpty(), "Missing native event list becomes an empty input batch"); }
        }, null);
    }
    private static void campaignClock() {
        SectorPadCampaignFrame frame = new SectorPadCampaignFrame();
        check(frame.runWhilePaused(), "Campaign clock remains scheduled while native UI or pause stops simulation");
        check(SectorPadCampaignFrame.class.getDeclaredFields().length == 0, "Frame hook retains no runtime, device or campaign fields");
        int[] ticks = {0};
        SectorPadCampaignFrame.advanceIfActive(true, GameState.CAMPAIGN, () -> ticks[0]++);
        SectorPadCampaignFrame.advanceIfActive(true, GameState.CAMPAIGN, () -> ticks[0]++);
        check(ticks[0] == 2, "Frame callback advances without relying on any native input event");
        SectorPadCampaignFrame.advanceIfActive(true, GameState.TITLE, () -> ticks[0]++);
        SectorPadCampaignFrame.advanceIfActive(true, GameState.COMBAT, () -> ticks[0]++);
        SectorPadCampaignFrame.advanceIfActive(false, GameState.CAMPAIGN, () -> ticks[0]++);
        SectorPadCampaignFrame.advanceIfActive(true, null, () -> ticks[0]++);
        check(ticks[0] == 2, "Inactive or disabled campaign hooks cannot tick another game's state");
    }
    private static void transientRegistration() {
        List<EveryFrameScript> scripts = new ArrayList<>();
        EveryFrameScript unrelated = new EveryFrameScript() {
            public boolean isDone() { return false; }
            public boolean runWhilePaused() { return true; }
            public void advance(float amount) { }
        };
        scripts.add(unrelated); scripts.add(new SectorPadCampaignFrame()); scripts.add(new SectorPadCampaignFrame());
        List<String> methods = new ArrayList<>();
        SectorAPI sector = (SectorAPI) Proxy.newProxyInstance(SectorAPI.class.getClassLoader(), new Class<?>[] {SectorAPI.class}, (proxy, method, arguments) -> {
            methods.add(method.getName());
            return switch (method.getName()) {
                case "removeTransientScriptsOfClass" -> { Class<?> type = (Class<?>) arguments[0]; scripts.removeIf(type::isInstance); yield null; }
                case "addTransientScript" -> { scripts.add((EveryFrameScript) arguments[0]); yield null; }
                default -> throw new AssertionError("Unexpected lifecycle API: " + method.getName());
            };
        });
        SectorPadCampaignFrame.install(sector);
        check(scripts.size() == 2 && scripts.get(0) == unrelated, "Installation removes duplicate own scripts and preserves unrelated scripts");
        EveryFrameScript installed = scripts.get(1); SectorPadCampaignFrame.install(sector);
        check(scripts.size() == 2 && scripts.get(1) != installed, "Repeated game load replaces the one transient clock");
        check(methods.equals(List.of("removeTransientScriptsOfClass", "addTransientScript", "removeTransientScriptsOfClass", "addTransientScript")), "Only transient SectorAPI registration methods are used");
        SectorPadCampaignFrame.install(null); check(scripts.size() == 2, "Missing sector cannot change another campaign's scripts");
    }

    private static void combatOverlayPass() {
        CombatOverlayPass pass = new CombatOverlayPass();
        Object engine = new Object(), replacement = new Object();
        check(!pass.consume(engine), "Combat input without a preceding render cannot paint an extra overlay");
        pass.begin(engine, null);
        check(pass.consume(engine), "Early combat render authorizes its one late overlay pass");
        check(!pass.consume(engine), "Repeated pre-core callback cannot draw the combat overlay twice");
        pass.begin(engine, null); pass.begin(engine, null);
        check(pass.consume(engine) && !pass.consume(engine), "Duplicate render hooks still produce one late pass");
        pass.begin(engine, null);
        check(!pass.consume(replacement), "A stale combat frame cannot paint over a replacement engine");
        check(!pass.consume(engine), "Rejected stale frame is discarded");

        float[] opacity = {.7f};
        UIComponentAPI ownPanel = (UIComponentAPI) Proxy.newProxyInstance(UIComponentAPI.class.getClassLoader(), new Class<?>[] {UIComponentAPI.class}, (proxy, method, arguments) -> switch (method.getName()) {
            case "getOpacity" -> opacity[0];
            case "setOpacity" -> { opacity[0] = (float) arguments[0]; yield null; }
            default -> throw new AssertionError("Unexpected deferred panel operation: " + method.getName());
        });
        pass.begin(engine, ownPanel);
        check(opacity[0] == 0, "Only the passed mod-owned settings panel defers its native render");
        check(pass.consume(engine) && opacity[0] == .7f, "Late pass restores the original opacity before native UI input");
        pass.begin(engine, ownPanel); pass.begin(engine, ownPanel);
        check(opacity[0] == 0, "Repeated early callback leaves own panel deferred");
        pass.consume(engine);
        check(opacity[0] == .7f, "Repeated early callback cannot lose the original panel opacity");
        pass.begin(engine, ownPanel); pass.reset();
        check(opacity[0] == .7f && !pass.consume(engine), "Detach restores own panel and cancels pending draw");
        pass.begin(engine, ownPanel);
        check(!pass.consume(replacement) && opacity[0] == .7f, "Engine change restores deferred panel without painting it");
        pass.begin(null, ownPanel);
        check(opacity[0] == .7f && !pass.consume(null), "Missing engine never makes a settings panel transparent");
        try { NativeCombatCursor.verifySignatures(); check(true, "Installed public software cursor status and paint signatures resolve without drawing"); }
        catch (ReflectiveOperationException changed) { throw new AssertionError("Installed public cursor paint signature changed", changed); }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
