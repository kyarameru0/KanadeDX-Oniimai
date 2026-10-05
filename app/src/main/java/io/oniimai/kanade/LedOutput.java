package io.oniimai.kanade;

import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** USB LED traffic never runs on the Unity, UI, or sensor-reader threads. */
final class LedOutput {
    private final UsbManager usb;
    private final boolean nativeLoaded;
    /** Welcome-screen button pattern; while it is active it replaces the game's eight button colours. */
    private final LobbyLights lobby;
    /** First-run setup's lighting; while setup is open it replaces both the welcome pattern and the game's colours. */
    private final SetupLights setup;
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private volatile boolean running,destroyed,foreground=true;
    /** True only while a board has answered and frames are being pumped; a retrying session is running but not linked. */
    private volatile boolean linked;
    private volatile java.util.function.Supplier<String> message=()->I18n.t(Msg.LED_STATUS_WAITING);
    private volatile long sent,events;
    private volatile int seen,nativeStatus;
    private volatile int bodyLevel,ringLevel,sideLevel;
    private volatile long cabinetFrames;
    private volatile int brightness=100,rotation,testColor=-1;
    private volatile long testUntil;
    private volatile boolean reverse,ring;
    // Owned exclusively by the LED worker.
    private LedChannel channel;
    private ScheduledFuture<?> pump,retry;
    private UsbIo.Port selectedPort;
    private int selectedAddress,failures;
    private int base;
    private int[] last;
    private boolean lastRing,blank;
    private long lastPing;
    LedOutput(UsbManager usb,boolean loaded){this(usb,loaded,null,null);}
    LedOutput(UsbManager usb,boolean loaded,LobbyLights lobby){this(usb,loaded,lobby,null);}
    LedOutput(UsbManager usb,boolean loaded,LobbyLights lobby,SetupLights setup){this.usb=usb;nativeLoaded=loaded;this.lobby=lobby;this.setup=setup;}
    boolean running(){return running;}
    boolean linked(){return running&&linked;}
    String summary(){return I18n.t(Msg.LED_STATUS_SUMMARY,message.get(),nativeStatus==255?I18n.t(Msg.LED_STATUS_READY):I18n.t(Msg.LED_STATUS_CODE,nativeStatus),Integer.bitCount(seen&255),sent);}
    String diagnostic(){return summary()+" / Events: "+events+" / Cabinet seen: "+((seen>>>8)&7)+" / FET body/ring/side: "+bodyLevel+"/"+ringLevel+"/"+sideLevel+" / FET frames: "+cabinetFrames;}
    /** Fixed fields only: a persistent failure report must not retain exception messages. */
    String failureDiagnostic(){return "LED running="+running+" linked="+linked+" native="+nativeStatus+" seen="+seen+" events="+events+" sent="+sent+" FET="+bodyLevel+"/"+ringLevel+"/"+sideLevel+" frames="+cabinetFrames;}
    void settings(int brightness,int rotation,boolean reverse,boolean ring){
        this.brightness=Math.max(0,Math.min(100,brightness));this.rotation=rotation;this.reverse=reverse;this.ring=ring;
    }
    void foreground(boolean value){foreground=value;if(!value)testColor=-1;}
    // The end time first: the worker clears a colour whose window has passed, and must not see the new colour with the old window.
    void test(int rgb){if(running){testUntil=SystemClock.uptimeMillis()+2000;testColor=rgb&0xffffff;}}
    void start(UsbIo.Port port,int address,int base){
        if(destroyed||running)return;
        if(address<1||address>255||base<0||base>24)throw new IllegalArgumentException(I18n.t(Msg.LED_RANGE_ERROR));
        int gen=generation.incrementAndGet();running=true;sent=0;message=()->I18n.t(Msg.LED_STATUS_CHECKING);
        io.execute(()->{
            closeCurrent();if(gen!=generation.get()||destroyed)return;
            selectedPort=port;selectedAddress=address;this.base=base;failures=0;cabinetFrames=0;
            open(gen);
        });
    }
    private void open(int gen){
        if(gen!=generation.get()||destroyed)return;
        // Reconnect runs independently of the settings panel, but opens no handles in background.
        if(!foreground){retry=io.schedule(()->open(gen),1000,TimeUnit.MILLISECONDS);return;}
        try{
            UsbIo.Port current=UsbIo.restore(usb,selectedPort);
            if(gen!=generation.get()||destroyed)return;
            channel=new LedChannel(new UsbIo.Cdc(usb,current,115200),selectedAddress);
            channel.enableReplies();byte[] info=channel.request(0xf0);
            if(info.length<8||!new String(info,0,8,StandardCharsets.US_ASCII).equals("15070-04"))throw new IOException(I18n.t(Msg.LED_UNSUPPORTED_BOARD));
            if(gen!=generation.get()||destroyed){closeCurrent();return;}
            last=null;lastRing=false;blank=false;lastPing=0;
            pump=io.scheduleWithFixedDelay(()->poll(gen),0,33,TimeUnit.MILLISECONDS);linked=true;
        }catch(Exception e){fail(gen,e);}
    }
    private void poll(int gen){
        if(gen!=generation.get()||destroyed)return;
        try{
            if(!foreground){if(!blank){blackout();blank=true;}message=()->I18n.t(Msg.LED_STATUS_BACKGROUND);return;}
            if(blank){last=null;blank=false;}
            long now=SystemClock.uptimeMillis();int[] game=null;
            nativeStatus=0;seen=0;
            int[] snapshot=nativeLoaded?NativeBridge.ledSnapshot():null;
            if(snapshot!=null&&snapshot.length==15){nativeStatus=snapshot[0];events=Integer.toUnsignedLong(snapshot[1]);seen=snapshot[2];if(nativeStatus==255)game=Arrays.copyOfRange(snapshot,3,14);}
            int[] welcome=lobby==null?null:lobby.frame(now);
            int[] guide=setup==null?null:setup.frame(now);
            boolean test=testColor>=0&&now<testUntil;if(!test)testColor=-1;
            int[] colors=compose(game,welcome,guide,setup!=null&&setup.dark(),test?testColor:-1);
            int[] frame=LedFrames.physical(colors,brightness,rotation,reverse);
            send(frame,ring);
            // Confirm a quiet/static connection too. Failure only closes this LED port.
            if(now-lastPing>=2000){channel.request(0xf0);lastPing=now;}
            failures=0;
            message=()->test?I18n.t(Msg.LED_STATUS_TESTING):I18n.t(Msg.LED_STATUS_LINKED);
            if(!test&&welcome==null&&guide==null&&(nativeStatus!=255||(seen&255)==0))message=()->I18n.t(Msg.LED_STATUS_WAITING_GAME);
        }catch(Exception e){fail(gen,e);}
    }
    /**
     * The eleven output values (eight button colours, then the body, ring and side levels) from the game's, the
     * welcome screen's and setup's lighting. The welcome screen's and setup's colours replace the game's button
     * colours; with lighting turned off in setup the body, ring and side go dark too. A colour test fills all.
     */
    static int[] compose(int[] game,int[] welcome,int[] guide,boolean setupDark,int test){
        int[] colors=new int[11];
        if(game!=null)System.arraycopy(game,0,colors,0,Math.min(11,game.length));
        if(welcome!=null)System.arraycopy(welcome,0,colors,0,8);
        if(guide!=null)System.arraycopy(guide,0,colors,0,8);
        if(setupDark){colors[8]=0;colors[9]=0;colors[10]=0;}
        if(test>=0)Arrays.fill(colors,test);
        return colors;
    }
    private void send(int[] frame,boolean useRing)throws IOException{
        boolean changed=false;
        for(int i=0;i<8;i++)if(last==null||last[i]!=frame[i]){channel.request(0x31,LedFrames.color(base+i,frame[i]));changed=true;}
        if(changed){channel.request(0x3c);sent++;}
        if(useRing&&(last==null||!lastRing||last[8]!=frame[8]||last[9]!=frame[9]||last[10]!=frame[10]))sendCabinet(frame[8],frame[9],frame[10]);
        else if(!useRing&&lastRing)sendCabinet(0,0,0);
        last=frame;lastRing=useRing;
    }
    private void sendCabinet(int body,int ring,int side)throws IOException{
        channel.request(0x39,new byte[]{(byte)body,(byte)ring,(byte)side});
        bodyLevel=body;ringLevel=ring;sideLevel=side;
        if(cabinetFrames++<4)android.util.Log.i("OniimaiKanade","LED FET acknowledged body/ring/side="+body+"/"+ring+"/"+side);
    }
    private void blackout()throws IOException{
        if(channel==null||channel.isClosed())return;
        for(int i=0;i<8;i++)channel.request(0x31,LedFrames.color(base+i,0));
        channel.request(0x3c);if(lastRing)sendCabinet(0,0,0);last=null;lastRing=false;
    }
    private void closeCurrent(){closeCurrent(true);}
    private void closeCurrent(boolean clear){
        linked=false;
        if(pump!=null){pump.cancel(false);pump=null;}
        if(retry!=null){retry.cancel(false);retry=null;}
        if(clear)try{blackout();}catch(Exception ignored){}
        if(channel!=null){channel.close();channel=null;}
    }
    private void fail(int gen,Exception error){
        closeCurrent(false);
        if(gen!=generation.get()||destroyed)return;
        int seconds=1<<Math.min(failures++,3);
        message=()->I18n.t(Msg.LED_STATUS_RETRY,seconds,error.getMessage());
        // Keep the enabled session alive so polling a settings panel cannot start a duplicate worker.
        retry=io.schedule(()->open(gen),seconds,TimeUnit.SECONDS);
    }
    void stop(){
        if(destroyed)return;generation.incrementAndGet();running=false;testColor=-1;message=()->I18n.t(Msg.LED_STATUS_OFF);io.execute(this::closeCurrent);
    }
    void destroy(){
        if(destroyed)return;destroyed=true;generation.incrementAndGet();running=false;io.execute(this::closeCurrent);io.shutdown();
    }
}
