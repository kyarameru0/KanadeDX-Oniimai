package io.oniimai.kanade;

/**
 * Ring-button lighting while the external welcome screen is shown. One function of mode, time and ring
 * position feeds both the USB LED worker (at the eight buttons) and the on-screen light ring (everywhere in
 * between), so the cabinet buttons and the screen always agree.
 *
 * Set on the UI thread, read on the LED worker. Colours are 0xRRGGBB in game button order
 * (index 0 is button 1, clockwise); brightness, rotation and reverse are applied later by LedFrames.
 */
final class LobbyLights {
    static final int OFF=0,LOADING=1,READY=2,COMPLETE=3;
    /** The Start button's blue (Miuix night accent), and white for acknowledgements. */
    static final int ACCENT=0x277af7,FLASH=0xffffff;
    static final long STEP_MS=140,WAVE_MS=2400,FLASH_MS=600,REPEAT_MS=2000;
    /**
     * Hand-off to the game: white fills the ring from button 1 (one button every SWEEP_STEP_MS, each
     * rising over SWEEP_RISE_MS), holds until HOLD_END_MS, then fades out by COMPLETE_MS.
     */
    static final long SWEEP_STEP_MS=40,SWEEP_RISE_MS=120,HOLD_END_MS=560,COMPLETE_MS=1100;
    /** Loading keeps every button faintly lit so the ring stays visible between passes. */
    static final float FLOOR=0.06f;

    private volatile int mode=OFF,previous=OFF;
    private volatile long since,previousSince,pressedAt=-1;

    int mode(){return mode;}
    /**
     * A drop from READY to LOADING is the game accepting Start, so it gets the same flash as the on-screen
     * button, unless that button was just pressed and has flashed already. A running hand-off is not cut
     * short when the welcome screen closes; it ends by itself after COMPLETE_MS.
     */
    synchronized void mode(int value,long now){
        if(value==mode)return;
        if(mode==COMPLETE&&value==OFF&&now-since<COMPLETE_MS)return;
        if(mode==READY&&value==LOADING&&(pressedAt<0||now-pressedAt>REPEAT_MS))pressedAt=now;
        if(value==OFF||value==COMPLETE)pressedAt=-1;
        previous=mode;previousSince=since;since=now;mode=value;
    }
    void pressed(long now){if(mode==LOADING||mode==READY)pressedAt=now;}
    /** When the last start acknowledgement flashed (screen or controller), or -1; the screen echoes it as a ripple. */
    long flashedAt(){return pressedAt;}
    /** The game is on its way to the monitor: play the white hand-off over whatever the ring shows now. */
    void complete(long now){if(mode==LOADING||mode==READY)mode(COMPLETE,now);}

    /** The eight button colours at {@code now}, or null when the game's own lighting applies. */
    int[] frame(long now){
        if(!active(now))return null;
        int[] colors=new int[8];
        for(int i=0;i<8;i++)colors[i]=at(now,i);
        return colors;
    }
    /** Whether the lobby pattern currently replaces the game's lighting. */
    boolean active(long now){int current=mode;return current!=OFF&&!(current==COMPLETE&&now-since>=COMPLETE_MS);}
    /**
     * The colour at a ring position measured in buttons: 0 is button 1, 1 is button 2, 7.5 is halfway from
     * button 8 back to button 1. Black when the pattern is not active.
     */
    int at(long now,double position){
        int current=mode;if(!active(now))return 0;
        long elapsed=Math.max(0,now-since);
        double p=((position%8)+8)%8;
        if(current==COMPLETE){
            // Continue the pattern that was showing, so the sweep starts from what the player sees.
            int color=scale(ACCENT,level(previous,Math.max(0,now-previousSince),p));
            color=mix(color,FLASH,ease((float)((elapsed-p*SWEEP_STEP_MS)/SWEEP_RISE_MS)));
            if(elapsed>HOLD_END_MS)color=mix(color,0,ease((elapsed-HOLD_END_MS)/(float)(COMPLETE_MS-HOLD_END_MS)));
            return color;
        }
        int color=scale(ACCENT,level(current,elapsed,p));
        long flash=pressedAt<0?-1:now-pressedAt;
        if(flash>=0&&flash<FLASH_MS)color=mix(FLASH,color,(float)flash/FLASH_MS);
        return color;
    }
    private static float level(int mode,long elapsed,double i){
        if(mode==READY){
            // Every button can start the game, so all stay lit; a slow wave runs clockwise.
            double phase=(double)elapsed/WAVE_MS-i/8.0;
            return (float)(0.55+0.45*(0.5+0.5*Math.cos(2*Math.PI*phase)));
        }
        if(mode==LOADING){
            // One light circles clockwise with a short tail while the game prepares.
            double behind=(((double)elapsed/STEP_MS-i)%8+8)%8;
            return behind>=3?FLOOR:FLOOR+(1-FLOOR)*(float)Math.pow(1-behind/3,2);
        }
        return 0;
    }
    /** A motionless stand-in for the screen when system animations are off; the cabinet LEDs still animate. */
    int[] still(){
        int current=mode;if(current==OFF)return null;
        int[] colors=new int[8];
        java.util.Arrays.fill(colors,current==COMPLETE?FLASH:scale(ACCENT,current==READY?0.8f:0.3f));
        return colors;
    }
    private static float ease(float t){t=Math.max(0,Math.min(1,t));float u=1-t;return 1-u*u*u;}
    static int scale(int rgb,float level){
        level=Math.max(0,Math.min(1,level));
        return Math.round((rgb>>>16&255)*level)<<16|Math.round((rgb>>>8&255)*level)<<8|Math.round((rgb&255)*level);
    }
    /** Linear blend: 0 gives {@code from}, 1 gives {@code to}. */
    static int mix(int from,int to,float amount){
        amount=Math.max(0,Math.min(1,amount));int result=0;
        for(int shift=16;shift>=0;shift-=8){int a=from>>>shift&255,b=to>>>shift&255;result|=Math.round(a+(b-a)*amount)<<shift;}
        return result;
    }
}
