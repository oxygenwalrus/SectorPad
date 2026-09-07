package sectorpad.bridge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;

/** X11-compatible fallback. Wayland/compositor acceptance requires runtime testing. */
final class RobotInputOutput implements DesktopInputBridge.Output {
    private final Robot robot;
    RobotInputOutput() throws AWTException { robot = new Robot(); robot.setAutoDelay(0); robot.setAutoWaitForIdle(false); }
    public void observe(boolean active) { }
    public boolean isPhysicalKeyDown(int key) { return Keyboard.isCreated() && Keyboard.isKeyDown(key); }
    public boolean isPhysicalMouseDown(int button) { return Mouse.isCreated() && Mouse.isButtonDown(button); }
    public boolean preservesPhysicalHolds() { return false; }
    public void key(int key, boolean down) { if(down) robot.keyPress(KeyCodes.awt(key)); else robot.keyRelease(KeyCodes.awt(key)); }
    public void mouse(int button, boolean down) {
        int mask = switch(button) { case 0 -> InputEvent.BUTTON1_DOWN_MASK; case 1 -> InputEvent.BUTTON3_DOWN_MASK; case 2 -> InputEvent.BUTTON2_DOWN_MASK; default -> throw new IllegalArgumentException("Unsupported mouse button"); };
        if(down) robot.mousePress(mask); else robot.mouseRelease(mask);
    }
    public void wheel(int notches) { robot.mouseWheel(-notches); }
    public boolean supportsUnicode() { return false; }
    public void unicode(char character) { throw new UnsupportedOperationException("Use the focused game text field on this platform"); }
    public String description() { return "Desktop Robot input; text via focused game fields; concurrent physical/synthetic holds require platform verification"; }
    public void close() { }
}
