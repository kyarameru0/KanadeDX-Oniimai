package io.oniimai.kanade;

/** External-output start policy: game rendering, portrait settle, bind/detach confirmation. */
public final class OutputGateTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    /** Feeds one frame every 16 ms from `from` to `to` (exclusive), returning the last count. */
    static long run(OutputGate gate,long count,long from,long to){for(long t=from;t<to;t+=16)gate.frames(++count,t);return count;}
    static long screen(OutputGate gate,int phase,long count,long from,long to){
        for(long t=from;t<to;t+=16)gate.frames(new OutputGate.Sample(++count,phase),t);
        return count;
    }

    public static void main(String[] args){
        // Review case A: monitor attached at launch. Output must wait for the game, not a fixed 350 ms.
        OutputGate launch=new OutputGate(0);
        launch.frames(-1,100);launch.begin();
        check(launch.state(true,1050)==OutputGate.WAIT_GAME,"no output while hooks are not reporting frames");
        launch.frames(0,2000);
        check(launch.state(true,2500)==OutputGate.WAIT_GAME,"a frame count alone is not rendering");
        long count=run(launch,0,3000,4000);
        check(launch.frameSignal(),"advancing frames are a signal");
        check(launch.state(true,4000)==OutputGate.WAIT_GAME,"rendering for under 1.5 s is not steady");
        count=run(launch,count,4000,4600);
        check(launch.state(true,4600)==OutputGate.READY,"steady portrait rendering is ready");
        check(launch.state(true,4600+OutputGate.STALL_MS+1)==OutputGate.WAIT_GAME,"stalled game (loading hitch) waits again");
        launch.frames(count+1,6200);
        check(launch.state(true,6200)==OutputGate.WAIT_GAME,"one frame after a stall is not readiness (1.3.0 review)");
        count=run(launch,count+1,6216,7700);
        check(launch.state(true,7700)==OutputGate.READY,"a new continuous 1.5 s run after the stall is ready");

        // Review case C: game running landscape, monitor connected later.
        OutputGate turn=new OutputGate(0);
        long c=run(turn,0,0,3000);turn.begin();
        check(turn.state(false,3000)==OutputGate.WAIT_ROTATION,"landscape game waits for portrait");
        c=run(turn,c,3000,3400);
        turn.configurationChanged(3400);
        check(turn.state(true,3400)==OutputGate.WAIT_SETTLE,"portrait just reported is not settled");
        check(turn.state(true,3400+OutputGate.SETTLE_MS)==OutputGate.WAIT_SETTLE,"settled but no frame rendered since the change");
        turn.frames(c+1,3400+OutputGate.SETTLE_MS+10);
        check(turn.state(true,3400+OutputGate.SETTLE_MS+10)==OutputGate.READY,"portrait, settled and rendered again is ready");
        turn.configurationChanged(4100);
        check(turn.state(true,4120)==OutputGate.WAIT_SETTLE,"a later configuration change restarts the settle wait");

        OutputGate stuck=new OutputGate(0);
        long s=run(stuck,0,0,2000);stuck.begin();
        check(stuck.state(false,2000)==OutputGate.WAIT_ROTATION,"orientation wait begins");
        s=run(stuck,s,2000,2000+OutputGate.ORIENT_TIMEOUT_MS);
        check(stuck.state(false,2000+OutputGate.ORIENT_TIMEOUT_MS)==OutputGate.ROTATION_TIMEOUT,"rotation that never lands times out instead of connecting landscape");
        stuck.begin();
        check(stuck.state(false,2000+OutputGate.ORIENT_TIMEOUT_MS+16)==OutputGate.WAIT_ROTATION,"a new attempt gets a new orientation budget");

        // 1.3.5: a long wait at READY, then a configuration change (KanadeDX loading its main scene).
        OutputGate held=new OutputGate(0);
        long h=run(held,0,0,2000);held.begin();
        check(held.state(true,2000)==OutputGate.READY,"ready before the long wait");
        h=run(held,h,2000,40000);
        check(held.state(true,40000)==OutputGate.READY,"still ready after waiting 38 s");
        held.configurationChanged(40000);
        check(held.state(true,40016)==OutputGate.WAIT_SETTLE,"a configuration change after a long ready wait settles instead of timing out");
        h=run(held,h,40000,40000+OutputGate.SETTLE_MS+50);
        check(held.state(true,40000+OutputGate.SETTLE_MS+50)==OutputGate.READY,"and is ready again");
        long lost=40000+OutputGate.SETTLE_MS+66;
        check(held.state(false,lost)==OutputGate.WAIT_ROTATION,"a real turn to landscape waits");
        h=run(held,h,lost,lost+OutputGate.ORIENT_TIMEOUT_MS);
        check(held.state(false,lost+OutputGate.ORIENT_TIMEOUT_MS)==OutputGate.ROTATION_TIMEOUT,"and times out from when readiness was lost");

        OutputGate loading=new OutputGate(0);
        run(loading,0,0,2000);loading.begin();
        check(loading.state(false,9000)==OutputGate.WAIT_GAME,"a long load does not consume the rotation budget");

        // 1.3.0 review case: hooks installed, counter stays 0 (game loop not started or stuck). Time never approves it.
        OutputGate zero=new OutputGate(0);zero.begin();
        for(long t=0;t<=30000;t+=100)zero.frames(0,t);
        check(zero.state(true,30000)==OutputGate.WAIT_GAME,"a live counter that never advances is never ready");
        OutputGate installing=new OutputGate(0);installing.begin();
        for(long t=0;t<=30000;t+=100)installing.frames(OutputGate.PENDING,t);
        check(installing.state(true,30000)==OutputGate.WAIT_GAME,"hooks still installing (slow boot) wait without a time limit");

        OutputGate noHook=new OutputGate(0);noHook.frames(OutputGate.PENDING,0);noHook.begin();
        noHook.frames(OutputGate.UNAVAILABLE,5000);noHook.frames(OutputGate.UNAVAILABLE,9000);
        check(noHook.state(true,5000+OutputGate.NO_SIGNAL_MS-1)==OutputGate.WAIT_GAME,"unavailable hook: bounded wait counted from the failure report");
        check(noHook.state(true,5000+OutputGate.NO_SIGNAL_MS)==OutputGate.READY,"unavailable hook: explicit compatibility fallback");
        check(noHook.state(false,5000+OutputGate.NO_SIGNAL_MS)==OutputGate.WAIT_ROTATION,"the fallback still needs portrait");
        // 1.3.1 review cases: leaving UNAVAILABLE ends its timer; a new span is timed from its own report.
        OutputGate recovered=new OutputGate(0);recovered.frames(OutputGate.UNAVAILABLE,1000);recovered.begin();
        for(long t=5000;t<=21000;t+=100)recovered.frames(OutputGate.PENDING,t);
        check(recovered.state(true,21000)==OutputGate.WAIT_GAME,"unavailable -> installing waits; the old fallback timer no longer applies");
        check(recovered.state(true,60000)==OutputGate.WAIT_GAME,"installing keeps waiting without a time limit");
        OutputGate again=new OutputGate(0);again.frames(OutputGate.UNAVAILABLE,1000);again.frames(OutputGate.PENDING,5000);again.begin();
        again.frames(OutputGate.UNAVAILABLE,20500);again.frames(OutputGate.UNAVAILABLE,21000);
        check(again.state(true,21000)==OutputGate.WAIT_GAME,"a new unavailable span 500 ms old is not the earlier span");
        check(again.state(true,20500+OutputGate.NO_SIGNAL_MS-1)==OutputGate.WAIT_GAME,"new span: still waiting just before its own 20 s");
        check(again.state(true,20500+OutputGate.NO_SIGNAL_MS)==OutputGate.READY,"new span: fallback after its own 20 s");
        OutputGate late=new OutputGate(0);late.frames(OutputGate.UNAVAILABLE,0);late.frames(7,1000);late.begin();
        check(late.state(true,40000)==OutputGate.WAIT_GAME,"once a counter appears, the time fallback no longer applies");

        // The character/start-button screen renders before UpdateAmInput has ever run.
        OutputGate startup=new OutputGate(0);startup.begin();
        startup.frames(new OutputGate.Sample(0,OutputGate.PHASE_WAITING),0);
        check(startup.state(true,60000)==OutputGate.WAIT_GAME,"no startup UI yet is not an elapsed-time bypass");
        long ui=screen(startup,OutputGate.PHASE_STARTUP,0,60000,62000);
        check(startup.state(true,62000)==OutputGate.READY,"interactive startup UI can connect without pressing Start");
        check(startup.state(false,62000)==OutputGate.WAIT_ROTATION,"startup UI still requires portrait");
        startup.configurationChanged(62000);
        check(startup.state(true,62200)==OutputGate.WAIT_SETTLE,"startup rotation still settles");
        ui=screen(startup,OutputGate.PHASE_STARTUP,ui,62200,62700);
        check(startup.state(true,62700)==OutputGate.READY,"startup rotation settles with further UI updates");
        // Pressing Start invalidates the UI proof immediately, even with a high retained counter.
        startup.frames(new OutputGate.Sample(ui,OutputGate.PHASE_WAITING),62701);
        check(startup.state(true,62701)==OutputGate.WAIT_GAME,"Start-to-loading clears startup readiness");
        startup.frames(new OutputGate.Sample(ui,OutputGate.PHASE_WAITING),90000);
        check(startup.state(true,90000)==OutputGate.WAIT_GAME,"long game loading does not reuse startup updates");
        long bindCount=ui;
        check(!OutputGate.bindConfirmed(ui,bindCount),"waiting without updates cannot confirm a surface switch");
        startup.frames(new OutputGate.Sample(++ui,OutputGate.PHASE_GAME),90001);
        check(startup.state(true,90001)==OutputGate.WAIT_GAME,"one initial game update is not steady");
        check(!OutputGate.bindConfirmed(ui,bindCount),"one update after loading is not two");
        ui=screen(startup,OutputGate.PHASE_GAME,ui,90017,91700);
        check(startup.state(true,91700)==OutputGate.READY,"game becomes ready after its own stable interval");
        check(OutputGate.bindConfirmed(ui,bindCount),"monotonic startup/game count confirms later progress");

        OutputGate skipped=new OutputGate(0);
        long next=screen(skipped,OutputGate.PHASE_STARTUP,0,0,2000);
        check(skipped.state(true,2000)==OutputGate.READY,"startup established before a quick transition");
        skipped.frames(new OutputGate.Sample(++next,OutputGate.PHASE_GAME),2010);
        check(skipped.state(true,2010)==OutputGate.WAIT_GAME,"polling that misses loading still resets on game phase");

        OutputGate hidden=new OutputGate(0);
        long visible=screen(hidden,OutputGate.PHASE_STARTUP,0,0,2000);
        hidden.frames(new OutputGate.Sample(visible,OutputGate.PHASE_WAITING),2001);
        hidden.frames(new OutputGate.Sample(++visible,OutputGate.PHASE_STARTUP),2010);
        check(hidden.state(true,2010)==OutputGate.WAIT_GAME,"a reappearing startup button needs fresh stability");

        check(OutputGate.detachConfirmed(15,2000),"quick detach is confirmed");
        check(!OutputGate.detachConfirmed(1999,2000)&&!OutputGate.detachConfirmed(2003,2000),"a call lasting Unity's timeout is not confirmed");
        check(!OutputGate.detachSettled(100,99,1000)&&OutputGate.detachSettled(102,100,300),"retained surface waits for two rendered frames");
        check(!OutputGate.detachSettled(110,100,100),"and a short minimum time");
        check(!OutputGate.detachSettled(-1,-1,4999)&&OutputGate.detachSettled(-1,-1,5000),"without frames: bounded retention");
        check(!OutputGate.bindConfirmed(11,10)&&OutputGate.bindConfirmed(12,10),"bind confirmed by two later frames");
        check(!OutputGate.bindConfirmed(-1,-1),"no frame signal never claims confirmation");
        // Back on the phone: the still of the last frame goes once Unity draws there again, or after a bound.
        check(!OutputGate.phoneUncover(100,100,true,1000),"no new frame yet: the still stays");
        check(!OutputGate.phoneUncover(105,100,false,1000),"phone surface not back yet: the still stays");
        check(!OutputGate.phoneUncover(105,100,true,100),"a short minimum time");
        check(OutputGate.phoneUncover(102,100,true,250),"surface back and two frames drawn: uncover");
        check(OutputGate.phoneUncover(-1,-1,true,250),"without a frame signal the surface is enough");
        check(OutputGate.phoneUncover(100,100,false,OutputGate.RETURN_UNCOVER_MS),"never longer than the bound");

        DisplayTimeline timeline=new DisplayTimeline(1000);
        check(timeline.add(1250,"bind queued").equals("+250ms bind queued"),"timeline relative to session start");
        for(int i=0;i<DisplayTimeline.CAPACITY+5;i++)timeline.add(2000+i,"e"+i);
        check(timeline.size()==DisplayTimeline.CAPACITY,"timeline bounded");
        check(timeline.dump().startsWith("+1005ms e5")&&timeline.dump().endsWith("e"+(DisplayTimeline.CAPACITY+4)),"oldest entries dropped first");
        // Lobby hand-off: the game phase starts part-way through KanadeDX's loading screen; wait for its end.
        int game=OutputGate.PHASE_GAME;
        check(!OutputGate.lobbyHandoff(new OutputGate.Sample(9,OutputGate.PHASE_STARTUP,OutputGate.STAGE_LOADED,true),0,1000),"never before the game phase");
        check(!OutputGate.lobbyHandoff(new OutputGate.Sample(9,game,0,true),0,60000),"the loading screen keeps the lobby");
        check(OutputGate.lobbyHandoff(new OutputGate.Sample(9,game,OutputGate.STAGE_LOADED,true),0,60000),"end of loading hands over");
        check(OutputGate.lobbyHandoff(new OutputGate.Sample(9,game),0,0),"without the loading signal the earlier behaviour remains");
        check(!OutputGate.lobbyHandoff(new OutputGate.Sample(9,game,0,true),1000,1000+OutputGate.HANDOFF_FALLBACK_MS-1)
            &&OutputGate.lobbyHandoff(new OutputGate.Sample(9,game,0,true),1000,1000+OutputGate.HANDOFF_FALLBACK_MS),"no screen ever reported: hand over after the fallback");
        check(!OutputGate.lobbyHandoff(new OutputGate.Sample(9,game,0,true),-1,Long.MAX_VALUE/2),"fallback is timed from the first game report");
        check(OutputGate.stageName(new OutputGate.Sample(9,game,OutputGate.STAGE_LOADED,true)).equals("loading screen finished"),"timeline names the signal");

        // Which window a READY gate opens (1.3.5 review P2: the compatibility path must not open a welcome screen).
        check(OutputGate.opensLobby(new OutputGate.Sample(9,OutputGate.PHASE_STARTUP,0,true),-1,0),"startup screen opens the welcome screen");
        check(OutputGate.opensLobby(new OutputGate.Sample(9,game,0,true),0,60000),"game still loading opens the welcome screen");
        check(!OutputGate.opensLobby(new OutputGate.Sample(9,game,OutputGate.STAGE_LOADED,true),0,60000),"loaded game opens the game output");
        check(!OutputGate.opensLobby(new OutputGate.Sample(9,game),0,0),"game without the loading signal opens the game output");
        check(!OutputGate.opensLobby(new OutputGate.Sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_WAITING),-1,0),"unavailable hooks open the game output");
        check(!OutputGate.opensLobby(new OutputGate.Sample(OutputGate.PENDING,OutputGate.PHASE_WAITING),-1,0),"a waiting phase never opens a welcome screen");
        // The whole compatibility path: unavailable hooks reach READY after NO_SIGNAL_MS and open the game output.
        OutputGate compat=new OutputGate(0);compat.begin();
        OutputGate.Sample none=new OutputGate.Sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_WAITING);
        for(long t=0;t<=OutputGate.NO_SIGNAL_MS;t+=250)compat.frames(none,t);
        check(compat.state(true,OutputGate.NO_SIGNAL_MS)==OutputGate.READY&&!OutputGate.opensLobby(none,-1,OutputGate.NO_SIGNAL_MS),
            "compatibility READY opens the game output, as in 1.3.4");
        for(long t=OutputGate.NO_SIGNAL_MS;t<=OutputGate.NO_SIGNAL_MS+600000;t+=1000)compat.frames(none,t);
        check(!OutputGate.opensLobby(none,-1,OutputGate.NO_SIGNAL_MS+600000),"and still does ten minutes later");
        System.out.println("PASS: "+checks+" external-output start/confirmation checks");
    }
}
