package sectorpad.bridge;

import org.lwjgl.input.Keyboard;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/** Explicit game scan-code mappings; no Windows/Guide keys or clipboard shortcuts. */
final class KeyCodes {
    private static final int[] AWT = new int[256];
    private static final int[] WINDOWS = new int[256];
    static {
        Arrays.fill(AWT, -1); Arrays.fill(WINDOWS, -1);
        int[] letters = {Keyboard.KEY_A,Keyboard.KEY_B,Keyboard.KEY_C,Keyboard.KEY_D,Keyboard.KEY_E,Keyboard.KEY_F,Keyboard.KEY_G,Keyboard.KEY_H,Keyboard.KEY_I,Keyboard.KEY_J,Keyboard.KEY_K,Keyboard.KEY_L,Keyboard.KEY_M,Keyboard.KEY_N,Keyboard.KEY_O,Keyboard.KEY_P,Keyboard.KEY_Q,Keyboard.KEY_R,Keyboard.KEY_S,Keyboard.KEY_T,Keyboard.KEY_U,Keyboard.KEY_V,Keyboard.KEY_W,Keyboard.KEY_X,Keyboard.KEY_Y,Keyboard.KEY_Z};
        for (int i = 0; i < letters.length; i++) map(letters[i], KeyEvent.VK_A + i, 0x41 + i);
        for (int i = 0; i < 9; i++) map(Keyboard.KEY_1 + i, KeyEvent.VK_1 + i, 0x31 + i);
        map(Keyboard.KEY_0, KeyEvent.VK_0, 0x30);
        for (int i = 0; i < 10; i++) map(Keyboard.KEY_F1 + i, KeyEvent.VK_F1 + i, 0x70 + i);
        map(Keyboard.KEY_F11, KeyEvent.VK_F11, 0x7A); map(Keyboard.KEY_F12, KeyEvent.VK_F12, 0x7B);
        map(Keyboard.KEY_ESCAPE,KeyEvent.VK_ESCAPE,0x1B); map(Keyboard.KEY_RETURN,KeyEvent.VK_ENTER,0x0D);
        map(Keyboard.KEY_SPACE,KeyEvent.VK_SPACE,0x20); map(Keyboard.KEY_TAB,KeyEvent.VK_TAB,0x09);
        map(Keyboard.KEY_BACK,KeyEvent.VK_BACK_SPACE,0x08); map(Keyboard.KEY_DELETE,KeyEvent.VK_DELETE,0x2E);
        map(Keyboard.KEY_INSERT,KeyEvent.VK_INSERT,0x2D); map(Keyboard.KEY_HOME,KeyEvent.VK_HOME,0x24);
        map(Keyboard.KEY_END,KeyEvent.VK_END,0x23); map(Keyboard.KEY_PRIOR,KeyEvent.VK_PAGE_UP,0x21);
        map(Keyboard.KEY_NEXT,KeyEvent.VK_PAGE_DOWN,0x22); map(Keyboard.KEY_UP,KeyEvent.VK_UP,0x26);
        map(Keyboard.KEY_DOWN,KeyEvent.VK_DOWN,0x28); map(Keyboard.KEY_LEFT,KeyEvent.VK_LEFT,0x25);
        map(Keyboard.KEY_RIGHT,KeyEvent.VK_RIGHT,0x27); map(Keyboard.KEY_PAUSE,KeyEvent.VK_PAUSE,0x13);
        map(Keyboard.KEY_LSHIFT,KeyEvent.VK_SHIFT,0xA0); map(Keyboard.KEY_RSHIFT,KeyEvent.VK_SHIFT,0xA1);
        map(Keyboard.KEY_LCONTROL,KeyEvent.VK_CONTROL,0xA2); map(Keyboard.KEY_RCONTROL,KeyEvent.VK_CONTROL,0xA3);
        map(Keyboard.KEY_LMENU,KeyEvent.VK_ALT,0xA4); map(Keyboard.KEY_RMENU,KeyEvent.VK_ALT_GRAPH,0xA5);
        map(Keyboard.KEY_MINUS,KeyEvent.VK_MINUS,0xBD); map(Keyboard.KEY_EQUALS,KeyEvent.VK_EQUALS,0xBB);
        map(Keyboard.KEY_LBRACKET,KeyEvent.VK_OPEN_BRACKET,0xDB); map(Keyboard.KEY_RBRACKET,KeyEvent.VK_CLOSE_BRACKET,0xDD);
        map(Keyboard.KEY_SEMICOLON,KeyEvent.VK_SEMICOLON,0xBA); map(Keyboard.KEY_APOSTROPHE,KeyEvent.VK_QUOTE,0xDE);
        map(Keyboard.KEY_GRAVE,KeyEvent.VK_BACK_QUOTE,0xC0); map(Keyboard.KEY_BACKSLASH,KeyEvent.VK_BACK_SLASH,0xDC);
        map(Keyboard.KEY_COMMA,KeyEvent.VK_COMMA,0xBC); map(Keyboard.KEY_PERIOD,KeyEvent.VK_PERIOD,0xBE);
        map(Keyboard.KEY_SLASH,KeyEvent.VK_SLASH,0xBF);
        int[] numpad = {Keyboard.KEY_NUMPAD0,Keyboard.KEY_NUMPAD1,Keyboard.KEY_NUMPAD2,Keyboard.KEY_NUMPAD3,Keyboard.KEY_NUMPAD4,Keyboard.KEY_NUMPAD5,Keyboard.KEY_NUMPAD6,Keyboard.KEY_NUMPAD7,Keyboard.KEY_NUMPAD8,Keyboard.KEY_NUMPAD9};
        for (int i=0;i<numpad.length;i++) map(numpad[i],KeyEvent.VK_NUMPAD0+i,0x60+i);
        map(Keyboard.KEY_ADD,KeyEvent.VK_ADD,0x6B); map(Keyboard.KEY_SUBTRACT,KeyEvent.VK_SUBTRACT,0x6D);
        map(Keyboard.KEY_MULTIPLY,KeyEvent.VK_MULTIPLY,0x6A); map(Keyboard.KEY_DIVIDE,KeyEvent.VK_DIVIDE,0x6F);
        map(Keyboard.KEY_DECIMAL,KeyEvent.VK_DECIMAL,0x6E); map(Keyboard.KEY_NUMPADENTER,KeyEvent.VK_ENTER,0x0D);
    }
    private static void map(int key, int awt, int windows) { AWT[key]=awt; WINDOWS[key]=windows; }
    static boolean supported(int key) { return key>0 && key<256 && AWT[key]>=0; }
    static int awt(int key) { if(!supported(key)) throw new IllegalArgumentException("Unsupported game key "+key); return AWT[key]; }
    static int windows(int key) { if(!supported(key)) return -1; return WINDOWS[key]; }
    private KeyCodes() { }
}
