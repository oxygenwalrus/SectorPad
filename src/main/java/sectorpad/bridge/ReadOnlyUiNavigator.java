package sectorpad.bridge;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the existing UI tree. Focus is expressed with normal pointer movement.
 * Never installs listeners on, removes, resizes, relabels, or replaces game widgets.
 * Deliberate user scroll/text operations use the corresponding existing API.
 */
public final class ReadOnlyUiNavigator {
    public record Candidate(UIComponentAPI component, String label, float x, float y,
                            float width, float height, ScrollPanelAPI scroller) {
        public float centerX() { return x + width / 2f; }
        public float centerY() { return y + height / 2f; }
        public boolean contains(float px,float py) { return px>=x && px<=x+width && py>=y && py<=y+height; }
    }

    /** A field intentionally selected before SectorPad's text-entry modal took input. */
    public record TextTarget(Object root,Object modal,TextFieldAPI field,String originalText) { }

    private static final int MAX_NODES=5000, MAX_DEPTH=48;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<ScrollPanelAPI> scrollers = new ArrayList<>();
    private final List<NativeMapAdapter.Target> maps = new ArrayList<>();
    private Object currentRoot, focusRoot;
    private UIComponentAPI selected;
    private float selectionPointerX=Float.NaN,selectionPointerY=Float.NaN;
    private String status="No active UI tree";
    private int visitedCount;
    private final java.util.function.BiFunction<Object,Object,String> additionalTarget;
    public ReadOnlyUiNavigator(){this((scope,node)->null);}
    /** A version adapter may recognize specific native rows within the already-isolated modal. */
    public ReadOnlyUiNavigator(java.util.function.BiFunction<Object,Object,String> additionalTarget){this.additionalTarget=java.util.Objects.requireNonNull(additionalTarget);}

    /** Returns the live title, combat, or campaign root without assigning any game field. */
    public Object discoverRoot() {
        try {
            Object state=com.fs.state.AppDriver.getInstance().getCurrentState();
            if(Boolean.TRUE.equals(read(state,"isShowingCodex"))) {
                Object codex=read(state,"getOverlayPanelForCodex");
                if(codex instanceof UIComponentAPI) return codex;
            }
            return read(state,"getScreenPanel");
        } catch(RuntimeException | LinkageError ex) {
            status="Current screen discovery unavailable: "+ex.getClass().getSimpleName();
            return null;
        }
    }

    public List<Candidate> refresh() { return refresh(discoverRoot()); }

    /** Identity only: last live native dialog in paint order, excluding our own panels. */
    public static Object modalIdentity(Object root) {
        return new ReadOnlyUiNavigator().findTopModal(root,identitySet(),0);
    }

    public List<Candidate> refresh(Object root) {
        candidates.clear(); scrollers.clear(); maps.clear(); visitedCount=0;
        if(root!=currentRoot) { selected=null; currentRoot=root; }
        focusRoot=root;
        if(root==null) { status="No active UI tree; pointer remains available"; return List.of(); }
        Object modal=findTopModal(root,identitySet(),0);
        if(modal!=null) focusRoot=modal;
        visitedCount=0;
        visit(focusRoot,null,identitySet(),0);
        if(candidates.stream().noneMatch(c->c.component()==selected)) selected=null;
        status=candidates.size()+" navigation targets"+(modal!=null ? "; modal bounds active" : "")+(visitedCount>=MAX_NODES ? "; tree limit reached" : "");
        return List.copyOf(candidates);
    }

    public List<Candidate> getCandidates() { return List.copyOf(candidates); }
    public Candidate selected() { return candidates.stream().filter(c->c.component()==selected).findFirst().orElse(null); }
    public Object getFocusRoot() { return focusRoot; }
    public String getStatus() { return status; }
    public void clearSelection() { selected=null; selectionPointerX=selectionPointerY=Float.NaN; }

    /** Selects a candidate geometrically; dx/dy use the game's bottom-left axes. */
    public Candidate move(int dx,int dy,float pointerX,float pointerY) {
        if(dx==0 && dy==0) return selected();
        Candidate origin=selected();
        // Keep logical focus through layout/scroll updates. Only actual pointer motion
        // outside the selected control transfers ownership back to the mouse.
        boolean pointerMoved=Float.isNaN(selectionPointerX)||Math.hypot(pointerX-selectionPointerX,pointerY-selectionPointerY)>3;
        if(origin==null || (pointerMoved && !origin.contains(pointerX,pointerY))) {
            origin=candidates.stream().filter(c->c.contains(pointerX,pointerY)).min(java.util.Comparator.comparingDouble(c->c.width()*c.height())).orElse(null);
        }
        float fromX=origin==null ? pointerX : origin.centerX(), fromY=origin==null ? pointerY : origin.centerY();
        Candidate best=null; double bestScore=Double.POSITIVE_INFINITY; boolean bestAligned=false;
        for(Candidate candidate:candidates) {
            if(candidate==origin || candidate.component()==(origin==null?null:origin.component())) continue;
            double score=directionScore(fromX,fromY,candidate.centerX(),candidate.centerY(),dx,dy);
            if(!Double.isFinite(score))continue;
            boolean aligned=origin!=null && (dx==0
                ? Math.min(origin.x()+origin.width(),candidate.x()+candidate.width())>Math.max(origin.x(),candidate.x())
                : Math.min(origin.y()+origin.height(),candidate.y()+candidate.height())>Math.max(origin.y(),candidate.y()));
            if(aligned)score=dx==0?Math.abs(candidate.centerY()-fromY):Math.abs(candidate.centerX()-fromX);
            if(best==null || (aligned&&!bestAligned) || (aligned==bestAligned && (score<bestScore || (score==bestScore && geometricOrder(candidate,best)<0)))) {
                best=candidate; bestScore=score; bestAligned=aligned;
            }
        }
        // If the pointer has no target and points past the entire UI, start at the nearest visible target.
        if(best==null && origin==null) best=candidates.stream().min(java.util.Comparator.comparingDouble(c->Math.hypot(c.centerX()-fromX,c.centerY()-fromY))).orElse(null);
        // Reaching a known menu edge is consumed; do not also send a native arrow key.
        if(best==null)best=origin;
        if(best!=null) selected=best.component();
        selectionPointerX=pointerX;selectionPointerY=pointerY;
        return best;
    }

    private static int geometricOrder(Candidate a,Candidate b) {
        int row=Float.compare(b.y(),a.y());
        return row!=0?row:Float.compare(a.x(),b.x());
    }

    public boolean navigate(int dx,int dy,DesktopInputBridge bridge) {
        if(!bridge.isActive()) return false;
        Candidate target=move(dx,dy,bridge.getPointerX(),bridge.getPointerY());
        if(target==null) return false;
        PositionAPI pos=target.component().getPosition();
        if(pos==null || !Float.isFinite(pos.getCenterX()) || !Float.isFinite(pos.getCenterY())) return false;
        float x=pos.getCenterX(),y=pos.getCenterY();
        float revealDelta=target.scroller()!=null ? reveal(target) : 0f;
        bridge.movePointer(x,y+revealDelta);
        selectionPointerX=bridge.getPointerX();selectionPointerY=bridge.getPointerY();
        return true;
    }

    public TextFieldAPI focusedTextField() {
        TextFieldAPI found=null;
        for(Candidate candidate:candidates) {
            if(candidate.component() instanceof TextFieldAPI field && field.hasFocus()) {
                if(found!=null && found!=field) return null; // Ambiguous focus fails closed.
                found=field;
            }
        }
        return found;
    }

    /** Validate first, then apply only the user's staged text to the still-present focused field. */
    public boolean commitText(TextFieldAPI field,String staged) {
        if(field==null || staged==null || !field.hasFocus() || candidates.stream().noneMatch(c->c.component()==field)) return false;
        return writeValidText(field,staged);
    }

    public TextTarget captureText() {
        TextFieldAPI field=focusedTextField();
        return field==null?null:new TextTarget(currentRoot,focusRoot,field,field.getText());
    }

    /**
     * Our keyboard's mouse interaction can release native keyboard focus. Preserve the
     * originally selected field, but reject a changed screen, modal, field, or source text.
     * The caller must refresh the live tree immediately before committing.
     */
    public boolean commitCapturedText(TextTarget target,String staged) {
        if(target==null || staged==null || target.root()!=currentRoot || target.modal()!=focusRoot
                || candidates.stream().noneMatch(c->c.component()==target.field())
                || !java.util.Objects.equals(target.originalText(),target.field().getText())) return false;
        return writeValidText(target.field(),staged);
    }

    private boolean writeValidText(TextFieldAPI field,String staged) {
        if(field.getMaxChars()>=0 && staged.length()>field.getMaxChars()) return false;
        for(int i=0;i<staged.length();i++) if(!field.isValidChar(staged.charAt(i))) return false;
        LabelAPI label=field.getTextLabelAPI();
        if(field.isLimitByStringWidth() && label!=null && field.getPosition()!=null && label.computeTextWidth(staged)>field.getPosition().getWidth()) return false;
        field.setText(staged);
        return staged.equals(field.getText());
    }

    public boolean commitText(String staged) { return commitText(focusedTextField(),staged); }

    /** Stable while the same live scroller is hovered; resets fractional scroll on pane changes. */
    public String scrollOwnerId(float pointerX,float pointerY) {
        ScrollPanelAPI owner=scrollerAt(pointerX,pointerY);
        return (owner==null?"root":"scroll")+":"+Integer.toHexString(System.identityHashCode(owner==null?focusRoot:owner));
    }

    private ScrollPanelAPI scrollerAt(float pointerX,float pointerY) {
        return scrollers.stream().filter(s->contains(s.getPosition(),pointerX,pointerY)).min(java.util.Comparator.comparingDouble(s->area(s.getPosition()))).orElse(null);
    }

    public boolean hasScrollableAt(float pointerX,float pointerY) {
        return scrollerAt(pointerX,pointerY)!=null;
    }

    /** Move the hovered map's content in logical UI pixels without moving the pointer. */
    public boolean panMap(float dx,float dy,float pointerX,float pointerY) {
        NativeMapAdapter.Target target=mapAt(pointerX,pointerY);
        return target!=null && NativeMapAdapter.pan(target,dx,dy);
    }

    /** Positive notches zoom in, matching a physical wheel-up event. */
    public boolean zoomMap(int notches,float pointerX,float pointerY) {
        NativeMapAdapter.Target target=mapAt(pointerX,pointerY);
        return target!=null && NativeMapAdapter.zoom(target,notches,pointerX,pointerY);
    }

    private NativeMapAdapter.Target mapAt(float x,float y) {
        if(hasScrollableAt(x,y)) return null;
        if(candidates.stream().anyMatch(c->(c.component() instanceof ButtonAPI || c.component() instanceof TextFieldAPI) && c.contains(x,y))) return null;
        NativeMapAdapter.Target found=null;
        for(NativeMapAdapter.Target map:maps) if(visible(map.owner()) && contains(map.bounds().getPosition(),x,y)) found=map;
        return found;
    }

    /**
     * dx/dy are logical UI pixels added to content offsets, not wheel notches.
     * Positive dy moves farther down the content. Changes only follow user scrolling.
     */
    public boolean scrollAt(float dx,float dy,float pointerX,float pointerY) {
        ScrollPanelAPI active=scrollerAt(pointerX,pointerY);
        if(active==null) return false;
        Object content=read(active,"getContentContainer");
        if(!(content instanceof UIComponentAPI component)) return false;
        PositionAPI box=active.getPosition(), contents=component.getPosition();
        if(box==null || contents==null) return false;
        if(Float.isFinite(dx) && dx!=0) active.setXOffset(Math.max(0,Math.min(active.getXOffset()+dx,Math.max(0,contents.getWidth()-box.getWidth()))));
        if(Float.isFinite(dy) && dy!=0) active.setYOffset(Math.max(0,Math.min(active.getYOffset()+dy,Math.max(0,contents.getHeight()-box.getHeight()))));
        return true;
    }

    public static double directionScore(float x,float y,float tx,float ty,int dx,int dy) {
        double length=Math.hypot(dx,dy);
        if(length==0) return Double.POSITIVE_INFINITY;
        double along=((tx-x)*dx+(ty-y)*dy)/length;
        if(along<=1) return Double.POSITIVE_INFINITY;
        double across=Math.abs((tx-x)*dy-(ty-y)*dx)/length;
        return along + across*2.75 + across*across/Math.max(16,along);
    }

    private Object findTopModal(Object node,Set<Object> visited,int depth) {
        if(node==null || depth>MAX_DEPTH || !visited.add(node) || ++visitedCount>MAX_NODES || !visible(node)) return null;
        Object found=isModal(node) ? node : null;
        for(Object child:children(node)) {
            Object inner=findTopModal(child,visited,depth+1);
            if(inner!=null) found=inner;
        }
        return found;
    }

    private void visit(Object node,ScrollPanelAPI nearestScroller,Set<Object> visited,int depth) {
        if(node==null || depth>MAX_DEPTH || !visited.add(node) || ++visitedCount>MAX_NODES || !visible(node)
                || Boolean.FALSE.equals(read(node,"isActive")) || Boolean.TRUE.equals(read(read(node,"getFader"),"isFadingOut"))) return;
        NativeMapAdapter.Target map=NativeMapAdapter.identify(node);
        if(map!=null) maps.add(map);
        if(node instanceof ScrollPanelAPI scroll) { nearestScroller=scroll; if(!scrollers.contains(scroll)) scrollers.add(scroll); }
        for(Object child:children(node)) visit(child,nearestScroller,visited,depth+1);
        if(node instanceof ButtonAPI button) {
            if(button.isEnabled() && !Boolean.FALSE.equals(read(node,"isClickable"))) add(button,button.getText(),nearestScroller);
        } else if(node instanceof TextFieldAPI field) {
            if(!Boolean.FALSE.equals(read(node,"isEnabled")) && !Boolean.FALSE.equals(read(node,"isEditable"))
                    && !Boolean.TRUE.equals(read(node,"isReadOnly"))) add(field,"Text field",nearestScroller);
        } else if(node instanceof com.fs.starfarer.campaign.ui.trade.CargoStackView cargo && !cargo.isExpired()
                && cargo.getStack()!=null && !cargo.getStack().isEmpty() && cargo.isEnabled()) {
            PositionAPI position=cargo.getPosition();
            if(position!=null && position.getWidth()>=16 && position.getHeight()>=12) add(cargo,label(node),nearestScroller);
        } else if(node instanceof UIComponentAPI component){
            String label=additionalTarget.apply(focusRoot,node);
            if(label!=null)add(component,label,nearestScroller);
        }
        // Scroll containers also contain descriptions, headings and decorative rows. Membership
        // in a scroll list does not prove an action exists; unknown custom rows retain pointer access.
    }

    private void add(UIComponentAPI component,String label,ScrollPanelAPI scroller) {
        PositionAPI pos=component.getPosition();
        if(pos==null || pos.getWidth()<=0 || pos.getHeight()<=0 || !Float.isFinite(pos.getX()+pos.getY()+pos.getWidth()+pos.getHeight())) return;
        if(candidates.stream().anyMatch(c->c.component()==component)) return;
        candidates.add(new Candidate(component,label==null || label.isBlank()?"Control":label,pos.getX(),pos.getY(),pos.getWidth(),pos.getHeight(),scroller));
    }

    public float reveal(Candidate target) {
        if(target==null||target.scroller()==null)return 0f;
        ScrollPanelAPI scroll=target.scroller();
        PositionAPI bounds=scroll.getPosition(), pos=target.component().getPosition();
        Object content=read(scroll,"getContentContainer");
        if(!(content instanceof UIComponentAPI component) || bounds==null || pos==null) return 0f;
        float before=scroll.getYOffset(), offset=before;
        if(pos.getY()<bounds.getY()) offset+=bounds.getY()-pos.getY();
        else if(pos.getY()+pos.getHeight()>bounds.getY()+bounds.getHeight()) offset-=pos.getY()+pos.getHeight()-bounds.getY()-bounds.getHeight();
        float after=Math.max(0,Math.min(offset,Math.max(0,component.getPosition().getHeight()-bounds.getHeight())));
        scroll.setYOffset(after);
        return after-before;
    }

    private static List<?> children(Object node) {
        LinkedHashSet<Object> found=new LinkedHashSet<>();
        Object children=read(node,"getChildrenCopy");
        if(children instanceof List<?> list) found.addAll(list);
        Object content=read(node,"getContentContainer");
        if(content instanceof UIComponentAPI) found.add(content);
        Object views=read(node,"getViews");
        if(views instanceof List<?> list) found.addAll(list);
        Object renderer=read(node,"getRendererPanel");
        if(renderer instanceof UIComponentAPI) found.add(renderer);
        found.remove(node);
        return new ArrayList<>(found);
    }

    private static String label(Object node) {
        Object text=read(node,"getText");
        return text instanceof String value && !value.isBlank() ? value : "Item";
    }
    private static boolean visible(Object node) {
        if(node instanceof CustomPanelAPI panel && panel.getPlugin()!=null && panel.getPlugin().getClass().getName().startsWith("sectorpad.")) return false;
        if(node instanceof UIComponentAPI component && (!Float.isFinite(component.getOpacity()) || component.getOpacity()<=0.01f)) return false;
        if(Boolean.FALSE.equals(read(node,"isVisible")) || Boolean.TRUE.equals(read(node,"isBeingDismissed")) || Boolean.TRUE.equals(read(node,"isSlidOut"))) return false;
        Object fader=read(node,"getFader"), brightness=read(fader,"getBrightness");
        return !(brightness instanceof Number number) || number.floatValue()>0.01f;
    }
    private static boolean isModal(Object node) {
        return PublicUiAccess.has(node,"getBackgroundDimAmount") && PublicUiAccess.has(node,"getDialogParent");
    }
    private static boolean contains(PositionAPI pos,float x,float y) { return pos!=null && x>=pos.getX() && y>=pos.getY() && x<=pos.getX()+pos.getWidth() && y<=pos.getY()+pos.getHeight(); }
    private static double area(PositionAPI pos) { return pos==null ? Double.POSITIVE_INFINITY : pos.getWidth()*pos.getHeight(); }
    private static Set<Object> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    private static Object read(Object target,String name) {
        return PublicUiAccess.read(target,name);
    }
}
