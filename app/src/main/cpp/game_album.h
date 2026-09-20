#pragma once
#include <vector>
#include <initializer_list>

// Managed/graphics work runs only inside existing Unity main-thread hooks,
// during game preparation, pre-play countdown or result entry, never timed play.
// Android obtains immutable PNG copies by revision and never touches Unity objects.
namespace GameAlbum {
static constexpr int EDGE=256;
static constexpr size_t MAX_PNG=1024*1024;
struct Entry {int music=-1;uint64_t revision=0;std::vector<unsigned char> png;};
static Entry cache[3];
static unsigned slot=0;
static uint64_t sequence=0;
static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static uintptr_t imageBase=0;
static bool targetVerified=false,apiReady=false,initialized=false,available=false;
static uint64_t nextInitialization=0;
static int attemptMusic=-1,attempts=0;
static uint64_t nextAttempt=0;

static void* (*domainGet)();
static const void** (*assemblies)(void*,size_t*);
static void* (*assemblyImage)(const void*);
static const char* (*imageName)(void*);
static void* (*classFromName)(void*,const char*,const char*);
static const void* (*classMethods)(void*,void**);
static const char* (*methodName)(const void*);
static uint32_t (*paramCount)(const void*);
static const void* (*methodParam)(const void*,uint32_t);
static char* (*typeName)(const void*);
static void (*freeMemory)(void*);
static void* (*invoke)(const void*,void*,void**,void**);
static void* (*objectNew)(void*);
static uint32_t (*newHandle)(void*,bool);
static void (*freeHandle)(uint32_t);
static void* textureClass;
static const void *assetInstance,*jacket,*selectedMusic,*tempGet,*tempRelease,*activeGet,*activeSet,
                  *blit,*textureCtor,*readPixels,*encodePng,*destroy;

static bool call(const void* method,void* self,void** arguments,void*& result){
    void* exception=nullptr;result=invoke(method,self,arguments,&exception);
    if(exception){__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album capture: managed %s failed",methodName(method));return false;}
    return true;
}
static void cleanupCall(const void* method,void* object){void* args[]={object};void* ignored=nullptr;call(method,nullptr,args,ignored);}
struct Roots {
    uint32_t handles[8]{};unsigned count=0;
    bool add(void* object){if(!object)return true;if(count==8)return false;uint32_t h=newHandle(object,false);if(!h)return false;handles[count++]=h;return true;}
    ~Roots(){while(count)freeHandle(handles[--count]);}
};
static void* klass(const char* image,const char* space,const char* name){
    size_t count=0;const void** list=assemblies(domainGet(),&count);
    if(!list||count>512){__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata: invalid assembly list (%zu)",count);return nullptr;}
    for(size_t i=0;i<count;i++){void* item=assemblyImage(list[i]);const char* label=item?imageName(item):nullptr;
        if(label&&!strcmp(label,image)){void* result=classFromName(item,space,name);
            if(!result)__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata: missing class %s:%s.%s",image,space,name);return result;}}
    __android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata: image %s not found among %zu",image,count);
    return nullptr;
}
static const void* method(void* type,const char* name,std::initializer_list<const char*> params){
    if(!type)return nullptr;void* iterator=nullptr;const void* candidate;const void* found=nullptr;
    while((candidate=classMethods(type,&iterator))){
        if(strcmp(methodName(candidate),name)||paramCount(candidate)!=params.size())continue;
        bool match=true;unsigned i=0;
        for(const char* expected:params){char* actual=typeName(methodParam(candidate,i++));match&=actual&&!strcmp(actual,expected);if(actual)freeMemory(actual);}
        if(match){if(found)return nullptr;found=candidate;}
    }
    if(!found)__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata: method %s/%zu with exact parameter types missing",name,params.size());
    return found;
}
static bool resolveApis(void* library){
    // The caller verified this loaded image's build ID. Resolve exports while its
    // callback handle is valid; reopening by basename fails across linker namespaces.
    if(!library)return false;
    bool symbols=true;
#define ALBUM_SYMBOL(variable,name) {void* address=dlsym(library,name);Dl_info info{};if(!address||!dladdr(address,&info)||reinterpret_cast<uintptr_t>(info.dli_fbase)!=imageBase){symbols=false;variable=nullptr;__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album API missing or outside verified image: %s",name);}else variable=reinterpret_cast<decltype(variable)>(address);}
    ALBUM_SYMBOL(domainGet,"il2cpp_domain_get") ALBUM_SYMBOL(assemblies,"il2cpp_domain_get_assemblies")
    ALBUM_SYMBOL(assemblyImage,"il2cpp_assembly_get_image") ALBUM_SYMBOL(imageName,"il2cpp_image_get_name")
    ALBUM_SYMBOL(classFromName,"il2cpp_class_from_name") ALBUM_SYMBOL(classMethods,"il2cpp_class_get_methods")
    ALBUM_SYMBOL(methodName,"il2cpp_method_get_name") ALBUM_SYMBOL(paramCount,"il2cpp_method_get_param_count")
    ALBUM_SYMBOL(methodParam,"il2cpp_method_get_param") ALBUM_SYMBOL(typeName,"il2cpp_type_get_name")
    ALBUM_SYMBOL(freeMemory,"il2cpp_free") ALBUM_SYMBOL(invoke,"il2cpp_runtime_invoke")
    ALBUM_SYMBOL(objectNew,"il2cpp_object_new") ALBUM_SYMBOL(newHandle,"il2cpp_gchandle_new") ALBUM_SYMBOL(freeHandle,"il2cpp_gchandle_free")
#undef ALBUM_SYMBOL
    __android_log_print(symbols?ANDROID_LOG_INFO:ANDROID_LOG_WARN,"OniimaiKanade","Album IL2CPP APIs from verified callback image %s",symbols?"ready":"unavailable");
    return symbols;
}
static bool initialize(){
    if(initialized)return available;
    if(!targetVerified||!apiReady)return false;
    uint64_t now=nowMs();if(now<nextInitialization)return false;nextInitialization=now+1000;
    // Export resolution is safe at library-load time, but metadata is only read
    // on Unity's main thread. A domain/assembly not ready yet is retryable.
    void* domain=domainGet();size_t count=0;
    const void** list=domain?assemblies(domain,&count):nullptr;
    if(!domain||!list||count==0||count>512){
        __android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata not ready: domain=%p assemblies=%zu",domain,count);return false;}
    void* assets=klass("Assembly-CSharp.dll","","AssetManager");
    void* gameManager=klass("Assembly-CSharp.dll","Manager","GameManager");
    void* render=klass("UnityEngine.CoreModule.dll","UnityEngine","RenderTexture");
    void* graphics=klass("UnityEngine.CoreModule.dll","UnityEngine","Graphics");
    void* conversion=klass("UnityEngine.ImageConversionModule.dll","UnityEngine","ImageConversion");
    void* unityObject=klass("UnityEngine.CoreModule.dll","UnityEngine","Object");
    textureClass=klass("UnityEngine.CoreModule.dll","UnityEngine","Texture2D");
    assetInstance=method(assets,"Instance",{});jacket=method(assets,"GetJacketTexture2D",{"System.Int32"});
    selectedMusic=method(gameManager,"get_SelectMusicID",{});
    if(!assetInstance||!jacket||!selectedMusic)return false;
    // Check resolved game MethodInfos against the exact verified functions.
    uintptr_t instanceCode=0,jacketCode=0,selectedCode=0;
    if(assetInstance)memcpy(&instanceCode,assetInstance,sizeof(instanceCode));if(jacket)memcpy(&jacketCode,jacket,sizeof(jacketCode));
    if(selectedMusic)memcpy(&selectedCode,selectedMusic,sizeof(selectedCode));
    if(instanceCode!=imageBase+targetBuild->RVA_ALBUM_INSTANCE||jacketCode!=imageBase+targetBuild->RVA_ALBUM_JACKET||selectedCode!=imageBase+targetBuild->RVA_ALBUM_SELECTED){
        initialized=true; // A present but different game method is not a readiness delay.
        __android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album metadata code mismatch: base=%p Instance=%p expected=%p Jacket=%p expected=%p Selected=%p expected=%p",reinterpret_cast<void*>(imageBase),reinterpret_cast<void*>(instanceCode),reinterpret_cast<void*>(imageBase+targetBuild->RVA_ALBUM_INSTANCE),reinterpret_cast<void*>(jacketCode),reinterpret_cast<void*>(imageBase+targetBuild->RVA_ALBUM_JACKET),reinterpret_cast<void*>(selectedCode),reinterpret_cast<void*>(imageBase+targetBuild->RVA_ALBUM_SELECTED));return false;}
    tempGet=method(render,"GetTemporary",{"System.Int32","System.Int32"});
    tempRelease=method(render,"ReleaseTemporary",{"UnityEngine.RenderTexture"});
    activeGet=method(render,"get_active",{});activeSet=method(render,"set_active",{"UnityEngine.RenderTexture"});
    blit=method(graphics,"Blit",{"UnityEngine.Texture","UnityEngine.RenderTexture"});
    textureCtor=method(textureClass,".ctor",{"System.Int32","System.Int32","UnityEngine.TextureFormat","System.Boolean"});
    readPixels=method(textureClass,"ReadPixels",{"UnityEngine.Rect","System.Int32","System.Int32"});
    encodePng=method(conversion,"EncodeToPNG",{"UnityEngine.Texture2D"});destroy=method(unityObject,"Destroy",{"UnityEngine.Object"});
    available=tempGet&&tempRelease&&activeGet&&activeSet&&blit&&textureCtor&&readPixels&&encodePng&&destroy;
    initialized=available;
    __android_log_print(available?ANDROID_LOG_INFO:ANDROID_LOG_WARN,"OniimaiKanade","Album 256px readback metadata %s",available?"ready":"unavailable");
    return available;
}
static bool readback(int music,std::vector<unsigned char>& png){
    if(!initialize())return false;
    Roots roots;void *manager=nullptr,*source=nullptr,*previous=nullptr,*render=nullptr,*texture=nullptr,*bytes=nullptr,*ignored=nullptr;
    if(!call(assetInstance,nullptr,nullptr,manager)||!manager||!roots.add(manager))return false;
    void* idArgs[]={&music};if(!call(jacket,manager,idArgs,source)||!source||!roots.add(source))return false;
    if(!call(activeGet,nullptr,nullptr,previous)||!roots.add(previous))return false;
    int width=EDGE,height=EDGE,rgba32=4,zero=0;bool mipmaps=false;
    void* sizeArgs[]={&width,&height};if(!call(tempGet,nullptr,sizeArgs,render)||!render)return false;
    bool success=false;
    do{
        if(!roots.add(render))break;
        void* blitArgs[]={source,render};if(!call(blit,nullptr,blitArgs,ignored))break;
        void* activeArgs[]={render};if(!call(activeSet,nullptr,activeArgs,ignored))break;
        texture=objectNew(textureClass);if(!texture||!roots.add(texture))break;
        void* constructorArgs[]={&width,&height,&rgba32,&mipmaps};if(!call(textureCtor,texture,constructorArgs,ignored))break;
        struct Rect{float x,y,width,height;} rect{0,0,float(EDGE),float(EDGE)};
        void* readArgs[]={&rect,&zero,&zero};if(!call(readPixels,texture,readArgs,ignored))break;
        void* encodeArgs[]={texture};if(!call(encodePng,nullptr,encodeArgs,bytes)||!bytes||!roots.add(bytes))break;
        uintptr_t length=0;memcpy(&length,static_cast<char*>(bytes)+0x18,sizeof(length));
        const auto* data=reinterpret_cast<unsigned char*>(bytes)+0x20;
        static const unsigned char magic[]={137,80,78,71,13,10,26,10};
        if(length<24||length>MAX_PNG||memcmp(data,magic,8))break;
        png.assign(data,data+length);success=true;
    }while(false);
    // Restore the previous framebuffer even when Blit, ReadPixels or encoding fails.
    cleanupCall(activeSet,previous);
    cleanupCall(tempRelease,render);
    if(texture)cleanupCall(destroy,texture);
    return success;
}
static void install(uintptr_t base,void* library){
    imageBase=base;bool instanceOk=matchesTarget(reinterpret_cast<void*>(base+targetBuild->RVA_ALBUM_INSTANCE),targetBuild->SIG_ALBUM_INSTANCE);
    bool jacketOk=matchesTarget(reinterpret_cast<void*>(base+targetBuild->RVA_ALBUM_JACKET),targetBuild->SIG_ALBUM_JACKET);
    bool selectedOk=matchesTarget(reinterpret_cast<void*>(base+targetBuild->RVA_ALBUM_SELECTED),targetBuild->SIG_ALBUM_SELECTED);
    targetVerified=instanceOk&&jacketOk&&selectedOk;
    __android_log_print(targetVerified?ANDROID_LOG_INFO:ANDROID_LOG_WARN,"OniimaiKanade","Album target verification: Instance=%d Jacket=%d Selected=%d",instanceOk,jacketOk,selectedOk);
    apiReady=targetVerified&&resolveApis(library);
}
static uint64_t revision(int music){
    uint64_t value=0;pthread_mutex_lock(&mutex);for(const auto& entry:cache)if(entry.music==music){value=entry.revision;break;}pthread_mutex_unlock(&mutex);return value;
}
static void capture(int music,uint64_t now){
    if(music<0||!targetVerified||revision(music))return;
    if(!initialize())return; // Readiness retries do not spend the bounded GPU readback budget.
    if(attemptMusic!=music){attemptMusic=music;attempts=0;nextAttempt=0;}
    if(attempts>=3||now<nextAttempt)return;
    ++attempts;nextAttempt=now+(attempts==1?1000:5000);
    std::vector<unsigned char> png;
    if(!readback(music,png)){__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Album capture unavailable: music=%d attempt=%d",music,attempts);return;}
    pthread_mutex_lock(&mutex);Entry& entry=cache[slot++%3];entry.music=music;entry.revision=++sequence;entry.png.swap(png);pthread_mutex_unlock(&mutex);
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Album captured: music=%d, 256px, attempt=%d",music,attempts);
}
static void prewarm(unsigned player,uint64_t now){
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Album preparation entered: player=%u",player);
    if(player>1||!initialize())return;
    void* array=nullptr;if(!call(selectedMusic,nullptr,nullptr,array)||!array)return;
    Roots roots;if(!roots.add(array))return;
    uintptr_t length=0;memcpy(&length,static_cast<char*>(array)+0x18,sizeof(length));
    if(length<=player||length>4)return;
    int music=-1;memcpy(&music,static_cast<char*>(array)+0x20+player*sizeof(int),sizeof(music));
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Album preparing selected music=%d cached=%d",music,revision(music)!=0);
    capture(music,now);
}
static jbyteArray copy(JNIEnv* env,uint64_t requested){
    jbyteArray result=nullptr;pthread_mutex_lock(&mutex);
    for(const auto& entry:cache)if(entry.revision==requested&&requested&&!entry.png.empty()){
        result=env->NewByteArray(jsize(entry.png.size()));if(result)env->SetByteArrayRegion(result,0,jsize(entry.png.size()),reinterpret_cast<const jbyte*>(entry.png.data()));break;
    }
    pthread_mutex_unlock(&mutex);return result;
}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_io_oniimai_kanade_GameAlbum_nativePng(JNIEnv* env,jclass,jlong revision){return GameAlbum::copy(env,uint64_t(revision));}
