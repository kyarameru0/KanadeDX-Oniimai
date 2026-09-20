#pragma once
#include <stdint.h>

// Process identity comparisons do not dereference or retain managed objects.
// Result startup may overlap GameProcess release; an old release must not clear a new session.
struct StatsLifecycle {
    uintptr_t gameProcess=0,resultProcess=0;
    bool available=false,finished=false,result=false;
    void startGame(uintptr_t p){gameProcess=p;resultProcess=0;available=finished=result=false;}
    bool playing()const{return gameProcess!=0&&!result;}
    void capture(bool final){available=true;finished|=final;}
    bool releaseGame(uintptr_t p){
        if(gameProcess!=p)return false;
        gameProcess=0;
        if(!result&&!finished)available=false;
        return true;
    }
    void startResult(uintptr_t p){resultProcess=p;result=true;gameProcess=0;}
    bool releaseResult(uintptr_t p){
        if(resultProcess!=p)return false;
        gameProcess=resultProcess=0;available=finished=result=false;return true;
    }
};
inline bool statsDecimal(uint32_t flags,uint32_t hi,uint32_t lo,uint32_t mid,double& value){
    unsigned scale=(flags>>16)&255;
    if((flags&0x7f00ffffu)||scale>28)return false;
    value=double(hi)*18446744073709551616.0+double(mid)*4294967296.0+lo;
    for(unsigned i=0;i<scale;i++)value/=10.0;
    if(flags&0x80000000u)value=-value;
    return value>=0.0&&value<=101.00001;
}
