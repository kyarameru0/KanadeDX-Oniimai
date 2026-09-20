#pragma once
#include <stdint.h>
// Compatibility fingerprint, not a cryptographic security boundary.
// Store a digest rather than redistributing the target game's instruction bytes.
static inline uint64_t targetFingerprint16(const void* code) {
    const auto* bytes = static_cast<const unsigned char*>(code);
    uint64_t value = UINT64_C(0xcbf29ce484222325);
    for (unsigned i = 0; i < 16; ++i) value = (value ^ bytes[i]) * UINT64_C(0x100000001b3);
    return value;
}
static inline bool matchesTarget(const void* code, uint64_t expected) {
    return targetFingerprint16(code) == expected;
}
