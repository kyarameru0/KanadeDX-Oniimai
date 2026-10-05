#pragma once
#include <stdint.h>

// Main-thread progress for output switching, serialized by stateLock.
// Startup UI and game input use different Update hooks. Keep one monotonic
// counter for surface lifetime checks, but separate their readiness phases.
struct DisplayFrameState {
    enum Phase { WAITING=0, STARTUP=1, GAME=2 };
    // Reached after Start. GAME begins part-way through KanadeDX's own loading screen; LOADED marks
    // that screen ending (the game's control UI becomes active as the loading overlay fades out).
    enum Stage { LOADED=1 };
    uint64_t updates=0;
    int phase=WAITING,stages=0;
    bool startRequested=false;

    void startup(bool ready){
        if(startRequested||phase==GAME)return;
        phase=ready?STARTUP:WAITING;
        if(ready)++updates;
    }
    void starting(){startRequested=true;stages=0;if(phase!=GAME)phase=WAITING;}
    void reached(int stage){if(startRequested)stages|=stage;}
    void game(){phase=GAME;++updates;}
};
