package io.oniimai.kanade;

/** First-install/relaunch progress, pause, missing hooks, and main-screen transitions. */
public final class InitialSetupGateTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static OutputGate.Sample sample(long count,int phase){return new OutputGate.Sample(count,phase);}
    static boolean run(InitialSetupGate gate,long from,int phase,int stages,boolean screens){
        boolean ready=false;
        for(long t=from;t<=from+2000;t+=250)
            ready=gate.ready(new OutputGate.Sample(t+1,phase,stages,screens),true,t);
        return ready;
    }
    public static void main(String[] args){
        InitialSetupGate gate=new InitialSetupGate();
        for(long t=0;t<=3600000;t+=60000){
            check(!gate.ready(sample(t,OutputGate.PHASE_WAITING),true,t),"installer never times out into setup");
            check(!gate.ready(sample(OutputGate.PENDING,OutputGate.PHASE_WAITING),true,t),"hooks pending waits");
            check(!gate.ready(sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_WAITING),true,t),"unavailable has no 20 s compatibility bypass");
        }
        check(!gate.ready(sample(100,OutputGate.PHASE_STARTUP),true,3600100),"one main frame does not open setup");
        check(run(gate,3601000,OutputGate.PHASE_STARTUP,0,false),"main screen opens after installer finishes");
        check(!gate.ready(sample(3604000,OutputGate.PHASE_WAITING),true,3604000),"installer reopening clears main readiness");
        check(!gate.ready(sample(3605000,OutputGate.PHASE_STARTUP),true,3605000),"returning main must settle again");
        check(run(gate,3610000,OutputGate.PHASE_STARTUP,0,false),"main ready again");
        check(!gate.ready(sample(3612500,OutputGate.PHASE_STARTUP),false,3612500),"background or focus loss waits");
        check(!gate.ready(sample(3613000,OutputGate.PHASE_STARTUP),true,3613000),"resume cannot reuse old readiness");
        check(run(gate,3620000,OutputGate.PHASE_STARTUP,0,false),"resumed main is allowed");
        check(!gate.ready(sample(3622001,OutputGate.PHASE_STARTUP),true,3625000),"frozen main frame is not ready");
        check(run(gate,3630000,OutputGate.PHASE_STARTUP,0,false),"main recovers after a stall");
        gate.reset();
        check(!gate.ready(sample(3640000,OutputGate.PHASE_STARTUP),true,3640000),"recreation/configuration cannot retain readiness");
        check(!run(gate,3650000,OutputGate.PHASE_GAME,0,true),"Start tapped before setup: wait during game loading");
        check(!run(gate,3660000,OutputGate.PHASE_GAME,OutputGate.STAGE_LOADED,false),"unverified stage is not completion");
        check(run(gate,3670000,OutputGate.PHASE_GAME,OutputGate.STAGE_LOADED,true),"setup remains reachable after loading if Start was tapped early");
        check(!gate.ready(sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_STARTUP),true,3680000),"negative counter never approves even a stale startup phase");
        System.out.println("PASS: "+checks+" initial-setup timing checks");
    }
}
