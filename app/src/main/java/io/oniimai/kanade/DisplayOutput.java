package io.oniimai.kanade;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Routes Unity's main render surface to a user-selected Presentation display.
 *
 * Start order: choose display, lock portrait, wait in {@link OutputGate} until the game renders
 * steadily and the Activity has settled in portrait, then open the Presentation and hand Unity its
 * surface. Unity's own return values do not confirm either the switch or the detach, so both are
 * checked against later game frames, and a surface whose detach is unconfirmed is kept alive.
 */
final class DisplayOutput implements DisplayManager.DisplayListener {
    private final Activity activity;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final DisplayManager displays;
    private final Runnable disarm;
    private final Consumer<String> message;
    private final Consumer<AlertDialog> protect;
    private final SharedPreferences prefs;
    private final DashboardHost host;
    private DashboardView dashboard;
    private boolean foreground=true;
    private AlertDialog preview;
    private Object unity;
    private Method displayChanged,aspectChanged,surfaceChanged;
    /** Unity's own way to take away the still it shows over its view until it has drawn again; optional. */
    private Method hidePreserved;
    private Field glView;
    /** When the game last returned to the phone, and the frame count then; see returnLoop. */
    private long returnAt,returnFrames=-1;
    private External presentation;
    private final FrameLayout phoneCover;
    private boolean overrideRequested,phonePortrait,locked,destroyed;
    private boolean clockwise;
    private int previousOrientation;
    private boolean previousKeepScreenOn;
    private String detail=I18n.t(Msg.DISPLAY_STATUS_PHONE);
    /*
     * Recovery after an output that failed or lost its surface. A user OFF, a removed display and
     * the app going to the background never retry. Delays grow 1-2-4-8-8 s; a session that stayed
     * bound for STABLE_MS earns a fresh budget, one that drops right after binding does not.
     */
    private static final int MAX_RETRIES=5;
    private static final long STABLE_MS=15000,BIND_CONFIRM_MS=4000;
    /**
     * Transitions. The game fades in from black once a bind is confirmed, or REVEAL_MS after binding at the
     * latest; the phone dashboard rises in when the game leaves the phone and fades out when it returns.
     */
    private static final long REVEAL_MS=900,REVEAL_FADE_MS=450,COVER_IN_MS=320,COVER_OUT_MS=200;
    /** Never fully transparent: a zero-alpha layer or view may stop consuming Unity's frames. */
    private static final float REVEAL_FROM=0.02f;
    private int retries;
    private long boundAt;
    private final Runnable retryOutput=()->{if(!destroyed&&presentation==null&&this.pendingDisplay==null)resumeOutput();};
    /** Startup/game progress sampled on Unity's main thread; negative counts describe hook availability. */
    private final Supplier<OutputGate.Sample> frames;
    private final OutputGate gate;
    private final DisplayTimeline timeline;
    /** A chosen display whose window opens only once the gate reports ready. */
    private Display pendingDisplay;
    private int lastGateState=-1;
    /** Keeps the Activity portrait for a whole output attempt, including the waits between retries. */
    private boolean outputLock;
    private long detachTimeoutMs=OutputGate.DEFAULT_DETACH_TIMEOUT_MS,bindFrames=-1;
    private boolean bindConfirmed;
    /** When the lobby started its hand-off to the game (-1 if not), and when the current game phase began. */
    private long lobbyExitAt=-1,gameSince=-1;
    private boolean lobbyWaitLogged;
    private int lastStages;
    private boolean coverShown;
    /** Surfaces whose Unity detach was not confirmed; released after Unity has rendered again. */
    private final List<Retained> retained=new ArrayList<>();
    /**
     * First-run setup is open. Its page is in front on the controller's display, and its rotation choice applies
     * at once (also to the game or welcome screen underneath, without saving until setup is finished).
     */
    private boolean setupOpen;
    private final ExternalSetupState setupState=new ExternalSetupState();
    private Presentation setupWindow;

    DisplayOutput(Activity activity,FrameLayout overlay,SharedPreferences prefs,Runnable disarm,Consumer<String> message,Consumer<AlertDialog> protect,DashboardHost host,Supplier<OutputGate.Sample> frames){
        this.activity=activity;this.prefs=prefs;this.disarm=disarm;this.message=message;this.protect=protect;this.host=host;this.frames=frames;
        long created=SystemClock.uptimeMillis();gate=new OutputGate(created);timeline=new DisplayTimeline(created);
        displays=(DisplayManager)activity.getSystemService(Context.DISPLAY_SERVICE);
        phoneCover=new FrameLayout(activity);phoneCover.setBackgroundColor(Miui.BG);phoneCover.setClickable(true);phoneCover.setVisibility(View.GONE);
        overlay.addView(phoneCover,0,new FrameLayout.LayoutParams(-1,-1));
        clockwise=prefs.getBoolean("external_clockwise",false);
        // Retired controls must not leave invisible overrides after an upgrade.
        prefs.edit().remove("graphics_force_gles").remove("graphics_full_hd")
            .remove("external_top_offset").remove("external_bottom_offset")
            .remove("external_top_scale").remove("external_bottom_scale").remove("external_split").apply();
        try{
            Field field=activity.getClass().getDeclaredField("mUnityPlayer");field.setAccessible(true);unity=field.get(activity);
            displayChanged=unity.getClass().getMethod("displayChanged",int.class,Surface.class);
            aspectChanged=unity.getClass().getMethod("setMainSurfaceViewAspectRatio",float.class);
            surfaceChanged=unity.getClass().getDeclaredMethod("sendSurfaceChangedEvent");surfaceChanged.setAccessible(true);
            hidePreserved=declared(unity.getClass(),"hidePreservedContent");glView=field(unity.getClass(),"mGlView");
            prefs.edit().remove("phone_wide").apply();
            phonePortrait=prefs.getBoolean("phone_portrait",false);applyPhoneAspect();
        }catch(Exception e){detail=I18n.t(Msg.DISPLAY_ERROR_INIT,e.getClass().getSimpleName());message.accept(detail);}
        if(unity!=null)try{
            Class<?> sync=Class.forName("com.unity3d.player.UnityPlayer$SynchronizationTimeout",false,unity.getClass().getClassLoader());
            for(Object value:sync.getEnumConstants())if("SurfaceDetach".equals(((Enum<?>)value).name()))detachTimeoutMs=((Number)sync.getMethod("getTimeout").invoke(value)).longValue();
        }catch(ReflectiveOperationException|RuntimeException ignored){/* Keep Unity's documented 2 s default. */}
        record("session module="+BuildConfig.VERSION_NAME+" portrait="+isPortrait()+" activityHandlesRotation="+handlesRotation()
            +" graphics="+(UnityStartup.needsGles(Build.VERSION.SDK_INT,Build.MANUFACTURER,Build.BRAND)?"gles30-forced":"unity-default")+" detachTimeout="+detachTimeoutMs+"ms");
        displays.registerDisplayListener(this,ui);
        // Turning output off lasts for one game launch: every new launch starts it again when a monitor
        // is attached. Done before the launch policy below so its portrait lock applies.
        if(prefs.getBoolean("setup_complete",false)&&!prefs.getBoolean("external_enabled",true)){
            prefs.edit().putBoolean("external_enabled",true).apply();record("external output on again for this launch");
        }
        // With output on and a monitor already attached, lock portrait now, before Unity's first
        // surface exists, so the game never starts landscape only to rotate when output begins.
        if(displayChanged!=null&&prefs.getBoolean("setup_complete",false)&&prefs.getBoolean("external_enabled",true)&&!available().isEmpty()){
            outputLock=true;updateOrientation();record("portrait locked at launch: monitor attached");
        }
    }
    private void record(String event){android.util.Log.i("OniimaiDisplay",timeline.add(SystemClock.uptimeMillis(),event));}
    /** A method or field declared on the player class or one it extends, made accessible; null when there is none. */
    private static Method declared(Class<?> type,String name){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name);m.setAccessible(true);return m;}catch(ReflectiveOperationException|RuntimeException ignored){}
        return null;
    }
    private static Field field(Class<?> type,String name){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException|RuntimeException ignored){}
        return null;
    }
    private boolean isPortrait(){return activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT;}
    /** Whether the installed game Activity handles rotation itself (true in the original 1.60 manifest). */
    private String handlesRotation(){
        try{
            int changes=activity.getPackageManager().getActivityInfo(activity.getComponentName(),0).configChanges;
            int rotation=ActivityInfo.CONFIG_ORIENTATION|ActivityInfo.CONFIG_SCREEN_SIZE;
            return String.valueOf((changes&rotation)==rotation);
        }catch(Exception e){return "unknown";}
    }
    private void populateCover(){
        phoneCover.removeAllViews();
        dashboard=new DashboardView(host,false,()->stop(I18n.t(Msg.DISPLAY_RETURNED_TO_PHONE)));
        phoneCover.addView(dashboard,new FrameLayout.LayoutParams(-1,-1));
    }
    boolean active(){return overrideRequested&&presentation!=null;}
    boolean dashboardBack(){return dashboard!=null&&dashboard.back();}
    boolean hasExternal(){return !available().isEmpty();}
    private List<Display> available(){
        List<Display> result=new ArrayList<>();int main=activity.getWindowManager().getDefaultDisplay().getDisplayId();
        for(Display d:displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))if(d.isValid()&&d.getDisplayId()!=main)result.add(d);
        return result;
    }
    void enable(){retries=0;prefs.edit().putBoolean("external_enabled",true).apply();if(!resumeOutput())choose();}
    private boolean resumeOutput(){
        if(destroyed||!foreground)return false;
        if(!prefs.getBoolean("setup_complete",false)||!prefs.getBoolean("external_enabled",true)){releaseLock();return false;}
        if(presentation!=null||pendingDisplay!=null)return true;
        List<Display> options=available();if(options.isEmpty()){releaseLock();return false;}
        String saved=prefs.getString("external_display_name","");
        for(Display d:options)if(d.getName().equals(saved)){start(d);return true;}
        if(options.size()==1){start(options.get(0));return true;}
        releaseLock();return false;
    }
    void previewDashboard(){
        if(active()||(preview!=null&&preview.isShowing()))return;
        DashboardView view=new DashboardView(host,true,()->{if(preview!=null)preview.dismiss();});
        AlertDialog dialog=NativeUi.page(activity,view);preview=dialog;protect.accept(dialog);
        dialog.setOnDismissListener(d->{if(preview==dialog)preview=null;});
        dialog.setOnKeyListener((d,code,event)->{
            if(new ControllerInput().captures(event))return true;
            return code==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&view.back();
        });
        NativeUi.showFullScreen(dialog);view.requestApplyInsets();
    }
    void languageChanged(){
        if(active())populateCover();
        if(!setupOpen)clockwise=prefs.getBoolean("external_clockwise",false);
        setupState.setLanguage(I18n.language());
        if(presentation!=null)presentation.fit();
        if(presentation!=null&&presentation.lobby)detail=I18n.t(Msg.DISPLAY_LOBBY_STATUS);
        else if(presentation!=null&&overrideRequested)updateOutputDetail(presentation);
        else detail=phonePortrait?I18n.t(Msg.DISPLAY_STATUS_PHONE_PORTRAIT):I18n.t(Msg.DISPLAY_STATUS_PHONE);
    }
    boolean portraitLocked(){return locked;}
    boolean isPhonePortrait(){return phonePortrait;}
    boolean isClockwise(){return clockwise;}
    private String rotationName(){return clockwise?I18n.t(Msg.DISPLAY_ROTATION_CW):I18n.t(Msg.DISPLAY_ROTATION_CCW);}
    void setClockwise(boolean enabled){
        disarm.run();clockwise=enabled;prefs.edit().putBoolean("external_clockwise",enabled).apply();
        if(presentation!=null){presentation.fit();if(overrideRequested)updateOutputDetail(presentation);}
        else detail=I18n.t(Msg.DISPLAY_STATUS_ROTATION,rotationName());
    }
    private void updateOutputDetail(External owner){detail=I18n.t(Msg.DISPLAY_STATUS_ACTIVE,owner.getDisplay().getName(),rotationName());}
    String summary(){return detail;}
    String diagnostic(){
        StringBuilder b=new StringBuilder(detail).append("\nPhone 9:16: ").append(phonePortrait).append(" / Activity portrait: ").append(isPortrait()).append(" / Output lock: ").append(outputLock)
            .append("\nOutput gate: ").append(pendingDisplay==null?"idle":OutputGate.name(lastGateState)).append(" / Output update signal: ").append(gate.frameSignal()).append(" / Bind confirmed: ").append(bindConfirmed).append(" / Retained surfaces: ").append(retained.size()).append("\nExternal rotation: ").append(clockwise?90:-90).append(" / Unity buffer: 1080x1920 / ").append(presentation==null?"none":presentation.route());
        if(presentation!=null)b.append("\nExternal mode: ").append(presentation.getDisplay().getMode()).append(" / Window: ").append(presentation.frame.getWidth()).append('x').append(presentation.frame.getHeight());
        if(presentation!=null&&presentation.texture!=null)b.append("\nExternal view: ").append(presentation.texture.getWidth()).append('x').append(presentation.texture.getHeight()).append(" / Texture frames: ").append(presentation.textureFrames).append(" / Hardware: ").append(presentation.texture.isHardwareAccelerated());
        for(Display d:displays.getDisplays()){
            Point size=new Point();d.getRealSize(size);b.append("\nDisplay ").append(d.getDisplayId()).append(": ").append(d.getName()).append(' ').append(size.x).append('x').append(size.y).append(" flags=").append(d.getFlags());
        }
        return b.append("\nDisplay timeline:\n").append(timeline.dump()).toString();
    }
    void setPhonePortrait(boolean enabled){
        disarm.run();phonePortrait=enabled;prefs.edit().putBoolean("phone_portrait",enabled).apply();applyPhoneAspect();
        if(presentation==null)detail=enabled?I18n.t(Msg.DISPLAY_STATUS_PHONE_PORTRAIT):I18n.t(Msg.DISPLAY_STATUS_PHONE_NATIVE);
    }
    private void applyPhoneAspect(){
        updateOrientation();
        try{if(aspectChanged!=null)aspectChanged.invoke(unity,phonePortrait?9f/16f:0f);}catch(Exception e){message.accept(I18n.t(Msg.DISPLAY_ERROR_ASPECT,e.getClass().getSimpleName()));}
    }
    private void updateOrientation(){
        // The Activity follows the portrait game; the Presentation keeps the external display's native landscape bounds.
        boolean need=phonePortrait||outputLock||presentation!=null;
        if(need&&!locked){previousOrientation=activity.getRequestedOrientation();locked=true;activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);}
        else if(!need&&locked){locked=false;activity.setRequestedOrientation(previousOrientation);}
    }
    void choose(){
        if(displayChanged==null){message.accept(I18n.t(Msg.DISPLAY_ERROR_UNITY_INIT));return;}
        Display host=activity.getWindowManager().getDefaultDisplay();List<Display> options=new ArrayList<>();
        for(Display d:displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))if(d.isValid()&&d.getDisplayId()!=host.getDisplayId())options.add(d);
        if(options.isEmpty()){
            detail=I18n.t(Msg.DISPLAY_ERROR_NO_DISPLAY);
            message.accept(detail);return;
        }
        String[] names=new String[options.size()];
        for(int i=0;i<options.size();i++){Display d=options.get(i);Point size=new Point();d.getRealSize(size);names[i]=d.getName()+"  ·  "+size.x+" × "+size.y;}
        NativeUi.choice(activity,I18n.t(Msg.DISPLAY_CHOOSE_TITLE),names,-1,protect,n->{retries=0;start(options.get(n));});
    }
    private void start(Display display){
        if(displayChanged==null)return;
        if(presentation!=null&&presentation.getDisplay().getDisplayId()==display.getDisplayId())return;
        if(pendingDisplay!=null&&pendingDisplay.getDisplayId()==display.getDisplayId())return;
        stopInternal();disarm.run();
        prefs.edit().putBoolean("external_enabled",true).putString("external_display_name",display.getName()).apply();
        if(destroyed||!display.isValid())return;
        pendingDisplay=display;lastGateState=-1;gate.begin();outputLock=true;updateOrientation();
        detail=I18n.t(Msg.DISPLAY_STATUS_WILL_CONNECT,display.getName());
        record("start requested display="+display.getDisplayId()+" portrait="+isPortrait());
        ui.removeCallbacks(gateLoop);ui.post(gateLoop);
    }
    /** Polls the gate; the external window is created only when the game is rendering in a settled portrait Activity. */
    private boolean outputAllowed(){return prefs.getBoolean("setup_complete",false)&&prefs.getBoolean("external_enabled",true);}
    private final Runnable gateLoop=new Runnable(){@Override public void run(){
        Display target=pendingDisplay;
        if(target==null||destroyed||!foreground)return;
        if(!outputAllowed()){pendingDisplay=null;record("start cancelled: external output turned off");detail=I18n.t(Msg.DISPLAY_STATUS_PHONE);releaseLock();return;}
        if(!target.isValid()){closeOutput(I18n.t(Msg.DISPLAY_REMOVED));return;}
        long now=SystemClock.uptimeMillis();OutputGate.Sample sample=observe(now);
        int state=gate.state(isPortrait(),now);
        if(state!=lastGateState){lastGateState=state;record("gate "+OutputGate.name(state)+" portrait="+isPortrait());}
        if(state==OutputGate.READY){
            // The startup screen, or a retry during KanadeDX's loading screen, gets the welcome screen; the
            // compatibility path (no hooks, no phase) opens the game output as before.
            boolean lobby=OutputGate.opensLobby(sample,gameSince,now);
            if(lobby&&sample.phase==OutputGate.PHASE_GAME)record("game still loading: welcome screen until its loading screen ends");
            pendingDisplay=null;open(target,lobby);return;
        }
        if(state==OutputGate.ROTATION_TIMEOUT){fail(I18n.t(Msg.DISPLAY_ERROR_NOT_PORTRAIT));return;}
        detail=state==OutputGate.WAIT_GAME?I18n.t(Msg.DISPLAY_STATUS_WAIT_GAME,target.getName()):I18n.t(Msg.DISPLAY_STATUS_WAIT_PORTRAIT,target.getName());
        ui.postDelayed(this,100);
    }};
    private void open(Display display,boolean lobby){
        // Re-check everything at the last moment: settings, lifecycle and the display itself.
        if(destroyed||!foreground||!outputAllowed()||!display.isValid()){
            record("open skipped: "+(destroyed?"destroyed":!foreground?"background":!outputAllowed()?"output off":"display gone"));
            releaseLock();return;
        }
        try{
            previousKeepScreenOn=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
            External next=new External(display,lobby);presentation=next;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);updateOrientation();
            next.setOnDismissListener(d->{if(presentation==next)fail(I18n.t(Msg.DISPLAY_ENDED));});
            detail=lobby?I18n.t(Msg.DISPLAY_LOBBY_STATUS):I18n.t(Msg.DISPLAY_STATUS_CONNECTING,display.getName(),rotationName());
            record("presentation show display="+display.getDisplayId()+" lobby="+lobby);
            next.show();next.getWindow().setLayout(-1,-1);
            if(lobby){lastGateState=-1;ui.removeCallbacks(lobbyLoop);ui.post(lobbyLoop);}
            // Setup stays in front of the output that has just opened under it.
            if(setupWindow!=null){closeSetupWindow();showSetupWindow();}
        }catch(RuntimeException e){fail(I18n.t(Msg.DISPLAY_ERROR_OPEN,e.getClass().getSimpleName()));}
    }
    private OutputGate.Sample observe(long now){
        OutputGate.Sample sample=frames.get();if(gate.frames(sample,now))record("output updates started phase="+sample.phase);
        if(sample.phase!=OutputGate.PHASE_GAME)gameSince=-1;else if(gameSince<0)gameSince=now;
        int loaded=sample.stages&OutputGate.STAGE_LOADED;
        if(loaded!=lastStages){lastStages=loaded;if(loaded!=0)record("game loading screen finished");}
        return sample;
    }
    /** The external lobby owns no Unity surface. Keep the real game on the phone until it is ready. */
    private final Runnable lobbyLoop=new Runnable(){@Override public void run(){
        External current=presentation;
        if(current==null||!current.lobby||destroyed||!foreground)return;
        if(!outputAllowed()){disabledBySettings(I18n.t(Msg.DISPLAY_STATUS_PHONE));return;}
        if(!current.getDisplay().isValid()){closeOutput(I18n.t(Msg.DISPLAY_REMOVED));return;}
        long now=SystemClock.uptimeMillis();OutputGate.Sample sample=observe(now);
        int state=gate.state(isPortrait(),now);
        boolean ready=sample.phase==OutputGate.PHASE_STARTUP&&state==OutputGate.READY;
        if(current.lobbyView!=null)current.lobbyView.update(ready);
        if(state!=lastGateState){lastGateState=state;record("lobby gate "+OutputGate.name(state)+" portrait="+isPortrait());}
        if(sample.phase==OutputGate.PHASE_GAME&&!lobbyWaitLogged){
            lobbyWaitLogged=true;record(sample.screens?"lobby: game loading, waiting for the end of its loading screen":"lobby: game phase, no loading-end signal");
        }
        // The game phase begins part-way through KanadeDX's own loading screen; hand over as that screen ends.
        if(state==OutputGate.READY&&OutputGate.lobbyHandoff(sample,gameSince,now)){
            // Play the hand-off (white ring, fade to black) first, and switch only if the game is still ready after it.
            if(lobbyExitAt<0){lobbyExitAt=now;record("lobby hand-off: "+OutputGate.stageName(sample));if(current.lobbyView!=null)current.lobbyView.finish(true);}
            long left=(android.animation.ValueAnimator.areAnimatorsEnabled()?CabinetLobbyView.EXIT_MS:0)-(now-lobbyExitAt);
            if(left<=0||current.lobbyView==null){
                Display target=current.getDisplay();record("lobby finished: game ready");
                stopInternal();open(target,false);return;
            }
            ui.postDelayed(this,Math.min(50,left));return;
        }
        if(lobbyExitAt>=0){lobbyExitAt=-1;record("lobby hand-off cancelled: gate "+OutputGate.name(state));if(current.lobbyView!=null)current.lobbyView.finish(false);}
        if(state==OutputGate.ROTATION_TIMEOUT){fail(I18n.t(Msg.DISPLAY_ERROR_NOT_PORTRAIT));return;}
        ui.postDelayed(this,250);
    }};
    /** From the session's 1 s probe, so the gate knows the game is rendering before any output attempt. */
    void observeFrames(){observe(SystemClock.uptimeMillis());}
    /** Forwarded from the Activity's onConfigurationChanged hook. */
    void configurationChanged(){
        long now=SystemClock.uptimeMillis();observe(now);gate.configurationChanged(now);
        Configuration config=activity.getResources().getConfiguration();
        record("configuration "+(isPortrait()?"portrait":"landscape")+" "+config.screenWidthDp+"x"+config.screenHeightDp+"dp");
    }
    /** The game's input hooks just became ready: a fresh retry budget for an output that failed while loading. */
    void gameReady(){
        record("game hooks ready");
        if(destroyed||presentation!=null||pendingDisplay!=null||!foreground)return;
        retries=0;ui.removeCallbacks(retryOutput);ui.postDelayed(retryOutput,500);
    }
    /** Ends an output attempt for good: lets the phone rotate freely again. */
    private void releaseLock(){
        if(!outputLock||presentation!=null||pendingDisplay!=null)return;
        outputLock=false;updateOrientation();record("portrait lock released");
    }
    /** Like closeOutput, but schedules recovery and toasts only the first failure of a retry series. */
    private void fail(String reason){record("failed: "+reason);disarm.run();stopInternal();detail=reason;if(retries==0)message.accept(reason);scheduleRetry();}
    private void scheduleRetry(){
        ui.removeCallbacks(retryOutput);
        if(destroyed)return;
        if(!foreground)return; // resume() starts over and keeps the portrait lock meanwhile
        if(!prefs.getBoolean("external_enabled",true)||available().isEmpty()){boundAt=0;releaseLock();return;}
        if(boundAt>0&&SystemClock.uptimeMillis()-boundAt>=STABLE_MS)retries=0;
        boundAt=0;
        if(retries>=MAX_RETRIES){detail=I18n.t(Msg.DISPLAY_STATUS_RETRY_STOPPED,detail);message.accept(detail);releaseLock();return;}
        long delay=1000L<<Math.min(retries,3);retries++;
        detail=I18n.t(Msg.DISPLAY_STATUS_RETRY_IN,detail,delay/1000,retries,MAX_RETRIES);
        android.util.Log.w("OniimaiDisplay","External output lost; retry "+retries+"/"+MAX_RETRIES+" in "+delay+"ms");
        ui.postDelayed(retryOutput,delay);
    }
    private void bind(External owner,Surface surface){
        if(destroyed||presentation!=owner||!surface.isValid())return;
        try{
            overrideRequested=true;
            if(!Boolean.TRUE.equals(displayChanged.invoke(unity,0,surface)))throw new IllegalStateException("Unity not ready");
            surfaceChanged.invoke(unity);
            NativeBridge.externalDisplay(true);
            populateCover();showCover();
            updateOutputDetail(owner);boundAt=SystemClock.uptimeMillis();
            // true only means the switch was queued on UnityMain; later game frames confirm it ran.
            bindFrames=frames.get().count;bindConfirmed=false;record("bind queued surface="+owner.route());
            ui.removeCallbacks(confirmLoop);ui.postDelayed(confirmLoop,200);
        }catch(Exception e){fail(I18n.t(Msg.DISPLAY_ERROR_BIND,e.getClass().getSimpleName()));}
    }
    private final Runnable confirmLoop=new Runnable(){@Override public void run(){
        External owner=presentation;
        if(owner==null||!overrideRequested||bindConfirmed||destroyed)return;
        long now=SystemClock.uptimeMillis(),count=frames.get().count;
        if(now-boundAt>=REVEAL_MS)owner.reveal();
        if(OutputGate.bindConfirmed(count,bindFrames)){bindConfirmed=true;record("bind confirmed after "+(now-boundAt)+"ms");owner.reveal();return;}
        if(now-boundAt>=BIND_CONFIRM_MS){
            if(count<0){record("bind not verifiable: no output update signal");return;}
            record("bind unconfirmed: no output update for "+(now-boundAt)+"ms");
            detail=I18n.t(Msg.DISPLAY_STATUS_UNCONFIRMED,detail);return;
        }
        ui.postDelayed(this,200);
    }};
    /**
     * Back on the phone, Unity adds its view again and covers it with a still of the phone's last frame (taken
     * when the game left for the monitor) until it is told it has drawn again. That word does not always come,
     * so the phone could keep showing the old still while the game runs underneath, until the monitor was turned
     * on and off once more. Once the phone's surface is back and the game is drawing, the still is taken away.
     */
    private final Runnable returnLoop=new Runnable(){@Override public void run(){
        if(destroyed||overrideRequested)return;
        long now=SystemClock.uptimeMillis();
        if(!OutputGate.phoneUncover(frames.get().count,returnFrames,phoneSurfaceReady(),now-returnAt)){ui.postDelayed(this,100);return;}
        try{
            if(hidePreserved!=null)hidePreserved.invoke(unity);
            // As the view's own surfaceChanged does: the game reads the phone's size again.
            if(surfaceChanged!=null)surfaceChanged.invoke(unity);
            record("phone view uncovered after "+(now-returnAt)+"ms"+(hidePreserved==null?" (no still to remove)":""));
        }catch(ReflectiveOperationException|RuntimeException e){record("phone view uncover failed: "+e.getClass().getSimpleName());}
    }};
    /** Whether Unity's own view is back on the phone with a surface to draw on. */
    private boolean phoneSurfaceReady(){
        try{
            Object view=glView==null?null:glView.get(unity);
            if(!(view instanceof ViewGroup))return true;
            ViewGroup group=(ViewGroup)view;
            if(!group.isAttachedToWindow())return false;
            for(int i=0;i<group.getChildCount();i++){
                View child=group.getChildAt(i);
                if(child instanceof SurfaceView)return ((SurfaceView)child).getHolder().getSurface().isValid();
            }
            return true;
        }catch(ReflectiveOperationException|RuntimeException e){return true;}
    }

    /** A surface Unity may still be using, kept alive until Unity has rendered again. */
    private static final class Retained {
        final Surface surface;final SurfaceControl layer;final SurfaceTexture texture;final long at,frames;
        Retained(Surface surface,SurfaceControl layer,SurfaceTexture texture,long at,long frames){this.surface=surface;this.layer=layer;this.texture=texture;this.at=at;this.frames=frames;}
        void release(){
            if(surface!=null)surface.release();
            if(layer!=null)layer.release();
            if(texture!=null)texture.release();
        }
    }
    private void retain(Surface surface,SurfaceControl layer,SurfaceTexture texture){
        retained.add(new Retained(surface,layer,texture,SystemClock.uptimeMillis(),frames.get().count));
        record("surface retained: Unity detach not confirmed");
        ui.removeCallbacks(releaseRetained);ui.postDelayed(releaseRetained,300);
    }
    private final Runnable releaseRetained=new Runnable(){@Override public void run(){
        long now=SystemClock.uptimeMillis(),count=frames.get().count;
        for(Iterator<Retained> it=retained.iterator();it.hasNext();){
            Retained r=it.next();
            if(OutputGate.detachSettled(count,r.frames,now-r.at)){r.release();it.remove();record("retained surface released after "+(now-r.at)+"ms");}
        }
        if(!retained.isEmpty())ui.postDelayed(this,250);
    }};
    /** A saved OFF from any screen (first-run setup included): cancels waiting, retrying and active output alike. */
    void disabledBySettings(String reason){
        ui.removeCallbacks(retryOutput);retries=0;
        if(presentation!=null||pendingDisplay!=null)closeOutput(reason);else releaseLock();
    }
    void stop(String reason){ui.removeCallbacks(retryOutput);retries=0;prefs.edit().putBoolean("external_enabled",false).apply();closeOutput(reason);}
    private void closeOutput(String reason){record("closed: "+reason);disarm.run();stopInternal();detail=reason;message.accept(reason);releaseLock();}
    /**
     * displayChanged(0,null) waits for Unity's detach but returns true even after its timeout.
     * A call that lasted the whole timeout is treated as unconfirmed, and the old surface is retained.
     */
    private boolean detachUnity(){
        long started=SystemClock.uptimeMillis();boolean returned=false;
        try{displayChanged.invoke(unity,0,(Surface)null);returned=true;}catch(Exception e){message.accept(I18n.t(Msg.DISPLAY_ERROR_RESTORE));}
        long elapsed=SystemClock.uptimeMillis()-started;
        boolean confirmed=returned&&OutputGate.detachConfirmed(elapsed,detachTimeoutMs);
        record("detach "+elapsed+"ms "+(confirmed?"confirmed":"not confirmed"));
        return confirmed;
    }
    private void stopInternal(){
        NativeBridge.externalDisplay(false);
        if(pendingDisplay!=null){pendingDisplay=null;record("start cancelled");}
        ui.removeCallbacks(gateLoop);ui.removeCallbacks(confirmLoop);ui.removeCallbacks(lobbyLoop);lobbyExitAt=-1;lobbyWaitLogged=false;
        External old=presentation;presentation=null;
        if(overrideRequested){
            overrideRequested=false;bindConfirmed=false;
            if(!detachUnity()&&old!=null)old.retainSurface=true;
            returnAt=SystemClock.uptimeMillis();returnFrames=frames.get().count;
            ui.removeCallbacks(returnLoop);ui.postDelayed(returnLoop,100);
        }
        if(old!=null){old.releaseSurface();old.dismiss();if(!previousKeepScreenOn)activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
        hideCover();dashboard=null;updateOrientation();
        if(setupOpen)ui.post(this::showSetupWindow);
    }
    /** What setup is showing, including its rotation choice; see {@link #setupOpen}. */
    void setupPreview(SetupView view){
        if(destroyed)return;
        // While setup's detailed settings are open, only a choice made there turns the screen: a view sent
        // then (say, for a button press) still carries setup's older choice and must not undo it.
        boolean turn=view.getRotationHeld()?clockwise:view.getClockwise();
        setupOpen=true;setupState.setView(view.getClockwise()==turn?view:view.turned(turn));
        if(clockwise!=turn){
            clockwise=turn;record("setup rotation "+(turn?"clockwise":"counterclockwise"));
            if(presentation!=null){presentation.fit();if(overrideRequested)updateOutputDetail(presentation);}
        }
        showSetupWindow();
    }
    /** Setup closed (finished or cancelled): its screen goes, and the saved rotation applies again. */
    void setupEnded(){
        if(!setupOpen)return;
        setupOpen=false;closeSetupWindow();
        boolean saved=prefs.getBoolean("external_clockwise",false);
        if(clockwise!=saved){clockwise=saved;if(presentation!=null){presentation.fit();if(overrideRequested)updateOutputDetail(presentation);}}
    }
    /**
     * Setup's page in front of whatever the controller's display shows: nothing, the welcome screen or the game,
     * which keeps running underneath untouched (no surface is moved). A later window on the same display is
     * drawn above an earlier one, so an output that opens during setup puts the page back in front (see open).
     */
    private void showSetupWindow(){
        if(!setupOpen||setupWindow!=null||!foreground||destroyed)return;
        Display target=null;
        if(presentation!=null)target=presentation.getDisplay();
        else if(pendingDisplay!=null)target=pendingDisplay;
        else{
            List<Display> options=available();if(options.isEmpty())return;
            target=options.get(0);String saved=prefs.getString("external_display_name","");
            for(Display d:options)if(d.getName().equals(saved))target=d;
        }
        if(target==null||!target.isValid())return;
        try{
            Presentation next=new SetupWindow(target);setupWindow=next;
            next.setOnDismissListener(d->{if(setupWindow==next)setupWindow=null;});
            next.show();next.getWindow().setLayout(-1,-1);
            record("setup screen shown display="+target.getDisplayId());
        }catch(RuntimeException e){setupWindow=null;record("setup screen failed: "+e.getClass().getSimpleName());}
    }
    private void closeSetupWindow(){
        Presentation old=setupWindow;setupWindow=null;
        if(old!=null){old.dismiss();record("setup screen closed");}
    }
    /** The phone dashboard rises in as the game moves to the monitor. */
    private void showCover(){
        coverShown=true;phoneCover.animate().cancel();
        phoneCover.setVisibility(View.VISIBLE);phoneCover.setAlpha(0);
        phoneCover.animate().alpha(1).setDuration(COVER_IN_MS).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        if(dashboard!=null){
            dashboard.setTranslationY(24*activity.getResources().getDisplayMetrics().density);
            dashboard.animate().translationY(0).setDuration(COVER_IN_MS).setInterpolator(new android.view.animation.DecelerateInterpolator(2)).start();
        }
    }
    /** It fades out to show the game again; in the background or at teardown it goes at once. */
    private void hideCover(){
        coverShown=false;phoneCover.animate().cancel();
        if(destroyed||!foreground||phoneCover.getVisibility()!=View.VISIBLE){phoneCover.setVisibility(View.GONE);phoneCover.removeAllViews();phoneCover.setAlpha(1);return;}
        phoneCover.animate().alpha(0).setDuration(COVER_OUT_MS).withEndAction(()->{
            if(coverShown)return;
            phoneCover.setVisibility(View.GONE);phoneCover.removeAllViews();phoneCover.setAlpha(1);
        }).start();
    }
    void pause(){foreground=false;ui.removeCallbacks(retryOutput);record("activity paused");closeSetupWindow();stopInternal();}
    void resume(){foreground=true;retries=0;ui.postDelayed(()->{if(!destroyed){resumeOutput();showSetupWindow();}},350);}
    /**
     * Module teardown, called before the game's own onDestroy. Surfaces whose Unity detach was not
     * confirmed stay alive here: UnityPlayer.destroy() runs after this, in the game's onDestroy.
     */
    void destroy(){
        destroyed=true;ui.removeCallbacks(retryOutput);ui.removeCallbacks(returnLoop);displays.unregisterDisplayListener(this);if(preview!=null)preview.dismiss();closeSetupWindow();stopInternal();
        ui.removeCallbacks(releaseRetained);
        if(!retained.isEmpty())record(retained.size()+" retained surface(s) kept until the game's onDestroy returns");
        outputLock=false;phonePortrait=false;updateOrientation();
    }
    /** After the game's onDestroy (and with it UnityPlayer.destroy) has returned. */
    void releaseAfterUnity(){
        if(retained.isEmpty())return;
        for(Retained r:retained)r.release();
        android.util.Log.i("OniimaiDisplay",timeline.add(SystemClock.uptimeMillis(),retained.size()+" retained surface(s) released after the game's onDestroy"));
        retained.clear();
    }
    @Override public void onDisplayAdded(int id){ui.postDelayed(()->{if(!destroyed){resumeOutput();showSetupWindow();}},350);}
    @Override public void onDisplayRemoved(int id){
        if(setupWindow!=null&&setupWindow.getDisplay().getDisplayId()==id)closeSetupWindow();
        boolean ours=(presentation!=null&&presentation.getDisplay().getDisplayId()==id)||(pendingDisplay!=null&&pendingDisplay.getDisplayId()==id);
        if(ours)closeOutput(I18n.t(Msg.DISPLAY_REMOVED));
        else if(presentation==null&&pendingDisplay==null&&available().isEmpty())releaseLock();
    }
    @Override public void onDisplayChanged(int id){
        if(presentation!=null){if(presentation.getDisplay().getDisplayId()==id)presentation.fit();return;}
        if(pendingDisplay!=null)return;
        // A mode change can drop the old Presentation; try again once the display settles, within the same budget.
        if(destroyed||!foreground||retries>=MAX_RETRIES||!prefs.getBoolean("external_enabled",true))return;
        if(id==activity.getWindowManager().getDefaultDisplay().getDisplayId())return;
        ui.removeCallbacks(retryOutput);ui.postDelayed(retryOutput,800);
    }

    /** Setup's own screen: Compose content only, never a Unity surface, and never focusable or touchable. */
    private final class SetupWindow extends Presentation {
        SetupWindow(Display display){
            super(activity,display,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
        @Override protected void onCreate(Bundle saved){
            super.onCreate(saved);
            Window window=getWindow();window.setTitle("Oniimai Setup");
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
            WindowManager.LayoutParams attrs=window.getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(attrs);
            setContentView(NativeUi.externalSetup(activity,setupState));
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            if(Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);WindowInsetsController controller=window.getInsetsController();if(controller!=null)controller.hide(WindowInsets.Type.systemBars());}
        }
    }

    private final class External extends Presentation implements TextureView.SurfaceTextureListener,SurfaceHolder.Callback2 {
        private final boolean lobby;
        private CabinetLobbyView lobbyView;
        private FrameLayout frame;
        private TextureView texture;
        private SurfaceView anchor;
        private SurfaceControl gameLayer;
        private Method layerMatrix,layerPosition;
        private boolean direct=Build.VERSION.SDK_INT>=29;
        private boolean fallbackPending;
        private Surface renderSurface;
        private SurfaceTexture sourceTexture;
        /** Set when Unity's detach was not confirmed: releaseSurface() hands the surface to the retained list. */
        boolean retainSurface;
        private boolean textureRetained;
        private long textureFrames;
        /** The game's fade-in from black: the layer (or TextureView) alpha, and the animator that raises it. */
        private float revealAlpha=REVEAL_FROM;
        private boolean revealed;
        private android.animation.ValueAnimator revealAnimator;
        External(Display display,boolean lobby){
            super(activity,display,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            this.lobby=lobby;
            // The target Activity disables acceleration, so enable it explicitly for this window.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
        @Override protected void onCreate(Bundle saved){
            super.onCreate(saved);
            Window window=getWindow();window.setTitle("Oniimai External Game");window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if(lobby)window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            WindowManager.LayoutParams attrs=window.getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(attrs);
            frame=new FrameLayout(getContext());frame.setBackgroundColor(Color.BLACK);setContentView(frame);
            if(lobby){
                lobbyView=new CabinetLobbyView(getContext(),host,clockwise);
                frame.addView(lobbyView,new FrameLayout.LayoutParams(-1,-1));
            }else if(direct){
                // Only the child layer carries Unity buffers. The unrotated SurfaceView
                // is an anchor; Android owns its position and lifecycle.
                anchor=new SurfaceView(getContext());anchor.setZOrderOnTop(true);
                anchor.getHolder().addCallback(this);
                frame.addView(anchor,new FrameLayout.LayoutParams(-1,-1));
            }else installTexture();
            frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->fit());
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            if(Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);WindowInsetsController controller=window.getInsetsController();if(controller!=null){controller.hide(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        }
        String route(){return lobby?"Native module lobby":direct?"SurfaceControl direct":"TextureView fallback";}
        private void installTexture(){
            texture=new TextureView(getContext());texture.setOpaque(true);texture.setSurfaceTextureListener(this);texture.setAlpha(revealAlpha);
            frame.addView(texture,new FrameLayout.LayoutParams(-1,-1));
            frame.post(()->{if(presentation==this&&!texture.isHardwareAccelerated())closeOutput(I18n.t(Msg.DISPLAY_ERROR_ACCELERATION));});
        }
        void fit(){
            if(frame==null||frame.getWidth()<=0||frame.getHeight()<=0)return;
            if(lobby){if(lobbyView!=null)lobbyView.rotation(clockwise);return;}
            if(direct){
                if(gameLayer==null||!gameLayer.isValid())return;
                float[] m=DisplayGeometry.surfaceMatrix(frame.getWidth(),frame.getHeight(),clockwise);
                try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
                    // Transform only our own child, never the framework-owned SurfaceView layer.
                    // setGeometry's deprecated rotation/scaling implementation differs across releases.
                    layerMatrix.invoke(tx,gameLayer,m[0],m[3],m[1],m[4]);
                    layerPosition.invoke(tx,gameLayer,m[2],m[5]);
                    tx.setLayer(gameLayer,1).setAlpha(gameLayer,revealAlpha).setVisibility(gameLayer,true).apply();
                }catch(ReflectiveOperationException|RuntimeException error){fallbackToTexture(error);}
            }else if(texture!=null&&texture.getWidth()>0&&texture.getHeight()>0){
                Matrix matrix=new Matrix();matrix.setValues(DisplayGeometry.rotationMatrix(texture.getWidth(),texture.getHeight(),clockwise));texture.setTransform(matrix);
            }
        }
        /** Fades the game in from black. Idempotent; the lobby has its own entrance. */
        void reveal(){
            if(lobby||revealed||presentation!=this||destroyed)return;
            revealed=true;record("output fade-in");
            revealAnimator=android.animation.ValueAnimator.ofFloat(revealAlpha,1);revealAnimator.setDuration(REVEAL_FADE_MS);
            revealAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            revealAnimator.addUpdateListener(animation->{revealAlpha=(float)animation.getAnimatedValue();applyReveal();});
            revealAnimator.start();
        }
        private void applyReveal(){
            if(presentation!=this)return;
            if(texture!=null)texture.setAlpha(revealAlpha);
            if(gameLayer!=null&&gameLayer.isValid())try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){tx.setAlpha(gameLayer,revealAlpha).apply();}catch(RuntimeException ignored){}
        }
        @Override public void surfaceCreated(SurfaceHolder holder){
            if(presentation!=this||destroyed||!direct)return;
            try{
                layerMatrix=SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class);
                layerPosition=SurfaceControl.Transaction.class.getMethod("setPosition",SurfaceControl.class,float.class,float.class);
                gameLayer=new SurfaceControl.Builder().setName("Oniimai Unity External")
                    .setParent(anchor.getSurfaceControl()).setBufferSize(DisplayGeometry.GAME_WIDTH,DisplayGeometry.GAME_HEIGHT)
                    .setOpaque(true).setHidden(true).build();
                renderSurface=new Surface(gameLayer);fit();if(fallbackPending)return;
                if(Build.VERSION.SDK_INT>=30)renderSurface.setFrameRate(getDisplay().getRefreshRate(),Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                bind(this,renderSurface);
                if(presentation==this&&overrideRequested)android.util.Log.i("OniimaiDisplay","Bound direct Unity surface 1080x1920 on display "+getDisplay().getDisplayId());
            }catch(ReflectiveOperationException|RuntimeException error){
                fallbackToTexture(error);
            }
        }
        private void fallbackToTexture(Exception error){
            if(fallbackPending||presentation!=this||destroyed)return;
            fallbackPending=true;android.util.Log.w("OniimaiDisplay","Direct surface unavailable; using TextureView",error);
            // Defer replacement until the current SurfaceHolder/layout callback returns.
            ui.post(()->{
                if(presentation!=this||destroyed)return;
                try{
                    if(overrideRequested){
                        disarm.run();overrideRequested=false;bindConfirmed=false;ui.removeCallbacks(confirmLoop);
                        if(!detachUnity())retainSurface=true;
                    }
                    direct=false;anchor.getHolder().removeCallback(this);
                    releaseSurface();retainSurface=false;frame.removeView(anchor);anchor=null;installTexture();
                }catch(RuntimeException failure){fail(I18n.t(Msg.DISPLAY_ERROR_BIND,failure.getClass().getSimpleName()));}
            });
        }
        @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(presentation==this)fit();}
        @Override public void surfaceRedrawNeeded(SurfaceHolder holder){
            // Submit a single black anchor buffer so window relayout can finish.
            // Unity renders only into the independent child, never into this buffer.
            if(presentation!=this||destroyed||!holder.getSurface().isValid())return;
            Canvas canvas=null;try{canvas=holder.lockCanvas();if(canvas!=null)canvas.drawColor(Color.BLACK);}finally{if(canvas!=null)holder.unlockCanvasAndPost(canvas);}
        }
        @Override public void surfaceDestroyed(SurfaceHolder holder){
            if(presentation==this)fail(I18n.t(Msg.DISPLAY_SURFACE_ENDED));
            releaseSurface();
        }
        private void attach(SurfaceTexture source){
            if(presentation!=this||destroyed)return;
            // TextureView may reset this default to the view's LANDSCAPE size on resize.
            // Restore portrait dimensions before Unity creates/recreates its EGL surface.
            source.setDefaultBufferSize(DisplayGeometry.GAME_WIDTH,DisplayGeometry.GAME_HEIGHT);
            sourceTexture=source;fit();
            // Bind a producer once. Rebinding the same surface recreates Unity's swapchain.
            if(presentation==this&&renderSurface==null){renderSurface=new Surface(source);bind(this,renderSurface);}
        }
        void releaseSurface(){
            // A cancelled fade (fallback to TextureView) runs again from its current alpha on the next confirmed bind.
            if(revealAnimator!=null){revealAnimator.cancel();revealAnimator=null;revealed=false;}
            if(retainSurface){
                // Unity may still render into this producer: keep producer and consumer alive, off screen.
                if(gameLayer!=null)try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){if(gameLayer.isValid())tx.reparent(gameLayer,null).apply();}catch(RuntimeException ignored){}
                SurfaceTexture texture=textureRetained?null:sourceTexture;
                if(renderSurface!=null||gameLayer!=null||texture!=null){retain(renderSurface,gameLayer,texture);textureRetained|=texture!=null;}
                renderSurface=null;gameLayer=null;return;
            }
            if(renderSurface!=null){renderSurface.release();renderSurface=null;}
            if(gameLayer!=null){
                try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){if(gameLayer.isValid())tx.reparent(gameLayer,null).apply();}
                catch(RuntimeException error){android.util.Log.w("OniimaiDisplay","External layer already disconnected",error);}
                finally{gameLayer.release();gameLayer=null;}
            }
        }
        @Override public void onSurfaceTextureAvailable(SurfaceTexture source,int width,int height){attach(source);}
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture source,int width,int height){attach(source);}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture source){
            // Detach the Unity producer before TextureView releases its consumer texture.
            if(presentation==this)fail(I18n.t(Msg.DISPLAY_SURFACE_ENDED));
            releaseSurface();
            // A retained SurfaceTexture is released by us once Unity has detached.
            return !textureRetained;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture source){textureFrames++;}
    }
}
