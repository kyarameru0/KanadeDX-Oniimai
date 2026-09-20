package io.oniimai.kanade;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One serialized, sequence-matched NFC request. A timeout permanently poisons this transport. */
final class AimeChannel implements AutoCloseable {
    static final int NONE=0,UNSUPPORTED_TYPE=1,MULTIPLE_CARDS=2,READ_FAILED=3,INVALID_CARD=4,TRANSPORT_ERROR=5;
    static final class PollResult {
        final boolean present,presenceKnown;final String accessCode;final int cardType,issue;
        // An error without a validated positive DETECT is neither card presence
        // nor card removal. Only a successful empty response proves removal.
        PollResult(boolean present,String code,int type,int issue){this.present=present;presenceKnown=present||issue==NONE;accessCode=code;cardType=type;this.issue=issue;}
    }
    private static final byte[] KEY_B_BYTES={0x57,0x43,0x43,0x46,0x76,0x32};
    private static final byte[] KEY_A_BYTES={0x60,(byte)0x90,(byte)0xd0,0x06,0x32,(byte)0xf5};
    private final UsbIo.Cdc port;private final int timeout,detectTimeout;private final AtomicBoolean closed=new AtomicBoolean();
    private final AimeProtocol.Parser parser;
    private final AimeTrace trace=new AimeTrace();
    private volatile CompletableFuture<AimeProtocol.Reply> pending;
    private volatile int expectedAddress=-1,expectedSequence=-1,expectedCommand=-1;private int sequence;
    private byte[] cachedId;private int cachedType,readAttempts;private PollResult cachedResult;private long retryAt;
    private boolean initialized,ledInitialized;private int ledRgb=-1;private long ledQuietUntil;
    private volatile boolean scanning;private boolean radioOn;
    private long commandReadyAt,radioReadyAt,radioOffUntil;
    // Observed on the user's Windows maimai1.65: >=25ms after each reply,
    // >=300ms from RF-on acknowledgement to DETECT, >=100ms RF-off recovery.
    private static final long COMMAND_GAP=25,RF_SETTLE=300,RF_OFF_GAP=100;
    private volatile int radioType=3;
    private volatile int lastAddress=-1,lastCommand=-1,lastStatus=-1,lastCardCount=-1,lastCardType;
    private volatile int lastFelicaCommand=-1,lastFelicaBlock=-1,lastFelicaPollRetries;
    private volatile long transactions,lastStartedNanos,lastElapsedMillis;
    // Diagnostic budget exceeds the observed ~6.4 s device detach, so physical
    // removal can be distinguished from our own timeout cleanup/DTR change.
    AimeChannel(UsbIo.Cdc port){this(port,1200,10000);}
    AimeChannel(UsbIo.Cdc port,int timeout){this(port,timeout,timeout);}
    AimeChannel(UsbIo.Cdc port,int timeout,int detectTimeout){
        this.port=port;this.timeout=timeout;this.detectTimeout=detectTimeout;
        parser=new AimeProtocol.Parser(reply->{
            CompletableFuture<AimeProtocol.Reply> future=pending;
            if(future!=null&&reply.address==expectedAddress&&reply.sequence==expectedSequence&&reply.command==expectedCommand)future.complete(reply);
        });
        port.start(parser::feed,error->close());
    }
    synchronized void initialize() throws IOException {initialize(true);}
    synchronized void initialize(boolean enableScanning) throws IOException {
        if(initialized)return;
        // The documented normal-mode/reset handshake can return status 3 when
        // already in normal mode. Other checked errors must stop initialization.
        try{request(AimeProtocol.NORMAL,AimeProtocol.EMPTY);}
        catch(DeviceError error){if(error.status!=AimeProtocol.STATUS_INVALID_COMMAND)throw error;}
        byte[] firmware=request(AimeProtocol.FW,AimeProtocol.EMPTY),hardware=request(AimeProtocol.HW,AimeProtocol.EMPTY);
        if(firmware.length==0||hardware.length==0){close();throw new IOException("NFC reader identity missing");}
        request(AimeProtocol.KEY_B,KEY_B_BYTES);request(AimeProtocol.KEY_A,KEY_A_BYTES);
        request(enableScanning?AimeProtocol.START:AimeProtocol.STOP,enableScanning?new byte[]{(byte)radioType}:AimeProtocol.EMPTY);
        scanning=enableScanning;radioOn=enableScanning;initialized=true;
        if(radioOn)radioReadyAt=afterMillis(RF_SETTLE);else radioOffUntil=afterMillis(RF_OFF_GAP);
    }
    /** RF polling can be stopped during play while retaining the CDC handle and LED control. */
    synchronized void setScanning(boolean enabled)throws IOException{
        if(!initialized)throw new IOException("NFC reader not initialized");
        if(closed.get())throw new IOException("NFC reader disconnected");
        if(enabled){lastCardCount=-1;lastCardType=0;}
        if(scanning==enabled)return;
        if(enabled)startRadio();else stopRadio();
        scanning=enabled;if(!enabled)clearCache();
    }
    private void startRadio()throws IOException{
        if(radioOn)return;awaitUntil(radioOffUntil);
        request(AimeProtocol.START,new byte[]{(byte)radioType});radioOn=true;radioReadyAt=afterMillis(RF_SETTLE);
    }
    private void stopRadio()throws IOException{
        if(!radioOn)return;
        request(AimeProtocol.STOP,AimeProtocol.EMPTY);radioOn=false;radioOffUntil=afterMillis(RF_OFF_GAP);
    }
    /** Documented RadioOn mask: 1=MIFARE, 2=FeliCa, 3=both. Never changes card data. */
    synchronized void setRadioType(int type)throws IOException{
        if(type<1||type>3)throw new IllegalArgumentException("NFC radio type");
        if(closed.get())throw new IOException("NFC reader disconnected");
        if(radioType==type)return;
        // A mode change is serialized behind any request; never send an RF
        // command while DETECT is outstanding or silently enable a paused scan.
        if(scanning)setScanning(false);
        radioType=type;clearCache();
    }
    /** LED reset F5 is acknowledged; RGB81 has no mandatory ACK. */
    synchronized void setReaderLed(int rgb)throws IOException{
        byte[] color=AimeProtocol.rgb(rgb);
        if(closed.get())throw new IOException("NFC reader disconnected");
        if(rgb==ledRgb)return;
        if(!ledInitialized){request(AimeProtocol.LED_ADDRESS,AimeProtocol.LED_NORMAL,AimeProtocol.EMPTY);ledInitialized=true;}
        writeOnly(AimeProtocol.LED_ADDRESS,AimeProtocol.LED_RGB,color);ledRgb=rgb;
        ledQuietUntil=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(250);
    }
    synchronized PollResult poll() throws IOException {
        if(!initialized)throw new IOException("NFC reader not initialized");
        if(!scanning)throw new IOException("NFC reader scanning paused");
        lastCardCount=-1;lastCardType=0;
        try{return pollActive();}
        finally{if(!closed.get())stopRadio();}
    }
    private PollResult pollActive()throws IOException{
        // Called on the NFC worker. Match the reference poll loop's 250 ms
        // spacing from its optional RGB write to the next card-detect request.
        awaitLedQuiet();
        startRadio();awaitUntil(radioReadyAt);
        AimeProtocol.Card[] cards;
        try{
            cards=AimeProtocol.cards(request(AimeProtocol.DETECT,AimeProtocol.EMPTY));
            // Commit evidence before STOP/SELECT/authentication can fail. It is
            // scoped to this attempt, never inherited from a previous tap.
            lastCardCount=cards.length;lastCardType=cards.length==1?cards[0].type:0;
            // Windows FeliCa uses STOP before through commands. The upstream
            // MIFARE capture instead keeps RF on through SELECT/AUTH/READ and
            // stops afterwards: stopping here destroys the selected Type A tag.
            // poll()'s finally also covers cached/rejected cards and failures.
            boolean mifare=cards.length==1&&cards[0].type==AimeProtocol.MIFARE;
            if(!mifare)stopRadio();
        }
        catch(DeviceError error){
            // A checked reader error is not a USB failure, nor proof of card removal.
            // Keep the held-card cache so a temporary RF error cannot rearm it.
            return new PollResult(cardObserved(),null,lastCardType,error.status==AimeProtocol.STATUS_INVALID_COMMAND?UNSUPPORTED_TYPE:READ_FAILED);
        }
        catch(IllegalArgumentException e){lastStatus=-6;close();throw new IOException("Invalid NFC card report",e);}
        if(cards.length==0){clearCache();return new PollResult(false,null,0,NONE);}
        if(cards.length!=1){clearCache();return new PollResult(true,null,0,MULTIPLE_CARDS);}
        AimeProtocol.Card card=cards[0];long now=System.nanoTime()/1000000;
        boolean sameCard=card.type==cachedType&&Arrays.equals(card.id,cachedId);
        if(sameCard&&cachedResult!=null&&(cachedResult.issue!=READ_FAILED||readAttempts>=2||now<retryAt))return cachedResult;
        if(!sameCard)readAttempts=0;
        readAttempts++;
        PollResult result;
        try{
            String code=null;int issue=NONE;
            if((card.type==AimeProtocol.MIFARE&&(radioType&1)==0)||(card.type==AimeProtocol.FELICA_CARD&&(radioType&2)==0)){
                // Some compatible firmware ignores RadioOn's mask. Do not issue
                // another card family's authentication/read commands in that case.
                issue=UNSUPPORTED_TYPE;
            }else if(card.type==AimeProtocol.MIFARE&&card.id.length==4){
                request(AimeProtocol.SELECT,card.id);
                if(!authenticate(card.id))issue=UNSUPPORTED_TYPE;
                else{
                    byte[] marker=request(AimeProtocol.READ,AimeProtocol.block(card.id,1));
                    // Compatible MIFARE1K cards may have zero or custom block1.
                    // Authentication and valid nonempty block2 BCD are required.
                    if(!AimeProtocol.aimeMifareLayout(marker))issue=INVALID_CARD;
                    else code=AimeProtocol.accessCode(request(AimeProtocol.READ,AimeProtocol.block(card.id,2)));
                }
            }else if(card.type==AimeProtocol.FELICA_CARD){
                result=readFelica(card);
                code=result.accessCode;issue=result.issue;
            }else issue=UNSUPPORTED_TYPE;
            if(issue==NONE&&code==null)issue=INVALID_CARD;
            result=new PollResult(true,code,card.type,issue);
        }catch(DeviceError error){result=new PollResult(true,null,card.type,error.status==AimeProtocol.STATUS_INVALID_COMMAND?UNSUPPORTED_TYPE:READ_FAILED);}
        // One paced retry tolerates a transient RF error. Further attempts require
        // removal, a different card, or a new scan session; they never loop forever.
        cachedId=card.id.clone();cachedType=card.type;cachedResult=result;retryAt=System.nanoTime()/1000000+1000;return result;
    }
    private PollResult readFelica(AimeProtocol.Card card)throws IOException{
        try{return readFelicaData(card);}
        finally{
            // FeliCa-through can enable its own RF exchange after STOP. Windows
            // sends another STOP at the end; do not rely on our polling flag.
            if(!closed.get()){request(AimeProtocol.STOP,AimeProtocol.EMPTY);radioOn=false;radioOffUntil=afterMillis(RF_OFF_GAP);}
        }
    }
    private PollResult readFelicaData(AimeProtocol.Card card)throws IOException{
        byte[] id=Arrays.copyOf(card.id,8);
        // Arduino-Aime-Reader's capture selects the detected FeliCa system before
        // reading blocks. Never try a zero-IDm discovery or undocumented Active2.
        lastFelicaPollRetries=0;
        byte[] selected=request(AimeProtocol.FELICA,AimeProtocol.felicaPoll(id));
        // Two observed Windows reads returned the length-only FeliCa packet
        // 01, then a full reply after the SAME Polling request and 25ms gap.
        // 00 was an unverified guess in rc3. Retry the observed 01 only once;
        // never retry arbitrary malformed identities or transport timeouts.
        if(selected.length==1&&selected[0]==1){lastFelicaPollRetries=1;selected=request(AimeProtocol.FELICA,AimeProtocol.felicaPoll(id));}
        int system=AimeProtocol.felicaSystem(selected,card.id);
        if(system<0)return new PollResult(true,null,card.type,READ_FAILED);
        if(system!=0x88b4)return new PollResult(true,null,card.type,UNSUPPORTED_TYPE);
        // Arcade Docs identifies SEGA LiteS by DFC0078. Verify the immutable ID
        // block first, so another issuer is never subjected to an SPAD0 decode.
        byte[] identity=request(AimeProtocol.FELICA,AimeProtocol.felicaRead(id,0x82));
        byte[] block=AimeProtocol.felicaBlock(identity,id);
        if(block==null)return felicaReadFailure(identity,id,card.type);
        int dfc=AimeProtocol.felicaDfc(block,id);
        if(dfc<0)return new PollResult(true,null,card.type,READ_FAILED);
        if(dfc!=0x0078)return new PollResult(true,null,card.type,UNSUPPORTED_TYPE);
        byte[] response=request(AimeProtocol.FELICA,AimeProtocol.felicaRead(id));
        block=AimeProtocol.felicaBlock(response,id);
        if(block==null)return felicaReadFailure(response,id,card.type);
        String code=AimeFelica.accessCode(block);
        return new PollResult(true,code,card.type,code==null?INVALID_CARD:NONE);
    }
    private PollResult felicaReadFailure(byte[] response,byte[] id,int type){
        return new PollResult(true,null,type,AimeProtocol.felicaReadError(response,id)==0xffa1?UNSUPPORTED_TYPE:READ_FAILED);
    }
    private boolean authenticate(byte[] id)throws IOException{
        try{request(AimeProtocol.AUTH_B,AimeProtocol.block(id,3));return true;}
        catch(DeviceError error){if(error.status!=AimeProtocol.STATUS_CARD_ERROR)throw error;}
        try{request(AimeProtocol.AUTH_A,AimeProtocol.block(id,3));return true;}
        catch(DeviceError error){if(error.status!=AimeProtocol.STATUS_CARD_ERROR)throw error;return false;}
    }
    private void clearCache(){cachedId=null;cachedResult=null;cachedType=readAttempts=0;retryAt=0;}
    private void awaitLedQuiet()throws IOException{
        try{long remaining;while((remaining=ledQuietUntil-System.nanoTime())>0){if(closed.get())throw new IOException("NFC reader disconnected");TimeUnit.NANOSECONDS.sleep(remaining);}}
        catch(InterruptedException error){Thread.currentThread().interrupt();close();throw new IOException("NFC reader canceled",error);}
    }
    private void beginTransaction(int address,int command){lastAddress=address;lastCommand=command;lastStatus=-1;lastElapsedMillis=0;lastStartedNanos=System.nanoTime();transactions++;}
    private void finishTransaction(){lastElapsedMillis=Math.max(0,(System.nanoTime()-lastStartedNanos)/1000000);commandReadyAt=afterMillis(COMMAND_GAP);}
    private static long afterMillis(long ms){return System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(ms);}
    private void awaitUntil(long deadline)throws IOException{
        try{long remaining;while((remaining=deadline-System.nanoTime())>0){if(closed.get())throw new IOException("NFC reader disconnected");TimeUnit.NANOSECONDS.sleep(remaining);}}
        catch(InterruptedException error){Thread.currentThread().interrupt();close();throw new IOException("NFC reader canceled",error);}
    }
    private void writeOnly(int address,int command,byte[] payload)throws IOException{
        if(closed.get())throw new IOException("NFC reader disconnected");
        awaitUntil(commandReadyAt);
        beginTransaction(address,command);
        int sentSequence=sequence++&255;trace.sent(address,sentSequence,command,payload);
        try{port.write(AimeProtocol.request(address,sentSequence,command,payload));lastStatus=-5;}
        catch(IOException error){lastStatus=-4;close();throw error;}
        finally{finishTransaction();}
    }
    synchronized byte[] request(int command,byte[] payload)throws IOException{return request(AimeProtocol.ADDRESS,command,payload);}
    private byte[] request(int address,int command,byte[] payload)throws IOException{
        if(closed.get())throw new IOException("NFC reader disconnected");
        awaitUntil(commandReadyAt);
        CompletableFuture<AimeProtocol.Reply> future=new CompletableFuture<>();
        expectedAddress=address;expectedSequence=sequence++&255;expectedCommand=command;pending=future;
        beginTransaction(address,command);
        trace.sent(address,expectedSequence,command,payload);
        if(command==AimeProtocol.FELICA&&payload.length>=10){lastFelicaCommand=payload[9]&255;lastFelicaBlock=lastFelicaCommand==6&&payload.length==24?payload[23]&255:-1;}
        try{
            port.write(AimeProtocol.request(address,expectedSequence,command,payload));
            AimeProtocol.Reply reply=future.get(command==AimeProtocol.DETECT?detectTimeout:timeout,TimeUnit.MILLISECONDS);
            trace.received(reply);
            lastStatus=reply.status;
            if(reply.status!=0)throw new DeviceError(reply.status,command);return reply.payload;
        }catch(TimeoutException error){lastStatus=-2;close();throw new IOException("NFC reader response timeout",error);}
        catch(InterruptedException error){lastStatus=-3;Thread.currentThread().interrupt();close();throw new IOException("NFC reader canceled",error);}
        catch(ExecutionException error){lastStatus=-4;close();throw new IOException("NFC reader disconnected",error.getCause());}
        catch(DeviceError error){throw error;}
        catch(IOException error){lastStatus=-4;close();throw error;}
        finally{if(lastStatus<0)trace.failure(lastStatus);finishTransaction();pending=null;expectedAddress=expectedSequence=expectedCommand=-1;}
    }
    private static final class DeviceError extends IOException {
        final int status;DeviceError(int status,int command){super("NFC command "+Integer.toHexString(command)+" status "+status);this.status=status;}
    }
    boolean isClosed(){return closed.get();}
    boolean cardObserved(){return lastCardCount>0;}
    String[] trace(){return trace.snapshot();}
    boolean detectPending(){CompletableFuture<AimeProtocol.Reply> current=pending;return !closed.get()&&expectedAddress==AimeProtocol.ADDRESS&&expectedCommand==AimeProtocol.DETECT&&current!=null&&!current.isDone();}
    /** Metadata-only early observation; never cancels or sends a second command. */
    long stalledDetect(){
        long started=lastStartedNanos;
        return detectPending()&&lastStatus==-1&&started!=0&&System.nanoTime()-started>=TimeUnit.MILLISECONDS.toNanos(1500)?started:0;
    }
    boolean cardRequestFailed(){
        // START/STOP are part of a card transaction too. In rc3 a lost START
        // reply escaped RF cooldown and game error feedback. An initialization
        // STOP has no authorized scan, so keep it in ordinary connection recovery.
        boolean scanRadio=initialized&&(lastCommand==AimeProtocol.START||lastCommand==AimeProtocol.STOP);
        return lastAddress==AimeProtocol.ADDRESS&&(lastStatus==-2||lastStatus==-3||lastStatus==-4)&&(scanRadio||lastCommand==AimeProtocol.DETECT||lastCommand==AimeProtocol.SELECT||lastCommand==AimeProtocol.AUTH_A||lastCommand==AimeProtocol.AUTH_B||lastCommand==AimeProtocol.READ||lastCommand==AimeProtocol.FELICA);
    }
    /** Only complete the pending future here; its worker owns USB cleanup. */
    boolean cancelPending(){CompletableFuture<AimeProtocol.Reply> current=pending;return current!=null&&current.completeExceptionally(new IOException("USB detached"));}
    /** Non-sensitive numerical diagnostics; no identity, access code, keys or payload data. */
    String diagnostic(){
        long elapsed=lastStatus==-1&&lastStartedNanos!=0?Math.max(0,(System.nanoTime()-lastStartedNanos)/1000000):lastElapsedMillis;
        return "node="+lastAddress+" cmd="+(lastCommand<0?"none":Integer.toHexString(lastCommand))+" status="+lastStatus+" ms="+elapsed+" requests="+transactions+" rx="+parser.validFrames+" invalid="+parser.invalidFrames+" cards="+lastCardCount+" type="+Integer.toHexString(lastCardType)+" radio="+radioType+" scan="+(scanning?1:0)+" closed="+(closed.get()?1:0)+" felica="+lastFelicaCommand+" block="+lastFelicaBlock+" pollRetry="+lastFelicaPollRetries;
    }
    public void close(){
        if(!closed.compareAndSet(false,true))return;
        CompletableFuture<AimeProtocol.Reply> future=pending;if(future!=null)future.completeExceptionally(new IOException("NFC reader disconnected"));
        port.close();
    }
}
