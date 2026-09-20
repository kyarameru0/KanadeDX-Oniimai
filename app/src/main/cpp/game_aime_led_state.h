#pragma once
#include <stdint.h>

// Mirrors KanadeDX's DebugLED Aime status palette and off timer. Status commands
// are captured at their real caller because AimeUnit's Android setters are RET.
struct GameAimeLedState {
    bool valid=false;
    int rgb=0;
    uint64_t writtenAt=0, expiresAt=0;
    void status(int value,uint64_t now){
        if(value<0||value>4)return;
        static constexpr int colors[]={0x000000,0xffffff,0x0000ff,0xffeb04,0xff0000};
        valid=true;rgb=colors[value];writtenAt=now;
        expiresAt=value>=2?now+3000:0;
    }
    int sample(uint64_t now)const{
        if(!valid)return -1;
        if(now<writtenAt||(expiresAt&&now>=expiresAt))return 0;
        return rgb;
    }
    void scanEnded(uint64_t now){
        // Cancellation/idle should not leave the persistent scanning white on;
        // a timed success/error indication remains visible until its deadline.
        if(valid&&rgb==0xffffff&&!expiresAt)status(0,now);
    }
    static int resultStatus(bool hasError,int category){
        if(!hasError)return 2;
        if(category==1)return -1; // Warning category leaves the game's prior LED.
        return category==2?3:4;
    }
};
