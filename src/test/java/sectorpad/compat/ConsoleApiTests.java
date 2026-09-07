package sectorpad.compat;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Optional dependency linkage regression; never initializes Console or executes commands. */
public final class ConsoleApiTests {
    public static void main(String[] args) throws Exception {
        Path jars=Path.of(args[0],"mods","Console Commands-4.0.9","jars");
        if(!Files.isDirectory(jars)){System.out.println("ConsoleApiTests: SKIPPED; optional Console Commands 4.0.9 not installed");return;}
        List<URL> urls=new ArrayList<>();
        try(var paths=Files.walk(jars)){for(Path jar:paths.filter(p->p.toString().endsWith(".jar")).toList())urls.add(jar.toUri().toURL());}
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ConsoleApiTests.class.getClassLoader())){
            var lookup=MethodHandles.publicLookup();
            Class<?> panel=Class.forName("org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel",false,loader);
            Class<?> context=Class.forName("org.lazywizard.console.BaseCommand$CommandContext",false,loader);
            Class<?> element=Class.forName("org.lazywizard.console.overlay.v2.elements.BaseConsoleElement",false,loader);
            lookup.findConstructor(panel,MethodType.methodType(void.class,context));
            lookup.findStatic(panel,"getInstance",MethodType.methodType(panel));
            lookup.findVirtual(panel,"getParent",MethodType.methodType(CustomPanelAPI.class));
            lookup.findVirtual(panel,"getLogElement",MethodType.methodType(TooltipMakerAPI.class));
            lookup.findVirtual(panel,"getInput",MethodType.methodType(String.class));
            lookup.findVirtual(panel,"setInput",MethodType.methodType(void.class,String.class));
            lookup.findVirtual(panel,"setCursorIndex",MethodType.methodType(void.class,int.class));
            lookup.findVirtual(panel,"setRequiresRecreation",MethodType.methodType(void.class,boolean.class));
            lookup.findVirtual(panel,"getLastMatchesDisplayed",MethodType.methodType(ArrayList.class));
            lookup.findVirtual(panel,"addSuggestionWidget",MethodType.methodType(void.class,List.class,TooltipMakerAPI.class,float.class,float.class));
            lookup.findVirtual(panel,"recreatePanel",MethodType.methodType(void.class));
            lookup.findVirtual(panel,"close",MethodType.methodType(void.class));
            lookup.findVirtual(element,"getParentElement",MethodType.methodType(TooltipMakerAPI.class));
        }
        System.out.println("ConsoleApiTests: 13 public optional-dependency signatures passed (no console execution)");
    }
}
