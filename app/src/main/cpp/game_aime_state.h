#pragma once
#include <stdint.h>

// Primitive-only handoff: the USB/JNI thread never touches managed game objects.
// Callers serialize access. A scan is a short-lived authorization, not a backlog.
struct GameAimeState {
    bool enabled=false, scanning=false, window=false, used=false, pending=false, physicalCommitted=false;
    uint64_t generation=0, queuedGeneration=0, queuedAt=0, lastFrame=0;
    int pendingError=0;
    char digits[21]{};
    static constexpr uint64_t FRAME_TTL=500, QUEUE_TTL=750;
    void clear(){for(char& c:digits)c=0;pending=false;pendingError=0;queuedAt=0;queuedGeneration=0;}
    void enable(bool value){enabled=value;if(!value){clear();window=false;}}
    void start(){++generation;used=false;physicalCommitted=false;scanning=false;window=false;clear();}
    void consumed(){used=true;window=false;clear();}
    bool acceptVirtual(){
        // Only suppress virtual input after this adapter committed a physical
        // card. Virtual-only calls preserve the original game's behavior.
        if(physicalCommitted)return false;
        consumed();return true;
    }
    void update(bool active,uint64_t now){
        lastFrame=now;scanning=active;window=enabled&&active&&!used;
        if(!window)clear();
    }
    int status(uint64_t now) const {
        if(!enabled||!scanning||now<lastFrame||now-lastFrame>FRAME_TTL)return 0;
        return used||pending||pendingError?2:window?1:0;
    }
    bool submit(const unsigned char* bcd,unsigned length,uint64_t now){
        if(!bcd||length!=10||status(now)!=1)return false;
        bool nonzero=false;
        for(unsigned i=0;i<10;i++){
            if((bcd[i]>>4)>9||(bcd[i]&15)>9)return false;
            nonzero|=bcd[i]!=0;
        }
        if(!nonzero)return false;
        for(unsigned i=0;i<10;i++){digits[i*2]=char('0'+(bcd[i]>>4));digits[i*2+1]=char('0'+(bcd[i]&15));}
        digits[20]=0;pending=true;queuedAt=now;queuedGeneration=generation;return true;
    }
    bool take(char* result,uint64_t now){
        if(!pending)return false;
        if(!enabled||!window||used||queuedGeneration!=generation||now<queuedAt||now-queuedAt>QUEUE_TTL){clear();return false;}
        for(unsigned i=0;i<21;i++)result[i]=digits[i];
        consumed();physicalCommitted=true;return true;
    }
    bool submitForScan(const unsigned char* bcd,unsigned length,uint64_t scanGeneration,uint64_t now){
        return generation!=0&&scanGeneration==generation&&submit(bcd,length,now);
    }
    bool reportError(int issue,uint64_t scanGeneration,uint64_t now){
        // Zero means no card. A transport callback carries the generation at
        // request start; it must never fail a later scan or an accepted card.
        if(issue<1||issue>5||generation==0||scanGeneration!=generation||status(now)!=1)return false;
        pendingError=issue;queuedAt=now;queuedGeneration=generation;return true;
    }
    int takeError(uint64_t now){
        if(!pendingError)return 0;
        if(!enabled||!window||used||queuedGeneration!=generation||now<queuedAt||now-queuedAt>QUEUE_TTL){clear();return 0;}
        int issue=pendingError;consumed();physicalCommitted=true;return issue;
    }
};
