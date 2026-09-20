#pragma once
#include "game_aime_state.h"
#include "game_aime_led_state.h"
#include "game_aime_error_state.h"

namespace GameAime {
static std::atomic<int> hookStatus{0};
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static GameAimeState input;
static GameAimeLedState readerLed;
static GameAimeErrorState injectedError;
static void (*originalUpdate)(void*,const void*);
static void (*originalStart)(void*,void*,const void*);
static void (*originalUse)(void*,const void*);
static void (*originalManagerExecute)(void*,const void*);
static bool (*originalAdvCheck)(void*,const void*);
static bool (*originalAnyRead)(void*,const void*);
static void* (*stringNew)(const char*);
static void* (*objectClass)(void*);
static const char* (*className)(void*);
static const char* (*classNamespace)(void*);
static void* (*fieldByName)(void*,const char*);
static size_t (*fieldOffset)(void*);
static void (*staticGet)(void*,void*);
static void* currentUnitField;
static bool unitLayoutReady=false;
static bool managerLayoutReady=false,errorLayoutReady=false;
static bool managerResultDiagnosticReady=false;
static uint64_t lastErrorResultGeneration=0;
static uint64_t lastErrorAdvanceGeneration=0;
static uint64_t lastErrorReadGeneration=0;
static bool fieldAt(void* klass,const char* name,size_t offset){void* f=fieldByName(klass,name);return f&&fieldOffset(f)==offset;}
static bool named(void* klass,const char* name){return klass&&!strcmp(className(klass),name)&&!strcmp(classNamespace(klass),"AMDaemon");}
static bool verifyUnit(void* unit){
    if(!unit)return false;
    if(unitLayoutReady)return true;
    void* klass=objectClass(unit);
    if(!named(klass,"AimeUnit")||!fieldAt(klass,"IsBusy",0x10)||!fieldAt(klass,"HasError",0x11)||!fieldAt(klass,"ErrorInfo",0x18)||!fieldAt(klass,"HasConfirm",0x20)||!fieldAt(klass,"HasResult",0x21)){hookStatus=-7;return false;}
    unitLayoutReady=true;
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Aime game metadata verified; physical cards use the existing game scan flow");
    return true;
}
static bool verifyError(void* error){
    if(!error)return false;
    if(!errorLayoutReady){
        void* klass=objectClass(error);errorLayoutReady=named(klass,"AimeErrorInfo")&&fieldAt(klass,"Category",0x10);
        if(!errorLayoutReady)hookStatus=-7;
    }
    return errorLayoutReady;
}
static bool unitWaiting(void* unit){
    if(!verifyUnit(unit))return false;
    const auto* bytes=static_cast<unsigned char*>(unit);
    return GameAimeErrorState::waiting(bytes[0x10],bytes[0x11],bytes[0x20],bytes[0x21]);
}
static void resetReadError(void* unit){
    // Start does not clear HasError in this Android build. Clear only our own
    // completed warning when the game explicitly starts its next scan.
    void* error=nullptr;int category=0,restore=0;bool hasError=false,hasResult=false;
    if(verifyUnit(unit)){
        const auto* bytes=static_cast<unsigned char*>(unit);hasError=bytes[0x11];hasResult=bytes[0x21];
        memcpy(&error,bytes+0x18,sizeof(error));
        if(verifyError(error))memcpy(&category,static_cast<char*>(error)+0x10,sizeof(category));
    }
    if(injectedError.start(reinterpret_cast<uintptr_t>(unit),reinterpret_cast<uintptr_t>(error),hasError,hasResult,category,restore)){
        memcpy(static_cast<char*>(error)+0x10,&restore,sizeof(restore));
        *(static_cast<unsigned char*>(unit)+0x11)=0;
    }
}
static bool applyReadError(void* unit,int issue){
    if(!unitWaiting(unit))return false; // Preserve an existing game/server result.
    void* error=nullptr;memcpy(&error,static_cast<char*>(unit)+0x18,sizeof(error));
    if(!verifyError(error))return false;
    int previous=0;memcpy(&previous,static_cast<char*>(error)+0x10,sizeof(previous));
    const int category=GameAimeErrorState::READ_WARNING;
    injectedError.committed(reinterpret_cast<uintptr_t>(unit),reinterpret_cast<uintptr_t>(error),previous);
    // Primitive fields on Unity's own thread: no access code/result is made up,
    // no network operation is started, and no managed references are replaced.
    // Original Polling -> Result sees HasError + !HasResult, sets Result.Error,
    // then TryAime opens EntryErrorAimeUnknown for category Warning (1).
    memcpy(static_cast<char*>(error)+0x10,&category,sizeof(category));
    auto* bytes=static_cast<unsigned char*>(unit);bytes[0x11]=1;bytes[0x10]=0;
    pthread_mutex_lock(&lock);readerLed.status(4,nowMs());pthread_mutex_unlock(&lock);
    __android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Physical Aime read failed; original game error flow notified (issue %d)",issue);
    return true;
}
static bool scanOpen(void* self,void*& unit){
    if(!self)return false;
    if(!currentUnitField){
        void* klass=objectClass(self);
        if(!named(klass,"API")||!fieldAt(klass,"isReady",0x20)||(currentUnitField=fieldByName(klass,"currentAimeUnit"))==nullptr){hookStatus=-7;return false;}
    }
    if(!*(static_cast<unsigned char*>(self)+0x20))return false;
    staticGet(currentUnitField,&unit);return unitWaiting(unit);
}
static void update(void* self,const void* method){
    originalUpdate(self,method);
    if(hookStatus.load()!=3)return;
    void* unit=nullptr;bool open=scanOpen(self,unit);char code[21]{};bool deliver=false;int error=0;
    pthread_mutex_lock(&lock);
    if(!open)readerLed.scanEnded(nowMs());
    input.update(open&&hookStatus.load()==3,nowMs());deliver=input.take(code,nowMs());error=input.takeError(nowMs());
    pthread_mutex_unlock(&lock);
    if(deliver){
        // Exact same entry as Kanade's Aime control. Server lookup/registration,
        // confirmation, result handling, and failures remain the game's own flow.
        void* managed=stringNew(code);
        for(char& c:code)c=0;
        if(managed){originalUse(managed,nullptr);__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Physical Aime card delivered to game scan");}
    }
    else if(error)applyReadError(unit,error);
}
static void start(void* self,void* unit,const void* method){
    originalStart(self,unit,method);
    if(hookStatus.load()==3)resetReadError(unit);
    pthread_mutex_lock(&lock);input.start();uint64_t generation=input.generation;readerLed.status(1,nowMs());pthread_mutex_unlock(&lock);
    if(hookStatus.load()==3)__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Game Aime scan started (generation %llu)",static_cast<unsigned long long>(generation));
}
static void use(void* code,const void* method){
    // Whichever source commits first owns this scan: virtual-first cancels the
    // queued physical input, physical-first suppresses a second coroutine.
    pthread_mutex_lock(&lock);bool accept=input.acceptVirtual();pthread_mutex_unlock(&lock);
    if(accept)originalUse(code,method);
}
static void managerExecute(void* self,const void* method){
    int before=-1,command=-1;bool adapterError=false;
    if(self&&hookStatus.load()==3){
        if(!managerLayoutReady){
            void* klass=objectClass(self);
            if(!klass||strcmp(className(klass),"AimeReaderManager")||strcmp(classNamespace(klass),"Manager")||!fieldAt(klass,"currentState",0x6c)||!fieldAt(klass,"_unit",0x18))hookStatus=-7;
            else{managerLayoutReady=true;managerResultDiagnosticReady=fieldAt(klass,"_result",0x68)&&fieldAt(klass,"_readFlag",0x10)&&fieldAt(klass,"_enableFlag",0x11);}
        }
        if(managerLayoutReady&&hookStatus.load()==3){
            memcpy(&before,static_cast<char*>(self)+0x6c,sizeof(before));
            // The verified Result-state branch emits Success, Warning or Error
            // to the otherwise inert Android AimeUnit.SetLedStatus method.
            if(before==6){
                void* unit=nullptr;memcpy(&unit,static_cast<char*>(self)+0x18,sizeof(unit));
                if(verifyUnit(unit)){
                    const auto* bytes=static_cast<unsigned char*>(unit);
                    adapterError=bytes[0x11]&&injectedError.unit==reinterpret_cast<uintptr_t>(unit);
                    if(!bytes[0x11])command=2;
                    else{
                        void* error=nullptr;memcpy(&error,bytes+0x18,sizeof(error));
                        if(error){
                            if(verifyError(error)){int category=0;memcpy(&category,static_cast<char*>(error)+0x10,sizeof(category));command=GameAimeLedState::resultStatus(true,category);}
                        }
                    }
                }
            }
        }
    }
    originalManagerExecute(self,method);
    if(hookStatus.load()!=3)return;
    if(before==6&&adapterError&&managerResultDiagnosticReady){
        int after=-1,result=-1;memcpy(&after,static_cast<char*>(self)+0x6c,sizeof(after));memcpy(&result,static_cast<char*>(self)+0x68,sizeof(result));
        pthread_mutex_lock(&lock);uint64_t generation=input.generation;pthread_mutex_unlock(&lock);
        if(after==9&&result==4&&generation&&lastErrorResultGeneration!=generation){
            lastErrorResultGeneration=generation;
            __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Game Aime manager processed Result.Error (generation %llu)",static_cast<unsigned long long>(generation));
        }
    }
    if(before==3){int after=-1;memcpy(&after,static_cast<char*>(self)+0x6c,sizeof(after));if(after==4)command=1;}
    if(command>=0){pthread_mutex_lock(&lock);readerLed.status(command,nowMs());pthread_mutex_unlock(&lock);}
}
static bool completedReadFailure(void* self){
    if(!self||hookStatus.load()!=3||!managerLayoutReady||!managerResultDiagnosticReady)return false;
    // TryAime.WaitAdvCheck normally needs a read result before it can reach the
    // original error window. A physical failure has no fabricated read result.
    // Release that gate only after our own warning became Result.Error/Done.
    void* unit=nullptr;memcpy(&unit,static_cast<char*>(self)+0x18,sizeof(unit));
    if(!verifyUnit(unit))return false;
    const auto* bytes=static_cast<unsigned char*>(unit);
    void* error=nullptr;memcpy(&error,bytes+0x18,sizeof(error));
    if(!verifyError(error))return false;
    int category=0,state=-1,result=-1;
    memcpy(&category,static_cast<char*>(error)+0x10,sizeof(category));
    memcpy(&state,static_cast<char*>(self)+0x6c,sizeof(state));
    memcpy(&result,static_cast<char*>(self)+0x68,sizeof(result));
    return injectedError.completedReadFailure(reinterpret_cast<uintptr_t>(unit),reinterpret_cast<uintptr_t>(error),bytes[0x10],bytes[0x11],bytes[0x20],bytes[0x21],category,state,result);
}
static bool advCheck(void* self,const void* method){
    if(originalAdvCheck(self,method))return true;
    if(!completedReadFailure(self))return false;
    pthread_mutex_lock(&lock);uint64_t generation=input.generation;pthread_mutex_unlock(&lock);
    if(generation&&lastErrorAdvanceGeneration!=generation){
        lastErrorAdvanceGeneration=generation;
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Physical read error advanced to original entry error flow (generation %llu)",static_cast<unsigned long long>(generation));
    }
    return true;
}
static bool anyRead(void* self,const void* method){
    if(originalAnyRead(self,method))return true;
    if(!completedReadFailure(self))return false;
    const auto* bytes=static_cast<unsigned char*>(self);
    if(!GameAimeErrorState::readEvent(true,bytes[0x10],bytes[0x11]))return false;
    // AdvertiseProcess.IsGotoEntry uses AnyRead rather than AdvCheck. Treat a
    // completed physical failure as a read event, not a successful card, so its
    // own transition reaches TryAime and the original error window. The game's
    // login-disable/transition guards still run; no account result is invented.
    pthread_mutex_lock(&lock);uint64_t generation=input.generation;pthread_mutex_unlock(&lock);
    if(generation&&lastErrorReadGeneration!=generation){
        lastErrorReadGeneration=generation;
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Physical read error exposed as entry read event (generation %llu)",static_cast<unsigned long long>(generation));
    }
    return true;
}
static void install(uintptr_t base,void* library){
    if(hookStatus.load()!=0)return;
    const uintptr_t errorOffsets[]={targetBuild->RVA_AIME_ERROR_POLL,targetBuild->RVA_AIME_ERROR_RESULT,targetBuild->RVA_AIME_ERROR_WINDOW,targetBuild->RVA_AIME_UNIT_START};
    const uint64_t errorSignatures[]={targetBuild->SIG_AIME_ERROR_POLL,targetBuild->SIG_AIME_ERROR_RESULT,targetBuild->SIG_AIME_ERROR_WINDOW,targetBuild->SIG_AIME_UNIT_START};
    for(unsigned i=0;i<4;i++)if(!matchesTarget(reinterpret_cast<void*>(base+errorOffsets[i]),errorSignatures[i])){hookStatus=-4;return;}
    const uintptr_t offsets[]={targetBuild->RVA_AIME_UPDATE,targetBuild->RVA_AIME_START,targetBuild->RVA_AIME_USE,targetBuild->RVA_AIME_MANAGER_EXECUTE,targetBuild->RVA_AIME_ADVCHECK,targetBuild->RVA_AIME_ANYREAD};
    const uint64_t signatures[]={targetBuild->SIG_AIME_UPDATE,targetBuild->SIG_AIME_START,targetBuild->SIG_AIME_USE,targetBuild->SIG_AIME_MANAGER_EXECUTE,targetBuild->SIG_AIME_ADVCHECK,targetBuild->SIG_AIME_ANYREAD};
    for(unsigned i=0;i<6;i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){hookStatus=-4;return;}
#define AIME_SYMBOL(variable,name) variable=reinterpret_cast<decltype(variable)>(dlsym(library,name));{Dl_info info{};if(!variable||!dladdr(reinterpret_cast<void*>(variable),&info)||reinterpret_cast<uintptr_t>(info.dli_fbase)!=base){hookStatus=-6;return;}}
    AIME_SYMBOL(stringNew,"il2cpp_string_new")
    AIME_SYMBOL(objectClass,"il2cpp_object_get_class")
    AIME_SYMBOL(className,"il2cpp_class_get_name")
    AIME_SYMBOL(classNamespace,"il2cpp_class_get_namespace")
    AIME_SYMBOL(fieldByName,"il2cpp_class_get_field_from_name")
    AIME_SYMBOL(fieldOffset,"il2cpp_field_get_offset")
    AIME_SYMBOL(staticGet,"il2cpp_field_static_get_value")
#undef AIME_SYMBOL
    void* hooks[]={reinterpret_cast<void*>(update),reinterpret_cast<void*>(start),reinterpret_cast<void*>(use),reinterpret_cast<void*>(managerExecute),reinterpret_cast<void*>(advCheck),reinterpret_cast<void*>(anyRead)};
    void** backups[]={reinterpret_cast<void**>(&originalUpdate),reinterpret_cast<void**>(&originalStart),reinterpret_cast<void**>(&originalUse),reinterpret_cast<void**>(&originalManagerExecute),reinterpret_cast<void**>(&originalAdvCheck),reinterpret_cast<void**>(&originalAnyRead)};
    unsigned count=0;for(;count<6;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),hooks[count],backups[count])!=0)break;
    if(count==6){hookStatus=3;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; 6 physical Aime scan/error/reader LED hooks installed");}
    else{for(int i=int(count)-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));hookStatus=-5;}
}
}
