package io.oniimai.kanade;

/**
 * Start policy for external output, free of Android types so the host suite can test it.
 *
 * Unity's displayChanged() returns true as soon as the player object exists and only queues the
 * surface switch on its UnityMain thread, so its result says nothing about graphics readiness.
 * The verified startup UI/game Update hooks provide a progress signal (not a GPU fence). Output may
 * start when (1) updates in the current phase have advanced without a gap longer than STALL_MS for the last STEADY_MS,
 * (2) the Activity is portrait, (3) its configuration has stopped changing, and (4) the current phase has
 * updated again since that last change.
 *
 * The frame source has three states. A counter (>= 0) must advance: time alone never approves it.
 * PENDING (hooks still installing) waits without limit. Only UNAVAILABLE (native library or hook
 * installation failed for good) uses the explicit compatibility fallback of NO_SIGNAL_MS, and only
 * while UNAVAILABLE is the current state: each unavailable span is timed from its own first report,
 * and leaving that state (to PENDING or a counter) ends the span.
 *
 * "Confirmed" bind and detach below are inferences from that update counter and from call timing;
 * they are not display-complete events from Android or the GPU.
 */
final class OutputGate {
    static final long STEADY_MS=1500,STALL_MS=1500,SETTLE_MS=600,ORIENT_TIMEOUT_MS=4000,NO_SIGNAL_MS=20000;
    static final int WAIT_GAME=0,WAIT_ROTATION=1,WAIT_SETTLE=2,READY=3,ROTATION_TIMEOUT=4;
    /** Unity's default SynchronizationTimeout.SurfaceDetach; read at runtime when available. */
    static final long DEFAULT_DETACH_TIMEOUT_MS=2000;
    /** Frame-source values other than a counter. */
    static final long PENDING=-1,UNAVAILABLE=-2;
    static final int PHASE_WAITING=0,PHASE_STARTUP=1,PHASE_GAME=2;
    /** Reached after Start: KanadeDX's loading screen has finished (its control UI became active). */
    static final int STAGE_LOADED=1;
    /** Lobby hand-off safety net for a game phase in which the end of loading is never reported. */
    static final long HANDOFF_FALLBACK_MS=180000;
    static final class Sample {
        final long count;
        final int phase;
        /** STAGE_* bits reached since Start; meaningful only when {@link #screens} is true. */
        final int stages;
        /** Whether this session can report those stages at all (verified UI and startup hooks installed). */
        final boolean screens;
        Sample(long count,int phase){this(count,phase,0,false);}
        Sample(long count,int phase,int stages,boolean screens){this.count=count;this.phase=phase;this.stages=stages;this.screens=screens;}
    }
    private int phase=-1;
    private long frames=-1,firstAdvanceAt=-1,runStart=-1,lastAdvanceAt=-1,configAt=-1,framesAtConfig=-1,orientWaitSince=-1,unavailableSince=-1;
    private boolean counterSeen;
    /** The most recently reported frame-source state. */
    private int source=SOURCE_PENDING;
    private static final int SOURCE_PENDING=0,SOURCE_UNAVAILABLE=1,SOURCE_COUNTER=2;

    OutputGate(long now){}

    /** Never carry startup readiness into game loading, even if polling skips the loading phase. */
    boolean frames(Sample sample,long now){
        if(phase!=sample.phase){
            phase=sample.phase;frames=-1;firstAdvanceAt=-1;runStart=-1;lastAdvanceAt=-1;
            framesAtConfig=-1;orientWaitSince=-1;
        }
        return frames(sample.count>=0&&phase==PHASE_WAITING?0:sample.count,now);
    }

    /** Feeds the frame source: a counter, PENDING or UNAVAILABLE. Returns true on the first advance. */
    boolean frames(long count,long now){
        if(count==UNAVAILABLE){
            // A new unavailable span is timed from its own first report, never from an earlier span.
            if(source!=SOURCE_UNAVAILABLE)unavailableSince=now;
            source=SOURCE_UNAVAILABLE;return false;
        }
        unavailableSince=-1;
        if(count<0){source=SOURCE_PENDING;return false;}
        source=SOURCE_COUNTER;counterSeen=true;
        boolean first=false;
        if(frames>=0&&count>frames){
            first=firstAdvanceAt<0;if(first)firstAdvanceAt=now;
            // A gap longer than STALL_MS starts a new run, so readiness always means recent continuous rendering.
            if(runStart<0||now-lastAdvanceAt>STALL_MS)runStart=now;
            lastAdvanceAt=now;
        }
        frames=count;return first;
    }
    void configurationChanged(long now){configAt=now;framesAtConfig=frames;}
    /** A new start attempt; orientation waiting is timed per attempt. */
    void begin(){orientWaitSince=-1;}
    boolean frameSignal(){return firstAdvanceAt>=0;}

    boolean gameSteady(long now){
        if(runStart>=0&&now-lastAdvanceAt<=STALL_MS)return now-runStart>=STEADY_MS;
        // A live counter that is not advancing (still loading, or stalled) never times out into readiness.
        if(counterSeen)return false;
        // Explicit compatibility path: the hook is known to be unavailable, so no frame signal can ever arrive.
        return source==SOURCE_UNAVAILABLE&&now-unavailableSince>=NO_SIGNAL_MS;
    }

    int state(boolean portrait,long now){
        if(!gameSteady(now)){orientWaitSince=-1;return WAIT_GAME;}
        boolean settled=configAt<0||now-configAt>=SETTLE_MS;
        boolean renderedSince=configAt<0||firstAdvanceAt<0||frames>framesAtConfig;
        // The orientation budget runs from when readiness is missing, not from the first steady frame:
        // after a long wait at READY (the welcome screen waiting for the game), one configuration change
        // must settle, not time out at once.
        if(portrait&&settled&&renderedSince){orientWaitSince=-1;return READY;}
        if(orientWaitSince<0)orientWaitSince=now;
        if(now-orientWaitSince>=ORIENT_TIMEOUT_MS)return ROTATION_TIMEOUT;
        return portrait?WAIT_SETTLE:WAIT_ROTATION;
    }

    /** displayChanged(0,null) blocks until Unity detaches or its timeout passes, and returns true either way. */
    static boolean detachConfirmed(long elapsedMs,long timeoutMs){return elapsedMs<Math.max(1,timeoutMs-100);}
    /**
     * A surface whose detach was not confirmed stays alive until Unity has rendered again
     * (UnityMain runs queued work in order) or, with no frame signal, for a bounded time.
     */
    static boolean detachSettled(long framesNow,long framesAtDetach,long elapsedMs){
        if(framesNow<0||framesAtDetach<0)return elapsedMs>=5000;
        return framesNow>=framesAtDetach+2&&elapsedMs>=300;
    }
    /** After binding, two further game frames show UnityMain processed the queued surface switch. */
    static boolean bindConfirmed(long framesNow,long framesAtBind){return framesNow>=0&&framesAtBind>=0&&framesNow>=framesAtBind+2;}
    /** The longest the phone waits, after the game returns to it, before its still of the last frame is removed anyway. */
    static final long RETURN_UNCOVER_MS=2000;
    /**
     * After the game returns to the phone, Unity covers its view with a still of the phone's last frame until it
     * has drawn there again. Remove it once the phone's surface is back and the game has drawn two more frames
     * (a short minimum time without a frame signal), or after RETURN_UNCOVER_MS whatever happens.
     */
    static boolean phoneUncover(long framesNow,long framesAtReturn,boolean surfaceReady,long elapsedMs){
        if(elapsedMs>=RETURN_UNCOVER_MS)return true;
        if(!surfaceReady||elapsedMs<250)return false;
        return framesNow<0||framesAtReturn<0||framesNow>=framesAtReturn+2;
    }

    /**
     * When the external lobby hands the monitor to the game. The game phase begins part-way through
     * KanadeDX's own loading screen, so where its end is reported the lobby waits for it; the STARTUP
     * self-check, notice and title then all appear on the monitor. Without that signal it keeps the earlier
     * behaviour. With it, a game phase lasting HANDOFF_FALLBACK_MS (timed from its first report) without the
     * end of loading hands over anyway, so the lobby is never stranded. The start gate must still be READY.
     */
    static boolean lobbyHandoff(Sample sample,long gameSince,long now){
        if(sample.phase!=PHASE_GAME)return false;
        if(!sample.screens)return true;
        if((sample.stages&STAGE_LOADED)!=0)return true;
        return gameSince>=0&&now-gameSince>=HANDOFF_FALLBACK_MS;
    }
    /**
     * Which window a READY gate opens. The welcome screen only on the startup screen, or in a game phase
     * whose loading screen has not ended yet; everything else opens the game output as before 1.3.5. That
     * includes the compatibility path, where hooks are unavailable, no phase is ever reported, and READY
     * comes from the NO_SIGNAL_MS fallback: a welcome screen there could never start or hand over.
     */
    static boolean opensLobby(Sample sample,long gameSince,long now){
        if(sample.phase==PHASE_STARTUP)return true;
        return sample.phase==PHASE_GAME&&!lobbyHandoff(sample,gameSince,now);
    }
    static String stageName(Sample sample){
        if(!sample.screens)return "game ready";
        if((sample.stages&STAGE_LOADED)!=0)return "loading screen finished";
        return "loading not reported after "+HANDOFF_FALLBACK_MS/1000+" s";
    }

    static String name(int state){
        switch(state){case WAIT_GAME:return "wait-game";case WAIT_ROTATION:return "wait-portrait";case WAIT_SETTLE:return "wait-settle";case READY:return "ready";default:return "orientation-timeout";}
    }
}
