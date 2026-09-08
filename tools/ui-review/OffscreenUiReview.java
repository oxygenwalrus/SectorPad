package sectorpad.review;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import org.lazywizard.lazylib.ui.LazyFont;
import sectorpad.core.RadialModel;
import sectorpad.core.TextEntryModel;
import sectorpad.refit.RefitAdapter;
import sectorpad.refit.RefitWorkspace;
import sectorpad.ui.OverlayLayout;
import sectorpad.ui.OverlayRenderer;
import sectorpad.ui.TripadDrawing;
import sectorpad.ui.TripadTheme;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test-only resource adapter. All GL geometry and text drawing is production code. */
public final class OffscreenUiReview {
    private static int assertions;
    private static Path core, output;
    private static final Map<String, SpriteAPI> sprites = new HashMap<>();

    public static void main(String[] args) throws Exception {
        org.apache.log4j.Logger.getRootLogger().addAppender(new org.apache.log4j.varia.NullAppender());
        core=Path.of(args[0]).toAbsolutePath().normalize();output=Path.of(args[1]);Files.createDirectories(output);
        if((Pbuffer.getCapabilities()&Pbuffer.PBUFFER_SUPPORTED)==0)throw new IllegalStateException("Offscreen Pbuffer unavailable; no visible fallback is permitted");
        Pbuffer pbuffer=new Pbuffer(1920,1080,new PixelFormat(8,24,8),null,null);
        try {
            pbuffer.makeCurrent();
            Global.setSettings((SettingsAPI)Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),new Class<?>[]{SettingsAPI.class},(proxy,method,a)->switch(method.getName()){
                case "openStream" -> Files.newInputStream(resource((String)a[0]));
                case "loadTexture" -> {sprite((String)a[0]);yield null;}
                case "getSprite" -> sprite((String)a[0]);
                case "getScreenScaleMult" -> 1f;
                case "toString" -> "SectorPad offscreen read-only resource adapter";
                default -> throw new UnsupportedOperationException("Unexpected game API: "+method);
            }));
            // Warm fonts before state-restoration checks, matching the game's preloaded texture lifecycle.
            LazyFont.loadFont("graphics/fonts/insignia17LTaa.fnt");LazyFont.loadFont("graphics/fonts/insignia21LTaa.fnt");
            for(int[] size:new int[][]{{1280,720},{1280,800},{1920,1080}}){
                for(float scale:new float[]{1f,1.8f})review(size[0],size[1],scale);
            }
            capture("shared-controls",1280,720,1,OffscreenUiReview::controlSamples);
            String report="OffscreenUiReview: "+assertions+" assertions passed\nRenderer: "+GL11.glGetString(GL11.GL_RENDERER)
                +"\nActual production OverlayRenderer/RefitWorkspace and LazyFont, local installed insignia fonts.\n"
                +"Synthetic fixture data and neutral background; no native game UI, input, mod compatibility, or physical-device validation.\n"
                +"Resource adapter implements only read-only local streams and OpenGL sprite upload. Game installation untouched.\n";
            Files.writeString(output.resolve("EVIDENCE.txt"),report);System.out.print(report);
        } finally {Global.setSettings(null);pbuffer.destroy();}
    }
    private static Path resource(String name){Path p=core.resolve(name).normalize();if(!p.startsWith(core))throw new IllegalArgumentException("Resource escapes installation");return p;}
    private static SpriteAPI sprite(String name) throws Exception {
        SpriteAPI cached=sprites.get(name);if(cached!=null)return cached;
        BufferedImage image=ImageIO.read(resource(name).toFile());if(image==null)throw new IllegalArgumentException("Unreadable texture: "+name);
        int width=image.getWidth(),height=image.getHeight();ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
        for(int y=height-1;y>=0;y--)for(int x=0;x<width;x++){int rgba=image.getRGB(x,y);pixels.put((byte)(rgba>>16)).put((byte)(rgba>>8)).put((byte)rgba).put((byte)(rgba>>24));}pixels.flip();
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),texture=GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
        float[] state={width,height,0,1};
        SpriteAPI value=(SpriteAPI)Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),new Class<?>[]{SpriteAPI.class},(proxy,method,a)->switch(method.getName()){
            case "getTextureId" -> texture;case "getWidth" -> state[0];case "getHeight" -> state[1];case "getAngle" -> state[2];case "getAlphaMult" -> state[3];
            case "setSize" -> {state[0]=(float)a[0];state[1]=(float)a[1];yield null;}
            case "setAngle" -> {state[2]=(float)a[0];yield null;}case "setAlphaMult" -> {state[3]=(float)a[0];yield null;}
            case "renderAtCenter" -> {float cx=(float)a[0],cy=(float)a[1],w=state[0]/2,h=state[1]/2;GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(1,1,1,state[3]);GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0,0);GL11.glVertex2f(cx-w,cy-h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(cx+w,cy-h);GL11.glTexCoord2f(1,1);GL11.glVertex2f(cx+w,cy+h);GL11.glTexCoord2f(0,1);GL11.glVertex2f(cx-w,cy+h);GL11.glEnd();yield null;}
            case "toString" -> "Read-only sprite "+name;
            default -> throw new UnsupportedOperationException("Unexpected sprite API: "+method);
        });sprites.put(name,value);return value;
    }
    private static void review(int w,int h,float s)throws Exception{
        OverlayRenderer renderer=new OverlayRenderer();RadialModel wheel=new RadialModel();TextEntryModel keyboard=new TextEntryModel();
        List<RadialModel.Entry> entries=new ArrayList<>();String[][] labels={{"refit","Refit fleet"},{"cargo","Cargo & storage"},{"map","Sector map"},{"fleet","Fleet management"},{"intel","Intelligence"},{"abilities","Abilities"},{"save","Quick save"},{"settings","Controller settings"}};
        for(int i=0;i<labels.length;i++)entries.add(new RadialModel.Entry(labels[i][0],labels[i][1],"Review your fleet's weapons, hull modifications and flux distribution. Select a ship, inspect its equipment, then return to the campaign.",i!=6,"Unavailable during this encounter",false,()->{}));
        wheel.open("Fleet command",entries,false,"BACK",8);wheel.navigate(1);
        capture("radial",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"A Select  ·  B Back  ·  LB / RB Page",null,null,List.of(),null));
        OverlayLayout.Wheel geometry=OverlayLayout.wheel(w,h,s);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;check(renderer.pointWheel(wheel,geometry.x()+(float)Math.sin(a)*geometry.radius()*.72f,geometry.y()+(float)Math.cos(a)*geometry.radius()*.72f),"wheel point accepted");check(wheel.selected()==i,"visual segment and hit selection agree");}
        check(!renderer.pointWheel(wheel,-1,-1),"outside wheel rejected");wheel.close();
        keyboard.open("ISS Meridian",false,96);keyboard.title("Rename flagship");keyboard.move(3,2);
        capture("keyboard",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"A Type  ·  X Erase  ·  Y Shift  ·  Start Apply  ·  B Cancel",null,null,List.of(),null));
        OverlayLayout.Keyboard layout=OverlayLayout.keyboard(w,h,s,keyboard.rows(),false);
        for(var key:layout.keys()){var r=key.bounds();var hit=renderer.keyAt(r.x()+r.width()/2,r.y()+r.height()/2);check(hit!=null&&hit.row()==key.row()&&hit.column()==key.column(),"keyboard visual and hit agree");}
        check(renderer.keyAt(-1,-1)==null,"outside keyboard rejected");
        keyboard.docked(true);keyboard.title("Console command");
        capture("keyboard-docked",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"A Type  ·  X Erase  ·  Start Apply  ·  B Cancel",null,null,List.of(),null));keyboard.close();
        capture("dialog",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"A Confirm  ·  B Cancel","Controller ready","Your controller is connected. Review your bindings and return to the fleet when you are ready.",List.of(),null));
        renderer.setNavigation(new OverlayRenderer.Pointer(w*.42f,h*.48f,true,false,false),new OverlayRenderer.Focus(w*.3f,h*.44f,270,42,false));
        capture("hud",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,"Campaign navigation","X Pause  ·  Back Command wheel  ·  F10 Setup",null,null,List.of(),new OverlayRenderer.Aim(w*.68f,h*.55f,"ISS Resolute",true,true)));
        RefitWorkspace refit=new RefitWorkspace();refit.update(snapshot());refit.move(1);
        capture("refit",w,h,s,()->refit.render(w,h,s,"A Select  ·  B Back  ·  LB / RB Section  ·  Y Details"));
        var hitsField=RefitWorkspace.class.getDeclaredField("hits");hitsField.setAccessible(true);
        @SuppressWarnings("unchecked") List<RefitWorkspace.Hit> hits=(List<RefitWorkspace.Hit>)hitsField.get(refit);
        for(var hit:List.copyOf(hits)){var r=hit.bounds();check(r.valid()&&r.x()>=0&&r.y()>=0&&r.right()<=w&&r.top()<=h,"refit hit stays in viewport: "+hit.id());}
        check(!hits.isEmpty(),"refit produces input geometry");
        refit.section(2);capture("refit-stats",w,h,s,()->refit.render(w,h,s,"A Select  ·  B Back  ·  LB / RB Section  ·  Y Details"));
    }
    private static RefitAdapter.Snapshot snapshot(){
        List<RefitAdapter.Item> mounts=List.of(new RefitAdapter.Item("mount:1","Heavy Autocannon","Medium ballistic · Forward arc. Sustained kinetic fire pressures enemy shields at medium range; coordinate with your energy weapon groups.","mount:1",80,0,true),new RefitAdapter.Item("mount:2","Pulse Laser","Medium energy · Port mount","mount:2",5,50,true),new RefitAdapter.Item("mount:3","Pulse Laser","Medium energy · Starboard mount","mount:3",5,-50,true));
        List<RefitAdapter.Action> actions=new ArrayList<>();for(String id:List.of("mount:1","mount:2","mount:3","officer","name","hullmods","smods","vents+","vents-","caps+","caps-","groups","autofit","save","undo","strip","restore","simulation","variant-name","next-op","additional"))actions.add(new RefitAdapter.Action(id,switch(id){case "vents+"->"Add flux vent";case "vents-"->"Remove flux vent";case "caps+"->"Add capacitor";case "caps-"->"Remove capacitor";case "officer"->"Assign officer";case "name"->"Rename ship";default->id;},true,"",true));
        return new RefitAdapter.Snapshot(new Object(),new Object(),new Object(),null,"review","meridian","ISS Meridian","Eagle-class Cruiser","graphics/ships/eagle/eagle_base.png",27,155,30,15,List.of(),mounts,List.of(),List.of(),List.of("Hull integrity: 10,000","Armor rating: 1,000","Flux capacity: 15,000","Flux dissipation: 750 / sec","Shield efficiency: 0.8 flux / damage","Maximum speed: 50"),actions,"All standard controls available");
    }
    private static void controlSamples(){
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,1280,0,720,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            TripadDrawing.frame(130,140,1020,450,1);
            String[] names={"Normal","Hovered","Selected","Faded / disabled preview"};
            var font=LazyFont.loadFont("graphics/fonts/insignia21LTaa.fnt");
            for(int i=0;i<4;i++){
                float y=480-i*85;TripadDrawing.control(175,y,930,60,1,i==2,i==1,i==3?.35f:1);
                var text=font.createText();text.setFontSize(21);text.setBaseColor(i==3?TripadTheme.MUTED:TripadTheme.INK);text.setText(names[i]);text.draw(198,y+41);
            }
        }catch(Exception e){throw new IllegalStateException(e);}finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }
    private static void capture(String name,int w,int h,float s,Runnable render)throws Exception{
        GL11.glViewport(0,0,w,h);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glClearColor(.019f,.029f,.046f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(7,11,w-19,h-23);
        GL11.glDisable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ZERO);GL11.glLineWidth(3);GL11.glColor4f(.2f,.3f,.4f,.7f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glTranslatef(.13f,.17f,.19f);float[] projection=matrix(GL11.GL_PROJECTION_MATRIX);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslatef(3,5,7);float[] model=matrix(GL11.GL_MODELVIEW_MATRIX);GL11.glMatrixMode(GL11.GL_TEXTURE);
        int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);boolean textureEnabled=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        render.run();
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"no GL errors: "+name);check(GL11.glGetInteger(GL11.GL_MATRIX_MODE)==GL11.GL_TEXTURE,"matrix mode restored");
        check(Arrays.equals(projection,matrix(GL11.GL_PROJECTION_MATRIX))&&Arrays.equals(model,matrix(GL11.GL_MODELVIEW_MATRIX)),"matrices restored");
        check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glIsEnabled(GL11.GL_CULL_FACE)&&GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&!GL11.glIsEnabled(GL11.GL_BLEND),"GL enables restored");
        check(GL11.glGetInteger(GL11.GL_BLEND_SRC)==GL11.GL_ONE&&GL11.glGetInteger(GL11.GL_BLEND_DST)==GL11.GL_ZERO,"blend state restored");
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==texture&&GL11.glIsEnabled(GL11.GL_TEXTURE_2D)==textureEnabled,"texture state restored");
        check(Math.abs(GL11.glGetFloat(GL11.GL_LINE_WIDTH)-3)<.01,"line width restored");
        ByteBuffer bytes=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
        BufferedImage result=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y*w+x)*4;result.setRGB(x,h-y-1,((bytes.get(i)&255)<<16)|((bytes.get(i+1)&255)<<8)|(bytes.get(i+2)&255));}
        ImageIO.write(result,"png",output.resolve(name+"-"+w+"x"+h+"-s"+s+".png").toFile());
    }
    private static float[] matrix(int type){FloatBuffer buffer=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(type,buffer);float[] result=new float[16];buffer.get(result);return result;}
    private static void check(boolean pass,String message){assertions++;if(!pass)throw new AssertionError(message);}
}
