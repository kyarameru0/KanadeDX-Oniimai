#pragma once
#include <stdint.h>

// Caller serializes access. Kept independent of Android for host regression tests.
struct InputState {
    uint64_t touch = 0, pendingTouch = 0, updated = 0;
    uint32_t buttons = 0, pendingButtons = 0, frameButtons = 0, previousButtons = 0, rising = 0;
    int player = 0, framePlayer = 0;
    int touchFlush[2] = {0,0};
    bool active = false;
    void submit(uint64_t t, uint32_t b, int p, bool enabled, uint64_t now) {
        if (!enabled || p != player) reset();
        player = p; active = enabled; updated = now;
        t &= ((uint64_t{1} << 34) - 1); b &= 511; // Eight ring buttons plus Select/P1.
        if (!active) { t = 0; b = 0; }
        pendingTouch |= t & ~touch; pendingButtons |= b & ~buttons;
        touch = t; buttons = b;
    }
    void reset() {
        touch = pendingTouch = 0;
        buttons = pendingButtons = frameButtons = previousButtons = rising = 0;
        active = false;
    }
    void expire(uint64_t now) { if (active && (now < updated || now - updated > 500)) reset(); }
    void frame(uint64_t now) {
        expire(now);
        uint32_t next = active ? buttons | pendingButtons : 0;
        rising = next & ~previousButtons;
        previousButtons = frameButtons = next; framePlayer = player; pendingButtons = 0;
    }
    uint64_t takeTouch(int p, uint64_t now) {
        expire(now);
        if (!active || p != player) return 0;
        uint64_t result = touch | pendingTouch; pendingTouch = 0; return result;
    }
    bool touchUpdate(int p, bool realUpdate) {
        if(p < 0 || p > 1) return realUpdate;
        if(active && p == player) { touchFlush[p] = 3; return true; }
        if(touchFlush[p] > 0) { --touchFlush[p]; return true; }
        return realUpdate;
    }
    bool button(int id, bool edge, uint64_t now) {
        expire(now);
        int bit = id - (framePlayer == 0 ? 2 : 11);
        return active && bit >= 0 && bit < 9 && (((edge ? rising : frameButtons) >> bit) & 1);
    }
};
