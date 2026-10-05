package io.oniimai.kanade;

import android.os.SystemClock;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** IO4 output shares the session's claimed HID handle. This worker never owns/closes it. */
final class CeilingOutput {
    private final Supplier<UsbIo.Hid> transport;
    private final boolean nativeLoaded;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean enabled=true,foreground=true,destroyed;
    private volatile int brightness=100,testColor=-1,state;
    private volatile long testUntil,sent;
    /** First-run setup's lighting, whose average colour replaces the game's while setup is open. */
    private volatile SetupLights setup;
    private UsbIo.Hid current;
    private int last=-1;
    private long retryAt;
    CeilingOutput(Supplier<UsbIo.Hid> transport,boolean nativeLoaded){
        this.transport=transport;this.nativeLoaded=nativeLoaded;
        worker.scheduleWithFixedDelay(this::poll,0,33,TimeUnit.MILLISECONDS);
    }
    void setup(SetupLights lights){setup=lights;}
    void settings(boolean enabled,int brightness){this.enabled=enabled;this.brightness=Math.max(0,Math.min(100,brightness));}
    void foreground(boolean foreground){this.foreground=foreground;if(!foreground)testColor=-1;}
    // The end time first, as in LedOutput.test.
    void test(int color){testUntil=SystemClock.uptimeMillis()+2000;testColor=color&0xffffff;}
    /** Lit and writable: on, in the foreground, and an IO4 port answering (with or without game colours). */
    boolean ready(){int current=state;return enabled&&foreground&&(current==1||current==2||current==4);}
    String summary(){
        if(!enabled)return I18n.t(Msg.CEILING_STATUS_OFF);
        if(state==0)return I18n.t(Msg.CEILING_STATUS_WAITING_IO4);
        if(state==3)return I18n.t(Msg.CEILING_STATUS_CHECK_OUTPUT);
        if(state==4)return I18n.t(Msg.CEILING_STATUS_NO_COLORS);
        if(!foreground)return I18n.t(Msg.CEILING_STATUS_BACKGROUND);
        return state==2?I18n.t(Msg.CEILING_STATUS_LINKED):I18n.t(Msg.CEILING_STATUS_WAITING_SIGNAL);
    }
    String diagnostic(){return summary()+" / HID sent: "+sent;}
    private void poll(){
        if(destroyed)return;
        UsbIo.Hid port=transport.get();
        if(port!=current){current=port;last=-1;retryAt=0;}
        if(port==null){state=0;return;}
        long now=SystemClock.uptimeMillis();if(now<retryAt)return;
        int rgb=0;boolean seen=false,unavailable=false;
        if(enabled&&foreground){
            int[] frame=nativeLoaded?NativeBridge.ledSnapshot():null;
            unavailable=frame!=null&&frame.length==15&&frame[14]<0;
            if(frame!=null&&frame.length==15&&(frame[2]&(1<<11))!=0){rgb=frame[14];seen=true;}
            SetupLights lights=setup;int guide=lights==null?-1:lights.ceiling(now);
            if(guide>=0){rgb=guide;seen=true;}
            if(testColor>=0&&now<testUntil){rgb=testColor;seen=true;}else testColor=-1;
            rgb=LedFrames.scale(rgb,brightness);
        }
        try{if(last!=rgb){port.writeCeiling(rgb);last=rgb;sent++;}state=seen?2:unavailable?4:1;}
        catch(Exception error){state=3;retryAt=now+2000;last=-1;}
    }
    void destroy(){
        if(destroyed)return;destroyed=true;
        worker.execute(()->{UsbIo.Hid port=transport.get();if(port!=null)try{port.writeCeiling(0);}catch(Exception ignored){}});
        worker.shutdown();
    }
}
