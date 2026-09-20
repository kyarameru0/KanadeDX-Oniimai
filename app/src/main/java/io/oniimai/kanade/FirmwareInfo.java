package io.oniimai.kanade;

import java.util.Locale;

/** Read-only INFO response. The serial-number bytes are deliberately not retained. */
final class FirmwareInfo {
    final long versionNumber,build;
    final String version;
    FirmwareInfo(byte[] data){
        if(data==null||data.length<16)throw new IllegalArgumentException("Invalid controller INFO response");
        versionNumber=u32(data,0);build=u32(data,4);
        if(versionNumber>99999999L)throw new IllegalArgumentException("Invalid controller firmware version");
        String digits=String.format(Locale.ROOT,"%08d",versionNumber);
        version=digits.substring(0,2)+"."+digits.substring(2,4)+"."+digits.substring(4,6)+"."+digits.substring(6,8);
    }
    private static long u32(byte[] data,int at){
        return (data[at]&255L)|((data[at+1]&255L)<<8)|((data[at+2]&255L)<<16)|((data[at+3]&255L)<<24);
    }
    String summary(){return version+" · build "+build;}
    /** A named Command interface on precisely the selected USB enumeration. */
    static int commandPort(String[] devices,String[] names,boolean[] hid,String selectedDevice){
        if(selectedDevice==null||selectedDevice.isEmpty())return -1;
        if(devices==null||names==null||hid==null||devices.length!=names.length||devices.length!=hid.length)
            throw new IllegalArgumentException("Port descriptions differ in length");
        int found=-1;
        for(int i=0;i<devices.length;i++)if(selectedDevice.equals(devices[i])&&PortSelection.role(names[i],hid[i])==PortSelection.COMMAND){
            if(found>=0)return -2;found=i;
        }
        return found;
    }
}
