#pragma once
#include <stdint.h>

// A short IL2CPP RET stub cannot accommodate a normal 16-byte inline hook.
// Encode one aligned direct B only; its destination must be a nearby thunk.
namespace Arm64StubBranch {
static constexpr uint32_t RET=0xd65f03c0;
static bool encode(uintptr_t source,uintptr_t target,uint32_t& instruction){
    if((source|target)&3)return false;
    uint64_t distance=target>=source?target-source:source-target;
    if(target>=source?distance>=0x08000000ull:distance>0x08000000ull)return false;
    int64_t words=target>=source?int64_t(distance/4):-int64_t(distance/4);
    instruction=0x14000000u|(uint32_t(words)&0x03ffffffu);return true;
}
struct Thunk {uint32_t load=0x58000050,branch=0xd61f0200;uint64_t target;};
static_assert(sizeof(Thunk)==16,"AArch64 LDR x16; BR x16; address literal");
}
