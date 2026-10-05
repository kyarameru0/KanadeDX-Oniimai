package io.oniimai.kanade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.LocaleList;
import java.util.function.Consumer;

/** Stores the chosen UI language. Without a saved choice the device's preferred languages decide. */
final class UiLanguage {
    private static final String KEY="ui_language";
    static void load(SharedPreferences prefs){I18n.language(prefs.contains(KEY)?prefs.getString(KEY,I18n.FALLBACK):system());}
    static void save(SharedPreferences prefs,String code){I18n.language(code);prefs.edit().putString(KEY,I18n.language()).apply();}
    static String name(){return I18n.NAMES[I18n.index()];}
    static void choose(Activity activity,SharedPreferences prefs,Consumer<AlertDialog> protect,Runnable changed){
        NativeUi.choice(activity,I18n.LANGUAGE_TITLE,I18n.NAMES,I18n.index(),protect,index->{
            String next=I18n.CODES[index];if(next.equals(I18n.language()))return;
            save(prefs,next);changed.run();
        });
    }
    private static String system(){
        LocaleList locales=LocaleList.getDefault();String[] tags=new String[locales.size()];
        for(int i=0;i<tags.length;i++)tags[i]=locales.get(i).toLanguageTag();
        return I18n.preferred(tags);
    }
}
