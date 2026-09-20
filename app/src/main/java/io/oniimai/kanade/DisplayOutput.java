package io.oniimai.kanade;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
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

/** Routes Unity's main render surface to a user-selected Presentation display. */
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
    private External presentation;
    private final FrameLayout phoneCover;
    private boolean overrideRequested,phonePortrait,locked,destroyed;
    private boolean clockwise;
    private int previousOrientation;
    private boolean previousKeepScreenOn;
    private String detail=UiText.t("휴대폰 화면 사용 중");

    DisplayOutput(Activity activity,FrameLayout overlay,SharedPreferences prefs,Runnable disarm,Consumer<String> message,Consumer<AlertDialog> protect,DashboardHost host){
        this.activity=activity;this.prefs=prefs;this.disarm=disarm;this.message=message;this.protect=protect;this.host=host;
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
            prefs.edit().remove("phone_wide").apply();
            phonePortrait=prefs.getBoolean("phone_portrait",false);applyPhoneAspect();
        }catch(Exception e){detail=UiText.t("화면 제어 초기화 실패: ")+e.getClass().getSimpleName();message.accept(detail);}
        displays.registerDisplayListener(this,ui);
    }
    private void populateCover(){
        phoneCover.removeAllViews();
        dashboard=new DashboardView(host,false,()->stop(UiText.t("휴대폰 화면으로 돌아왔습니다.")));
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
    void enable(){prefs.edit().putBoolean("external_enabled",true).apply();if(!resumeOutput())choose();}
    private boolean resumeOutput(){
        if(destroyed||!foreground||!prefs.getBoolean("setup_complete",false)||!prefs.getBoolean("external_enabled",true))return false;
        if(presentation!=null)return true;
        List<Display> options=available();if(options.isEmpty())return false;
        String saved=prefs.getString("external_display_name","");
        for(Display d:options)if(d.getName().equals(saved)){start(d);return true;}
        if(options.size()==1){start(options.get(0));return true;}return false;
    }
    void previewDashboard(){
        if(active()||(preview!=null&&preview.isShowing()))return;
        DashboardView view=new DashboardView(host,true,()->{if(preview!=null)preview.dismiss();});
        AlertDialog dialog=new AlertDialog.Builder(activity,android.R.style.Theme_Material_Light_NoActionBar).setView(view).create();preview=dialog;protect.accept(dialog);
        dialog.setOnDismissListener(d->{if(preview==dialog)preview=null;});
        dialog.setOnKeyListener((d,code,event)->{
            if(new ControllerInput().captures(event))return true;
            return code==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&view.back();
        });
        NativeUi.showFullScreen(dialog);view.requestApplyInsets();
    }
    void languageChanged(){
        if(active())populateCover();
        clockwise=prefs.getBoolean("external_clockwise",false);if(presentation!=null)presentation.fit();
        if(presentation!=null&&overrideRequested)updateOutputDetail(presentation);
        else detail=phonePortrait?UiText.t("휴대폰 세로 9:16 · 미러링 여백은 연결 방식에 따라 남을 수 있음"):UiText.t("휴대폰 화면 사용 중");
    }
    boolean portraitLocked(){return locked;}
    boolean isPhonePortrait(){return phonePortrait;}
    boolean isClockwise(){return clockwise;}
    private String rotationName(){return clockwise?UiText.t("시계 90°"):UiText.t("반시계 90°");}
    void setClockwise(boolean enabled){
        disarm.run();clockwise=enabled;prefs.edit().putBoolean("external_clockwise",enabled).apply();
        if(presentation!=null){presentation.fit();if(overrideRequested)updateOutputDetail(presentation);}
        else detail=UiText.t("외부 출력 회전: ")+rotationName();
    }
    private void updateOutputDetail(External owner){detail=owner.getDisplay().getName()+UiText.t(" · 세로 게임 → ")+rotationName()+UiText.t(" · 가로 16:9 채움");}
    String summary(){return detail;}
    String diagnostic(){
        StringBuilder b=new StringBuilder(detail).append("\nPhone 9:16: ").append(phonePortrait).append("\nExternal rotation: ").append(clockwise?90:-90).append(" / Unity buffer: 1080x1920 / ").append(presentation==null?"none":presentation.route());
        if(presentation!=null)b.append("\nExternal mode: ").append(presentation.getDisplay().getMode()).append(" / Window: ").append(presentation.frame.getWidth()).append('x').append(presentation.frame.getHeight());
        if(presentation!=null&&presentation.texture!=null)b.append("\nExternal view: ").append(presentation.texture.getWidth()).append('x').append(presentation.texture.getHeight()).append(" / Texture frames: ").append(presentation.textureFrames).append(" / Hardware: ").append(presentation.texture.isHardwareAccelerated());
        for(Display d:displays.getDisplays()){
            Point size=new Point();d.getRealSize(size);b.append("\nDisplay ").append(d.getDisplayId()).append(": ").append(d.getName()).append(' ').append(size.x).append('x').append(size.y).append(" flags=").append(d.getFlags());
        }
        return b.toString();
    }
    void setPhonePortrait(boolean enabled){
        disarm.run();phonePortrait=enabled;prefs.edit().putBoolean("phone_portrait",enabled).apply();applyPhoneAspect();
        if(presentation==null)detail=enabled?UiText.t("휴대폰 세로 9:16 · 미러링 여백은 연결 방식에 따라 남을 수 있음"):UiText.t("휴대폰 원래 화면 비율");
    }
    private void applyPhoneAspect(){
        updateOrientation();
        try{if(aspectChanged!=null)aspectChanged.invoke(unity,phonePortrait?9f/16f:0f);}catch(Exception e){message.accept(UiText.t("휴대폰 비율 적용 실패: ")+e.getClass().getSimpleName());}
    }
    private void updateOrientation(){
        // The Activity follows the portrait game; the Presentation keeps the external display's native landscape bounds.
        boolean need=phonePortrait||presentation!=null;
        if(need&&!locked){previousOrientation=activity.getRequestedOrientation();locked=true;activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);}
        else if(!need&&locked){locked=false;activity.setRequestedOrientation(previousOrientation);}
    }
    void choose(){
        if(displayChanged==null){message.accept(UiText.t("Unity 화면 제어를 초기화하지 못했습니다."));return;}
        Display host=activity.getWindowManager().getDefaultDisplay();List<Display> options=new ArrayList<>();
        for(Display d:displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))if(d.isValid()&&d.getDisplayId()!=host.getDisplayId())options.add(d);
        if(options.isEmpty()){
            detail=UiText.t("외부 출력 화면을 찾지 못했습니다. HDMI/DP 연결 또는 미러링 방식 확인");
            message.accept(detail);return;
        }
        String[] names=new String[options.size()];
        for(int i=0;i<options.size();i++){Display d=options.get(i);Point size=new Point();d.getRealSize(size);names[i]=d.getName()+"  ·  "+size.x+" × "+size.y;}
        NativeUi.choice(activity,UiText.t("출력할 디스플레이"),names,-1,protect,n->start(options.get(n)));
    }
    private void start(Display display){
        if(displayChanged==null)return;
        if(presentation!=null&&presentation.getDisplay().getDisplayId()==display.getDisplayId())return;
        stopInternal();disarm.run();
        prefs.edit().putBoolean("external_enabled",true).putString("external_display_name",display.getName()).apply();
        if(destroyed||!display.isValid())return;
        try{
            previousKeepScreenOn=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
            External next=new External(display);presentation=next;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);updateOrientation();
            next.setOnDismissListener(d->{if(presentation==next)closeOutput(UiText.t("외부 출력이 종료되어 휴대폰으로 돌아왔습니다."));});
            next.show();next.getWindow().setLayout(-1,-1);
            detail=display.getName()+UiText.t(" · 세로 게임 ")+rotationName()+UiText.t(" 출력 연결 중");
        }catch(RuntimeException e){closeOutput(UiText.t("외부 화면을 열지 못했습니다: ")+e.getClass().getSimpleName());}
    }
    private void bind(External owner,Surface surface){
        if(destroyed||presentation!=owner||!surface.isValid())return;
        try{
            overrideRequested=true;
            if(!Boolean.TRUE.equals(displayChanged.invoke(unity,0,surface)))throw new IllegalStateException("Unity not ready");
            surfaceChanged.invoke(unity);
            NativeBridge.externalDisplay(true);
            populateCover();phoneCover.setVisibility(View.VISIBLE);
            updateOutputDetail(owner);
        }catch(Exception e){closeOutput(UiText.t("외부 출력 연결 실패: ")+e.getClass().getSimpleName());}
    }
    void stop(String reason){prefs.edit().putBoolean("external_enabled",false).apply();closeOutput(reason);}
    private void closeOutput(String reason){disarm.run();stopInternal();detail=reason;message.accept(reason);}
    private void stopInternal(){
        NativeBridge.externalDisplay(false);
        External old=presentation;presentation=null;
        if(overrideRequested){
            overrideRequested=false;
            try{displayChanged.invoke(unity,0,(Surface)null);}catch(Exception e){message.accept(UiText.t("휴대폰 화면 복귀 실패 — 게임을 재시작하세요."));}
        }
        if(old!=null){old.releaseSurface();old.dismiss();if(!previousKeepScreenOn)activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
        phoneCover.setVisibility(View.GONE);phoneCover.removeAllViews();dashboard=null;updateOrientation();
    }
    void pause(){foreground=false;stopInternal();}
    void resume(){foreground=true;ui.postDelayed(()->{if(!destroyed)resumeOutput();},350);}
    void destroy(){destroyed=true;displays.unregisterDisplayListener(this);if(preview!=null)preview.dismiss();stopInternal();phonePortrait=false;updateOrientation();}
    @Override public void onDisplayAdded(int id){ui.postDelayed(()->{if(!destroyed)resumeOutput();},350);}
    @Override public void onDisplayRemoved(int id){if(presentation!=null&&presentation.getDisplay().getDisplayId()==id)closeOutput(UiText.t("디스플레이 연결이 해제되어 휴대폰으로 돌아왔습니다."));}
    @Override public void onDisplayChanged(int id){if(presentation!=null&&presentation.getDisplay().getDisplayId()==id)presentation.fit();}

    private final class External extends Presentation implements TextureView.SurfaceTextureListener,SurfaceHolder.Callback2 {
        private FrameLayout frame;
        private TextureView texture;
        private SurfaceView anchor;
        private SurfaceControl gameLayer;
        private Method layerMatrix,layerPosition;
        private boolean direct=Build.VERSION.SDK_INT>=29;
        private boolean fallbackPending;
        private Surface renderSurface;
        private long textureFrames;
        External(Display display){
            super(activity,display,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
            // The target Activity disables acceleration, so enable it explicitly for this window.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
        @Override protected void onCreate(Bundle saved){
            super.onCreate(saved);
            Window window=getWindow();window.setTitle("Oniimai External Game");window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
            WindowManager.LayoutParams attrs=window.getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(attrs);
            frame=new FrameLayout(getContext());frame.setBackgroundColor(Color.BLACK);setContentView(frame);
            if(direct){
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
        String route(){return direct?"SurfaceControl direct":"TextureView fallback";}
        private void installTexture(){
            texture=new TextureView(getContext());texture.setOpaque(true);texture.setSurfaceTextureListener(this);
            frame.addView(texture,new FrameLayout.LayoutParams(-1,-1));
            frame.post(()->{if(presentation==this&&!texture.isHardwareAccelerated())closeOutput(UiText.t("외부 화면 회전에 필요한 그래픽 가속을 시작하지 못했습니다."));});
        }
        void fit(){
            if(frame==null||frame.getWidth()<=0||frame.getHeight()<=0)return;
            if(direct){
                if(gameLayer==null||!gameLayer.isValid())return;
                float[] m=DisplayGeometry.surfaceMatrix(frame.getWidth(),frame.getHeight(),clockwise);
                try(SurfaceControl.Transaction tx=new SurfaceControl.Transaction()){
                    // Transform only our own child, never the framework-owned SurfaceView layer.
                    // setGeometry's deprecated rotation/scaling implementation differs across releases.
                    layerMatrix.invoke(tx,gameLayer,m[0],m[3],m[1],m[4]);
                    layerPosition.invoke(tx,gameLayer,m[2],m[5]);
                    tx.setLayer(gameLayer,1).setVisibility(gameLayer,true).apply();
                }catch(ReflectiveOperationException|RuntimeException error){fallbackToTexture(error);}
            }else if(texture!=null&&texture.getWidth()>0&&texture.getHeight()>0){
                Matrix matrix=new Matrix();matrix.setValues(DisplayGeometry.rotationMatrix(texture.getWidth(),texture.getHeight(),clockwise));texture.setTransform(matrix);
            }
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
                        disarm.run();displayChanged.invoke(unity,0,(Surface)null);overrideRequested=false;
                    }
                    direct=false;anchor.getHolder().removeCallback(this);
                    releaseSurface();frame.removeView(anchor);anchor=null;installTexture();
                }catch(ReflectiveOperationException|RuntimeException failure){closeOutput(UiText.t("외부 출력 연결 실패: ")+failure.getClass().getSimpleName());}
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
            if(presentation==this)closeOutput(UiText.t("외부 화면 표면이 종료되어 휴대폰으로 돌아왔습니다."));
            releaseSurface();
        }
        private void attach(SurfaceTexture source){
            if(presentation!=this||destroyed)return;
            // TextureView may reset this default to the view's LANDSCAPE size on resize.
            // Restore portrait dimensions before Unity creates/recreates its EGL surface.
            source.setDefaultBufferSize(DisplayGeometry.GAME_WIDTH,DisplayGeometry.GAME_HEIGHT);
            fit();
            // Bind a producer once. Rebinding the same surface recreates Unity's swapchain.
            if(presentation==this&&renderSurface==null){renderSurface=new Surface(source);bind(this,renderSurface);}
        }
        void releaseSurface(){
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
            if(presentation==this)closeOutput(UiText.t("외부 화면 표면이 종료되어 휴대폰으로 돌아왔습니다."));
            releaseSurface();return true;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture source){textureFrames++;}
    }
}
