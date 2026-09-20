package io.oniimai.kanade;

/** Module-owned text only: no changes to the game's Resources or system locale. */
final class UiText {
    private static volatile String language="ko";
    static void language(String value){language="zh-Hans".equals(value)?"zh-Hans":"ko";}
    static String language(){return language;}
    static String t(String korean){
        if(!"zh-Hans".equals(language))return korean;
        String translated=UiTextCatalog.CHINESE.get(korean);
        return translated==null?korean:translated;
    }
}
