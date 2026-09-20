package io.oniimai.kanade;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.hardware.usb.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class GameSession implements DashboardHost {
    final Activity activity;
    private final boolean nativeLoaded;
    private final UsbManager usb;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final ControllerInput guard=new ControllerInput();
    private final KeyboardState keys=new KeyboardState();
    private final SharedPreferences prefs;
    private final Object dataLock=new Object();
    private long touches,touchAt,hidAt;
    private int hidButtons,keyButtons;
    private volatile boolean destroyed,foreground=true,armed,connected,busy;
    private volatile int nativeStatus=-1,packets,hidPackets;
    private volatile String message=UiText.t("USB 권한을 허용하고 포트를 선택하세요.");
    private volatile CommandChannel command;
    private FirmwareInfo firmwareInfo;
    private boolean firmwareBusy;
    private String firmwareQueryError="";
    private TextView firmwareStatus;
    private UsbIo.Cdc serial;
    private volatile UsbIo.Hid hid;
    private List<UsbIo.Port> ports=new ArrayList<>();
    private String touchPort="",hidPort="";
    private boolean commandMode;
    private int player,learn=-1;
    private volatile int buttonMode=2;
    private FrameLayout overlay;
    private DisplayOutput displayOutput;
    private final LedOutput ledOutput;
    private final CeilingOutput ceilingOutput;
    private final AimeReader aimeReader;
    private final PhoneNfcReader phoneNfc;
    private boolean aimeEnabled,nativeAimeAllowed;
    private String aimePort="";
    private TextView aimeChoice,aimeStatusView;
    private String ledPort="";
    private boolean ledEnabled,ledReverse,ledRing,ledCeiling;
    private int ledAddress,ledBase,ledRotation,ledBrightness;
    private TextView ledStatusView,ledChoice;
    private LinearLayout panelBody,tabStrip;
    private ScrollView panelScroll;
    private final int[] tabScroll=new int[4];
    private TextView screenStatus;
    private Switch externalToggle;
    private boolean syncingExternalToggle;
    private int panelTab;
    private long lastUi;
    private View settingsButton;
    private AlertDialog panel;
    private boolean reopenPanelForLanguage;
    private TextView status;
    private LinearLayout deviceList;
    private TextView touchChoice,hidChoice;
    private final Button[] learnButtons=new Button[9];
    private long lastProbe,lastStatsFrame,lastStatsTouch;
    private final String permissionAction="io.oniimai.kanade.USB_PERMISSION";

    GameSession(Activity activity,boolean loaded){
        this.activity=activity;nativeLoaded=loaded;usb=(UsbManager)activity.getSystemService(Context.USB_SERVICE);
        prefs=activity.getSharedPreferences("oniimai_controller_v1",Context.MODE_PRIVATE);
        applyDefaults(prefs); UiLanguage.load(prefs); commandMode=prefs.getBoolean("touch_command",false);
        inputRequested=prefs.getBoolean("input_enabled",true);buttonMode=bounded(prefs.getInt("button_mode",2),0,2);GameAssets.bind(assetContext());
        ledOutput=new LedOutput(usb,loaded);ledEnabled=prefs.getBoolean("led_enabled",true);
        ceilingOutput=new CeilingOutput(()->hid,loaded);ledCeiling=prefs.getBoolean("led_ceiling",true);
        aimeEnabled=prefs.getBoolean("aime_enabled",true);
        phoneNfc=new PhoneNfcReader(activity);
        aimeReader=new AimeReader(usb,()->nativeLoaded?NativeBridge.aimeStatus():-1,()->nativeLoaded?NativeBridge.aimeLed():-1,()->nativeLoaded?NativeBridge.aimeGeneration():-1,(code,scanGeneration)->nativeLoaded&&NativeBridge.submitAime(code,scanGeneration),(issue,scanGeneration)->{if(nativeLoaded)NativeBridge.aimeError(issue,scanGeneration);});
        aimeReader.led(prefs.getBoolean("aime_led_enabled",true));
        aimeReader.radioType(bounded(prefs.getInt("aime_radio_type",3),1,3));
        ledAddress=bounded(prefs.getInt("led_address",17),1,255);ledBase=bounded(prefs.getInt("led_base",0),0,24);
        ledBrightness=bounded(prefs.getInt("led_brightness",100),0,100);ledRotation=bounded(prefs.getInt("led_rotation",0),0,7);
        ledReverse=prefs.getBoolean("led_reverse",false);ledRing=prefs.getBoolean("led_ring",false);applyLedSettings();
        for(int i=0;i<keys.map.length;i++)keys.map[i]=prefs.getInt("key"+i,keys.map[i]);
        player=prefs.getInt("player",0)==1?1:0;
        panelTab=bounded(prefs.getInt("settings_tab",0),0,3);
        for(int i=0;i<tabScroll.length;i++)tabScroll[i]=Math.max(0,prefs.getInt("settings_scroll_"+i,0));
        IntentFilter filter=new IntentFilter(permissionAction);filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(receiver,filter);
        attachControls();displayOutput=new DisplayOutput(activity,overlay,prefs,this::suspendInput,this::tell,this::protect,this);ui.post(tick);ui.postDelayed(()->{if(!destroyed){if(!prefs.getBoolean("setup_complete",false))showSetup();else startAutomatic();}},700);
    }
    private final Set<AlertDialog> dialogs=Collections.newSetFromMap(new WeakHashMap<AlertDialog,Boolean>());
    private final Set<String> permissionAsked=new HashSet<>();
    private String permissionPending;
    private boolean inputRequested,dashboardEditing,setupActive,focus=true,initializing=true;
    private long neutralSince,lastScan,lastGameFrame;
    private String gameFrame="{}";
    private final GameAlbum gameAlbum=new GameAlbum();
    private Button returnButton;
    static void applyDefaults(SharedPreferences prefs){
        Map<String,Object> missing=SetupDefaults.missing(prefs.getAll());SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,Object> entry:missing.entrySet()){
            if(entry.getValue() instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)entry.getValue());
            else if(entry.getValue() instanceof Integer)edit.putInt(entry.getKey(),(Integer)entry.getValue());
        }
        edit.apply();
    }
    private static String tr(String ko,String zh){return GameUi.tr(ko,zh);}
    public Activity activity(){return activity;}
    public Context assetContext(){try{return activity.createPackageContext("io.oniimai.kanade",0);}catch(Exception e){return activity;}}
    public SharedPreferences prefs(){return prefs;}
    public long[] diagnostic(){synchronized(dataLock){return new long[]{hidButtons|keyButtons,touches,armed?0:1,(touchPort.isEmpty()?0:connected?1:0)|(hidPort.isEmpty()?keyButtons!=0?2:0:connected?2:0)};}}
    public String connectionDescription(){return connected?tr("터치와 버튼 연결됨","触摸和按钮已连接"):message;}
    public String ledDescription(){return ledOutput.summary()+"\n"+ceilingOutput.summary();}
    public String displayDescription(){return displayOutput==null?"—":displayOutput.summary();}
    public boolean externalActive(){return displayOutput!=null&&displayOutput.active();}
    public boolean ledEnabled(){return ledEnabled;}
    public void showSettings(){showPanel();}
    public void updateDashboard(DashboardView view){gameAlbum.update(gameFrame);view.update(gameFrame,!nativeLoaded||nativeStatus!=15,gameAlbum.bitmap());}
    public void setDashboardEditing(boolean editing){dashboardEditing=editing;suspendInput();}
    public void choose(String title,String[] options,int selected,java.util.function.IntConsumer action){
        NativeUi.choice(activity,title,options,selected,this::protect,action);
    }
    private void showSetup(){
        if(destroyed||setupActive)return;suspendInput();setupActive=true;
        InitialSetup.show(activity,prefs,this::protect,()->{commandMode=prefs.getBoolean("touch_command",false);buttonMode=bounded(prefs.getInt("button_mode",2),0,2);ledEnabled=prefs.getBoolean("led_enabled",true);aimeEnabled=prefs.getBoolean("aime_enabled",true);languageChanged();},()->{
            setupActive=false;if(destroyed)return;
            if(!ledEnabled)ledOutput.stop();else if(!ledPort.isEmpty()&&!ledOutput.running())startLeds();
            if(!aimeEnabled)aimeReader.stop();else if(!aimePort.isEmpty()&&!aimeReader.running())startAime();
            if(!prefs.getBoolean("external_enabled",true)&&externalActive())displayOutput.stop(UiText.t("휴대폰 화면으로 돌아왔습니다."));
            startAutomatic();
        });
    }
    private void startAutomatic(){initializing=false;if(!destroyed){scan();displayOutput.resume();}}
    void focusGained(){focus=true;neutralSince=0;}
    private boolean uiBlocked(){
        if(panel!=null||setupActive||dashboardEditing)return true;
        for(AlertDialog dialog:dialogs)if(dialog.isShowing())return true;
        return false;
    }
    private void updateAutomaticInput(long now){
        boolean ready=inputRequested&&foreground&&focus&&!initializing&&!uiBlocked()&&nativeStatus==15;
        long physical;synchronized(dataLock){physical=touches|hidButtons|keyButtons;}
        if(!ready){if(armed)suspendInput();neutralSince=0;return;}
        if(!armed){if(physical!=0)neutralSince=0;else if(neutralSince==0)neutralSince=now;else if(now-neutralSince>=250){armed=true;}}
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void attachControls(){
        overlay=new FrameLayout(activity);
        attachSettingsButton();
        returnButton=Miui.action(activity,tr("모니터 켜기 · 길게 끌어 이동","开启显示器 · 拖动移动"),true,()->displayOutput.enable());
        returnButton.setTextSize(12);returnButton.setVisibility(View.GONE);
        overlay.addView(returnButton,new FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP|Gravity.START));FloatingShortcut.attach(returnButton,prefs);
        activity.addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
    }
    private void attachSettingsButton(){
        if(settingsButton!=null)overlay.removeView(settingsButton);
        settingsButton=NativeUi.floatingSettings(activity,this::showPanel);
        float scale=Math.max(1,activity.getResources().getConfiguration().fontScale);
        int width=Math.min(dp(Math.round(NativeUi.settingsShortcutWidthDp()*scale)),activity.getResources().getDisplayMetrics().widthPixels-dp(24));
        overlay.addView(settingsButton,new FrameLayout.LayoutParams(width,dp(Math.round(48*scale)),Gravity.TOP|Gravity.START));
        FloatingShortcut.attach(settingsButton,prefs,"settingsShortcut",16);
        settingsButton.setVisibility(externalActive()?View.GONE:View.VISIBLE);
    }
    boolean portraitLocked(){return displayOutput!=null&&displayOutput.portraitLocked();}
    private void toggleArm(){
        if(inputRequested){inputRequested=false;prefs.edit().putBoolean("input_enabled",false).apply();suspendInput();return;}
        if(!nativeLoaded||nativeStatus!=15){tell(nativeExplanation());return;}
        synchronized(dataLock){if(touches!=0||hidButtons!=0||keyButtons!=0){tell(UiText.t("센서와 버튼에서 손을 뗀 뒤 켜세요."));return;}}
        inputRequested=true;prefs.edit().putBoolean("input_enabled",true).apply();neutralSince=0;push();tell(UiText.t("컨트롤러 입력을 게임에 전달합니다."));
    }
    private void allowAime(boolean allowed){if(nativeLoaded&&nativeAimeAllowed!=allowed){nativeAimeAllowed=allowed;NativeBridge.aimeEnabled(allowed);}}
    void suspendInput(){suspendInput(true);}
    private void suspendInput(boolean suspendCard){neutralSince=0;armed=false;if(suspendCard)allowAime(false);push();}
    void focusLost(){focus=false;suspendInput();keys.clear();synchronized(dataLock){keyButtons=0;}push();}
    void pause(){phoneNfc.pause();if(displayOutput!=null)displayOutput.pause();foreground=false;ledOutput.foreground(false);ceilingOutput.foreground(false);aimeReader.foreground(false);suspendInput();keys.clear();learn=-1;synchronized(dataLock){keyButtons=0;}push();}
    void resume(){foreground=true;focus=activity.hasWindowFocus();ledOutput.foreground(true);ceilingOutput.foreground(true);aimeReader.foreground(true);if(displayOutput!=null)displayOutput.resume();}
    boolean motion(MotionEvent event){return guard.captures(event);}
    boolean key(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&!guard.captures(event)&&externalActive()){
            if(event.getAction()==KeyEvent.ACTION_UP){if(!displayOutput.dashboardBack())displayOutput.stop(tr("휴대폰 화면으로 돌아왔습니다.","已切换到手机屏幕。"));}return true;
        }
        if(!guard.captures(event))return false;
        int action=event.getAction();
        if(action==KeyEvent.ACTION_DOWN||action==KeyEvent.ACTION_UP){
            boolean changed=keys.event(event.getDeviceId(),event.getKeyCode(),action==KeyEvent.ACTION_DOWN,event.getRepeatCount());
            if(changed&&action==KeyEvent.ACTION_DOWN&&learn>=0){
                keys.learn(learn,event.getKeyCode());SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<keys.map.length;i++)edit.putInt("key"+i,keys.map[i]);edit.apply();
                learn=-1;message=UiText.t("키 배치를 저장했습니다.");updateLearning();
            }
            synchronized(dataLock){keyButtons=buttonMode==1?keys.mask():0;}push();
        }
        return true;
    }
    private void push(){
        if(!nativeLoaded)return;
        synchronized(dataLock){
            long now=SystemClock.uptimeMillis();
            if(now-touchAt>500)touches=0;
            if(now-hidAt>500)hidButtons=0;
            NativeBridge.submit(touches,hidButtons|keyButtons,player,armed&&foreground&&!destroyed);
        }
    }
    private void touch(boolean[] pressed){
        long mask=0;for(int i=0;i<Math.min(34,pressed.length);i++)if(pressed[i])mask|=1L<<i;
        synchronized(dataLock){touches=mask;touchAt=SystemClock.uptimeMillis();packets++;}push();
    }
    private void buttons(int mask){
        synchronized(dataLock){hidButtons=buttonMode==2?mask:0;hidAt=SystemClock.uptimeMillis();hidPackets++;}push();
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(destroyed)return;push();long now=SystemClock.uptimeMillis();updateAutomaticInput(now);
        boolean inputsReady=connected||(touchPort.isEmpty()&&(buttonMode!=2||hidPort.isEmpty()));
        // NFC owns its own USB transport. An IO4/touch reconnect must not erase
        // the reader's pending error before Unity can consume it.
        boolean phoneEnabled=prefs.getBoolean("phone_nfc_enabled",true)&&phoneNfc.supported();
        allowAime(((aimeEnabled&&aimeReader.running()&&inputRequested)||phoneEnabled)&&foreground&&focus&&!initializing&&!uiBlocked()&&nativeStatus==15);
        phoneNfc.update(phoneEnabled&&nativeAimeAllowed,now);
        if(now-lastGameFrame>=150){lastGameFrame=now;if(nativeLoaded&&nativeStatus==15)gameFrame=NativeBridge.gameplayStats();
            boolean external=externalActive();settingsButton.setVisibility(external?View.GONE:View.VISIBLE);
            returnButton.setVisibility(!external&&displayOutput.hasExternal()?View.VISIBLE:View.GONE);
        }
        if(now-lastScan>=4000&&foreground&&!setupActive&&!initializing){lastScan=now;if(prefs.getBoolean("auto_connect",true)&&!connected&&!busy)scan();}

        if(nativeLoaded&&now-lastProbe>1000){lastProbe=now;nativeStatus=NativeBridge.initialize();long[] stats=NativeBridge.stats();if(stats!=null){lastStatsFrame=stats[1];lastStatsTouch=stats[2];}}
        if(status!=null&&now-lastUi>=200){lastUi=now;long t;int b;synchronized(dataLock){t=touches;b=hidButtons|keyButtons;}
            status.setText((connected?UiText.t("컨트롤러 연결됨"):UiText.t("컨트롤러 연결 대기"))+(busy?UiText.t(" · 처리 중"):"")+"  ·  "+(nativeStatus==15?UiText.t("게임 준비 4/4"):UiText.t("게임 확인 필요"))+
                UiText.t("\n터치 ")+Long.bitCount(t)+UiText.t(" / 34     버튼 ")+Integer.bitCount(b&255)+" / 8 · P1 "+((b&256)!=0?"ON":"OFF")+"\n"+message);
            if(screenStatus!=null)screenStatus.setText(displayOutput.summary());
            if(externalToggle!=null){
                boolean enabled=prefs.getBoolean("external_enabled",true);
                if(externalToggle.isChecked()!=enabled){syncingExternalToggle=true;try{externalToggle.setChecked(enabled);}finally{syncingExternalToggle=false;}}
            }
            if(ledStatusView!=null)ledStatusView.setText(ledDescription());
            if(aimeStatusView!=null)aimeStatusView.setText(aimeDescription());
            for(int i=0;i<learnButtons.length;i++)if(learnButtons[i]!=null)learnButtons[i].setTextColor((b&(1<<i))!=0?Miui.BLUE:Miui.INK);
        }
        ui.postDelayed(this,20);
    }};
    private String nativeExplanation(){
        if(!nativeLoaded)return UiText.t("네이티브 라이브러리 로드 실패 — LSPosed 로그 확인");
        switch(nativeStatus){
            case 15:return UiText.t("API 102 · 게임 입력 훅 4/4 준비됨");
            case -1:return UiText.t("프레임워크 Native Hook API 미연결 — LSPosed API 102 지원 확인");
            case -2:return UiText.t("Unity 입력 라이브러리 로드 대기");
            case -3:return UiText.t("지원하지 않는 KanadeDX 빌드 — 260207.0635 필요");
            case -4:return UiText.t("입력 코드가 예상과 다름 — 다른 모듈/빌드 확인");
            case -5:return UiText.t("입력 훅 설치 실패 — 게임을 완전히 재시작하세요.");
            default:return UiText.t("입력 훅 상태 ")+nativeStatus;
        }
    }
    private void tell(String text){message=text;Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
    private void postMessage(String text){ui.post(()->{if(!destroyed){message=text;if(panel!=null)refreshPortLabels();}});}
    private Button button(LinearLayout parent,String text,Runnable action){return Miui.addAction(parent,text,false,action);}
    private void text(LinearLayout parent,String text){Miui.note(parent,text);}
    private void protect(AlertDialog dialog){suspendInput();dialogs.add(dialog);dialog.setOnKeyListener((d,code,event)->guard.captures(event)&&key(event));}
    private void showPanel(){showPanel(true);}
    private void showPanel(boolean scanPorts){
        suspendInput();learn=-1;if(panel!=null)return;
        panel=NativeUi.settings(this,()->{if(panel!=null)panel.dismiss();});protect(panel);
        panel.setOnDismissListener(d->{panel=null;learn=-1;
            if(reopenPanelForLanguage){reopenPanelForLanguage=false;ui.post(()->{if(!destroyed)showPanel(false);});}
        });
        NativeUi.showFullScreen(panel);if(scanPorts)scan();
    }

    static void styleSettingsSystemBars(Window window){
        if(window==null)return;
        // Only the dialog owns these flags; dismissing it reveals Unity's unchanged Window.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(GameUi.PAGE);window.setNavigationBarColor(GameUi.PAGE);
        if(Build.VERSION.SDK_INT>=29){window.setStatusBarContrastEnforced(false);window.setNavigationBarContrastEnforced(false);}
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(light,light);controller.show(WindowInsets.Type.systemBars());}
        }else{
            View decor=window.getDecorView();int flags=decor.getSystemUiVisibility();
            flags&=~(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            decor.setSystemUiVisibility(flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
    void layoutChanged(){ui.post(this::resizePanel);}
    private void languageChanged(){
        // Keep Unity, USB and the external Surface alive while rebuilding only these views.
        attachSettingsButton();displayOutput.languageChanged();
        tell(UiText.t("언어를 변경하고 저장했습니다."));
        if(panel!=null){reopenPanelForLanguage=true;panel.dismiss();}
    }
    private void resizePanel(){if(panel!=null&&panel.getWindow()!=null)panel.getWindow().setLayout(-1,-1);}
    private void saveTabScroll(){
        if(panelScroll==null)return;tabScroll[panelTab]=panelScroll.getScrollY();
        prefs.edit().putInt("settings_tab",panelTab).putInt("settings_scroll_"+panelTab,tabScroll[panelTab]).apply();
    }
    private void selectTab(int next){if(next==panelTab)return;saveTabScroll();panelTab=next;renderTab(false);}
    private void renderTab(){} // Compose reads a fresh settings snapshot while the panel is visible.
    private void renderTab(boolean ignored){}
    private List<NativeSettings.Group> buildingSettings;
    List<NativeSettings.Group> nativeSettings(int tab){
        buildingSettings=new ArrayList<>();
        if(tab==0)renderConnectionTab();else if(tab==1)renderDisplayTab();else if(tab==2)renderButtonTab();else renderLedTab();
        return buildingSettings;
    }
    int settingsTab(){return panelTab;}
    void settingsTab(int tab){panelTab=tab;learn=-1;prefs.edit().putInt("settings_tab",tab).apply();}
    void chooseLanguage(){UiLanguage.choose(activity,prefs,this::protect,this::languageChanged);}
    void showLicenses(){LicenseUi.show(activity,this::protect);}
    private NativeSettings.Group settingsGroup(String title){NativeSettings.Group group=new NativeSettings.Group(title);buildingSettings.add(group);return group;}
    private TextView settingRow(LinearLayout group,String title,String value,Runnable action){return GameUi.row(group,title,value,action);}
    private void renderConnectionTab(){
        NativeSettings.Group input=settingsGroup(tr("컨트롤러 연결","控制器连接"));
        input.note(connectionDescription());
        input.toggle(tr("컨트롤러 입력","控制器输入"),tr("설정창을 닫고 손을 떼면 게임에 입력합니다.","关闭设置并松手后向游戏传送输入。"),inputRequested,value->{
            if(value!=inputRequested)toggleArm();
        });
        input.toggle(tr("자동 연결","自动连接"),tr("다음 실행에도 저장한 포트를 사용합니다.","下次启动继续使用已保存的端口。"),prefs.getBoolean("auto_connect",true),value->{prefs.edit().putBoolean("auto_connect",value).apply();if(value)scan();});
        input.row(UiText.t("컨트롤러 연결"),tr("저장된 포트로 연결","连接已保存的端口"),()->{prefs.edit().putBoolean("auto_connect",true).apply();connect();renderTab();});
        input.row(UiText.t("연결 해제"),tr("포트 선택은 유지됩니다","保留端口选择"),()->{prefs.edit().putBoolean("auto_connect",false).apply();disconnect(true);renderTab();});
        NativeSettings.Group ports=settingsGroup(tr("터치 입력","触摸输入"));
        ports.row(tr("터치 포트","触摸端口"),portName(touchPort),()->choosePort(false));
        ports.row(tr("터치 프로토콜","触摸协议"),commandMode?"Command · 115200":"Touch Serial · 9600",()->{
            if(!canChangeInput())return;
            choose(tr("터치 프로토콜","触摸协议"),new String[]{"Touch Serial · 9600","Command · 115200"},commandMode?1:0,n->{commandMode=n==1;touchPort="";prefs.edit().putBoolean("touch_command",commandMode).remove("touch_identity").apply();renderTab();scan();});
        });
        NativeSettings.Group cards=settingsGroup(tr("Aime 카드 리더","Aime 读卡器"));
        cards.toggle(tr("Aime 카드 인식","读取 Aime 卡"),tr("게임의 카드 인식 화면에서 사용합니다.","在游戏读卡画面中使用。"),aimeEnabled,value->{
            aimeEnabled=value;prefs.edit().putBoolean("aime_enabled",value).apply();allowAime(false);
            if(value){if(aimePort.isEmpty())scan();else startAime();}else aimeReader.stop();
        });
        cards.row(tr("카드 리더 포트","读卡器端口"),portName(aimePort),this::chooseAimePort);
        String[] radioNames={tr("구형 Aime · MIFARE","旧版 Aime · MIFARE"),"Amusement IC Aime · FeliCa",tr("자동 · 두 방식 탐색","自动 · 两种方式")};
        int radio=bounded(prefs.getInt("aime_radio_type",3),1,3);
        cards.row(tr("카드 탐색 방식","寻卡方式"),radioNames[radio-1],()->choose(tr("카드 탐색 방식","寻卡方式"),radioNames,radio-1,index->{
            int selected=index+1;prefs.edit().putInt("aime_radio_type",selected).apply();aimeReader.radioType(selected);
        }));
        cards.toggle(tr("카드 리더 LED","读卡器 LED"),tr("게임의 대기·성공·오류 상태를 표시합니다.","显示游戏的等待读卡、成功和错误状态。"),prefs.getBoolean("aime_led_enabled",true),value->{
            prefs.edit().putBoolean("aime_led_enabled",value).apply();aimeReader.led(value);
        });
        cards.note(aimeDescription());
        cards.note(tr("카드를 읽은 뒤에는 리더에서 떼어 주세요. 카드 번호는 설정이나 진단 기록에 저장하지 않습니다.","读取后请将卡片移开。卡号不会保存到设置或诊断记录中。"));
        NativeSettings.Group phoneCard=settingsGroup(tr("폰 NFC 리더","手机 NFC 读卡器"));
        phoneCard.toggle(tr("폰 NFC 자동 인식","手机 NFC 自动读卡"),tr("게임 화면에서 폰 뒷면에 바로 카드를 댑니다","在游戏画面中直接将卡片靠近手机背面"),prefs.getBoolean("phone_nfc_enabled",true),value->{
            prefs.edit().putBoolean("phone_nfc_enabled",value).apply();if(!value)phoneNfc.pause();
        });
        phoneCard.note(phoneNfc.summary());
        phoneCard.note(tr("컨트롤러 리더와 함께 사용할 수 있습니다. MIFARE는 폰 NFC 칩의 지원이 필요합니다.","可与控制器读卡器同时使用。MIFARE 需要手机 NFC 芯片支持。"));
        NativeSettings.Group devices=settingsGroup(tr("USB 장치","USB 设备"));
        devices.row(UiText.t("USB 기기 찾기"),tr("이름으로 포트 자동 선택","按名称自动选择端口"),this::scan);
        Set<String> seen=new HashSet<>();
        for(UsbIo.Port p:this.ports)if(seen.add(p.device.getDeviceName())){
            UsbDevice d=p.device;devices.row(d.getProductName()==null?d.getDeviceName():d.getProductName(),usb.hasPermission(d)?tr("권한 허용됨","已授予权限"):tr("눌러서 USB 권한 허용","点击授予 USB 权限"),()->requestPermission(d));
        }
        if(seen.isEmpty())devices.note(UiText.t("USB 장치 없음 — OTG 케이블을 확인하세요."));
        NativeSettings.Group more=settingsGroup(tr("설정 및 진단","设置与诊断"));
        more.row(tr("컨트롤러 펌웨어 확인","查看控制器固件"),firmwareDescription(),this::queryFirmware);
        more.row(tr("초기 설정","初始设置"),tr("언어 · 모니터 · 연결","语言 · 显示器 · 连接"),()->{if(panel!=null)panel.dismiss();showSetup();});
        more.row(UiText.t("진단 정보 복사"),nativeExplanation(),this::copyDiagnostics);
    }
    private void renderDisplayTab(){
        NativeSettings.Group output=settingsGroup(UiText.t("외부 디스플레이"));
        output.note(displayOutput.summary());
        output.toggle(tr("외부 화면으로 게임 출력","向外接屏幕输出游戏"),tr("모니터 연결 시 자동 출력","连接显示器时自动输出"),prefs.getBoolean("external_enabled",true),value->{
            if(syncingExternalToggle)return;
            if(value)displayOutput.enable();else displayOutput.stop(UiText.t("휴대폰 화면으로 돌아왔습니다."));
        });
        output.row(tr("회전 방향","旋转方向"),displayOutput.isClockwise()?UiText.t("시계 90°"):UiText.t("반시계 90°"),()->
            choose(tr("회전 방향","旋转方向"),new String[]{UiText.t("시계 90°"),UiText.t("반시계 90°")},displayOutput.isClockwise()?0:1,n->{displayOutput.setClockwise(n==0);renderTab();}));
        output.row(UiText.t("외부 디스플레이 선택"),tr("연결된 모니터 목록","已连接的显示器"),displayOutput::choose);
        NativeSettings.Group phone=settingsGroup(UiText.t("휴대폰 화면"));
        phone.toggle(tr("세로 화면","纵向画面"),tr("휴대폰 게임 영역을 9:16으로 표시","将手机游戏区域设为 9:16"),displayOutput.isPhonePortrait(),value->displayOutput.setPhonePortrait(value));
        phone.row(tr("위젯 대시보드","小组件仪表盘"),tr("미리보기 및 배치 편집","预览和编辑布局"),()->{if(panel!=null)panel.dismiss();displayOutput.previewDashboard();});
        NativeSettings.Group help=settingsGroup(UiText.t("연결 안내"));
        help.note(tr("외부 모니터는 가로 16:9, 게임은 세로 9:16을 90° 회전해 표시합니다.","外接显示器采用横向 16:9，将纵向 9:16 游戏旋转 90° 显示。"));
        help.note(tr("HDMI·DP처럼 Android가 별도 화면으로 인식하는 연결을 사용하세요.","请使用 HDMI、DP 等 Android 能识别为独立显示器的连接。"));
        help.row(UiText.t("화면 진단 정보 복사"),tr("연결 상태와 화면 정보","连接状态和屏幕信息"),this::copyDiagnostics);
    }
    private boolean canChangeInput(){if(connected||busy){tell(UiText.t("연결을 해제한 뒤 변경하세요."));return false;}return true;}
    private void renderButtonTab(){
        NativeSettings.Group input=settingsGroup(tr("버튼 입력","按钮输入"));
        String[] modes={UiText.t("사용 안 함"),"USB Keyboard","IO4 HID"};
        input.row(tr("입력 방식","输入方式"),modes[buttonMode],()->{
            if(!canChangeInput())return;
            choose(tr("버튼 입력 방식","按钮输入方式"),modes,buttonMode,n->{buttonMode=n;prefs.edit().putInt("button_mode",n).apply();keys.clear();synchronized(dataLock){keyButtons=hidButtons=0;}suspendInput();renderTab();});
        });
        input.row(UiText.t("플레이어"),player==0?"1P":"2P",()->{
            if(!canChangeInput())return;
            choose(UiText.t("플레이어"),new String[]{"1P","2P"},player,n->{player=n;prefs.edit().putInt("player",n).apply();push();renderTab();});
        });
        if(buttonMode==2){
            input.row("IO4 HID",portName(hidPort),()->choosePort(true));
            input.note(tr("버튼 8개와 P1 / START를 IO4에서 읽습니다.","通过 IO4 读取 8 个按钮和 P1 / START。"));
        }else if(buttonMode==1){
            NativeSettings.Group mapping=settingsGroup(UiText.t("버튼 배치"));
            mapping.note(tr("번호를 고른 뒤 실제 버튼을 누르세요. 눌린 버튼은 강조됩니다.","选择编号后按实体按钮。按下的按钮会高亮显示。"));
            for(int n=0;n<9;n++){
                final int index=n;
                mapping.row(n==8?"P1 / START":UiText.t("버튼 ")+(n+1),learn==n?tr("실제 버튼을 누르세요","请按实体按钮"):KeyEvent.keyCodeToString(keys.map[n]),()->{learn=index;updateLearning();});
            }
            mapping.row(UiText.t("키 지정 취소"),"",()->{learn=-1;updateLearning();});
        }
        NativeSettings.Group guide=settingsGroup(tr("입력 안내","输入说明"));
        guide.note(tr("설정과 위젯 편집 중에는 게임 입력이 잠시 멈춥니다. 창을 닫고 버튼과 센서에서 손을 떼면 복구됩니다.","设置和编辑小组件时暂停游戏输入。关闭窗口并松开按钮和传感器后恢复。"));
        guide.note(tr("센서 감도는 Oniimai Test에서 조절합니다.","传感器灵敏度请在 Oniimai Test 中调整。"));
    }
    private static int bounded(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private void applyLedSettings(){ledOutput.settings(ledBrightness,ledRotation,ledReverse,ledRing);ceilingOutput.settings(ledEnabled&&ledCeiling,ledBrightness);}
    private void saveLedSettings(){
        prefs.edit().putBoolean("led_enabled",ledEnabled).putInt("led_address",ledAddress).putInt("led_base",ledBase)
            .putInt("led_brightness",ledBrightness).putInt("led_rotation",ledRotation).putBoolean("led_reverse",ledReverse).putBoolean("led_ring",ledRing).putBoolean("led_ceiling",ledCeiling).apply();applyLedSettings();
    }
    private void renderLedTab(){
        NativeSettings.Group link=settingsGroup(UiText.t("게임 LED 연동"));
        link.note(ledDescription());
        link.toggle(tr("LED 연동","LED 联动"),tr("Virtual Keyboard를 켜지 않아도 작동합니다.","无需开启 Virtual Keyboard。"),ledEnabled,value->{
            ledEnabled=value;saveLedSettings();if(value){startLeds();if(ledCeiling&&hid==null){if(connected)disconnect(false);ui.postDelayed(this::scan,500);}}else ledOutput.stop();
        });
        link.row(UiText.t("LED 포트"),portName(ledPort),this::chooseLedPort);
        link.row(UiText.t("LED 다시 연결"),tr("끊어진 LED 연결 재시도","重试 LED 连接"),()->{if(ledOutput.running())ledOutput.stop();startLeds();renderTab();});
        NativeSettings.Group ceiling=settingsGroup(tr("천장 RGB 조명","顶部 RGB 灯光"));
        ceiling.toggle(tr("천장등 연동","顶部灯光联动"),tr("스피커 주변 조명 · IO4 포트로 게임 색상 출력","扬声器周围灯光 · 通过 IO4 输出游戏颜色"),ledCeiling,value->{ledCeiling=value;saveLedSettings();if(value&&hid==null){if(connected)disconnect(false);ui.postDelayed(this::scan,500);}});

        ceiling.row(tr("천장등 색상 테스트","顶部灯光颜色测试"),tr("빨강 · 초록 · 파랑 / 2초","红 · 绿 · 蓝 / 2 秒"),()->
            choose(tr("천장등 색상 테스트","顶部灯光颜色测试"),new String[]{UiText.t("빨강"),UiText.t("초록"),UiText.t("파랑")},-1,n->{if(hid==null||!ledEnabled||!ledCeiling){tell(tr("LED 연동과 천장등을 켜고 IO4를 연결하세요.","请开启 LED 和顶部灯光并连接 IO4。"));return;}ceilingOutput.test(new int[]{0xff0000,0x00ff00,0x0000ff}[n]);}));
        ceiling.row(tr("천장등 다시 연결","重新连接顶部灯光"),tr("IO4 입력·조명 연결을 다시 시작","重新启动 IO4 输入与灯光连接"),()->{disconnect(false);ui.postDelayed(this::scan,600);});
        NativeSettings.Group level=settingsGroup(UiText.t("밝기와 배치"));
        level.row(UiText.t("LED 밝기"),ledBrightness+"%",this::editLedBrightness);
        level.row(tr("LED 회전","LED 旋转"),ledRotation+tr("칸","格"),()->{
            String[] steps=new String[8];for(int i=0;i<steps.length;i++)steps[i]=i+tr("칸","格");
            choose(tr("LED 회전","LED 旋转"),steps,ledRotation,n->{ledRotation=n;saveLedSettings();renderTab();});
        });
        level.toggle(tr("순서 반전","反转顺序"),tr("버튼 LED의 순서를 반대로 표시","反转按钮 LED 顺序"),ledReverse,value->{ledReverse=value;saveLedSettings();});
        level.row(tr("색상 테스트","颜色测试"),tr("빨강 · 초록 · 파랑 / 2초","红 · 绿 · 蓝 / 2 秒"),()->
            choose(tr("색상 테스트","颜色测试"),new String[]{UiText.t("빨강"),UiText.t("초록"),UiText.t("파랑")},-1,n->{if(!ledOutput.running()){tell(UiText.t("LED 연동을 먼저 시작하세요."));return;}ledOutput.test(new int[]{0xff0000,0x00ff00,0x0000ff}[n]);}));
        NativeSettings.Group board=settingsGroup(UiText.t("보드와 링 조명"));
        board.row(tr("LED 시작 번호","LED 起始编号"),String.valueOf(ledBase),()->editLedNumber(UiText.t("LED 시작 번호 (0~24)"),ledBase,0,24,value->{ledBase=value;saveLedSettings();renderTab();},true));
        board.row(tr("노드 주소","节点地址"),String.valueOf(ledAddress),()->editLedNumber(UiText.t("LED 노드 주소 (1~255)"),ledAddress,1,255,value->{ledAddress=value;saveLedSettings();renderTab();},true));
        board.toggle(tr("링·기체 조명","环形与机身灯光"),tr("기체·링·측면 밝기 연동 · 천장 RGB와 별도","机身、环形及侧面亮度联动 · 独立于顶部 RGB"),ledRing,value->{ledRing=value;saveLedSettings();});
        board.note(tr("기본값은 버튼 1–8 → LED 0–7, 노드 17입니다. RGB 버튼만 있으면 링 조명은 끄세요.","默认按钮 1–8 对应 LED 0–7，节点为 17。只有 RGB 按钮时请关闭环形灯光。"));
    }
    private void editLedBrightness(){
        NativeUi.brightness(activity,ledBrightness,this::protect,value->{ledBrightness=value;saveLedSettings();});
    }
    private void editLedNumber(String title,int current,int min,int max,java.util.function.IntConsumer changed,boolean requireStopped){
        if(requireStopped&&ledOutput.running()){tell(UiText.t("LED 연동을 끈 뒤 변경하세요."));return;}
        NativeUi.number(activity,title,current,min,max,this::protect,changed);
    }
    private void chooseLedPort(){
        if(ledOutput.running()){tell(UiText.t("LED 연동을 끈 뒤 포트를 바꾸세요."));return;}
        List<UsbIo.Port> options=new ArrayList<>();for(UsbIo.Port p:ports)if(!p.hid&&!p.key().equals(touchPort)&&!p.key().equals(aimePort)&&PortSelection.role(p.name,p.hid)!=PortSelection.NFC)options.add(p);
        String[] labels=new String[options.size()+1];labels[0]=UiText.t("사용 안 함");for(int i=0;i<options.size();i++)labels[i+1]=options.get(i).name+" · IF"+options.get(i).controlId;
        choose(UiText.t("onii-mai LED 포트 선택"),labels,-1,n->{ledPort=n==0?"":options.get(n-1).key();savePort("led_identity",n==0?null:options.get(n-1));refreshPortLabels();});
    }
    private void startLeds(){
        if(destroyed)return;
        if(ledPort.isEmpty()||ledPort.equals(touchPort)||ledPort.equals(aimePort)){tell(UiText.t("터치와 다른 LED 포트를 선택하세요."));return;}
        for(UsbIo.Port p:ports)if(p.key().equals(ledPort)){
            if(!usb.hasPermission(p.device)){tell(UiText.t("연결 탭에서 USB 권한을 먼저 허용하세요."));return;}
            ledEnabled=true;saveLedSettings();ledOutput.start(p,ledAddress,ledBase);return;
        }
        tell(UiText.t("LED 포트가 없습니다. 연결 탭에서 USB 기기를 찾으세요."));
    }
    private String aimeDescription(){
        if(aimeEnabled&&aimePort.isEmpty())return tr("onii-mai NFC 포트 연결 대기","等待连接 onii-mai NFC 端口");
        return aimeReader.summary();
    }
    private void chooseAimePort(){
        if(aimeReader.running()){tell(tr("Aime 카드 인식을 끈 뒤 포트를 바꾸세요.","请先关闭 Aime 读卡，再更改端口。"));return;}
        List<UsbIo.Port> options=new ArrayList<>();
        for(UsbIo.Port p:ports)if(!p.hid&&!p.key().equals(touchPort)&&!p.key().equals(ledPort)&&PortSelection.role(p.name,p.hid)!=PortSelection.COMMAND&&PortSelection.role(p.name,p.hid)!=PortSelection.TOUCH&&PortSelection.role(p.name,p.hid)!=PortSelection.LED)options.add(p);
        String[] labels=new String[options.size()+1];labels[0]=UiText.t("사용 안 함");
        for(int i=0;i<options.size();i++)labels[i+1]=options.get(i).name+" · IF"+options.get(i).controlId;
        choose(tr("Aime NFC 포트 선택","选择 Aime NFC 端口"),labels,-1,n->{
            aimePort=n==0?"":options.get(n-1).key();savePort("aime_identity",n==0?null:options.get(n-1));refreshPortLabels();
        });
    }
    private void startAime(){
        if(destroyed||!aimeEnabled||aimeReader.running()||aimePort.isEmpty())return;
        if(aimePort.equals(touchPort)||aimePort.equals(ledPort)){tell(tr("카드 리더는 터치·LED와 다른 포트를 선택하세요.","读卡器必须使用与触摸和 LED 不同的端口。"));return;}
        for(UsbIo.Port p:ports)if(p.key().equals(aimePort)){
            if(!usb.hasPermission(p.device)){requestPermission(p.device);return;}
            aimeReader.start(p);return;
        }
    }
    private void copyDiagnostics(){
        String report="Oniimai Kanade 0.3.7 / API 102\nAndroid "+Build.VERSION.RELEASE+" / "+Build.MANUFACTURER+" "+Build.MODEL+
            "\nController firmware INFO: "+(firmwareInfo==null?"not queried":firmwareInfo.summary())+(firmwareQueryError.isEmpty()?"":" / query failed: "+firmwareQueryError)+
            "\n"+nativeExplanation()+"\nFrames: "+lastStatsFrame+" / Touch reads: "+lastStatsTouch+"\nUSB: "+connected+" / Packets: "+packets+" / HID: "+hidPackets+
            "\n"+(commandMode?"Command: ":"Touch: ")+portName(touchPort)+"\nHID: "+portName(hidPort)+"\nKeys: "+Arrays.toString(keys.map)+"\nLED: "+portName(ledPort)+" / node="+ledAddress+" / base="+ledBase+" / rotation="+ledRotation+" / reverse="+ledReverse+" / brightness="+ledBrightness+" / ring="+ledRing+"\n"+ledOutput.diagnostic()+"\nCeiling: "+ceilingOutput.diagnostic()+"\nNFC: "+portName(aimePort)+" / "+aimeReader.diagnostic()+"\n"+displayOutput.diagnostic()+"\nStats: "+gameFrame+"\n"+message;
        ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Oniimai diagnosis",report));tell(UiText.t("진단 정보를 복사했습니다."));
    }
    private String firmwareDescription(){
        if(firmwareBusy)return tr("버전과 빌드를 읽는 중…","正在读取版本和构建信息…");
        if(!firmwareQueryError.isEmpty())return tr("확인 실패 · 눌러서 다시 시도","读取失败 · 点击重试");
        return firmwareInfo==null?tr("선택한 컨트롤러의 버전과 빌드 읽기","读取所选控制器的版本与构建信息"):firmwareInfo.summary();
    }
    private void refreshFirmwareInfo(){if(firmwareStatus!=null)firmwareStatus.setText(firmwareDescription());}
    private UsbIo.Port firmwareCommandPort(){
        UsbDevice selected=null;
        // Prefer the controller providing the selected input, then NFC/LED when
        // used without inputs. Never choose another connected controller by name.
        for(String key:new String[]{touchPort,hidPort,aimePort,ledPort}){
            for(UsbIo.Port p:ports)if(p.key().equals(key)){selected=p.device;break;}
            if(selected!=null)break;
        }
        if(selected==null)return null;
        String[] devices=new String[ports.size()],names=new String[ports.size()];boolean[] hids=new boolean[ports.size()];
        for(int i=0;i<ports.size();i++){UsbIo.Port p=ports.get(i);devices[i]=p.device.getDeviceName();names[i]=p.name;hids[i]=p.hid;}
        int found=FirmwareInfo.commandPort(devices,names,hids,selected.getDeviceName());
        return found>=0?ports.get(found):null;
    }
    private boolean firmwareDevicePresent(UsbIo.Port port){
        UsbDevice current=usb.getDeviceList().get(port.device.getDeviceName());
        return current!=null&&current.getDeviceId()==port.device.getDeviceId();
    }
    private static FirmwareInfo parseFirmwareInfo(byte[] bytes)throws IOException{
        try{return new FirmwareInfo(bytes);}
        catch(IllegalArgumentException error){throw new IOException(UiText.t("Oniimai Command 응답이 아닙니다."),error);}
    }
    private void queryFirmware(){
        if(destroyed||firmwareBusy)return;
        if(busy){tell(tr("연결 작업이 끝난 뒤 다시 시도하세요.","请在连接完成后重试。"));return;}
        final UsbIo.Port selected=firmwareCommandPort();
        if(selected==null){tell(tr("선택한 컨트롤러의 Command 포트를 찾지 못했습니다.","未找到所选控制器的 Command 端口。"));return;}
        if(!usb.hasPermission(selected.device)){requestPermission(selected.device);return;}
        final int gen=generation.get();
        final boolean inputPort=selected.key().equals(touchPort);
        final boolean otherOwner=(selected.key().equals(aimePort)&&aimeReader.running())||(selected.key().equals(ledPort)&&ledOutput.running());
        firmwareBusy=true;firmwareQueryError="";refreshFirmwareInfo();
        worker.execute(()->{
            FirmwareInfo result=null;String failure=null;CommandChannel temporary=null,shared=null;
            try{
                if(destroyed||gen!=generation.get()||!firmwareDevicePresent(selected))return;
                if(otherOwner)throw new IOException(tr("Command 포트가 다른 기능에 사용 중입니다.","Command 端口正用于其他功能。"));
                CommandChannel active=inputPort?command:null;
                if(active!=null&&!active.isClosed())shared=active;
                if(active==null||active.isClosed()){
                    if(inputPort&&(serial!=null||command!=null))throw new IOException(tr("현재 입력 포트가 사용 중입니다.","当前输入端口正在使用。"));
                    temporary=new CommandChannel(new UsbIo.Cdc(usb,selected,115200),(data,n)->{},line->{},error->{});
                    active=temporary;
                }
                // INFO only: no CONFIG, DEBUG, reset, save or DFU commands.
                result=parseFirmwareInfo(active.request(Protocol.INFO));
            }catch(Exception error){failure=error.getMessage();if(failure==null)failure=error.getClass().getSimpleName();}
            finally{
                if(temporary!=null)temporary.close(); // Never close the shared input channel.
                // A request timeout closes CommandChannel without its reader callback.
                // Recover input only when this query used that actual shared handle.
                if(shared!=null&&shared.isClosed())usbError(gen,tr("펌웨어 조회 중 입력 포트 연결이 끊겼습니다.","读取固件时输入端口已断开。"));
                final FirmwareInfo info=result;final String error=failure;
                ui.post(()->{
                    firmwareBusy=false;
                    if(destroyed)return;
                    if(gen!=generation.get()||!firmwareDevicePresent(selected)){refreshFirmwareInfo();return;}
                    if(info!=null){
                        firmwareInfo=info;firmwareQueryError="";
                        android.util.Log.i("OniimaiKanade","Controller firmware INFO: "+info.summary());
                        tell(tr("컨트롤러 펌웨어 ","控制器固件 ")+info.summary());
                    }else if(error!=null){firmwareQueryError=error;tell(tr("펌웨어 확인 실패: ","固件读取失败：")+error);}
                    refreshFirmwareInfo();
                });
            }
        });
    }
    private void updateLearning(){for(int i=0;i<learnButtons.length;i++)if(learnButtons[i]!=null)learnButtons[i].setText((learn==i?UiText.t("지정 중"):i==8?"P1 / START":""+(i+1))+"\n"+(keys.map[i]<0?UiText.t("없음"):KeyEvent.keyCodeToString(keys.map[i]).replace("KEYCODE_","")));}
    private String portName(String key){for(UsbIo.Port p:ports)if(p.key().equals(key))return p.name+" · IF"+p.controlId;return UiText.t("사용 안 함");}
    private void refreshPortLabels(){if(touchChoice!=null)touchChoice.setText(portName(touchPort));if(hidChoice!=null)hidChoice.setText(portName(hidPort));if(ledChoice!=null)ledChoice.setText(portName(ledPort));if(aimeChoice!=null)aimeChoice.setText(portName(aimePort));}
    private void choosePort(boolean isHid){
        if(connected||busy){tell(UiText.t("연결을 해제한 뒤 변경하세요."));return;}
        List<UsbIo.Port> options=new ArrayList<>();for(UsbIo.Port p:ports)if(p.hid==isHid&&(!ledOutput.running()||!p.key().equals(ledPort))&&!p.key().equals(aimePort)&&PortSelection.role(p.name,p.hid)!=PortSelection.NFC)options.add(p);
        String[] labels=new String[options.size()+1];labels[0]=UiText.t("사용 안 함");
        for(int i=0;i<options.size();i++){UsbIo.Port p=options.get(i);labels[i+1]=p.name+" · IF"+p.controlId+" · "+p.device.getDeviceName();}
        choose(isHid?"IO4 HID":UiText.t("터치 포트 선택"),labels,-1,n->{String key=n==0?"":options.get(n-1).key();UsbIo.Port chosen=n==0?null:options.get(n-1);if(isHid)hidPort=key;else{touchPort=key;if(chosen!=null){commandMode=PortSelection.protocol(chosen.name,chosen.hid,commandMode?0:1)==0;prefs.edit().putBoolean("touch_command",commandMode).apply();}}
            savePort(isHid?"hid_identity":"touch_identity",chosen);renderTab();});
    }
    private void savePort(String name,UsbIo.Port port){prefs.edit().putString(name,port==null?"":port.stableId()).apply();}
    private String resolvePort(String preference,int role){
        String[] ids=new String[ports.size()],names=new String[ports.size()];boolean[] hids=new boolean[ports.size()];
        for(int i=0;i<ports.size();i++){UsbIo.Port p=ports.get(i);ids[i]=p.stableId();names[i]=p.name;hids[i]=p.hid;}
        boolean saved=prefs.contains(preference);String identity=prefs.getString(preference,"");
        if(saved&&identity.isEmpty())return "";
        int index=saved?PortSelection.unique(ids,identity):PortSelection.named(names,hids,role);
        if(index<0)return "";
        UsbIo.Port chosen=ports.get(index);if(usb.hasPermission(chosen.device))savePort(preference,chosen);
        return chosen.key();
    }
    private void scan(){
        if(busy||destroyed||connected)return;
        busy=true;worker.execute(()->{
            List<UsbIo.Port> found=null;String failure=null;
            try{found=UsbIo.ports(usb);}catch(Exception e){failure=e.getMessage();}
            final List<UsbIo.Port> result=found;final String error=failure;
            ui.post(()->{busy=false;if(destroyed)return;if(result==null){message=UiText.t("USB 검색 실패: ")+error;return;}
                ports=result;touchPort=resolvePort("touch_identity",commandMode?PortSelection.COMMAND:PortSelection.TOUCH);
                hidPort=resolvePort("hid_identity",PortSelection.IO4);ledPort=resolvePort("led_identity",PortSelection.LED);
                aimePort=resolvePort("aime_identity",PortSelection.NFC);
                renderDevices();refreshPortLabels();
                if(!setupActive&&prefs.getBoolean("auto_connect",true)){
                    boolean allowed=true;
                    for(UsbIo.Port p:ports){
                        boolean selected=p.key().equals(touchPort)||((buttonMode==2||ledEnabled&&ledCeiling)&&p.key().equals(hidPort))||(ledEnabled&&p.key().equals(ledPort))||(aimeEnabled&&p.key().equals(aimePort));
                        String product=p.device.getProductName();String name=product==null?"":product.toLowerCase(Locale.ROOT);
                        boolean known=PortSelection.role(p.name,p.hid)!=PortSelection.NONE||name.contains("onii-mai")||name.contains("oniimai");
                        if((selected||known)&&!usb.hasPermission(p.device)){
                            allowed=false;String id=p.device.getDeviceName();
                            if(permissionPending==null&&permissionAsked.add(id)){permissionPending=id;requestPermission(p.device);break;}
                        }
                    }
                    if(allowed){if(!touchPort.isEmpty()||((buttonMode==2||ledEnabled&&ledCeiling)&&!hidPort.isEmpty()))connect();if(ledEnabled&&!ledPort.isEmpty()&&!ledOutput.running())startLeds();if(aimeEnabled)startAime();}
                }
            });
        });
    }
    private void renderDevices(){
        if(deviceList==null)return;deviceList.removeAllViews();Set<String> seen=new HashSet<>();
        for(UsbIo.Port p:this.ports)if(seen.add(p.device.getDeviceName())){
            UsbDevice d=p.device;settingRow(deviceList,d.getProductName()==null?d.getDeviceName():d.getProductName(),usb.hasPermission(d)?tr("권한 허용됨","已授予权限"):tr("눌러서 USB 권한 허용","点击授予 USB 权限"),()->requestPermission(d));
        }
        if(seen.isEmpty())text(deviceList,UiText.t("USB 장치 없음 — OTG 케이블을 확인하세요."));
    }
    private void requestPermission(UsbDevice device){
        if(usb.hasPermission(device)){tell(UiText.t("이미 허용된 장치입니다."));return;}
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent pending=PendingIntent.getBroadcast(activity,device.getDeviceId(),new Intent(permissionAction).setPackage(activity.getPackageName()),flags);
        usb.requestPermission(device,pending);
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(permissionAction.equals(intent.getAction())){permissionPending=null;message=UiText.t("USB 권한 결과를 반영합니다.");scan();}
        else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())){permissionAsked.clear();scan();}
        else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())){
            UsbDevice removed=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(removed==null)return;
            if(portOnDevice(aimePort,removed))aimeReader.detached();
            boolean inputs=portOnDevice(touchPort,removed)||portOnDevice(hidPort,removed);
            if(!inputs)return;
            permissionAsked.remove(removed.getDeviceName());if(removed.getDeviceName().equals(permissionPending))permissionPending=null;
            android.util.Log.w("OniimaiKanade","Controller USB detached; releasing this input generation");
            disconnect(false);keys.clear();synchronized(dataLock){keyButtons=0;}push();message=UiText.t("USB 장치가 분리되어 입력을 해제했습니다.");
        }
    }};
    private boolean portOnDevice(String key,UsbDevice device){return !key.isEmpty()&&key.startsWith(device.getDeviceName()+"#");}
    private void connect(){
        if(busy||connected||destroyed)return;
        UsbIo.Port tp=null,hp=null;for(UsbIo.Port p:ports){if(p.key().equals(touchPort))tp=p;if((buttonMode==2||ledEnabled&&ledCeiling)&&p.key().equals(hidPort))hp=p;}
        if(tp==null&&hp==null){tell(UiText.t("포트를 선택하세요. 키보드만 사용하면 USB 연결 없이 입력을 켤 수 있습니다."));return;}
        if(tp!=null&&ledOutput.running()&&tp.key().equals(ledPort)){tell(UiText.t("LED와 입력은 서로 다른 포트를 선택하세요."));return;}
        if(tp!=null&&aimeReader.running()&&tp.key().equals(aimePort)){tell(tr("카드 리더와 입력은 서로 다른 포트를 선택하세요.","读卡器和输入必须使用不同端口。"));return;}
        final UsbIo.Port t=tp,h=hp;final boolean useCommand=commandMode;final int bank=player,gen=generation.incrementAndGet();
        busy=true;packets=hidPackets=0;worker.execute(()->{
            try{
                if(t!=null){
                    if(useCommand){
                        command=new CommandChannel(new UsbIo.Cdc(usb,t,115200),(data,n)->{if(gen!=generation.get())return;try{touch(new Protocol.TouchDebug(data).pressed);}catch(Exception e){postMessage(UiText.t("터치 프레임 오류: ")+e.getMessage());}},this::postMessage,error->usbError(gen,error));
                        FirmwareInfo info=parseFirmwareInfo(command.request(Protocol.INFO));
                        ui.post(()->{if(!destroyed&&gen==generation.get()){firmwareInfo=info;firmwareQueryError="";refreshFirmwareInfo();android.util.Log.i("OniimaiKanade","Controller firmware INFO: "+info.summary());}});
                        new Protocol.Config(command.request(Protocol.CONFIG_GET));command.request(Protocol.DEBUG_START);
                    }else{
                        serial=new UsbIo.Cdc(usb,t,9600);Protocol.TouchParser parser=new Protocol.TouchParser(pressed->{if(gen==generation.get())touch(pressed);});
                        serial.start(parser::feed,error->usbError(gen,error));serial.write("{HALT}{RSET}{STAT}".getBytes(StandardCharsets.US_ASCII));
                    }
                }
                if(h!=null)hid=new UsbIo.Hid(usb,h,(data,n)->{if(gen==generation.get()&&buttonMode==2)try{buttons(Io4Input.mask(data,bank));}catch(Exception e){postMessage(UiText.t("IO4 형식 불일치: ")+n+" bytes");}},error->usbError(gen,error));
                if(gen!=generation.get())throw new IOException(UiText.t("연결이 취소되었습니다."));
                connected=true;postMessage(UiText.t("연결 완료. 센서 상태를 확인한 뒤 입력을 켜세요."));
                ui.post(()->{if(!destroyed&&gen==generation.get()){if(ledEnabled&&!ledOutput.running())startLeds();if(aimeEnabled)startAime();}});
            }catch(Exception e){closePorts(false);postMessage(UiText.t("연결 실패: ")+e.getMessage());}
            finally{busy=false;}
        });
    }
    private void usbError(int sourceGeneration,String error){ui.post(()->{if(!destroyed&&sourceGeneration==generation.get()){
        android.util.Log.w("OniimaiKanade","Current controller input transport failed; reconnecting");
        disconnect(false);message=UiText.t("USB 오류: ")+error;
    }});}
    private void disconnect(boolean stopLed){
        connected=false;
        if(stopLed){ledOutput.stop();aimeReader.stop();}
        suspendInput(stopLed);generation.incrementAndGet();synchronized(dataLock){touches=0;hidButtons=0;}push();
        if(!destroyed){busy=true;worker.execute(()->{try{closePorts(stopLed);postMessage(UiText.t("USB 연결을 해제했습니다."));}finally{busy=false;}});}
    }
    private void closePorts(boolean graceful){
        if(graceful){try{if(command!=null&&!command.isClosed())command.request(Protocol.DEBUG_STOP);}catch(Exception ignored){}
            try{if(hid!=null)hid.writeCeiling(0);}catch(Exception ignored){}
            try{if(serial!=null)serial.write("{HALT}".getBytes(StandardCharsets.US_ASCII));}catch(Exception ignored){}}
        if(command!=null){command.close();command=null;}if(serial!=null){serial.close();serial=null;}if(hid!=null){hid.close();hid=null;}connected=false;
    }
    void destroy(){
        if(destroyed)return;destroyed=true;armed=false;allowAime(false);generation.incrementAndGet();push();ui.removeCallbacks(tick);
        phoneNfc.destroy();gameAlbum.close();
        if(displayOutput!=null)displayOutput.destroy();
        ledOutput.destroy();
        ceilingOutput.destroy();
        aimeReader.destroy();
        try{activity.unregisterReceiver(receiver);}catch(Exception ignored){}
        for(AlertDialog dialog:new ArrayList<>(dialogs))if(dialog.isShowing())dialog.dismiss();if(overlay!=null&&overlay.getParent() instanceof ViewGroup)((ViewGroup)overlay.getParent()).removeView(overlay);
        worker.execute(()->closePorts(true));worker.shutdown();
    }
}
