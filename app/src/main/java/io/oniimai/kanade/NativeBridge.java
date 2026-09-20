package io.oniimai.kanade;
final class NativeBridge {
    static native int initialize();
    static native void submit(long touch,int buttons,int player,boolean active);
    static native long[] stats();
    /** Copied game statistics as JSON; missing values are omitted. No game calls on this thread. */
    static native String gameplayStats();
    static native int[] ledSnapshot();
    /** Negative: unavailable; 0: no scan window; 1: ready; 2: submitted this scan. */
    static native int aimeStatus();
    /** Increments only when the game starts a new scan; -1 if native hooks are unavailable. */
    static native long aimeGeneration();
    /** Game reader LED 0xRRGGBB, or -1 before a verified game LED command. */
    static native int aimeLed();
    /** Validated physical card only. Queues ten BCD bytes for the current game scan. */
    static native boolean submitAime(byte[] accessCode,long scanGeneration);
    /** Read failure 1..5 for the request's actual game scan. No managed calls on this thread. */
    static native void aimeError(int issue,long scanGeneration);
    /** Publishes lifecycle/UI authorization. False immediately erases queued card data. */
    static native void aimeEnabled(boolean enabled);
    static native void clearAime();
    /** Publishes intent only; Unity's Update thread performs and restores UI changes. */
    private static native void externalUiHidden(boolean active);
    static void externalDisplay(boolean active){
        try{externalUiHidden(active);}catch(UnsatisfiedLinkError ignored){/* External video works even when native hooks are unavailable. */}
    }
}
