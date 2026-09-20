package io.oniimai.kanade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.util.function.Consumer;

final class UiLanguage {
    private static final String KEY="ui_language";
    // Autonyms remain recognizable when switching back from either language.
    static final String[] NAMES={"한국어","简体中文"};
    private static final String[] CODES={"ko","zh-Hans"};
    static void load(SharedPreferences prefs){UiText.language(prefs.getString(KEY,"ko"));}
    static String name(){return NAMES["zh-Hans".equals(UiText.language())?1:0];}
    static void choose(Activity activity,SharedPreferences prefs,Consumer<AlertDialog> protect,Runnable changed){
        NativeUi.choice(activity,"Language / 语言",NAMES,"zh-Hans".equals(UiText.language())?1:0,protect,index->{
            String next=CODES[index];if(next.equals(UiText.language()))return;
            prefs.edit().putString(KEY,next).apply();UiText.language(next);changed.run();
        });
    }
}
