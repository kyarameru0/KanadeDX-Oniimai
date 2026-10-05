package io.oniimai.kanade;

/**
 * The controller's lights while first-run setup is open, following its step. One function of the setup
 * state and time ({@link #color}) feeds the USB LED worker (the eight ring buttons), the ceiling light (their
 * average) and setup's page on the controller's screen, so the buttons and both screens always agree.
 *
 * Set on the UI thread, read on the LED workers. While open it replaces the welcome screen's and the game's
 * button colours; a colour test still comes first. Colours are 0xRRGGBB in game button order (index 0 is
 * button 1, clockwise); brightness, rotation and reverse are applied later by LedFrames.
 */
final class SetupLights {
    /**
     * Setup's steps, in order: the language first, so every page reads in it; the connection; then the
     * built-in screen's direction, so the input check and lighting read upright on the controller. The five
     * numbered pages are FIRST (LANGUAGE) to LAST (LIGHTING).
     */
    static final int WELCOME=-1,LANGUAGE=0,CONNECT=1,SCREEN=2,INPUT=3,LIGHTING=4,DONE=5,FIRST=LANGUAGE,LAST=LIGHTING;
    static final int ACCENT=0x277af7,WHITE=0xffffff,AMBER=0xf2b45a;
    /** The lighting step's preview: pink, purple and cyan round the ring, as on the phone. */
    static final int[] PREVIEW={0xff6fb5,0xb07bff,0x5bc8ff};
    /** A white fill from button 1 (one button every STEP_MS, each rising over RISE_MS), held, then settling to the accent. */
    static final long STEP_MS=40,RISE_MS=120,HOLD_MS=560,SETTLE_MS=1100;
    /** One turn of the light that looks for the controller, the welcome wave, and the preview's opening run. */
    static final long TRAVEL_MS=2400,WAVE_MS=2400,RUN_MS=2400;

    private volatile boolean open;
    private volatile int step,link=SetupLink.NOT_LOOKING,buttons,checked;
    private volatile long touches,stepSince,linkSince;
    private volatile boolean touchView,leds=true;

    /**
     * Setup's state: its step ({@link #WELCOME} to {@link #DONE}), the {@link SetupLink} state, the buttons held and those
     * already checked (bits 0-7), the touch areas held, whether the input check shows touch, and whether setup
     * has lighting on. A new step or link state restarts that part's timing.
     */
    synchronized void update(int step,int link,int buttons,int checked,long touches,boolean touchView,boolean leds,long now){
        if(!open||step!=this.step)stepSince=now;
        if(!open||link!=this.link)linkSince=now;
        this.step=step;this.link=link;this.buttons=buttons&255;this.checked=checked&255;this.touches=touches;
        this.touchView=touchView;this.leds=leds;open=true;
    }
    void close(){open=false;}
    boolean active(){return open;}
    /** Setup is open with its lighting turned off: every light, the body, ring and side included, stays dark. */
    boolean dark(){return open&&!leds;}

    /** The eight button colours at {@code now}, or null when setup is not open. */
    int[] frame(long now){
        if(!open)return null;
        int step=this.step,link=this.link,buttons=this.buttons,checked=this.checked;long touches=this.touches;boolean touch=touchView,on=leds;
        long stepMs=Math.max(0,now-stepSince),linkMs=Math.max(0,now-linkSince);
        int[] colors=new int[8];
        for(int i=0;i<8;i++)colors[i]=color(step,link,buttons,checked,touches,touch,on,stepMs,linkMs,i);
        return colors;
    }
    /** The ceiling light: the average of the buttons, or -1 when setup is not open. */
    int ceiling(long now){int[] colors=frame(now);return colors==null?-1:average(colors);}

    /** The colour of button {@code i} (0-7), {@code stepMs} into the step and {@code linkMs} into the link state. */
    static int color(int step,int link,int buttons,int checked,long touches,boolean touchView,boolean leds,long stepMs,long linkMs,int i){
        if(!leds)return 0;
        switch(step){
            case CONNECT:return connection(link,linkMs,i);
            case INPUT:
                if(touchView)return touches!=0?scale(WHITE,0.45f):scale(ACCENT,0.25f);
                if((buttons>>i&1)!=0)return WHITE;
                return (checked>>i&1)!=0?scale(ACCENT,0.6f):scale(WHITE,0.06f);
            case LIGHTING:return preview(stepMs,i);
            case SCREEN:return scale(ACCENT,0.3f);
            case DONE:return fill(stepMs,i);
            default:
                // Welcome and language: every button softly lit, a slow wave running clockwise.
                return scale(ACCENT,0.2f+0.3f*(0.5f+0.5f*(float)Math.cos(2*Math.PI*((double)stepMs/WAVE_MS-i/8.0))));
        }
    }
    /** Looking: one light travels round. Permission: all breathe. Connected: the white fill. Partial or failed: amber. */
    private static int connection(int link,long ms,int i){
        switch(link){
            case SetupLink.CONNECTED:return fill(ms,i);
            case SetupLink.PARTIAL:return scale(AMBER,0.2f+0.25f*(0.5f+0.5f*(float)Math.cos(2*Math.PI*ms/2000.0)));
            case SetupLink.FAILED:return scale(AMBER,0.2f);
            case SetupLink.PERMISSION:return scale(ACCENT,0.1f+0.25f*(0.5f+0.5f*(float)Math.cos(2*Math.PI*ms/3000.0)));
            default:{
                double head=(ms%TRAVEL_MS)*8.0/TRAVEL_MS,d=Math.abs(head-i);d=Math.min(d,8-d);
                return scale(ACCENT,0.04f+0.8f*(float)Math.exp(-d*d/0.5));
            }
        }
    }
    /** The phone's lighting preview: a run round the ring when the step opens, then a quiet breath. */
    private static int preview(long ms,int i){
        double t=ms/1000.0;
        double pos=((i/8.0+t/8.0)%1.0)*PREVIEW.length;int k=(int)pos%PREVIEW.length;
        int color=mix(PREVIEW[k],PREVIEW[(k+1)%PREVIEW.length],(float)(pos-Math.floor(pos)));
        float level;
        if(ms<RUN_MS){double head=ms*8.0/RUN_MS,d=head-i;level=0.25f+0.75f*(float)Math.exp(-d*d/1.2);}
        else level=0.45f+0.25f*(float)Math.sin(t*2*Math.PI/3-i*Math.PI/4);
        return scale(color,level);
    }
    private static int fill(long ms,int i){
        int base=scale(ACCENT,0.3f);
        if(ms<HOLD_MS)return mix(base,WHITE,clamp((ms-i*STEP_MS)/(float)RISE_MS));
        return mix(WHITE,base,clamp((ms-HOLD_MS)/(float)(SETTLE_MS-HOLD_MS)));
    }

    static int average(int[] colors){
        int r=0,g=0,b=0;
        for(int c:colors){r+=c>>>16&255;g+=c>>>8&255;b+=c&255;}
        int n=Math.max(1,colors.length);
        return (r/n)<<16|(g/n)<<8|(b/n);
    }
    static int scale(int rgb,float f){
        f=clamp(f);
        return Math.round((rgb>>>16&255)*f)<<16|Math.round((rgb>>>8&255)*f)<<8|Math.round((rgb&255)*f);
    }
    static int mix(int a,int b,float t){
        t=clamp(t);
        int r=Math.round((a>>>16&255)+((b>>>16&255)-(a>>>16&255))*t),g=Math.round((a>>>8&255)+((b>>>8&255)-(a>>>8&255))*t),bl=Math.round((a&255)+((b&255)-(a&255))*t);
        return r<<16|g<<8|bl;
    }
    private static float clamp(float v){return v<0?0:v>1?1:v;}
}
