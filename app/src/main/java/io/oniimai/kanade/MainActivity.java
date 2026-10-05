package io.oniimai.kanade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

/** Module launcher and local dashboard preview. The preview never opens USB or reads game memory. */
public final class MainActivity extends Activity {
    private SharedPreferences uiPrefs;
    private DashboardView dashboard;
    private final ControllerInput guard=new ControllerInput();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);NfcBridgeAccess.grant(this);uiPrefs=getSharedPreferences("oniimai_ui",MODE_PRIVATE);UiLanguage.load(uiPrefs);GameAssets.bind(this);showHome();
    }
    private int dp(float n){return GameUi.dp(this,n);}
    private void install(View root){
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        setContentView(root);
    }
    private void showHome(){
        dashboard=null;
        install(NativeUi.home(this,uiPrefs,this::showPreview,()->{
            Intent intent=getPackageManager().getLaunchIntentForPackage("app.KanadeDX");
            if(intent==null)intent=getPackageManager().getLaunchIntentForPackage("app.KanadeDX.oniimai");
            if(intent!=null)startActivity(intent);else Toast.makeText(this,I18n.t(Msg.HOME_NOT_INSTALLED),Toast.LENGTH_LONG).show();
        }));
    }
    private void showPreview(){
        dashboard=new DashboardView(new PreviewHost(),true,this::showHome);install(dashboard);
    }

    private final class PreviewHost implements DashboardHost {
        public Activity activity(){return MainActivity.this;}
        public Context assetContext(){return MainActivity.this;}
        public SharedPreferences prefs(){return getSharedPreferences("oniimai_dashboard_preview",MODE_PRIVATE);}
        public boolean demo(){return true;}
        public long[] diagnostic(){return new long[]{0,0,1,0};}
        public String connectionDescription(){return I18n.t(Msg.PREVIEW_INPUT);}
        public String ledDescription(){return I18n.t(Msg.PREVIEW_LED);}
        public String displayDescription(){return I18n.t(Msg.PREVIEW_DISPLAY);}
        public boolean externalActive(){return false;}
        public boolean ledEnabled(){return false;}
        public void showSettings(){new AlertDialog.Builder(MainActivity.this).setMessage(I18n.t(Msg.PREVIEW_SETTINGS_HINT)).setPositiveButton(I18n.t(Msg.COMMON_OK),null).show();}
        public void updateDashboard(DashboardView view){view.update("{}",true,null);}
        public void setDashboardEditing(boolean editing){}
        public void choose(String title,String[] options,int selected,IntConsumer action){
            NativeUi.choice(MainActivity.this,title,options,selected,null,action);
        }
    }
    @Override public void onBackPressed(){if(dashboard!=null){if(!dashboard.back())showHome();return;}super.onBackPressed();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return guard.captures(event)||super.dispatchKeyEvent(event);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){return guard.captures(event)||super.dispatchGenericMotionEvent(event);}
}
