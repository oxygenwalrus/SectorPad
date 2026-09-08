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
import sectorpad.core.DeviceVisual;
import sectorpad.core.PadFrame;
import sectorpad.refit.RefitAdapter;
import sectorpad.refit.RefitWorkspace;
import sectorpad.ui.OverlayLayout;
import sectorpad.ui.OverlayRenderer;
import sectorpad.ui.TripadDrawing;
import sectorpad.ui.TripadTheme;
import sectorpad.ui.ControlGlyphs;
import sectorpad.ui.DeviceDiagram;
import sectorpad.ui.PromptRenderer;
import sectorpad.ui.UiArtwork;

import javax.imageio.ImageIO;
import java.awt.Color;
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
import java.util.Set;

/** Test-only resource adapter. All GL geometry and text drawing is production code. */
public final class OffscreenUiReview {
    private static int assertions;
    private static Path core, output, mod;
    private static final Map<String, SpriteAPI> sprites = new HashMap<>();
    private static final Map<String, Integer> renderedTextures = new HashMap<>();
    private static final Map<String, SpriteSnapshot> lastTextureDraw = new HashMap<>();
    private static final DeviceDiagram deviceDiagram=new DeviceDiagram();
    private static final PromptRenderer prompts=new PromptRenderer();

    public static void main(String[] args) throws Exception {
        org.apache.log4j.Logger.getRootLogger().addAppender(new org.apache.log4j.varia.NullAppender());
        core=Path.of(args[0]).toAbsolutePath().normalize();output=Path.of(args[1]).toAbsolutePath().normalize();Files.createDirectories(output);
        mod=output.getParent().getParent().resolve("mod").normalize();
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
            capture("devices-live",1280,800,1,()->orthographic(1280,800,()->deviceSamples(800,true,false)));
            capture("devices-bindings",1280,720,1,()->orthographic(1280,720,()->deviceSamples(720,false,false)));
            capture("devices-disconnected",1280,720,1,()->orthographic(1280,720,()->deviceSamples(720,false,true)));
            for(int height:new int[]{720,800})capture("device-glyphs-prompts",1280,height,1,()->orthographic(1280,height,()->glyphSamples(height)));
            artworkChecks();
            String report="OffscreenUiReview: "+assertions+" assertions passed\nRenderer: "+GL11.glGetString(GL11.GL_RENDERER)
                +"\nActual production OverlayRenderer/RefitWorkspace/DeviceDiagram/ControlGlyphs/PromptRenderer and LazyFont, local installed insignia fonts.\n"
                +"Synthetic fixture data and neutral background; no native game UI, input, mod compatibility, or physical-device validation.\n"
                +"Resource adapter reads installed fonts and one ship sprite, plus mod-local sourced controller artwork; honors tint, alpha, rotation, blend and checks sprite-state restoration. Game installation untouched.\n"
                +"Sourced texture render counts: "+new java.util.TreeMap<>(renderedTextures.entrySet().stream().filter(e->e.getKey().startsWith("graphics/sectorpad/controls/")).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)))+"\n";
            Files.writeString(output.resolve("EVIDENCE.txt"),report);System.out.print(report);
        } finally {Global.setSettings(null);pbuffer.destroy();}
    }
    private static Path resource(String name){
        Path root=name.startsWith("graphics/sectorpad/controls/")?mod:core;
        Path p=root.resolve(name).normalize();
        if(!p.startsWith(root))throw new IllegalArgumentException("Resource escapes allowed root");
        if(root.equals(mod)&&!p.startsWith(mod.resolve("graphics/sectorpad/controls")))
            throw new IllegalArgumentException("Artwork escapes mod-local controls directory");
        if(root.equals(core)&&!(name.startsWith("graphics/fonts/")||name.equals("graphics/ships/eagle/eagle_base.png")))
            throw new IllegalArgumentException("Unexpected installation resource: "+name);
        return p;
    }
    private static SpriteAPI sprite(String name) throws Exception {
        SpriteAPI cached=sprites.get(name);if(cached!=null)return cached;
        BufferedImage image=ImageIO.read(resource(name).toFile());if(image==null)throw new IllegalArgumentException("Unreadable texture: "+name);
        int width=image.getWidth(),height=image.getHeight();ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
        for(int y=height-1;y>=0;y--)for(int x=0;x<width;x++){int rgba=image.getRGB(x,y);pixels.put((byte)(rgba>>16)).put((byte)(rgba>>8)).put((byte)rgba).put((byte)(rgba>>24));}pixels.flip();
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),texture=GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous);
        float[] state={width,height,0,1};Color[] color={Color.WHITE};
        int[] blend={GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA};
        SpriteAPI value=(SpriteAPI)Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),new Class<?>[]{SpriteAPI.class},(proxy,method,a)->switch(method.getName()){
            case "getTextureId" -> texture;case "getWidth" -> state[0];case "getHeight" -> state[1];case "getAngle" -> state[2];case "getAlphaMult" -> state[3];
            case "getColor" -> color[0];case "setColor" -> {color[0]=(Color)a[0];yield null;}
            case "getBlendSrc" -> blend[0];case "getBlendDest" -> blend[1];
            case "setNormalBlend" -> {blend[0]=GL11.GL_SRC_ALPHA;blend[1]=GL11.GL_ONE_MINUS_SRC_ALPHA;yield null;}
            case "setBlendFunc" -> {blend[0]=(int)a[0];blend[1]=(int)a[1];yield null;}
            case "setSize" -> {state[0]=(float)a[0];state[1]=(float)a[1];yield null;}
            case "setAngle" -> {state[2]=(float)a[0];yield null;}case "setAlphaMult" -> {state[3]=(float)a[0];yield null;}
            case "renderAtCenter" -> {
                float cx=(float)a[0],cy=(float)a[1],w=state[0]/2,h=state[1]/2;
                double angle=Math.toRadians(state[2]);float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
                GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(blend[0],blend[1]);
                GL11.glColor4f(color[0].getRed()/255f,color[0].getGreen()/255f,color[0].getBlue()/255f,state[3]*color[0].getAlpha()/255f);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0,0);GL11.glVertex2f(cx-w*cos+h*sin,cy-w*sin-h*cos);
                GL11.glTexCoord2f(1,0);GL11.glVertex2f(cx+w*cos+h*sin,cy+w*sin-h*cos);
                GL11.glTexCoord2f(1,1);GL11.glVertex2f(cx+w*cos-h*sin,cy+w*sin+h*cos);
                GL11.glTexCoord2f(0,1);GL11.glVertex2f(cx-w*cos-h*sin,cy-w*sin+h*cos);GL11.glEnd();
                renderedTextures.merge(name,1,Integer::sum);
                lastTextureDraw.put(name,new SpriteSnapshot(state[0],state[1],state[2],state[3],color[0],blend[0],blend[1]));yield null;
            }
            case "toString" -> "Read-only sprite "+name;
            default -> throw new UnsupportedOperationException("Unexpected sprite API: "+method);
        });sprites.put(name,value);return value;
    }
    private static void review(int w,int h,float s)throws Exception{
        OverlayRenderer renderer=new OverlayRenderer();RadialModel wheel=new RadialModel();TextEntryModel keyboard=new TextEntryModel();
        DeviceVisual visual=h==720?DeviceVisual.XBOX:h==800?DeviceVisual.STEAM_DECK:DeviceVisual.ROG_ALLY;
        renderer.setDeviceVisual(visual);
        List<RadialModel.Entry> entries=new ArrayList<>();String[][] labels={{"refit","Refit fleet"},{"cargo","Cargo & storage"},{"map","Sector map"},{"fleet","Fleet management"},{"intel","Intelligence"},{"abilities","Abilities"},{"save","Quick save"},{"settings","Controller settings"}};
        for(int i=0;i<labels.length;i++)entries.add(new RadialModel.Entry(labels[i][0],labels[i][1],"Review your fleet's weapons, hull modifications and flux distribution. Select a ship, inspect its equipment, then return to the campaign.",i!=6,"Unavailable during this encounter",false,()->{}));
        wheel.open("Fleet command",entries,false,"BACK",8);wheel.navigate(1);
        capture("radial",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"{pad:A} Select  ·  {pad:B} Back  ·  {pad:LB} / {pad:RB} Page",null,null,List.of(),null));
        OverlayLayout.Wheel geometry=OverlayLayout.wheel(w,h,s);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;check(renderer.pointWheel(wheel,geometry.x()+(float)Math.sin(a)*geometry.radius()*.72f,geometry.y()+(float)Math.cos(a)*geometry.radius()*.72f),"wheel point accepted");check(wheel.selected()==i,"visual segment and hit selection agree");}
        check(!renderer.pointWheel(wheel,-1,-1),"outside wheel rejected");wheel.close();
        keyboard.open("ISS Meridian",false,96);keyboard.title("Rename flagship");keyboard.move(3,2);
        String keyboardFooter="{pad:A} Type   {pad:X} Erase   {pad:Y} Shift   {pad:LB}/{pad:RB} Caret   {pad:MENU} Apply   {pad:B} Cancel";
        capture("keyboard",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,keyboardFooter,null,null,List.of(),null));
        OverlayLayout.Keyboard layout=OverlayLayout.keyboard(w,h,s,keyboard.rows(),false);
        for(var key:layout.keys()){var r=key.bounds();var hit=renderer.keyAt(r.x()+r.width()/2,r.y()+r.height()/2);check(hit!=null&&hit.row()==key.row()&&hit.column()==key.column(),"keyboard visual and hit agree");}
        check(renderer.keyAt(-1,-1)==null,"outside keyboard rejected");
        keyboard.docked(true);keyboard.title("Console command");
        capture("keyboard-docked",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,keyboardFooter,null,null,List.of(),null));keyboard.close();
        capture("dialog",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,null,"{pad:A} Confirm  ·  {pad:B} Cancel","Controller ready","Your controller is connected. Review your bindings and return to the fleet when you are ready.",List.of(),null));
        renderer.setNavigation(new OverlayRenderer.Pointer(w*.42f,h*.48f,true,false,false),new OverlayRenderer.Focus(w*.3f,h*.44f,270,42,false));
        renderer.setBannerPrompts(true);
        capture("hud",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,"Campaign travel · Left stick moves fleet · {pad:X} Pause","{pad:VIEW} Command hub   {pad:VIEW} + {pad:MENU} (hold) Recovery",null,null,List.of(),new OverlayRenderer.Aim(w*.68f,h*.55f,"ISS Resolute",true,true)));
        renderer.setBannerPrompts(false);
        capture("hud-literal-status",w,h,s,()->renderer.render(w,h,s,wheel,keyboard,"Saved profile: Fleet {pad:A} / X Wing","{pad:VIEW} Command hub   {pad:VIEW} + {pad:MENU} (hold) Recovery",null,null,List.of(),null));
        RefitWorkspace refit=new RefitWorkspace();refit.setDeviceVisual(visual);refit.update(snapshot());refit.move(1);
        capture("refit",w,h,s,()->refit.render(w,h,s,"{pad:A} Select  ·  {pad:B} Back  ·  {pad:LB} / {pad:RB} Section  ·  {pad:R3} Details"));
        var hitsField=RefitWorkspace.class.getDeclaredField("hits");hitsField.setAccessible(true);
        @SuppressWarnings("unchecked") List<RefitWorkspace.Hit> hits=(List<RefitWorkspace.Hit>)hitsField.get(refit);
        for(var hit:List.copyOf(hits)){var r=hit.bounds();check(r.valid()&&r.x()>=0&&r.y()>=0&&r.right()<=w&&r.top()<=h,"refit hit stays in viewport: "+hit.id());}
        check(!hits.isEmpty(),"refit produces input geometry");
        refit.section(2);capture("refit-stats",w,h,s,()->refit.render(w,h,s,"{pad:A} Select  ·  {pad:B} Back  ·  {pad:LB} / {pad:RB} Section  ·  {pad:R3} Details"));
    }
    private static RefitAdapter.Snapshot snapshot(){
        List<RefitAdapter.Item> mounts=List.of(new RefitAdapter.Item("mount:1","Heavy Autocannon","Medium ballistic · Forward arc. Sustained kinetic fire pressures enemy shields at medium range; coordinate with your energy weapon groups.","mount:1",80,0,true),new RefitAdapter.Item("mount:2","Pulse Laser","Medium energy · Port mount","mount:2",5,50,true),new RefitAdapter.Item("mount:3","Pulse Laser","Medium energy · Starboard mount","mount:3",5,-50,true));
        List<RefitAdapter.Action> actions=new ArrayList<>();for(String id:List.of("mount:1","mount:2","mount:3","officer","name","hullmods","smods","vents+","vents-","caps+","caps-","groups","autofit","save","undo","strip","restore","simulation","variant-name","next-op","additional"))actions.add(new RefitAdapter.Action(id,switch(id){case "vents+"->"Add flux vent";case "vents-"->"Remove flux vent";case "caps+"->"Add capacitor";case "caps-"->"Remove capacitor";case "officer"->"Assign officer";case "name"->"Rename ship";default->id;},true,"",true));
        return new RefitAdapter.Snapshot(new Object(),new Object(),new Object(),null,"review","meridian","ISS Meridian","Eagle-class Cruiser","graphics/ships/eagle/eagle_base.png",27,155,30,15,List.of(),mounts,List.of(),List.of(),List.of("Hull integrity: 10,000","Armor rating: 1,000","Flux capacity: 15,000","Flux dissipation: 750 / sec","Shield efficiency: 0.8 flux / damage","Maximum speed: 50"),actions,"All standard controls available");
    }
    private static final DeviceVisual[] DEVICES={DeviceVisual.XBOX,DeviceVisual.STEAM_DECK,DeviceVisual.ROG_ALLY,DeviceVisual.GENERIC};
    private static final String[] DEVICE_NAMES={"Xbox controller","Steam Deck","ROG Ally","Unknown controller"};
    private static void deviceSamples(int height,boolean live,boolean disconnected){
        label("SECTORPAD / DEVICE SCHEMATICS",44,height-35,25,TripadTheme.INK);
        label(disconnected?"Disconnected: controls neutral after a live sample":live?"Live input: both triggers, asymmetric stick motion, held buttons":"Current bindings: mapped controls with one focused action",44,height-66,17,TripadTheme.MUTED);
        float cardHeight=height==800?310:270;
        for(int i=0;i<DEVICES.length;i++){
            float x=40+(i%2)*620,y=height-108-(i/2)*(cardHeight+24)-cardHeight;
            TripadDrawing.frame(x,y,600,cardHeight,1);
            label(DEVICE_NAMES[i],x+20,y+cardHeight-20,21,TripadTheme.CYAN);
            float diagramHeight=height==800?242:200;
            PadFrame frame=disconnected?PadFrame.disconnected():samplePad(DEVICE_NAMES[i],live);
            Set<String> mapped=disconnected?Set.of():Set.of("A","B","LB","RT","LEFT_STICK","RIGHT_STICK","DPAD_UP","MENU");
            DeviceVisual visual=DEVICES[i];String selected=disconnected?null:i%2==0?"RT":"RIGHT_STICK";
            componentState("device "+visual,()->deviceDiagram.render(x+40,y+25,520,diagramHeight,visual,frame,mapped,selected,1));
        }
        label("Production sourced artwork + reactive overlays · synthetic snapshots · no physical-device claim",44,28,15,TripadTheme.MUTED);
    }
    private static PadFrame samplePad(String name,boolean live){
        return new PadFrame(true,"offscreen-fixture",name,true,live?.72f:0,live?-.54f:0,live?-.65f:0,live?.8f:0,
            live?.84f:0,live?.61f:0,live?Set.of("A","Y","LB","L3","DPAD_RIGHT"):Set.of());
    }
    private static void glyphSamples(int height){
        label("SECTORPAD / DEVICE GLYPHS & REMAPPED PROMPTS",44,height-35,25,TripadTheme.INK);
        label("Native insignia font · controller-specific shoulders and utility icons · deliberate wrapping",44,height-67,17,TripadTheme.MUTED);
        String[] controls={"A","B","X","Y","LB","RB","LT","RT","VIEW","MENU","L3","R3","DPAD_UP","DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT","DPAD","LEFT_STICK","RIGHT_STICK"};
        for(int i=0;i<DEVICES.length;i++){
            float top=height-104-i*((height-133)/4f),rowHeight=(height-133)/4f-12;
            TripadDrawing.frame(40,top-rowHeight,1200,rowHeight,1);
            label(DEVICE_NAMES[i],58,top-15,19,TripadTheme.CYAN);
            for(int j=0;j<controls.length;j++){
                float width=ControlGlyphs.width(controls[j],DEVICES[i],24);
                check(Float.isFinite(width)&&width>0,"Glyph has finite advance: "+DEVICES[i]+" "+controls[j]);
                String control=controls[j];DeviceVisual visual=DEVICES[i];float cx=302+j*48.5f;boolean active=j%3==0;
                componentState("glyph "+control,()->ControlGlyphs.draw(control,visual,cx,top-28,24,active,1));
            }
            prompts.draw("{pad:RB} + {pad:DPAD_RIGHT} Next equipment bank  ·  {pad:LT} Hold precision aim  ·  {pad:MENU} Apply current configuration",
                60,top-59,18,TripadTheme.INK,590,rowHeight-65,false,DEVICES[i]);
            prompts.draw("{pad:VIEW} + {pad:R3} Open controller settings  ·  {pad:LB} / {pad:RT} Switch inspection section  ·  {pad:B} Return to fleet",
                688,top-59,18,TripadTheme.INK,524,rowHeight-65,false,DEVICES[i]);
        }
        componentState("short-height prompt",()->prompts.draw("{pad:LB} + {pad:RIGHT_STICK} Remapped aim · A, X, L1 and {pad:UNKNOWN} stay literal",
            44,24,20,TripadTheme.MUTED,1180,16,false,DeviceVisual.GENERIC));
        componentState("narrow prompt",()->prompts.draw("{pad:RIGHT_STICK}",1218,height-66,24,TripadTheme.CYAN,14,16,false,DeviceVisual.GENERIC));
    }
    private static void label(String value,float x,float top,float size,java.awt.Color ink){
        try{var text=LazyFont.loadFont("graphics/fonts/insignia21LTaa.fnt").createText();text.setFontSize(size);text.setBaseColor(ink);text.setText(value);text.draw(x,top);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void orthographic(int width,int height,Runnable render){
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,width,0,height,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);render.run();
        }finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }
    private static void componentState(String name,Runnable render){
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        float[] projection=matrix(GL11.GL_PROJECTION_MATRIX),model=matrix(GL11.GL_MODELVIEW_MATRIX);
        boolean textured=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),blended=GL11.glIsEnabled(GL11.GL_BLEND);
        float lineWidth=GL11.glGetFloat(GL11.GL_LINE_WIDTH);float[] color=currentColor();
        int blendSrc=GL11.glGetInteger(GL11.GL_BLEND_SRC),blendDst=GL11.glGetInteger(GL11.GL_BLEND_DST);
        Map<String,SpriteSnapshot> before=new HashMap<>();sprites.forEach((path,sprite)->before.put(path,SpriteSnapshot.of(sprite)));
        render.run();
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"Direct component has no GL error: "+name);
        check(GL11.glGetInteger(GL11.GL_MATRIX_MODE)==mode&&Arrays.equals(projection,matrix(GL11.GL_PROJECTION_MATRIX))
            &&Arrays.equals(model,matrix(GL11.GL_MODELVIEW_MATRIX)),"Direct component restores matrices: "+name);
        check(texture==GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)&&textured==GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
            &&blended==GL11.glIsEnabled(GL11.GL_BLEND)&&Math.abs(lineWidth-GL11.glGetFloat(GL11.GL_LINE_WIDTH))<.001f,
            "Direct component restores drawing attributes: "+name);
        check(Arrays.equals(color,currentColor())&&blendSrc==GL11.glGetInteger(GL11.GL_BLEND_SRC)
            &&blendDst==GL11.glGetInteger(GL11.GL_BLEND_DST),"Direct component restores color and blend factors: "+name);
        before.forEach((path,state)->check(state.equals(SpriteSnapshot.of(sprites.get(path))),"Direct component restores cached sprite state: "+name+" "+path));
    }
    private record SpriteSnapshot(float width,float height,float angle,float alpha,Color color,int src,int dst){
        private static SpriteSnapshot of(SpriteAPI sprite){return new SpriteSnapshot(sprite.getWidth(),sprite.getHeight(),sprite.getAngle(),sprite.getAlphaMult(),sprite.getColor(),sprite.getBlendSrc(),sprite.getBlendDest());}
    }
    private static float[] currentColor(){FloatBuffer buffer=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,buffer);float[] color=new float[4];buffer.get(color);return color;}
    private static void artworkChecks()throws Exception{
        for(String device:List.of("xbox","steam-deck","rog-ally")){
            String path="graphics/sectorpad/controls/devices/"+device+".png";
            check(sprites.containsKey(path)&&renderedTextures.getOrDefault(path,0)>0,"Sourced device texture loaded and rendered, never silent fallback: "+device);
            check(resource(path).startsWith(mod),"Device artwork is read only from the mod: "+device);
        }
        for(DeviceVisual visual:DeviceVisual.values())for(String control:List.of("A","B","X","Y","LB","RB","LT","RT","VIEW","MENU","L3","R3","DPAD_UP","DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT","DPAD","LEFT_STICK","RIGHT_STICK")){
            String glyph=ControlGlyphs.artworkPath(control,visual);
            check(glyph!=null&&sprites.containsKey(glyph)&&renderedTextures.getOrDefault(glyph,0)>0,"Sourced glyph texture actually loaded and rendered: "+visual+" "+control);
        }
        String path="graphics/sectorpad/controls/devices/steam-deck.png";
        SpriteAPI sprite=sprite(path);SpriteSnapshot original=SpriteSnapshot.of(sprite);
        try{
            sprite.setSize(123,89);sprite.setAngle(37);sprite.setAlphaMult(.42f);sprite.setColor(new Color(41,73,109,157));sprite.setBlendFunc(GL11.GL_ONE,GL11.GL_ONE);
            SpriteSnapshot previous=SpriteSnapshot.of(sprite);Color tint=new Color(181,211,227,129);
            orthographic(1280,720,()->componentState("sourced artwork unusual shared sprite state",()->{
                check(UiArtwork.draw(path,640,360,390,155,tint),"Valid sourced artwork draw succeeds");
                check(new SpriteSnapshot(390,155,0,1,tint,GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA).equals(lastTextureDraw.get(path)),"Sourced artwork applies requested size, tint and normal blend at draw time");
            }));
            check(previous.equals(SpriteSnapshot.of(sprite)),"Sourced artwork restores unusual dimensions, angle, alpha, tint and blend");
        }finally{sprite.setSize(original.width(),original.height());sprite.setAngle(original.angle());sprite.setAlphaMult(original.alpha());sprite.setColor(original.color());sprite.setBlendFunc(original.src(),original.dst());}
        componentState("missing artwork fallback",()->check(!UiArtwork.draw("graphics/sectorpad/controls/missing.png",1,1,24,24,Color.WHITE),"Missing artwork returns safe fallback"));
        componentState("cached missing artwork fallback",()->check(!UiArtwork.draw("graphics/sectorpad/controls/missing.png",1,1,24,24,Color.WHITE),"Repeated missing artwork remains safe"));
        for(String invalid:List.of("graphics/sectorpad/controls/../outside.png","graphics/fonts/insignia21LTaa.png","graphics/sectorpad/controls/devices/steam-deck.svg"))
            check(!UiArtwork.draw(invalid,1,1,24,24,Color.WHITE),"Artwork loader rejects invalid path: "+invalid);
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
