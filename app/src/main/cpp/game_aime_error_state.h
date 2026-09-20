#pragma once
#include <stdint.h>

// Ownership is an identity token, never a managed pointer dereferenced here.
// Only an actual new AimeUnitStart may clear an error injected by this adapter.
struct GameAimeErrorState {
    static constexpr int READ_WARNING=1;
    uintptr_t unit=0, info=0;
    int previousCategory=0;
    static bool readEvent(bool completedError,bool readFlag,bool enableFlag){return completedError&&readFlag&&enableFlag;}
    static bool waiting(bool busy,bool error,bool confirm,bool result){return busy&&!error&&!confirm&&!result;}
    void committed(uintptr_t unitIdentity,uintptr_t infoIdentity,int category){unit=unitIdentity;info=infoIdentity;previousCategory=category;}
    bool completedReadFailure(uintptr_t unitIdentity,uintptr_t infoIdentity,bool busy,bool error,bool confirm,bool result,int category,int managerState,int managerResult)const{
        return unit&&info&&unit==unitIdentity&&info==infoIdentity&&!busy&&error&&!confirm&&!result&&category==READ_WARNING&&managerState==9&&managerResult==4;
    }
    bool start(uintptr_t unitIdentity,uintptr_t infoIdentity,bool error,bool result,int category,int& restore){
        bool owns=unit&&unit==unitIdentity&&info==infoIdentity&&error&&!result&&category==READ_WARNING;
        if(owns)restore=previousCategory;
        unit=info=0;previousCategory=0;return owns;
    }
};
