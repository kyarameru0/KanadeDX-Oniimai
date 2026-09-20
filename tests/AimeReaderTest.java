package io.oniimai.kanade;

import android.hardware.usb.UsbManager;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Simulated USB transport only. Never calls the game or sends synthetic IDs to a server. */
public final class AimeReaderTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static void await(BooleanSupplier condition,String label)throws Exception{
        await(condition,label,4000);
    }
    static void await(BooleanSupplier condition,String label,long timeout)throws Exception{
        long end=System.currentTimeMillis()+timeout;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(10);
        check(condition.getAsBoolean(),label);
    }
    static byte[] decode(byte[] frame){
        byte[] body=new byte[frame.length];int n=0;
        for(int i=1;i<frame.length;i++){int value=frame[i]&255;if(value==0xd0)value=(frame[++i]&255)+1;body[n++]=(byte)value;}
        return Arrays.copyOf(body,n-1);
    }
    static byte[] reply(byte[] request,byte[] data){
        return reply(request,0,data);
    }
    static byte[] reply(byte[] request,int status,byte[] data){
        byte[] body=new byte[6+data.length];body[0]=(byte)body.length;body[1]=request[1];body[2]=request[2];body[3]=request[3];body[4]=(byte)status;body[5]=(byte)data.length;
        System.arraycopy(data,0,body,6,data.length);return AimeProtocol.frame(body);
    }
    public static void main(String[] args)throws Exception{
        testEmptyFieldCadence();
        testRfCommandRecovery();
        AtomicInteger opens=new AtomicInteger(),accepted=new AtomicInteger(),transportErrors=new AtomicInteger(),ready=new AtomicInteger(0),reads=new AtomicInteger(),detects=new AtomicInteger(),radio=new AtomicInteger(),color=new AtomicInteger(-1),nativeColor=new AtomicInteger(-1);
        AtomicBoolean present=new AtomicBoolean(),silent=new AtomicBoolean();AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();
        AtomicLong scanGeneration=new AtomicLong(1),rfNow=new AtomicLong(1000);
        UsbIo.Cdc.opened=port->{opens.incrementAndGet();handle.set(port);port.onWrite=packet->{
            if(silent.get()){port.failure.accept("simulated USB failure");return;}
            byte[] request=decode(packet),data=new byte[0];int command=request[3]&255;
            if((request[1]&255)==8){if(command==0x81)color.set((request[5]&255)<<16|(request[6]&255)<<8|(request[7]&255));else port.receive(reply(request,data));return;}
            if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
            if(command==AimeProtocol.START)radio.set(1);if(command==AimeProtocol.STOP)radio.set(0);
            if(command==AimeProtocol.DETECT){detects.incrementAndGet();data=present.get()?new byte[]{1,0x10,4,1,2,3,4}:new byte[]{0};}
            if(command==AimeProtocol.READ){
                reads.incrementAndGet();data=new byte[16];
                if(request[9]==1){data[0]='S';data[1]='B';data[2]='S';data[3]='D';}
                else System.arraycopy(AimePresence.bcd("01234567890123456789"),0,data,6,10);
            }
            port.receive(reply(request,data));
        };};
        AimeReader reader=new AimeReader(new UsbManager(),ready::get,nativeColor::get,scanGeneration::get,(code,gen)->{accepted.incrementAndGet();ready.set(2);return true;},(code,gen)->transportErrors.incrementAndGet(),rfNow::get);
        try{
            reader.start(new UsbIo.Port());await(()->reader.summary().contains("리더 연결됨"),"named port handshake completed");
            present.set(true);Thread.sleep(550);
            check(reads.get()==0&&detects.get()==0&&radio.get()==0,"no RF polling or authentication outside game scan");ready.set(1);
            await(()->accepted.get()==1,"scanning game receives card");Thread.sleep(600);
            check(accepted.get()==1&&reads.get()==2&&detects.get()==1&&radio.get()==0,"successful card stops RF with no repeated detect/authenticate");
            check(color.get()==0x0000ff,"successful physical read lights reader blue without ACK");
            check(!reader.diagnostic().contains("0123456789"),"diagnostic excludes card code");
            String failureReport=reader.failureDiagnostic();
            check(failureReport.contains("cmd=")&&!failureReport.contains("0123456789")&&!failureReport.contains("[1, 2, 3, 4]"),"failure history retains command metadata without card number or UID");
            check(failureReport.split("\n").length<=25,"failure snapshot retains at most 24 command events");
            reader.foreground(false);await(()->color.get()==0&&reader.summary().contains("백그라운드"),"background stops RF and turns reader LED off");
            check(!handle.get().closed&&radio.get()==0,"background preserves CDC without DTR toggle");
            int count=opens.get();Thread.sleep(700);check(opens.get()==count,"background opens no new handle");
            reader.foreground(true);nativeColor.set(0xffeb04);
            await(()->color.get()==0xffeb04,"native game warning color reaches reader");
            check(opens.get()==count&&!handle.get().closed,"resume reuses NFC handle");
            reader.led(false);await(()->color.get()==0,"reader LED toggle OFF clears hardware");reader.led(true);
            await(()->color.get()==0xffeb04,"reader LED toggle ON restores game color");
            Thread.sleep(400);check(accepted.get()==1,"resume with held card cannot duplicate login");
            int beforeRemoval=detects.get();ready.set(1);present.set(false);await(()->detects.get()>=beforeRemoval+3,"empty-field RF cycles establish card removal");present.set(true);await(()->accepted.get()==2,"card removal rearms reader in next game scan");
            present.set(false);Thread.sleep(550);present.set(true);scanGeneration.incrementAndGet();ready.set(1);
            await(()->accepted.get()==3,"new game scan rearms card removed while RF was stopped");
            ready.set(1);
            silent.set(true);await(()->reader.summary().contains("재연결"),"USB read failure schedules independent retry");
            check(reader.running()&&handle.get().closed,"failure closes only NFC handle while session stays enabled");
            check(transportErrors.get()==0,"an earlier successful card cannot justify a new START failure as another card event");
            check(reader.failureDiagnostic().contains("\n+")&&!reader.failureDiagnostic().contains("0123456789"),"closed reader retains safe command history after transport cleanup");
            UsbIo.replacement=new UsbIo.Port();
            silent.set(false);await(()->!handle.get().closed&&(reader.summary().contains("카드를 리더")||reader.summary().contains("자동 재시도")),"reconnect succeeds without settings panel; only failed card requests require RF cooldown");
            check(handle.get().selected==UsbIo.replacement,"stable identity resolves replacement USB address");
            check(accepted.get()==3,"short link failure with held card cannot duplicate login");
            final UsbIo.Cdc lost=handle.get();reader.detached();await(()->lost.closed,"USB detach promptly closes dead NFC handle");
            await(()->handle.get()!=lost&&!handle.get().closed,"USB detach restores reader through saved identity");
            check(accepted.get()==3,"USB reenumeration does not resubmit held card");
            silent.set(true);present.set(false);rfNow.addAndGet(15000);scanGeneration.incrementAndGet();await(()->reader.summary().contains("재연결"),"second RF failure schedules retry after cooldown and a new scan");
            reader.stop();count=opens.get();Thread.sleep(1150);check(opens.get()==count&&!reader.running(),"OFF cancels pending retry");
        }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
        testErrorFeedbackAndScanContainment();
        testStaleScanGeneration();
        testRfCooldown();
        testRadioRecovery();
        System.out.println("PASS: "+checks+" Aime USB worker/lifecycle checks");
    }
    static void testRadioRecovery()throws Exception{
        AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();AtomicInteger radio=new AtomicInteger(),detects=new AtomicInteger(),ready=new AtomicInteger(1);
        UsbIo.Cdc.opened=port->{handle.set(port);port.onWrite=packet->{
            byte[] request=decode(packet),data=new byte[0];int command=request[3]&255;
            if((request[1]&255)==8&&command==0x81)return;
            if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
            if(command==AimeProtocol.START)radio.set(request[5]&255);if(command==AimeProtocol.STOP)radio.set(0);
            if(command==AimeProtocol.DETECT){detects.incrementAndGet();data=new byte[]{0};}
            port.receive(reply(request,data));
        };};
        AimeReader reader=new AimeReader(new UsbManager(),ready::get,code->{throw new AssertionError("Empty field submitted a card");});
        try{
            reader.radioType(2);reader.start(new UsbIo.Port());await(()->detects.get()>0&&radio.get()==2,"Saved FeliCa mode is applied to the worker's initial scan");
            ready.set(0);await(()->radio.get()==0,"Game ends RF scan before unrelated USB removal");
            UsbIo.Cdc old=handle.get();reader.detached();await(()->handle.get()!=old&&!handle.get().closed,"NFC transport reconnects with the selected family");
            ready.set(1);await(()->radio.get()==2,"USB reconnect preserves FeliCa selection instead of restoring both families");
            reader.foreground(false);await(()->radio.get()==0,"Background scan is paused before changing family");int before=detects.get();reader.radioType(1);Thread.sleep(550);
            check(radio.get()==0&&detects.get()==before,"Changing family never starts polling in the background");
            reader.foreground(true);await(()->radio.get()==1&&detects.get()>before,"Foreground resumes with MIFARE-only mask");
        }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
    }
    static void testErrorFeedbackAndScanContainment()throws Exception{
        AtomicInteger mode=new AtomicInteger(),errors=new AtomicInteger(),lastError=new AtomicInteger(),detects=new AtomicInteger(),emptyReports=new AtomicInteger(),heldRequests=new AtomicInteger(),unknownReports=new AtomicInteger(),radio=new AtomicInteger(),color=new AtomicInteger(-1);
        AtomicBoolean holdDetect=new AtomicBoolean();AtomicLong scanGeneration=new AtomicLong(1),reportedGeneration=new AtomicLong(-1),rfNow=new AtomicLong(1000);AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();
        UsbIo.Cdc.opened=port->{handle.set(port);port.onWrite=packet->{
            byte[] request=decode(packet),data=new byte[0];int command=request[3]&255,status=0;
            if((request[1]&255)==8){if(command==0x81){color.set((request[5]&255)<<16|(request[6]&255)<<8|(request[7]&255));return;}}
            if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
            if(command==AimeProtocol.START)radio.set(1);if(command==AimeProtocol.STOP)radio.set(0);
            if(command==AimeProtocol.DETECT){
                detects.incrementAndGet();if(holdDetect.get()){heldRequests.incrementAndGet();return;}
                if(mode.get()==0){data=new byte[]{0};emptyReports.incrementAndGet();}
                else if(mode.get()==5){status=1;unknownReports.incrementAndGet();}
                else if(mode.get()==1)data=new byte[]{1,0x10,7,1,2,3,4,5,6,7};
                else if(mode.get()==2)data=new byte[]{2,0x10,4,1,2,3,4,0x10,4,5,6,7,8};
                else data=new byte[]{1,0x10,4,1,2,3,4};
            }
            if(command==AimeProtocol.READ){if(mode.get()==3)status=1;else data=new byte[16];}
            port.receive(reply(request,status,data));
        };};
        AimeReader reader=new AimeReader(new UsbManager(),()->1,()->0xffffff,scanGeneration::get,(code,gen)->{throw new AssertionError("Error test submitted a card");},(code,gen)->{lastError.set(code);reportedGeneration.set(gen);errors.incrementAndGet();},rfNow::get);
        try{
            reader.start(new UsbIo.Port());await(()->emptyReports.get()>=2,"reader polls empty field with RF active");check(errors.get()==0,"no-card report does not show a game error");
            mode.set(1);await(()->errors.get()==1,"unsupported physical card produces game error");check(lastError.get()==AimeChannel.UNSUPPORTED_TYPE,"unsupported card retains error category");
            Thread.sleep(750);check(errors.get()==1,"held unsupported card does not repeatedly show errors");
            mode.set(5);await(()->unknownReports.get()>=2,"reader reports DETECT errors without a card list");
            check(errors.get()==1,"DETECT error without new card evidence does not become a game read event");
            int beforeUnknown=detects.get();mode.set(1);await(()->detects.get()>beforeUnknown,"positive card report follows uncertain detector response");Thread.sleep(200);
            check(errors.get()==1,"uncertain detector response does not rearm held-card error feedback");
            scanGeneration.incrementAndGet();await(()->errors.get()==2,"new explicit game scan allows a new error result");
            for(int testMode=2;testMode<=4;testMode++){
                int empties=emptyReports.get(),beforeErrors=errors.get();mode.set(0);await(()->emptyReports.get()>empties,"card removal observed before next error case");mode.set(testMode);
                await(()->errors.get()==beforeErrors+1,"new card-presence episode produces exactly one error");
                check(lastError.get()==testMode,"multiple/read/invalid failures keep their distinct game error categories");
            }
            int empties=emptyReports.get();mode.set(0);await(()->emptyReports.get()>empties,"empty report rearms error feedback before USB reset test");
            holdDetect.set(true);await(()->heldRequests.get()==1&&reader.diagnostic().contains("cmd=42 status=-1"),"DETECT is outstanding before physical detach simulation");
            UsbIo.Cdc old=handle.get();int previousErrors=errors.get();
            AtomicBoolean observedStall=new AtomicBoolean();
            await(()->{if(reader.takeDetectStall())observedStall.set(true);return observedStall.get();},"slow DETECT is observable before the 10-second timeout");
            check(!reader.takeDetectStall(),"one early snapshot per outstanding DETECT");
            check(!old.closed&&heldRequests.get()==1&&errors.get()==previousErrors,"early snapshot does not close USB, retry DETECT or invent a game error");
            reader.detached();
            await(()->old.closed&&reader.diagnostic().contains("Issue: 5"),"physical detach cancels pending worker and records transport failure locally");
            check(errors.get()==previousErrors,"empty-field USB failure cannot advance game entry");
            check(lastError.get()==AimeChannel.INVALID_CARD,"idle failure does not overwrite the game's last real card error");
            await(()->handle.get()!=old&&!handle.get().closed&&reader.summary().contains("자동 재시도"),"NFC handle recovers while RF-only retry countdown remains active");
            int blockedDetects=detects.get();Thread.sleep(550);check(detects.get()==blockedDetects&&radio.get()==0&&color.get()==0xffffff,"blocked recovery preserves reader LED but sends no START or DETECT");
            reader.foreground(false);Thread.sleep(350);reader.foreground(true);Thread.sleep(650);
            check(detects.get()==blockedDetects&&radio.get()==0&&reader.summary().contains("자동 재시도"),"focus alone cannot restart the failed game scan");
            UsbIo.Cdc recovered=handle.get();reader.detached();await(()->recovered.closed&&handle.get()!=recovered&&!handle.get().closed,"unrelated later USB reconnect restores the same NFC worker");Thread.sleep(550);
            check(detects.get()==blockedDetects&&radio.get()==0,"USB reconnect alone cannot restart the failed game scan");
            holdDetect.set(false);rfNow.addAndGet(15000);
            await(()->detects.get()>blockedDetects&&radio.get()==1&&!reader.summary().contains("자동 재시도"),"idle transport recovery resumes in the same game scan after cooldown without forcing entry");
            check(errors.get()==previousErrors,"empty field after same-scan recovery does not create a game error");
            final int beforeStop=heldRequests.get();holdDetect.set(true);await(()->heldRequests.get()>beforeStop,"request pending before reader OFF");
            UsbIo.Cdc stopping=handle.get();reader.stop();await(()->stopping.closed,"OFF cancels10s DETECT without waiting for its deadline");
            check(errors.get()==previousErrors,"explicit OFF cancellation does not report a game read error");
            final int beforeDestroy=heldRequests.get();reader.start(new UsbIo.Port());await(()->heldRequests.get()>beforeDestroy,"request pending before destroy");
            UsbIo.Cdc destroying=handle.get();reader.destroy();await(()->destroying.closed,"destroy cancels10s DETECT promptly");
            check(errors.get()==previousErrors,"destroy cancellation does not report a game read error");
        }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
    }
    static void testStaleScanGeneration()throws Exception{
        AtomicInteger ready=new AtomicInteger(1),accepted=new AtomicInteger(),errors=new AtomicInteger(),emptyReports=new AtomicInteger();
        AtomicLong generation=new AtomicLong(1),submittedGeneration=new AtomicLong(-1);AtomicBoolean present=new AtomicBoolean(true),holdRead=new AtomicBoolean(true);
        AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();AtomicReference<byte[]> pendingRead=new AtomicReference<>();
        UsbIo.Cdc.opened=port->{handle.set(port);port.onWrite=packet->{
            byte[] request=decode(packet),data=new byte[0];int command=request[3]&255;
            if((request[1]&255)==8&&command==0x81)return;
            if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
            if(command==AimeProtocol.DETECT){if(present.get())data=new byte[]{1,0x10,4,1,2,3,4};else{data=new byte[]{0};emptyReports.incrementAndGet();}}
            if(command==AimeProtocol.READ){
                data=new byte[16];if(request[9]==1){data[0]='S';data[1]='B';data[2]='S';data[3]='D';}
                else{if(holdRead.get()){pendingRead.set(request);return;}System.arraycopy(AimePresence.bcd("01234567890123456789"),0,data,6,10);}
            }
            port.receive(reply(request,data));
        };};
        AimeReader reader=new AimeReader(new UsbManager(),ready::get,()->0,generation::get,(code,gen)->{submittedGeneration.set(gen);accepted.incrementAndGet();ready.set(0);return true;},(code,gen)->errors.incrementAndGet());
        try{
            reader.start(new UsbIo.Port());await(()->pendingRead.get()!=null,"successful card block pending in first game scan");
            generation.set(2);present.set(false);holdRead.set(false);byte[] data=new byte[16];System.arraycopy(AimePresence.bcd("01234567890123456789"),0,data,6,10);
            handle.get().receive(reply(pendingRead.getAndSet(null),data));await(()->emptyReports.get()>0,"next scan observes card removal after stale successful reply");
            check(accepted.get()==0&&errors.get()==0,"old scan's successful card response is neither submitted nor reported as an error to a new scan");
            present.set(true);await(()->accepted.get()==1,"fresh card in current scan is submitted");check(submittedGeneration.get()==2,"successful submit carries its original scan generation for native atomic verification");
            Thread.sleep(350);generation.set(3);holdRead.set(true);ready.set(1);await(()->pendingRead.get()!=null,"card read pending before a second scan transition");
            generation.set(4);present.set(false);holdRead.set(false);int previousEmpty=emptyReports.get();handle.get().receive(reply(pendingRead.getAndSet(null),1,new byte[0]));
            await(()->emptyReports.get()>previousEmpty,"new scan can poll after an old scan read error");check(accepted.get()==1&&errors.get()==0,"stale scan's read error cannot interrupt the new scan");
        }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
    }
    static void testRfCommandRecovery()throws Exception{
        for(int failedCommand:new int[]{AimeProtocol.START,AimeProtocol.STOP}){
            AtomicInteger ready=new AtomicInteger(),errors=new AtomicInteger(),lastError=new AtomicInteger(),rfRequests=new AtomicInteger();
            AtomicLong scanGeneration=new AtomicLong(1),rfNow=new AtomicLong(1000),reportedGeneration=new AtomicLong(-1);
            AtomicBoolean fail=new AtomicBoolean(true);AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();
            UsbIo.Cdc.opened=port->{handle.set(port);port.onWrite=packet->{
                byte[] request=decode(packet),data=new byte[0];int command=request[3]&255;
                if((request[1]&255)==8&&command==0x81)return;
                if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
                if(command==AimeProtocol.START||command==AimeProtocol.DETECT)rfRequests.incrementAndGet();
                if(ready.get()==1&&command==failedCommand&&fail.compareAndSet(true,false)){port.failure.accept("simulated RF command disconnect");return;}
                if(command==AimeProtocol.DETECT)data=new byte[]{0};
                port.receive(reply(request,data));
            };};
            AimeReader reader=new AimeReader(new UsbManager(),ready::get,()->0,scanGeneration::get,(code,gen)->{throw new AssertionError("No card exists");},(code,gen)->{lastError.set(code);reportedGeneration.set(gen);errors.incrementAndGet();},rfNow::get);
            try{
                reader.start(new UsbIo.Port());await(()->reader.summary().contains("리더 연결됨"),"RF error test initializes before game scan");
                UsbIo.Cdc before=handle.get();ready.set(1);
                await(()->reader.diagnostic().contains("Issue: 5"),"RF command disconnect is recorded as a local transport fault");
                check(errors.get()==0&&reportedGeneration.get()==-1,"START and empty-field STOP errors do not fabricate card entry");
                check(reader.diagnostic().contains("Issue: 5"),"RF command transport failure is visible in diagnostic status");
                await(()->handle.get()!=before&&!handle.get().closed&&reader.summary().contains("자동 재시도"),"RF command failure reconnects CDC with cooldown");
                int attempts=rfRequests.get();scanGeneration.incrementAndGet();Thread.sleep(650);
                check(rfRequests.get()==attempts&&errors.get()==0,"New game scan cannot bypass RF cooldown or fabricate a card error after START or STOP failure");
                rfNow.addAndGet(15000);await(()->rfRequests.get()>attempts,"New scan resumes automatically after RF command cooldown");
            }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
        }
    }
    static void testRfCooldown()throws Exception{
        AtomicLong scanGeneration=new AtomicLong(1),rfNow=new AtomicLong(1000);
        AtomicInteger ready=new AtomicInteger(1),held=new AtomicInteger(),accepted=new AtomicInteger(),errors=new AtomicInteger(),radio=new AtomicInteger(),color=new AtomicInteger(-1),nativeColor=new AtomicInteger(0);
        AtomicBoolean stall=new AtomicBoolean(true);AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();
        UsbIo.Cdc.opened=port->{handle.set(port);port.onWrite=packet->{
            byte[] request=decode(packet),data=new byte[0];int command=request[3]&255;
            if((request[1]&255)==8&&command==0x81){color.set((request[5]&255)<<16|(request[6]&255)<<8|(request[7]&255));return;}
            if(command==AimeProtocol.FW||command==AimeProtocol.HW)data=new byte[]{1};
            if(command==AimeProtocol.START)radio.set(1);if(command==AimeProtocol.STOP)radio.set(0);
            if(command==AimeProtocol.DETECT)data=new byte[]{1,0x10,4,1,2,3,4};
            if(command==AimeProtocol.SELECT&&stall.get()){held.incrementAndGet();return;}
            if(command==AimeProtocol.READ){data=new byte[16];if(request[9]==1){data[0]='S';data[1]='B';data[2]='S';data[3]='D';}else System.arraycopy(AimePresence.bcd("01234567890123456789"),0,data,6,10);}
            port.receive(reply(request,data));
        };};
        AimeReader reader=new AimeReader(new UsbManager(),ready::get,nativeColor::get,scanGeneration::get,(code,gen)->{accepted.incrementAndGet();ready.set(2);return true;},(code,gen)->errors.incrementAndGet(),rfNow::get);
        try{
            reader.start(new UsbIo.Port());await(()->held.get()==1,"RF cooldown test begins with SELECT after a confirmed card");
            long[] delays={15000,30000,60000,60000};
            for(int i=0;i<delays.length;i++){
                UsbIo.Cdc old=handle.get();int beforeHeld=held.get(),beforeErrors=errors.get();
                // Cover either callback ordering: USB failure may close the channel
                // before Android broadcasts its detach notification.
                if(i==1)old.failure.accept("simulated physical removal");else reader.detached();
                await(()->old.closed&&errors.get()==beforeErrors+1,"confirmed card plus failed SELECT sends exactly one game read error");
                await(()->handle.get()!=old&&!handle.get().closed&&reader.summary().contains("자동 재시도"),"CDC reconnect is independent of RF cooldown",12000);
                check(reader.summary().contains((delays[i]/1000)+"초"),"RF retry uses 15, 30, 60 seconds with a 60-second cap");
                scanGeneration.addAndGet(3);nativeColor.set(0x102030+i);
                final int expectedColor=nativeColor.get();await(()->color.get()==expectedColor,"reader LED updates while RF recovery waits");
                rfNow.addAndGet(delays[i]-1);Thread.sleep(550);
                check(held.get()==beforeHeld&&radio.get()==0,"automatic game scan generations cannot bypass RF cooldown");
                if(i==delays.length-1)stall.set(false);
                rfNow.incrementAndGet();
                if(i<delays.length-1)await(()->held.get()==beforeHeld+1,"RF resumes when its cooldown and new-scan conditions are met");
                else await(()->accepted.get()==1,"valid physical card read succeeds after repeated RF failures");
            }
            stall.set(true);final int previousHeld=held.get();scanGeneration.incrementAndGet();ready.set(1);
            await(()->held.get()==previousHeld+1,"new scan waits for a card after successful read");
            UsbIo.Cdc old=handle.get();reader.detached();
            await(()->handle.get()!=old&&!handle.get().closed&&reader.summary().contains("15초"),"successful card read resets RF escalation to 15 seconds",12000);
            final int beforeRestart=held.get();reader.stop();reader.start(new UsbIo.Port());
            await(()->held.get()==beforeRestart+1,"explicit OFF/ON resets RF cooldown without waiting or a retry button");
            check(accepted.get()==1,"cooldown and transport recovery never fabricate card submissions");
        }finally{reader.destroy();UsbIo.Cdc.opened=null;UsbIo.replacement=null;}
    }
    static void testEmptyFieldCadence(){
        AimeReader.EmptyFieldCadence cadence=new AimeReader.EmptyFieldCadence();long now=1000;
        check(cadence.due(now),"initial scan is immediately eligible");
        for(int i=0;i<12;i++){
            cadence.completed(false,now);int gap=i<7?250:1000;
            check(cadence.gapMillis()==gap,"empty-field delay is capped after eight misses");
            check(!cadence.due(now+gap-1)&&cadence.due(now+gap),"no early retry and no permanent idle disable");
            now+=gap+550;
        }
        cadence.completed(true,now);check(cadence.gapMillis()==250&&cadence.due(now+250),"physical presence restores fast removal tracking");
        for(int i=0;i<8;i++)cadence.completed(false,now);
        cadence.reset();check(cadence.gapMillis()==250&&cadence.due(now),"leaving scan resets the delay for the next entry");
    }
}
