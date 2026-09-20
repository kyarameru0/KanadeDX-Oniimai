package io.oniimai.kanade;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

public final class AimeChannelTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static byte[] hex(String text){return AimeProtocolTest.hex(text);}
    static byte[] unframe(byte[] wire){byte[] raw=new byte[wire.length];int n=0;for(int i=1;i<wire.length;i++){int b=wire[i]&255;if(b==0xd0)b=(wire[++i]&255)+1;raw[n++]=(byte)b;}return Arrays.copyOf(raw,n-1);}
    static void reply(UsbIo.Cdc port,byte[] request,int status,byte[] payload){port.receive(AimeProtocolTest.response(request[1]&255,request[2]&255,request[3]&255,status,payload));}
    static final class Reader {
        final UsbIo.Cdc port=new UsbIo.Cdc();final List<Integer> commands=new ArrayList<>();
        byte[] detected=hex("01100401020304"),felicaPayload=hex("1d070123456789abcdef000001893d691806eccfef90c8773d41eee231");
        byte[] marker=hex("53425344000000000000000000000000");
        byte[] accessBlock=hex("00000000000000123456789012345678");
        byte[] polling=hex("14010123456789abcdef00f100000001430088b4");
        byte[] identity=hex("1d070123456789abcdef0000010123456789abcdef0078000000000000");
        final List<Integer> felicaOps=new ArrayList<>();
        boolean felicaUnsupported,failB,failA,radioOn;int reads,felicaReads,detectStatus,readStatus,felicaStatus,normalStatus;
        Reader(){port.onWrite=wire->{
            byte[] request=unframe(wire);int cmd=request[3]&255;commands.add(cmd);
            check(cmd!=0x53&&cmd!=0x60&&cmd!=0x61&&cmd!=0x63&&cmd!=0x64,"Never writes cards or firmware");
            byte[] payload=new byte[0];int status=0;
            switch(cmd){
                case 0x62:check(request.length==5&&request[1]==0,"Normal-mode handshake has NFC address and empty payload");status=normalStatus;break;
                case 0x30:payload=hex("94");break;case 0x32:payload="837-15396".getBytes(java.nio.charset.StandardCharsets.US_ASCII);break;
                case 0x42:payload=detected;status=detectStatus;break;
                case 0x40:radioOn=true;break;
                case 0x41:radioOn=false;break;
                case 0x43:check(radioOn,"MIFARE target selection requires RF still on");break;
                case 0x55:check(radioOn,"MIFARE KeyB auth must not follow RF-off");if(failB)status=1;break;
                case 0x51:check(radioOn,"MIFARE KeyA auth must not follow RF-off");if(failA)status=1;break;
                case 0x52:check(radioOn,"MIFARE block read must retain RF");reads++;status=readStatus;payload=request[9]==1?marker:accessBlock;break;
                case 0x71:
                    int operation=request[14]&255;
                    check(operation==0||operation==6,"Only documented FeliCa polling and read commands passed through");
                    if(operation==0){felicaOps.add(0x100);payload=polling;}
                    else if((request[28]&255)==0x82){felicaOps.add(0x82);payload=identity;}
                    else {check(request[28]==0,"Only ID and SPAD0 blocks read");felicaOps.add(0);felicaReads++;status=felicaStatus;payload=felicaPayload;}
                    if(felicaUnsupported)status=3;break;
                default:break;
            }
            reply(port,request,status,payload);
        };}
    }
    public static void main(String[] args)throws Exception{
        testRadioSelection();
        testReferenceRfCycle();
        testRfTransportFailures();
        testFelicaIdentification();
        testWindowsLengthOnlyPolling();
        testCompatibleMifare();
        Reader reader=new Reader();AimeChannel channel=new AimeChannel(reader.port,60);channel.initialize();
        check(reader.commands.equals(Arrays.asList(0x62,0x30,0x32,0x54,0x50,0x40)),"Initialize checks normal mode before identity, keys and polling");
        Reader alreadyNormal=new Reader();alreadyNormal.normalStatus=3;AimeChannel normal=new AimeChannel(alreadyNormal.port,60);normal.initialize(false);
        check(!normal.isClosed()&&alreadyNormal.commands.equals(Arrays.asList(0x62,0x30,0x32,0x54,0x50,0x41)),"Documented already-normal status 3 permits initialization with RF stopped");normal.close();
        Reader normalFailure=new Reader();normalFailure.normalStatus=6;AimeChannel rejectedNormal=new AimeChannel(normalFailure.port,60);
        try{rejectedNormal.initialize(false);throw new AssertionError("Reader normal-mode failure was ignored");}catch(IOException expected){check(normalFailure.commands.equals(Arrays.asList(0x62)),"Other normal-mode errors never proceed to identity, keys or RF commands");}finally{rejectedNormal.close();}
        UsbIo.Cdc absentNormal=new UsbIo.Cdc();AimeChannel silentNormal=new AimeChannel(absentNormal,25);AtomicInteger normalWrites=new AtomicInteger();absentNormal.onWrite=wire->normalWrites.incrementAndGet();
        try{silentNormal.initialize(false);throw new AssertionError("Unacknowledged normal-mode command accepted");}catch(IOException expected){check(normalWrites.get()==1&&silentNormal.isClosed(),"Missing normal-mode reply remains a transport timeout and stops initialization");}
        AimeChannel.PollResult first=channel.poll();
        check(first.present&&first.issue==AimeChannel.NONE&&"00123456789012345678".equals(first.accessCode),"MIFARE returns actual block2 BCD code");
        String readDiagnostic=channel.diagnostic();
        check(readDiagnostic.contains("cmd=41 status=0")&&readDiagnostic.contains("cards=1 type=10")&&readDiagnostic.contains("scan=1"),"Successful MIFARE read ends with RF off and preserves card type diagnostics");
        check(!readDiagnostic.contains(first.accessCode)&&!readDiagnostic.contains("01020304"),"Diagnosis omits access codes and UIDs");
        check(reader.reads==2,"MIFARE marker and accesscode blocks read once");
        check(channel.poll()==first&&reader.reads==2,"Held card is detected but not reauthenticated/read every poll");
        reader.detected=hex("00");check(!channel.poll().present,"Removal clears card state");
        reader.detected=hex("01100401020304");reader.failB=true;channel.poll();
        check(reader.reads==4&&reader.commands.contains(0x51),"New tap reads again; supported KeyA fallback after KeyB card failure");
        reader.detected=hex("0120100123456789abcdef00f1000000014300");
        AimeChannel.PollResult felica=channel.poll();check(felica.present&&"50101234567890123456".equals(felica.accessCode),"Actual FeliCa SPAD code read, no UID-derived code");
        reader.detected=hex("00");channel.poll();reader.felicaUnsupported=true;reader.detected=hex("0120100123456789abcdef00f1000000014300");
        check(channel.poll().issue==AimeChannel.UNSUPPORTED_TYPE&&!channel.isClosed(),"FeliCa unsupported firmware is reported without losing transport");
        check(channel.diagnostic().contains("cmd=41 status=0"),"Unsupported through command still closes the RF exchange without payload logging");
        reader.detected=hex("02300401020304100405060708");check(channel.poll().issue==AimeChannel.MULTIPLE_CARDS,"Multiple cards do not pick arbitrary account");
        reader.detected=hex("01300401020304");check(channel.poll().issue==AimeChannel.UNSUPPORTED_TYPE,"Unknown type remains visible without fake code");
        channel.close();check(reader.port.closed,"Close releases reader transport");
        Reader banapass=new Reader();banapass.marker=hex("00004e42474943000000000000000000");AimeChannel nonAimeM=new AimeChannel(banapass.port,60);nonAimeM.initialize();
        AimeChannel.PollResult wrongBrand=nonAimeM.poll();
        check(wrongBrand.present&&wrongBrand.accessCode==null&&wrongBrand.issue==AimeChannel.INVALID_CARD&&!nonAimeM.isClosed(),"Authenticated legacy NBGIC Banapass card is not an SBSD Aime card");
        check(banapass.reads==1&&nonAimeM.poll()==wrongBrand&&banapass.reads==1,"Rejected marker prevents access-code read and repeated authentication while held");nonAimeM.close();
        for(String encryptedOther:new String[]{"6628766bd0eccfef9034b0310114f831","8c3e6bddd0eccfef90c8c8d1f0979931","965a08daeca7cfef90778f0339bbc631"}){
            Reader other=new Reader();other.detected=hex("0120100123456789abcdef00f1000000014300");other.felicaPayload=hex("1d070123456789abcdef000001"+encryptedOther);
            AimeChannel filtered=new AimeChannel(other.port,60);filtered.initialize();AimeChannel.PollResult denied=filtered.poll();
            check(denied.present&&denied.accessCode==null&&denied.issue==AimeChannel.INVALID_CARD&&!filtered.isClosed(),"Valid non-SEGA Amusement IC code is never offered to the game and leaves USB connected");
            check(filtered.poll()==denied&&other.felicaReads==1,"Rejected non-Aime Amusement IC service data is read only once while held");
            other.detected=hex("00");filtered.poll();other.detected=hex("0120100123456789abcdef00f1000000014300");other.felicaPayload=hex("1d070123456789abcdef000001893d691806eccfef90c8773d41eee231");
            check("50101234567890123456".equals(filtered.poll().accessCode),"Aime succeeds after another publisher's card is removed on the same USB connection");filtered.close();
        }
        Reader ordinary=new Reader();ordinary.failB=ordinary.failA=true;AimeChannel nonAime=new AimeChannel(ordinary.port,60);nonAime.initialize();
        AimeChannel.PollResult unsupported=nonAime.poll();
        check(unsupported.present&&unsupported.accessCode==null&&unsupported.issue==AimeChannel.UNSUPPORTED_TYPE&&!nonAime.isClosed(),"An ordinary Type A card rejecting both documented keys is unsupported without disconnecting USB");
        check(ordinary.reads==0&&Collections.frequency(ordinary.commands,0x55)==1&&Collections.frequency(ordinary.commands,0x51)==1,"Each Aime key is tried once and failed authentication never proceeds to block reads");
        Reader ordinaryF=new Reader();ordinaryF.detected=hex("0120100123456789abcdef00f1000000014300");ordinaryF.felicaPayload=hex("0c070123456789abcdefffa1");AimeChannel nonAimeF=new AimeChannel(ordinaryF.port,60);nonAimeF.initialize();
        check(nonAimeF.poll().issue==AimeChannel.UNSUPPORTED_TYPE&&!nonAimeF.isClosed(),"A matching FeliCa missing-service response is unsupported and keeps transport open");
        Reader flakyF=new Reader();flakyF.detected=ordinaryF.detected;flakyF.felicaStatus=1;AimeChannel transientF=new AimeChannel(flakyF.port,60);transientF.initialize();
        AimeChannel.PollResult transientFirst=transientF.poll();
        check(transientFirst.issue==AimeChannel.READ_FAILED&&transientF.poll()==transientFirst&&flakyF.felicaReads==1&&!transientF.isClosed(),"A reader card error is reported and immediate repeat does not issue another FeliCa read");
        Reader flakyM=new Reader();flakyM.readStatus=1;AimeChannel transientM=new AimeChannel(flakyM.port,60);transientM.initialize();
        check(transientM.poll().issue==AimeChannel.READ_FAILED&&!transientM.isClosed(),"MIFARE read error retains USB for a bounded retry");
        Thread.sleep(1050);
        check(nonAime.poll()==unsupported&&ordinary.reads==0&&Collections.frequency(ordinary.commands,0x55)==1&&Collections.frequency(ordinary.commands,0x51)==1,"Held unsupported Type A card is not reauthenticated even after the old one-second retry interval");
        check(nonAimeF.poll().issue==AimeChannel.UNSUPPORTED_TYPE&&ordinaryF.felicaReads==1,"Held non-Aime FeliCa card is detected without repeating its rejected service request");
        check(transientF.poll().issue==AimeChannel.READ_FAILED&&flakyF.felicaReads==2&&!transientF.isClosed(),"A transient FeliCa card error permits exactly one paced retry");
        flakyM.readStatus=0;check(transientM.poll().issue==AimeChannel.NONE&&!transientM.isClosed(),"A transient MIFARE read failure can recover on the same CDC connection");
        Thread.sleep(1050);
        check(transientF.poll().issue==AimeChannel.READ_FAILED&&flakyF.felicaReads==2&&!transientF.isClosed(),"Repeated held-card read failure stops after two attempts without closing CDC");
        for(int status=1;status<=6;status++){
            ordinary.detectStatus=status;AimeChannel.PollResult detectError=nonAime.poll();
            check(detectError.present&&detectError.accessCode==null&&detectError.issue!=AimeChannel.NONE&&!nonAime.isClosed(),"Valid DETECT error keeps transport and does not fabricate removal or a usable card");
        }
        ordinary.detectStatus=0;check(nonAime.poll()==unsupported&&Collections.frequency(ordinary.commands,0x55)==1,"An intervening DETECT error does not rearm held-card authentication");
        ordinary.detected=hex("00");check(!nonAime.poll().present&&!nonAime.isClosed(),"Removal of unsupported card is still detected on the existing CDC connection");
        ordinary.failB=ordinary.failA=false;ordinary.detected=hex("01100401020304");check(nonAime.poll().issue==AimeChannel.NONE&&!nonAime.isClosed(),"A fresh valid Aime tap works after an unsupported card without reconnecting USB");
        ordinary.detected=hex("01100701020304050607");int beforeSeven=ordinary.commands.size();
        check(nonAime.poll().issue==AimeChannel.UNSUPPORTED_TYPE&&ordinary.commands.size()==beforeSeven+3,"Seven-byte UID tags are detected but never sent four-byte Aime authentication commands");
        ordinaryF.detected=hex("00");nonAimeF.poll();ordinaryF.detected=flakyF.detected;ordinaryF.felicaPayload=hex("1d070123456789abcdef000001893d691806eccfef90c8773d41eee231");
        check(nonAimeF.poll().issue==AimeChannel.NONE&&!nonAimeF.isClosed(),"A valid FeliCa card works after removing an unsupported card on the same connection");
        flakyF.detected=hex("00");transientF.poll();flakyF.detected=ordinaryF.detected;flakyF.felicaStatus=0;
        check(transientF.poll().issue==AimeChannel.NONE&&flakyF.felicaReads==3,"Card removal rearms a previously exhausted read attempt budget");
        nonAime.setReaderLed(0x102030);nonAime.setScanning(false);check(!ordinary.port.closed,"Reader LED and RF stop remain usable after unsupported and failed card reads");
        nonAime.close();nonAimeF.close();transientF.close();transientM.close();
        Reader quietReader=new Reader();quietReader.detected=hex("00");AimeChannel quiet=new AimeChannel(quietReader.port,60);quiet.initialize(false);
        check(quietReader.commands.equals(Arrays.asList(0x62,0x30,0x32,0x54,0x50,0x41)),"Initialization with RF disabled never sends START, including recovery after a scan-triggered USB reset");
        quiet.setScanning(true);long[] quietTimes=new long[2];java.util.function.Consumer<byte[]> quietReply=quietReader.port.onWrite;
        quietReader.port.onWrite=wire->{int command=unframe(wire)[3]&255;if(command==0x81)quietTimes[0]=System.nanoTime();if(command==0x42)quietTimes[1]=System.nanoTime();quietReply.accept(wire);};
        quiet.setReaderLed(0xffffff);quiet.poll();check(quietTimes[1]-quietTimes[0]>=TimeUnit.MILLISECONDS.toNanos(250),"RGB change leaves at least250ms before the next DETECT request");quiet.close();
        Reader pausedReader=new Reader();AimeChannel paused=new AimeChannel(pausedReader.port,60);paused.initialize();paused.poll();int before=pausedReader.commands.size();
        paused.setScanning(false);check(pausedReader.commands.size()==before&&pausedReader.commands.get(before-1)==0x41&&!pausedReader.port.closed,"Already stopped RF is not toggled again after the read");
        paused.setScanning(false);check(pausedReader.commands.size()==before,"Repeated pause sends nothing");
        try{paused.poll();throw new AssertionError("Paused scanner polled");}catch(IOException expected){check(!paused.isClosed(),"Paused scan rejected locally without port closure");}
        paused.setScanning(true);paused.poll();check(pausedReader.reads==4,"Restart after stopping reads held card afresh without stale cache");paused.close();
        UsbIo.Cdc ledPort=new UsbIo.Cdc();AimeChannel led=new AimeChannel(ledPort,25);List<byte[]> ledWrites=new ArrayList<>();
        ledPort.onWrite=wire->{byte[] request=unframe(wire);ledWrites.add(request);if((request[3]&255)==0xf5){ledPort.receive(AimeProtocolTest.response(0,request[2]&255,0xf5,1,new byte[0]));reply(ledPort,request,0,new byte[0]);}};
        led.setReaderLed(0x0068de);check(ledWrites.size()==2&&!led.isClosed(),"LED reset matches its node8 ACK and RGB write succeeds with no ACK");
        check(ledWrites.get(0)[1]==8&&(ledWrites.get(0)[3]&255)==0xf5&&ledWrites.get(0)[4]==0,"Reader LED normal mode is addressed to node8 once");
        check(ledWrites.get(1)[1]==8&&(ledWrites.get(1)[3]&255)==0x81&&Arrays.equals(Arrays.copyOfRange(ledWrites.get(1),5,8),hex("0068de")),"Reader LED gets exact RGB payload at node8");
        led.setReaderLed(0x0068de);check(ledWrites.size()==2,"Unchanged reader LED sends no traffic");led.setReaderLed(0x00ff00);check(ledWrites.size()==3&&(ledWrites.get(2)[3]&255)==0x81,"Changed LED updates without repeated initialization");
        check(led.diagnostic().contains("node=8 cmd=81 status=-5")&&led.diagnostic().contains("requests=3"),"No-ACK LED writes are distinguishable from completed request replies");
        try{led.setReaderLed(-1);throw new AssertionError("Invalid color accepted");}catch(IllegalArgumentException expected){check(ledWrites.size()==3,"Invalid RGB is rejected before sending commands");}
        ledPort.onWrite=wire->{byte[] request=unframe(wire);ledPort.receive(AimeProtocolTest.response(8,request[2]&255,0x81,0,new byte[0]));reply(ledPort,request,0,hex("00"));};
        check(Arrays.equals(led.request(0x42,new byte[0]),hex("00")),"Optional/late reader LED ACK cannot complete NFC card polling");led.close();
        UsbIo.Cdc matchPort=new UsbIo.Cdc();AimeChannel match=new AimeChannel(matchPort,60);
        matchPort.onWrite=wire->{byte[] req=unframe(wire);matchPort.receive(AimeProtocolTest.response(8,req[2]&255,req[3]&255,0,hex("11")));matchPort.receive(AimeProtocolTest.response(0,(req[2]+1)&255,req[3]&255,0,hex("22")));matchPort.receive(AimeProtocolTest.response(0,req[2]&255,0x32,0,hex("33")));reply(matchPort,req,0,hex("44"));};
        check(Arrays.equals(match.request(0x30,new byte[0]),hex("44")),"Address, sequence and command all matched");match.close();
        // The diagnostic constructor must leave USB open beyond the former3s
        // deadline so an independent physical detach can be observed first.
        Reader slowReader=new Reader();slowReader.detected=hex("00");AimeChannel slow=new AimeChannel(slowReader.port);
        ScheduledExecutorService responses=Executors.newSingleThreadScheduledExecutor();
        java.util.function.Consumer<byte[]> immediate=slowReader.port.onWrite;
        slowReader.port.onWrite=wire->{if((unframe(wire)[3]&255)==AimeProtocol.DETECT)responses.schedule(()->immediate.accept(wire),3500,TimeUnit.MILLISECONDS);else immediate.accept(wire);};
        try{
            slow.initialize();long began=System.nanoTime();AimeChannel.PollResult absent=slow.poll();
            check(!absent.present&&!slow.isClosed()&&!slowReader.port.closed,"Default DETECT accepts a delayed3500ms valid reply without closing CDC");
            check((System.nanoTime()-began)/1000000>=3400&&slow.diagnostic().contains("cmd=41 status=0"),"Regression exercises a real delay beyond the old3s deadline");
            slow.setScanning(false);check(!slowReader.port.closed&&slow.diagnostic().contains("cmd=41 status=0"),"Reader remains usable after a slow empty-field scan");
        }finally{slow.close();responses.shutdownNow();}
        UsbIo.Cdc silent=new UsbIo.Cdc();AimeChannel timed=new AimeChannel(silent,25);
        try{timed.request(0x42,new byte[0]);throw new AssertionError("Timeout expected");}catch(IOException expected){check(timed.isClosed()&&silent.closed,"Timeout closes port");check(timed.diagnostic().contains("cmd=42 status=-2")&&timed.diagnostic().contains("closed=1"),"Timeout records failed stage before releasing transport");}
        silent.receive(AimeProtocolTest.response(0,0,0x42,0,hex("00")));
        try{timed.request(0x42,new byte[0]);throw new AssertionError("Closed channel accepted late reply");}catch(IOException expected){checks++;}
        Reader silentCard=new Reader();silentCard.detected=hex("0120100123456789abcdef00f1000000014300");AimeChannel readTimeout=new AimeChannel(silentCard.port,25);
        java.util.function.Consumer<byte[]> normalReply=silentCard.port.onWrite;silentCard.port.onWrite=wire->{if((unframe(wire)[3]&255)!=AimeProtocol.FELICA)normalReply.accept(wire);};readTimeout.initialize();
        try{readTimeout.poll();throw new AssertionError("Silent card read accepted");}catch(IOException expected){check(readTimeout.isClosed()&&silentCard.port.closed&&readTimeout.cardRequestFailed(),"A missing serial response still closes CDC, distinct from a valid card-error reply");}
        ExecutorService executor=Executors.newSingleThreadExecutor();UsbIo.Cdc cancelPort=new UsbIo.Cdc();AimeChannel cancel=new AimeChannel(cancelPort,3000);CountDownLatch started=new CountDownLatch(1);cancelPort.onWrite=p->started.countDown();
        Future<Boolean> pending=executor.submit(()->{try{cancel.request(0x42,new byte[0]);return false;}catch(IOException expected){return true;}});
        check(started.await(1,TimeUnit.SECONDS),"Request awaiting reader");check(cancel.detectPending()&&cancel.diagnostic().contains("cmd=42 status=-1"),"In-flight DETECT diagnosis is readable without locking blocked request");
        check(cancel.cancelPending(),"USB event can cancel a pending request without synchronously closing its handle");check(pending.get(300,TimeUnit.MILLISECONDS)&&cancel.isClosed()&&cancel.cardRequestFailed(),"Pending USB cancellation wakes its worker and records a card transport failure");executor.shutdownNow();
        UsbIo.Cdc noResetAck=new UsbIo.Cdc();AimeChannel missingResetAck=new AimeChannel(noResetAck,25);AtomicInteger resetWrites=new AtomicInteger();noResetAck.onWrite=wire->resetWrites.incrementAndGet();
        try{missingResetAck.setReaderLed(0x123456);throw new AssertionError("LED reset accepted without ACK");}catch(IOException expected){check(resetWrites.get()==1&&missingResetAck.isClosed()&&!missingResetAck.cardRequestFailed(),"No RGB or DETECT can pass an unacknowledged LED reset, and LED failure is not a card-read failure");}
        UsbIo.Cdc failed=new UsbIo.Cdc();AimeChannel broken=new AimeChannel(failed,1000);failed.onWrite=p->failed.failure.accept("USB disconnected");
        try{broken.request(0x42,new byte[0]);throw new AssertionError("Disconnected reader accepted request");}catch(IOException expected){check(broken.isClosed(),"USB failure immediately cancels request");}
        System.out.println("PASS: "+checks+" Aime channel checks");
    }
    static void testRfTransportFailures()throws Exception{
        for(int command:new int[]{AimeProtocol.START,AimeProtocol.STOP}){
            Reader device=new Reader();device.detected=hex("00");AimeChannel channel=new AimeChannel(device.port,25);
            channel.initialize(false);java.util.function.Consumer<byte[]> responder=device.port.onWrite;
            device.port.onWrite=wire->{if((unframe(wire)[3]&255)!=command)responder.accept(wire);};
            try{channel.setScanning(true);channel.poll();throw new AssertionError("Missing RF acknowledgement accepted");}
            catch(IOException expected){check(channel.cardRequestFailed()&&channel.isClosed(),"RF ON and scan RF OFF failures enter card error/cooldown handling");}
        }
        Reader device=new Reader();AimeChannel channel=new AimeChannel(device.port,25);
        java.util.function.Consumer<byte[]> responder=device.port.onWrite;
        device.port.onWrite=wire->{if((unframe(wire)[3]&255)!=AimeProtocol.STOP)responder.accept(wire);};
        try{channel.initialize(false);throw new AssertionError("Silent initialization STOP accepted");}
        catch(IOException expected){check(channel.isClosed()&&!channel.cardRequestFailed(),"Initialization failure cannot block a game generation that does not yet exist");}
    }
    static void testCompatibleMifare()throws Exception{
        Reader r=new Reader();r.marker=new byte[16];
        AimeChannel c=new AimeChannel(r.port,60);c.initialize(false);r.commands.clear();
        c.setScanning(true);AimeChannel.PollResult result=c.poll();
        check(result.issue==AimeChannel.NONE&&"00123456789012345678".equals(result.accessCode),"Upstream MIFARE1K zero-block1 layout returns the stored code");
        check(r.commands.equals(Arrays.asList(0x40,0x42,0x43,0x55,0x52,0x52,0x41)),"Upstream capture order retains RF through SELECT/AUTH/READ, then STOP");
        check(!r.radioOn&&!c.isClosed(),"Compatible MIFARE success stops RF without closing USB");
        c.poll();check(r.reads==2&&!r.radioOn,"Held compatible card is not reread and RF is still stopped");
        r.detected=hex("00");c.poll();r.detected=hex("01100401020304");r.marker=hex("0102030405060708090a0b0c0d0e0f10");
        check("00123456789012345678".equals(c.poll().accessCode)&&!r.radioOn,"A user-provisioned custom block1 does not hide the valid stored Aime code");
        r.detected=hex("00");c.poll();r.detected=hex("01100401020304");r.accessBlock=new byte[16];
        check(c.poll().issue==AimeChannel.INVALID_CARD&&!r.radioOn&&!c.isClosed(),"Blank card with valid keys cannot submit an empty access code");
        r.detected=hex("00");c.poll();r.detected=hex("01100401020304");r.accessBlock=hex("000000000000fa123456789012345678");
        check(c.poll().issue==AimeChannel.INVALID_CARD&&!r.radioOn,"Non-BCD compatible-card contents fail safely with RF off");
        r.detected=hex("00");c.poll();r.detected=hex("01100401020304");r.readStatus=1;
        check(c.poll().issue==AimeChannel.READ_FAILED&&!r.radioOn&&!c.isClosed(),"A checked MIFARE read error also stops RF and keeps USB connected");c.close();
    }
    static void testReferenceRfCycle()throws Exception{
        Reader reader=new Reader();reader.detected=hex("00");List<Integer> commands=new ArrayList<>();List<Long> times=new ArrayList<>();
        java.util.function.Consumer<byte[]> reply=reader.port.onWrite;
        reader.port.onWrite=wire->{commands.add(unframe(wire)[3]&255);times.add(System.nanoTime());reply.accept(wire);};
        AimeChannel channel=new AimeChannel(reader.port,60);channel.initialize(false);commands.clear();times.clear();
        channel.setScanning(true);channel.poll();channel.poll();
        check(commands.equals(Arrays.asList(0x40,0x42,0x41,0x40,0x42,0x41)),"Each empty-field probe follows captured Windows ON/DETECT/OFF cycle");
        check(times.get(1)-times.get(0)>=TimeUnit.MILLISECONDS.toNanos(300)&&times.get(4)-times.get(3)>=TimeUnit.MILLISECONDS.toNanos(300),"Every DETECT waits for RF settling, not just the first scan");
        check(times.get(3)-times.get(2)>=TimeUnit.MILLISECONDS.toNanos(100),"RF remains off between probes");
        check(times.get(2)-times.get(1)>=TimeUnit.MILLISECONDS.toNanos(25),"Command turnaround matches Windows instead of sending on the USB receive callback");
        int before=commands.size();channel.setScanning(false);check(commands.size()==before,"Pausing the scanner preserves the already-off field");channel.close();
    }
    static void testWindowsLengthOnlyPolling()throws Exception{
        for(int mode=0;mode<4;mode++){
            final int testMode=mode;
            Reader device=new Reader();device.detected=hex("0120100123456789abcdef00f1000000014300");
            java.util.function.Consumer<byte[]> responder=device.port.onWrite;
            List<byte[]> polls=new ArrayList<>();List<Long> replied=new ArrayList<>();List<Long> sent=new ArrayList<>();
            device.port.onWrite=wire->{
                byte[] request=unframe(wire);
                if((request[3]&255)==AimeProtocol.FELICA&&request[14]==0){
                    polls.add(Arrays.copyOfRange(request,5,request.length));sent.add(System.nanoTime());
                    if(polls.size()==1||testMode==1){
                        byte[] payload=testMode==2?hex("00"):testMode==3?hex("0100"):hex("01");
                        reply(device.port,request,0,payload);replied.add(System.nanoTime());return;
                    }
                }
                responder.accept(wire);
            };
            AimeChannel reader=new AimeChannel(device.port,60);reader.initialize();
            try{
                AimeChannel.PollResult result=reader.poll();
                if(mode==0){
                    check(result.accessCode!=null&&result.issue==AimeChannel.NONE,"Windows length-only01 then valid Polling completes actual card reads");
                    check(polls.size()==2&&Arrays.equals(polls.get(0),polls.get(1)),"Repeat preserves selected card and FFFF/01/0F parameters exactly");
                    check(sent.get(1)-replied.get(0)>=TimeUnit.MILLISECONDS.toNanos(25),"Length-only reply keeps observed25ms turnaround");
                    check(device.felicaReads==1&&reader.diagnostic().contains("pollRetry=1"),"One successful SPAD0 read after one Polling repeat");
                    reader.poll();check(polls.size()==2&&device.felicaReads==1,"Held successful card does not repeat its two-stage Polling");
                }else{
                    check(result.issue==AimeChannel.READ_FAILED&&result.accessCode==null&&device.felicaReads==0,"Repeated short or malformed responses cannot reach SPAD0 or produce a card");
                    check(polls.size()==(mode==1?2:1),"Only exact01 repeats, at most once per attempt");
                    reader.poll();check(polls.size()==(mode==1?2:1),"Held failed card respects paced retry rather than a Polling loop");
                }
                check(!reader.isClosed()&&device.commands.get(device.commands.size()-1)==AimeProtocol.STOP,"Completed short replies preserve transport and final RF cleanup");
            }finally{reader.close();}
        }
    }
    static void testFelicaIdentification()throws Exception{
        Reader good=new Reader();good.detected=hex("0120100123456789abcdef00f1000000014300");
        AimeChannel reader=new AimeChannel(good.port,60);reader.initialize();
        check(reader.poll().issue==AimeChannel.NONE,"Known SEGA FeliCa format accepted");
        check(good.felicaOps.equals(Arrays.asList(0x100,0x82,0)),"Reference flow: addressed polling, manufacturer ID block, then SPAD0");
        reader.poll();check(good.felicaOps.size()==3,"Held card does not repeat system or manufacturer reads");reader.close();
        for(int mode=0;mode<5;mode++){
            Reader bad=new Reader();bad.detected=good.detected;
            if(mode==0)bad.polling[19]=0;
            if(mode==1)bad.polling[2]^=1;
            if(mode==2)bad.polling=Arrays.copyOf(bad.polling,19);
            if(mode==3)bad.identity[22]=0x68;
            if(mode==4)bad.identity[13]^=1;
            AimeChannel guarded=new AimeChannel(bad.port,60);guarded.initialize();AimeChannel.PollResult rejected=guarded.poll();
            check(rejected.accessCode==null&&bad.felicaReads==0,"Different system, malformed polling, another issuer, or mismatched ID never reads SPAD0");
            check(rejected.issue==(mode==0||mode==3?AimeChannel.UNSUPPORTED_TYPE:AimeChannel.READ_FAILED),"Unsupported format and malformed identity are distinguished");
            guarded.poll();check(bad.felicaReads==0&&!guarded.isClosed(),"Rejected identity leaves USB connected without immediate repeated reads");guarded.close();
        }
    }
    static void testRadioSelection()throws Exception{
        for(int type=1;type<=3;type++){
            Reader reader=new Reader();List<byte[]> requests=new ArrayList<>();
            java.util.function.Consumer<byte[]> responder=reader.port.onWrite;
            reader.port.onWrite=wire->{requests.add(unframe(wire));responder.accept(wire);};
            AimeChannel channel=new AimeChannel(reader.port,60);
            try{
                channel.setRadioType(type);channel.initialize(false);
                check(!reader.commands.contains(AimeProtocol.START),"Configuring a radio family during initialization leaves RF stopped");
                channel.setScanning(true);byte[] start=requests.get(requests.size()-1);
                check(start[3]==AimeProtocol.START&&start[4]==1&&start[5]==type,"RadioOn carries exactly the selected documented family mask");
                int count=requests.size();channel.setRadioType(type);channel.setScanning(true);
                check(requests.size()==count,"Unchanged family and scan state send no extra RF commands");
                int next=type%3+1;channel.setRadioType(next);
                check(requests.size()==count+1&&requests.get(count)[3]==AimeProtocol.STOP,"Changing an active family first acknowledges STOP and does not automatically restart");
                channel.setScanning(true);check(requests.get(requests.size()-1)[5]==next,"Explicit resume uses the new family");
                channel.setScanning(false);count=requests.size();channel.setRadioType(type);
                check(requests.size()==count,"Changing a paused family does not send USB traffic");
                for(int invalid:new int[]{-1,0,4,255}){
                    try{channel.setRadioType(invalid);throw new AssertionError("Invalid radio mask accepted");}
                    catch(IllegalArgumentException expected){check(requests.size()==count,"Invalid family never reaches hardware");}
                }
                channel.setScanning(true);
                check(requests.get(requests.size()-1)[5]==type&&channel.diagnostic().contains("radio="+type),"Resume and diagnostics retain validated family");
            }finally{channel.close();}
        }
        for(int selected:new int[]{1,2}){
            Reader reader=new Reader();reader.detected=selected==1?hex("0120100123456789abcdef00f1000000014300"):hex("01100401020304");
            AimeChannel channel=new AimeChannel(reader.port,60);
            try{
                channel.setRadioType(selected);channel.initialize();int before=reader.commands.size();
                AimeChannel.PollResult result=channel.poll();
                check(result.present&&result.accessCode==null&&result.issue==AimeChannel.UNSUPPORTED_TYPE,"Firmware ignoring the mask cannot submit another card family");
                check(reader.commands.size()==before+2&&reader.commands.get(before)==AimeProtocol.DETECT&&reader.commands.get(before+1)==AimeProtocol.STOP,"Excluded family is rejected without authentication, SELECT or FeliCa read");
            }finally{channel.close();}
        }
        Reader reader=new Reader();reader.detected=hex("00");AimeChannel channel=new AimeChannel(reader.port,1500);
        channel.initialize();CountDownLatch detecting=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<byte[]> held=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<byte[]> responder=reader.port.onWrite;
        reader.port.onWrite=wire->{if(unframe(wire)[3]==AimeProtocol.DETECT){held.set(wire);detecting.countDown();}else responder.accept(wire);};
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try{
            Future<AimeChannel.PollResult> poll=workers.submit(channel::poll);check(detecting.await(1,TimeUnit.SECONDS),"DETECT is outstanding before family change");
            Future<?> changed=workers.submit(()->{channel.setRadioType(2);return null;});Thread.sleep(80);
            check(!changed.isDone()&&reader.commands.get(reader.commands.size()-1)==AimeProtocol.START,"Mode change cannot interleave STOP into an outstanding DETECT");
            responder.accept(held.get());check(!poll.get(1,TimeUnit.SECONDS).present,"Outstanding request completes normally");changed.get(1,TimeUnit.SECONDS);
            check(reader.commands.get(reader.commands.size()-1)==AimeProtocol.STOP&&channel.diagnostic().contains("radio=2"),"Mode changes only after DETECT acknowledgement");
        }finally{channel.close();workers.shutdownNow();}
    }
}
