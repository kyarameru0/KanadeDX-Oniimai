package io.oniimai.kanade;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class CeilingOutputTest {
    private static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static void await(BooleanSupplier b,String label)throws Exception{long end=System.currentTimeMillis()+3000;while(!b.getAsBoolean()&&System.currentTimeMillis()<end)Thread.sleep(5);check(b.getAsBoolean(),label);}
    public static void main(String[] args)throws Exception{
        AtomicReference<UsbIo.Hid> handle=new AtomicReference<>();
        int[] saved=NativeBridge.snapshot;
        NativeBridge.snapshot=new int[]{255,1,1<<11,0,0,0,0,0,0,0,0,0,0,0,0x804020};
        CeilingOutput output=new CeilingOutput(handle::get,true);
        try{
            Thread.sleep(100);check(output.summary().contains("연결 대기"),"no independent HID claim without session handle");
            UsbIo.Hid first=new UsbIo.Hid();handle.set(first);
            await(()->first.rgb==0x804020,"native billboard RGB reaches IO4");
            int writes=first.writes;Thread.sleep(130);check(first.writes==writes,"static RGB is not resent");
            output.settings(true,50);await(()->first.rgb==0x402010,"ceiling uses user brightness");
            output.foreground(false);await(()->first.rgb==0,"background clears ceiling");
            output.foreground(true);await(()->first.rgb==0x402010,"foreground restores ceiling");
            output.settings(false,50);await(()->first.rgb==0,"toggle OFF clears ceiling");
            output.settings(true,50);await(()->first.rgb==0x402010,"toggle ON restores ceiling");
            output.test(0x0000ff);await(()->first.rgb==0x000080,"ceiling-only RGB test uses brightness");
            await(()->first.rgb==0x402010,"test expires to actual game RGB");
            first.fail=true;output.test(0xff0000);await(()->output.summary().contains("확인 필요"),"HID write failure stays on ceiling worker");
            UsbIo.Hid replacement=new UsbIo.Hid();handle.set(replacement);
            await(()->replacement.rgb==0x800000,"new shared HID handle restores output immediately");
            check(first.fail,"ceiling worker does not close or replace input transport");
            output.foreground(false);await(()->replacement.rgb==0,"pause cancels temporary color");
            output.foreground(true);await(()->replacement.rgb==0x402010,"resume uses game color after cancel");
            output.destroy();await(()->replacement.rgb==0,"destroy clears shared output");
        }finally{output.destroy();NativeBridge.snapshot=saved;}
        System.out.println("PASS: "+checks+" ceiling RGB worker checks");
    }
}
