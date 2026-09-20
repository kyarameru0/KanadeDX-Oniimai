package io.oniimai.kanade;

/** Ring buttons occupy bits 0..7; the independent P1 / Start switch occupies bit 8. */
final class Io4Input {
    static int mask(byte[] report,int player) {
        boolean[] pressed=Protocol.io4(report,player);
        int mask=0;for(int i=0;i<8;i++)if(pressed[i])mask|=1<<i;
        // IO4 payload byte28 bit1 is the active-high P1 function switch, regardless of ring bank.
        if((Protocol.u(report[29])&2)!=0)mask|=256;
        return mask;
    }
}
