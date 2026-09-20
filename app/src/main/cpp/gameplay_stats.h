#pragma once
#include <string>
#include <stdio.h>
#include <math.h>
#include "stats_state.h"
#include "game_album.h"

// All managed reads happen synchronously while a verified original method owns
// its arguments. JNI only serializes native copies; no GC handles or game calls
// escape to the Android UI / serial worker threads.
namespace GameplayStats {
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static std::atomic<int> status{0};
static StatsLifecycle lifecycle;
static uint64_t updated=0,pendingSince=0;
static int musicId=-1;
static unsigned player=0;
struct Snapshot {
    bool valid=false,hasAchievement=false;
    double achievement=0,seconds=-1,length=-1;
    uint32_t combo=0,dx=0,critical=0,perfect=0,great=0,good=0,miss=0,fast=0,late=0;
    std::string title,artist,designer,level;
    uint32_t levelColor=0;
};
static Snapshot snapshot;
struct Music {int id=-1;std::string title,artist;};
static Music musicCache[64];
static unsigned musicSlot=0;
template<class T> static T field(const void* object,size_t offset){
    T value{};if(object)memcpy(&value,static_cast<const char*>(object)+offset,sizeof(T));return value;
}
// ASCII JSON escapes preserve UTF-16 surrogate pairs, including emoji, without
// passing ordinary UTF-8 to JNI's different Modified UTF-8 decoder.
static std::string managedText(const void* text){
    if(!text)return {};
    int length=field<int>(text,0x10);if(length<=0||length>4096)return {};
    if(length>256)length=256;
    std::string out="\"";char escaped[7];
    for(int i=0;i<length;i++){
        uint16_t c=field<uint16_t>(text,0x14+size_t(i)*2);
        if(c>=0x20&&c<0x7f&&c!='"'&&c!='\\')out+=char(c);
        else{snprintf(escaped,sizeof(escaped),"\\u%04x",unsigned(c));out+=escaped;}
    }
    out+='"';return out;
}
static std::string stringIdText(const void* id){return managedText(field<void*>(id,0x18));}
static void attachMusic(){
    for(const auto& m:musicCache)if(m.id==musicId){snapshot.title=m.title;snapshot.artist=m.artist;break;}
}
static void clear(){snapshot=Snapshot{};musicId=-1;updated=0;pendingSince=0;}
static void (*gameStartOriginal)(void*,const void*);
static void (*gameReleaseOriginal)(void*,const void*);
static void (*resultStartOriginal)(void*,const void*);
static void (*resultReleaseOriginal)(void*,const void*);
static void (*scoreOriginal)(void*,const void*);
static void (*utageOriginal)(void*,void*,const void*);
static void (*finishOriginal)(void*,void*,const void*);
static void* (*musicOriginal)(void*,int,const void*);
static void (*notesOriginal)(void*,const void*);
static float (*currentMsec)(const void*);
static unsigned selectedPlayer(){
    pthread_mutex_lock(&stateLock);unsigned p=unsigned(state.player);pthread_mutex_unlock(&stateLock);return p;
}
static bool enabled(){return status.load()==511;}
static void capture(void* score,bool final){
    if(!score||!field<uint8_t>(score,0x10))return;
    pthread_mutex_lock(&lock);
    if((!lifecycle.playing()&&!lifecycle.result)||field<unsigned>(score,0x64)!=player){pthread_mutex_unlock(&lock);return;}
    const int id=field<int>(score,0x94);
    if(id!=musicId){snapshot=Snapshot{};musicId=id;}
    snapshot.valid=true;
    // True* retain the separate Critical/Perfect counts even when the game's
    // presentation option combines Critical into Perfect.
    snapshot.critical=field<uint32_t>(score,0x13c);
    snapshot.perfect=field<uint32_t>(score,0x140);
    snapshot.great=field<uint32_t>(score,0x130);
    snapshot.good=field<uint32_t>(score,0x134);
    snapshot.miss=field<uint32_t>(score,0x138);
    snapshot.combo=field<uint32_t>(score,0x194);
    snapshot.fast=field<uint32_t>(score,0x1b8);
    snapshot.late=field<uint32_t>(score,0x1bc);
    snapshot.dx=field<uint32_t>(score,0x1ec); // Earned DX score, not hypothetical remaining score.
    snapshot.hasAchievement=statsDecimal(field<uint32_t>(score,0x1d8),field<uint32_t>(score,0x1dc),
        field<uint32_t>(score,0x1e0),field<uint32_t>(score,0x1e4),snapshot.achievement);
    void* notes=field<void*>(score,0x98);
    if(notes){
        int level=field<int>(notes,0x2c); // MusicLevelID enum, includes explicit '+' variants.
        if(level>0&&level<25){
            char text[16];snprintf(text,sizeof(text),"\"%d%s\"",level<8?level:7+(level-7)/2,level>=8&&!(level&1)?"+":"");
            snapshot.level=text;
        }
        snapshot.designer=stringIdText(field<void*>(notes,0x20));
    }
    int difficulty=field<int>(score,0xa0);
    const uint32_t colors[]={0xff5bb85du,0xffefb92au,0xffe85e73u,0xff9864c9u,0xffb7a0d5u,0xffea72aeu};
    snapshot.levelColor=difficulty>=0&&difficulty<6?colors[difficulty]:0;
    attachMusic();lifecycle.capture(final);updated=nowMs();pthread_mutex_unlock(&lock);
}
static void gameStartHook(void* self,const void* method){
    if(enabled()){
        unsigned p=selectedPlayer();pthread_mutex_lock(&lock);clear();player=p;lifecycle.startGame(uintptr_t(self));pthread_mutex_unlock(&lock);
    }
    gameStartOriginal(self,method);
    // Finish one-time GPU work during game preparation, before timed note play.
    if(enabled())GameAlbum::prewarm(player,nowMs());
}
static void gameReleaseHook(void* self,const void* method){
    // Keep the exact finished snapshot through the loading transition into Result.
    if(enabled()){
        pthread_mutex_lock(&lock);
        if(lifecycle.releaseGame(uintptr_t(self))){
            if(!lifecycle.available)clear();else if(!lifecycle.result)pendingSince=nowMs();
        }
        pthread_mutex_unlock(&lock);
    }
    gameReleaseOriginal(self,method);
}
static void resultStartHook(void* self,const void* method){
    if(enabled()){pthread_mutex_lock(&lock);lifecycle.startResult(uintptr_t(self));pendingSince=0;pthread_mutex_unlock(&lock);}
    resultStartOriginal(self,method);
    if(enabled()){
        // Result owns its finalized score array. Copy while that owning object is live.
        void* array=field<void*>(self,0x70);uintptr_t count=field<uintptr_t>(array,0x18);
        if(array&&count>player&&count<=4)capture(field<void*>(array,0x20+player*sizeof(void*)),true);
        pthread_mutex_lock(&lock);int albumMusic=snapshot.valid?musicId:-1;pthread_mutex_unlock(&lock);
        if(albumMusic>=0)GameAlbum::capture(albumMusic,nowMs());
    }
}
static void resultReleaseHook(void* self,const void* method){
    if(enabled()){pthread_mutex_lock(&lock);if(lifecycle.releaseResult(uintptr_t(self)))clear();pthread_mutex_unlock(&lock);}
    resultReleaseOriginal(self,method);
}
static void scoreHook(void* self,const void* method){scoreOriginal(self,method);if(enabled())capture(self,false);}
static void utageHook(void* self,void* other,const void* method){utageOriginal(self,other,method);if(enabled())capture(self,false);}
static void finishHook(void* self,void* other,const void* method){finishOriginal(self,other,method);if(enabled())capture(self,true);}
static void* musicHook(void* self,int id,const void* method){
    void* value=musicOriginal(self,id,method);
    if(enabled()&&value&&id>=0){
        Music copy;copy.id=id;copy.title=stringIdText(field<void*>(value,0x28));copy.artist=stringIdText(field<void*>(value,0x40));
        pthread_mutex_lock(&lock);
        unsigned slot=64;for(unsigned i=0;i<64;i++)if(musicCache[i].id==id){slot=i;break;}
        if(slot==64)slot=(musicSlot++)%64;
        musicCache[slot]=copy;if(id==musicId)attachMusic();pthread_mutex_unlock(&lock);
    }
    return value;
}
static void notesHook(void* self,const void* method){
    notesOriginal(self,method);
    if(!enabled()||!self||!field<uint8_t>(self,0x14))return;
    // NotesTime frames are 60 Hz (getPlayProgress/getPlayFinalMsec in this build).
    // Use the chart's actual play-end frame, including its chart-defined outro.
    double length=field<float>(self,0xa8)/60.0;
    double seconds=currentMsec(nullptr)/1000.0;
    int albumMusic=-1;
    pthread_mutex_lock(&lock);
    if(lifecycle.playing()&&field<unsigned>(self,0x10)==player&&field<int>(self,0x1c)==musicId){
        if(std::isfinite(length)&&length>0&&length<86400)snapshot.length=length;
        if(std::isfinite(seconds)&&seconds>-60&&seconds<86400)snapshot.seconds=seconds<0?0:seconds;
        updated=nowMs();
        if(snapshot.valid&&seconds<=0)albumMusic=musicId;
    }
    pthread_mutex_unlock(&lock);
    // Retry only in the pre-play countdown. During timed play, the already
    // captured image is served from cache without GPU readback or PNG encoding.
    if(albumMusic>=0)GameAlbum::capture(albumMusic,nowMs());
}
static void install(uintptr_t base,void* library){
    if(status.load()!=0)return;
    const uintptr_t offsets[]={targetBuild->RVA_GAME_START,targetBuild->RVA_GAME_RELEASE,targetBuild->RVA_RESULT_START,targetBuild->RVA_RESULT_RELEASE,targetBuild->RVA_SCORE_UPDATE,targetBuild->RVA_SCORE_UTAGE,targetBuild->RVA_SCORE_FINISH,targetBuild->RVA_MUSIC_GET,targetBuild->RVA_NOTES_UPDATE,targetBuild->RVA_CURRENT_MSEC};
    const uint64_t signatures[]={targetBuild->SIG_GAME_START,targetBuild->SIG_GAME_RELEASE,targetBuild->SIG_RESULT_START,targetBuild->SIG_RESULT_RELEASE,targetBuild->SIG_SCORE_UPDATE,targetBuild->SIG_SCORE_UTAGE,targetBuild->SIG_SCORE_FINISH,targetBuild->SIG_MUSIC_GET,targetBuild->SIG_NOTES_UPDATE,targetBuild->SIG_CURRENT_MSEC};
    for(int i=0;i<10;i++)if(!matchesTarget(reinterpret_cast<void*>(base+offsets[i]),signatures[i])){status=-4;return;}
    currentMsec=reinterpret_cast<float(*)(const void*)>(base+targetBuild->RVA_CURRENT_MSEC);
    void* hooks[]={reinterpret_cast<void*>(gameStartHook),reinterpret_cast<void*>(gameReleaseHook),reinterpret_cast<void*>(resultStartHook),reinterpret_cast<void*>(resultReleaseHook),reinterpret_cast<void*>(scoreHook),reinterpret_cast<void*>(utageHook),reinterpret_cast<void*>(finishHook),reinterpret_cast<void*>(musicHook),reinterpret_cast<void*>(notesHook)};
    void** originals[]={reinterpret_cast<void**>(&gameStartOriginal),reinterpret_cast<void**>(&gameReleaseOriginal),reinterpret_cast<void**>(&resultStartOriginal),reinterpret_cast<void**>(&resultReleaseOriginal),reinterpret_cast<void**>(&scoreOriginal),reinterpret_cast<void**>(&utageOriginal),reinterpret_cast<void**>(&finishOriginal),reinterpret_cast<void**>(&musicOriginal),reinterpret_cast<void**>(&notesOriginal)};
    int count=0;for(;count<9;count++)if(hookFunction(reinterpret_cast<void*>(base+offsets[count]),hooks[count],originals[count])!=0)break;
    if(count==9){GameAlbum::install(base,library);status=511;__android_log_print(ANDROID_LOG_INFO,"OniimaiKanade","Verified gameplay statistics hooks installed: %s",targetBuild->name);}
    else{for(int i=count-1;i>=0;i--)unhookFunction(reinterpret_cast<void*>(base+offsets[i]));status=-5;}
}
static std::string json(){
    pthread_mutex_lock(&lock);
    // Finished gameplay without a Result process (abort / retry) cannot linger forever.
    if(pendingSince&&nowMs()-pendingSince>30000){lifecycle=StatsLifecycle{};clear();}
    std::string out="{\"game\":";out+=(enabled()&&snapshot.valid&&lifecycle.available)?"true":"false";
    out+=",\"result\":";out+=lifecycle.result?"true":"false";
    out+=",\"scene\":\"";out+=lifecycle.result?"Result":lifecycle.playing()?"Game":pendingSince?"Loading":"Idle";out+='"';
    char number[128];snprintf(number,sizeof(number),",\"status\":%d,\"updatedMs\":%llu",status.load(),static_cast<unsigned long long>(updated));out+=number;
    if(enabled()&&snapshot.valid&&lifecycle.available){
        const auto& s=snapshot;
        auto text=[&out](const char* key,const std::string& v){if(!v.empty()){out+=",\"";out+=key;out+="\":";out+=v;}};
        auto integer=[&out,&number](const char* key,uint32_t v){snprintf(number,sizeof(number),",\"%s\":%u",key,v);out+=number;};
        auto real=[&out,&number](const char* key,double v){snprintf(number,sizeof(number),",\"%s\":%.6f",key,v);out+=number;};
        text("title",s.title);text("artist",s.artist);text("designer",s.designer);text("level",s.level);
        if(musicId>=0){integer("musicId",uint32_t(musicId));uint64_t revision=GameAlbum::revision(musicId);
            if(revision){snprintf(number,sizeof(number),",\"albumRevision\":%llu",static_cast<unsigned long long>(revision));out+=number;}}
        if(s.levelColor)integer("levelColor",s.levelColor);
        if(s.hasAchievement)real("achievement",s.achievement);
        if(s.seconds>=0)real("seconds",s.seconds);if(s.length>0)real("length",s.length);
        integer("combo",s.combo);integer("dx",s.dx);integer("critical",s.critical);integer("perfect",s.perfect);
        integer("great",s.great);integer("good",s.good);integer("miss",s.miss);integer("fast",s.fast);integer("late",s.late);
    }
    out+='}';pthread_mutex_unlock(&lock);return out;
}
} // namespace GameplayStats
