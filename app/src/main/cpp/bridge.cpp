#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <dlfcn.h>
#include <elf.h>
#include <pthread.h>
#include <time.h>
#include <atomic>
#include <android/log.h>
#include "input_state.h"
#include "led_state.h"
#include "target_build.h"

// Assigned under installLock before publishing hooks; never switched afterward.
static const TargetBuild* targetBuild=nullptr;

using HookFun = int (*)(void*,void*,void**);
using UnhookFun = int (*)(void*);
using LibraryCallback = void (*)(const char*,void*);
struct NativeAPIEntries { uint32_t version; HookFun hook_func; UnhookFun unhook_func; };
static HookFun hookFunction;
static UnhookFun unhookFunction;
static std::atomic<int> status{-1};
static std::atomic<uint64_t> frames{0},touchReads{0};
static pthread_mutex_t stateLock=PTHREAD_MUTEX_INITIALIZER, installLock=PTHREAD_MUTEX_INITIALIZER;
static InputState state;
static LedState leds;
static pthread_mutex_t ledLock=PTHREAD_MUTEX_INITIALIZER;
static std::atomic<int> ledStatus{0};
static uint64_t nowMs();
static void (*originalLedColor)(void*,int,uint32_t,const void*);
static void (*originalLedPress)(void*,uint8_t,LedColor,const void*);
static void (*originalLedAll)(void*,LedColor,int,const void*);
static void (*originalLedOff)(void*,const void*);
static void (*originalLedFade)(void*,void*,const void*);
static void (*originalLedRing)(void*,uint32_t,const void*);
static void (*originalLedFet)(void*,uint8_t,uint8_t,const void*);
static void (*originalLedRingFade)(void*,void*,const void*);
static bool firstPlayer(void* self){return self&&*(static_cast<unsigned char*>(self)+0x10)==1;}
static int readFade(void* list,LedFadeStep* result){
    if(!list)return 0;
    int count=0;void* array=nullptr;uintptr_t capacity=0;
    memcpy(&count,static_cast<char*>(list)+0x18,sizeof(count));
    memcpy(&array,static_cast<char*>(list)+0x10,sizeof(array));
    if(!array||count<=0||count>LedTrack::MAX_STEPS)return 0;
    memcpy(&capacity,static_cast<char*>(array)+0x18,sizeof(capacity));
    if(capacity<uintptr_t(count)||capacity>4096)return 0;
    memcpy(result,static_cast<char*>(array)+0x20,size_t(count)*sizeof(LedFadeStep));return count;
}
// Capture BEFORE the original facade's CanDisplay/VirtualKeyboard early return.
// Do not change the game's GUI setting or call any UI color/fade methods ourselves.
static void ledColorHook(void* self,int index,uint32_t color,const void* method){
    if(firstPlayer(self)&&index>=0&&index<8){pthread_mutex_lock(&ledLock);leds.set(index,LedTrack::unpack(color));pthread_mutex_unlock(&ledLock);}
    originalLedColor(self,index,color,method);
}
static void ledPressHook(void* self,uint8_t index,LedColor color,const void* method){
    if(firstPlayer(self)){pthread_mutex_lock(&ledLock);leds.pressed(index,color,nowMs());pthread_mutex_unlock(&ledLock);}
    originalLedPress(self,index,color,method);
}
static void ledAllHook(void* self,LedColor color,int speed,const void* method){
    if(firstPlayer(self)){pthread_mutex_lock(&ledLock);leds.all(color);pthread_mutex_unlock(&ledLock);}
    originalLedAll(self,color,speed,method);
}
static void ledOffHook(void* self,const void* method){
    if(firstPlayer(self)){pthread_mutex_lock(&ledLock);leds.buttonsOff();pthread_mutex_unlock(&ledLock);}
    originalLedOff(self,method);
}
static void captureFade(void* self,void* list,bool ring){
    if(!firstPlayer(self))return;
    LedFadeStep steps[LedTrack::MAX_STEPS];int count=readFade(list,steps);if(!count)return;
    pthread_mutex_lock(&ledLock);leds.fade(steps,count,ring,nowMs());pthread_mutex_unlock(&ledLock);
}
static void ledFadeHook(void* self,void* list,const void* method){captureFade(self,list,false);originalLedFade(self,list,method);}
static void ledRingFadeHook(void* self,void* list,const void* method){captureFade(self,list,true);originalLedRingFade(self,list,method);}
static void ledRingHook(void* self,uint32_t color,const void* method){
    if(firstPlayer(self)){pthread_mutex_lock(&ledLock);leds.allFet(color);pthread_mutex_unlock(&ledLock);}
    originalLedRing(self,color,method);
}
static void ledFetHook(void* self,uint8_t index,uint8_t value,const void* method){
    if(firstPlayer(self)&&index<LedState::BOARD_COUNT){
        pthread_mutex_lock(&ledLock);leds.setFet(index,value);pthread_mutex_unlock(&ledLock);
    }
    originalLedFet(self,index,value,method);
}
static void installLeds(uintptr_t base){
    if(ledStatus.load()!=0)return;
    const uintptr_t offsets[]={targetBuild->RVA_LED_COLOR,targetBuild->RVA_LED_PRESS,targetBuild->RVA_LED_ALL,targetBuild->RVA_LED_OFF,targetBuild->RVA_LED_FADE,targetBuild->RVA_LED_RING,targetBuild->RVA_LED_FET,targetBuild->RVA_LED_RING_FADE};
    const uint64_t signatures[]={targetBuild->SIG_LED_COLOR,targetBuild->SIG_LED_PRESS,targetBuild->SIG_LED_ALL,targetBuild->SIG_LED_OFF,targetBuild->SIG_LED_FADE,targetBuild->SIG_LED_RING,targetBuild->SIG_LED_FET,targetBuild->SIG_LED_RING_FADE};
    void* hooks[]={reinterpret_cast<void*>(ledColorHook),reinterpret_cast<void*>(ledPressHook),reinterpret_cast<void*>(ledAllHook),reinterpret_cast<void*>(ledOffHook),reinterpret_cast<void*>(ledFadeHook),reinterpret_cast<void*>(ledRingHook),reinterpret_cast<void*>(ledFetHook),reinterpret_cast<void*>(ledRingFadeHook)};
    void** originals[]={reinterpret_cast<void**>(&originalLedColor),reinterpret_cast<void**>(&originalLedPress),reinterpret_cast<void**>(&originalLedAll),reinterpret_cast<void**>(&originalLedOff),reinterpret_cast<void**>(&originalLedFade),reinterpret_cast<void**>(&originalLedRing),reinterpret_cast<void**>(&originalLedFet),reinterpret_cast<void**>(&originalLedRingFade)};
    for(int i=0;i<8;i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){ledStatus=-4;return;}
    int count=0;
    for(;count<8;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),hooks[count],originals[count])!=0)break;
    if(count==8){ledStatus=255;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; 8 direct LED hooks installed (no Virtual Keyboard dependency)");}
    else{for(int i=count-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));ledStatus=-5;}
}
static void (*originalTouch)(void*,int,uint64_t,bool,const void*);
static void (*originalFrame)(const void*);
static bool (*originalRaw)(void*,int,const void*),(*originalDown)(void*,int,const void*);
static uint64_t nowMs(){timespec t{};clock_gettime(CLOCK_MONOTONIC,&t);return uint64_t(t.tv_sec)*1000+t.tv_nsec/1000000;}
#include "gameplay_stats.h"
#include "boot_input.h"
#include "game_ui.h"
#include "game_aime.h"
#include "game_ceiling.h"
static void touchHook(void* self,int player,uint64_t real,bool updated,const void* method){
    touchReads++;
    pthread_mutex_lock(&stateLock);
    uint64_t added=state.takeTouch(player,nowMs());
    updated=state.touchUpdate(player,updated);
    pthread_mutex_unlock(&stateLock);
    // Feed the existing 3-frame input log, including zero frames when input is disarmed.
    originalTouch(self,player,real|added,updated,method);
}
static void frameHook(const void* method){
    pthread_mutex_lock(&stateLock);state.frame(nowMs());pthread_mutex_unlock(&stateLock);frames++;
    originalFrame(method);
}
static bool rawHook(void* self,int id,const void* method){
    bool real=originalRaw(self,id,method);
    pthread_mutex_lock(&stateLock);bool added=state.button(id,false,nowMs());pthread_mutex_unlock(&stateLock);
    return real||added;
}
static bool downHook(void* self,int id,const void* method){
    bool real=originalDown(self,id,method);
    pthread_mutex_lock(&stateLock);bool added=state.button(id,true,nowMs());pthread_mutex_unlock(&stateLock);
    return real||added;
}
static const TargetBuild* identity(uintptr_t base){
    const auto* e=reinterpret_cast<const Elf64_Ehdr*>(base);
    if(memcmp(e->e_ident,ELFMAG,SELFMAG)||e->e_machine!=EM_AARCH64||e->e_phnum>128)return nullptr;
    const auto* ph=reinterpret_cast<const Elf64_Phdr*>(base+e->e_phoff);
    for(int i=0;i<e->e_phnum;i++)if(ph[i].p_type==PT_NOTE){
        const unsigned char* p=reinterpret_cast<const unsigned char*>(base+ph[i].p_vaddr);
        const unsigned char* end=p+ph[i].p_memsz;
        while(end-p>=12){
            Elf64_Nhdr h;memcpy(&h,p,12);p+=12;
            if(h.n_namesz>1024||h.n_descsz>4096)return nullptr;
            size_t ns=(h.n_namesz+3)&~3u,ds=(h.n_descsz+3)&~3u;
            if(size_t(end-p)<ns+ds)return nullptr;
            if(h.n_type==NT_GNU_BUILD_ID&&h.n_namesz==4&&!memcmp(p,"GNU",4))
                return targetByBuildId(p+ns,h.n_descsz);
            p+=ns+ds;
        }
    }
    return nullptr;
}
static void install(void* handle){
    if(!hookFunction||!unhookFunction||!handle)return;
    pthread_mutex_lock(&installLock);
    if(status.load()==15){pthread_mutex_unlock(&installLock);return;}
    Dl_info info{};void* symbol=dlsym(handle,"il2cpp_init");
    if(!symbol||!dladdr(symbol,&info)||!info.dli_fbase){status=-2;pthread_mutex_unlock(&installLock);return;}
    uintptr_t base=reinterpret_cast<uintptr_t>(info.dli_fbase);
    const auto* profile=identity(base);
    if(!profile||(targetBuild&&targetBuild!=profile)){status=-3;pthread_mutex_unlock(&installLock);return;}
    targetBuild=profile;
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Selected verified profile: %s",profile->name);
    const uintptr_t offsets[]={targetBuild->RVA_TOUCH,targetBuild->RVA_RAW,targetBuild->RVA_DOWN,targetBuild->RVA_FRAME};
    const uint64_t signatures[]={targetBuild->SIG_TOUCH,targetBuild->SIG_RAW,targetBuild->SIG_DOWN,targetBuild->SIG_FRAME};
    void* replacements[]={reinterpret_cast<void*>(touchHook),reinterpret_cast<void*>(rawHook),reinterpret_cast<void*>(downHook),reinterpret_cast<void*>(frameHook)};
    void** backups[]={reinterpret_cast<void**>(&originalTouch),reinterpret_cast<void**>(&originalRaw),reinterpret_cast<void**>(&originalDown),reinterpret_cast<void**>(&originalFrame)};
    for(int i=0;i<4;i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){
        status=-4;pthread_mutex_unlock(&installLock);return;
    }
    int count=0;
    for(;count<4;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),replacements[count],backups[count])!=0)break;
    if(count==4){status=15;installLeds(base);GameCeiling::install(base);GameplayStats::install(base,handle);GameUi::install(base,handle);BootInput::install(base);GameAime::install(base,handle);__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified build; 4 input hooks installed");}
    else{
        // Leave any hook whose rollback fails inert, and never arm this generation.
        for(int i=count-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));
        status=-5;
    }
    pthread_mutex_unlock(&installLock);
}
static void libraryLoaded(const char* name,void* handle){
    if(name){const char* file=strrchr(name,'/');file=file?file+1:name;if(!strcmp(file,"libil2cpp.so"))install(handle);}
}
extern "C" __attribute__((visibility("default"),used))
LibraryCallback native_init(const NativeAPIEntries* entries){
    if(entries){hookFunction=entries->hook_func;unhookFunction=entries->unhook_func;status=-2;}
    return libraryLoaded;
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*){return JNI_VERSION_1_6;}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_externalUiHidden(JNIEnv*,jclass,jboolean active){
    GameUi::externalActive.store(active);
}
extern "C" JNIEXPORT jint JNICALL Java_io_oniimai_kanade_NativeBridge_initialize(JNIEnv*,jclass){
    if(status.load()==-2){void* h=dlopen("libil2cpp.so",RTLD_NOW|RTLD_NOLOAD);if(h){install(h);dlclose(h);}}
    return status.load();
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_submit(JNIEnv*,jclass,jlong touch,jint buttons,jint player,jboolean active){
    pthread_mutex_lock(&stateLock);
    uint64_t now=nowMs();bool enabled=active&&status.load()==15;
    bool forward=BootInput::gate.submit(uint64_t(touch),uint32_t(buttons),enabled,now);
    state.submit(uint64_t(touch),uint32_t(buttons),player==1?1:0,enabled&&forward,now);
    pthread_mutex_unlock(&stateLock);
}
extern "C" JNIEXPORT jlongArray JNICALL Java_io_oniimai_kanade_NativeBridge_stats(JNIEnv* env,jclass){
    jlong data[]={status.load(),jlong(frames.load()),jlong(touchReads.load())};
    jlongArray result=env->NewLongArray(3);if(result)env->SetLongArrayRegion(result,0,3,data);return result;
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_aimeEnabled(JNIEnv*,jclass,jboolean enabled){
    pthread_mutex_lock(&GameAime::lock);GameAime::input.enable(enabled&&GameAime::hookStatus.load()==3);pthread_mutex_unlock(&GameAime::lock);
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_clearAime(JNIEnv*,jclass){
    pthread_mutex_lock(&GameAime::lock);GameAime::input.clear();pthread_mutex_unlock(&GameAime::lock);
}
extern "C" JNIEXPORT jint JNICALL Java_io_oniimai_kanade_NativeBridge_aimeStatus(JNIEnv*,jclass){
    int install=GameAime::hookStatus.load();if(install<0)return install;if(install!=3)return 0;
    pthread_mutex_lock(&GameAime::lock);int result=GameAime::input.status(nowMs());pthread_mutex_unlock(&GameAime::lock);return result;
}
extern "C" JNIEXPORT jlong JNICALL Java_io_oniimai_kanade_NativeBridge_aimeGeneration(JNIEnv*,jclass){
    if(GameAime::hookStatus.load()!=3)return -1;
    pthread_mutex_lock(&GameAime::lock);jlong generation=jlong(GameAime::input.generation);pthread_mutex_unlock(&GameAime::lock);return generation;
}
extern "C" JNIEXPORT jint JNICALL Java_io_oniimai_kanade_NativeBridge_aimeLed(JNIEnv*,jclass){
    if(GameAime::hookStatus.load()!=3)return -1;
    pthread_mutex_lock(&GameAime::lock);int rgb=GameAime::readerLed.sample(nowMs());pthread_mutex_unlock(&GameAime::lock);return rgb;
}
extern "C" JNIEXPORT jboolean JNICALL Java_io_oniimai_kanade_NativeBridge_submitAime(JNIEnv* env,jclass,jbyteArray code,jlong generation){
    if(GameAime::hookStatus.load()!=3||generation<=0||!code||env->GetArrayLength(code)!=10)return false;
    unsigned char bytes[10]{};env->GetByteArrayRegion(code,0,10,reinterpret_cast<jbyte*>(bytes));if(env->ExceptionCheck())return false;
    pthread_mutex_lock(&GameAime::lock);bool accepted=GameAime::input.submitForScan(bytes,10,uint64_t(generation),nowMs());pthread_mutex_unlock(&GameAime::lock);
    for(auto& value:bytes)value=0;return accepted;
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_aimeError(JNIEnv*,jclass,jint issue,jlong generation){
    if(GameAime::hookStatus.load()!=3||generation<=0)return;
    pthread_mutex_lock(&GameAime::lock);GameAime::input.reportError(issue,uint64_t(generation),nowMs());pthread_mutex_unlock(&GameAime::lock);
}
extern "C" JNIEXPORT jstring JNICALL Java_io_oniimai_kanade_NativeBridge_gameplayStats(JNIEnv* env,jclass){
    const std::string value=GameplayStats::json();return env->NewStringUTF(value.c_str());
}
extern "C" JNIEXPORT jintArray JNICALL Java_io_oniimai_kanade_NativeBridge_ledSnapshot(JNIEnv* env,jclass){
    jint data[3+LedState::COUNT]{};data[0]=ledStatus.load();
    pthread_mutex_lock(&ledLock);leds.sample(nowMs());data[1]=jint(leds.events);data[2]=jint(leds.seen);
    for(int i=0;i<LedState::COUNT;i++)data[3+i]=jint(leds.colors[i]);pthread_mutex_unlock(&ledLock);
    if(GameCeiling::installStatus.load()<0)data[3+LedState::BILLBOARD]=-1;
    jintArray result=env->NewIntArray(3+LedState::COUNT);if(result)env->SetIntArrayRegion(result,0,3+LedState::COUNT,data);return result;
}
