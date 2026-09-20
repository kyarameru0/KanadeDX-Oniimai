#pragma once
#include "game_ui_state.h"

namespace GameUi {
static std::atomic<bool> externalActive{false};
static std::atomic<int> hookStatus{0};
static uintptr_t imageBase;
static void (*originalSettingsCctor)(const void*);
static void (*originalSettingsLoad)(void*,const void*);
static void (*originalControlUpdate)(void*,const void*);
static void* (*stringNew)(const char*);
static void* (*fieldByName)(void*,const char*);
static void (*setStaticField)(void*,void*);
static void* (*resolveIcall)(const char*);
static uint32_t (*newHandle)(void*,bool);
static void* (*handleTarget)(uint32_t);
static void (*freeHandle)(uint32_t);
static int (*prefsGetInt)(void*,int,const void*);
static void (*prefsSetInt)(void*,int,const void*);
static void (*prefsSave)(const void*);
static float (*getAlpha)(void*,const void*);
static void (*setAlpha)(void*,float,const void*);
static bool (*getInteractable)(void*,const void*);
static bool (*getRaycasts)(void*,const void*);
static void (*setRaycasts)(void*,bool,const void*);
static bool (*objectAlive)(void*,const void*);
static void (*setInteractable)(void*,bool);
static bool triedInteractable=false;
static GameUiState::TemporaryHide temporary;

struct Preferences {
    int getInt(const char* key,int fallback){return prefsGetInt(stringNew(key),fallback,nullptr);}
    void setInt(const char* key,int value){prefsSetInt(stringNew(key),value,nullptr);}
    void save(){prefsSave(nullptr);}
};
struct Objects {
    bool alive(void* value){return value&&objectAlive(value,nullptr);}
    uint32_t hold(void* value){return newHandle(value,false);}
    void* resolve(uint32_t handle){return handleTarget(handle);}
    void release(uint32_t handle){freeHandle(handle);}
    GameUiState::Visibility read(void* value){return {getAlpha(value,nullptr),getInteractable(value,nullptr),getRaycasts(value,nullptr)};}
    void write(void* value,GameUiState::Visibility state){
        // Unchanged setters can dirty Unity's UI hierarchy every frame.
        if(getAlpha(value,nullptr)!=state.alpha)setAlpha(value,state.alpha,nullptr);
        if(getInteractable(value,nullptr)!=state.interactable)setInteractable(value,state.interactable);
        if(getRaycasts(value,nullptr)!=state.raycasts)setRaycasts(value,state.raycasts,nullptr);
    }
};

static void settingsCctor(const void* method){
    originalSettingsCctor(method);
    if(hookStatus.load()!=7)return;
    // The original .cctor initialized this verified TypeInfo slot. Use IL2CPP's
    // field API rather than assuming the runtime's static-field memory layout.
    void* klass=nullptr;memcpy(&klass,reinterpret_cast<void*>(imageBase+targetBuild->DATA_UI_SETTINGS_TYPEINFO),sizeof(klass));
    if(klass){void* field=fieldByName(klass,"compactMode");if(field){bool disabled=false;setStaticField(field,&disabled);}}
}
static void settingsLoad(void* self,const void* method){
    // Called by the game's main thread after its custom JSON preferences load.
    // The marker travels with game data, independently of Android panel prefs.
    Preferences prefs;
    if(hookStatus.load()==7&&GameUiState::migrateCompact(prefs))__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Compact mode OFF migration saved; future user choices are preserved");
    originalSettingsLoad(self,method);
}
static void controlUpdate(void* self,const void* method){
    originalControlUpdate(self,method);
    if(hookStatus.load()!=7)return;
    // Unity objects are touched only here, never from Java/UI/JNI callbacks.
    if(!triedInteractable){
        triedInteractable=true;
        setInteractable=reinterpret_cast<decltype(setInteractable)>(resolveIcall("UnityEngine.CanvasGroup::set_interactable"));
        if(!setInteractable)__android_log_print(ANDROID_LOG_ERROR,"OniimaiKanade","External UI hide unavailable: CanvasGroup interactable icall missing");
    }
    if(!setInteractable)return;
    void* group=nullptr;if(self)memcpy(&group,static_cast<char*>(self)+targetBuild->FIELD_UI_MAIN_GROUP,sizeof(group));
    uint32_t previous=temporary.handle;
    Objects objects;temporary.update(objects,group,externalActive.load());
    if((previous==0)!=(temporary.handle==0))__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Game control UI %s",temporary.handle?"hidden for external output (raycasts/interactable OFF)":"restored on phone");
}

static void install(uintptr_t base,void* library){
    if(hookStatus.load()!=0)return;
    const uintptr_t offsets[]={targetBuild->RVA_UI_SETTINGS_CCTOR,targetBuild->RVA_UI_SETTINGS_LOAD,targetBuild->RVA_UI_CONTROL_UPDATE,targetBuild->RVA_UI_PREFS_GETINT,targetBuild->RVA_UI_PREFS_SETINT,targetBuild->RVA_UI_PREFS_SAVE,targetBuild->RVA_UI_GROUP_ALPHA_GET,targetBuild->RVA_UI_GROUP_ALPHA_SET,targetBuild->RVA_UI_GROUP_INTERACTABLE_GET,targetBuild->RVA_UI_GROUP_RAYCAST_GET,targetBuild->RVA_UI_GROUP_RAYCAST_SET,targetBuild->RVA_UI_OBJECT_ALIVE};
    const uint64_t signatures[]={targetBuild->SIG_UI_SETTINGS_CCTOR,targetBuild->SIG_UI_SETTINGS_LOAD,targetBuild->SIG_UI_CONTROL_UPDATE,targetBuild->SIG_UI_PREFS_GETINT,targetBuild->SIG_UI_PREFS_SETINT,targetBuild->SIG_UI_PREFS_SAVE,targetBuild->SIG_UI_GROUP_ALPHA_GET,targetBuild->SIG_UI_GROUP_ALPHA_SET,targetBuild->SIG_UI_GROUP_INTERACTABLE_GET,targetBuild->SIG_UI_GROUP_RAYCAST_GET,targetBuild->SIG_UI_GROUP_RAYCAST_SET,targetBuild->SIG_UI_OBJECT_ALIVE};
    for(unsigned i=0;i<sizeof(offsets)/sizeof(offsets[0]);i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){hookStatus=-4;return;}
#define UI_SYMBOL(variable,name) variable=reinterpret_cast<decltype(variable)>(dlsym(library,name));if(!variable){hookStatus=-6;return;}
    UI_SYMBOL(stringNew,"il2cpp_string_new")
    UI_SYMBOL(fieldByName,"il2cpp_class_get_field_from_name")
    UI_SYMBOL(setStaticField,"il2cpp_field_static_set_value")
    UI_SYMBOL(resolveIcall,"il2cpp_resolve_icall")
    UI_SYMBOL(newHandle,"il2cpp_gchandle_new")
    UI_SYMBOL(handleTarget,"il2cpp_gchandle_get_target")
    UI_SYMBOL(freeHandle,"il2cpp_gchandle_free")
#undef UI_SYMBOL
#define UI_ADDRESS(variable,rva) variable=reinterpret_cast<decltype(variable)>(base+rva)
    UI_ADDRESS(prefsGetInt,targetBuild->RVA_UI_PREFS_GETINT);UI_ADDRESS(prefsSetInt,targetBuild->RVA_UI_PREFS_SETINT);UI_ADDRESS(prefsSave,targetBuild->RVA_UI_PREFS_SAVE);
    UI_ADDRESS(getAlpha,targetBuild->RVA_UI_GROUP_ALPHA_GET);UI_ADDRESS(setAlpha,targetBuild->RVA_UI_GROUP_ALPHA_SET);UI_ADDRESS(getInteractable,targetBuild->RVA_UI_GROUP_INTERACTABLE_GET);
    UI_ADDRESS(getRaycasts,targetBuild->RVA_UI_GROUP_RAYCAST_GET);UI_ADDRESS(setRaycasts,targetBuild->RVA_UI_GROUP_RAYCAST_SET);UI_ADDRESS(objectAlive,targetBuild->RVA_UI_OBJECT_ALIVE);
#undef UI_ADDRESS
    imageBase=base;
    void* hooks[]={reinterpret_cast<void*>(settingsCctor),reinterpret_cast<void*>(settingsLoad),reinterpret_cast<void*>(controlUpdate)};
    void** backups[]={reinterpret_cast<void**>(&originalSettingsCctor),reinterpret_cast<void**>(&originalSettingsLoad),reinterpret_cast<void**>(&originalControlUpdate)};
    unsigned count=0;for(;count<3;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),hooks[count],backups[count])!=0)break;
    if(count==3){hookStatus=7;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; compact defaults and external control UI hooks installed");}
    else{for(int i=int(count)-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));hookStatus=-5;}
}
}
