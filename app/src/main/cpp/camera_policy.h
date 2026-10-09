#pragma once

// Pure policy shared by startup/menu hooks and host regression tests.
namespace CameraPolicy {
static inline bool authorized(unsigned bits) { return bits == 7; }
static inline bool begin(unsigned bits, bool verified, bool skipPhotoCamera) {
    return authorized(bits) && verified && !skipPhotoCamera;
}
static inline bool menuDisabled(bool original, unsigned bits, bool owned, bool preflightPassed) {
    // This only enables a setting; it never changes the user's photo selection.
    return authorized(bits) && owned && preflightPassed ? false : original;
}
}
