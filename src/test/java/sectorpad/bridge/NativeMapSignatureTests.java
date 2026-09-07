package sectorpad.bridge;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Checks installed classfile signatures without initializing game classes or creating a map. */
public final class NativeMapSignatureTests {
    public static void main(String[] args) throws Exception {
        // The game's obfuscated member names require its noverify VM flag. Reading raw
        // classfile metadata avoids loading/initializing it or weakening this test JVM.
        ClassInfo sector=info(NativeMapAdapter.SECTOR_MAP);
        require(sector,"getScroller","()Lcom/fs/starfarer/coreui/A/N;");
        require(sector,"getMap","()Lcom/fs/starfarer/coreui/A/H;");
        ClassInfo scroll=info("com.fs.starfarer.coreui.A.N");
        require(scroll,"getXOffset","()F"); require(scroll,"getYOffset","()F");
        require(scroll,"setOffset","(FF)V"); require(scroll,"clampAndRecompute","()V");
        require(info("com.fs.starfarer.coreui.A.H"),"getZoomTracker","()Lcom/fs/starfarer/util/A;");
        ClassInfo warroom=info(NativeMapAdapter.WARROOM);
        require(warroom,"computeWorldLocation","(FF)Lorg/lwjgl/util/vector/Vector2f;");
        require(warroom,"centerOn","(FF)V");
        require(warroom,"getMapDisplay","()Lcom/fs/starfarer/combat/new/b;");
        ClassInfo display=info("com.fs.starfarer.combat.new.b");
        long trackerCount=display.fields().stream().filter(field->field.descriptor().equals("Lcom/fs/starfarer/util/A;")).count();
        check(trackerCount==1,"Warroom tracker identity must be unambiguous");
        ClassInfo events=info("com.fs.starfarer.util.A.new");
        ClassInfo event=info("com.fs.starfarer.util.A.C");
        require(events,"<init>","()V");
        check(events.superName().equals("java/util/ArrayList"),"Native zoom input list");
        check(event.interfaces().contains("com/fs/starfarer/api/input/InputEventAPI"),"Native event contract");
        require(event,"<init>","(Lcom/fs/starfarer/api/input/InputEventClass;Lcom/fs/starfarer/api/input/InputEventType;IIIC)V");
        require(info("com.fs.starfarer.util.A"),"o00000","(Lcom/fs/starfarer/util/A/new;)V");
        System.out.println("NativeMapSignatureTests: installed map/warroom/zoom signatures passed (no game execution)");
    }
    private record Member(String name,String descriptor) { }
    private record ClassInfo(String superName,List<String> interfaces,List<Member> fields,List<Member> methods) { }
    private static void require(ClassInfo info,String name,String descriptor) {
        check(info.methods().contains(new Member(name,descriptor)),"Missing native method "+name+descriptor);
    }
    private static ClassInfo info(String className) throws Exception {
        InputStream resource=NativeMapSignatureTests.class.getClassLoader().getResourceAsStream(className.replace('.','/')+".class");
        check(resource!=null,"Missing installed game class "+className);
        try(DataInputStream input=new DataInputStream(resource)) {
            check(input.readInt()==0xCAFEBABE,"Classfile header"); input.readUnsignedShort(); input.readUnsignedShort();
            Object[] pool=new Object[input.readUnsignedShort()];
            for(int i=1;i<pool.length;i++) {
                switch(input.readUnsignedByte()) {
                    case 1 -> pool[i]=input.readUTF();
                    case 3,4 -> input.skipNBytes(4);
                    case 5,6 -> {input.skipNBytes(8);i++;}
                    case 7 -> pool[i]=input.readUnsignedShort();
                    case 8,16,19,20 -> input.skipNBytes(2);
                    case 9,10,11,12,17,18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new AssertionError("Unknown classfile pool tag");
                }
            }
            input.readUnsignedShort(); input.readUnsignedShort();
            String parent=(String)pool[(Integer)pool[input.readUnsignedShort()]];
            List<String> interfaces=new ArrayList<>();
            for(int count=input.readUnsignedShort();count>0;count--) interfaces.add((String)pool[(Integer)pool[input.readUnsignedShort()]]);
            List<Member> fields=members(input,pool), methods=members(input,pool);
            return new ClassInfo(parent,interfaces,fields,methods);
        }
    }
    private static List<Member> members(DataInputStream input,Object[] pool) throws Exception {
        List<Member> result=new ArrayList<>();
        for(int count=input.readUnsignedShort();count>0;count--) {
            input.readUnsignedShort();
            result.add(new Member((String)pool[input.readUnsignedShort()],(String)pool[input.readUnsignedShort()]));
            for(int attributes=input.readUnsignedShort();attributes>0;attributes--) {
                input.readUnsignedShort(); input.skipNBytes(Integer.toUnsignedLong(input.readInt()));
            }
        }
        return result;
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
