package sectorpad.core;

import java.util.List;

/** Pure wheel state; rendering and legal action execution are separate. */
public final class RadialModel {
    public record Entry(String id,String label,String description,boolean enabled,String reason,
                        boolean dangerous,Runnable execute) {}
    private List<Entry> entries=List.of();
    private int selected=-1,page,slots=8;
    private boolean open,hold;
    private String opener="";
    private String title="";
    private float activation=.35f,cancel=.25f;
    private double hysteresis=Math.toRadians(6);
    public void open(String title,List<Entry> entries,boolean hold,String opener,int slots) {
        if (slots!=4 && slots!=6 && slots!=8) throw new IllegalArgumentException("Wheel slots must be 4, 6 or 8");
        this.title=title; this.entries=List.copyOf(entries);this.hold=hold;this.opener=opener;
        this.slots=slots;selected=-1;page=0;open=true;
    }
    public void tune(float activation,float cancel,double hysteresisDegrees) {
        if (cancel<0 || activation<=cancel || activation>1 || hysteresisDegrees<0 || hysteresisDegrees>15)
            throw new IllegalArgumentException("Invalid wheel thresholds");
        this.activation=activation;this.cancel=cancel;this.hysteresis=Math.toRadians(hysteresisDegrees);
    }
    public void point(float x,float y) {
        if(!open) return;
        double magnitude=Math.hypot(x,y);
        if(magnitude<=cancel) { selected=-1;return; }
        if(selected<0 && magnitude<activation) return;
        double theta=Math.atan2(x,y);if(theta<0) theta+=Math.PI*2;
        double step=Math.PI*2/slots;
        if(selected>=0 && InputMath.angleDistance(theta,selected*step)<=step/2+hysteresis) return;
        int candidate=(int)Math.floor(theta/step+.5)%slots;
        selected=page*slots+candidate<entries.size()?candidate:-1;
    }
    public void navigate(int delta) {
        if(!open) return;
        int count=visible().size();if(count==0)return;
        selected=selected<0 ? (delta<0?count-1:0) : Math.floorMod(selected+delta,count);
    }
    public void page(int delta) { page=Math.floorMod(page+delta,pages());selected=-1; }
    public Entry commit() { Entry result=highlighted();close();return result!=null&&result.enabled()?result:null; }
    public void close() { open=false;selected=-1; }
    public Entry highlighted() { int i=page*slots+selected;return selected>=0&&i<entries.size()?entries.get(i):null; }
    public List<Entry> visible() { return entries.subList(Math.min(page*slots,entries.size()),Math.min((page+1)*slots,entries.size())); }
    public int pages(){return Math.max(1,(entries.size()+slots-1)/slots);}
    public boolean isOpen(){return open;}
    public boolean isHold(){return hold;}
    public String opener(){return opener;}
    public String title(){return title;}
    public int selected(){return selected;}
    public int slots(){return slots;}
    public int page(){return page;}
}
