package io.oniimai.kanade;

/** JNI input contract: ring 0..7, P1 8, cabinet TEST 9, SERVICE 10. */
final class Io4Input {
    static final int TEST=1<<9, SERVICE=1<<10;
    static int mask(byte[] report,int player) {
        boolean[] pressed=Protocol.io4(report,player);
        int mask=0;for(int i=0;i<8;i++)if(pressed[i])mask|=1<<i;
        // System switches belong to bank 0 even when the ring uses player 2.
        // Offsets include the report ID; do not use the older Assistant HID
        // tester's byte offsets for this 64-byte SEGA IO4 report.
        int cabinet=Protocol.u16(report,29);
        if((cabinet&2)!=0)mask|=256;
        if((cabinet&(1<<9))!=0)mask|=TEST;
        if((cabinet&(1<<6))!=0)mask|=SERVICE;
        return mask;
    }
}
