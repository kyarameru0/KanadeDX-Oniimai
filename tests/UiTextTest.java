package io.oniimai.kanade;

import android.hardware.usb.UsbManager;
import java.util.Map;

public class UiTextTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(UiText.language().equals("ko"),"existing users keep Korean");
        check(UiText.t("Onii 설정").equals("Onii 설정"),"Korean label");
        LedOutput led=new LedOutput(new UsbManager(),false);
        check(led.summary().contains("LED 연결 대기"),"initial idle message");
        UiText.language("zh-Hans");
        check(UiText.t("Onii 설정").equals("Onii 设置"),"Chinese label");
        check(led.summary().contains("等待连接 LED"),"idle message changes without reconnect");
        check(!led.running(),"language does not start USB");
        check(UiText.t("unknown device name / 1080P").equals("unknown device name / 1080P"),"unknown text passes through");
        check(UiText.t("USB 인터페이스를 찾을 수 없습니다: ").equals("找不到 USB 接口："),"USB error prefix");
        check((UiText.t("버튼 ")+5).equals("按钮 5"),"dynamic value preserved");
        boolean complete=true,lines=true,clean=true;
        for(Map.Entry<String,String> item:UiTextCatalog.CHINESE.entrySet()){
            String translated=UiText.t(item.getKey());
            complete&=translated.equals(item.getValue())&&!translated.trim().isEmpty();
            lines&=translated.chars().filter(c->c=='\n').count()==item.getKey().chars().filter(c->c=='\n').count();
            clean&=!translated.matches("(?s).*[가-힣].*");
        }
        check(complete,"all catalogue values are available");check(lines,"line breaks preserved");check(clean,"no Korean in Chinese catalogue");
        UiText.language("ko");
        check(led.summary().contains("LED 연결 대기"),"switch back without reconnect");
        boolean roundtrip=true;for(String key:UiTextCatalog.CHINESE.keySet())roundtrip&=UiText.t(key).equals(key);
        check(roundtrip,"all Korean originals restored");
        for(String invalid:new String[]{"zh","zh-Hant","en","",null}){UiText.language(invalid);check(UiText.language().equals("ko"),"safe fallback for "+invalid);}
        led.destroy();
        System.out.println("PASS: "+checks+" UI language checks across "+UiTextCatalog.CHINESE.size()+" entries");
    }
}
