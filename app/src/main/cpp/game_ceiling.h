#pragma once
#include <sys/mman.h>
#include <unistd.h>
#include <stdio.h>
#include "arm64_stub_branch.h"

// Actual billboard RGB commands, independent of the BD15070 white FET channels.
// Verified IO.Jvs.SetPwmOutput is exactly RET, followed immediately by other
// methods. Replace only that instruction, never pass this stub to Dobby.
namespace GameCeiling {
static std::atomic<int> installStatus{0};
static void* thunk=nullptr; // Process-lifetime mapping after any published branch.
static void (*originalBlockColor)(int,LedColor,const void*);
static void capture(uint8_t player,LedColor color){
    if(player!=0||installStatus.load()!=1)return;
    pthread_mutex_lock(&ledLock);leds.set(LedState::BILLBOARD,color);pthread_mutex_unlock(&ledLock);
}
static void pwm(void*,uint8_t player,LedColor color,const void*){capture(player,color);}
static void blockColor(int id,LedColor color,const void* method){
    if(id==11)capture(0,color); // DB.LedBlockID.Billboard_1P; 2P is 24.
    originalBlockColor(id,color,method);
}
static int currentProtection(uintptr_t address,size_t length){
    FILE* maps=fopen("/proc/self/maps","r");if(!maps)return -1;
    char line[512],perms[5];unsigned long begin,end;int protection=-1;
    while(fgets(line,sizeof(line),maps)){
        if(sscanf(line,"%lx-%lx %4s",&begin,&end,perms)!=3)continue;
        if(address<begin||address+length>end)continue;
        protection=(perms[0]=='r'?PROT_READ:0)|(perms[1]=='w'?PROT_WRITE:0)|(perms[2]=='x'?PROT_EXEC:0);break;
    }
    fclose(maps);return protection;
}
static void* allocateNear(uintptr_t source,size_t pageSize,uint32_t& branch){
    constexpr uintptr_t STEP=0x200000;
    uintptr_t aligned=source&~(STEP-1);
    // mmap receives hints only. An occupied range is never replaced/unmapped.
    for(uintptr_t distance=STEP;distance<0x08000000;distance+=STEP){
        for(int sign=0;sign<2;sign++){
            if((sign&&aligned<distance)||(!sign&&UINTPTR_MAX-aligned<distance))continue;
            uintptr_t hint=sign?aligned-distance:aligned+distance;
            void* result=mmap(reinterpret_cast<void*>(hint),pageSize,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
            if(result==MAP_FAILED)continue;
            if(Arm64StubBranch::encode(source,reinterpret_cast<uintptr_t>(result),branch))return result;
            munmap(result,pageSize);
        }
    }
    return nullptr;
}
static bool patchRet(uintptr_t address){
    long rawPage=sysconf(_SC_PAGESIZE);if(rawPage<4096||(rawPage&(rawPage-1)))return false;
    size_t pageSize=size_t(rawPage);uintptr_t page=address&~(uintptr_t(pageSize)-1);
    int protection=currentProtection(page,pageSize);
    if(protection<0||(protection&(PROT_READ|PROT_EXEC))!=(PROT_READ|PROT_EXEC))return false;
    auto* instruction=reinterpret_cast<uint32_t*>(address);
    if(__atomic_load_n(instruction,__ATOMIC_ACQUIRE)!=Arm64StubBranch::RET)return false;
    uint32_t branch=0;void* memory=allocateNear(address,pageSize,branch);if(!memory)return false;
    Arm64StubBranch::Thunk jump; jump.target=reinterpret_cast<uintptr_t>(pwm);
    memcpy(memory,&jump,sizeof(jump));
    __builtin___clear_cache(static_cast<char*>(memory),static_cast<char*>(memory)+sizeof(jump));
    if(mprotect(memory,pageSize,PROT_READ|PROT_EXEC)!=0){munmap(memory,pageSize);return false;}
    if(mprotect(reinterpret_cast<void*>(page),pageSize,protection|PROT_WRITE)!=0){munmap(memory,pageSize);return false;}
    // Publish a single aligned instruction. The original method was a no-op;
    // the replacement retains that behavior apart from copying its RGB value.
    uint32_t expected=Arm64StubBranch::RET;
    installStatus.store(1);
    bool changed=__atomic_compare_exchange_n(instruction,&expected,branch,false,__ATOMIC_RELEASE,__ATOMIC_RELAXED);
    if(changed){thunk=memory;__builtin___clear_cache(reinterpret_cast<char*>(address),reinterpret_cast<char*>(address+4));}
    int restored=mprotect(reinterpret_cast<void*>(page),pageSize,protection);
    if(restored!=0){
        installStatus.store(-1);
        if(changed){
            __atomic_store_n(instruction,Arm64StubBranch::RET,__ATOMIC_RELEASE);
            __builtin___clear_cache(reinterpret_cast<char*>(address),reinterpret_cast<char*>(address+4));
        }
        if(mprotect(reinterpret_cast<void*>(page),pageSize,protection)!=0)
            __android_log_print(ANDROID_LOG_ERROR,"OniimaiKanade","Ceiling stub page protection restore failed");
        if(!changed)munmap(memory,pageSize);
        return false; // Retain thunk: a concurrent call could already be in it.
    }
    if(!changed){installStatus.store(-1);munmap(memory,pageSize);return false;}
    return true;
}
static void install(uintptr_t base){
    if(installStatus.load()!=0)return;
    if(!matchesTarget(reinterpret_cast<void*>(base+targetBuild->RVA_LED_PWM),targetBuild->SIG_LED_PWM)||
       !matchesTarget(reinterpret_cast<void*>(base+targetBuild->RVA_LED_BLOCK_COLOR),targetBuild->SIG_LED_BLOCK_COLOR)){
        installStatus=-1;
    }else if(hookFunction(reinterpret_cast<void*>(base+targetBuild->RVA_LED_BLOCK_COLOR),reinterpret_cast<void*>(blockColor),reinterpret_cast<void**>(&originalBlockColor))!=0){
        installStatus=-1;
    }else if(!patchRet(base+targetBuild->RVA_LED_PWM)){
        installStatus=-1;unhookFunction(reinterpret_cast<void*>(base+targetBuild->RVA_LED_BLOCK_COLOR));
    }
    if(installStatus.load()!=1){
        pthread_mutex_lock(&ledLock);leds.seen&=~(1u<<LedState::BILLBOARD);pthread_mutex_unlock(&ledLock);
        __android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Ceiling RGB capture unavailable; button/FET LEDs remain independent");
    }else __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified JVS billboard RGB capture installed; adjacent methods untouched");
}
}
