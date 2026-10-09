#pragma once
#include "camera_pixels.h"
#include "camera_policy.h"
#include "camera_yuv.h"
#include <initializer_list>
#include <mutex>
#include <vector>
#include <chrono>

// Experimental front-camera adapter. Nothing is opened or read on JNI threads.
// Android publishes authorization; game hooks perform all Unity/managed work.
namespace GameCamera {
static std::atomic<unsigned> authorization{0}; // wanted=1, permission=2, foreground=4
static std::atomic<bool> requestedRear{false},activeRear{false};
static std::atomic<bool> requestedMirror{true},activeMirror{true};
static std::atomic<int> status{0};
static uintptr_t base=0;
static std::atomic<bool> installed{false};
static bool metadataReady=false,copySucceeded=false;
static std::atomic<bool> owned{false};
static std::mutex frameMutex;
static std::vector<uint32_t> cpuPixels;
static uint64_t frameToken=0,nextFrameToken=0,frameSequence=0,takenSequence=0;
static int cpuWidth=0,cpuHeight=0;
static std::chrono::steady_clock::time_point frameTime;
static bool preflightFailed=false,preflightPassed=false,preflightFrameReady=false;
static thread_local bool unlockScope=false,rawUnlock=false,rawPlay=false;
static uint32_t cameraHandle=0;
static uint32_t pixelsHandle=0;
static bool previewReported=false,frameReported=false;
static bool stoppedForPause=false;
static void* (*originalDevices)(const void*);
static bool (*originalUnlock)(void*,const void*);
static void (*originalConfig)(void*,void*,void*,const void*);
static void (*originalCopy)(void*,void*,const void*);
static bool (*originalTake)(int,const void*);
static void* (*originalGet)(void*,int,const void*);
static void (*originalPlay)(void*,const void*);
static void (*originalStop)(void*,const void*);
static bool (*originalIconDisabled)(void*,const void*);
static bool (*originalPhotoDisabled)(void*,const void*);
static void (*originalPreview)(void*,const void*);
static void* (*originalPhotoFrame)(void*,const void*);
static void (*originalView)(void*,const void*);
static void (*originalCapture)(void*,int,const void*);
static void (*originalDevelop)(void*,int,const void*);
static void* (*originalRegionPixels)(void*,int,int,int,int,const void*);
static thread_local bool captureReentry=false;
struct CaptureRegion {void* texture=nullptr;float width=0,height=0,x=0,y=0;bool consumed=false;};
static thread_local CaptureRegion captureRegion;
static void* (*domainGet)();
static const void** (*assemblies)(void*,size_t*);
static void* (*assemblyImage)(const void*);
static const char* (*imageName)(void*);
static void* (*classFromName)(void*,const char*,const char*);
static void* (*objectClass)(void*);
static void* (*classParent)(void*);
static const void* (*classMethods)(void*,void**);
static const char* (*methodName)(const void*);
static uint32_t (*paramCount)(const void*);
static void* (*fieldByName)(void*,const char*);
static size_t (*fieldOffset)(void*);
static void (*getStatic)(void*,void*);
static void (*getField)(void*,void*,void*);
static void (*setField)(void*,void*,void*);
static int32_t (*valueSize)(void*,uint32_t*);
static void* (*invoke)(const void*,void*,void**,void**);
static void* (*unbox)(void*);
static void* (*arrayNew)(void*,uintptr_t);
static uintptr_t (*arrayLength)(void*);
static void (*barrier)(void*,void**,void*);
static uint32_t (*hold)(void*,bool);
static void* (*held)(uint32_t);
static void (*release)(uint32_t);
static void* (*resolveIcall)(const char*);
static int (*rotation)(void*);
static bool (*mirrored)(void*);
static void *deviceClass=nullptr,*colorClass=nullptr,*skipField=nullptr,*dummyField=nullptr;
static void *systemClass=nullptr,*configClass=nullptr,*instanceField=nullptr,*configField=nullptr;
static const void *unlockMethod=nullptr,*pixelsMethod=nullptr,*widthMethod=nullptr,*heightMethod=nullptr,
                  *playingMethod=nullptr,*updatedMethod=nullptr,*stopMethod=nullptr,*playMethod=nullptr,*textureMethod=nullptr,
                  *setPixelsMethod=nullptr,*applyMethod=nullptr,*setTextureMethod=nullptr,
                  *getTextureMethod=nullptr,*rectTransformMethod=nullptr,*rectMethod=nullptr,
                  *positionMethod=nullptr,*setPositionMethod=nullptr,*setSizeMethod=nullptr,
                  *setUvMethod=nullptr,*setMaskableMethod=nullptr,
                  *enabledMethod=nullptr,*gameObjectMethod=nullptr,*setActiveMethod=nullptr,
                  *childCountMethod=nullptr,*childMethod=nullptr,*nameMethod=nullptr,
                  *captureMethod=nullptr,*developMethod=nullptr;

static bool allowed(){return CameraPolicy::authorized(authorization.load());}
struct Root {
    uint32_t id;
    explicit Root(void* value):id(value?hold(value,false):0){}
    ~Root(){if(id)release(id);}
};
static bool call(const void* method,void* self,void** args,void*& result){
    void* error=nullptr;result=invoke(method,self,args,&error);
    if(error){status=-5;__android_log_print(ANDROID_LOG_WARN,"OniimaiKanade","Front camera: managed call failed (%s)",methodName(method));return false;}
    return true;
}
static bool integer(const void* method,void* object,int& value){
    void* boxed=nullptr;if(!call(method,object,nullptr,boxed)||!boxed)return false;
    void* data=unbox(boxed);if(!data)return false;memcpy(&value,data,sizeof(value));return true;
}
static bool boolean(const void* method,void* object){
    void* boxed=nullptr;if(!call(method,object,nullptr,boxed)||!boxed)return false;
    void* data=unbox(boxed);return data&&*static_cast<unsigned char*>(data)!=0;
}
static void* klass(const char* image,const char* space,const char* name){
    void* domain=domainGet();size_t count=0;const void** list=domain?assemblies(domain,&count):nullptr;
    if(!list||count>512)return nullptr;
    for(size_t i=0;i<count;i++){void* item=assemblyImage(list[i]);const char* label=item?imageName(item):nullptr;
        if(label&&!strcmp(label,image))return classFromName(item,space,name);}
    return nullptr;
}
static const void* method(void* type,const char* name,unsigned parameters,uintptr_t expected=0){
    if(!type)return nullptr;void* iterator=nullptr;const void* candidate;const void* found=nullptr;
    while((candidate=classMethods(type,&iterator))){
        if(strcmp(methodName(candidate),name)||paramCount(candidate)!=parameters)continue;
        if(found)return nullptr;found=candidate;
    }
    uintptr_t code=0;if(found)memcpy(&code,found,sizeof(code));
    return found&&(!expected||code==base+expected)?found:nullptr;
}
static bool offset(void* type,const char* name,size_t expected){
    void* field=type?fieldByName(type,name):nullptr;return field&&fieldOffset(field)==expected;
}
static bool initialize(){
    if(metadataReady)return true;
    if(!installed)return false;
    deviceClass=klass("UnityEngine.AudioModule.dll","UnityEngine","WebCamDevice");
    colorClass=klass("UnityEngine.CoreModule.dll","UnityEngine","Color32");
    void* webcam=klass("UnityEngine.AudioModule.dll","UnityEngine","WebCamTexture");
    void* texture=klass("UnityEngine.CoreModule.dll","UnityEngine","Texture");
    void* manager=klass("Assembly-CSharp.dll","Manager","CameraManager");
    void* photo=klass("Assembly-CSharp.dll","Manager","GamePhotoConteiner");
    void* preview=klass("Assembly-CSharp.dll","","PhotographingController");
    void* simpleProcess=klass("Assembly-CSharp.dll","Process","SimpleSettingProcess");
    void* photoProcess=klass("Assembly-CSharp.dll","","PhotoShootProcess");
    void* simpleMonitor=klass("Assembly-CSharp.dll","","SimpleSettingMonitor");
    void* photoMonitor=klass("Assembly-CSharp.dll","","PhotoShootMonitor");
    void* texture2d=klass("UnityEngine.CoreModule.dll","UnityEngine","Texture2D");
    void* rawImage=klass("UnityEngine.UI.dll","UnityEngine.UI","RawImage");
    void* graphic=klass("UnityEngine.UI.dll","UnityEngine.UI","Graphic");
    void* rectTransform=klass("UnityEngine.CoreModule.dll","UnityEngine","RectTransform");
    void* settings=klass("Assembly-CSharp.dll","","KanadeDXMainSetting");
    configClass=klass("Assembly-CSharp.dll","MAI2System","Config");
    systemClass=klass("Assembly-CSharp.dll","MAI2System","SystemConfig");
    // Resolve the coroutine through its actual object class in unlock().
    skipField=settings?fieldByName(settings,"skipPhotoCamera"):nullptr;
    dummyField=configClass?fieldByName(configClass,"<IsDummyPhotoCamera>k__BackingField"):nullptr;
    void* parent=systemClass?classParent(systemClass):nullptr;
    instanceField=parent?fieldByName(parent,"_instance"):nullptr;
    configField=systemClass?fieldByName(systemClass,"_config"):nullptr;
    uint32_t alignment=0;
    if(!deviceClass||!colorClass||!skipField||!dummyField||!instanceField||!configField||valueSize(deviceClass,&alignment)!=32||valueSize(colorClass,&alignment)!=4||
       !offset(deviceClass,"m_Name",16)||!offset(deviceClass,"m_DepthCameraName",24)||!offset(deviceClass,"m_Flags",32)||
       !offset(deviceClass,"m_Kind",36)||!offset(deviceClass,"m_Resolutions",40)||
       !offset(photo,"<Enable>k__BackingField",16)||!offset(photo,"<Colors>k__BackingField",24)||
       !offset(preview,"_originalTexture",0x108)||!offset(preview,"_previewTexture",0x110)||
       !offset(preview,"_mirrorPreviewTexture",0x118)||!offset(preview,"_originalColors",0x120)||
       !offset(preview,"_mirrorColors",0x130)||!offset(preview,"_cameraPreview",0x28)||
       !offset(preview,"_subMonitorAllPreview",0x38)||!offset(preview,"_subMonitorFramePreview",0x50)||
       !offset(preview,"_subMonitorPseudoFramePreview",0x40)||!offset(preview,"_subMonitorPreviewObject",0x48)||
       !offset(preview,"_frameImage",0x30)||!offset(preview,"_previewColor",0x128)||
       !offset(preview,"_adjustBasePosition",0x140)||!offset(preview,"_subMonitorPreviewTargetObject",0x78)||
       !offset(preview,"_shutter",0x60)||!offset(preview,"_subMonitorFrame",0x58)||!offset(preview,"_isShutter",0x13d)||
       !offset(simpleProcess,"_monitors",0x98)||!offset(photoProcess,"_monitors",0x48)||
       !offset(simpleMonitor,"_photoController",0x50)||!offset(photoMonitor,"_photoController",0x38))return false;
    captureMethod=method(simpleProcess,"Capture",1,targetBuild->RVA_CAMERA_CAPTURE);
    developMethod=method(photoProcess,"CaptureDevelop",1,targetBuild->RVA_CAMERA_DEVELOP);
    pixelsMethod=method(webcam,"GetPixels32",1);
    setPixelsMethod=method(texture2d,"SetPixels32",1);applyMethod=method(texture2d,"Apply",0);
    setTextureMethod=method(rawImage,"set_texture",1);
    getTextureMethod=method(rawImage,"get_texture",0);
    rectTransformMethod=method(graphic,"get_rectTransform",0);
    rectMethod=method(rectTransform,"get_rect",0);
    positionMethod=method(rectTransform,"get_anchoredPosition",0);
    setPositionMethod=method(rectTransform,"set_anchoredPosition",1);
    setSizeMethod=method(rectTransform,"set_sizeDelta",1);
    setUvMethod=method(rawImage,"set_uvRect",1);
    setMaskableMethod=method(klass("UnityEngine.UI.dll","UnityEngine.UI","MaskableGraphic"),"set_maskable",1);
    enabledMethod=method(klass("UnityEngine.CoreModule.dll","UnityEngine","Behaviour"),"set_enabled",1);
    gameObjectMethod=method(klass("UnityEngine.CoreModule.dll","UnityEngine","Component"),"get_gameObject",0);
    setActiveMethod=method(klass("UnityEngine.CoreModule.dll","UnityEngine","GameObject"),"SetActive",1);
    void* transform=klass("UnityEngine.CoreModule.dll","UnityEngine","Transform");
    childCountMethod=method(transform,"get_childCount",0);childMethod=method(transform,"GetChild",1);
    nameMethod=method(klass("UnityEngine.CoreModule.dll","UnityEngine","Object"),"get_name",0);
    widthMethod=method(texture,"get_width",0);heightMethod=method(texture,"get_height",0);
    playingMethod=method(webcam,"get_isPlaying",0);updatedMethod=method(webcam,"get_didUpdateThisFrame",0);
    playMethod=method(webcam,"Play",0,targetBuild->RVA_CAMERA_PLAY);stopMethod=method(webcam,"Stop",0);
    textureMethod=method(manager,"GetTexture",1);
    rotation=reinterpret_cast<decltype(rotation)>(resolveIcall("UnityEngine.WebCamTexture::get_videoRotationAngle"));
    mirrored=reinterpret_cast<decltype(mirrored)>(resolveIcall("UnityEngine.WebCamTexture::get_videoVerticallyMirrored"));
    metadataReady=pixelsMethod&&widthMethod&&heightMethod&&playingMethod&&updatedMethod&&playMethod&&stopMethod&&textureMethod&&rotation&&mirrored&&setPixelsMethod&&applyMethod&&setTextureMethod&&getTextureMethod&&rectTransformMethod&&rectMethod&&positionMethod&&setPositionMethod&&setSizeMethod&&setUvMethod&&setMaskableMethod&&enabledMethod&&gameObjectMethod&&setActiveMethod&&childCountMethod&&childMethod&&nameMethod&&captureMethod&&developMethod;
    if(metadataReady)__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera metadata verified; Android permission and opt-in required");
    return metadataReady;
}
static bool setDummy(bool value){
    // Use the already-created configuration, without constructing a replacement
    // singleton or changing the saved SkipPhotoCamera preference.
    void* system=nullptr;getStatic(instanceField,&system);
    if(!system||objectClass(system)!=systemClass)return false;
    void* config=nullptr;getField(system,configField,&config);
    if(!config||objectClass(config)!=configClass)return false;
    setField(config,dummyField,&value);return true;
}
static void* devices(const void* info){
    void* all=originalDevices(info);
    if(!owned)return all;
    if(!initialize())return nullptr;
    Root original(all);uintptr_t count=all?arrayLength(all):0;
    if(all&&!original.id){status=-5;return arrayNew(deviceClass,0);}
    int index=-1;
    if(allowed()&&count<=64)for(uintptr_t i=0;i<count;i++){
        int flags=0;memcpy(&flags,static_cast<char*>(all)+0x20+i*32+16,sizeof(flags));
        if(bool(flags&1)!=activeRear.load()){index=int(i);break;} // Unity FrontFacing flag; no opposite-lens fallback.
    }
    void* one=arrayNew(deviceClass,index>=0?1:0);if(!one)return nullptr;
    if(index<0){status=-3;return one;}
    auto* src=static_cast<char*>(all)+0x20+index*32;auto* dst=static_cast<char*>(one)+0x20;
    // Three managed references require the write barrier even in a value array.
    for(int pos:{0,8,24}){void* reference=nullptr;memcpy(&reference,src+pos,8);barrier(one,reinterpret_cast<void**>(dst+pos),reference);}
    memcpy(dst+16,src+16,8);status=2;return one;
}
static void config(void* object,void* key,void* value,const void* info){
    originalConfig(object,key,value,info);
    if(!owned||!unlockScope||!allowed()||!metadataReady||!object||!key)return;
    static constexpr char name[]="IsDummyPhotoCamera";
    int length=0;memcpy(&length,static_cast<char*>(key)+16,4);if(length!=sizeof(name)-1)return;
    const auto* text=reinterpret_cast<const uint16_t*>(static_cast<char*>(key)+20);
    for(int i=0;i<length;i++)if(text[i]!=uint16_t(name[i]))return;
    // A real preflight timeout must stay a failure. Previous releases forced
    // this flag back to false even when startup had not tested the camera.
    bool dummy=false;getField(object,dummyField,&dummy);
    if(dummy){preflightFailed=true;status=-4;}
}
static bool unlock(void* self,const void* info){
    if(rawUnlock)return originalUnlock(self,info);
    if(!self)return originalUnlock(self,info);
    int state=0;memcpy(&state,static_cast<char*>(self)+0x10,4);
    if(state==0){
        preflightFailed=false;preflightPassed=false;preflightFrameReady=false;owned=false;
        activeRear=requestedRear.load();
        activeMirror=requestedMirror.load();
        if(!allowed())return originalUnlock(self,info);
        if(!initialize()){status=-2;return originalUnlock(self,info);}
        bool skip=false;getStatic(skipField,&skip);
        if(!CameraPolicy::begin(authorization.load(),metadataReady,skip)){
            if(skip)status=-6;
            return originalUnlock(self,info);
        }
        unlockMethod=method(objectClass(self),"MoveNext",0,targetBuild->RVA_CAMERA_UNLOCK);
        if(!unlockMethod){status=-2;return originalUnlock(self,info);}
        if(!setDummy(false)){status=-2;return originalUnlock(self,info);}
        if(cameraHandle){release(cameraHandle);cameraHandle=0;}
        owned=true;
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Camera: SkipPhotoCamera OFF; running real %s-camera preflight",activeRear.load()?"rear":"front");
    }
    if(!owned)return originalUnlock(self,info);
    // Never force skipPhotoCamera=true. Filtering get_devices makes the real
    // preflight select the front camera, with the game's original timeout.
    rawUnlock=true;unlockScope=true;void* result=nullptr;
    bool ok=call(unlockMethod,self,nullptr,result);
    unlockScope=false;rawUnlock=false;
    void* data=ok&&result?unbox(result):nullptr;
    bool pending=data&&*static_cast<unsigned char*>(data)!=0;
    if(!pending){
        void* camera=cameraHandle?held(cameraHandle):nullptr;
        preflightPassed=ok&&!preflightFailed&&allowed()&&preflightFrameReady;
        if(!preflightPassed){setDummy(true);if(status>=0)status=-4;}
        // Stop also on timeout: the port only stops its test texture on success.
        void* ignored=nullptr;if(camera)call(stopMethod,camera,nullptr,ignored);
        if(cameraHandle){release(cameraHandle);cameraHandle=0;}
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera preflight %s; photo settings %s",preflightPassed?"passed":"failed",preflightPassed?"unlocked":"unchanged");
    }
    return pending;
}
static bool iconDisabled(void* self,const void* info){
    return CameraPolicy::menuDisabled(originalIconDisabled(self,info),authorization.load(),owned,preflightPassed);
}
static bool photoDisabled(void* self,const void* info){
    return CameraPolicy::menuDisabled(originalPhotoDisabled(self,info),authorization.load(),owned,preflightPassed);
}
static void play(void* self,const void* info){
    if(rawPlay){originalPlay(self,info);return;}
    if(!owned){originalPlay(self,info);return;}
    if(!allowed()||!metadataReady||!self)return;
    if(cameraHandle){release(cameraHandle);cameraHandle=0;}
    if(pixelsHandle){release(pixelsHandle);pixelsHandle=0;}
    previewReported=false;frameReported=false;
    cameraHandle=hold(self,false);if(!cameraHandle){status=-5;return;}
    stoppedForPause=false;rawPlay=true;void* ignored=nullptr;bool ok=call(playMethod,self,nullptr,ignored);rawPlay=false;
    if(ok)status=3;
}
static void stop(void* self,const void* info){
    // The success branch stops the test texture. Inspect it BEFORE Stop, since
    // some Unity backends reset dimensions immediately after closing the camera.
    if(owned&&unlockScope&&!preflightFailed&&allowed()&&self&&cameraHandle&&held(cameraHandle)==self){
        int w=0,h=0;
        preflightFrameReady=boolean(playingMethod,self)&&integer(widthMethod,self,w)&&integer(heightMethod,self,h)&&w>128&&h>128;
    }
    originalStop(self,info);
}
static void poll(){
    if(!owned||!metadataReady||!cameraHandle||allowed()||stoppedForPause)return;
    void* camera=held(cameraHandle);void* ignored=nullptr;
    if(camera)call(stopMethod,camera,nullptr,ignored);
    stoppedForPause=true;
    // Do not reopen automatically on resume: the game's next explicit camera
    // Play owns that decision, including its photo consent/countdown screens.
}
static bool frame(void* camera,void* destination,bool requireFresh=true){
    if(!owned||!allowed()||!metadataReady||!camera||!destination||
       arrayLength(destination)!=size_t(CameraPixels::WIDTH)*CameraPixels::HEIGHT)return false;
    if(!boolean(playingMethod,camera))return false;
    Root textureRoot(camera),destRoot(destination);if(!textureRoot.id||!destRoot.id)return false;
    int angle=rotation(camera);bool flip=mirrored(camera);
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        if(frameToken){
            // The port can omit Hidden/VideoDecodeAndroid. Read the YUV output
            // from the SAME Camera2 session, avoiding that shader entirely.
            if(cpuPixels.empty()||std::chrono::steady_clock::now()-frameTime>std::chrono::milliseconds(500)||
                (requireFresh&&frameSequence==takenSequence))return false;
            bool ok=CameraPixels::copy(cpuPixels.data(),cpuWidth,cpuHeight,cpuPixels.size(),
                reinterpret_cast<uint32_t*>(static_cast<char*>(destination)+0x20),CameraPixels::WIDTH,CameraPixels::HEIGHT,
                arrayLength(destination),angle,flip);
            if(ok){
                if(requireFresh)takenSequence=frameSequence;
                status=4;
                if(!frameReported){frameReported=true;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera CPU frame: %dx%d, rotation=%d, flip=%d",cpuWidth,cpuHeight,angle,int(flip));}
            }
            return ok;
        }
    }
    if(requireFresh&&!boolean(updatedMethod,camera))return false;
    int w=0,h=0;if(!integer(widthMethod,camera,w)||!integer(heightMethod,camera,h))return false;
    if(w<=16||h<=16||w>CameraPixels::MAX_EDGE||h>CameraPixels::MAX_EDGE||size_t(w)*h>CameraPixels::MAX_PIXELS)return false;
    // The engine getters are registered in both verified libunity builds, even
    // though their managed public wrappers were stripped from the game metadata.
    // Reuse the engine-sized array across preview frames. Always consume the
    // returned array: Unity may replace the supplied one after a size change.
    void* buffer=pixelsHandle?held(pixelsHandle):nullptr;
    if(buffer&&arrayLength(buffer)!=size_t(w)*h){release(pixelsHandle);pixelsHandle=0;buffer=nullptr;}
    void* args[]={buffer};void* pixels=nullptr;
    if(!call(pixelsMethod,camera,args,pixels)||!pixels)return false;
    Root sourceRoot(pixels);if(!sourceRoot.id)return false;
    if(buffer!=pixels){if(pixelsHandle)release(pixelsHandle);pixelsHandle=hold(pixels,false);}
    uintptr_t count=arrayLength(pixels);if(!CameraPixels::shape(w,h,count))return false;
    bool ok=CameraPixels::copy(reinterpret_cast<uint32_t*>(static_cast<char*>(pixels)+0x20),w,h,count,
        reinterpret_cast<uint32_t*>(static_cast<char*>(destination)+0x20),CameraPixels::WIDTH,CameraPixels::HEIGHT,
        arrayLength(destination),angle,flip);
    if(ok){status=4;if(!frameReported){frameReported=true;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera frame: %dx%d, rotation=%d, flip=%d; normalized to 1280x960",w,h,angle,int(flip));}}
    return ok;
}
static void* reference(void* self,size_t offset){void* value=nullptr;memcpy(&value,static_cast<char*>(self)+offset,sizeof(value));return value;}
static bool uploadPreview(void* texture,void* colors){
    if(!texture||!colors)return false;
    int w=0,h=0;
    if(!integer(widthMethod,texture,w)||!integer(heightMethod,texture,h)||w!=CameraPixels::WIDTH||h!=CameraPixels::HEIGHT)return false;
    void* args[]={colors};void* ignored=nullptr;
    return call(setPixelsMethod,texture,args,ignored)&&call(applyMethod,texture,nullptr,ignored);
}
static bool bindPreview(void* image,void* texture){
    if(!image)return false;void* args[]={texture};void* ignored=nullptr;
    return call(setTextureMethod,image,args,ignored);
}
struct Vec2 {float x,y;};
struct Rect {float x,y,w,h;};
template<class T> static bool value(const void* m,void* self,T& result){
    void* boxed=nullptr;if(!self||!call(m,self,nullptr,boxed)||!boxed)return false;
    void* data=unbox(boxed);if(!data)return false;memcpy(&result,data,sizeof(T));return true;
}
static void* imageRect(void* image){
    void* result=nullptr;return image&&call(rectTransformMethod,image,nullptr,result)?result:nullptr;
}
static bool setVector(const void* m,void* self,Vec2 v){
    void* ignored=nullptr;void* args[]={&v};return self&&call(m,self,args,ignored);
}
static bool cropSize(void* self,int& w,int& h){
    void* image=reference(self,0x30),*texture=nullptr;int tw=0,th=0;
    if(!image||!call(getTextureMethod,image,nullptr,texture)||!texture||
       !integer(widthMethod,texture,tw)||!integer(heightMethod,texture,th)||
       tw<=0||th<=0||tw>CameraPixels::WIDTH||th>CameraPixels::HEIGHT)return false;
    w=tw;h=th;return true;
}
static void centerPreview(void* self){
    void* main=imageRect(reference(self,0x28)),*cut=imageRect(reference(self,0x50)),
        *guide=imageRect(reference(self,0x40)),*all=imageRect(reference(self,0x38));
    Vec2 basePosition{};memcpy(&basePosition,static_cast<char*>(self)+0x140,sizeof(basePosition));
    // Drop the cabinet P1/P2 quarter-width offset, retaining later user edits.
    if(basePosition.x!=0||basePosition.y!=0){
        Vec2 zero{};if(!setVector(setPositionMethod,main,zero))return;
        memcpy(static_cast<char*>(self)+0x140,&zero,sizeof(zero));
    }
    Rect root{},full{},mainBounds{};Vec2 mainPosition{};int cw=512,ch=512;
    cropSize(self,cw,ch); // The frame texture is absent during Initialize.
    if(!value(rectMethod,reference(self,0x48),root)||!value(rectMethod,all,full)||
       !value(rectMethod,main,mainBounds)||!value(positionMethod,main,mainPosition)||
       !(root.w>0&&root.h>0&&full.w>0&&full.h>0&&mainBounds.w>0&&mainBounds.h>0))return;
    int left=0,bottom=0;
    if(!CameraPixels::viewCrop(CameraPixels::WIDTH,CameraPixels::HEIGHT,cw,ch,
        mainBounds.w,mainBounds.h,mainPosition.x,mainPosition.y,left,bottom))return;
    Rect uv{float(left)/CameraPixels::WIDTH,float(bottom)/CameraPixels::HEIGHT,
            float(cw)/CameraPixels::WIDTH,float(ch)/CameraPixels::HEIGHT};
    Vec2 selected{full.w*(uv.x+uv.w*.5f-.5f),full.h*(uv.y+uv.h*.5f-.5f)};
    Vec2 selectedSize{full.w*uv.w,full.h*uv.h};
    setVector(setPositionMethod,guide,selected);setVector(setSizeMethod,guide,selectedSize);
    // IMG_PreviewGuide is a separate sliced sprite with its white square in
    // the P1 half. Moving the decorative RawImage above cannot move this box.
    setVector(setPositionMethod,reference(self,0x78),{selected.x+full.w*.25f,selected.y});
    // Render a bounded quad with cropped UVs, rather than an oversized image
    // relying on the cabinet's RectMask2D path (black side strips on Android).
    setVector(setPositionMethod,cut,{0,0});setVector(setSizeMethod,cut,{root.w,root.h});
    void* ignored=nullptr;void* uvArgs[]={&uv};call(setUvMethod,reference(self,0x50),uvArgs,ignored);
    bool maskable=false;void* maskArgs[]={&maskable};call(setMaskableMethod,reference(self,0x50),maskArgs,ignored);
    static auto lastLog=std::chrono::steady_clock::time_point{};
    auto now=std::chrono::steady_clock::now();
    if(now-lastLog>std::chrono::seconds(10)){
        lastLog=now;
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Camera crop 1.3.36: square=%dx%d pane=%.1fx%.1f UV=%.3f,%.3f,%.3f,%.3f",cw,ch,root.w,root.h,uv.x,uv.y,uv.w,uv.h);
    }
}
static bool fillPhotoCrop(void* self){
    int w=0,h=0;if(!cropSize(self,w,h))return false;
    void* colors=reference(self,activeMirror.load()?0x130:0x120),*cropColors=reference(self,0x128);
    if(!colors||!cropColors)return false;
    void* main=imageRect(reference(self,0x28));Rect bounds{};Vec2 pos{};
    if(!value(rectMethod,main,bounds)||!value(positionMethod,main,pos)||!(bounds.w>0&&bounds.h>0))return false;
    int left=0,bottom=0;
    if(!CameraPixels::viewCrop(CameraPixels::WIDTH,CameraPixels::HEIGHT,w,h,bounds.w,bounds.h,pos.x,pos.y,left,bottom))return false;
    return CameraPixels::crop(reinterpret_cast<uint32_t*>(static_cast<char*>(colors)+0x20),
        CameraPixels::WIDTH,CameraPixels::HEIGHT,arrayLength(colors),
        reinterpret_cast<uint32_t*>(static_cast<char*>(cropColors)+0x20),w,h,arrayLength(cropColors),
        left-(CameraPixels::WIDTH-w)/2,bottom-(CameraPixels::HEIGHT-h)/2);
}
// Both profile-photo processes crop _previewTexture themselves, bypassing
// GetCameraFrameTexture. Scope the correction to their one camera GetPixels
// request; retain the game's frame compositing, resizing, consent and saving.
static void* regionPixels(void* texture,int x,int y,int w,int h,const void* info){
    auto& region=captureRegion;
    if(region.texture&&region.texture==texture&&!region.consumed){
        region.consumed=true;
        int left=0,bottom=0;
        if(!allowed()||!CameraPixels::viewCrop(CameraPixels::WIDTH,CameraPixels::HEIGHT,w,h,
            region.width,region.height,region.x,region.y,left,bottom))return nullptr;
        __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Camera final crop: %d,%d -> %d,%d size=%dx%d",x,y,left,bottom,w,h);
        x=left;y=bottom;
    }
    return originalRegionPixels(texture,x,y,w,h,info);
}
static void captureSelected(void* self,int player,bool simple){
    if(!self||!allowed()||!metadataReady||player<0||player>1)return;
    Root process(self);if(!process.id)return;
    void* monitors=reference(self,simple?0x98:0x48);
    if(!monitors||arrayLength(monitors)<=size_t(player)||arrayLength(monitors)>2)return;
    void* monitor=reference(monitors,0x20+size_t(player)*sizeof(void*));
    void* controller=monitor?reference(monitor,simple?0x50:0x38):nullptr;
    void* texture=controller?reference(controller,0x110):nullptr;
    Root pixels(texture);if(!pixels.id)return;
    int width=0,height=0;
    void* main=controller?imageRect(reference(controller,0x28)):nullptr;
    Rect bounds{};Vec2 position{};
    if(!integer(widthMethod,texture,width)||!integer(heightMethod,texture,height)||
       width!=CameraPixels::WIDTH||height!=CameraPixels::HEIGHT||
       !value(rectMethod,main,bounds)||!value(positionMethod,main,position))return;
    CaptureRegion previous=captureRegion;
    captureRegion={texture,bounds.w,bounds.h,position.x,position.y,false};
    // runtime_invoke contains managed exceptions, so the scope cannot leak
    // into unrelated texture reads if the original save operation fails.
    bool previousReentry=captureReentry;captureReentry=true;
    void* ignored=nullptr;void* args[]={&player};
    call(simple?captureMethod:developMethod,self,args,ignored);
    captureReentry=previousReentry;captureRegion=previous;
}
static void capture(void* self,int player,const void* info){
    if(!owned||captureReentry){originalCapture(self,player,info);return;}
    captureSelected(self,player,true);
}
static void develop(void* self,int player,const void* info){
    if(!owned||captureReentry){originalDevelop(self,player,info);return;}
    captureSelected(self,player,false);
}
static void* photoFrame(void* self,const void* info){
    if(!owned)return originalPhotoFrame(self,info);
    if(!self||!allowed()||!metadataReady||!fillPhotoCrop(self))return nullptr;
    return originalPhotoFrame(self,info);
}
static void preview(void* self,const void* info){
    if(!owned){originalPreview(self,info);return;}
    if(!self||!allowed()||!metadataReady)return;
    // This path does not use CameraManager.Get or GamePhotoConteiner.CopyColor.
    // Keep the game's own textures/crop UI, but populate its arrays from the
    // actual returned camera frame before publishing either preview texture.
    Root owner(self);if(!owner.id)return;
    void* camera=reference(self,0x108),*colors=reference(self,0x120),*mirror=reference(self,0x130);
    if(!camera||!colors||!mirror||arrayLength(mirror)!=size_t(CameraPixels::WIDTH)*CameraPixels::HEIGHT)return;
    if(!frame(camera,colors,false))return;
    centerPreview(self);
    if(!CameraPixels::mirrorHorizontal(reinterpret_cast<uint32_t*>(static_cast<char*>(colors)+0x20),
        reinterpret_cast<uint32_t*>(static_cast<char*>(mirror)+0x20),CameraPixels::WIDTH,CameraPixels::HEIGHT,arrayLength(mirror)))return;
    void* normalTexture=reference(self,0x110),*mirrorTexture=reference(self,0x118);
    void* displayTexture=activeMirror.load()?mirrorTexture:normalTexture;
    // GetCaptureTexture returns _previewTexture directly after the shot. Keep
    // it and the crop buffer in exactly the same orientation as the preview,
    // so switching to the adjustment/result screen cannot flip the photograph.
    void* displayColors=activeMirror.load()?mirror:colors;
    bool ok=uploadPreview(normalTexture,displayColors)&&uploadPreview(mirrorTexture,displayColors)&&
        bindPreview(reference(self,0x28),normalTexture)&&bindPreview(reference(self,0x38),displayTexture)&&
        bindPreview(reference(self,0x50),displayTexture);
    if(ok&&!previewReported){previewReported=true;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera preview: centered single-player crop; game textures updated");}
}
static void view(void* self,const void* info){
    if(!owned||!self||!metadataReady){originalView(self,info);return;}
    Root owner(self);if(!owner.id){originalView(self,info);return;}
    originalView(self,info);
    // The cabinet wipe shader leaves opaque side bars on the Android preview
    // even in its fully-open state. Keep shutter layers out of the idle preview;
    // the game's own timing/material animation still runs during a real shot.
    bool visible=!allowed()||*reinterpret_cast<unsigned char*>(static_cast<char*>(self)+0x13d)!=0;
    void* args[]={&visible};void* ignored=nullptr;
    void* shutter=reference(self,0x60);
    if(shutter)call(enabledMethod,shutter,args,ignored);
    void* frame=imageRect(reference(self,0x58));int count=0;
    if(frame&&integer(childCountMethod,frame,count)&&count>0&&count<=8){
        for(int i=0;i<count;i++){
            void* child=nullptr;void* childArgs[]={&i};void* label=nullptr;
            if(!call(childMethod,frame,childArgs,child)||!child||!call(nameMethod,child,nullptr,label)||!label)continue;
            int length=0;memcpy(&length,static_cast<char*>(label)+0x10,4);
            static const char16_t expected[]=u"Shutter";
            if(length!=7||memcmp(static_cast<char*>(label)+0x14,expected,14))continue;
            void* object=nullptr;if(call(gameObjectMethod,child,nullptr,object)&&object)call(setActiveMethod,object,args,ignored);
        }
    }
}
static void copy(void* self,void* camera,const void* info){
    if(!owned){originalCopy(self,camera,info);return;}
    copySucceeded=false;if(!self)return;
    *static_cast<unsigned char*>(self)=0;void* destination=nullptr;memcpy(&destination,static_cast<char*>(self)+8,8);
    if(!frame(camera,destination)){status=-4;return;}
    // Memorial photos bypass the profile preview textures. Normalize each new
    // frame first, then apply the selected mirroring once before Enable/JPEG.
    // The same startup-latched setting controls profile preview and final crop.
    bool mirror=activeMirror.load();
    if(mirror&&!CameraPixels::mirrorHorizontalInPlace(
        reinterpret_cast<uint32_t*>(static_cast<char*>(destination)+0x20),
        CameraPixels::WIDTH,CameraPixels::HEIGHT,arrayLength(destination))){status=-4;return;}
    *static_cast<unsigned char*>(self)=1;copySucceeded=true;
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Camera memorial photo: %s",mirror?"mirrored to match preview":"unmirrored to match preview");
}
static bool take(int index,const void* info){
    if(!owned)return originalTake(index,info);
    if(!allowed()||index!=0)return false;
    copySucceeded=false;bool result=originalTake(index,info);return result&&copySucceeded;
}
static void* get(void* self,int type,const void* info){
    if(!owned)return originalGet(self,type,info);
    if(!allowed()||!metadataReady||type!=0)return nullptr;
    void* camera=nullptr;void* args[]={&type};if(!call(textureMethod,nullptr,args,camera)||!camera)return nullptr;
    Root textureRoot(camera);if(!textureRoot.id)return nullptr;
    void* result=arrayNew(colorClass,CameraPixels::WIDTH*CameraPixels::HEIGHT);
    // This legacy getter can run faster than the camera's frame rate. Reuse the
    // engine's latest valid frame, as the original getter does; only a new game
    // photograph requires didUpdateThisFrame (copy()/TakeGamePicture).
    if(!result||!frame(camera,result,false)){status=-4;return nullptr;}
    return result;
}
static void install(uintptr_t image,void* library){
    base=image;bool valid=true;
#define CAMERA_API(variable,name) {void* p=dlsym(library,name);Dl_info d{};if(!p||!dladdr(p,&d)||reinterpret_cast<uintptr_t>(d.dli_fbase)!=base)valid=false;else variable=reinterpret_cast<decltype(variable)>(p);}
    CAMERA_API(domainGet,"il2cpp_domain_get") CAMERA_API(assemblies,"il2cpp_domain_get_assemblies")
    CAMERA_API(assemblyImage,"il2cpp_assembly_get_image") CAMERA_API(imageName,"il2cpp_image_get_name")
    CAMERA_API(classFromName,"il2cpp_class_from_name") CAMERA_API(classMethods,"il2cpp_class_get_methods")
    CAMERA_API(objectClass,"il2cpp_object_get_class")
    CAMERA_API(classParent,"il2cpp_class_get_parent")
    CAMERA_API(methodName,"il2cpp_method_get_name") CAMERA_API(paramCount,"il2cpp_method_get_param_count")
    CAMERA_API(fieldByName,"il2cpp_class_get_field_from_name") CAMERA_API(fieldOffset,"il2cpp_field_get_offset")
    CAMERA_API(getStatic,"il2cpp_field_static_get_value")
    CAMERA_API(getField,"il2cpp_field_get_value")
    CAMERA_API(setField,"il2cpp_field_set_value") CAMERA_API(valueSize,"il2cpp_class_value_size")
    CAMERA_API(invoke,"il2cpp_runtime_invoke") CAMERA_API(unbox,"il2cpp_object_unbox")
    CAMERA_API(arrayNew,"il2cpp_array_new") CAMERA_API(arrayLength,"il2cpp_array_length")
    CAMERA_API(barrier,"il2cpp_gc_wbarrier_set_field") CAMERA_API(hold,"il2cpp_gchandle_new")
    CAMERA_API(held,"il2cpp_gchandle_get_target") CAMERA_API(release,"il2cpp_gchandle_free")
    CAMERA_API(resolveIcall,"il2cpp_resolve_icall")
#undef CAMERA_API
    const uintptr_t offsets[]={targetBuild->RVA_CAMERA_DEVICES,targetBuild->RVA_CAMERA_UNLOCK,targetBuild->RVA_CAMERA_CONFIG,targetBuild->RVA_CAMERA_COPY,targetBuild->RVA_CAMERA_TAKE,targetBuild->RVA_CAMERA_GET,targetBuild->RVA_CAMERA_PLAY,targetBuild->RVA_CAMERA_ICON_DISABLED,targetBuild->RVA_CAMERA_PHOTO_DISABLED,targetBuild->RVA_CAMERA_STOP,targetBuild->RVA_CAMERA_PREVIEW,targetBuild->RVA_CAMERA_PHOTO_FRAME,targetBuild->RVA_CAMERA_VIEW,targetBuild->RVA_CAMERA_CAPTURE,targetBuild->RVA_CAMERA_DEVELOP,targetBuild->RVA_CAMERA_REGION_PIXELS};
    const uint64_t sigs[]={targetBuild->SIG_CAMERA_DEVICES,targetBuild->SIG_CAMERA_UNLOCK,targetBuild->SIG_CAMERA_CONFIG,targetBuild->SIG_CAMERA_COPY,targetBuild->SIG_CAMERA_TAKE,targetBuild->SIG_CAMERA_GET,targetBuild->SIG_CAMERA_PLAY,targetBuild->SIG_CAMERA_ICON_DISABLED,targetBuild->SIG_CAMERA_PHOTO_DISABLED,targetBuild->SIG_CAMERA_STOP,targetBuild->SIG_CAMERA_PREVIEW,targetBuild->SIG_CAMERA_PHOTO_FRAME,targetBuild->SIG_CAMERA_VIEW,targetBuild->SIG_CAMERA_CAPTURE,targetBuild->SIG_CAMERA_DEVELOP,targetBuild->SIG_CAMERA_REGION_PIXELS};
    void* hooks[]={reinterpret_cast<void*>(devices),reinterpret_cast<void*>(unlock),reinterpret_cast<void*>(config),reinterpret_cast<void*>(copy),reinterpret_cast<void*>(take),reinterpret_cast<void*>(get),reinterpret_cast<void*>(play),reinterpret_cast<void*>(iconDisabled),reinterpret_cast<void*>(photoDisabled),reinterpret_cast<void*>(stop),reinterpret_cast<void*>(preview),reinterpret_cast<void*>(photoFrame),reinterpret_cast<void*>(view),reinterpret_cast<void*>(capture),reinterpret_cast<void*>(develop),reinterpret_cast<void*>(regionPixels)};
    void** backups[]={reinterpret_cast<void**>(&originalDevices),reinterpret_cast<void**>(&originalUnlock),reinterpret_cast<void**>(&originalConfig),reinterpret_cast<void**>(&originalCopy),reinterpret_cast<void**>(&originalTake),reinterpret_cast<void**>(&originalGet),reinterpret_cast<void**>(&originalPlay),reinterpret_cast<void**>(&originalIconDisabled),reinterpret_cast<void**>(&originalPhotoDisabled),reinterpret_cast<void**>(&originalStop),reinterpret_cast<void**>(&originalPreview),reinterpret_cast<void**>(&originalPhotoFrame),reinterpret_cast<void**>(&originalView),reinterpret_cast<void**>(&originalCapture),reinterpret_cast<void**>(&originalDevelop),reinterpret_cast<void**>(&originalRegionPixels)};
    constexpr int hookCount=sizeof(offsets)/sizeof(offsets[0]);
    for(int i=0;i<hookCount;i++)valid&=matchesTarget(reinterpret_cast<void*>(base+offsets[i]),sigs[i]);
    if(!valid){status=-1;return;}
    int count=0;for(;count<hookCount;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),hooks[count],backups[count])!=0)break;
    if(count!=hookCount){for(int i=count-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));status=-1;return;}
    installed=true;status=1;
    __android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Front camera hooks verified; OFF until user enables them and grants CAMERA");
}
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_cameraAuthorization(JNIEnv*,jclass,jboolean wanted,jboolean permission,jboolean foreground,jboolean rear,jboolean mirror){
    GameCamera::requestedRear.store(rear);
    GameCamera::requestedMirror.store(mirror);
    GameCamera::authorization.store((wanted?1u:0u)|(permission?2u:0u)|(foreground?4u:0u));
}
extern "C" JNIEXPORT jint JNICALL Java_io_oniimai_kanade_NativeBridge_cameraStatus(JNIEnv*,jclass){return GameCamera::owned&&(GameCamera::requestedRear.load()!=GameCamera::activeRear.load()||GameCamera::requestedMirror.load()!=GameCamera::activeMirror.load())?-7:GameCamera::status.load();}
extern "C" JNIEXPORT jboolean JNICALL Java_io_oniimai_kanade_NativeBridge_cameraFrameAllowed(JNIEnv*,jclass){return GameCamera::owned&&GameCamera::allowed();}
extern "C" JNIEXPORT jlong JNICALL Java_io_oniimai_kanade_NativeBridge_cameraFrameStart(JNIEnv*,jclass){
    using namespace GameCamera;
    if(!owned||!allowed())return 0;
    std::lock_guard<std::mutex> lock(frameMutex);
    cpuPixels.clear();cpuWidth=cpuHeight=0;frameSequence=takenSequence=0;
    frameToken=++nextFrameToken;return jlong(frameToken);
}
extern "C" JNIEXPORT void JNICALL Java_io_oniimai_kanade_NativeBridge_cameraFrameEnd(JNIEnv*,jclass,jlong token){
    using namespace GameCamera;std::lock_guard<std::mutex> lock(frameMutex);
    if(uint64_t(token)!=frameToken)return;
    for(auto& pixel:cpuPixels)pixel=0;
    cpuPixels.clear();frameToken=0;cpuWidth=cpuHeight=0;frameSequence=takenSequence=0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_io_oniimai_kanade_NativeBridge_cameraFrameYuv(JNIEnv* env,jclass,jlong token,jint w,jint h,
    jobject y,jint yr,jint yp,jobject u,jint ur,jint up,jobject v,jint vr,jint vp){
    using namespace GameCamera;
    if(!owned||!allowed()||!token||w<32||h<32||w>4096||h>4096||!y||!u||!v)return false;
    const jlong yc=env->GetDirectBufferCapacity(y),uc=env->GetDirectBufferCapacity(u),vc=env->GetDirectBufferCapacity(v);
    if(yc<=0||uc<=0||vc<=0)return false;
    CameraYuv::Plane py{static_cast<const uint8_t*>(env->GetDirectBufferAddress(y)),size_t(yc),yr,yp},
        pu{static_cast<const uint8_t*>(env->GetDirectBufferAddress(u)),size_t(uc),ur,up},
        pv{static_cast<const uint8_t*>(env->GetDirectBufferAddress(v)),size_t(vc),vr,vp};
    if(!CameraYuv::fits(py,w,h)||!CameraYuv::fits(pu,(w+1)/2,(h+1)/2)||!CameraYuv::fits(pv,(w+1)/2,(h+1)/2))return false;
    std::lock_guard<std::mutex> lock(frameMutex);
    if(uint64_t(token)!=frameToken||!owned||!allowed())return false;
    cpuPixels.resize(size_t(w)*h);
    if(!CameraYuv::convert(py,pu,pv,w,h,cpuPixels.data(),cpuPixels.size()))return false;
    cpuWidth=w;cpuHeight=h;++frameSequence;frameTime=std::chrono::steady_clock::now();return true;
}
