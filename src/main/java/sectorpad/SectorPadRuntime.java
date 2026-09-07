package sectorpad;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import sectorpad.bridge.DesktopInputBridge;
import sectorpad.bridge.ReadOnlyUiNavigator;
import sectorpad.core.*;
import sectorpad.game.*;
import sectorpad.input.SdlBackend;
import sectorpad.settings.*;
import sectorpad.ui.OverlayRenderer;
import sectorpad.ui.OverlayLayout;
import sectorpad.compat.ConsoleIntegration;
import java.util.*;

/** One game-thread owner coordinates the device, logical bindings, overlays and vanilla actions. */
public final class SectorPadRuntime implements AutoCloseable {
    private static final SectorPadRuntime INSTANCE=new SectorPadRuntime();
    public static SectorPadRuntime get(){return INSTANCE;}
    private final SdlBackend backend=new SdlBackend();
    private final DesktopInputBridge bridge=new DesktopInputBridge();
    private final GameActions game=new GameActions(this::ordinaryAction);
    private final CargoQuantityAdapter quantities=new CargoQuantityAdapter();
    private final ConsoleIntegration console=new ConsoleIntegration();
    private final ReadOnlyUiNavigator navigator=new ReadOnlyUiNavigator();
    private final SettingsService settings=new SettingsService(ProfileStore.inCommonData());
    private final InputGate gate=new InputGate();
    private final UiClock clock=new UiClock();
    private final RepeatKey navigation=new RepeatKey(),modalNavigation=new RepeatKey();
    private final ScrollAccumulator scrolling=new ScrollAccumulator(),zooming=new ScrollAccumulator();
    private final RadialModel wheel=new RadialModel();
    private final TextEntryModel keyboard=new TextEntryModel();
    private final OverlayRenderer renderer=new OverlayRenderer();
    private PadFrame raw=PadFrame.disconnected(),calibrated=PadFrame.disconnected();
    private Set<String> modalPrevious=Set.of();
    private GameContext context=new GameContext("UI","startup",false,false,"Loading");
    private String wheelContext,fieldContext,status="Connect an Xbox or Steam Deck-style controller";
    private TextFieldAPI textField;
    private ReadOnlyUiNavigator.TextTarget textCapture;
    private CargoQuantityAdapter.Target quantityTarget;
    private Object consoleTextTarget;
    private AutoCloseable pauseLease;
    private OverlayHost.Handle remapHandle;
    private LunaRemappingPanel remapPanel;
    private RadialModel.Entry confirmation;
    private Runnable deferredAction;
    private String deferredContext;
    private boolean wheelPointerMode;
    private float wheelPointerX,wheelPointerY;
    private boolean initialized,closed,precision,campaignPointer,latchedDrag,multiSelect,requireReconnectAck,wasConnected;
    private boolean outputPrepared;
    private boolean physicalModalDispatch,physicalBridgeWork,deferredControllerOwned,quantityControllerOwned;
    private boolean controllerLastInput;
    private boolean ltHeld,rtHeld,panning,recoveryOverride;
    private PendingWheel pendingWheel;
    private record PendingWheel(String title,String catalog,String opener,long started,String context){}
    private long lastAdvance,recoveryStarted,lastStatus,errors;
    private boolean recoveryFired;

    private SectorPadRuntime(){}
    public void initialize(){
        if(initialized||closed)return;initialized=true;
        settings.setListener(new SettingsService.Listener(){
            @Override public void changed(ControllerSettings prefs,BindingProfile profile){releaseInputs();}
            @Override public void statusChanged(String message){notifyStatus(message);}
        });
        settings.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{bridge.close();backend.close();},"SectorPad-input-release"));
    }
    public void onGameLoad(){initialize();cancelOverlays();settings.refresh();releaseInputs();}
    public void advance(){
        if(closed||!Display.isCreated())return;
        long now=System.nanoTime();
        // Title background and campaign hooks can both be called during transitions.
        if(lastAdvance!=0&&now-lastAdvance<1_000_000L)return;lastAdvance=now;
        try{tick(now);}catch(RuntimeException|LinkageError failure){
            errors++;game.invalidatePauseResume();releaseInputs();cancelOverlays();
            notifyStatus("SectorPad stopped input safely: "+failure.getClass().getSimpleName());
            if(errors<4)Global.getLogger(SectorPadRuntime.class).error("SectorPad input failed; native controls remain available",failure);
        }
    }
    private void tick(long now){
        initialize();float dt=clock.tick(now);settings.advance(now);
        ControllerSettings prefs=settings.settings();
        game.configureCampaign(prefs.zoomSpeed);
        String previousDevice=raw.deviceId();PadFrame previousRaw=raw;
        backend.selectDevice(prefs.controllerIndex);raw=backend.poll(now);
        if(raw.connected())settings.setDevice("sdl:"+raw.deviceName()+":standard-gamepad");
        // Menus, quantity cancellation and gameplay must share the same calibrated trigger edges.
        calibrated=calibrate(raw,prefs);
        if(raw.connected()&&prefs.enabled&&Display.isActive()&&(!raw.buttons().equals(previousRaw.buttons())||Math.abs(raw.lx()-previousRaw.lx())>.05f||Math.abs(raw.ly()-previousRaw.ly())>.05f||Math.abs(raw.rx()-previousRaw.rx())>.05f||Math.abs(raw.ry()-previousRaw.ry())>.05f||Math.abs(raw.lt()-previousRaw.lt())>.05f||Math.abs(raw.rt()-previousRaw.rt())>.05f))controllerLastInput=true;
        console.update(controllerLastInput&&raw.connected(),Display.isActive(),prefs.enabled&&prefs.consoleTopLeft,keyboard.isOpen()&&keyboard.docked(),prefs.uiScale);
        GameContext next=game.context();
        Object consoleInstance=console.activeInstance();
        if(consoleInstance!=null)next=new GameContext("UI",next.identity()+":console:"+System.identityHashCode(consoleInstance),false,true,"Console Commands");
        boolean changed=!next.identity().equals(context.identity()),pauseChanged=next.paused()!=context.paused();context=next;
        if(changed){settings.revert("The screen changed; previous controls restored.");cancelOverlays();releaseInputs();navigator.clearSelection();campaignPointer=false;multiSelect=false;}
        if(pauseChanged&&!hasModal())releaseInputs();
        if(raw.connected()&&wasConnected&&!raw.deviceId().equals(previousDevice)){
            game.invalidatePauseResume();settings.revert("The controller changed; previous controls restored.");cancelOverlays();releaseInputs();requireReconnectAck=true;
            if(prefs.pauseOnDisconnect)game.requestPause();
        }
        if(clock.interrupted()){
            // Staged keyboard-only menus contain no held game input. Preserve that work
            // through harmless long frames; controller/output ownership still fails closed.
            boolean owned=raw.connected()||wasConnected||quantities.isActive()||physicalBridgeWork||deferredAction!=null;
            game.invalidatePauseResume();releaseInputs();
            if(owned){settings.revert("Input was interrupted; previous controls restored.");cancelOverlays();requireReconnectAck=raw.connected();if(prefs.pauseOnDisconnect)game.requestPause();}
        }
        if(!Display.isActive()){
            game.invalidatePauseResume();settings.revert("The game lost focus; previous controls restored.");bridge.pump(false,context.identity());cancelOverlays();releaseInputs();
            if(wasConnected&&prefs.pauseOnDisconnect)game.requestPause();return;
        }
        if(!outputPrepared){
            // Capability startup only: there are no requested buttons, keys, wheels or text.
            bridge.pump(true,context.identity()+":capability-check");bridge.pump(false,context.identity());outputPrepared=true;
            Global.getLogger(SectorPadRuntime.class).info("SectorPad desktop input: "+bridge.capabilitySummary());
        }
        if(quantities.isActive()){
            if(quantityControllerOwned&&!raw.connected())quantities.cancel("Controller disconnected; quantity selection cancelled.");
            else {
                Set<String> pressed=pressed(calibrated.buttons(),modalPrevious);modalPrevious=calibrated.buttons();
                if(pressed.contains(settings.activeProfile().binding("UI","ui.cancel")))quantities.cancel("Quantity selection cancelled.");
                else quantities.advance(game.uiRoot(),context.identity(),now);
            }
            bridge.pump(false,context.identity()+":quantity");
            if(!quantities.isActive()){notifyStatus(quantities.status());closeModal();}
            return;
        }
        // Keyboard-operated SectorPad menus also work when no controller is connected.
        if((deferredAction!=null&&!deferredControllerOwned)||physicalBridgeWork){
            boolean ready=bridge.pump(true,context.identity());
            if(ready&&deferredAction!=null){Runnable action=deferredAction;String expected=deferredContext;deferredAction=null;
                if(Objects.equals(context.identity(),expected)){physicalBridgeWork=true;action.run();}}
            if(deferredAction==null&&bridge.pendingCount()==0)physicalBridgeWork=false;
            return;
        }
        recovery(now);
        if(!raw.connected()){
            if(wasConnected){game.invalidatePauseResume();cancelOverlays();releaseInputs();settings.revert("Controller disconnected; previous controls restored.");
                requireReconnectAck=true;if(prefs.pauseOnDisconnect)game.requestPause();}
            wasConnected=false;bridge.pump(false,context.identity());return;
        }
        wasConnected=true;
        if(remapPanel!=null){bridge.pump(false,context.identity()+":remap");return;}
        if(prefs.enabled)recoveryOverride=false;
        if(!prefs.enabled&&!recoveryOverride){bridge.pump(false,context.identity());return;}
        if(requireReconnectAck){
            bridge.pump(false,context.identity()+":reconnect");
            if(gate.armWhenNeutral(calibrated,Math.max(prefs.leftDeadzone,prefs.rightDeadzone))){
                Set<String> pressed=pressed(calibrated.buttons(),modalPrevious);
                if(pressed.contains("A")){requireReconnectAck=false;releaseInputs();notifyStatus("Controller ready. Resume from the normal pause control.");}
            }
            modalPrevious=calibrated.buttons();return;
        }
        if(settings.isPreviewing()&&!wheel.isOpen()&&!keyboard.isOpen()&&confirmation==null){
            Set<String> pressed=pressed(calibrated.buttons(),modalPrevious);
            if(pressed.contains("MENU")){settings.confirm();modalPrevious=calibrated.buttons();return;}
            if(pressed.contains("VIEW")){settings.revert("Previous controls restored.");modalPrevious=calibrated.buttons();return;}
            modalPrevious=calibrated.buttons();
        }
        if(wheel.isOpen()||keyboard.isOpen()||confirmation!=null){
            bridge.pump(true,context.identity()+":overlay");updateModal(now,prefs);return;
        }
        gate.updateOwner(context.identity()+":"+settings.activeProfile().id+":"+campaignPointer);
        boolean bridgeReady=bridge.pump(true,context.identity());
        if(deferredAction!=null&&bridgeReady){
            Runnable action=deferredAction;String expected=deferredContext;deferredAction=null;
            if(Objects.equals(context.identity(),expected))action.run();
            return;
        }
        if(!gate.armWhenNeutral(calibrated,.18f))return;
        BindingProfile profile=settings.activeProfile();String bindingContext=recoveryOverride?"UI":context.name();
        Set<String> held=new LinkedHashSet<>();
        profile.bindings(bindingContext).forEach((action,button)->{if(calibrated.down(button))held.add(action);});
        InputGate.Edges edges=gate.edges(held);
        if(edges.pressed("hub.open")){openHub();return;}
        if(recoveryOverride){game.neutralize();menuInput(profile,bindingContext,edges,now,dt,prefs);return;}
        if(pendingWheel!=null){
            if(!calibrated.down(pendingWheel.opener())||!context.identity().equals(pendingWheel.context()))pendingWheel=null;
            else if(now-pendingWheel.started()>=(long)(prefs.radialHoldSeconds*1_000_000_000L)){
                PendingWheel pending=pendingWheel;pendingWheel=null;
                openWheel(pending.catalog(),pending.title(),gameEntries(pending.catalog()),true,pending.opener());return;
            }
        }
        if(edges.pressed("combat.weapons")||edges.pressed("campaign.abilities")){
            String action=context.is("COMBAT")?"combat.weapons":"campaign.abilities";
            String title=context.is("COMBAT")?"Weapon groups":"Campaign abilities",catalog=context.is("COMBAT")?"weapons":"abilities",opener=profile.binding(bindingContext,action);
            if(prefs.wheelHoldMode)pendingWheel=new PendingWheel(title,catalog,opener,now,context.identity());
            else {openWheel(catalog,title,gameEntries(catalog),false,opener);return;}
        }
        if(edges.pressed("ui.actions")&&!console.isOpen()){openWheel("selection",context.is("TACTICAL")?"Tactical commands":"Selection actions",selectionEntries(),false,profile.binding(bindingContext,"ui.actions"));return;}
        if(edges.pressed("campaign.pointerMode")){campaignPointer=!campaignPointer;releaseInputs();notifyStatus(campaignPointer?"Campaign pointer mode":"Campaign travel mode");return;}
        if(edges.pressed("ui.precision"))precision=!precision;
        if(console.isOpen()){
            game.neutralize();consoleInput(profile,edges,now,dt,prefs);
        }else if(context.is("COMBAT")&&context.gameplayAllowed()){
            boolean twoTriggers=Set.of("LT","RT").contains(profile.binding(bindingContext,"combat.fire"))&&Set.of("LT","RT").contains(profile.binding(bindingContext,"combat.shield"));
            if(twoTriggers&&!raw.independentTriggers()&&(held.contains("combat.fire")||held.contains("combat.shield"))){
                held.remove("combat.fire");held.remove("combat.shield");
                notifyStatus("Both trigger axes are required. Open Controller Setup to use another binding.");
            }
            float[] move=vector(profile.binding(bindingContext,"combat.move"),calibrated),aim=vector(profile.binding(bindingContext,"combat.aim"),calibrated);
            game.configure(prefs.steeringMode,prefs.shieldToggle,prefs.aimRange);
            game.dispatch(held,edges.pressed(),edges.released(),move[0],move[1],aim[0],aim[1],dt);
        }else if(context.is("CAMPAIGN")&&!campaignPointer&&!context.paused()){
            float[] move=vector(profile.binding(bindingContext,"campaign.move"),calibrated);
            float[] aim=vector(profile.binding(bindingContext,"campaign.pointer"),calibrated);
            game.dispatch(held,edges.pressed(),edges.released(),move[0],move[1],aim[0],aim[1],dt);
            pointer(aim[0],aim[1],dt,prefs);
        }else if(context.is("CAMPAIGN")){
            game.dispatch(held,edges.pressed(),edges.released(),0,0,0,0,dt);
            float[] point=vector(profile.binding(bindingContext,"campaign.move"),calibrated),view=vector(profile.binding(bindingContext,"campaign.pointer"),calibrated);
            pointer(point[0],point[1],dt,prefs);
            navigator.refresh(game.uiRoot());pan(view[0],view[1],dt,prefs);
        }else{
            game.neutralize();menuInput(profile,bindingContext,edges,now,dt,prefs);
        }
    }
    private PadFrame calibrate(PadFrame frame,ControllerSettings prefs){
        if(!frame.connected()){ltHeld=rtHeld=false;return frame;}
        DeviceCalibration calibration=settings.calibration();
        float[] left=calibration.left.apply(frame.lx(),frame.ly()),right=calibration.right.apply(frame.rx(),frame.ry());
        float lt=calibration.leftTrigger.apply(frame.lt()),rt=calibration.rightTrigger.apply(frame.rt());
        ltHeld=ltHeld?lt>prefs.triggerRelease:lt>prefs.triggerPress;rtHeld=rtHeld?rt>prefs.triggerRelease:rt>prefs.triggerPress;
        Set<String> buttons=new HashSet<>(frame.buttons());buttons.remove("LT");buttons.remove("RT");
        if(ltHeld)buttons.add("LT");if(rtHeld)buttons.add("RT");
        return new PadFrame(true,frame.deviceId(),frame.deviceName(),frame.independentTriggers(),left[0],left[1],right[0],right[1],lt,rt,buttons);
    }
    private void recovery(long now){
        if(raw.down("VIEW")&&raw.down("MENU")){
            if(recoveryStarted==0)recoveryStarted=now;
            if(!recoveryFired&&now-recoveryStarted>=2_000_000_000L){
                recoveryFired=true;recoveryOverride=!settings.settings().enabled;settings.revert("Recovery opened. Previous controls restored.");openSettings();
            }
        }else{recoveryStarted=0;recoveryFired=false;}
    }
    private void menuInput(BindingProfile profile,String mode,InputGate.Edges edges,long now,float dt,ControllerSettings prefs){
        float[] pointing=vector(profile.binding(mode,"ui.pointer"),calibrated);
        float[] scroll=vector(profile.binding(mode,"ui.scroll"),calibrated);
        pointer(pointing[0],pointing[1],dt,prefs);
        navigator.refresh(game.uiRoot());
        String direction=edges.held().stream().filter(a->Set.of("ui.up","ui.down","ui.left","ui.right").contains(a)).findFirst().orElse("");
        if(navigation.pulse(direction,now,prefs.repeatDelay,prefs.repeatInterval)){
            int dx=direction.equals("ui.left")?-1:direction.equals("ui.right")?1:0;
            int dy=direction.equals("ui.down")?-1:direction.equals("ui.up")?1:0;
            navigate(dx,dy);
        }
        boolean map=context.is("MAP")||context.is("TACTICAL");
        if(map&&!navigator.hasScrollableAt(bridge.getPointerX(),bridge.getPointerY())){
            pan(scroll[0],scroll[1],dt,prefs);
            double zoom=controlAmount(profile.binding(mode,"ui.nextSubtab"))-controlAmount(profile.binding(mode,"ui.previousSubtab"));
            int notches=zooming.advance(context.identity(),zoom*prefs.zoomSpeed,dt);
            if(notches!=0&&!navigator.zoomMap(notches,bridge.getPointerX(),bridge.getPointerY()))bridge.scroll(notches);
        }else{
            if(panning){bridge.mouseUp(1);panning=false;}zooming.reset();
            double velocity=Math.copySign(Math.pow(Math.abs(scroll[1]),prefs.scrollGamma),scroll[1])*prefs.scrollSpeed*(prefs.invertScroll?-1:1);
            int notches=scrolling.advance(context.identity()+":"+navigator.scrollOwnerId(bridge.getPointerX(),bridge.getPointerY()),velocity,dt);
            if(notches!=0&&!navigator.scrollAt(0,-notches*40f,bridge.getPointerX(),bridge.getPointerY()))bridge.scroll(notches);
        }
        int tooltipKey=control("GENERAL_EXPAND_TOOLTIP",Keyboard.KEY_F1);
        if(edges.held().contains("ui.tooltip"))bridge.keyDown(tooltipKey);else bridge.keyUp(tooltipKey);
        boolean confirmHeld=edges.held().contains("ui.confirm");
        if(multiSelect&&(confirmHeld||latchedDrag))bridge.keyDown(Keyboard.KEY_LSHIFT);
        if(prefs.latchedDrag){
            if(edges.pressed("ui.confirm")){latchedDrag=!latchedDrag;if(latchedDrag)bridge.mouseDown(0);else bridge.mouseUp(0);}
        }else{if(confirmHeld)bridge.mouseDown(0);else bridge.mouseUp(0);}
        if(!multiSelect||(!confirmHeld&&!latchedDrag))bridge.keyUp(Keyboard.KEY_LSHIFT);
        if(edges.pressed("ui.secondary"))bridge.mouseClick(1);
        if(edges.pressed("ui.cancel")){
            if(latchedDrag){bridge.keyTap(Keyboard.KEY_ESCAPE);bridge.mouseUp(0);latchedDrag=false;}
            else bridge.keyTap(Keyboard.KEY_ESCAPE);
        }
        for(String action:edges.pressed()){
            if(action.equals("ui.previousTab")||action.equals("ui.nextTab")||action.equals("game.menu"))ordinaryAction(action);
            if(!map&&(action.equals("ui.previousSubtab")||action.equals("ui.nextSubtab")))ordinaryAction(action);
        }
    }
    private void consoleInput(BindingProfile profile,InputGate.Edges edges,long now,float dt,ControllerSettings prefs){
        float[] point=vector(profile.binding("UI","ui.pointer"),calibrated);pointer(point[0],point[1],dt,prefs);
        if(edges.pressed("ui.confirm"))bridge.keyTap(Keyboard.KEY_RETURN);
        if(edges.pressed("ui.cancel")||edges.pressed("game.menu")){console.closeConsole();releaseInputs();return;}
        if(edges.pressed("ui.secondary")){openKeyboard(false);return;}
        if(edges.pressed("ui.actions"))bridge.keyTap(Keyboard.KEY_TAB);
        String direction=edges.held().stream().filter(a->Set.of("ui.up","ui.down","ui.left","ui.right").contains(a)).findFirst().orElse("");
        if(navigation.pulse(direction,now,prefs.repeatDelay,prefs.repeatInterval))bridge.keyTap(switch(direction){case "ui.up"->Keyboard.KEY_UP;case "ui.down"->Keyboard.KEY_DOWN;case "ui.left"->Keyboard.KEY_LEFT;default->Keyboard.KEY_RIGHT;});
        float[] scroll=vector(profile.binding("UI","ui.scroll"),calibrated);
        int notches=scrolling.advance(context.identity(),scroll[1]*prefs.scrollSpeed*(prefs.invertScroll?-1:1),dt);
        if(notches!=0){navigator.refresh(game.uiRoot());if(!navigator.scrollAt(0,-notches*40,bridge.getPointerX(),bridge.getPointerY()))bridge.scroll(notches);}
    }
    private void pointer(float x,float y,float dt,ControllerSettings prefs){
        if(x==0&&y==0)return;
        InputMath.Vector shaped=InputMath.shape(x,y,prefs.pointerDeadzone,prefs.pointerOuter,prefs.pointerGamma);
        if(shaped.x()!=0||shaped.y()!=0)navigator.clearSelection();
        bridge.movePointerBy(shaped.x()*prefs.pointerSpeed*dt*(precision?prefs.precisionMultiplier:1),
            shaped.y()*prefs.pointerSpeed*dt*(precision?prefs.precisionMultiplier:1));
    }
    private void pan(float x,float y,float dt,ControllerSettings prefs){
        if(x==0&&y==0){if(panning)bridge.mouseUp(1);panning=false;return;}
        float px=bridge.getPointerX(),py=bridge.getPointerY();
        if(navigator.panMap(-x*prefs.mapPanSpeed*dt,-y*prefs.mapPanSpeed*dt,px,py)){
            if(panning)bridge.mouseUp(1);panning=false;return;
        }
        if(navigator.hasScrollableAt(px,py)||navigator.getCandidates().stream().anyMatch(c->c.contains(px,py))){
            if(panning)bridge.mouseUp(1);panning=false;return;
        }
        // Native mouse-drag fallback needs one frame for the game to observe the button press.
        if(!panning){bridge.mouseDown(1);panning=true;return;}
        bridge.movePointerBy(-x*prefs.mapPanSpeed*dt,-y*prefs.mapPanSpeed*dt);
    }
    private void navigate(int dx,int dy){
        if(navigator.navigate(dx,dy,bridge))return;
        if(dy!=0)bridge.keyTap(dy>0?Keyboard.KEY_UP:Keyboard.KEY_DOWN);
        else bridge.keyTap(dx<0?Keyboard.KEY_LEFT:Keyboard.KEY_RIGHT);
    }
    private void updateModal(long now,ControllerSettings prefs){
        Set<String> pressed=pressed(calibrated.buttons(),modalPrevious),released=pressed(modalPrevious,calibrated.buttons());
        modalPrevious=calibrated.buttons();
        Map<String,String> bindings=settings.activeProfile().bindings("UI");
        Set<String> actions=new HashSet<>();bindings.forEach((action,button)->{if(pressed.contains(button))actions.add(action);});
        String confirmButton=bindings.get("ui.confirm"),cancelButton=bindings.get("ui.cancel");
        if(confirmation!=null){
            if(pressed.contains(cancelButton)){confirmation=null;closeModal();}
            else if(pressed.contains(confirmButton)){RadialModel.Entry action=confirmation;confirmation=null;closeModal();defer(action.execute());}
            return;
        }
        String direction=calibrated.down(bindings.get("ui.up"))?"up":calibrated.down(bindings.get("ui.down"))?"down":calibrated.down(bindings.get("ui.left"))?"left":calibrated.down(bindings.get("ui.right"))?"right":"";
        boolean repeat=modalNavigation.pulse(direction,now,prefs.repeatDelay,prefs.repeatInterval);
        if(keyboard.isOpen()){
            if(pressed.contains(cancelButton)){keyboard.close();closeModal();return;}
            if(repeat)keyboard.move(direction.equals("left")?-1:direction.equals("right")?1:0,direction.equals("up")?-1:direction.equals("down")?1:0);
            if(actions.contains("ui.secondary"))keyboard.backspace();
            if(actions.contains("ui.actions"))keyboard.toggleShift();
            if(actions.contains("ui.previousTab"))keyboard.caret(-1);if(actions.contains("ui.nextTab"))keyboard.caret(1);
            if(actions.contains("game.menu")){acceptText();return;}
            if(pressed.contains(confirmButton)){
                String command=keyboard.select();if(command.equals("accept"))acceptText();
                else if(command.equals("cancel")){keyboard.close();closeModal();}
            }
            return;
        }
        if(pressed.contains(cancelButton)){wheel.close();closeModal();return;}
        if(!wheel.isHold()&&pressed.contains(wheel.opener())&&!wheel.opener().equals(confirmButton)){wheel.close();closeModal();return;}
        float[] aim=vector(bindings.get("ui.scroll"),calibrated);
        if(aim[0]!=0||aim[1]!=0){wheelPointerMode=true;wheel.point(aim[0],aim[1]);}
        else if(wheelPointerMode){wheel.point(0,0);wheelPointerMode=false;}
        if(repeat){wheelPointerMode=false;wheel.navigate(direction.equals("up")||direction.equals("left")?-1:1);}
        if((actions.contains("ui.nextTab")||actions.contains("ui.nextSubtab"))&&wheel.pages()>1)wheel.page(1);
        if((actions.contains("ui.previousSubtab")||(actions.contains("ui.previousTab")&&!wheel.opener().equals(bindings.get("ui.previousTab"))))&&wheel.pages()>1)wheel.page(-1);
        if((wheel.isHold()&&released.contains(wheel.opener()))||(!wheel.isHold()&&pressed.contains(confirmButton)))commitWheel();
    }
    private void commitWheel(){
        RadialModel.Entry entry=wheel.commit();closeModal();
        if(entry==null)return;
        float anchorX=wheelPointerX,anchorY=wheelPointerY;
        Runnable invoke=()->{bridge.movePointer(anchorX,anchorY);entry.execute().run();};
        if(entry.dangerous()){confirmation=new RadialModel.Entry(entry.id(),entry.label(),entry.description(),entry.enabled(),entry.reason(),true,invoke);modalPrevious=calibrated.buttons();acquirePause();}
        else defer(invoke);
    }
    private void openWheel(String stableId,String title,List<RadialModel.Entry> entries,boolean hold,String opener){
        cancelOverlays();releaseInputs();wheelContext=context.identity();
        wheelPointerX=bridge.getPointerX();wheelPointerY=bridge.getPointerY();
        entries=settings.orderWheel(context.name()+":"+stableId,title,entries,RadialModel.Entry::id,RadialModel.Entry::label);
        ControllerSettings prefs=settings.settings();wheel.open(title,entries,hold,opener,prefs.wheelSlots);
        wheelPointerMode=false;
        wheel.tune(prefs.radialDeadzone,Math.min(.25f,prefs.radialDeadzone*.7f),6);
        modalPrevious=calibrated.buttons();modalNavigation.reset();if(prefs.pauseWheels)acquirePause();
    }
    private List<RadialModel.Entry> gameEntries(String catalog){
        return game.actions(catalog).stream().map(a->new RadialModel.Entry(a.id,a.label,a.description,a.enabled,a.disabledReason,a.dangerous,a.execute)).toList();
    }
    public void openHub(){
        List<RadialModel.Entry> entries=new ArrayList<>();
        entries.add(entry("setup","Controller setup","LunaLib profiles, remapping and calibration",this::openSettings));
        entries.add(entry("keyboard","Keyboard","Type a name, search or save label",()->openKeyboard(false)));
        entries.add(entry("numeric","Number entry","Edit a focused numeric text field",()->openKeyboard(true)));
        entries.add(entry("quantity","Cargo quantity","Choose an exact quantity from the stack under the pointer",this::openQuantity));
        if(console.available())entries.add(entry("console","Console Commands","Open Console Commands with the controller layout",()->{if(!console.openControllerConsole())notifyStatus(console.status());}));
        entries.add(entry("pointer","Pointer tools","Precision, dragging and selection tools",this::openPointerTools));
        entries.addAll(gameEntries("hub"));
        entries.add(entry("help","Controls & help","Show effective controller bindings",this::openHelp));
        entries.add(entry("settings","Mod settings","Open the existing LunaLib Mod Settings menu",()->ordinaryAction("game.settings")));
        openWheel("hub","Command hub",entries,false,settings.activeProfile().binding(context.name(),"hub.open"));
    }
    private void openPointerTools(){
        List<RadialModel.Entry> entries=new ArrayList<>();
        entries.add(entry("precision","Precision "+(precision?"off":"on"),"Slow the pointer for small controls",()->precision=!precision));
        entries.add(entry("shift","Multi-select "+(multiSelect?"off":"on"),"Hold Shift during controller clicks; vanilla owns selection",()->multiSelect=!multiSelect));
        entries.add(entry("right","Secondary click","Use the selected vanilla control's secondary action",()->bridge.mouseClick(1)));
        entries.add(entry("scroll-up","Page up","Scroll the hovered pane upwards",()->bridge.keyTap(Keyboard.KEY_PRIOR)));
        entries.add(entry("scroll-down","Page down","Scroll the hovered pane downwards",()->bridge.keyTap(Keyboard.KEY_NEXT)));
        entries.add(entry("keyboard","Keyboard","Type into the focused field",()->openKeyboard(false)));
        entries.add(entry("cancel","Cancel operation","Use the normal game cancellation",()->bridge.keyTap(Keyboard.KEY_ESCAPE)));
        openWheel("pointer","Pointer tools",entries,false,"VIEW");
    }
    private List<RadialModel.Entry> selectionEntries(){
        List<RadialModel.Entry> entries=new ArrayList<>(gameEntries(context.is("TACTICAL")?"tactical":"item"));
        entries.add(entry("quantity","Cargo quantity","Pick an exact number of units through the existing cargo controls",this::openQuantity));
        entries.add(entry("number","Number entry","Enter a number in a focused text field",()->openKeyboard(true)));
        entries.add(entry("multi","Multi-select","Toggle visible Shift-click selection mode",()->multiSelect=!multiSelect));
        entries.add(entry("tools","Pointer tools","Drag, precision and page controls",this::openPointerTools));return entries;
    }
    private static RadialModel.Entry entry(String id,String label,String description,Runnable runnable){return new RadialModel.Entry(id,label,description,true,"",false,runnable);}
    public void openKeyboard(boolean numeric){
        cancelOverlays();releaseInputs();navigator.refresh(game.uiRoot());textField=navigator.focusedTextField();
        textCapture=navigator.captureText();
        consoleTextTarget=!numeric?console.activeInstance():null;
        fieldContext=context.identity();int maximum=textField==null||textField.getMaxChars()<1?256:Math.min(1024,textField.getMaxChars());
        keyboard.open(textField==null?"":textField.getText(),numeric,maximum);
        if(consoleTextTarget!=null){String input=console.input(consoleTextTarget);keyboard.open(input==null?"":input,false,4096);keyboard.title("Console command · Apply text, then confirm in console");keyboard.docked(console.controllerLayout());textField=null;}
        modalPrevious=calibrated.buttons();modalNavigation.reset();acquirePause();
    }
    private void openQuantity(){
        CargoQuantityAdapter.Target target=quantities.capture(game.uiRoot(),bridge.getPointerX(),bridge.getPointerY(),context.identity());
        if(target==null){notifyStatus(quantities.status());return;}
        openKeyboard(true);quantityTarget=target;textField=null;
        keyboard.open("",true,Integer.toString(target.maximum()).length());
        keyboard.title(target.name()+" · Quantity 1–"+target.maximum());
    }
    private void acceptText(){
        String value=keyboard.text();TextFieldAPI target=textField;String expected=fieldContext;
        ReadOnlyUiNavigator.TextTarget captured=textCapture;
        CargoQuantityAdapter.Target quantity=quantityTarget;Object consoleTarget=consoleTextTarget;int amount=0;
        if(quantity!=null){
            try{amount=Integer.parseInt(value);}catch(NumberFormatException ignored){}
            if(amount<1||amount>quantity.maximum()){notifyStatus("Enter a whole quantity from 1 to "+quantity.maximum()+".");return;}
        }
        int chosenAmount=amount;boolean fromController=!physicalModalDispatch;quantityTarget=null;
        keyboard.close();closeModal();
        if(!Objects.equals(expected,context.identity())){notifyStatus("Text entry cancelled because the screen changed.");return;}
        defer(()->{
            if(quantity!=null){
                quantityControllerOwned=fromController;
                if(quantities.start(quantity,chosenAmount,game.uiRoot(),context.identity(),System.nanoTime())&&quantities.isActive()){
                    modalPrevious=calibrated.buttons();acquirePause();
                }
                notifyStatus(quantities.status());
            }else if(consoleTarget!=null){
                if(console.applyText(consoleTarget,value))notifyStatus("Command text applied. Confirm in Console Commands to execute it.");
                else notifyStatus("Console changed; command text was not applied.");
            }else if(target!=null){
                navigator.refresh(game.uiRoot());
                if(navigator.commitCapturedText(captured,value))notifyStatus("Text applied. Confirm with the normal game control.");
                else notifyStatus("The text field changed or rejected these characters. No text was applied.");
            }else if(!bridge.typeText(value))notifyStatus("Select a text field before opening the keyboard on this platform.");
        });
    }
    public void openSettings(){
        cancelOverlays();releaseInputs();acquirePause();
        remapPanel=new LunaRemappingPanel(settings,()->LunaRemappingPanel.InputState.fromFrames(calibrated,raw,Display.isActive()),this::closeSettings);
        remapHandle=OverlayHost.get().mount(remapPanel);
        if(remapHandle==null){remapPanel=null;closeModal();notifyStatus("Controller setup could not attach to the current screen. Open the title or campaign menu.");}
    }
    private void closeSettings(){
        OverlayHost.Handle handle=remapHandle;remapHandle=null;remapPanel=null;
        if(handle!=null)handle.close();closeModal();
    }
    private void openHelp(){
        String controls=settings.activeProfile().bindings(context.name()).entrySet().stream()
            .filter(e->!e.getValue().equals("NONE")).map(e->e.getValue()+"  "+BindingProfile.actionLabel(e.getKey())).limit(8).reduce((a,b)->a+"\n"+b).orElse("");
        notifyStatus(controls);openWheel("controls","Controls · "+context.label(),settings.activeProfile().bindings(context.name()).entrySet().stream()
            .filter(e->!e.getValue().equals("NONE")).map(e->entry(e.getKey(),BindingProfile.actionLabel(e.getKey()),e.getValue()+" · "+BindingProfile.actionLabel(e.getKey()),()->{})).toList(),false,"VIEW");
    }
    private void ordinaryAction(String action){
        boolean changingTab=Set.of("ui.previousTab","ui.nextTab","ui.previousSubtab","ui.nextSubtab").contains(action);
        if(changingTab&&(latchedDrag||panning||bridge.hasOwnedMouseHold()||game.nativeOperationActive())){
            notifyStatus("Finish or cancel the current drag, pickup or selection before changing tabs.");return;
        }
        if(Boolean.getBoolean("sectorpad.debugInput"))Global.getLogger(SectorPadRuntime.class).info("SectorPad input action: "+action+"; "+bridge.capabilitySummary());
        switch(action){
            case "ui.confirm","campaign.interact" -> bridge.mouseClick(0);
            case "ui.secondary" -> bridge.mouseClick(1);
            case "game.menu","ui.cancel" -> bridge.keyTap(Keyboard.KEY_ESCAPE);
            case "ui.keyboard" -> openKeyboard(false);
            case "game.settings" -> openNativeSettings();
            case "game.codex" -> bridge.keyTap(control("GENERAL_CODEX",Keyboard.KEY_F1));
            case "combat.tactical" -> bridge.keyTap(control("SHIP_SHOW_WARROOM",Keyboard.KEY_TAB));
            case "combat.autopilot" -> bridge.keyTap(control("C2_TOGGLE_AUTOPILOT",Keyboard.KEY_U));
            case "tactical.deployment" -> bridge.keyTap(control("C2_SHOW_REINFORCEMENTS",Keyboard.KEY_G));
            case "tactical.fleetOrders" -> bridge.keyTap(control("C2_SEARCH_AND_DESTROY",Keyboard.KEY_S));
            case "tactical.fullRetreat" -> bridge.keyTap(control("C2_FULL_RETREAT",Keyboard.KEY_R));
            case "tactical.cancelAssignment" -> bridge.keyTap(control("C2_CANCEL_ASSIGNMENT",Keyboard.KEY_C));
            case "ui.previousTab" -> game.cycleCoreTab(-1);
            case "ui.nextTab" -> game.cycleCoreTab(1);
            case "ui.previousSubtab" -> bridge.keyTap(Keyboard.KEY_LEFT);
            case "ui.nextSubtab" -> bridge.keyTap(Keyboard.KEY_RIGHT);
            case "campaign.fastForward.down" -> bridge.keyDown(control("FAST_FORWARD",Keyboard.KEY_LSHIFT));
            case "campaign.fastForward.up" -> bridge.keyUp(control("FAST_FORWARD",Keyboard.KEY_LSHIFT));
            case "campaign.fastForward.toggle" -> bridge.keyTap(control("FAST_FORWARD",Keyboard.KEY_LSHIFT));
            default -> notifyStatus("Use the normal game control for "+action);
        }
    }
    private void openNativeSettings(){
        if(Global.getCurrentState()==com.fs.starfarer.api.GameState.CAMPAIGN){
            Integer key=lunalib.lunaSettings.LunaSettings.getInt("lunalib","luna_SettingsKeybind_new");bridge.keyTap(key==null?Keyboard.KEY_F3:key);
        }else{
            navigator.refresh(game.uiRoot());
            var button=navigator.getCandidates().stream().filter(c->c.label().toLowerCase(Locale.ROOT).contains("mod settings")).findFirst();
            if(button.isPresent()){bridge.movePointer(button.get().centerX(),button.get().centerY());bridge.mouseClick(0);}
            else notifyStatus("Open the title screen's Mod Settings button to adjust Luna preferences.");
        }
    }
    private int control(String enumName,int fallback){
        try{String label=Global.getSettings().getControlStringForEnumName(enumName);int key=Keyboard.getKeyIndex(label.toUpperCase(Locale.ROOT));return key==Keyboard.KEY_NONE?fallback:key;}
        catch(RuntimeException ignored){return fallback;}
    }
    /** Hook before vanilla controls. Consumes only keys owned by a visible SectorPad modal. */
    public void processInput(List<InputEventAPI> events){
        if(closed)return;initialize();
        for(InputEventAPI event:events){
            if(event.isConsumed())continue;
            if(remapPanel!=null){remapPanel.processInput(List.of(event));continue;}
            if(!hasModal()&&bridge.pendingCount()==0&&!physicalBridgeWork&&(event.isKeyDownEvent()||event.isMouseDownEvent()))controllerLastInput=false;
            if(context.is("COMBAT")&&!hasModal()&&(event.isMouseDownEvent()||(event.isKeyDownEvent()&&Set.of(Keyboard.KEY_W,Keyboard.KEY_A,Keyboard.KEY_S,Keyboard.KEY_D,Keyboard.KEY_Q,Keyboard.KEY_E).contains(event.getEventValue())))){
                game.yieldToNativeInput();releaseInputs();
            }
            if(event.isKeyDownEvent()&&!event.isRepeat()&&settings.settings().hubKeycode>0&&event.getEventValue()==settings.settings().hubKeycode){event.consume();openHub();}
            else if(event.isKeyDownEvent()&&!event.isRepeat()&&settings.settings().remapKeycode>0&&event.getEventValue()==settings.settings().remapKeycode){event.consume();openSettings();}
            else if(event.isKeyDownEvent()&&!event.isRepeat()&&settings.settings().keyboardKeycode>0&&event.getEventValue()==settings.settings().keyboardKeycode){event.consume();openKeyboard(false);}
            else if(hasModal()&&remapPanel==null&&(event.isMouseEvent()||event.isKeyboardEvent())){
                if(event.isKeyDownEvent()){
                    physicalModalDispatch=true;try{modalKey(event);}finally{physicalModalDispatch=false;}
                }else if(event.isMouseDownEvent()&&!event.isDoubleClick()){
                    physicalModalDispatch=true;try{modalMouse(event);}finally{physicalModalDispatch=false;}
                }
                event.consume();
            }
        }
    }
    private void modalMouse(InputEventAPI event){
        if(event.isRMBDownEvent()){cancelOverlays();releaseInputs();return;}
        if(!event.isLMBDownEvent())return;
        if(keyboard.isOpen()){
            var hit=renderer.keyAt(event.getX(),event.getY());if(hit==null)return;
            keyboard.move(hit.column()-keyboard.column(),hit.row()-keyboard.row());
            String command=keyboard.select();
            if(command.equals("accept"))acceptText();
            else if(command.equals("cancel")){keyboard.close();closeModal();}
        }else if(wheel.isOpen()&&renderer.pointWheel(wheel,event.getX(),event.getY()))commitWheel();
    }
    private void modalKey(InputEventAPI event){
        int key=event.getEventValue();
        if(key==Keyboard.KEY_ESCAPE){cancelOverlays();releaseInputs();return;}
        if(keyboard.isOpen()){
            switch(key){
                case Keyboard.KEY_RETURN,Keyboard.KEY_NUMPADENTER -> {if(!event.isRepeat())acceptText();}
                case Keyboard.KEY_BACK -> keyboard.backspace();
                case Keyboard.KEY_DELETE -> keyboard.delete();
                case Keyboard.KEY_LEFT -> keyboard.caret(-1);
                case Keyboard.KEY_RIGHT -> keyboard.caret(1);
                case Keyboard.KEY_HOME -> keyboard.caret(-keyboard.text().length());
                case Keyboard.KEY_END -> keyboard.caret(keyboard.text().length());
                default -> {char ch=event.getEventChar();if(ch!=0)keyboard.insert(ch);}
            }
        }else if(confirmation!=null){
            if(key==Keyboard.KEY_RETURN&&!event.isRepeat()){RadialModel.Entry action=confirmation;confirmation=null;closeModal();defer(action.execute());}
        }else if(wheel.isOpen()){
            switch(key){
                case Keyboard.KEY_UP,Keyboard.KEY_LEFT -> {wheelPointerMode=false;wheel.navigate(-1);}
                case Keyboard.KEY_DOWN,Keyboard.KEY_RIGHT -> {wheelPointerMode=false;wheel.navigate(1);}
                case Keyboard.KEY_PRIOR -> wheel.page(-1);
                case Keyboard.KEY_NEXT -> wheel.page(1);
                case Keyboard.KEY_RETURN -> {if(!event.isRepeat())commitWheel();}
                default -> { }
            }
        }
    }
    public boolean hasModal(){return wheel.isOpen()||keyboard.isOpen()||confirmation!=null||remapPanel!=null||quantities.isActive();}
    public void render(){
        if(!initialized||closed||!Display.isCreated()||!Display.isActive())return;
        ControllerSettings prefs=settings.settings();float scale=Global.getSettings().getScreenScaleMult();
        if(scale<=0)scale=1;
        String dialogTitle=confirmation!=null?"Confirm "+confirmation.label():quantities.isActive()?"Selecting cargo quantity":null;
        String dialogBody=confirmation!=null?confirmation.description():quantities.isActive()?quantities.status():"";
        String footer=prompt("UI","ui.confirm")+" Confirm   "+prompt("UI","ui.cancel")+" Cancel   "+prompt("UI","ui.nextTab")+" Next page";
        if(keyboard.isOpen())footer=prompt("UI","ui.confirm")+" Type   "+prompt("UI","ui.secondary")+" Erase   "+prompt("UI","ui.actions")+" Shift   "+prompt("UI","ui.previousTab")+"/"+prompt("UI","ui.nextTab")+" Caret   "+prompt("UI","game.menu")+" Accept   "+prompt("UI","ui.cancel")+" Cancel";
        if(!hasModal())footer=prompt(context.name(),"hub.open")+" Command hub   View + Menu (hold) Recovery";
        if(requireReconnectAck&&raw.connected())footer="Release controls, then A to reconnect. Keyboard and mouse remain available.";
        if(settings.isPreviewing()&&remapPanel==null)footer="Preview: "+(int)Math.ceil(settings.previewSecondsRemaining())+"s   Menu Keep   View Revert";
        if(remapPanel!=null)footer="Controller Setup owns input · Changes require confirmation";
        if(console.isOpen()&&!hasModal())footer=prompt("UI","ui.secondary")+" Keyboard   "+prompt("UI","ui.actions")+" Complete   "+prompt("UI","ui.confirm")+" Run command   "+prompt("UI","ui.cancel")+" Close";
        if(quantities.isActive())footer=prompt("UI","ui.cancel")+" / Escape Cancel";
        String banner=raw.connected()?context.label()+" · "+(campaignPointer?"Pointer":precision?"Precision":multiSelect?"Multi-select":"Controller"):
            backend.status().startsWith("Controller backend unavailable")?"Controller input unavailable. See starsector.log for details.":"Connect an Xbox or Steam Deck-style controller · F10 Setup";
        if(recoveryOverride)banner="Recovery controls active · Enable SectorPad in Mod settings";
        if(context.is("COMBAT")&&raw.connected())banner+=" · "+(game.isAutopilotOn()?"Autopilot":game.isPrecisionTargeting()?"Precision target":game.isTargetLocked()?"Target locked":"Manual aim");
        if(System.nanoTime()-lastStatus<5_000_000_000L)banner=status;
        List<String> diagnostics=prefs.diagnosticsEnabled?List.of("SECTORPAD / "+backend.status(),"Context: "+context.name()+" / "+context.label(),
            "Profile: "+settings.activeProfile().displayName,"L "+format(raw.lx())+", "+format(raw.ly())+"   R "+format(raw.rx())+", "+format(raw.ry()),
            "LT "+format(raw.lt())+"   RT "+format(raw.rt())+"   Independent axes: "+raw.independentTriggers(),
            "Buttons: "+raw.buttons(),bridge.capabilitySummary(),"Input errors: "+errors):List.of();
        OverlayRenderer.Aim aim=null;
        if(raw.connected()&&prefs.enabled&&context.is("COMBAT")&&!context.paused()&&!hasModal()&&!game.isAutopilotOn()){
            var point=game.getAimPoint();var engine=Global.getCombatEngine();
            if(point!=null&&engine!=null){var view=engine.getViewport();aim=new OverlayRenderer.Aim(view.convertWorldXtoScreenX(point.x),view.convertWorldYtoScreenY(point.y),game.targetLabel(),game.isTargetLocked(),game.isPrecisionTargeting());}
        }
        OverlayRenderer.Pointer pointer=null;OverlayRenderer.Focus focus=null;
        boolean navigationVisible=raw.connected()&&controllerLastInput&&prefs.enabled&&!requireReconnectAck&&!hasModal()&&!console.isOpen()&&!context.is("COMBAT");
        if(navigationVisible){
            pointer=new OverlayRenderer.Pointer(bridge.getPointerX(),bridge.getPointerY(),precision,bridge.hasOwnedMouseHold(),latchedDrag);
            var selected=context.is("CAMPAIGN")?null:navigator.selected();
            if(selected!=null&&selected.contains(bridge.getPointerX(),bridge.getPointerY())){
                OverlayLayout.Rect bounds=new OverlayLayout.Rect(selected.x(),selected.y(),selected.width(),selected.height());
                if(selected.scroller()!=null){var pane=selected.scroller().getPosition();bounds=OverlayLayout.clip(bounds,new OverlayLayout.Rect(pane.getX(),pane.getY(),pane.getWidth(),pane.getHeight()));}
                if(bounds!=null)focus=new OverlayRenderer.Focus(bounds.x(),bounds.y(),bounds.width(),bounds.height(),multiSelect);
            }
        }
        renderer.setNavigation(pointer,focus);
        boolean recentStatus=lastStatus>0&&System.nanoTime()-lastStatus<6_000_000_000L;
        boolean hints=prefs.hintsEnabled&&remapPanel==null&&!nativeSettingsOpen()&&(raw.connected()||recentStatus||Global.getCurrentState()==com.fs.starfarer.api.GameState.TITLE)&&(!console.isOpen()||console.controllerLayout());
        renderer.render(Display.getWidth()/scale,Display.getHeight()/scale,prefs.uiScale,wheel,keyboard,hints?banner:null,footer,dialogTitle,dialogBody,diagnostics,aim);
    }
    private String prompt(String context,String action){return ButtonLabels.label(settings.activeProfile().binding(context,action),settings.settings().glyphStyle,raw.deviceName());}
    private static String format(float value){return String.format(Locale.ROOT,"%.2f",value);}
    private static boolean nativeSettingsOpen(){
        try{return lunalib.backend.ui.settings.LunaSettingsUIMainPanel.Companion.getPanelOpen();}
        catch(LinkageError unavailable){return true;}
    }
    private void acquirePause(){if(pauseLease==null)pauseLease=game.acquirePause();}
    private void releasePause(){if(pauseLease!=null){try{pauseLease.close();}catch(Exception ignored){}pauseLease=null;}}
    private void closeModal(){renderer.clearInputLayout();modalNavigation.reset();releasePause();releaseInputs();textField=null;textCapture=null;}
    private void cancelOverlays(){
        renderer.clearInputLayout();
        wheel.close();keyboard.close();confirmation=null;
        quantities.cancel("Quantity selection cancelled.");quantityTarget=null;consoleTextTarget=null;
        deferredAction=null;physicalBridgeWork=false;
        if(remapPanel!=null){settings.revert("Controller Setup closed. Previous controls restored.");LunaRemappingPanel oldPanel=remapPanel;oldPanel.cancelCapture("Screen or input ownership changed.");OverlayHost.Handle handle=remapHandle;remapHandle=null;remapPanel=null;oldPanel.onClose();if(handle!=null)handle.close();}
        releasePause();textField=null;textCapture=null;
    }
    private void releaseInputs(){bridge.releaseAll();game.neutralize();gate.disarm();scrolling.reset();zooming.reset();navigation.reset();latchedDrag=false;panning=false;pendingWheel=null;}
    private void defer(Runnable action){deferredAction=action;deferredContext=context.identity();deferredControllerOwned=!physicalModalDispatch;}
    public void emergencyStop(){game.invalidatePauseResume();cancelOverlays();releaseInputs();console.restore();backend.close();}
    public void notifyStatus(String value){status=value;lastStatus=System.nanoTime();}
    private static Set<String> pressed(Set<String> now,Set<String> before){Set<String> result=new HashSet<>(now);result.removeAll(before);return result;}
    private float controlAmount(String control){return "LT".equals(control)?calibrated.lt():"RT".equals(control)?calibrated.rt():calibrated.down(control)?1:0;}
    private static float[] vector(String name,PadFrame frame){return name.equals("LEFT_STICK")?new float[]{frame.lx(),frame.ly()}:name.equals("RIGHT_STICK")?new float[]{frame.rx(),frame.ry()}:new float[]{0,0};}
    @Override public void close(){if(closed)return;cancelOverlays();releaseInputs();console.restore();settings.close();backend.close();bridge.close();closed=true;}
}
