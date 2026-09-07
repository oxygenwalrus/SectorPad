package sectorpad.game;

/** Exercises bounded original-handler orchestration; this is not a live cargo UI test. */
public final class CargoQuantityTests {
    private static int checks;
    public static void main(String[] args){
        Port partial=new Port(500);var job=new CargoQuantityAdapter.Job(partial,500,170,1_000_000_000L);
        check(job.start()&&partial.count==1,"partial pickup begins at one through native port");
        job.advance(1_100_000_000L);check(partial.count<=65&&job.active,"each frame is bounded");
        for(int i=0;i<10&&job.active;i++)job.advance(1_200_000_000L+i);
        check(partial.count==170&&!job.active&&!partial.cancelled,"exact count stops without committing transaction");
        Port full=new Port(500);var entire=new CargoQuantityAdapter.Job(full,500,500,1);
        check(entire.start()&&!entire.active&&full.count==500&&full.increments==0,"whole-stack request uses one normal pickup");
        Port stale=new Port(500);stale.valid=false;var changed=new CargoQuantityAdapter.Job(stale,500,5,1);
        check(!changed.start()&&stale.count==0,"changed source is untouched");
        Port interrupted=new Port(500);var cancelled=new CargoQuantityAdapter.Job(interrupted,500,300,1);
        cancelled.start();cancelled.cancel("cancel");check(interrupted.cancelled&&!cancelled.active,"cancellation returns owned pickup");
        Port blocked=new Port(500);blocked.blockIncrement=true;var rejected=new CargoQuantityAdapter.Job(blocked,500,30,1);
        rejected.start();rejected.advance(2);check(blocked.cancelled&&!rejected.active,"native rejection cannot loop or overdraw");
        Port timed=new Port(500);var timeout=new CargoQuantityAdapter.Job(timed,500,30,1);
        timeout.start();timeout.advance(400_000_000_000L);check(timed.cancelled&&!timeout.active,"timeout returns pickup");
        Port external=new Port(500);var mismatch=new CargoQuantityAdapter.Job(external,500,30,1);
        mismatch.start();external.count=9;mismatch.advance(2);check(external.cancelled&&!mismatch.active,"unexpected pickup change cancels work");
        Port invalid=new Port(5);check(!new CargoQuantityAdapter.Job(invalid,5,6,1).start()&&invalid.count==0,"out-of-range input cannot pick up");
        System.out.println("CargoQuantityTests: "+checks+" bounded quantity orchestration checks passed (API port doubles)");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Port implements CargoQuantityAdapter.Port {
        final int total;int count,increments;boolean valid=true,cancelled,blockIncrement;
        Port(int total){this.total=total;}
        public boolean valid(){return valid;}
        public boolean pick(boolean all){count=all?total:1;return true;}
        public int picked(){return count;}
        public void increment(){increments++;if(!blockIncrement)count++;}
        public void cancel(){cancelled=true;count=0;}
    }
}
