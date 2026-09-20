package io.oniimai.kanade;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.util.function.Consumer;
final class InitialSetup {
    static AlertDialog show(Activity activity,SharedPreferences prefs,Consumer<AlertDialog> protect,Runnable changed,Runnable finished){
        return NativeUi.setup(activity,prefs,protect,changed,finished);
    }
}
