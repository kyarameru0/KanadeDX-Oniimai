#pragma once
#include <stdint.h>

// Serialized by the same mutex as controller input. Only fresh input can press
// the startup button; the triggering hold is swallowed until every input lifts.
struct BootInputState {
    uint64_t touch=0,requestAt=0;
    uint32_t buttons=0;
    bool pending=false,completed=false,swallow=false;
    bool submit(uint64_t t,uint32_t b,bool enabled,uint64_t now){
        t&=(uint64_t{1}<<34)-1;b&=511;
        if(!t&&!b)swallow=false;
        if(!enabled){touch=0;buttons=0;pending=false;return false;}
        if(!completed&&((t&~touch)||(b&~buttons))){pending=true;requestAt=now;}
        touch=t;buttons=b;
        return !swallow;
    }
    bool take(bool ready,uint64_t now){
        bool fire=pending&&!completed&&ready&&now>=requestAt&&now-requestAt<=500;
        pending=false;
        return fire;
    }
    void started(){completed=true;pending=false;swallow=(touch||buttons);}
};
