#pragma once
#include <stdint.h>

// Unity Color uses s0-s3 on ARM64; Color32 is packed RGBA in a general register.
struct LedColor {float r,g,b,a;};
struct LedFadeStep {uint32_t from,to;int64_t duration;int32_t next;};
static_assert(sizeof(LedColor)==16,"Unity Color ABI");
static_assert(sizeof(LedFadeStep)==24,"IL2CPP fade element ABI");
struct LedTrack {
    static constexpr int MAX_STEPS=32;
    LedColor base{},fixed{};
    LedFadeStep steps[MAX_STEPS]{};
    int count=0,index=-1,fetChannel=0;
    bool fet=false;
    uint64_t began=0;
    static float unit(float v){return !(v>0)?0:(v>=1?1:v);}
    static LedColor unpack(uint32_t rgba,bool fet=false,int channel=0){
        if(fet)return {1,1,1,((rgba>>(channel*8))&255)/255.f};
        return {(rgba&255)/255.f,((rgba>>8)&255)/255.f,((rgba>>16)&255)/255.f,((rgba>>24)&255)/255.f};
    }
    static uint32_t pack(LedColor c){return uint32_t(unit(c.r)*255+.5f)|(uint32_t(unit(c.g)*255+.5f)<<8)|(uint32_t(unit(c.b)*255+.5f)<<16)|(uint32_t(unit(c.a)*255+.5f)<<24);}
    static uint32_t rgb(LedColor c){
        float a=unit(c.a);return (uint32_t(unit(c.r)*a*255+.5f)<<16)|(uint32_t(unit(c.g)*a*255+.5f)<<8)|uint32_t(unit(c.b)*a*255+.5f);
    }
    void set(LedColor color){base=fixed=color;index=-1;}
    void animate(const LedFadeStep* data,int size,bool ring,uint64_t now,int channel=0){
        if(!data||size<=0||size>MAX_STEPS)return;
        for(int i=0;i<size;i++)steps[i]=data[i];
        count=size;fet=ring;fetChannel=channel;index=0;began=now;fixed=unpack(steps[0].from,fet,fetChannel);
    }
    void pressed(LedColor color,uint64_t now){
        LedFadeStep flash[2]={{pack(color),pack(color),500,1},{pack(base),pack(base),0,-1}};
        animate(flash,2,false,now);
    }
    LedColor sample(uint64_t now){
        if(index<0||index>=count)return fixed;
        if(now<began)began=now;
        // Bounded work even for malformed zero-duration loops or a long suspension.
        for(int transition=0;transition<64;transition++){
            const LedFadeStep& step=steps[index];uint64_t elapsed=now-began;
            if(step.duration>0&&elapsed<uint64_t(step.duration)){
                LedColor a=unpack(step.from,fet,fetChannel),b=unpack(step.to,fet,fetChannel);float t=elapsed/double(step.duration);
                fixed={a.r+(b.r-a.r)*t,a.g+(b.g-a.g)*t,a.b+(b.b-a.b)*t,a.a+(b.a-a.a)*t};return fixed;
            }
            fixed=unpack(step.to,fet,fetChannel);
            if(step.next<0||step.next>=count){index=-1;return fixed;}
            if(step.duration>0)began+=uint64_t(step.duration);
            index=step.next;
        }
        began=now;return fixed;
    }
};
struct LedState {
    static constexpr int BOARD_COUNT=11,BILLBOARD=11,COUNT=12;
    LedTrack tracks[COUNT]{};
    uint32_t colors[COUNT]{},seen=0,events=0;
    static uint32_t rgb(LedColor color){return LedTrack::rgb(color);}
    void set(int index,LedColor color){if(index<0||index>=COUNT)return;tracks[index].set(color);seen|=1u<<index;events++;}
    void all(LedColor color){for(int i=0;i<8;i++)set(i,color);}
    void allOff(){for(int i=0;i<BOARD_COUNT;i++)set(i,{0,0,0,1});}
    // Color32 R/G/B carries three independent white PWM channels, not RGB.
    void allFet(uint32_t rgba){for(int i=0;i<3;i++)set(8+i,LedTrack::unpack(rgba,true,i));}
    void setFet(int index,uint8_t value){if(index<0||index>=BOARD_COUNT)return;float v=value/255.f;set(index,{v,v,v,1});}
    void pressed(int index,LedColor color,uint64_t now){if(index<0||index>7)return;tracks[index].pressed(color,now);seen|=1u<<index;events++;}
    void fade(const LedFadeStep* data,int count,bool ring,uint64_t now){
        if(!data||count<=0||count>LedTrack::MAX_STEPS)return;
        for(int i=ring?8:0;i<(ring?BOARD_COUNT:8);i++){tracks[i].animate(data,count,ring,now,ring?i-8:0);seen|=1u<<i;events++;}
    }
    void sample(uint64_t now){for(int i=0;i<COUNT;i++)colors[i]=rgb(tracks[i].sample(now));}
};
