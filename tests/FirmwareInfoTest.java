package io.oniimai.kanade;

import java.util.Arrays;

public final class FirmwareInfoTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("FirmwareInfo check "+checks);}
    private static byte[] response(long version,long build){
        byte[] data=new byte[16];for(int i=0;i<4;i++){data[i]=(byte)(version>>>(8*i));data[4+i]=(byte)(build>>>(8*i));}
        Arrays.fill(data,8,16,(byte)0x7f);return data;
    }
    private static void invalid(byte[] data){boolean rejected=false;try{new FirmwareInfo(data);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);}
    public static void main(String[] args){
        FirmwareInfo v=new FirmwareInfo(response(305,20260112));
        check(v.version.equals("00.00.03.05"));check(v.versionNumber==305);check(v.build==20260112);
        check(v.summary().equals("00.00.03.05 · build 20260112"));
        check(new FirmwareInfo(response(12345678,0xffffffffL)).summary().equals("12.34.56.78 · build 4294967295"));
        check(new FirmwareInfo(response(0,0)).version.equals("00.00.00.00"));
        check(new FirmwareInfo(response(99999999,0)).version.equals("99.99.99.99"));
        byte[] source=response(305,0x87654321L);FirmwareInfo copied=new FirmwareInfo(source);Arrays.fill(source,(byte)0);
        check(copied.versionNumber==305&&copied.build==0x87654321L);
        invalid(null);invalid(new byte[0]);invalid(new byte[15]);invalid(response(100000000,0));invalid(response(0xffffffffL,0));
        check(new FirmwareInfo(Arrays.copyOf(response(305,4),20)).build==4);
        String[] devices={"controller-a","controller-b","controller-a","controller-a"};
        String[] names={"onii-mai Touch","onii-mai Command","onii-mai Command","onii-mai NFC"};
        boolean[] hid={false,false,false,false};
        check(FirmwareInfo.commandPort(devices,names,hid,"controller-a")==2);
        check(FirmwareInfo.commandPort(devices,names,hid,"controller-b")==1);
        check(FirmwareInfo.commandPort(devices,names,hid,"controller-c")==-1);
        check(FirmwareInfo.commandPort(devices,names,hid,"")==-1);
        names[3]="oniimai Command";check(FirmwareInfo.commandPort(devices,names,hid,"controller-a")==-2);
        hid[3]=true;check(FirmwareInfo.commandPort(devices,names,hid,"controller-a")==2);
        names[2]="CDC-ACM IF8";check(FirmwareInfo.commandPort(devices,names,hid,"controller-a")==-1);
        System.out.println("PASS: "+checks+" read-only firmware INFO checks");
    }
}
