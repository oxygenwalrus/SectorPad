package sectorpad.bridge;

import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Calls verified public game methods. No reflection classes or access overrides. */
final class PublicUiAccess {
    private static final ClassValue<Map<String,Optional<MethodHandle>>> GETTERS=new ClassValue<>() {
        @Override protected Map<String,Optional<MethodHandle>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final Map<String,Optional<Class<?>>> TYPES=new ConcurrentHashMap<>();
    private PublicUiAccess() { }
    static Object read(Object target,String name) {
        if(target==null) return null;
        MethodHandle handle=GETTERS.get(target.getClass()).computeIfAbsent(name,key->Optional.ofNullable(getter(target.getClass(),key))).orElse(null);
        if(handle==null) return null;
        try { return handle.invoke(target); } catch(Throwable unavailable) { return null; }
    }
    static boolean has(Object target,String name) {
        if(target==null) return false;
        return GETTERS.get(target.getClass()).computeIfAbsent(name,key->Optional.ofNullable(getter(target.getClass(),key))).isPresent();
    }
    static Class<?> type(String name) {
        return TYPES.computeIfAbsent(name,key->{
            try { return Optional.of(Class.forName(key,false,PublicUiAccess.class.getClassLoader())); }
            catch(ClassNotFoundException | LinkageError | SecurityException unavailable) { return Optional.empty(); }
        }).orElse(null);
    }
    static Object invoke(Object target,String name,Class<?> result,Class<?>[] signature,Object... arguments) throws Throwable {
        if(target==null || result==null) throw new IllegalArgumentException("Unavailable public UI method "+name);
        MethodHandle handle=find(target.getClass(),name,MethodType.methodType(result,signature));
        if(handle==null) throw new IllegalArgumentException("Unavailable public UI method "+name);
        List<Object> values=new ArrayList<>(arguments.length+1); values.add(target);
        java.util.Collections.addAll(values,arguments);
        return handle.invokeWithArguments(values);
    }
    static Object construct(Class<?> type,Class<?>[] signature,Object... arguments) throws Throwable {
        if(type==null) throw new IllegalArgumentException("Unavailable public UI type");
        return MethodHandles.publicLookup().findConstructor(type,MethodType.methodType(void.class,signature)).invokeWithArguments(arguments);
    }
    private static MethodHandle getter(Class<?> owner,String name) {
        List<Class<?>> results=new ArrayList<>();
        if(name.startsWith("is") || name.startsWith("has")) results.add(boolean.class);
        else if(name.equals("getBrightness") || name.equals("getBackgroundDimAmount") || name.endsWith("Offset")) results.add(float.class);
        else if(name.equals("getText")) results.add(String.class);
        else if(name.equals("getChildrenCopy") || name.equals("getViews")) {results.add(List.class);results.add(ArrayList.class);}
        else if(name.equals("getContentContainer")) {
            add(results,"com.fs.starfarer.ui.g$Oo"); add(results,"com.fs.starfarer.coreui.A.N$Oo");
            add(results,"com.fs.starfarer.ui.interfacenew"); results.add(UIPanelAPI.class); results.add(UIComponentAPI.class);
        } else if(name.equals("getScreenPanel") || name.equals("getOverlayPanelForCodex") || name.equals("getDialogParent")) {
            add(results,"com.fs.starfarer.ui.interfacenew"); results.add(UIPanelAPI.class); results.add(UIComponentAPI.class);
        } else if(name.equals("getFader")) add(results,"com.fs.graphics.util.Fader");
        else if(name.equals("getRendererPanel")) {add(results,"com.fs.starfarer.ui.m");results.add(UIComponentAPI.class);}
        for(Class<?> result:results) {
            MethodHandle handle=find(owner,name,MethodType.methodType(result));
            if(handle!=null) return handle;
        }
        return null;
    }
    private static void add(List<Class<?>> types,String name) {Class<?> value=type(name);if(value!=null) types.add(value);}
    private static MethodHandle find(Class<?> owner,String name,MethodType signature) {
        for(Class<?> current=owner;current!=null;current=current.getSuperclass()) {
            try { return MethodHandles.publicLookup().findVirtual(current,name,signature); }
            catch(NoSuchMethodException | IllegalAccessException | SecurityException ignored) { }
            for(Class<?> contract:current.getInterfaces()) {
                try { return MethodHandles.publicLookup().findVirtual(contract,name,signature); }
                catch(NoSuchMethodException | IllegalAccessException | SecurityException ignored) { }
            }
        }
        return null;
    }
}
