package io.oniimai.kanade;

import java.util.Locale;

/**
 * Module-owned UI text. Every message has a stable key ({@link Msg}) and one translation per
 * language in locales/*.json; scripts/generate_locales.py compiles them into {@link I18nCatalog}.
 * English is the fallback. The game's Resources and the system locale are never modified.
 */
final class I18n {
    /** BCP 47 tags of the supported languages, in catalogue column order. */
    static final String[] CODES=I18nCatalog.LANGUAGES;
    /** Autonyms in CODES order, so each language stays recognizable from any other. */
    static final String[] NAMES={"English","한국어","简体中文"};
    /** The language picker's title, written in every supported language at once. */
    static final String LANGUAGE_TITLE="Language · 언어 · 语言";
    /** Icon glyph for language rows. */
    static final String LANGUAGE_GLYPH="文";
    static final String FALLBACK="en";
    private static volatile int current=index(FALLBACK);
    private I18n(){}

    static String language(){return CODES[current];}
    static int index(){return current;}
    /** Selects a language by tag; unsupported or missing tags select English. */
    static void language(String tag){String code=supported(tag);current=index(code==null?FALLBACK:code);}

    /** Maps a BCP 47 tag (Android's "zh_CN" form included) to a supported language, or null. */
    static String supported(String tag){
        if(tag==null)return null;
        String[] parts=tag.trim().replace('_','-').split("-");
        switch(parts[0].toLowerCase(Locale.ROOT)){
            case "en":return "en";
            case "ko":return "ko";
            case "zh":
                for(int i=1;i<parts.length;i++){
                    String part=parts[i].toLowerCase(Locale.ROOT);
                    if(part.equals("hans"))return "zh-Hans";
                    // Traditional Chinese is not provided; English is the closer fallback than another script.
                    if(part.equals("hant")||part.equals("tw")||part.equals("hk")||part.equals("mo"))return null;
                }
                return "zh-Hans";
            default:return null;
        }
    }
    /** The first supported language among the device's preferred tags, else English. */
    static String preferred(String... tags){
        if(tags!=null)for(String tag:tags){String code=supported(tag);if(code!=null)return code;}
        return FALLBACK;
    }
    /** Locale for dates and number formats in the current language. */
    static Locale locale(){
        switch(language()){case "ko":return Locale.KOREAN;case "zh-Hans":return Locale.SIMPLIFIED_CHINESE;default:return Locale.ENGLISH;}
    }

    /** The message for id in the current language, with {0}, {1}… replaced by args. */
    static String t(int id,Object... args){
        String text=I18nCatalog.TEXT[current][id];
        if(text==null||text.isEmpty())text=I18nCatalog.TEXT[index(FALLBACK)][id];
        return args==null||args.length==0?text:format(text,args);
    }
    static String key(int id){return I18nCatalog.KEYS[id];}
    /** The message for id in a given supported language (English if that language lacks it), unformatted. */
    static String textIn(String code,int id){
        String text=I18nCatalog.TEXT[index(supported(code)==null?FALLBACK:supported(code))][id];
        return text==null||text.isEmpty()?I18nCatalog.TEXT[index(FALLBACK)][id]:text;
    }

    /**
     * Positional placeholders only. Unlike java.text.MessageFormat, apostrophes and other braces
     * stay literal, and numbers are not grouped, so translators cannot break a message by quoting.
     */
    static String format(String pattern,Object... args){
        StringBuilder out=new StringBuilder(pattern.length()+16);
        for(int i=0;i<pattern.length();i++){
            char c=pattern.charAt(i);int close;
            if(c=='{'&&(close=pattern.indexOf('}',i+1))>i+1){
                String number=pattern.substring(i+1,close);
                if(number.chars().allMatch(Character::isDigit)&&number.length()<4){
                    int n=Integer.parseInt(number);
                    if(n<args.length){out.append(args[n]);i=close;continue;}
                }
            }
            out.append(c);
        }
        return out.toString();
    }
    private static int index(String code){
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(code))return i;
        throw new IllegalArgumentException("Unsupported language "+code);
    }
}
