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
static bool (*interactable)(void*,const void*);
static void (*press)(void*,const void*);

static void started(void* self,const void* method){
    pthread_mutex_lock(&stateLock);gate.started();state.reset();pthread_mutex_unlock(&stateLock);
    originalStarted(self,method);
}
static void update(void* self,const void* method){
    originalUpdate(self,method);
    pthread_mutex_lock(&stateLock);bool requested=gate.pending&&!gate.completed;pthread_mutex_unlock(&stateLock);
    if(!requested)return;
    // The exact same Unity UI Button.Press path used by the visible character.
    // No Unity objects are retained across frames or accessed on the USB thread.
    void* canvas=getCanvas(nullptr);void* button=nullptr;void* callback=nullptr;
    if(canvas&&alive(canvas,nullptr)){
        memcpy(&button,static_cast<char*>(canvas)+FIELD_BOOT_START_BUTTON,sizeof(button));
        memcpy(&callback,static_cast<char*>(canvas)+FIELD_BOOT_START_CALLBACK,sizeof(callback));
    }
    bool ready=callback&&button&&alive(button,nullptr)&&isActive(button,nullptr)&&interactable(button,nullptr);
    pthread_mutex_lock(&stateLock);bool fire=gate.take(ready,nowMs());pthread_mutex_unlock(&stateLock);
    if(fire){press(button,nullptr);__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Controller pressed the KanadeDX startup button");}
}
static void install(uintptr_t base){
    if(hookStatus.load()!=0)return;
    const uintptr_t offsets[]={RVA_BOOT_UPDATE,RVA_BOOT_STARTED,RVA_BOOT_CANVAS,RVA_BOOT_BUTTON_PRESS,RVA_BOOT_ACTIVE,RVA_BOOT_INTERACTABLE,RVA_UI_OBJECT_ALIVE};
    const uint64_t signatures[]={SIG_BOOT_UPDATE,SIG_BOOT_STARTED,SIG_BOOT_CANVAS,SIG_BOOT_BUTTON_PRESS,SIG_BOOT_ACTIVE,SIG_BOOT_INTERACTABLE,SIG_UI_OBJECT_ALIVE};
    for(unsigned i=0;i<sizeof(offsets)/sizeof(offsets[0]);i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){hookStatus=-4;return;}
    getCanvas=reinterpret_cast<decltype(getCanvas)>(base+RVA_BOOT_CANVAS);
    alive=reinterpret_cast<decltype(alive)>(base+RVA_UI_OBJECT_ALIVE);
    isActive=reinterpret_cast<decltype(isActive)>(base+RVA_BOOT_ACTIVE);
    interactable=reinterpret_cast<decltype(interactable)>(base+RVA_BOOT_INTERACTABLE);
    press=reinterpret_cast<decltype(press)>(base+RVA_BOOT_BUTTON_PRESS);
    if(hookFunction(reinterpret_cast<void*>(base+RVA_BOOT_STARTED),reinterpret_cast<void*>(started),reinterpret_cast<void**>(&originalStarted))!=0){hookStatus=-5;return;}
    if(hookFunction(reinterpret_cast<void*>(base+RVA_BOOT_UPDATE),reinterpret_cast<void*>(update),reinterpret_cast<void**>(&originalUpdate))!=0){unhookFunction(reinterpret_cast<void*>(base+RVA_BOOT_STARTED));hookStatus=-5;return;}
    hookStatus=3;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; controller startup-button hooks installed");
}
}
