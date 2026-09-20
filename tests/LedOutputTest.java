package io.oniimai.kanade;
import android.hardware.usb.UsbManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

public class LedOutputTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static void await(BooleanSupplier condition,String label)throws Exception{long end=System.currentTimeMillis()+3000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(5);check(condition.getAsBoolean(),label);}
    public static void main(String[] args)throws Exception{
        List<byte[]> packets=Collections.synchronizedList(new ArrayList<>());AtomicReference<UsbIo.Cdc> handle=new AtomicReference<>();AtomicBoolean silent=new AtomicBoolean();AtomicInteger opens=new AtomicInteger();
        UsbIo.Cdc.opened=port->{opens.incrementAndGet();handle.set(port);port.onWrite=encoded->{
            byte[] p=LedTest.decode(encoded);packets.add(p);
            if(!silent.get()&&(p[3]&255)!=0x7d){byte[] data=(p[3]&255)==0xf0?"15070-04xx".getBytes(StandardCharsets.US_ASCII):new byte[0];port.receive(LedTest.ack(p[0]&255,p[3]&255,1,1,data));}
        };};
        LedOutput output=new LedOutput(new UsbManager(),true);
        try{
            output.settings(100,0,false,false);output.start(new UsbIo.Port(),17,0);
            await(()->output.summary().contains("송신 1"),"initial complete frame acknowledged");
            check(rgb(packets,0)==0xff0000&&rgb(packets,7)==0xffffff,"native snapshot to physical endpoints");
            int n=packets.size();Thread.sleep(130);check(packets.size()==n,"static frames are not queued repeatedly");
            output.settings(50,1,true,true);await(()->rgb(packets,1)==0x800000,"live brightness and mapping");await(()->has(packets,0x39),"optional ring command");check(last(packets,0x39)[4]==64&&last(packets,0x39)[5]==32&&last(packets,0x39)[6]==16,"body ring side use independent game values");
            output.settings(50,1,true,false);await(()->last(packets,0x39)[4]==0,"ring OFF clears previously enabled FET");
            output.foreground(false);await(()->output.summary().contains("백그라운드"),"pause completes blackout");check(rgb(packets,0)==0&&rgb(packets,7)==0,"pause clears both ends");
            output.foreground(true);await(()->rgb(packets,1)==0x800000,"resume restores game colors");
            output.test(0x0000ff);await(()->rgb(packets,0)==128&&rgb(packets,7)==128,"temporary color test uses brightness");
            await(()->rgb(packets,1)==0x800000,"test expires back to game colors");
            output.stop();await(()->handle.get().closed,"stop closes LED handle");check(rgb(packets,0)==0&&rgb(packets,7)==0,"stop clears lamps");
            silent.set(true);output.start(new UsbIo.Port(),17,0);
            await(()->output.summary().contains("재연결 대기"),"handshake timeout schedules recovery");
            check(output.running(),"enabled LED session survives a timeout");
            output.foreground(false);int before=opens.get();Thread.sleep(1150);check(opens.get()==before,"background retry opens no USB handles");
            silent.set(false);UsbIo.replacement=new UsbIo.Port();output.foreground(true);
            await(()->output.summary().contains("LED 연동 중"),"foreground automatically reconnects without settings");
            check(handle.get().selected==UsbIo.replacement,"reconnect uses newly enumerated stable port");
            UsbIo.Cdc failed=handle.get();silent.set(true);
            await(()->output.summary().contains("재연결 대기"),"static gameplay link failure detected by heartbeat");
            check(failed.closed,"failed handle closed before retry");silent.set(false);
            await(()->output.summary().contains("LED 연동 중"),"gameplay LED restores without reopening panel");
            check(handle.get()!=failed,"recovery opens a new handle");
            silent.set(true);output.test(0x123456);await(()->output.summary().contains("재연결 대기"),"test write failure schedules retry");
            before=opens.get();output.stop();Thread.sleep(1150);check(opens.get()==before&&!output.running(),"explicit OFF cancels pending reconnect");
        }finally{output.destroy();}
        await(()->handle.get().closed,"destroy closes worker-owned handle");
        System.out.println("PASS: "+checks+" LED worker/lifecycle checks");
    }
    static boolean has(List<byte[]> packets,int cmd){return last(packets,cmd)!=null;}
    static byte[] last(List<byte[]> packets,int cmd){synchronized(packets){for(int i=packets.size()-1;i>=0;i--)if((packets.get(i)[3]&255)==cmd)return packets.get(i);}return null;}
    static int rgb(List<byte[]> packets,int index){synchronized(packets){for(int i=packets.size()-1;i>=0;i--){byte[] p=packets.get(i);if((p[3]&255)==0x31&&(p[4]&255)==index)return (p[5]&255)<<16|(p[6]&255)<<8|(p[7]&255);}}return -1;}
}
