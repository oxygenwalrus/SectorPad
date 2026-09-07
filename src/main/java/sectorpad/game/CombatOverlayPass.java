package sectorpad.game;

import com.fs.starfarer.api.ui.UIComponentAPI;

/** Keeps the combat-only late pass paired with its render frame and restores only our own panel. */
final class CombatOverlayPass {
    private Object engine;
    private boolean pending;
    private UIComponentAPI deferredPanel;
    private float opacity;

    void begin(Object currentEngine, UIComponentAPI ownPanel) {
        restorePanel();
        engine = currentEngine;
        pending = currentEngine != null;
        if (pending && ownPanel != null) {
            deferredPanel = ownPanel;
            opacity = ownPanel.getOpacity();
            // The installed native UI skips rendering at zero opacity. Input is restored below
            // before its normal dispatch; no original game component is made transparent.
            ownPanel.setOpacity(0f);
        }
    }

    boolean consume(Object currentEngine) {
        boolean draw = pending && engine == currentEngine && currentEngine != null;
        reset();
        return draw;
    }

    void reset() {
        pending = false;
        engine = null;
        restorePanel();
    }

    private void restorePanel() {
        UIComponentAPI panel = deferredPanel;
        deferredPanel = null;
        if (panel != null) panel.setOpacity(opacity);
    }
}
