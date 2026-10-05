package io.oniimai.kanade;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Language selection, fallback, formatting and catalogue completeness. */
public final class I18nTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}

    public static void main(String[] args)throws Exception{
        check(I18n.language().equals("en"),"English before any choice");
        check(Arrays.equals(I18n.CODES,new String[]{"en","ko","zh-Hans"})&&I18n.NAMES.length==I18n.CODES.length,"languages and autonyms line up");
        check(I18n.NAMES[1].equals("한국어")&&I18n.NAMES[2].equals("简体中文"),"autonyms are not translated");

        for(String tag:new String[]{"ko","ko-KR","KO_kr"})check("ko".equals(I18n.supported(tag)),"Korean tag "+tag);
        for(String tag:new String[]{"zh","zh-CN","zh_CN","zh-SG","zh-Hans","zh-Hans-HK"})check("zh-Hans".equals(I18n.supported(tag)),"Simplified tag "+tag);
        for(String tag:new String[]{"zh-TW","zh-HK","zh-Hant","zh-Hant-CN"})check(I18n.supported(tag)==null,"Traditional Chinese is not mapped to Simplified: "+tag);
        for(String tag:new String[]{"en","en-GB","en_US"})check("en".equals(I18n.supported(tag)),"English tag "+tag);
        for(String tag:new String[]{null,"","ja","fr-FR","x"})check(I18n.supported(tag)==null,"unsupported tag "+tag);
        check(I18n.preferred("ja-JP","zh-TW","ko-KR").equals("ko"),"first supported device language wins");
        check(I18n.preferred("ja-JP").equals("en")&&I18n.preferred().equals("en"),"English fallback");

        I18n.language("ko");
        check(I18n.t(Msg.COMMON_SAVE).equals("저장")&&I18n.locale().equals(Locale.KOREAN),"Korean");
        I18n.language("zh-CN");
        check(I18n.language().equals("zh-Hans")&&I18n.t(Msg.COMMON_SAVE).equals("保存"),"Chinese via region tag");
        I18n.language("fr");
        check(I18n.language().equals("en")&&I18n.t(Msg.COMMON_SAVE).equals("Save")&&I18n.locale().equals(Locale.ENGLISH),"unsupported falls back to English");
        check(I18n.t(Msg.USB_ERROR,"timeout").equals("USB error: timeout"),"argument substituted");
        check(I18n.t(Msg.DISPLAY_STATUS_RETRY_IN,"HDMI",4,2,5).equals("HDMI · reconnecting in 4 s (2/5)"),"several arguments, word order from the message");
        I18n.language("ko");
        check(I18n.t(Msg.DISPLAY_STATUS_RETRY_IN,"HDMI",4,2,5).equals("HDMI · 4초 후 다시 연결 (2/5)"),"Korean word order");

        check(I18n.format("{1} before {0}","a","b").equals("b before a"),"reordered placeholders");
        check(I18n.format("Can't {0}","x").equals("Can't x"),"apostrophes stay literal");
        check(I18n.format("{0} {2}","a").equals("a {2}"),"missing argument leaves the placeholder visible");
        check(I18n.format("{x} {} {0","a").equals("{x} {} {0"),"other braces stay literal");

        // Catalogue: one key per Msg constant, every language complete, placeholders 0..n-1.
        int constants=0;
        for(Field field:Msg.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==int.class){
            int id=field.getInt(null);constants++;
            check(I18nCatalog.KEYS[id].toUpperCase(Locale.ROOT).replace('.','_').equals(field.getName()),"constant matches key "+field.getName());
        }
        check(constants==I18nCatalog.KEYS.length,"no orphan keys");
        Pattern slot=Pattern.compile("\\{(\\d+)\\}");
        for(int id=0;id<I18nCatalog.KEYS.length;id++){
            Set<Integer> reference=null;
            for(int lang=0;lang<I18n.CODES.length;lang++){
                String text=I18nCatalog.TEXT[lang][id];
                check(text!=null&&!text.trim().isEmpty(),I18n.CODES[lang]+" text for "+I18nCatalog.KEYS[id]);
                Set<Integer> found=new TreeSet<>();Matcher m=slot.matcher(text);while(m.find())found.add(Integer.parseInt(m.group(1)));
                if(reference==null){reference=found;int n=0;for(int value:found)check(value==n++,"placeholders numbered from 0 in "+I18nCatalog.KEYS[id]);}
                else check(found.equals(reference),"placeholders match English in "+I18n.CODES[lang]+" "+I18nCatalog.KEYS[id]);
            }
        }
        I18n.language("en");
        System.out.println("PASS: "+checks+" i18n checks across "+I18nCatalog.KEYS.length+" messages in "+I18n.CODES.length+" languages");
    }
}
