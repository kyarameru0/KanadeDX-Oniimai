package io.oniimai.kanade;

/** First-run setup must not cover KanadeDX's streaming-file installer. */
final class InitialSetupGate {
    private OutputGate progress;

    boolean ready(OutputGate.Sample sample,boolean uiAvailable,long now){
        // STARTUP comes from the verified visible main canvas/start button with no processing UI.
        // If the user tapped Start before we could show setup, wait until game loading has ended.
        boolean main=sample.phase==OutputGate.PHASE_STARTUP;
        boolean loaded=sample.phase==OutputGate.PHASE_GAME&&sample.screens&&(sample.stages&OutputGate.STAGE_LOADED)!=0;
        if(!uiAvailable||sample.count<0||(!main&&!loaded)){reset();return false;}
        if(progress==null)progress=new OutputGate(now);
        progress.frames(sample,now);
        // Deliberately no timeout/UNAVAILABLE fallback: downloading may take arbitrarily long.
        return progress.gameSteady(now);
    }

    void reset(){progress=null;}
}
