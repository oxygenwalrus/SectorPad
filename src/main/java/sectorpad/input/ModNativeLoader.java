package sectorpad.input;

import com.fs.starfarer.api.Global;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Loads shipped native libraries from this mod only; no extraction or game-library replacement. */
public final class ModNativeLoader {
    private static final Set<String> LOADED=new HashSet<>();
    private ModNativeLoader(){}
    public static String platform(){
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        String arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);
        if(!arch.equals("amd64")&&!arch.equals("x86_64"))throw new UnsupportedOperationException("SectorPad requires a 64-bit x86 game runtime");
        if(os.contains("win"))return "windows-x86_64";
        if(os.contains("linux"))return "linux-x86_64";
        throw new UnsupportedOperationException("SectorPad supports Windows and Linux x86-64");
    }
    public static void load(String fileName){
        String directory=Global.getSettings().getModManager().getModSpec("sectorpad").getPath().replace('\\','/');
        if(!directory.startsWith("/")&&!directory.matches("^[A-Za-z]:/.*"))directory=System.getProperty("user.dir").replace('\\','/')+"/"+directory;
        loadFromDirectory(directory+"/native",fileName);
    }
    /** Explicit native root for the read-only standalone probe and build verification. */
    public static synchronized void loadFromDirectory(String nativeRoot,String fileName){
        if(!fileName.matches("[A-Za-z0-9_-]+\\.(dll|so)"))throw new IllegalArgumentException("Invalid mod native filename");
        String path=nativeRoot.replace('\\','/')+"/"+platform()+"/"+fileName;
        if(!LOADED.contains(path)){System.load(path);LOADED.add(path);}
    }
    public static String sdlFile(){return platform().startsWith("windows")?"jamepad64.dll":"libjamepad64.so";}
}
