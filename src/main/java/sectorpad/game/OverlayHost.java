package sectorpad.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import lunalib.lunaUI.panel.LunaBaseCustomPanelPlugin;
import org.lwjgl.opengl.GL11;

/** Adds only SectorPad-owned panels to the existing root; never replaces a game panel. */
public final class OverlayHost {
    private static final OverlayHost INSTANCE = new OverlayHost();
    private UIPanelAPI parent;
    private UIPanelAPI renderParent;
    private CustomPanelAPI renderPanel;
    private Handle settings;
    private final CombatOverlayPass combatPass = new CombatOverlayPass();
    private boolean failed;

    private OverlayHost() {}
    public static OverlayHost get() { return INSTANCE; }
    public boolean isAttached() { return renderPanel != null && renderParent == StateAccess.renderingRoot() && containsRenderPanel(); }
    public boolean isMounted() { return settings != null && !settings.closed; }

    /** Run before vanilla UI input, when moving our component cannot interrupt child iteration. */
    public void ensureAttached() {
        UIPanelAPI root = StateAccess.uiRoot();
        UIPanelAPI upper = StateAccess.renderingRoot();
        if (root != parent || upper != renderParent) {
            detach();
            parent = root;
            renderParent = upper;
            failed = false;
        }
        if (root == null || upper == null || failed) return;
        try {
            float width = Global.getSettings().getScreenWidth();
            float height = Global.getSettings().getScreenHeight();
            if (renderPanel != null && !containsRenderPanel()) renderPanel = null;
            if (renderPanel == null) {
                renderPanel = Global.getSettings().createCustom(width, height, new BaseCustomUIPanelPlugin() {
                    @Override public void render(float alphaMult) {
                        // Combat paints target/weapon labels after this native panel layer. Its
                        // single late pass is dispatched separately; campaign has an above-UI hook.
                        if (Global.getCurrentState() == GameState.TITLE) RuntimeHooks.render();
                    }
                });
                upper.addComponent(renderPanel).inBL(0, 0);
            }
            renderPanel.getPosition().setSize(width, height);
            if (settings != null && !settings.closed) root.bringComponentToTop(settings.panel);
            // Both operations target mod-owned children. The original children keep their own order.
            upper.bringComponentToTop(renderPanel);
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            Global.getLogger(OverlayHost.class).warn("SectorPad could not attach its additive overlay; vanilla UI is unchanged", failure);
            detach();
        }
    }

    public Handle mount(LunaBaseCustomPanelPlugin plugin) {
        ensureAttached();
        if (parent == null || failed) throw new IllegalStateException("The current game UI does not expose an overlay host");
        if (settings != null) settings.closeWithCallback();
        float width = Math.max(160f, Math.min(Global.getSettings().getScreenWidth() - 40f, 1000f));
        float height = Math.max(160f, Math.min(Global.getSettings().getScreenHeight() - 40f, 760f));
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, plugin);
        Handle handle = new Handle(parent, panel, plugin);
        settings = handle;
        try {
            parent.addComponent(panel).inMid();
            plugin.initFromScript(panel);
            parent.bringComponentToTop(panel);
            if (renderPanel != null) renderParent.bringComponentToTop(renderPanel);
            return handle;
        } catch (RuntimeException | LinkageError failure) {
            handle.close();
            throw failure;
        }
    }

    public void closeSettings() { if (settings != null) settings.closeWithCallback(); }

    /** Marks the early combat rendering callback without painting a duplicate overlay. */
    void beginCombatFrame(Object engine) {
        combatPass.begin(engine, settings != null && !settings.closed ? settings.panel : null);
    }

    /** Installed CombatState calls pre-core input after all native HUD labels and before its swap. */
    void renderCombatAboveHud(Object engine) {
        if (!combatPass.consume(engine)) return;
        RuntimeHooks.renderCombat(() -> {
            // Only our settings panel was deferred. Original combat widgets have already rendered.
            if (settings != null && !settings.closed) settings.panel.render(1f);
        });
    }

    /** Public panels and the existing software cursor expect a UI projection outside render hooks. */
    static void inUiCoordinates(Runnable draw) {
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, Global.getSettings().getScreenWidth(), 0, Global.getSettings().getScreenHeight(), -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            draw.run();
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glMatrixMode(matrix); GL11.glPopAttrib();
        }
    }

    private boolean containsRenderPanel() {
        Object children = StateAccess.read(renderParent, "getChildrenCopy");
        if (!(children instanceof java.util.List<?>)) children = StateAccess.read(renderParent, "getChildrenNonCopy");
        // Unknown UI implementations retain ordinary drawing support; known vanilla roots are checked.
        return !(children instanceof java.util.List<?> list) || list.contains(renderPanel);
    }

    public void detach() {
        combatPass.reset();
        if (settings != null) settings.closeWithCallback();
        if (renderParent != null && renderPanel != null) {
            try { renderParent.removeComponent(renderPanel); }
            catch (RuntimeException ignored) { /* Old state already disposed. No unrelated UI touched. */ }
        }
        renderPanel = null;
        parent = null;
        renderParent = null;
    }

    public final class Handle implements AutoCloseable {
        private final UIPanelAPI owner;
        private final CustomPanelAPI panel;
        private final LunaBaseCustomPanelPlugin plugin;
        private boolean closed;
        private boolean notified;
        private Handle(UIPanelAPI owner, CustomPanelAPI panel, LunaBaseCustomPanelPlugin plugin) {
            this.owner = owner; this.panel = panel; this.plugin = plugin;
        }
        public boolean isClosed() { return closed; }
        public CustomPanelAPI panel() { return panel; }
        private void closeWithCallback() {
            if (!notified) {
                notified = true;
                try { plugin.onClose(); }
                finally { close(); }
            } else close();
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try { owner.removeComponent(panel); }
            finally { if (settings == this) settings = null; }
        }
    }
}
