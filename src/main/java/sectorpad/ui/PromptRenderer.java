package sectorpad.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import sectorpad.core.DeviceVisual;

/** Explicit, bounded inline legends. Ordinary text and device names are never interpreted as buttons. */
public final class PromptRenderer {
    private static final Set<String> CONTROLS=Set.of("A","B","X","Y","LB","RB","LT","RT","L3","R3",
        "MENU","VIEW","LEFT_STICK","RIGHT_STICK","DPAD","DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT");
    private LazyFont.DrawableString label;
    private boolean fontAttempted;
    public record Part(String text,String control,boolean newline,boolean whitespace) { }
    private record Run(Part part,float width,float size) { }
    private record Line(List<Run> runs,float width) { }

    public static boolean recognized(String control){return control!=null&&CONTROLS.contains(control);}
    public static String token(String control){return recognized(control)?"{pad:"+control+"}":control==null||control.isBlank()||control.equals("NONE")?"Unbound":control;}

    /** Unknown or malformed tags stay literal. Only exact canonical control identifiers produce glyphs. */
    public static List<Part> parse(String text){
        List<Part> out=new ArrayList<>();if(text==null||text.isEmpty())return out;
        int at=0;
        while(at<text.length()){
            char c=text.charAt(at);
            if(c=='\n'||c=='\r'){
                if(c=='\r'&&at+1<text.length()&&text.charAt(at+1)=='\n')at++;
                out.add(new Part("",null,true,false));at++;continue;
            }
            if(text.startsWith("{pad:",at)){
                int end=text.indexOf('}',at+5);
                if(end>=0&&recognized(text.substring(at+5,end))){
                    out.add(new Part("",text.substring(at+5,end),false,false));at=end+1;continue;
                }
            }
            boolean space=Character.isWhitespace(c);int end=at+1;
            while(end<text.length()&&text.charAt(end)!='\n'&&text.charAt(end)!='\r'
                    &&Character.isWhitespace(text.charAt(end))==space){
                if(text.startsWith("{pad:",end)){
                    int close=text.indexOf('}',end+5);
                    if(close>=0&&recognized(text.substring(end+5,close)))break;
                }
                end++;
            }
            out.add(new Part(text.substring(at,end),null,false,space));at=end;
        }
        return List.copyOf(out);
    }

    /** x is the left edge, or the center of the available area when centered is true; top uses game UI coordinates. */
    public void draw(String text,float x,float top,float size,Color ink,float maxWidth,float maxHeight,boolean centered,DeviceVisual visual){
        if(text==null||text.isEmpty()||ink==null||!Float.isFinite(x)||!Float.isFinite(top)||!Float.isFinite(size)
                ||!Float.isFinite(maxWidth)||!Float.isFinite(maxHeight)||size<=0||maxWidth<=0||maxHeight<=0)return;
        float rowHeight=size*1.25f;
        // Keep a full line and its glyphs inside the supplied bounds, including very small host panels.
        if(rowHeight>maxHeight){size*=maxHeight/rowHeight;rowHeight=maxHeight;}
        int matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();
        try {
            if(!fontAttempted){fontAttempted=true;try{label=LazyFont.loadFont("graphics/fonts/insignia17LTaa.fnt").createText();}catch(Exception ignored){}}
            if(label==null)return;
            List<Line> lines=layout(parse(text),size,maxWidth,visual);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            float offset=0;
            for(Line line:lines){
                if(offset+rowHeight>maxHeight+.01f)break;
                float left=centered?x-line.width()/2:x;
                for(Run run:line.runs()){
                    if(run.part().control()!=null){
                        ControlGlyphs.draw(run.part().control(),visual,left+run.width()/2,top-offset-rowHeight/2,
                            run.size(),false,ink.getAlpha()/255f);
                    }else if(!run.part().whitespace()){
                        prepare(run.part().text(),run.size(),ink);
                        label.draw(left,top-offset-(rowHeight-label.getHeight())/2);
                    }
                    left+=run.width();
                }
                offset+=rowHeight;
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(matrix);GL11.glPopAttrib();
        }
    }
    private List<Line> layout(List<Part> parts,float size,float maxWidth,DeviceVisual visual){
        List<Line> lines=new ArrayList<>();List<Run> line=new ArrayList<>();float used=0,pendingSpace=0;
        for(Part part:parts){
            if(part.newline()){lines.add(new Line(List.copyOf(line),used));line.clear();used=pendingSpace=0;continue;}
            if(part.whitespace()){if(!line.isEmpty())pendingSpace+=spaceWidth(size)*part.text().length();continue;}
            float width=part.control()!=null?ControlGlyphs.width(part.control(),visual,size)+size*.16f:measure(part.text(),size);
            float runSize=size;
            // An individual token is kept whole and scaled only when it cannot fit on an empty line.
            if(width>maxWidth){runSize*=maxWidth/width;width=maxWidth;}
            if(!line.isEmpty()&&used+pendingSpace+width>maxWidth){
                lines.add(new Line(List.copyOf(line),used));line.clear();used=pendingSpace=0;
            }
            if(pendingSpace>0&&!line.isEmpty()){
                line.add(new Run(new Part(" ",null,false,true),pendingSpace,size));used+=pendingSpace;
            }
            pendingSpace=0;line.add(new Run(part,width,runSize));used+=width;
        }
        if(!line.isEmpty())lines.add(new Line(List.copyOf(line),used));
        return lines;
    }
    private void prepare(String value,float size,Color ink){
        label.setFontSize(size);label.setMaxWidth(Float.MAX_VALUE);label.setMaxHeight(Float.MAX_VALUE);label.setBaseColor(ink);label.setText(value);
    }
    private float measure(String value,float size){prepare(value,size,Color.WHITE);return label.getWidth();}
    private float spaceWidth(float size){return Math.max(size*.15f,measure("M M",size)-measure("MM",size));}
}
