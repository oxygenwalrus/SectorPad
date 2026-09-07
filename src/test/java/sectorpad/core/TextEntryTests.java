package sectorpad.core;

import java.util.HashSet;
import java.util.Set;

public final class TextEntryTests {
    public static void main(String[] args){
        TextEntryModel editor=new TextEntryModel();editor.open("",false,4096);
        Set<Character> characters=new HashSet<>();
        for(int shift=0;shift<2;shift++){for(String row:editor.rows())for(char ch:row.toCharArray())characters.add(ch);editor.toggleShift();}
        for(char ch=32;ch<127;ch++)check(characters.contains(ch),"Printable command character missing: "+ch);
        editor.insert('\ud83d');editor.insert('\ude80');editor.insert('x');editor.caret(-1);editor.backspace();
        check(editor.text().equals("x")&&editor.caret()==0,"Backspace removes an entire supplementary character");
        editor.open("\ud83d\ude80x",false,4096);editor.caret(-2);editor.delete();check(editor.text().equals("x"),"Delete preserves UTF-16 boundaries");
        editor.open("a\ud83d\ude80",false,2);check(editor.text().equals("a"),"Length limit cannot retain a partial surrogate");
        editor.open("x".repeat(4000),false,4096);editor.caret(-1000);String visible=editor.visibleText(60);
        check(visible.contains("|")&&visible.length()<=65&&editor.text().length()==4000,"Long commands keep the caret visible without truncating stored text");
        editor.open("",true,10);editor.insert('a');editor.insert('\n');editor.insert('2');check(editor.text().equals("2"),"Numeric filtering remains active");
        System.out.println("TextEntryTests: printable ASCII coverage and 5 Unicode/viewport/numeric scenarios passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
