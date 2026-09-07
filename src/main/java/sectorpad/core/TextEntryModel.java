package sectorpad.core;

/** Text is staged locally. Cancel never edits or submits the target field. */
public final class TextEntryModel {
    private static final String[] LETTERS={"1234567890", "qwertyuiop", "asdfghjkl'", "zxcvbnm,.?", " -_@:/+!=;"};
    private static final String[] SHIFTED={"!@#$%^&*()", "QWERTYUIOP", "ASDFGHJKL\"", "ZXCVBNM<>\\", " []{}|~`:_"};
    private static final String[] NUMBERS={"123", "456", "789", "0.-"};
    private final StringBuilder text=new StringBuilder();
    private boolean open,numeric,shift;
    private int row,column,caret;
    private int maxLength=256;
    private String title="Text entry";
    private boolean docked;
    private char pendingHighSurrogate;
    public void open(String original,boolean numeric,int maxLength) {
        this.numeric=numeric;docked=false;title=numeric?"Number entry":"Text entry";this.maxLength=Math.max(1,maxLength);text.setLength(0);
        if(original!=null){int end=Math.min(original.length(),this.maxLength);if(end>0&&Character.isHighSurrogate(original.charAt(end-1)))end--;text.append(original,0,end);}
        pendingHighSurrogate=0;
        caret=text.length();row=column=0;shift=false;open=true;
    }
    public void move(int dx,int dy) { row=Math.floorMod(row+dy,rows().length+1);column=Math.floorMod(column+dx,columns()); }
    public void insert(char ch) {
        if(Character.isHighSurrogate(ch)){pendingHighSurrogate=ch;return;}
        if(Character.isLowSurrogate(ch)){
            if(pendingHighSurrogate!=0&&!numeric&&text.length()+2<=maxLength){text.insert(caret,new char[]{pendingHighSurrogate,ch});caret+=2;}
            pendingHighSurrogate=0;return;
        }
        pendingHighSurrogate=0;
        if(!Character.isISOControl(ch)&&(!numeric||Character.isDigit(ch)||ch=='.'||ch=='-')&&text.length()<maxLength)text.insert(caret++,ch);
    }
    public void title(String title){this.title=title;}
    public String title(){return title;}
    public void docked(boolean docked){this.docked=docked;}
    public boolean docked(){return docked;}
    public void backspace(){pendingHighSurrogate=0;if(caret>0){int previous=text.offsetByCodePoints(caret,-1);text.delete(previous,caret);caret=previous;}}
    public void delete(){pendingHighSurrogate=0;if(caret<text.length())text.delete(caret,text.offsetByCodePoints(caret,1));}
    public void caret(int delta){pendingHighSurrogate=0;int before=text.codePointCount(0,caret),after=text.codePointCount(caret,text.length());caret=text.offsetByCodePoints(caret,Math.max(-before,Math.min(after,delta)));}
    public void toggleShift(){shift=!shift;}
    /** Returns accept/cancel for footer controls, otherwise edits locally. */
    public String select() {
        if(row==rows().length) {
            return switch(column) {
                case 0 -> {insert(' ');yield "";}
                case 1 -> {backspace();yield "";}
                case 2 -> {toggleShift();yield "";}
                case 3 -> "accept";
                default -> "cancel";
            };
        }
        char ch=rows()[row].charAt(Math.min(column,rows()[row].length()-1));
        insert(shift?Character.toUpperCase(ch):ch);return "";
    }
    public String[] rows(){return (numeric?NUMBERS:shift?SHIFTED:LETTERS).clone();}
    /** Bounded viewport around the caret; the complete command stays in the staged model. */
    public String visibleText(int capacity){
        int half=Math.max(6,capacity/2),start=Math.max(0,caret-half),end=Math.min(text.length(),caret+half);
        if(start>0&&Character.isLowSurrogate(text.charAt(start)))start--;
        if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1)))end++;
        return (start>0?"…":"")+text.substring(start,caret)+"|"+text.substring(caret,end)+(end<text.length()?"…":"");
    }
    public int columns(){return row==rows().length?5:rows()[row].length();}
    public int row(){return row;}
    public int column(){return column;}
    public int caret(){return caret;}
    public String text(){return text.toString();}
    public boolean shifted(){return shift;}
    public boolean isOpen(){return open;}
    public void close(){open=false;}
}
