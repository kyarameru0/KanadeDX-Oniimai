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
    private UsbIo.Hid current;
    private int last=-1;
    private long retryAt;
    CeilingOutput(Supplier<UsbIo.Hid> transport,boolean nativeLoaded){
        this.transport=transport;this.nativeLoaded=nativeLoaded;
        worker.scheduleWithFixedDelay(this::poll,0,33,TimeUnit.MILLISECONDS);
    }
    void settings(boolean enabled,int brightness){this.enabled=enabled;this.brightness=Math.max(0,Math.min(100,brightness));}
    void foreground(boolean foreground){this.foreground=foreground;if(!foreground)testColor=-1;}
    void test(int color){testColor=color&0xffffff;testUntil=SystemClock.uptimeMillis()+2000;}
    private static String tr(String ko,String zh){return "zh-Hans".equals(UiText.language())?zh:ko;}
    String summary(){
        if(!enabled)return tr("천장 RGB 조명 OFF","顶部 RGB 灯已关闭");
        if(state==0)return tr("천장 RGB · IO4 연결 대기","顶部 RGB · 等待 IO4 连接");
        if(state==3)return tr("천장 RGB · IO4 출력 확인 필요","顶部 RGB · 请检查 IO4 输出");
        if(state==4)return tr("천장 RGB · 게임 색상 연결 실패","顶部 RGB · 无法读取游戏颜色");
        if(!foreground)return tr("천장 RGB · 백그라운드 소등","顶部 RGB · 后台熄灯");
        return state==2?tr("천장 RGB · 게임 연동 중","顶部 RGB · 游戏联动中"):tr("천장 RGB · 게임 조명 신호 대기","顶部 RGB · 等待游戏灯光信号");
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
