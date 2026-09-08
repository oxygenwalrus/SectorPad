package sectorpad.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import sectorpad.diagnostics.Diagnostics;

/** Mod-local controller textures. Failed artwork falls back to the caller's vector drawing. */
public final class UiArtwork {
    private static final String PREFIX="graphics/sectorpad/controls/";
    private static final int LIMIT=128;
    private static final Map<String,SpriteAPI> sprites=new HashMap<>();
    private static final Set<String> failed=new HashSet<>();
    private static SettingsAPI owner;
    private UiArtwork() { }

    public static boolean draw(String path,float cx,float cy,float width,float height,Color tint){
        if(path==null||!path.startsWith(PREFIX)||path.contains("..")||!path.endsWith(".png")
                ||tint==null||!Float.isFinite(cx)||!Float.isFinite(cy)||!Float.isFinite(width)
                ||!Float.isFinite(height)||width<=0||height<=0)return false;
        SettingsAPI settings=Global.getSettings();if(settings==null)return false;
        if(owner!=settings){sprites.clear();failed.clear();owner=settings;}
        if(failed.contains(path))return false;
        if(!sprites.containsKey(path)&&sprites.size()+failed.size()>=LIMIT)return false;
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{
            SpriteAPI sprite=sprites.get(path);
            if(sprite==null){settings.loadTexture(path);sprite=settings.getSprite(path);if(sprite==null)throw new IllegalStateException("Controller texture unavailable");sprites.put(path,sprite);}
            float w=sprite.getWidth(),h=sprite.getHeight(),angle=sprite.getAngle(),opacity=sprite.getAlphaMult();
            Color color=sprite.getColor();int src=sprite.getBlendSrc(),dst=sprite.getBlendDest();
            try{
                GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);sprite.setNormalBlend();sprite.setAngle(0);
                sprite.setSize(width,height);sprite.setColor(tint);sprite.setAlphaMult(1);
                sprite.renderAtCenter(cx,cy);return true;
            }finally{
                sprite.setSize(w,h);sprite.setAngle(angle);sprite.setColor(color);sprite.setAlphaMult(opacity);sprite.setBlendFunc(src,dst);
            }
        }catch(Exception|LinkageError failure){
            sprites.remove(path);failed.add(path);Diagnostics.error("ui.controller_art_unavailable",failure);return false;
        }finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(mode);GL11.glPopAttrib();
        }
    }
}
