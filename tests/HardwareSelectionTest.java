package io.oniimai.kanade;
import java.util.*;
public final class HardwareSelectionTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static void invalid(Runnable action,String name){try{action.run();throw new AssertionError(name);}catch(IllegalArgumentException expected){checks++;}}
    static void word(byte[] report,int bank,int value){report[29+bank*2]=(byte)value;report[30+bank*2]=(byte)(value>>8);}
    public static void main(String[] args){
        String[] names={"onii-mai Command","onii-mai Touch","onii-mai LED","I/O CONTROL BD;15257;01;00","onii-mai NFC","onii-mai Keyboard"};
        boolean[] hid={false,false,false,true,false,true};
        check(PortSelection.automaticTouch(names,hid)==1,"Touch selected by role, never first command CDC");
        check(PortSelection.named(names,hid,PortSelection.IO4)==3,"IO4 actual firmware name selected instead of keyboard");
        check(PortSelection.named(names,hid,PortSelection.LED)==2,"LED role selected");
        check(PortSelection.role(names[4],false)==PortSelection.NFC,"NFC has its own role, excluded from Touch");
        check(PortSelection.named(names,hid,PortSelection.NFC)==4,"Aime reader selected by NFC name");
        check(PortSelection.named(new String[]{names[4],names[4]},new boolean[]{false,false},PortSelection.NFC)==-2,"ambiguous NFC readers require manual choice");
        check(PortSelection.role("onii-mai NFC",true)==PortSelection.NONE,"HID interface cannot become serial NFC");
        check(PortSelection.role(names[5],true)==PortSelection.NONE,"keyboard excluded from IO4");
        check(PortSelection.role("Other Touch",false)==PortSelection.NONE,"unrelated device not assigned");
        check(PortSelection.protocol(names[0],false,1)==0&&PortSelection.protocol(names[1],false,0)==1,"protocol follows selected role");
        check(PortSelection.automaticTouch(new String[]{names[0]},new boolean[]{false})==-1,"command-only enumeration never gets touch assignment");
        check(PortSelection.automaticTouch(new String[]{names[1],names[1]},new boolean[]{false,false})==-2,"duplicate named ports require manual choice");
        check(PortSelection.unique(new String[]{"1:2:4:serialA"},"1:2:4:")==0,"permission-less identity upgraded when unambiguous");
        check(PortSelection.unique(new String[]{"1:2:4:serialA","1:2:4:serialB"},"1:2:4:")==-2,"multiple devices without serial are ambiguous");
        check(PortSelection.unique(new String[]{"1:2:4:serialB","1:2:4:serialA"},"1:2:4:serialA")==1,"serial selects original device");
        check(PortSelection.unique(new String[]{"1:2:8:serialA"},"1:2:4:serialA")==-1,"command interface cannot replace saved touch");
        check(PortSelection.unique(new String[]{"1:2:4:serialB"},"1:2:4:serialA")==-1,"other serial is not substituted");
        Map<String,Object> defaults=SetupDefaults.missing(Collections.emptyMap());
        check(Boolean.FALSE.equals(defaults.get("setup_complete")),"fresh install starts wizard");
        check(Boolean.FALSE.equals(defaults.get("touch_command")),"default9600 touch protocol");
        check(Integer.valueOf(2).equals(defaults.get("button_mode")),"default IO4 buttons");
        check(Boolean.TRUE.equals(defaults.get("led_enabled"))&&Boolean.TRUE.equals(defaults.get("external_enabled")),"LED and external output on by default");
        check(Boolean.TRUE.equals(defaults.get("aime_enabled")),"Aime reader on by default");
        check(Boolean.TRUE.equals(defaults.get("aime_led_enabled")),"reader LED on by default");
        check(Boolean.TRUE.equals(defaults.get("led_ceiling")),"ceiling RGB on by default");
        Map<String,Object> saved=new HashMap<>();saved.put("led_enabled",false);saved.put("aime_enabled",false);saved.put("touch_command",true);saved.put("button_mode",1);
        Map<String,Object> migrated=SetupDefaults.missing(saved);
        check(!migrated.containsKey("led_enabled")&&!migrated.containsKey("touch_command")&&!migrated.containsKey("button_mode"),"explicit settings retained");
        check(!migrated.containsKey("aime_enabled"),"explicit Aime OFF retained");
        check(Boolean.TRUE.equals(migrated.get("setup_complete")),"existing install not mistaken for fresh install");
        byte[] report=new byte[64];report[0]=1;int idle=0xf80d;word(report,0,idle);word(report,1,idle);
        check(Io4Input.mask(report,0)==0&&Io4Input.mask(report,1)==0,"IO4 both banks idle");
        int[] bits={2,3,0,15,14,13,12,11};
        for(int bank=0;bank<2;bank++)for(int i=0;i<8;i++){
            word(report,bank,idle&~(1<<bits[i]));check(Io4Input.mask(report,bank)==(1<<i),"ring mapping bank"+bank+" key"+i);word(report,bank,idle);
        }
        word(report,0,idle|2);check(Io4Input.mask(report,0)==256&&Io4Input.mask(report,1)==256,"P1 active-high independent of ring bank");
        word(report,1,idle&~4);check(Io4Input.mask(report,1)==257,"P1 and ring simultaneous");
        word(report,0,idle|(1<<9)|(1<<6));word(report,1,idle);check(Io4Input.mask(report,0)==0,"test/service not silently mapped to P1");
        invalid(()->Io4Input.mask(new byte[63],0),"short IO4 rejected");invalid(()->Io4Input.mask(report,2),"unknown player rejected");
        report[0]=2;invalid(()->Io4Input.mask(report,0),"wrong report ID rejected");
        System.out.println("PASS: "+checks+" hardware identity/default/IO4 checks");
    }
}
