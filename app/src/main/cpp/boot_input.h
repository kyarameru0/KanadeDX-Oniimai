#pragma once
#include "boot_input_state.h"

namespace BootInput {
static BootInputState gate;
static std::atomic<int> hookStatus{0};
static void (*originalUpdate)(void*,const void*);
static void (*originalStarted)(void*,const void*);
static void* (*getCanvas)(const void*);
static bool (*alive)(void*,const void*);
static bool (*isActive)(void*,const void*);
static bool (*behaviourActive)(void*,const void*);
static bool (*interactable)(void*,const void*);
static void (*press)(void*,const void*);

static void started(void* self,const void* method){
    pthread_mutex_lock(&stateLock);gate.started();state.reset();displayFrames.starting();pthread_mutex_unlock(&stateLock);
    originalStarted(self,method);
}
static void update(void* self,const void* method){
    originalUpdate(self,method);
    pthread_mutex_lock(&stateLock);bool completed=gate.completed;pthread_mutex_unlock(&stateLock);
    if(completed)return;
    // The exact same Unity UI Button.Press path used by the visible character.
    // No Unity objects are retained across frames or accessed on the USB thread.
    void* canvas=getCanvas(nullptr);void* button=nullptr;void* callback=nullptr;
    void* mainCanvas=nullptr;void* processingUi=nullptr;
    if(canvas&&alive(canvas,nullptr)){
        memcpy(&button,static_cast<char*>(canvas)+targetBuild->FIELD_BOOT_START_BUTTON,sizeof(button));
        memcpy(&callback,static_cast<char*>(canvas)+targetBuild->FIELD_BOOT_START_CALLBACK,sizeof(callback));
        memcpy(&mainCanvas,static_cast<char*>(canvas)+targetBuild->FIELD_BOOT_MAIN_CANVAS,sizeof(mainCanvas));
        memcpy(&processingUi,static_cast<char*>(canvas)+targetBuild->FIELD_BOOT_PROCESSING_UI,sizeof(processingUi));
    }
    bool mainVisible=mainCanvas&&alive(mainCanvas,nullptr)&&behaviourActive(mainCanvas,nullptr);
    bool processingVisible=processingUi&&alive(processingUi,nullptr)&&behaviourActive(processingUi,nullptr);
    bool ready=mainVisible&&!processingVisible&&callback&&button&&alive(button,nullptr)&&isActive(button,nullptr)&&interactable(button,nullptr);
    // EventSystem.Update runs before the game's input loop exists. Only an
    // active, interactable startup button with its callback is a ready screen.
    // Download/conversion/cache UI can cover that button: exclude it explicitly,
    // for both controller Start and first-run setup, even when the callback exists.
    // Reading it never presses it: a fresh controller edge is still required.
    pthread_mutex_lock(&stateLock);displayFrames.startup(ready);bool fire=gate.take(ready,nowMs());pthread_mutex_unlock(&stateLock);
    if(fire){press(button,nullptr);__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Controller pressed the KanadeDX startup button");}
}
static void install(uintptr_t base){
    if(hookStatus.load()!=0)return;
    const uintptr_t offsets[]={targetBuild->RVA_BOOT_UPDATE,targetBuild->RVA_BOOT_STARTED,targetBuild->RVA_BOOT_CANVAS,targetBuild->RVA_BOOT_BUTTON_PRESS,targetBuild->RVA_BOOT_ACTIVE,targetBuild->RVA_BOOT_INTERACTABLE,targetBuild->RVA_UI_OBJECT_ALIVE,targetBuild->RVA_BOOT_BEHAVIOUR_ACTIVE};
    const uint64_t signatures[]={targetBuild->SIG_BOOT_UPDATE,targetBuild->SIG_BOOT_STARTED,targetBuild->SIG_BOOT_CANVAS,targetBuild->SIG_BOOT_BUTTON_PRESS,targetBuild->SIG_BOOT_ACTIVE,targetBuild->SIG_BOOT_INTERACTABLE,targetBuild->SIG_UI_OBJECT_ALIVE,targetBuild->SIG_BOOT_BEHAVIOUR_ACTIVE};
    for(unsigned i=0;i<sizeof(offsets)/sizeof(offsets[0]);i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){hookStatus=-4;return;}
    getCanvas=reinterpret_cast<decltype(getCanvas)>(base+targetBuild->RVA_BOOT_CANVAS);
    alive=reinterpret_cast<decltype(alive)>(base+targetBuild->RVA_UI_OBJECT_ALIVE);
    isActive=reinterpret_cast<decltype(isActive)>(base+targetBuild->RVA_BOOT_ACTIVE);
    behaviourActive=reinterpret_cast<decltype(behaviourActive)>(base+targetBuild->RVA_BOOT_BEHAVIOUR_ACTIVE);
    interactable=reinterpret_cast<decltype(interactable)>(base+targetBuild->RVA_BOOT_INTERACTABLE);
    press=reinterpret_cast<decltype(press)>(base+targetBuild->RVA_BOOT_BUTTON_PRESS);
    if(hookFunction(reinterpret_cast<void*>(base+targetBuild->RVA_BOOT_STARTED),reinterpret_cast<void*>(started),reinterpret_cast<void**>(&originalStarted))!=0){hookStatus=-5;return;}
    if(hookFunction(reinterpret_cast<void*>(base+targetBuild->RVA_BOOT_UPDATE),reinterpret_cast<void*>(update),reinterpret_cast<void**>(&originalUpdate))!=0){unhookFunction(reinterpret_cast<void*>(base+targetBuild->RVA_BOOT_STARTED));hookStatus=-5;return;}
    hookStatus=3;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; controller startup-button hooks installed");
}
}
