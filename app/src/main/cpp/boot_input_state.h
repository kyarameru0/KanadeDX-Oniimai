#pragma once
#include <stdint.h>

// Serialized by the same mutex as controller input. Only fresh input can press
// the startup button; the triggering hold is swallowed until every input lifts.
struct BootInputState {
    uint64_t touch=0,requestAt=0;
    uint32_t buttons=0;
    bool pending=false,completed=false,swallow=false;
    bool manualPending=false;
    uint64_t manualAt=0;
    bool requestManual(uint64_t now){if(completed)return false;manualPending=true;manualAt=now;return true;}
    bool submit(uint64_t t,uint32_t b,bool enabled,uint64_t now){
        // TEST/SERVICE must never press the launch button or prolong its hold.
        t&=(uint64_t{1}<<34)-1;b&=511;
        if(!t&&!b)swallow=false;
        if(!enabled){touch=0;buttons=0;pending=false;return false;}
        if(!completed&&((t&~touch)||(b&~buttons))){pending=true;requestAt=now;}
        touch=t;buttons=b;
        return !swallow;
    }
    bool take(bool ready,uint64_t now){
        bool physical=pending&&now>=requestAt&&now-requestAt<=500;
        bool manual=manualPending&&now>=manualAt&&now-manualAt<=500;
        bool fire=!completed&&ready&&(physical||manual);
        pending=false;manualPending=false;
        return fire;
    }
    void started(){completed=true;pending=false;manualPending=false;swallow=(touch||buttons);}
};
