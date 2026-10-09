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
import org.json.JSONException;
import org.json.JSONObject;

final class GameSession implements DashboardHost, SetupProbe {
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
    private volatile boolean nativeProbed;
    private volatile String message=I18n.t(Msg.INPUT_INITIAL);
    private volatile CommandChannel command;
    private FirmwareInfo firmwareInfo;
    private boolean firmwareBusy;
    private String firmwareQueryError="";
    private TextView firmwareStatus;
    private UsbIo.Cdc serial;
    private volatile UsbIo.Hid hid;
    private List<UsbIo.Port> ports=new ArrayList<>();
    private String touchPort="",hidPort="";
    /** Port keys actually opened by the current input connection; empty when that role is not open. */
    private volatile String linkedTouch="",linkedHid="";
    /** Why the touch role is missing from a buttons-only connection; shown under the status headline. */
    private volatile String touchIssue="";
    /** Automatic reconnects to add a missing input. Bounded so a port that keeps failing cannot cycle the buttons. */
    private int inputAddAttempts;
    private static final int MAX_INPUT_ADD_ATTEMPTS=3;
    private boolean commandMode;
    private int player,learn=-1;
    private volatile int buttonMode=2;
    private FrameLayout overlay;
    private DisplayOutput displayOutput;
    private final LedOutput ledOutput;
    private final LobbyLights lobbyLights=new LobbyLights();
    private final CeilingOutput ceilingOutput;
    private final AimeReader aimeReader;
    private final PhoneNfcReader phoneNfc;
    private final FrontCamera frontCamera;
    private boolean aimeEnabled,nativeAimeAllowed;
    private String aimePort="";
    private TextView aimeChoice,aimeStatusView;
    private String ledPort="";
    private boolean ledEnabled,ledReverse,ledRing,ledCeiling;
    private int ledAddress,ledBase,ledRotation,ledBrightness;
    private TextView ledStatusView,ledChoice;
    private LinearLayout panelBody,tabStrip;
    private ScrollView panelScroll;
    private final int[] tabScroll=new int[5];
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
        frontCamera=new FrontCamera(activity,prefs,loaded);
        applyDefaults(prefs); UiLanguage.load(prefs); commandMode=prefs.getBoolean("touch_command",false);
        inputRequested=prefs.getBoolean("input_enabled",true);buttonMode=bounded(prefs.getInt("button_mode",2),0,2);GameAssets.bind(assetContext());
        ledOutput=new LedOutput(usb,loaded,lobbyLights,setupLights);ledEnabled=prefs.getBoolean("led_enabled",true);
        ceilingOutput=new CeilingOutput(()->hid,loaded);ceilingOutput.setup(setupLights);ledCeiling=prefs.getBoolean("led_ceiling",true);
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
        panelTab=bounded(prefs.getInt("settings_tab",0),0,4);
        for(int i=0;i<tabScroll.length;i++)tabScroll[i]=Math.max(0,prefs.getInt("settings_scroll_"+i,0));
        IntentFilter filter=new IntentFilter(permissionAction);filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(receiver,filter);
        attachControls();displayOutput=new DisplayOutput(activity,overlay,prefs,this::suspendInput,this::tell,this::protect,this,this::gameFrames);ui.post(tick);
        ui.postDelayed(()->{if(!destroyed){startupChecksEnabled=true;advanceInitialization();}},700);
    }
    private final Set<AlertDialog> dialogs=Collections.newSetFromMap(new WeakHashMap<AlertDialog,Boolean>());
    private final Set<String> permissionAsked=new HashSet<>();
    private String permissionPending;
    private boolean inputRequested,dashboardEditing,setupActive,focus=true,initializing=true;
    // Setup's connection step may look for the controller before setup is dismissed; refusals and failed
    // openings are remembered so that step can say so instead of waiting forever.
    private boolean setupSearching,permissionRefused;private volatile boolean openFailed;
    private boolean startupChecksEnabled,setupWaitingNotified,setupRequested;
    private View setupNotice;
    private final Runnable dismissSetupNotice=this::clearSetupNotice;
    private long lastSetupCheck;
    private final InitialSetupGate setupGate=new InitialSetupGate();
    private long neutralSince,lastScan,lastGameFrame,lastPresence;
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
    /**
     * Output progress: a verified interactive startup UI or the running game, with separate phases,
     * PENDING while they may still install (not probed yet, or waiting for the game library),
     * UNAVAILABLE when they cannot (native load failure, no hook API, unsupported build, install failure).
     */
    private OutputGate.Sample gameFrames(){
        if(!nativeLoaded)return new OutputGate.Sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_WAITING);
        if(!nativeProbed||nativeStatus==-2)return new OutputGate.Sample(OutputGate.PENDING,OutputGate.PHASE_WAITING);
        if(nativeStatus!=15)return new OutputGate.Sample(OutputGate.UNAVAILABLE,OutputGate.PHASE_WAITING);
        long[] progress=NativeBridge.displayFrames();
        if(progress==null||progress.length<2||progress[0]<0||progress[0]>2)
            return new OutputGate.Sample(OutputGate.PENDING,OutputGate.PHASE_WAITING);
        // phase, counter, stages reached after Start, and whether those stages are reported.
        if(progress.length>=4)return new OutputGate.Sample(progress[1],(int)progress[0],(int)progress[2],progress[3]==1);
        return new OutputGate.Sample(progress[1],(int)progress[0]);
    }
    public Activity activity(){return activity;}
    public Context assetContext(){try{return activity.createPackageContext("io.oniimai.kanade",0);}catch(Exception e){return activity;}}
    public SharedPreferences prefs(){return prefs;}
    public long[] diagnostic(){synchronized(dataLock){return new long[]{hidButtons|keyButtons,touches,armed?0:1,(touchPort.isEmpty()?0:connected?1:0)|(hidPort.isEmpty()?keyButtons!=0?2:0:connected?2:0)};}}
    public String connectionDescription(){
        if(!connected)return message;
        boolean touch=!linkedTouch.isEmpty(),buttons=!linkedHid.isEmpty();
        // The waiting part leads, so a partial connection never reads as fully connected.
        if(!touch&&!disabledPort("touch_identity"))return I18n.t(Msg.INPUT_STATUS_BUTTONS_ONLY)+(touchIssue.isEmpty()?"":"\n"+touchIssue);
        if(!buttons&&wantsHid())return I18n.t(Msg.INPUT_STATUS_TOUCH_ONLY);
        return touch&&buttons?I18n.t(Msg.INPUT_STATUS_BOTH):touch?I18n.t(Msg.INPUT_STATUS_TOUCH):I18n.t(Msg.INPUT_STATUS_BUTTONS);
    }
    // Read by first-run setup about 30 times a second; the display list is a binder call, so it is asked at most once a second.
    private final SetupLink.Poll externalPoll=new SetupLink.Poll(1000);private boolean externalSeen;
    @Override public boolean ledLinked(){return ledEnabled&&(ledOutput.linked()||ledCeiling&&ceilingOutput.ready());}
    @Override public boolean external(){if(externalPoll.due(SystemClock.uptimeMillis()))externalSeen=displayOutput!=null&&displayOutput.hasExternal();return externalSeen;}
    // Setup's lights and its page on the controller's screen follow what setup shows, from the UI thread.
    private final SetupLights setupLights=new SetupLights();
    @Override public void setupView(SetupView view){
        setupLights.update(view.getStep(),view.getLinkCode(),view.getPressed(),view.getCheckedButtons(),view.getTouched(),view.getTouchView(),view.getLeds(),SystemClock.uptimeMillis());
        if(displayOutput!=null)displayOutput.setupPreview(view);
    }
    @Override public SetupChoices setupChoices(){
        return new SetupChoices(prefs.getBoolean("external_enabled",true),displayOutput!=null?displayOutput.isClockwise():prefs.getBoolean("external_clockwise",false),
            prefs.getBoolean("auto_connect",true),ledEnabled,aimeEnabled);
    }
    @Override public List<NativeSettings.Group> setupSettings(int tab){forSetup=true;try{return nativeSettings(tab);}finally{forSetup=false;}}
    @Override public void setupSearch(){if(setupActive&&!setupSearching){setupSearching=true;lastScan=SystemClock.uptimeMillis();scan();}}
    @Override public int setupInputs(){return SetupLink.open(connected,!linkedTouch.isEmpty(),!linkedHid.isEmpty(),buttonMode);}
    @Override public int setupLink(){
        // From the channels that really opened, so touch failing while IO4 opens reads as partial, not connected.
        return SetupLink.state(connected,setupInputs(),SetupLink.expected(disabledPort("touch_identity"),buttonMode,disabledPort("hid_identity")),
            setupSearching,permissionPending!=null,permissionRefused||openFailed);
    }
    @Override public void setupRetry(){permissionRefused=false;openFailed=false;permissionAsked.clear();inputAddAttempts=0;scan();}
    public int ledTone(){return !ledEnabled?NativeSettings.INFO:ledOutput.linked()?NativeSettings.OK:NativeSettings.WAIT;}
    public int displayTone(){return externalActive()?NativeSettings.OK:NativeSettings.INFO;}
    public int inputTone(){
        if(!connected)return NativeSettings.WAIT;
        boolean partial=(linkedTouch.isEmpty()&&!disabledPort("touch_identity"))||(linkedHid.isEmpty()&&wantsHid());
        return partial?NativeSettings.WAIT:NativeSettings.OK;
    }
    public String ledDescription(){return ledOutput.summary()+"\n"+ceilingOutput.summary();}
    public String displayDescription(){return displayOutput==null?"—":displayOutput.summary();}
    public boolean externalActive(){return displayOutput!=null&&displayOutput.active();}
    public LobbyLights lobbyLights(){return lobbyLights;}
    public boolean requestStartup(){
        return nativeLoaded&&nativeStatus==15&&foreground&&focus&&!initializing&&!uiBlocked()&&NativeBridge.requestStartup();
    }
    public boolean ledEnabled(){return ledEnabled;}
    public void showSettings(){showPanel();}
    public void updateDashboard(DashboardView view){gameAlbum.update(gameFrame);view.update(gameFrame,!nativeLoaded||nativeStatus!=15,gameAlbum.bitmap());}
    public void setDashboardEditing(boolean editing){dashboardEditing=editing;suspendInput();}
    public void choose(String title,String[] options,int selected,java.util.function.IntConsumer action){
        NativeUi.choice(activity,title,options,selected,this::protect,action);
    }
    private void advanceInitialization(){
        if(destroyed||!startupChecksEnabled)return;
        if(setupRequested){showSetup(false);return;}
        if(!initializing)return;
        if(prefs.getBoolean("setup_complete",false)){startAutomatic();return;}
        showSetup(false);
    }
    private void showSetup(){showSetup(true);}
    private void showSetup(boolean requested){
        if(destroyed||setupActive)return;
        if(requested)setupRequested=true;
        boolean available=foreground&&!activity.isFinishing()&&!activity.isDestroyed()&&!uiBlocked();
        // Opened again from Settings once setup is finished: the installer gate and its wait for settled
        // main-screen frames only guard first run, so setup opens as soon as the settings panel has gone.
        if(setupRequested&&prefs.getBoolean("setup_complete",false)){if(available)openSetup();return;}
        boolean uiReady=available&&focus&&activity.hasWindowFocus();
        OutputGate.Sample sample=gameFrames();
        if(!setupGate.ready(sample,uiReady,SystemClock.uptimeMillis())){
            // Inform once automatically, and again when the user explicitly opens first-run setup.
            // Never mark setup complete or start USB permissions while the game's installer is on top.
            if(uiReady&&sample.phase==OutputGate.PHASE_WAITING&&(requested||(!setupWaitingNotified&&nativeProbed))){
                setupWaitingNotified=true;message=I18n.t(Msg.SETUP_WAIT_MAIN);
                showSetupNotice(message);
            }
            return;
        }
        openSetup();
    }
    private void openSetup(){
        clearSetupNotice();suspendInput();setupActive=true;setupRequested=false;setupGate.reset();
        InitialSetup.show(activity,prefs,this,this::protect,()->{commandMode=prefs.getBoolean("touch_command",false);buttonMode=bounded(prefs.getInt("button_mode",2),0,2);ledEnabled=prefs.getBoolean("led_enabled",true);aimeEnabled=prefs.getBoolean("aime_enabled",true);languageChanged();},()->{
            setupActive=false;setupSearching=false;if(destroyed)return;
            // Setup's own screen leaves the controller's display, the saved rotation applies again, and the
            // lights go back to the welcome screen and the game.
            displayOutput.setupEnded();
            // The saved LED choice reaches the ring board and the ceiling light before setup's lighting lets go,
            // so lighting turned off in setup stays off instead of the game's colours coming back on the ceiling.
            applyLedSettings();setupLights.close();
            if(!ledEnabled)ledOutput.stop();else if(!ledPort.isEmpty()&&!ledOutput.running())startLeds();
            if(!aimeEnabled)aimeReader.stop();else if(!aimePort.isEmpty()&&!aimeReader.running())startAime();
            // Also cancels an output that is still waiting for the game, which is not yet "active".
            if(!prefs.getBoolean("external_enabled",true))displayOutput.disabledBySettings(I18n.t(Msg.DISPLAY_RETURNED_TO_PHONE));
            startAutomatic();
        });
    }
    private void startAutomatic(){initializing=false;if(!destroyed){scan();displayOutput.resume();}}
    private void showSetupNotice(String text){
        clearSetupNotice();
        setupNotice=NativeUi.inlineNotice(activity,text,this::clearSetupNotice);
        overlay.addView(setupNotice,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        long duration=8000;
        if(Build.VERSION.SDK_INT>=29){
            android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if(accessibility!=null)duration=accessibility.getRecommendedTimeoutMillis((int)duration,
                android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT|android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        ui.postDelayed(dismissSetupNotice,duration);
    }
    private void clearSetupNotice(){
        ui.removeCallbacks(dismissSetupNotice);
        if(setupNotice!=null){overlay.removeView(setupNotice);setupNotice=null;}
    }
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
        returnButton=Miui.action(activity,I18n.t(Msg.OVERLAY_MONITOR_ON),true,()->displayOutput.enable());
        returnButton.setTextSize(12);returnButton.setVisibility(View.GONE);
        overlay.addView(returnButton,new FrameLayout.LayoutParams(-2,dp(44),Gravity.TOP|Gravity.START));FloatingShortcut.attach(returnButton,prefs);
        activity.addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
    }
    private void attachSettingsButton(){
        if(settingsButton!=null)overlay.removeView(settingsButton);
        settingsButton=NativeUi.floatingSettings(activity,this::showPanel);
        // Measure the translated label itself; a fixed width clips longer languages.
        overlay.addView(settingsButton,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START));
        FloatingShortcut.attach(settingsButton,prefs,"settingsShortcut",16);
        updateSettingsButtonVisibility();
    }
    private void updateSettingsButtonVisibility(){
        boolean playing=false;
        if(nativeLoaded&&nativeStatus==15&&gameFrame!=null){
            try{
                JSONObject frame=new JSONObject(gameFrame);
                String scene=frame.optString("scene");
                // "game" means a score snapshot is available: it is initially false during play
                // and stays true on Results. Use the verified process lifecycle instead.
                // Loading here is the finished-song transition into Results, not game startup.
                playing=frame.optInt("status")==511&&!frame.optBoolean("result")
                    &&("Game".equals(scene)||"Loading".equals(scene));
            }catch(JSONException ignored){/* Keep Settings reachable if statistics are unavailable. */}
        }
        int visibility=externalActive()||playing?View.GONE:View.VISIBLE;
        if(settingsButton!=null&&settingsButton.getVisibility()!=visibility)settingsButton.setVisibility(visibility);
    }
    boolean portraitLocked(){return displayOutput!=null&&displayOutput.portraitLocked();}
    private void toggleArm(){
        if(inputRequested){inputRequested=false;prefs.edit().putBoolean("input_enabled",false).apply();suspendInput();return;}
        if(!nativeLoaded||nativeStatus!=15){tell(nativeExplanation());return;}
        synchronized(dataLock){if(touches!=0||hidButtons!=0||keyButtons!=0){tell(I18n.t(Msg.INPUT_RELEASE_FIRST));return;}}
        inputRequested=true;prefs.edit().putBoolean("input_enabled",true).apply();neutralSince=0;push();tell(I18n.t(Msg.INPUT_ENABLED));
    }
    private void allowAime(boolean allowed){if(nativeLoaded&&nativeAimeAllowed!=allowed){nativeAimeAllowed=allowed;NativeBridge.aimeEnabled(allowed);}}
    void suspendInput(){suspendInput(true);}
    private void suspendInput(boolean suspendCard){neutralSince=0;armed=false;if(suspendCard)allowAime(false);push();}
    void focusLost(){setupGate.reset();focus=false;suspendInput();keys.clear();synchronized(dataLock){keyButtons=0;}push();}
    void pause(){frontCamera.foreground(false);clearSetupNotice();setupGate.reset();phoneNfc.pause();if(displayOutput!=null)displayOutput.pause();foreground=false;ledOutput.foreground(false);ceilingOutput.foreground(false);aimeReader.foreground(false);suspendInput();keys.clear();learn=-1;synchronized(dataLock){keyButtons=0;}push();}
    void resume(){frontCamera.foreground(true);foreground=true;focus=activity.hasWindowFocus();ledOutput.foreground(true);ceilingOutput.foreground(true);aimeReader.foreground(true);if(displayOutput!=null)displayOutput.resume();}
    boolean motion(MotionEvent event){return guard.captures(event);}
    boolean key(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&!guard.captures(event)&&externalActive()){
            if(event.getAction()==KeyEvent.ACTION_UP){if(!displayOutput.dashboardBack())displayOutput.stop(I18n.t(Msg.DISPLAY_SWITCHED_TO_PHONE));}return true;
        }
        if(!guard.captures(event))return false;
        int action=event.getAction();
        if(action==KeyEvent.ACTION_DOWN||action==KeyEvent.ACTION_UP){
            boolean changed=keys.event(event.getDeviceId(),event.getKeyCode(),action==KeyEvent.ACTION_DOWN,event.getRepeatCount());
            if(changed&&action==KeyEvent.ACTION_DOWN&&learn>=0){
                keys.learn(learn,event.getKeyCode());SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<keys.map.length;i++)edit.putInt("key"+i,keys.map[i]);edit.apply();
                learn=-1;message=I18n.t(Msg.INPUT_KEYS_SAVED);updateLearning();
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
        int previous,current;
        synchronized(dataLock){previous=hidButtons;hidButtons=buttonMode==2?mask:0;current=hidButtons;hidAt=SystemClock.uptimeMillis();hidPackets++;}
        // Only switch transitions are logged, never the 1000 Hz report stream.
        if(((previous^current)&(Io4Input.TEST|Io4Input.SERVICE))!=0)
            android.util.Log.i("OniimaiKanade","IO4 cabinet TEST="+((current&Io4Input.TEST)!=0)+" SERVICE="+((current&Io4Input.SERVICE)!=0));
        push();
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(destroyed)return;push();long now=SystemClock.uptimeMillis();updateAutomaticInput(now);
        if(aimeReader.takeDetectStall())saveUsbFailure("usb_last_nfc_stall","nfc_detect_slow");
        boolean inputsReady=connected||(touchPort.isEmpty()&&(buttonMode!=2||hidPort.isEmpty()));
        // NFC owns its own USB transport. An IO4/touch reconnect must not erase
        // the reader's pending error before Unity can consume it.
        boolean phoneEnabled=prefs.getBoolean("phone_nfc_enabled",true)&&phoneNfc.supported();
        allowAime(((aimeEnabled&&aimeReader.running()&&inputRequested)||phoneEnabled)&&foreground&&focus&&!initializing&&!uiBlocked()&&nativeStatus==15);
        phoneNfc.update(phoneEnabled&&nativeAimeAllowed,now);
        if(now-lastGameFrame>=150){lastGameFrame=now;if(nativeLoaded&&nativeStatus==15)gameFrame=NativeBridge.gameplayStats();
            boolean external=externalActive();updateSettingsButtonVisibility();
            returnButton.setVisibility(!external&&displayOutput.hasExternal()?View.VISIBLE:View.GONE);
        }
        if(now-lastScan>=(setupSearching?2000:4000)&&foreground&&(setupSearching||!setupActive&&!initializing&&prefs.getBoolean("auto_connect",true))&&!busy){
            if(!connected){lastScan=now;scan();}
            else if(now-lastScan>=10000&&waitingForSavedInput()){lastScan=now;scan();}
        }
        if(connected&&!busy&&now-lastPresence>=2000){lastPresence=now;
            if(!inputDevicePresent()){
                android.util.Log.w("OniimaiKanade","Controller input device left the USB list without a detach broadcast");
                saveUsbFailure("usb_last_detach","input_device_missing");releaseDetachedInput();
            }
        }

        if(nativeLoaded&&now-lastProbe>1000){lastProbe=now;int before=nativeStatus;nativeStatus=NativeBridge.initialize();nativeProbed=true;
            frontCamera.publish();
            if(before!=15&&nativeStatus==15&&displayOutput!=null)displayOutput.gameReady();
            if(displayOutput!=null)displayOutput.observeFrames();long[] stats=NativeBridge.stats();if(stats!=null){lastStatsFrame=stats[1];lastStatsTouch=stats[2];}}
        if(now-lastSetupCheck>=250){lastSetupCheck=now;advanceInitialization();}
        if(status!=null&&now-lastUi>=200){lastUi=now;long t;int b;synchronized(dataLock){t=touches;b=hidButtons|keyButtons;}
            status.setText(I18n.t(Msg.LEGACY_STATUS_LINE,
                connected?I18n.t(Msg.LEGACY_STATUS_CONNECTED):I18n.t(Msg.LEGACY_STATUS_WAITING),busy?I18n.t(Msg.LEGACY_STATUS_BUSY):"",nativeStatus==15?I18n.t(Msg.LEGACY_STATUS_GAME_READY):I18n.t(Msg.LEGACY_STATUS_GAME_CHECK),
                Long.bitCount(t),Integer.bitCount(b&255),(b&256)!=0?"ON":"OFF",message));
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
        if(!nativeLoaded)return I18n.t(Msg.NATIVE_LOAD_FAILED);
        switch(nativeStatus){
            case 15:return I18n.t(Msg.NATIVE_READY);
            case -1:return I18n.t(Msg.NATIVE_NO_HOOK_API);
            case -2:return I18n.t(Msg.NATIVE_WAITING_UNITY);
            case -3:return I18n.t(Msg.NATIVE_UNSUPPORTED_BUILD);
            case -4:return I18n.t(Msg.NATIVE_UNEXPECTED_CODE);
            case -5:return I18n.t(Msg.NATIVE_HOOK_FAILED);
            default:return I18n.t(Msg.NATIVE_STATUS,nativeStatus);
        }
    }
    private void tell(String text){message=text;Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
    private void postMessage(String text){ui.post(()->{if(!destroyed){message=text;if(panel!=null)refreshPortLabels();}});}
    private Button button(LinearLayout parent,String text,Runnable action){return Miui.addAction(parent,text,false,action);}
    private void text(LinearLayout parent,String text){Miui.note(parent,text);}
    private void protect(AlertDialog dialog){suspendInput();dialogs.add(dialog);dialog.setOnKeyListener((d,code,event)->guard.captures(event)&&key(event));}
    private void showPanel(){showPanel(true);}
    private void showPanel(boolean scanPorts){
        if(destroyed||setupActive)return;
        if(!prefs.getBoolean("setup_complete",false)){showSetup();return;}
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
    void layoutChanged(){setupGate.reset();ui.post(this::resizePanel);if(displayOutput!=null)displayOutput.configurationChanged();}
    private void languageChanged(){
        // Keep Unity, USB and the external Surface alive while rebuilding only these views.
        attachSettingsButton();displayOutput.languageChanged();
        tell(I18n.t(Msg.LANGUAGE_CHANGED));
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
    /** Building a settings tab for setup's detailed settings, which leaves out the way back into setup. */
    private boolean forSetup;
    List<NativeSettings.Group> nativeSettings(int tab){
        buildingSettings=new ArrayList<>();
        // Test features are intentionally outside first-run hardware setup.
        tab=bounded(tab,0,forSetup?3:4);
        if(tab==0)renderConnectionTab();else if(tab==1)renderDisplayTab();else if(tab==2)renderButtonTab();else if(tab==3)renderLedTab();else renderLabsTab();
        return buildingSettings;
    }
    int settingsTab(){return panelTab;}
    void settingsTab(int tab){panelTab=bounded(tab,0,4);learn=-1;prefs.edit().putInt("settings_tab",panelTab).apply();}
    void chooseLanguage(){UiLanguage.choose(activity,prefs,this::protect,this::languageChanged);}
    void showLicenses(){LicenseUi.show(activity,this::protect);}
    void showAbout(){String game=gameVersion();NativeUi.about(activity,()->NativeUi.aboutInfo(activity,game,nativeExplanation(),firmwareDescription()),this::protect,this::queryFirmware,this::copyDiagnostics);}
    /** The running game's own version, for About. */
    private String gameVersion(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return null;}}
    private NativeSettings.Group settingsGroup(String title){NativeSettings.Group group=new NativeSettings.Group(title);buildingSettings.add(group);return group;}
    private TextView settingRow(LinearLayout group,String title,String value,Runnable action){return GameUi.row(group,title,value,action);}
    private void renderConnectionTab(){
        NativeSettings.Group input=settingsGroup(I18n.t(Msg.SETTINGS_CONNECTION_GROUP));
        input.status(connectionDescription(),inputTone());
        input.toggle(I18n.t(Msg.SETTINGS_INPUT_TOGGLE),I18n.t(Msg.SETTINGS_INPUT_TOGGLE_SUMMARY),inputRequested,value->{
            if(value!=inputRequested)toggleArm();
        });
        input.toggle(I18n.t(Msg.SETTINGS_AUTO_CONNECT),I18n.t(Msg.SETTINGS_AUTO_CONNECT_SUMMARY),prefs.getBoolean("auto_connect",true),value->{prefs.edit().putBoolean("auto_connect",value).apply();if(value)scan();});
        input.row(I18n.t(Msg.SETTINGS_CONNECTION_CONNECT),I18n.t(Msg.SETTINGS_CONNECTION_CONNECT_SUMMARY),()->{prefs.edit().putBoolean("auto_connect",true).apply();connect();renderTab();});
        input.row(I18n.t(Msg.COMMON_DISCONNECT),I18n.t(Msg.SETTINGS_CONNECTION_DISCONNECT_SUMMARY),()->{prefs.edit().putBoolean("auto_connect",false).apply();disconnect(true);renderTab();});
        NativeSettings.Group ports=settingsGroup(I18n.t(Msg.SETTINGS_TOUCH_GROUP));
        ports.row(I18n.t(Msg.SETTINGS_TOUCH_PORT),portName(touchPort,"touch_identity",touchRole()),()->choosePort(false));
        ports.row(I18n.t(Msg.SETTINGS_TOUCH_PROTOCOL),commandMode?"Command · 115200":"Touch Serial · 9600",()->{
            if(!canChangeInput())return;
            choose(I18n.t(Msg.SETTINGS_TOUCH_PROTOCOL),new String[]{"Touch Serial · 9600","Command · 115200"},commandMode?1:0,n->{commandMode=n==1;touchPort="";prefs.edit().putBoolean("touch_command",commandMode).remove("touch_identity").apply();renderTab();scan();});
        });
        NativeSettings.Group cards=settingsGroup(I18n.t(Msg.SETTINGS_AIME_GROUP));
        // running() includes a session that is retrying; only a reader that answered is shown as healthy.
        cards.status(aimeDescription(),!aimeEnabled?NativeSettings.INFO:aimeReader.ready()?NativeSettings.OK:NativeSettings.WAIT);
        cards.toggle(I18n.t(Msg.SETTINGS_AIME_TOGGLE),I18n.t(Msg.SETTINGS_AIME_TOGGLE_SUMMARY),aimeEnabled,value->{
            aimeEnabled=value;prefs.edit().putBoolean("aime_enabled",value).apply();allowAime(false);
            if(value){if(aimePort.isEmpty())scan();else startAime();}else aimeReader.stop();
        });
        cards.row(I18n.t(Msg.SETTINGS_AIME_PORT),portName(aimePort,"aime_identity",PortSelection.NFC),this::chooseAimePort);
        String[] radioNames={I18n.t(Msg.SETTINGS_AIME_RADIO_MIFARE),"Amusement IC Aime · FeliCa",I18n.t(Msg.SETTINGS_AIME_RADIO_AUTO)};
        int radio=bounded(prefs.getInt("aime_radio_type",3),1,3);
        cards.row(I18n.t(Msg.SETTINGS_AIME_RADIO),radioNames[radio-1],()->choose(I18n.t(Msg.SETTINGS_AIME_RADIO),radioNames,radio-1,index->{
            int selected=index+1;prefs.edit().putInt("aime_radio_type",selected).apply();aimeReader.radioType(selected);
        }));
        cards.toggle(I18n.t(Msg.SETTINGS_AIME_LED),I18n.t(Msg.SETTINGS_AIME_LED_SUMMARY),prefs.getBoolean("aime_led_enabled",true),value->{
            prefs.edit().putBoolean("aime_led_enabled",value).apply();aimeReader.led(value);
        });
        cards.note(I18n.t(Msg.SETTINGS_AIME_PRIVACY));
        NativeSettings.Group phoneCard=settingsGroup(I18n.t(Msg.SETTINGS_PHONE_NFC_GROUP));
        phoneCard.toggle(I18n.t(Msg.SETTINGS_PHONE_NFC_TOGGLE),I18n.t(Msg.SETTINGS_PHONE_NFC_TOGGLE_SUMMARY),prefs.getBoolean("phone_nfc_enabled",true),value->{
            prefs.edit().putBoolean("phone_nfc_enabled",value).apply();if(!value)phoneNfc.pause();
        });
        phoneCard.status(phoneNfc.summary(),NativeSettings.INFO);
        phoneCard.note(I18n.t(Msg.SETTINGS_PHONE_NFC_NOTE));
        NativeSettings.Group devices=settingsGroup(I18n.t(Msg.SETTINGS_USB_GROUP));
        devices.row(I18n.t(Msg.SETTINGS_USB_SCAN),I18n.t(Msg.SETTINGS_USB_SCAN_SUMMARY),()->{inputAddAttempts=0;scan();});
        Set<String> seen=new HashSet<>();
        for(UsbIo.Port p:this.ports)if(seen.add(p.device.getDeviceName())){
            UsbDevice d=p.device;devices.row(d.getProductName()==null?d.getDeviceName():d.getProductName(),usb.hasPermission(d)?I18n.t(Msg.SETTINGS_USB_PERMITTED):I18n.t(Msg.SETTINGS_USB_REQUEST),()->requestPermission(d));
        }
        if(seen.isEmpty())devices.note(I18n.t(Msg.SETTINGS_USB_NONE));
        NativeSettings.Group more=settingsGroup(I18n.t(Msg.SETTINGS_DIAGNOSTICS_GROUP));
        more.row(I18n.t(Msg.SETTINGS_FIRMWARE),firmwareDescription(),this::queryFirmware);
        // After the panel's own dismissal has run, so setup opens at once (and not inside setup's own detailed settings).
        if(!forSetup)more.row(I18n.t(Msg.SETTINGS_SETUP),I18n.t(Msg.SETTINGS_SETUP_SUMMARY),()->{if(panel!=null)panel.dismiss();ui.post(this::showSetup);});
        more.row(I18n.t(Msg.SETTINGS_COPY_DIAGNOSTICS),nativeExplanation(),this::copyDiagnostics);
    }
    private void renderDisplayTab(){
        NativeSettings.Group output=settingsGroup(I18n.t(Msg.SETTINGS_DISPLAY_GROUP));
        output.status(displayOutput.summary(),displayTone());
        output.toggle(I18n.t(Msg.SETTINGS_DISPLAY_TOGGLE),I18n.t(Msg.SETTINGS_DISPLAY_TOGGLE_SUMMARY),prefs.getBoolean("external_enabled",true),value->{
            if(syncingExternalToggle)return;
            if(value)displayOutput.enable();else displayOutput.stop(I18n.t(Msg.DISPLAY_RETURNED_TO_PHONE));
        });
        output.row(I18n.t(Msg.SETTINGS_DISPLAY_ROTATION),displayOutput.isClockwise()?I18n.t(Msg.DISPLAY_ROTATION_CW):I18n.t(Msg.DISPLAY_ROTATION_CCW),()->
            choose(I18n.t(Msg.SETTINGS_DISPLAY_ROTATION),new String[]{I18n.t(Msg.DISPLAY_ROTATION_CW),I18n.t(Msg.DISPLAY_ROTATION_CCW)},displayOutput.isClockwise()?0:1,n->{displayOutput.setClockwise(n==0);renderTab();}));
        output.row(I18n.t(Msg.SETTINGS_DISPLAY_CHOOSE),I18n.t(Msg.SETTINGS_DISPLAY_CHOOSE_SUMMARY),displayOutput::choose);
        NativeSettings.Group phone=settingsGroup(I18n.t(Msg.SETTINGS_PHONE_GROUP));
        phone.toggle(I18n.t(Msg.SETTINGS_PHONE_PORTRAIT),I18n.t(Msg.SETTINGS_PHONE_PORTRAIT_SUMMARY),displayOutput.isPhonePortrait(),value->displayOutput.setPhonePortrait(value));
        phone.row(I18n.t(Msg.SETTINGS_PHONE_DASHBOARD),I18n.t(Msg.SETTINGS_PHONE_DASHBOARD_SUMMARY),()->{if(panel!=null)panel.dismiss();displayOutput.previewDashboard();});
        NativeSettings.Group help=settingsGroup(I18n.t(Msg.SETTINGS_DISPLAY_HELP));
        help.note(I18n.t(Msg.SETTINGS_DISPLAY_NOTE_ROTATION));
        help.note(I18n.t(Msg.SETTINGS_DISPLAY_NOTE_CONNECTION));
        help.row(I18n.t(Msg.SETTINGS_DISPLAY_COPY_DIAGNOSTICS),I18n.t(Msg.SETTINGS_DISPLAY_DIAGNOSTICS_SUMMARY),this::copyDiagnostics);
    }
    private void renderLabsTab(){
        NativeSettings.Group introduction=settingsGroup(I18n.t(Msg.LABS_TITLE));
        introduction.note(I18n.t(Msg.LABS_SUMMARY));
        NativeSettings.Group camera=settingsGroup(I18n.t(Msg.CAMERA_GROUP));
        camera.status(frontCamera.summary(),NativeSettings.INFO);
        camera.toggle(I18n.t(Msg.CAMERA_TOGGLE),I18n.t(Msg.CAMERA_SUMMARY),frontCamera.enabled(),value->{frontCamera.enable(value);renderTab();});
        String[] lenses={I18n.t(Msg.CAMERA_LENS_FRONT),I18n.t(Msg.CAMERA_LENS_REAR)};
        camera.row(I18n.t(Msg.CAMERA_LENS),lenses[frontCamera.rear()?1:0],()->
            choose(I18n.t(Msg.CAMERA_LENS),lenses,frontCamera.rear()?1:0,n->{frontCamera.rear(n==1);renderTab();}));
        camera.toggle(I18n.t(Msg.CAMERA_MIRROR),I18n.t(Msg.CAMERA_MIRROR_SUMMARY),frontCamera.mirror(),value->{frontCamera.mirror(value);renderTab();});
        camera.note(I18n.t(Msg.CAMERA_LENS_RESTART));
        if(frontCamera.enabled()&&!frontCamera.permitted())camera.row(I18n.t(Msg.CAMERA_PERMISSION),I18n.t(Msg.CAMERA_PERMISSION_NEEDED),frontCamera::request);
        camera.note(I18n.t(Msg.CAMERA_NOTE));
    }
    private boolean canChangeInput(){if(connected||busy){tell(I18n.t(Msg.SETTINGS_DISCONNECT_FIRST));return false;}return true;}
    private void renderButtonTab(){
        NativeSettings.Group input=settingsGroup(I18n.t(Msg.SETTINGS_BUTTONS_GROUP));
        String[] modes={I18n.t(Msg.COMMON_NOT_USED),"USB Keyboard","IO4 HID"};
        input.row(I18n.t(Msg.SETTINGS_BUTTONS_MODE),modes[buttonMode],()->{
            if(!canChangeInput())return;
            choose(I18n.t(Msg.SETTINGS_BUTTONS_MODE_TITLE),modes,buttonMode,n->{buttonMode=n;prefs.edit().putInt("button_mode",n).apply();keys.clear();synchronized(dataLock){keyButtons=hidButtons=0;}suspendInput();renderTab();});
        });
        input.row(I18n.t(Msg.SETTINGS_BUTTONS_PLAYER),player==0?"1P":"2P",()->{
            if(!canChangeInput())return;
            choose(I18n.t(Msg.SETTINGS_BUTTONS_PLAYER),new String[]{"1P","2P"},player,n->{player=n;prefs.edit().putInt("player",n).apply();push();renderTab();});
        });
        if(buttonMode==2){
            input.row("IO4 HID",portName(hidPort,"hid_identity",PortSelection.IO4),()->choosePort(true));
            input.note(I18n.t(Msg.SETTINGS_BUTTONS_IO4_NOTE));
        }else if(buttonMode==1){
            NativeSettings.Group mapping=settingsGroup(I18n.t(Msg.SETTINGS_BUTTONS_MAPPING));
            mapping.note(I18n.t(Msg.SETTINGS_BUTTONS_MAPPING_NOTE));
            for(int n=0;n<9;n++){
                final int index=n;
                mapping.row(n==8?"P1 / START":I18n.t(Msg.SETTINGS_BUTTONS_NUMBER,n+1),learn==n?I18n.t(Msg.SETTINGS_BUTTONS_PRESS):KeyEvent.keyCodeToString(keys.map[n]),()->{learn=index;updateLearning();});
            }
            mapping.row(I18n.t(Msg.SETTINGS_BUTTONS_CANCEL_LEARNING),"",()->{learn=-1;updateLearning();});
        }
        NativeSettings.Group guide=settingsGroup(I18n.t(Msg.SETTINGS_BUTTONS_GUIDE));
        guide.note(I18n.t(Msg.SETTINGS_BUTTONS_PAUSE_NOTE));
        guide.note(I18n.t(Msg.SETTINGS_BUTTONS_SENSITIVITY_NOTE));
    }
    private static int bounded(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private void applyLedSettings(){ledOutput.settings(ledBrightness,ledRotation,ledReverse,ledRing);ceilingOutput.settings(ledEnabled&&ledCeiling,ledBrightness);}
    private void saveLedSettings(){
        prefs.edit().putBoolean("led_enabled",ledEnabled).putInt("led_address",ledAddress).putInt("led_base",ledBase)
            .putInt("led_brightness",ledBrightness).putInt("led_rotation",ledRotation).putBoolean("led_reverse",ledReverse).putBoolean("led_ring",ledRing).putBoolean("led_ceiling",ledCeiling).apply();applyLedSettings();
    }
    private void renderLedTab(){
        NativeSettings.Group link=settingsGroup(I18n.t(Msg.SETTINGS_LED_GROUP));
        link.status(ledDescription(),ledTone());
        link.toggle(I18n.t(Msg.SETTINGS_LED_TOGGLE),I18n.t(Msg.SETTINGS_LED_TOGGLE_SUMMARY),ledEnabled,value->{
            ledEnabled=value;saveLedSettings();if(value){startLeds();if(ledCeiling&&hid==null){if(connected)disconnect(false);ui.postDelayed(this::scan,500);}}else ledOutput.stop();
        });
        link.row(I18n.t(Msg.SETTINGS_LED_PORT),portName(ledPort,"led_identity",PortSelection.LED),this::chooseLedPort);
        link.row(I18n.t(Msg.SETTINGS_LED_RECONNECT),I18n.t(Msg.SETTINGS_LED_RECONNECT_SUMMARY),()->{if(ledOutput.running())ledOutput.stop();startLeds();renderTab();});
        NativeSettings.Group ceiling=settingsGroup(I18n.t(Msg.SETTINGS_CEILING_GROUP));
        ceiling.toggle(I18n.t(Msg.SETTINGS_CEILING_TOGGLE),I18n.t(Msg.SETTINGS_CEILING_TOGGLE_SUMMARY),ledCeiling,value->{ledCeiling=value;saveLedSettings();if(value&&hid==null){if(connected)disconnect(false);ui.postDelayed(this::scan,500);}});

        ceiling.row(I18n.t(Msg.SETTINGS_CEILING_TEST),I18n.t(Msg.SETTINGS_COLOR_TEST_SUMMARY),()->
            choose(I18n.t(Msg.SETTINGS_CEILING_TEST),new String[]{I18n.t(Msg.COLOR_RED),I18n.t(Msg.COLOR_GREEN),I18n.t(Msg.COLOR_BLUE)},-1,n->{if(hid==null||!ledEnabled||!ledCeiling){tell(I18n.t(Msg.SETTINGS_CEILING_TEST_REQUIREMENTS));return;}ceilingOutput.test(new int[]{0xff0000,0x00ff00,0x0000ff}[n]);}));
        ceiling.row(I18n.t(Msg.SETTINGS_CEILING_RECONNECT),I18n.t(Msg.SETTINGS_CEILING_RECONNECT_SUMMARY),()->{disconnect(false);ui.postDelayed(this::scan,600);});
        NativeSettings.Group level=settingsGroup(I18n.t(Msg.SETTINGS_LED_LAYOUT_GROUP));
        level.row(I18n.t(Msg.SETTINGS_LED_BRIGHTNESS),ledBrightness+"%",this::editLedBrightness);
        level.row(I18n.t(Msg.SETTINGS_LED_ROTATION),I18n.t(Msg.SETTINGS_LED_ROTATION_STEPS,ledRotation),()->{
            String[] steps=new String[8];for(int i=0;i<steps.length;i++)steps[i]=I18n.t(Msg.SETTINGS_LED_ROTATION_STEPS,i);
            choose(I18n.t(Msg.SETTINGS_LED_ROTATION),steps,ledRotation,n->{ledRotation=n;saveLedSettings();renderTab();});
        });
        level.toggle(I18n.t(Msg.SETTINGS_LED_REVERSE),I18n.t(Msg.SETTINGS_LED_REVERSE_SUMMARY),ledReverse,value->{ledReverse=value;saveLedSettings();});
        level.row(I18n.t(Msg.SETTINGS_LED_TEST),I18n.t(Msg.SETTINGS_COLOR_TEST_SUMMARY),()->
            choose(I18n.t(Msg.SETTINGS_LED_TEST),new String[]{I18n.t(Msg.COLOR_RED),I18n.t(Msg.COLOR_GREEN),I18n.t(Msg.COLOR_BLUE)},-1,n->{if(!ledOutput.running()){tell(I18n.t(Msg.SETTINGS_LED_START_FIRST));return;}ledOutput.test(new int[]{0xff0000,0x00ff00,0x0000ff}[n]);}));
        NativeSettings.Group board=settingsGroup(I18n.t(Msg.SETTINGS_LED_BOARD_GROUP));
        board.row(I18n.t(Msg.SETTINGS_LED_BASE),String.valueOf(ledBase),()->editLedNumber(I18n.t(Msg.SETTINGS_LED_BASE_TITLE),ledBase,0,24,value->{ledBase=value;saveLedSettings();renderTab();},true));
        board.row(I18n.t(Msg.SETTINGS_LED_ADDRESS),String.valueOf(ledAddress),()->editLedNumber(I18n.t(Msg.SETTINGS_LED_ADDRESS_TITLE),ledAddress,1,255,value->{ledAddress=value;saveLedSettings();renderTab();},true));
        board.toggle(I18n.t(Msg.SETTINGS_LED_RING),I18n.t(Msg.SETTINGS_LED_RING_SUMMARY),ledRing,value->{ledRing=value;saveLedSettings();});
        board.note(I18n.t(Msg.SETTINGS_LED_DEFAULTS_NOTE));
    }
    private void editLedBrightness(){
        NativeUi.brightness(activity,ledBrightness,this::protect,value->{ledBrightness=value;saveLedSettings();});
    }
    private void editLedNumber(String title,int current,int min,int max,java.util.function.IntConsumer changed,boolean requireStopped){
        if(requireStopped&&ledOutput.running()){tell(I18n.t(Msg.SETTINGS_LED_STOP_BEFORE_EDIT));return;}
        NativeUi.number(activity,title,current,min,max,this::protect,changed);
    }
    private void chooseLedPort(){
        if(ledOutput.running()){tell(I18n.t(Msg.SETTINGS_LED_STOP_BEFORE_PORT));return;}
        List<UsbIo.Port> options=new ArrayList<>();for(UsbIo.Port p:ports)if(!p.hid&&!p.key().equals(touchPort)&&!p.key().equals(aimePort)&&PortSelection.role(p.name,p.hid)!=PortSelection.NFC)options.add(p);
        choose(I18n.t(Msg.SETTINGS_LED_PORT_TITLE),portChoices(options,false),currentChoice(options,ledPort,"led_identity"),n->{
            if(n==1){prefs.edit().remove("led_identity").apply();ledPort=resolvePort("led_identity",PortSelection.LED);refreshPortLabels();return;}
            ledPort=n==0?"":options.get(n-2).key();savePort("led_identity",n==0?null:options.get(n-2));refreshPortLabels();});
    }
    private void startLeds(){
        if(destroyed)return;
        if(ledPort.isEmpty()||ledPort.equals(touchPort)||ledPort.equals(aimePort)){tell(I18n.t(Msg.LED_PORT_CONFLICT));return;}
        for(UsbIo.Port p:ports)if(p.key().equals(ledPort)){
            if(!usb.hasPermission(p.device)){tell(I18n.t(Msg.LED_PERMISSION_FIRST));return;}
            ledEnabled=true;saveLedSettings();ledOutput.start(p,ledAddress,ledBase);return;
        }
        tell(I18n.t(Msg.LED_NO_PORT));
    }
    private String aimeDescription(){
        if(aimeEnabled&&aimePort.isEmpty())return I18n.t(Msg.AIME_STATUS_WAITING_PORT);
        return aimeReader.summary();
    }
    private void chooseAimePort(){
        if(aimeReader.running()){tell(I18n.t(Msg.SETTINGS_AIME_STOP_BEFORE_PORT));return;}
        List<UsbIo.Port> options=new ArrayList<>();
        for(UsbIo.Port p:ports)if(!p.hid&&!p.key().equals(touchPort)&&!p.key().equals(ledPort)&&PortSelection.role(p.name,p.hid)!=PortSelection.COMMAND&&PortSelection.role(p.name,p.hid)!=PortSelection.TOUCH&&PortSelection.role(p.name,p.hid)!=PortSelection.LED)options.add(p);
        choose(I18n.t(Msg.SETTINGS_AIME_PORT_TITLE),portChoices(options,false),currentChoice(options,aimePort,"aime_identity"),n->{
            if(n==1){prefs.edit().remove("aime_identity").apply();aimePort=resolvePort("aime_identity",PortSelection.NFC);refreshPortLabels();return;}
            aimePort=n==0?"":options.get(n-2).key();savePort("aime_identity",n==0?null:options.get(n-2));refreshPortLabels();
        });
    }
    private void startAime(){
        if(destroyed||!aimeEnabled||aimeReader.running()||aimePort.isEmpty())return;
        if(aimePort.equals(touchPort)||aimePort.equals(ledPort)){tell(I18n.t(Msg.SETTINGS_AIME_PORT_CONFLICT));return;}
        for(UsbIo.Port p:ports)if(p.key().equals(aimePort)){
            if(!usb.hasPermission(p.device)){requestPermission(p.device);return;}
            aimeReader.start(p);return;
        }
    }
    private void copyDiagnostics(){
        String report="Oniimai Kanade "+BuildConfig.VERSION_NAME+" / API 102\nAndroid "+Build.VERSION.RELEASE+" / "+Build.MANUFACTURER+" "+Build.MODEL+
            "\nController firmware INFO: "+(firmwareInfo==null?"not queried":firmwareInfo.summary())+(firmwareQueryError.isEmpty()?"":" / query failed: "+firmwareQueryError)+
            "\n"+nativeExplanation()+"\nFrames: "+lastStatsFrame+" / Touch reads: "+lastStatsTouch+"\nUSB: "+connected+" / Packets: "+packets+" / HID: "+hidPackets+
            "\n"+(commandMode?"Command: ":"Touch: ")+portName(touchPort,"touch_identity",touchRole())+" / open="+!linkedTouch.isEmpty()+"\nHID: "+portName(hidPort,"hid_identity",PortSelection.IO4)+" / open="+!linkedHid.isEmpty()+"\nKeys: "+Arrays.toString(keys.map)+"\nLED: "+portName(ledPort,"led_identity",PortSelection.LED)+" / node="+ledAddress+" / base="+ledBase+" / rotation="+ledRotation+" / reverse="+ledReverse+" / brightness="+ledBrightness+" / ring="+ledRing+"\n"+ledOutput.diagnostic()+"\nCeiling: "+ceilingOutput.diagnostic()+"\nNFC: "+portName(aimePort,"aime_identity",PortSelection.NFC)+" / "+aimeReader.diagnostic()+"\n"+displayOutput.diagnostic()+"\nStats: "+gameFrame+"\n"+message+
            "\nLast slow NFC request:\n"+prefs.getString("usb_last_nfc_stall","none")+"\nLast USB device detach:\n"+prefs.getString("usb_last_detach","none")+"\nLast input transport failure:\n"+prefs.getString("usb_last_input_failure","none");
        ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Oniimai diagnosis",report));tell(I18n.t(Msg.DIAGNOSTICS_COPIED));
    }
    /** Failure-only local snapshots survive logcat rotation and a game restart.
     * No card payload/number, device serial, or arbitrary exception text is saved.
     */
    private void saveUsbFailure(String key,String event){
        long now=SystemClock.uptimeMillis(),touchAge,hidAge;
        synchronized(dataLock){touchAge=touchAt==0?-1:Math.max(0,now-touchAt);hidAge=hidAt==0?-1:Math.max(0,now-hidAt);}
        String report="module="+BuildConfig.VERSION_NAME+" event="+event+" utcMillis="+System.currentTimeMillis()+" uptimeMs="+now+
            " inputGeneration="+generation.get()+" connected="+connected+" foreground="+foreground+"\nTouch packets="+packets+" ageMs="+touchAge+
            " / HID packets="+hidPackets+" ageMs="+hidAge+"\n"+ledOutput.failureDiagnostic()+"\n"+ceilingOutput.diagnostic()+"\n"+aimeReader.failureDiagnostic();
        // Only three fixed keys are used. Do not perform synchronous disk I/O or USB queries here.
        report=report.substring(0,Math.min(report.length(),8192));
        prefs.edit().putString(key,report).apply();
        android.util.Log.w("OniimaiUsb",report);
    }
    private String firmwareDescription(){
        if(firmwareBusy)return I18n.t(Msg.FIRMWARE_READING);
        if(!firmwareQueryError.isEmpty())return I18n.t(Msg.FIRMWARE_FAILED_RETRY);
        return firmwareInfo==null?I18n.t(Msg.FIRMWARE_SUMMARY):firmwareInfo.summary();
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
        catch(IllegalArgumentException error){throw new IOException(I18n.t(Msg.FIRMWARE_NOT_COMMAND),error);}
    }
    private void queryFirmware(){
        if(destroyed||firmwareBusy)return;
        if(busy){tell(I18n.t(Msg.FIRMWARE_BUSY));return;}
        final UsbIo.Port selected=firmwareCommandPort();
        if(selected==null){tell(I18n.t(Msg.FIRMWARE_NO_COMMAND_PORT));return;}
        if(!usb.hasPermission(selected.device)){requestPermission(selected.device);return;}
        final int gen=generation.get();
        final boolean inputPort=selected.key().equals(touchPort);
        final boolean otherOwner=(selected.key().equals(aimePort)&&aimeReader.running())||(selected.key().equals(ledPort)&&ledOutput.running());
        firmwareBusy=true;firmwareQueryError="";refreshFirmwareInfo();
        worker.execute(()->{
            FirmwareInfo result=null;String failure=null;CommandChannel temporary=null,shared=null;
            try{
                if(destroyed||gen!=generation.get()||!firmwareDevicePresent(selected))return;
                if(otherOwner)throw new IOException(I18n.t(Msg.FIRMWARE_PORT_IN_USE));
                CommandChannel active=inputPort?command:null;
                if(active!=null&&!active.isClosed())shared=active;
                if(active==null||active.isClosed()){
                    if(inputPort&&(serial!=null||command!=null))throw new IOException(I18n.t(Msg.FIRMWARE_INPUT_PORT_BUSY));
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
                if(shared!=null&&shared.isClosed())usbError(gen,"firmware_query",I18n.t(Msg.FIRMWARE_INPUT_LOST));
                final FirmwareInfo info=result;final String error=failure;
                ui.post(()->{
                    firmwareBusy=false;
                    if(destroyed)return;
                    if(gen!=generation.get()||!firmwareDevicePresent(selected)){refreshFirmwareInfo();return;}
                    if(info!=null){
                        firmwareInfo=info;firmwareQueryError="";
                        android.util.Log.i("OniimaiKanade","Controller firmware INFO: "+info.summary());
                        tell(I18n.t(Msg.FIRMWARE_RESULT,info.summary()));
                    }else if(error!=null){firmwareQueryError=error;tell(I18n.t(Msg.FIRMWARE_FAILED,error));}
                    refreshFirmwareInfo();
                });
            }
        });
    }
    private void updateLearning(){for(int i=0;i<learnButtons.length;i++)if(learnButtons[i]!=null)learnButtons[i].setText((learn==i?I18n.t(Msg.SETTINGS_BUTTONS_LEARNING):i==8?"P1 / START":""+(i+1))+"\n"+(keys.map[i]<0?I18n.t(Msg.COMMON_NONE):KeyEvent.keyCodeToString(keys.map[i]).replace("KEYCODE_","")));}
    private int touchRole(){return commandMode?PortSelection.COMMAND:PortSelection.TOUCH;}
    /** Distinguishes "not used" from a saved device that is absent, ambiguous or still awaiting permission. */
    private String portName(String key,String preference,int role){
        for(UsbIo.Port p:ports)if(p.key().equals(key)){String name=p.name+" · IF"+p.controlId;return usb.hasPermission(p.device)?name:I18n.t(Msg.PORT_PERMISSION_PENDING,name);}
        boolean saved=prefs.contains(preference);int index=resolveIndex(preference,role);
        if(index>=0){UsbIo.Port p=ports.get(index);return p.name+" · IF"+p.controlId;}
        if(index==PortSelection.DISABLED)return I18n.t(Msg.COMMON_NOT_USED);
        if(index==PortSelection.DUPLICATE)return saved?I18n.t(Msg.PORT_DUPLICATE_SAVED):I18n.t(Msg.PORT_DUPLICATE_NAMED);
        return saved?I18n.t(Msg.PORT_WAITING_SAVED):I18n.t(Msg.PORT_AUTO_SEARCHING);
    }
    private void refreshPortLabels(){if(touchChoice!=null)touchChoice.setText(portName(touchPort,"touch_identity",touchRole()));if(hidChoice!=null)hidChoice.setText(portName(hidPort,"hid_identity",PortSelection.IO4));if(ledChoice!=null)ledChoice.setText(portName(ledPort,"led_identity",PortSelection.LED));if(aimeChoice!=null)aimeChoice.setText(portName(aimePort,"aime_identity",PortSelection.NFC));}
    /** Chooser rows: 0 = not used, 1 = automatic by name, then the listed ports. Marks the current choice. */
    private String[] portChoices(List<UsbIo.Port> options,boolean withAddress){
        String[] labels=new String[options.size()+2];labels[0]=I18n.t(Msg.COMMON_NOT_USED);
        labels[1]=I18n.t(Msg.PORT_AUTO_OPTION);
        for(int i=0;i<options.size();i++){UsbIo.Port p=options.get(i);labels[i+2]=p.name+" · IF"+p.controlId+(withAddress?" · "+p.device.getDeviceName():"");}
        return labels;
    }
    private int currentChoice(List<UsbIo.Port> options,String key,String preference){
        if(!prefs.contains(preference))return 1;
        if(prefs.getString(preference,"").isEmpty())return 0;
        for(int i=0;i<options.size();i++)if(options.get(i).key().equals(key))return i+2;
        return -1;
    }
    private void choosePort(boolean isHid){
        if(connected||busy){tell(I18n.t(Msg.SETTINGS_DISCONNECT_FIRST));return;}
        List<UsbIo.Port> options=new ArrayList<>();for(UsbIo.Port p:ports)if(p.hid==isHid&&(!ledOutput.running()||!p.key().equals(ledPort))&&!p.key().equals(aimePort)&&PortSelection.role(p.name,p.hid)!=PortSelection.NFC)options.add(p);
        String preference=isHid?"hid_identity":"touch_identity";
        choose(isHid?"IO4 HID":I18n.t(Msg.SETTINGS_TOUCH_PORT_TITLE),portChoices(options,true),currentChoice(options,isHid?hidPort:touchPort,preference),n->{
            if(n==1){prefs.edit().remove(preference).apply();if(isHid)hidPort=resolvePort(preference,PortSelection.IO4);else touchPort=resolvePort(preference,touchRole());refreshPortLabels();renderTab();return;}
            String key=n==0?"":options.get(n-2).key();UsbIo.Port chosen=n==0?null:options.get(n-2);if(isHid)hidPort=key;else{touchPort=key;if(chosen!=null){commandMode=PortSelection.protocol(chosen.name,chosen.hid,commandMode?0:1)==0;prefs.edit().putBoolean("touch_command",commandMode).apply();}}
            savePort(preference,chosen);renderTab();});
    }
    private void savePort(String name,UsbIo.Port port){prefs.edit().putString(name,port==null?"":port.stableId()).apply();}
    private int resolveIndex(String preference,int role){
        String[] ids=new String[ports.size()],names=new String[ports.size()];boolean[] hids=new boolean[ports.size()];
        for(int i=0;i<ports.size();i++){UsbIo.Port p=ports.get(i);ids[i]=p.stableId();names[i]=p.name;hids[i]=p.hid;}
        return PortSelection.resolve(prefs.contains(preference),prefs.getString(preference,""),ids,names,hids,role);
    }
    private String resolvePort(String preference,int role){
        int index=resolveIndex(preference,role);
        if(index<0)return "";
        UsbIo.Port chosen=ports.get(index);if(usb.hasPermission(chosen.device))savePort(preference,chosen);
        return chosen.key();
    }
    private boolean wantsHid(){return (buttonMode==2||ledEnabled&&ledCeiling)&&!disabledPort("hid_identity");}
    private boolean disabledPort(String preference){return prefs.contains(preference)&&prefs.getString(preference,"").isEmpty();}
    /** A connection missing an input whose device was saved earlier keeps looking for it, at a gentle pace. */
    private boolean waitingForSavedInput(){
        return connected&&inputAddAttempts<MAX_INPUT_ADD_ATTEMPTS&&((linkedTouch.isEmpty()&&!prefs.getString("touch_identity","").isEmpty())||(wantsHid()&&linkedHid.isEmpty()&&!prefs.getString("hid_identity","").isEmpty()));
    }
    /**
     * Enumerates ports and refreshes permissions even while inputs are open. Open input handles are
     * never replaced here; a role the current connection lacks triggers one clean reconnect.
     */
    private void scan(){
        if(busy||destroyed||(initializing||setupActive)&&!setupSearching)return;
        busy=true;worker.execute(()->{
            List<UsbIo.Port> found=null;String failure=null;
            try{found=UsbIo.ports(usb);}catch(Exception e){failure=e.getMessage();}
            final List<UsbIo.Port> result=found;final String error=failure;
            ui.post(()->{busy=false;if(destroyed||(initializing||setupActive)&&!setupSearching)return;if(result==null){message=I18n.t(Msg.USB_SCAN_FAILED,error);return;}
                ports=result;
                String touch=resolvePort("touch_identity",touchRole()),buttons=resolvePort("hid_identity",PortSelection.IO4);
                boolean live=connected,addInput=false;
                if(live){
                    addInput=(linkedTouch.isEmpty()&&!touch.isEmpty())||(wantsHid()&&linkedHid.isEmpty()&&!buttons.isEmpty());
                    if(!linkedTouch.isEmpty())touch=linkedTouch;
                    if(!linkedHid.isEmpty())buttons=linkedHid;
                }
                touchPort=touch;hidPort=buttons;
                ledPort=resolvePort("led_identity",PortSelection.LED);
                aimePort=resolvePort("aime_identity",PortSelection.NFC);
                renderDevices();refreshPortLabels();
                if(setupSearching||!setupActive&&prefs.getBoolean("auto_connect",true)){
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
                    if(allowed){
                        if(!live){if(!touchPort.isEmpty()||((buttonMode==2||ledEnabled&&ledCeiling)&&!hidPort.isEmpty()))connect();}
                        else if(addInput&&connected&&inputAddAttempts<MAX_INPUT_ADD_ATTEMPTS){inputAddAttempts++;
                            android.util.Log.i("OniimaiKanade","Missing input port found; reconnecting with all inputs");
                            disconnect(false);message=I18n.t(Msg.INPUT_RECONNECTING_MISSING);
                            ui.postDelayed(this::scan,600);
                        }
                        if(ledEnabled&&!ledPort.isEmpty()&&!ledOutput.running())startLeds();if(aimeEnabled)startAime();
                    }
                }
            });
        });
    }
    /** Detach broadcasts can be missed; an input device absent from the USB list is released the same way. */
    private boolean inputDevicePresent(){
        Map<String,UsbDevice> devices=usb.getDeviceList();
        for(String key:new String[]{linkedTouch,linkedHid}){
            int mark=key.lastIndexOf('#');if(mark>0&&!devices.containsKey(key.substring(0,mark)))return false;
        }
        return true;
    }
    private void releaseDetachedInput(){
        disconnect(false);keys.clear();synchronized(dataLock){keyButtons=0;}push();message=I18n.t(Msg.USB_DETACHED);
    }
    private void renderDevices(){
        if(deviceList==null)return;deviceList.removeAllViews();Set<String> seen=new HashSet<>();
        for(UsbIo.Port p:this.ports)if(seen.add(p.device.getDeviceName())){
            UsbDevice d=p.device;settingRow(deviceList,d.getProductName()==null?d.getDeviceName():d.getProductName(),usb.hasPermission(d)?I18n.t(Msg.SETTINGS_USB_PERMITTED):I18n.t(Msg.SETTINGS_USB_REQUEST),()->requestPermission(d));
        }
        if(seen.isEmpty())text(deviceList,I18n.t(Msg.SETTINGS_USB_NONE));
    }
    private void requestPermission(UsbDevice device){
        if(usb.hasPermission(device)){tell(I18n.t(Msg.USB_ALREADY_PERMITTED));return;}
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
        PendingIntent pending=PendingIntent.getBroadcast(activity,device.getDeviceId(),new Intent(permissionAction).setPackage(activity.getPackageName()),flags);
        usb.requestPermission(device,pending);
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(permissionAction.equals(intent.getAction())){permissionPending=null;inputAddAttempts=0;message=I18n.t(Msg.USB_PERMISSION_RESULT);
            if(!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))permissionRefused=true;scan();}
        else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())){permissionAsked.clear();permissionRefused=false;openFailed=false;inputAddAttempts=0;scan();}
        else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())){
            UsbDevice removed=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(removed==null)return;
            if(portOnDevice(touchPort,removed)||portOnDevice(hidPort,removed)||portOnDevice(ledPort,removed)||portOnDevice(aimePort,removed))saveUsbFailure("usb_last_detach","android_device_detached");
            if(portOnDevice(aimePort,removed))aimeReader.detached();
            boolean inputs=portOnDevice(touchPort,removed)||portOnDevice(hidPort,removed);
            if(!inputs)return;
            permissionAsked.remove(removed.getDeviceName());if(removed.getDeviceName().equals(permissionPending))permissionPending=null;
            android.util.Log.w("OniimaiKanade","Controller USB detached; releasing this input generation");
            releaseDetachedInput();
        }
    }};
    private boolean portOnDevice(String key,UsbDevice device){return !key.isEmpty()&&key.startsWith(device.getDeviceName()+"#");}
    private void connect(){
        if(busy||connected||destroyed)return;
        UsbIo.Port tp=null,hp=null;for(UsbIo.Port p:ports){if(p.key().equals(touchPort))tp=p;if((buttonMode==2||ledEnabled&&ledCeiling)&&p.key().equals(hidPort))hp=p;}
        if(tp==null&&hp==null){tell(I18n.t(Msg.INPUT_CHOOSE_PORT));return;}
        if(tp!=null&&ledOutput.running()&&tp.key().equals(ledPort)){tell(I18n.t(Msg.INPUT_LED_CONFLICT));return;}
        if(tp!=null&&aimeReader.running()&&tp.key().equals(aimePort)){tell(I18n.t(Msg.INPUT_READER_CONFLICT));return;}
        final UsbIo.Port t=tp,h=hp;final boolean useCommand=commandMode;final int bank=player,gen=generation.incrementAndGet();
        busy=true;packets=hidPackets=0;touchIssue="";worker.execute(()->{
            try{
                boolean touchOpen=false;
                if(t!=null)try{
                    if(useCommand){
                        command=new CommandChannel(new UsbIo.Cdc(usb,t,115200),(data,n)->{if(gen!=generation.get())return;try{touch(new Protocol.TouchDebug(data).pressed);}catch(Exception e){postMessage(I18n.t(Msg.INPUT_TOUCH_FRAME_ERROR,e.getMessage()));}},this::postMessage,error->usbError(gen,"command",error));
                        FirmwareInfo info=parseFirmwareInfo(command.request(Protocol.INFO));
                        ui.post(()->{if(!destroyed&&gen==generation.get()){firmwareInfo=info;firmwareQueryError="";refreshFirmwareInfo();android.util.Log.i("OniimaiKanade","Controller firmware INFO: "+info.summary());}});
                        new Protocol.Config(command.request(Protocol.CONFIG_GET));command.request(Protocol.DEBUG_START);
                    }else{
                        serial=new UsbIo.Cdc(usb,t,9600);Protocol.TouchParser parser=new Protocol.TouchParser(pressed->{if(gen==generation.get())touch(pressed);});
                        serial.start(parser::feed,error->usbError(gen,"touch",error));serial.write("{HALT}{RSET}{STAT}".getBytes(StandardCharsets.US_ASCII));
                    }
                    touchOpen=true;
                }catch(Exception touchError){
                    if(h==null)throw touchError;
                    // Buttons stay usable; the partial-connection search retries touch a bounded number of times.
                    if(command!=null){command.close();command=null;}if(serial!=null){serial.close();serial=null;}
                    touchIssue=I18n.t(Msg.INPUT_TOUCH_OPEN_FAILED,touchError.getMessage());
                    android.util.Log.w("OniimaiKanade","Touch port failed to open; continuing with IO4 buttons only",touchError);
                }
                if(h!=null)hid=new UsbIo.Hid(usb,h,(data,n)->{if(gen==generation.get()&&buttonMode==2)try{buttons(Io4Input.mask(data,bank));}catch(Exception e){postMessage(I18n.t(Msg.INPUT_IO4_FORMAT,n));}},error->usbError(gen,"hid",error));
                if(gen!=generation.get())throw new IOException(I18n.t(Msg.INPUT_CANCELLED));
                linkedTouch=touchOpen?t.key():"";linkedHid=h==null?"":h.key();
                connected=true;openFailed=false;postMessage(I18n.t(Msg.INPUT_CONNECTED));
                ui.post(()->{if(!destroyed&&gen==generation.get()){if(inputTone()==NativeSettings.OK)inputAddAttempts=0;if(ledEnabled&&!ledOutput.running())startLeds();if(aimeEnabled)startAime();}});
            }catch(Exception e){closePorts(false);openFailed=true;postMessage(I18n.t(Msg.INPUT_CONNECT_FAILED,e.getMessage()));}
            finally{busy=false;}
        });
    }
    private void usbError(int sourceGeneration,String source,String error){ui.post(()->{if(!destroyed&&sourceGeneration==generation.get()){
        saveUsbFailure("usb_last_input_failure",source);
        if(!"hid".equals(source)&&connected&&!linkedHid.isEmpty()&&!linkedTouch.isEmpty()){
            // Keep IO4 buttons playing; only the touch stream is dropped, and the partial search brings it back.
            android.util.Log.w("OniimaiKanade","Touch transport failed; keeping IO4 buttons and dropping touch only");
            linkedTouch="";touchIssue=I18n.t(Msg.USB_ERROR,error);message=touchIssue;synchronized(dataLock){touches=0;}push();
            worker.execute(()->{if(command!=null){command.close();command=null;}if(serial!=null){serial.close();serial=null;}});
            return;
        }
        android.util.Log.w("OniimaiKanade","Current controller input transport failed; reconnecting");
        disconnect(false);message=I18n.t(Msg.USB_ERROR,error);
    }});}
    private void disconnect(boolean stopLed){
        connected=false;
        if(stopLed){ledOutput.stop();aimeReader.stop();}
        suspendInput(stopLed);generation.incrementAndGet();synchronized(dataLock){touches=0;hidButtons=0;}push();
        if(!destroyed){busy=true;worker.execute(()->{try{closePorts(stopLed);postMessage(I18n.t(Msg.USB_DISCONNECTED));}finally{busy=false;}});}
    }
    private void closePorts(boolean graceful){
        if(graceful){try{if(command!=null&&!command.isClosed())command.request(Protocol.DEBUG_STOP);}catch(Exception ignored){}
            try{if(hid!=null)hid.writeCeiling(0);}catch(Exception ignored){}
            try{if(serial!=null)serial.write("{HALT}".getBytes(StandardCharsets.US_ASCII));}catch(Exception ignored){}}
        if(command!=null){command.close();command=null;}if(serial!=null){serial.close();serial=null;}if(hid!=null){hid.close();hid=null;}connected=false;linkedTouch=linkedHid="";
    }
    /** After the game's own onDestroy has returned: release what had to outlive Unity's shutdown. */
    void afterActivityDestroyed(){if(displayOutput!=null)displayOutput.releaseAfterUnity();}
    void destroy(){
        if(destroyed)return;clearSetupNotice();destroyed=true;armed=false;allowAime(false);generation.incrementAndGet();push();ui.removeCallbacks(tick);
        frontCamera.foreground(false);
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
