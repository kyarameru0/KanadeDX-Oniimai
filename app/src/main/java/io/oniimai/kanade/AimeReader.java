package io.oniimai.kanade;

import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owns only the NFC serial interface. All USB requests run independently of game/input/LED threads. */
final class AimeReader {
    interface CardSubmitter {boolean test(byte[] code,long scanGeneration);}
    interface ErrorReporter {void accept(int issue,long scanGeneration);}
    private final UsbManager usb;
    private final IntSupplier gameStatus;
    private final IntSupplier gameLed;
    private final LongSupplier gameGeneration;
    private final LongSupplier rfClock;
    private final CardSubmitter submit;
    private final ErrorReporter reportError;
    private final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final AtomicInteger radioGeneration=new AtomicInteger();
    private final AimePresence presence=new AimePresence();
    private final EmptyFieldCadence cadence=new EmptyFieldCadence();
    private AimeChannel reportedStallChannel;private long reportedStall;
    private volatile boolean running,destroyed,foreground=true,ledEnabled=true,scanBlocked;
    private volatile int state,game,failures,issue;
    private volatile int radioType=3;
    private volatile long accepted;
    private UsbIo.Port selected;
    private volatile AimeChannel channel;
    private volatile String lastTransport="";
    private volatile String lastTrace="";
    private volatile String transportStep="idle";
    private long stableSince,successUntil,lastGameGeneration=-1;private boolean errorReported;
    private volatile long requestGameGeneration=-1,blockedGameGeneration=-1;
    private volatile long rfRetryAfter;private int rfFailures;
    private boolean waitForNewGameScan;
    private ScheduledFuture<?> pump,retry;
    // State values contain no card identifiers: 0 off,1 opening,2 ready,3 unsupported,4 retry,5 background,6 scan blocked.
    AimeReader(UsbManager usb,IntSupplier gameStatus,Predicate<byte[]> submit){this(usb,gameStatus,()->-1,submit);}
    AimeReader(UsbManager usb,IntSupplier gameStatus,IntSupplier gameLed,Predicate<byte[]> submit){this(usb,gameStatus,gameLed,()->-1,submit);}
    AimeReader(UsbManager usb,IntSupplier gameStatus,IntSupplier gameLed,LongSupplier gameGeneration,Predicate<byte[]> submit){this(usb,gameStatus,gameLed,gameGeneration,(code,gen)->submit.test(code),(ignored,gen)->{});}
    AimeReader(UsbManager usb,IntSupplier gameStatus,IntSupplier gameLed,LongSupplier gameGeneration,CardSubmitter submit,ErrorReporter reportError){this(usb,gameStatus,gameLed,gameGeneration,submit,reportError,SystemClock::uptimeMillis);}
    AimeReader(UsbManager usb,IntSupplier gameStatus,IntSupplier gameLed,LongSupplier gameGeneration,CardSubmitter submit,ErrorReporter reportError,LongSupplier rfClock){this.usb=usb;this.gameStatus=gameStatus;this.gameLed=gameLed;this.gameGeneration=gameGeneration;this.submit=submit;this.reportError=reportError;this.rfClock=rfClock;}
    boolean running(){return running;}
    private static String tr(String ko,String zh){return "zh-Hans".equals(UiText.language())?zh:ko;}
    String summary(){
        if(!running)return tr("Aime 리더 OFF","Aime 读卡器已关闭");
        if(state==1)return tr("Aime 리더 연결 중…","正在连接 Aime 读卡器…");
        if(state==4)return tr("Aime 리더 재연결 대기","等待重新连接 Aime 读卡器");
        if(state==5)return tr("앱 백그라운드 · 카드 읽기 중지","应用在后台 · 已暂停读卡");
        if(scanBlocked){
            long remaining=Math.max(0,rfRetryAfter-rfClock.getAsLong());
            if(remaining>0)return tr("카드 리더 복구 중 · ","读卡器恢复中 · ")+((remaining+999)/1000)+tr("초 후 자동 재시도","秒后自动重试");
            return tr("카드 읽기 연결 복구됨 · 게임 카드 인식 대기","读卡连接已恢复 · 等待游戏读卡");
        }
        if(state==3){
            if(issue==AimeChannel.MULTIPLE_CARDS)return tr("카드를 한 장만 대세요","请只放置一张卡");
            if(issue==AimeChannel.READ_FAILED)return tr("카드 읽기 실패 · 카드를 떼었다가 다시 대 주세요","读卡失败 · 请移开卡片后重试");
            if(issue==AimeChannel.INVALID_CARD)return tr("Aime 카드 번호를 확인할 수 없습니다","无法验证 Aime 卡号");
            return tr("지원하지 않는 카드 · Aime 카드를 사용하세요","不支持此卡 · 请使用 Aime 卡");
        }
        if(game<0)return tr("리더 연결됨 · 게임 카드 기능 준비 중","读卡器已连接 · 等待游戏读卡功能就绪");
        if(game==1)return tr("카드를 리더에 대세요","请将卡片放在读卡器上");
        if(game==2)return tr("카드 전달됨 · 게임에서 처리 중","卡片已提交 · 游戏正在处理");
        return tr("리더 연결됨 · 게임 카드 인식 화면 대기","读卡器已连接 · 等待游戏读卡画面");
    }
    String diagnostic(){AimeChannel current=channel;return summary()+" / Accepted: "+accepted+" / Game: "+game+" / State: "+state+" / Issue: "+issue+" / Idle gap: "+cadence.gapMillis()+"ms / "+(current==null?lastTransport:current.diagnostic());}
    /** Called only by the UI tick: no USB operation, wait or channel monitor. */
    boolean takeDetectStall(){
        AimeChannel current=channel;if(current==null)return false;
        long started=current.stalledDetect();
        if(started==0||(current==reportedStallChannel&&started==reportedStall))return false;
        reportedStallChannel=current;reportedStall=started;return true;
    }
    /** A mitigation for sustained empty-field traffic, not a firmware-reset fix.
     * Keep automatic reading, but leave RF off longer after eight empty replies.
     * Game scan-generation churn alone must not restart the fast polling burst.
     */
    static final class EmptyFieldCadence {
        private volatile int empty;
        private long next;
        int gapMillis(){return empty>=8?1000:250;}
        boolean due(long now){return now>=next;}
        void completed(boolean present,long now){empty=present?0:Math.min(8,empty+1);next=now+gapMillis();}
        void uncertain(long now){next=now+1000;}
        void reset(){empty=0;next=0;}
    }
    /** Copy bounded command metadata before detach cleanup; never includes card bytes. */
    String failureDiagnostic(){
        AimeChannel current=channel;return diagnostic()+(current==null?lastTrace:commandTrace(current));
    }
    private static String commandTrace(AimeChannel current){
        StringBuilder report=new StringBuilder();String[] events=current.trace();
        for(int i=Math.max(0,events.length-24);i<events.length;i++)report.append('\n').append(events[i]);
        return report.toString();
    }
    void foreground(boolean value){foreground=value;}
    void led(boolean enabled){ledEnabled=enabled;}
    void radioType(int type){
        if(type<1||type>3)throw new IllegalArgumentException("NFC radio type");
        if(destroyed||radioType==type)return;
        radioType=type;radioGeneration.incrementAndGet();int gen=generation.get();
        io.execute(()->{if(gen==generation.get()&&!destroyed){resetRfRecovery();presence.clear();errorReported=false;lastGameGeneration=-1;}});
    }
    void start(UsbIo.Port port){
        if(destroyed||running)return;
        int gen=generation.incrementAndGet();running=true;state=1;accepted=0;
        io.execute(()->{closeCurrent();presence.clear();lastGameGeneration=-1;if(gen!=generation.get()||destroyed)return;resetRfRecovery();errorReported=false;selected=port;failures=0;open(gen);});
    }
    private void open(int gen){
        if(gen!=generation.get()||destroyed)return;
        if(!foreground){state=5;retry=io.schedule(()->open(gen),600,TimeUnit.MILLISECONDS);return;}
        try{
            transportStep="restore";
            UsbIo.Port restored=UsbIo.restore(usb,selected);
            if(gen!=generation.get()||destroyed)return;
            transportStep="open";state=1;channel=new AimeChannel(new UsbIo.Cdc(usb,restored,115200,1));
            transportStep="initialize";channel.setRadioType(radioType);channel.initialize(false);
            if(gen!=generation.get()||destroyed){closeCurrent();return;}
            state=scanBlocked?6:2;stableSince=SystemClock.uptimeMillis();cadence.reset();
            Log.i("OniimaiNfc","NFC connected; "+channel.diagnostic());
            pump=io.scheduleWithFixedDelay(()->poll(gen),0,250,TimeUnit.MILLISECONDS);
        }catch(Exception error){fail(gen,error);}
    }
    private void poll(int gen){
        if(gen!=generation.get()||destroyed)return;
        try{
            transportStep="poll";
            int scanRadioGeneration=radioGeneration.get();
            channel.setRadioType(radioType);
            long now=SystemClock.uptimeMillis();
            game=foreground?gameStatus.getAsInt():0;
            long scanGeneration=gameGeneration.getAsLong();
            // The game can open a new scan automatically after an error, so a
            // generation change alone must not bypass the RF-only cooldown.
            if(scanBlocked&&rfClock.getAsLong()>=rfRetryAfter&&foreground&&game==1&&(!waitForNewGameScan||(blockedGameGeneration>=0&&scanGeneration>=0&&scanGeneration!=blockedGameGeneration))){
                scanBlocked=false;blockedGameGeneration=-1;issue=AimeChannel.NONE;state=2;
            }
            // RF requests compete for controller MCU time. Outside an authorized game
            // scan, leave the CDC handle alive and quiet instead of toggling DTR/RTS.
            if(scanBlocked||game!=1||!foreground){
                cadence.reset();
                channel.setScanning(false);
                updateLed(now);state=foreground?(scanBlocked?6:2):5;
                if(now-stableSince>10000)failures=0;
                return;
            }
            // Only a genuine new game scan rearms a card removed while RF was off.
            // A USB reconnect, focus change or settings panel does not create a scan.
            if(scanGeneration>=0&&scanGeneration!=lastGameGeneration){presence.clear();errorReported=false;lastGameGeneration=scanGeneration;}
            updateLed(now);
            if(!cadence.due(now))return; // The preceding poll already stopped RF.
            requestGameGeneration=scanGeneration;
            channel.setScanning(true);
            AimeChannel.PollResult card=channel.poll();
            if(!card.presenceKnown){
                // DETECT rejected/failed without identifying a card: do not
                // launch entry, clear a held-card latch or invent card removal.
                cadence.uncertain(SystemClock.uptimeMillis());issue=card.issue;state=2;return;
            }
            cadence.completed(card.present,SystemClock.uptimeMillis());
            if(gen!=generation.get()||destroyed||!foreground||scanRadioGeneration!=radioGeneration.get())return;
            if(scanGeneration>=0&&scanGeneration!=gameGeneration.getAsLong())return;
            if(card.accessCode!=null)resetRfRecovery();
            game=gameStatus.getAsInt();
            issue=card.issue;state=card.present&&card.accessCode==null?3:2;
            if(!card.present)errorReported=false;
            else if(card.accessCode==null&&card.issue!=AimeChannel.NONE)notifyGameError(card.issue);
            if(presence.observe(card.present,card.accessCode,now,game==1,code->submit.test(code,scanGeneration))){
                accepted++;successUntil=now+3000;game=gameStatus.getAsInt();
                // Stop the reader before another detect/authenticate cycle after a tap.
                channel.setScanning(false);updateLed(now);
                Log.i("OniimaiNfc","Physical card accepted; RF scan stopped; "+channel.diagnostic());
            }
            if(state==3)updateLed(now);
            if(now-stableSince>10000)failures=0;
        }catch(Exception error){fail(gen,error);}
    }
    private void updateLed(long now)throws java.io.IOException{
        int rgb=0;
        if(foreground&&ledEnabled){
            int original=gameLed.getAsInt();
            rgb=original>=0?original:now<successUntil?0x0000ff:game==1?0xffffff:0;
            if(state==3)rgb=issue==AimeChannel.MULTIPLE_CARDS?0xffeb04:0xff0000;
        }
        channel.setReaderLed(rgb);
    }
    private void fail(int gen,Exception error){
        AimeChannel current=channel;if(current!=null){
            lastTransport=current.diagnostic();lastTrace=commandTrace(current);
            // Capture the events BEFORE the last command, not just the command
            // that happened to time out. The allowlist excludes card contents.
            for(String event:current.trace())Log.w("OniimaiNfcTrace",event);
        }
        boolean readFailed=current!=null&&current.cardRequestFailed();
        boolean cardSeen=readFailed&&current.cardObserved();
        closeCurrent();
        if(gen!=generation.get()||destroyed)return;
        if(readFailed){
            issue=AimeChannel.TRANSPORT_ERROR;
            blockedGameGeneration=requestGameGeneration;scanBlocked=true;
            waitForNewGameScan=cardSeen;
            rfFailures=Math.min(rfFailures+1,3);
            rfRetryAfter=rfClock.getAsLong()+(15000L<<(rfFailures-1));
            if(cardSeen)notifyGameError(AimeChannel.TRANSPORT_ERROR);
            else Log.w("OniimaiNfc","NFC transport failed without card evidence; game entry unchanged");
        }
        state=4;int seconds=1<<Math.min(failures++,3);
        Log.w("OniimaiNfc","NFC recovery in "+seconds+"s / stage="+transportStep+" / "+error.getClass().getSimpleName()+" / "+lastTransport);
        // Preserve the presence latch across short disconnects: a held card must not log in twice.
        retry=io.schedule(()->open(gen),seconds,TimeUnit.SECONDS);
    }
    private void resetRfRecovery(){scanBlocked=false;blockedGameGeneration=-1;rfRetryAfter=0;rfFailures=0;waitForNewGameScan=false;}
    private void notifyGameError(int code){
        if(errorReported||!foreground||gameStatus.getAsInt()!=1)return;
        if(requestGameGeneration>=0&&requestGameGeneration!=gameGeneration.getAsLong())return;
        errorReported=true;
        AimeChannel current=channel;
        if(current!=null){
            Log.w("OniimaiNfc","Physical card rejected; issue="+code+" / "+current.diagnostic());
            for(String event:current.trace())Log.w("OniimaiNfcTrace",event);
        }
        try{reportError.accept(code,requestGameGeneration);}catch(RuntimeException error){Log.w("OniimaiNfc","Game card error feedback unavailable");}
    }
    void detached(){
        if(destroyed||!running)return;
        int gen=generation.get();
        AimeChannel current=channel;
        // The pending request can fail before Android broadcasts detach. Its
        // worker already owns recovery; do not count that same failure twice.
        if(current==null)return;
        // An actual USB removal during an unanswered DETECT must not repeatedly
        // restart RF on the same held card in the failed game scan. A genuinely
        // new game scan can try again after transport recovery.
        if(current!=null&&current.detectPending()){blockedGameGeneration=requestGameGeneration;scanBlocked=true;}
        Log.w("OniimaiNfc","USB controller detached / "+diagnostic());
        // Wake a blocked worker without a USB transfer or thread join on the UI.
        if(current!=null&&current.cancelPending())return;
        io.execute(()->{if(gen==generation.get()&&!destroyed)fail(gen,new java.io.IOException("USB detached"));});
    }
    private void closeCurrent(){
        if(pump!=null){pump.cancel(false);pump=null;}
        if(retry!=null){retry.cancel(false);retry=null;}
        if(channel!=null){channel.close();channel=null;}
    }
    void stop(){
        if(destroyed)return;generation.incrementAndGet();running=false;state=0;
        AimeChannel current=channel;if(current!=null)current.cancelPending();
        io.execute(()->{quiesce();closeCurrent();presence.clear();});
    }
    void destroy(){
        if(destroyed)return;destroyed=true;generation.incrementAndGet();running=false;
        AimeChannel current=channel;if(current!=null)current.cancelPending();
        io.execute(()->{quiesce();closeCurrent();presence.clear();});io.shutdown();
    }
    private void quiesce(){if(channel!=null&&!channel.isClosed())try{channel.setScanning(false);channel.setReaderLed(0);}catch(Exception ignored){}}
}
