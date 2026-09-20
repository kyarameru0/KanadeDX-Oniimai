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
    private String tr(String ko,String zh){return GameUi.tr(ko,zh);}
    private void install(View root){
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        setContentView(root);
    }
    private void showHome(){
        dashboard=null;
        install(NativeUi.home(this,uiPrefs,this::showPreview,()->{
            Intent intent=getPackageManager().getLaunchIntentForPackage("app.KanadeDX");
            if(intent==null)intent=getPackageManager().getLaunchIntentForPackage("app.KanadeDX.oniimai");
            if(intent!=null)startActivity(intent);else Toast.makeText(this,tr("KanadeDX가 설치되어 있지 않습니다.","尚未安装 KanadeDX。"),Toast.LENGTH_LONG).show();
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
        public String connectionDescription(){return tr("미리보기 · USB 연결 OFF","预览 · USB 连接 OFF");}
        public String ledDescription(){return tr("미리보기 · LED OFF","预览 · LED OFF");}
        public String displayDescription(){return tr("폰 미리보기 · 외부 출력 OFF","手机预览 · 外接输出 OFF");}
        public boolean externalActive(){return false;}
        public boolean ledEnabled(){return false;}
        public void showSettings(){new AlertDialog.Builder(MainActivity.this).setMessage(tr("실제 연결 설정은 게임 안 Onii 설정을 사용하세요.","实际连接请使用游戏内 Onii 设置。" )).setPositiveButton(tr("확인","确定"),null).show();}
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
