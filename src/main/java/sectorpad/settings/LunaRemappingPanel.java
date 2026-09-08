package sectorpad.settings;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUI.elements.LunaElement;
import lunalib.lunaUI.panel.LunaBaseCustomPanelPlugin;
import org.lwjgl.input.Keyboard;
import sectorpad.core.PadFrame;
import java.awt.Color;
import static sectorpad.ui.TripadTheme.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.nio.file.Path;
import java.io.IOException;

/** Additive LunaLib controller setup. Host removes only this panel in closeOwnPanel. */
public final class LunaRemappingPanel extends LunaBaseCustomPanelPlugin {
    public static final class InputState {
        /** Calibrated controls used by navigation, neutral gating, and remapping. */
        public final Set<String> buttons;
        public final float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;
        /** Unmodified device samples used only by the diagnostic diagram and calibration readouts. */
        public final Set<String> rawButtons;
        public final float rawLeftX, rawLeftY, rawRightX, rawRightY, rawLeftTrigger, rawRightTrigger;
        public final boolean connected, focused;
        public final String deviceName;
        /** Compatibility constructor: supplied axes are already normalized and also serve as raw readouts. */
        public InputState(Set<String> buttons, float leftX, float leftY, float rightX, float rightY, float leftTrigger, float rightTrigger, boolean connected, boolean focused, String deviceName) {
            this(legacyFrame(buttons, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, connected, deviceName), focused);
        }
        private InputState(PadFrame same, boolean focused) { this(same, same, focused); }
        private InputState(PadFrame normalized, PadFrame raw, boolean focused) {
            java.util.Objects.requireNonNull(normalized, "Normalized controller frame");
            java.util.Objects.requireNonNull(raw, "Raw controller frame");
            Set<String> copy = new LinkedHashSet<>(normalized.buttons()); copy.retainAll(BindingProfile.BUTTONS);
            this.buttons = Collections.unmodifiableSet(copy);
            this.leftX = normalized.lx(); this.leftY = normalized.ly(); this.rightX = normalized.rx(); this.rightY = normalized.ry();
            this.leftTrigger = normalized.lt(); this.rightTrigger = normalized.rt();
            this.rawButtons = Collections.unmodifiableSet(new LinkedHashSet<>(raw.buttons()));
            this.rawLeftX = raw.lx(); this.rawLeftY = raw.ly(); this.rawRightX = raw.rx(); this.rawRightY = raw.ry();
            this.rawLeftTrigger = raw.lt(); this.rawRightTrigger = raw.rt();
            this.connected = raw.connected() && normalized.connected(); this.focused = focused;
            this.deviceName = raw.deviceName() == null ? "Controller" : raw.deviceName();
        }
        /** Runtime entry point. Trigger buttons retain the runtime's calibrated threshold and hysteresis. */
        public static InputState fromFrames(PadFrame normalized, PadFrame raw, boolean focused) {
            return new InputState(normalized, raw, focused);
        }
        private static PadFrame legacyFrame(Set<String> buttons, float leftX, float leftY, float rightX, float rightY, float leftTrigger, float rightTrigger, boolean connected, String deviceName) {
            Set<String> copy = new LinkedHashSet<>(buttons == null ? Set.of() : buttons); copy.retainAll(BindingProfile.BUTTONS);
            if (leftTrigger > .55f) copy.add("LT"); if (rightTrigger > .55f) copy.add("RT");
            return new PadFrame(connected, "", deviceName, true, leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, copy);
        }
        public static InputState disconnected() { return new InputState(Set.of(), 0, 0, 0, 0, 0, 0, false, true, "No controller connected"); }
        public boolean neutral() { return buttons.isEmpty() && Math.hypot(leftX, leftY) < .22 && Math.hypot(rightX, rightY) < .22 && leftTrigger < .15 && rightTrigger < .15; }
        String navigationDirection() { return buttons.contains("DPAD_UP") || leftY > .6f ? "UP" : buttons.contains("DPAD_DOWN") || leftY < -.6f ? "DOWN" : ""; }
        void sampleCapture(RemapCapture capture, long nowNanos) {
            capture.sample(buttons, leftX, leftY, rightX, rightY,
                    captureTrigger(leftTrigger, buttons.contains("LT")), captureTrigger(rightTrigger, buttons.contains("RT")), nowNanos);
        }
        private static float captureTrigger(float value, boolean pressed) {
            if (!Float.isFinite(value)) return value;
            // RemapCapture also supports legacy analog-only callers. Preserve its neutral check while
            // preventing its fixed .55 press threshold from overriding calibrated button decisions.
            return pressed ? 1f : Math.min(value, .55f);
        }
    }
    private final SettingsService service;
    private final Supplier<InputState> input;
    private final Runnable closeOwnPanel;
    private final RemapCapture capture = new RemapCapture();
    private BindingProfile draft;
    private int contextIndex, selected, profileSelected, toolSelected, pageSize = 8;
    private boolean profilesMode, toolsMode, wheelsMode, dirty, closed, armed, previewLastFrame, controllerWasConnected;
    private int wheelSelected, wheelEntrySelected;
    private String wheelId;
    private List<String> wheelDraft = new ArrayList<>();
    private boolean wheelMoving, wheelReset;
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private record ClickTarget(LunaElement element, Runnable action) { }
    private CustomPanelAPI contentPanel;
    private LabelAPI statusLabel, liveLabel, deviceLabel;
    private ControllerDiagram diagram;
    private Set<String> previous = Set.of();
    private long repeatAt;
    private String repeating = "";
    private String pendingDelete;
    private List<Path> importFiles = List.of();
    private record SetupTool(String label, Runnable execute) { }
    private SetupTool pendingTool;
    private InputState latest = InputState.disconnected();

    public LunaRemappingPanel(SettingsService service, Supplier<InputState> input, Runnable closeOwnPanel) {
        this.service = java.util.Objects.requireNonNull(service); this.input = java.util.Objects.requireNonNull(input);
        this.closeOwnPanel = closeOwnPanel == null ? () -> { } : closeOwnPanel; this.draft = service.activeProfile();
        this.previewLastFrame = service.isPreviewing();
    }
    @Override public void init() { setEnableCloseButton(false); rebuild(); }
    public boolean isCapturing() { return capture.isCapturing(); }
    public void cancelCapture(String reason) { capture.cancel(reason); service.report(reason); }
    public void closePanel() { if (isOpenedFromScript()) onClose(); else close(); }
    @Override public void onClose() {
        if (closed) return; closed = true;
        capture.cancel("Controller Setup closed.");
        if (service.isPreviewing()) service.revert("Controller Setup closed. Previous controls restored.");
        closeOwnPanel.run();
    }
    private String context() { return BindingProfile.CONTEXTS.get(contextIndex); }
    private List<String> actions() { return new ArrayList<>(draft.bindings(context()).keySet()); }
    private String selectedAction() { List<String> actions = actions(); selected = Math.max(0, Math.min(selected, actions.size() - 1)); return actions.get(selected); }
    private boolean hasPending() { return pendingDelete != null || pendingTool != null; }
    private void changeContext(int delta) { if (wheelsMode || service.isPreviewing() || capture.isCapturing() || hasPending()) return; contextIndex = Math.floorMod(contextIndex + delta, BindingProfile.CONTEXTS.size()); selected = 0; profilesMode = toolsMode = false; capture.reset(); dirty = true; }
    private void select(int delta) {
        if (capture.isCapturing() || capture.stage() == RemapCapture.Stage.CONFLICT || service.isPreviewing() || hasPending()) return;
        if (wheelsMode) { selectWheel(delta); return; }
        if (toolsMode) toolSelected = Math.floorMod(toolSelected + delta, tools().size());
        else if (profilesMode) profileSelected = Math.floorMod(profileSelected + delta, service.profiles().size());
        else selected = Math.floorMod(selected + delta, actions().size());
        dirty = true;
    }
    private void activate() {
        if (pendingTool != null) { SetupTool confirmed = pendingTool; pendingTool = null; confirmed.execute().run(); dirty = true; return; }
        if (pendingDelete != null) { service.deleteProfile(pendingDelete); pendingDelete = null; dirty = true; return; }
        if (service.isPreviewing()) { if (service.confirm()) draft = service.committedProfile(); dirty = true; return; }
        if (capture.stage() == RemapCapture.Stage.CONFLICT) { capture.swapConflict(); takeCaptureResult(); return; }
        if (wheelsMode) { activateWheel(); return; }
        if (toolsMode) { List<SetupTool> tools = tools(); tools.get(Math.min(toolSelected, tools.size() - 1)).execute().run(); dirty = true; return; }
        if (profilesMode) { service.preview(service.profiles().get(Math.min(profileSelected, service.profiles().size() - 1))); dirty = true; return; }
        if (!latest.connected) { service.report("Connect a controller to capture an input. Saved profiles remain available."); return; }
        capture.begin(draft, context(), selectedAction(), System.nanoTime()); service.report(capture.message()); dirty = true;
    }
    private void cancelLocal() {
        if (pendingTool != null) { pendingTool = null; service.report("Section reset cancelled."); dirty = true; }
        else if (pendingDelete != null) { pendingDelete = null; service.report("Profile deletion cancelled."); dirty = true; }
        else if (capture.stage() == RemapCapture.Stage.CONFLICT || capture.isCapturing()) { cancelCapture("Capture cancelled. Existing draft binding kept."); dirty = true; }
        else if (service.isPreviewing()) { service.revert("Preview cancelled. Previous controls restored."); draft = service.committedProfile(); dirty = true; }
        else if (wheelsMode) { if (wheelId != null) { wheelId = null; wheelMoving = false; wheelDraft.clear(); service.report("Returned to wheels. Unsaved wheel edits were discarded."); } else { wheelsMode = false; toolsMode = true; } dirty = true; }
        else if (profilesMode || toolsMode) { profilesMode = toolsMode = false; dirty = true; }
        else closePanel();
    }
    private void previewDraft() {
        if (capture.isCapturing() || capture.stage() == RemapCapture.Stage.CONFLICT || hasPending() || service.isPreviewing()) return;
        if (wheelsMode) { saveWheelDraft(); return; }
        if (toolsMode) return;
        else if (profilesMode) activate();
        else { service.preview(draft); dirty = true; }
    }
    private void secondary() {
        if (service.isPreviewing() || capture.isCapturing() || capture.stage() == RemapCapture.Stage.CONFLICT || hasPending()) return;
        if (wheelsMode) {
            if (wheelId != null) { wheelDraft = new ArrayList<>(service.defaultWheelEntryIds(wheelId)); wheelEntrySelected = 0; wheelMoving = false; wheelReset = true; dirty = true; service.report("Default wheel order loaded into the draft. Y saves; B discards."); }
            return;
        }
        if (toolsMode) return;
        if (profilesMode) { draft = service.duplicateCurrentProfile(); profilesMode = false; dirty = true; return; }
        if (BindingProfile.required(selectedAction())) { service.report("This action is required. Capture another control or swap it with another action."); return; }
        draft = draft.withBinding(context(), selectedAction(), BindingProfile.NONE); service.report("Action unassigned in the draft. Preview before saving."); dirty = true;
    }
    private void takeCaptureResult() {
        if (capture.stage() == RemapCapture.Stage.READY) { draft = capture.result(); service.report(capture.message()); capture.reset(); dirty = true; }
    }
    @Override public void advance(float amount) {
        if (closed || !sectorpad.game.RuntimeHooks.isEnabled()) return;
        try { advancePanel(amount); }
        catch (RuntimeException | LinkageError failure) { sectorpad.game.RuntimeHooks.fail("runtime.setup_advance", failure); }
    }
    private void advancePanel(float amount) {
        if (closed) return;
        service.advance();
        if (previewLastFrame && !service.isPreviewing()) { draft = service.committedProfile(); dirty = true; }
        previewLastFrame = service.isPreviewing();
        latest = input.get(); if (latest == null) latest = InputState.disconnected();
        boolean controllerLost = controllerWasConnected && !latest.connected;
        controllerWasConnected = latest.connected;
        Set<String> pressed = new LinkedHashSet<>(latest.buttons); pressed.removeAll(previous); previous = latest.buttons;
        if (controllerLost || !latest.focused) {
            if (capture.isCapturing() || capture.stage() == RemapCapture.Stage.CONFLICT) cancelCapture("Controller or focus lost. Capture cancelled.");
            if (service.isPreviewing()) service.revert("Controller or focus lost. Previous controls restored.");
            armed = false;
        } else if (!latest.connected) {
            armed = false;
        } else if (!armed) { if (latest.neutral()) armed = true; }
        else if (capture.isCapturing()) {
            latest.sampleCapture(capture, System.nanoTime());
            service.report(capture.message()); takeCaptureResult();
            if (capture.stage() == RemapCapture.Stage.CONFLICT || capture.stage() == RemapCapture.Stage.CANCELLED) dirty = true;
        } else {
            if (pressed.contains("B")) cancelLocal();
            else if (pressed.contains("MENU")) closePanel();
            else if (pressed.contains("A")) activate();
            else if (pressed.contains("Y")) previewDraft();
            else if (pressed.contains("X")) secondary();
            else if (pressed.contains("VIEW")) nextSection();
            else if (pressed.contains("LB")) changeContext(-1);
            else if (pressed.contains("RB")) changeContext(1);
            if (!service.isPreviewing() && capture.stage() != RemapCapture.Stage.CONFLICT) repeatNavigation();
        }
        if (closed) return;
        if (dirty) rebuild();
        updateLive();
    }
    private void repeatNavigation() {
        String direction = latest.navigationDirection();
        long now = System.nanoTime();
        if (direction.isEmpty()) { repeating = ""; return; }
        if (!direction.equals(repeating)) { repeating = direction; select(direction.equals("UP") ? -1 : 1); repeatAt = now + (long) (service.settings().repeatDelay * 1_000_000_000L); }
        else if (now - repeatAt >= 0) { select(direction.equals("UP") ? -1 : 1); repeatAt = now + (long) (service.settings().repeatInterval * 1_000_000_000L); }
    }
    @Override public void processInput(List<InputEventAPI> events) {
        if (closed || !sectorpad.game.RuntimeHooks.isEnabled()) return;
        try { processPanelInput(events); }
        catch (RuntimeException | LinkageError failure) { sectorpad.game.RuntimeHooks.fail("runtime.setup_input", failure); }
    }
    private void processPanelInput(List<InputEventAPI> events) {
        if (closed) return;
        for (InputEventAPI event : events) {
            if (closed) { if (event.isKeyboardEvent() || event.isMouseEvent()) event.consume(); continue; }
            if (event.isConsumed()) continue;
            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();
                if (event.isRepeat() && key != Keyboard.KEY_UP && key != Keyboard.KEY_DOWN && key != Keyboard.KEY_LEFT && key != Keyboard.KEY_RIGHT) { event.consume(); continue; }
                if (key == Keyboard.KEY_ESCAPE) cancelLocal();
                else if (!capture.isCapturing()) {
                    if (key == Keyboard.KEY_RETURN) activate();
                    else if (key == Keyboard.KEY_UP) select(-1);
                    else if (key == Keyboard.KEY_DOWN) select(1);
                    else if (key == Keyboard.KEY_LEFT) changeContext(-1);
                    else if (key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_TAB) changeContext(1);
                    else if (key == Keyboard.KEY_Y) previewDraft();
                    else if (key == Keyboard.KEY_DELETE || key == Keyboard.KEY_X) secondary();
                    else if (key == Keyboard.KEY_P) nextSection();
                }
                event.consume();
            } else if (event.isKeyUpEvent()) event.consume();
            else if (event.isMouseScrollEvent()) { if (event.getEventValue() != 0) select(event.getEventValue() > 0 ? -1 : 1); event.consume(); }
            else if (event.isMouseDownEvent()) {
                // The pre-core host dispatches our buttons before vanilla root panels can
                // consume clicks. These are the actual Luna elements' public positions.
                if (!dirty && event.isLMBDownEvent() && !event.isDoubleClick()) {
                    for (ClickTarget target : List.copyOf(clickTargets)) {
                        if (target.element.getPosition().containsEvent(event)) {
                            event.consume(); target.element.playClickSound(); target.action.run(); break;
                        }
                    }
                }
                event.consume();
            } else if (event.isMouseEvent()) {
                event.consume();
            }
        }
        // Never call Luna's script-mounted Escape handler: it changes a shared native-menu flag.
    }
    private void nextSection() {
        if (isCapturing() || service.isPreviewing() || hasPending()) return;
        if (profilesMode) {
            profilesMode = false; toolsMode = true;
            try { importFiles = service.store().importableFiles(); }
            catch (IOException failure) { importFiles = List.of(); service.report("Import folder could not be read: " + failure.getMessage()); }
        } else if (toolsMode) { toolsMode = false; openWheels(); }
        else if (wheelsMode) { wheelsMode = false; wheelId = null; wheelDraft.clear(); wheelMoving = false; }
        else profilesMode = true;
        capture.reset(); dirty = true;
    }
    private void reconnectController() {
        capture.cancel("Controller discovery restarted.");armed=false;previous=Set.of();
        service.reconnectController();dirty=true;
    }
    private List<SetupTool> tools() {
        List<SetupTool> tools = new ArrayList<>();
        tools.add(new SetupTool("Reconnect controller", this::reconnectController));
        tools.add(new SetupTool("Export diagnostic report", () -> service.report(sectorpad.diagnostics.Diagnostics.exportReport())));
        tools.add(new SetupTool("Save Luna calibration for this controller", () -> service.applyLunaCalibrationToDevice()));
        tools.add(new SetupTool("Restore standard controls", () -> { capture.reset(); draft = BindingProfile.defaults(); service.preview(draft); }));
        tools.add(new SetupTool("Duplicate committed profile", () -> { draft = service.duplicateCurrentProfile(); profilesMode = toolsMode = false; }));
        tools.add(new SetupTool("Export committed profile", () -> service.exportCurrentProfile()));
        tools.add(new SetupTool("Delete selected saved profile", this::requestDelete));
        tools.add(new SetupTool("Customize command wheel order", this::openWheels));
        for (String section : BindingProfile.CONTEXTS) tools.add(new SetupTool("Reset " + section + " bindings", () -> {
            capture.reset(); draft = draft.resetContext(section); service.preview(draft); service.report("Previewing standard " + section + " bindings. Other contexts are unchanged. A keeps; B reverts.");
        }));
        for (DeviceCalibration.Section section : DeviceCalibration.Section.values()) tools.add(new SetupTool("Reset device " + section.name().toLowerCase(Locale.ROOT).replace('_', ' '), () -> requestReset("device " + section.name().toLowerCase(Locale.ROOT).replace('_', ' '), () -> service.resetCalibration(section))));
        for (PreferenceResets.Section section : PreferenceResets.Section.values()) {
            tools.add(new SetupTool("Reset " + section.label + (service.activePreferenceResets().contains(section) ? " (defaults active)" : ""), () -> requestReset(section.label, () -> service.resetPreferenceSection(section))));
            if (service.activePreferenceResets().contains(section)) tools.add(new SetupTool("Use Luna values: " + section.label, () -> requestReset("use Luna values for " + section.label, () -> service.useLunaPreferenceSection(section))));
        }
        tools.add(new SetupTool("Reset all wheel orders for this profile", () -> requestReset("all wheel orders for this profile", () -> service.resetAllWheelOrders())));
        for (Path path : importFiles) tools.add(new SetupTool("Import " + path.getFileName(), () -> service.importProfile(path)));
        return tools;
    }
    private void requestDelete() {
        if (service.isPreviewing() || hasPending()) return;
        List<BindingProfile> profiles = service.profiles();
        pendingDelete = profiles.get(Math.min(profileSelected, profiles.size() - 1)).id;
        service.report("Delete profile " + pendingDelete + "? A confirms; B cancels. The active and built-in profiles are protected."); dirty = true;
    }
    private void requestReset(String label, Runnable reset) {
        if (service.isPreviewing() || hasPending()) return;
        pendingTool = new SetupTool(label, reset); service.report("Confirm reset: " + label + ". A / Enter confirms; B / Escape cancels."); dirty = true;
    }
    private void openWheels() {
        if (service.isPreviewing() || hasPending()) return;
        profilesMode = toolsMode = false; wheelsMode = true; wheelId = null; wheelMoving = false; wheelDraft.clear();
        service.report("Choose a wheel. Open a game wheel once to discover its commands. Orders are saved separately for each profile."); dirty = true;
    }
    private void activateWheel() {
        if (wheelId == null) {
            List<SettingsService.WheelCatalog> catalogs = service.wheelCatalogs();
            if (catalogs.isEmpty()) { service.report("Open the command hub or another game wheel once, then return here to customize it."); return; }
            wheelSelected = Math.min(wheelSelected, catalogs.size() - 1); wheelId = catalogs.get(wheelSelected).id();
            wheelDraft = new ArrayList<>(service.wheelEntries(wheelId).stream().map(SettingsService.WheelItem::id).toList()); wheelEntrySelected = 0; wheelMoving = wheelReset = false;
        } else if (!wheelDraft.isEmpty()) { wheelMoving = !wheelMoving; service.report(wheelMoving ? "Move this command with up/down, then A / Enter to place it. Y saves the wheel order." : "Command placed in the draft. Y saves; B discards unsaved edits."); }
        dirty = true;
    }
    private void selectWheel(int delta) {
        if (wheelId == null) { int size = service.wheelCatalogs().size(); if (size > 0) wheelSelected = Math.floorMod(wheelSelected + delta, size); }
        else if (!wheelDraft.isEmpty()) {
            int next = wheelMoving ? Math.max(0, Math.min(wheelEntrySelected + delta, wheelDraft.size() - 1)) : Math.floorMod(wheelEntrySelected + delta, wheelDraft.size());
            if (wheelMoving && next != wheelEntrySelected) { String id = wheelDraft.remove(wheelEntrySelected); wheelDraft.add(next, id); wheelReset = false; }
            wheelEntrySelected = next;
        }
        dirty = true;
    }
    private void saveWheelDraft() {
        if (wheelId == null) return;
        boolean saved = wheelReset ? service.resetWheelOrder(wheelId) : service.saveWheelOrder(wheelId, wheelDraft);
        if (saved) { wheelMoving = wheelReset = false; wheelDraft = new ArrayList<>(service.wheelEntries(wheelId).stream().map(SettingsService.WheelItem::id).toList()); }
        dirty = true;
    }
    private void rebuild() {
        dirty = false;
        clickTargets.clear();
        if (contentPanel != null) getPanel().removeComponent(contentPanel);
        float width = getPanel().getPosition().getWidth() - 24f, height = getPanel().getPosition().getHeight() - 20f;
        boolean compact = height < 620;
        contentPanel = getPanel().createCustomPanel(width, height, null); getPanel().addComponent(contentPanel); contentPanel.getPosition().inTL(12, 10);
        TooltipMakerAPI ui = contentPanel.createUIElement(width, height, false); contentPanel.addUIElement(ui); ui.getPosition().inTL(0, 0);
        LabelAPI title = ui.addSectionHeading("SectorPad Controller Setup", Alignment.MID, 0); title.getPosition().inTL(0, 0);
        float tabWidth = (width - 5f * (BindingProfile.CONTEXTS.size() - 1)) / BindingProfile.CONTEXTS.size();
        for (int i = 0; i < BindingProfile.CONTEXTS.size(); i++) {
            int index = i;
            button(ui, BindingProfile.CONTEXTS.get(i), i * (tabWidth + 5), 27, tabWidth, 27, i == contextIndex && !profilesMode && !toolsMode && !wheelsMode, () -> { if (!isCapturing() && !service.isPreviewing() && !hasPending()) { contextIndex = index; profilesMode = toolsMode = wheelsMode = false; wheelId = null; wheelDraft.clear(); selected = 0; capture.reset(); dirty = true; } });
        }
        float diagramWidth = Math.min(440f, width * .5f);
        float diagramHeight = compact ? 110 : 145;
        diagram = new ControllerDiagram(ui, diagramWidth, diagramHeight); diagram.getPosition().inTL(0, 62);
        float infoX = diagramWidth + 12, infoWidth = width - infoX;
        LunaElement info = new LunaElement(ui, infoWidth, diagramHeight); info.getPosition().inTL(infoX, 62); info.setRenderBackground(false); info.setRenderBorder(false);
        deviceLabel = info.getInnerElement().addPara("Controller", 0);
        info.getInnerElement().addPara("Profile: " + draft.displayName + (compact ? "\nA / Enter: choose   Y: preview\nArrows: select / context   P: sections" : "\nD-pad / up-down: select\nLB-RB / left-right: context\nA / Enter: choose   X: unassign\nY: preview   View / P: sections"), 5f);
        info.getInnerElement().addPara("Recovery: hold View + Menu for 2 seconds.", 7f, FOCUS);
        liveLabel = ui.addPara("Live input", 0); liveLabel.getPosition().inTL(0, diagramHeight + 69);
        statusLabel = ui.addPara("", 0); statusLabel.getPosition().inTL(0, diagramHeight + 92);
        float listTop = compact ? 240 : 280, rowHeight = 29f;
        pageSize = Math.max(1, (int) ((height - listTop - 86) / rowHeight));
        if (wheelsMode) buildWheels(ui, width, listTop, rowHeight);
        else if (toolsMode) buildTools(ui, width, listTop, rowHeight);
        else if (profilesMode) buildProfiles(ui, width, listTop, rowHeight);
        else buildActions(ui, width, listTop, rowHeight);
        float footer = height - 75, gap = 8, third = (width - 2 * gap) / 3;
        if (wheelsMode) {
            button(ui, wheelId == null ? "A / Enter: Choose wheel" : "Y: Save wheel order", 0, footer, third, 30, false, wheelId == null ? this::activateWheel : this::saveWheelDraft);
            button(ui, "B / Escape: Back", third + gap, footer, third, 30, false, this::cancelLocal);
            button(ui, "Close", 2 * (third + gap), footer, third, 30, false, this::closePanel);
            button(ui, "Bindings / View / P", 0, footer + 36, third, 27, false, this::nextSection);
            button(ui, "X: Draft default order", third + gap, footer + 36, third, 27, false, this::secondary);
            button(ui, wheelMoving ? "A / Enter: Place command" : "A / Enter: Move command", 2 * (third + gap), footer + 36, third, 27, wheelMoving, this::activateWheel);
            return;
        }
        String primary = pendingTool != null ? "A / Enter: Confirm reset" : pendingDelete != null ? "A / Enter: Confirm delete" : service.isPreviewing() ? "A / Enter: Keep controls" : capture.stage() == RemapCapture.Stage.CONFLICT ? "A / Enter: Swap controls" : "Y: Preview draft";
        Runnable primaryAction;
        if (pendingTool != null) { SetupTool expected = pendingTool; primaryAction = () -> { if (pendingTool == expected) activate(); }; }
        else if (pendingDelete != null) { String expectedDelete = pendingDelete; primaryAction = () -> { if (expectedDelete.equals(pendingDelete)) activate(); }; }
        else if (service.isPreviewing()) { BindingProfile expectedPreview = service.activeProfile(); primaryAction = () -> { if (service.isPreviewing() && expectedPreview.equals(service.activeProfile())) activate(); }; }
        else if (capture.stage() == RemapCapture.Stage.CONFLICT) primaryAction = () -> { if (capture.stage() == RemapCapture.Stage.CONFLICT) activate(); };
        else primaryAction = this::previewDraft;
        button(ui, primary, 0, footer, third, 30, service.isPreviewing(), primaryAction);
        button(ui, hasPending() ? "B / Escape: Cancel" : service.isPreviewing() ? "B: Revert controls" : "Restore standard draft", third + gap, footer, third, 30, false, () -> {
            if (service.isPreviewing() || hasPending()) cancelLocal();
            else { capture.reset(); draft = BindingProfile.defaults(); profilesMode = toolsMode = false; service.report("Standard controls loaded into the draft. Preview before saving."); dirty = true; }
        });
        button(ui, "Close / Escape", 2 * (third + gap), footer, third, 30, false, this::closePanel);
        button(ui, (profilesMode ? "Tools" : toolsMode ? "Wheels" : "Profiles") + " / View / P", 0, footer + 36, third, 27, profilesMode || toolsMode, this::nextSection);
        button(ui, "Save device calibration", third + gap, footer + 36, third, 27, false, () -> { if (!hasPending() && !service.isPreviewing() && !isCapturing()) service.applyLunaCalibrationToDevice(); });
        button(ui, "Reconnect controller", 2 * (third + gap), footer + 36, third, 27, false, () -> { if (!hasPending()) reconnectController(); });
    }
    private void buildActions(TooltipMakerAPI ui, float width, float top, float rowHeight) {
        List<String> actions = actions(); int start = selected / pageSize * pageSize;
        for (int i = start; i < Math.min(actions.size(), start + pageSize); i++) {
            String action = actions.get(i); int index = i;
            String label = (i == selected ? "> " : "  ") + BindingProfile.actionLabel(action) + "   [" + sectorpad.core.ButtonLabels.label(draft.binding(context(), action),service.settings().glyphStyle,latest.deviceName) + "]";
            button(ui, label, 0, top + (i - start) * rowHeight, width - 92, rowHeight - 3, i == selected, () -> { if (!hasPending() && !isCapturing() && !service.isPreviewing()) { selected = index; activate(); } });
        }
        button(ui, "Up", width - 84, top, 84, 27, false, () -> select(-1));
        button(ui, "Down", width - 84, top + 33, 84, 27, false, () -> select(1));
        LunaElement count = new LunaElement(ui, 84, 48); count.getPosition().inTL(width - 84, top + 66); count.setRenderBackground(false); count.setRenderBorder(false);
        count.addText((selected + 1) + " / " + actions.size(), INK, FOCUS, List.of()); count.centerText();
    }
    private void buildProfiles(TooltipMakerAPI ui, float width, float top, float rowHeight) {
        List<BindingProfile> profiles = service.profiles(); profileSelected = Math.min(profileSelected, profiles.size() - 1); int start = profileSelected / pageSize * pageSize;
        for (int i = start; i < Math.min(profiles.size(), start + pageSize); i++) {
            BindingProfile profile = profiles.get(i); int index = i;
            button(ui, profile.displayName + " [" + profile.id + "]" + (profile.id.equals(service.committedProfile().id) ? "  saved active" : ""), 0, top + (i - start) * rowHeight, width - 180, rowHeight - 3, i == profileSelected, () -> { if (!hasPending() && !service.isPreviewing()) { profileSelected = index; activate(); } });
        }
        button(ui, "X: Duplicate current", width - 170, top, 170, 29, false, this::secondary);
        button(ui, "Delete selected", width - 170, top + 35, 170, 29, false, this::requestDelete);
    }
    private void buildTools(TooltipMakerAPI ui, float width, float top, float rowHeight) {
        List<SetupTool> tools = tools(); toolSelected = Math.min(toolSelected, tools.size() - 1); int start = toolSelected / pageSize * pageSize;
        for (int i = start; i < Math.min(tools.size(), start + pageSize); i++) {
            SetupTool tool = tools.get(i); int index = i;
            button(ui, tool.label(), 0, top + (i - start) * rowHeight, width - 92, rowHeight - 3, i == toolSelected, () -> { if (!hasPending() && !service.isPreviewing()) { toolSelected = index; activate(); } });
        }
        button(ui, "Up", width - 84, top, 84, 27, false, () -> select(-1));
        button(ui, "Down", width - 84, top + 33, 84, 27, false, () -> select(1));
    }
    private void buildWheels(TooltipMakerAPI ui, float width, float top, float rowHeight) {
        if (wheelId == null) {
            List<SettingsService.WheelCatalog> catalogs = service.wheelCatalogs();
            if (catalogs.isEmpty()) { LabelAPI label = ui.addPara("Open the command hub or another game wheel once, then return here. Its commands will be available to reorder.", 0); label.getPosition().inTL(0, top); }
            else {
                wheelSelected = Math.min(wheelSelected, catalogs.size() - 1); int start = wheelSelected / pageSize * pageSize;
                for (int i = start; i < Math.min(catalogs.size(), start + pageSize); i++) {
                    int index = i; SettingsService.WheelCatalog catalog = catalogs.get(i);
                    button(ui, catalog.label(), 0, top + (i - start) * rowHeight, width - 92, rowHeight - 3, i == wheelSelected, () -> { wheelSelected = index; activateWheel(); });
                }
            }
        } else {
            Map<String, SettingsService.WheelItem> labels = new LinkedHashMap<>(); service.wheelEntries(wheelId).forEach(item -> labels.put(item.id(), item));
            wheelEntrySelected = Math.max(0, Math.min(wheelEntrySelected, wheelDraft.size() - 1)); int start = wheelEntrySelected / pageSize * pageSize;
            for (int i = start; i < Math.min(wheelDraft.size(), start + pageSize); i++) {
                int index = i; String id = wheelDraft.get(i); SettingsService.WheelItem item = labels.get(id);
                String label = (i + 1) + ". " + (item == null ? id : item.label()) + (item == null || !item.available() ? " [not currently available]" : "") + (wheelMoving && i == wheelEntrySelected ? " [moving]" : "");
                button(ui, label, 0, top + (i - start) * rowHeight, width - 92, rowHeight - 3, i == wheelEntrySelected, () -> { if (wheelMoving) selectWheel(index - wheelEntrySelected); else wheelEntrySelected = index; activateWheel(); });
            }
            if (wheelDraft.isEmpty()) { LabelAPI label = ui.addPara("No commands were observed for this wheel in the current session. Y can still restore its default order.", 0); label.getPosition().inTL(0, top); }
        }
        button(ui, "Up", width - 84, top, 84, 27, false, () -> select(-1));
        button(ui, "Down", width - 84, top + 33, 84, 27, false, () -> select(1));
    }
    private void updateLive() {
        if (diagram == null) return; diagram.update(latest);
        deviceLabel.setText(shorten(latest.connected ? latest.deviceName : service.controllerStatus(), 65) + (latest.focused ? "" : " — focus lost"));
        BindingProfile shown = service.isPreviewing() ? service.activeProfile() : draft;
        List<String> matches = new ArrayList<>();
        shown.bindings(context()).forEach((action, control) -> { if (latest.buttons.contains(control)) matches.add(BindingProfile.actionLabel(action)); });
        liveLabel.setText(wheelsMode ? wheelId == null ? "Wheel order: choose a wheel to customize for " + service.committedProfile().displayName : "Wheel: " + wheelId + "   A / Enter: " + (wheelMoving ? "place" : "move") + "   Up/down: " + (wheelMoving ? "reorder" : "select") + "   Y: save" : String.format(Locale.ROOT, "Raw LT %.2f  RT %.2f   %s: %s", latest.rawLeftTrigger, latest.rawRightTrigger, context().toLowerCase(Locale.ROOT), matches.isEmpty() ? "release controls to see the next input" : String.join(", ", matches)));
        String status = service.isPreviewing() ? String.format(Locale.ROOT, "PREVIEW — %.0f seconds remaining. A keeps these controls; B restores the previous profile.", Math.ceil(service.previewSecondsRemaining())) : service.status();
        statusLabel.setText(shorten(status, 175)); statusLabel.setColor(service.isPreviewing() ? FOCUS : INK);
    }
    private static String shorten(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit - 1) + "…"; }
    private void button(TooltipMakerAPI ui, String text, float x, float y, float width, float height, boolean selected, Runnable action) {
        LunaElement element = new LunaElement(ui, width, height) {
            @Override public void onClick(InputEventAPI event) {
                // LunaElement dispatches even consumed events and all mouse buttons.
                if (closed || event.isConsumed() || !event.isLMBDownEvent() || event.isDoubleClick()) return;
                event.consume(); playClickSound(); action.run();
            }
            @Override public void onHoverEnter(InputEventAPI event) { setBorderColor(FOCUS); }
            @Override public void onHoverExit(InputEventAPI event) { setBorderColor(selected ? FOCUS : KEY); }
        };
        element.getPosition().inTL(x, y); element.setSelectionGroup("sectorpad.setup");
        clickTargets.add(new ClickTarget(element, action));
        element.setBorderColor(selected ? FOCUS : KEY);
        element.addText(text, selected ? FOCUS : INK, FOCUS, List.of()); element.centerText();
    }
    private static final class ControllerDiagram extends LunaElement {
        private final Map<String, LunaElement> controls = new LinkedHashMap<>();
        private final LunaElement leftStick, rightStick;
        private final float verticalScale;
        ControllerDiagram(TooltipMakerAPI ui, float width, float height) {
            super(ui, width, height); verticalScale = height / 145f; setRenderBackground(false); setRenderBorder(false);
            float unit = width / 10f;
            tile("LT", .1f * unit, 0, 1.1f * unit, 23); tile("LB", 1.3f * unit, 0, 1.1f * unit, 23);
            tile("RB", 7.6f * unit, 0, 1.1f * unit, 23); tile("RT", 8.8f * unit, 0, 1.1f * unit, 23);
            tile("VIEW", 3.5f * unit, 29, 1.4f * unit, 24); tile("MENU", 5.1f * unit, 29, 1.4f * unit, 24);
            tile("Y", 8f * unit, 30, .8f * unit, 22); tile("X", 7.1f * unit, 54, .8f * unit, 22);
            tile("B", 8.9f * unit, 54, .8f * unit, 22); tile("A", 8f * unit, 78, .8f * unit, 22);
            tile("DPAD_UP", 1.6f * unit, 80, 1.1f * unit, 19); tile("DPAD_LEFT", .4f * unit, 101, 1.1f * unit, 19);
            tile("DPAD_RIGHT", 2.8f * unit, 101, 1.1f * unit, 19); tile("DPAD_DOWN", 1.6f * unit, 122, 1.1f * unit, 19);
            tile("L3", .6f * unit, 29, .8f * unit, 22); tile("R3", 6.3f * unit, 115, .8f * unit, 22);
            leftStick = analog("Left stick", .1f * unit, 54, 3.3f * unit, 24);
            rightStick = analog("Right stick", 4.7f * unit, 86, 3.2f * unit, 24);
        }
        private void tile(String name, float x, float y, float width, float height) {
            LunaElement tile = new LunaElement(getInnerElement(), width, height * verticalScale); tile.getPosition().inTL(x, y * verticalScale);
            String label = name.replace("DPAD_", "").replace("LEFT", "<").replace("RIGHT", ">").replace("UP", "^").replace("DOWN", "v");
            tile.addText(label, INK, FOCUS, List.of()); tile.centerText(); controls.put(name, tile);
        }
        private LunaElement analog(String name, float x, float y, float width, float height) {
            LunaElement tile = new LunaElement(getInnerElement(), width, height * verticalScale); tile.getPosition().inTL(x, y * verticalScale); tile.setRenderBackground(false); tile.setRenderBorder(false);
            tile.addText(name, INK, FOCUS, List.of()); tile.centerText(); return tile;
        }
        void update(InputState state) {
            controls.forEach((control, tile) -> { boolean held = state.rawButtons.contains(control); tile.setBackgroundColor(held ? FOCUS.darker() : KEY.darker()); tile.setBorderColor(held ? FOCUS : KEY); });
            leftStick.changeText(String.format(Locale.ROOT, "Raw L %+.2f %+.2f", state.rawLeftX, state.rawLeftY), List.of()); leftStick.centerText();
            rightStick.changeText(String.format(Locale.ROOT, "Raw R %+.2f %+.2f", state.rawRightX, state.rawRightY), List.of()); rightStick.centerText();
        }
    }
}
