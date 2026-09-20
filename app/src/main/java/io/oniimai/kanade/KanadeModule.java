package io.oniimai.kanade;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

public final class KanadeModule extends XposedModule {
    private static final String TARGET="app.KanadeDX";
    private static final String NON_ROOT_TARGET="app.KanadeDX.oniimai";
    private static boolean supportedPackage(String name){return TARGET.equals(name)||NON_ROOT_TARGET.equals(name);}
    private boolean targetProcess,installed,nativeLoaded;
    private GameSession session;
    @Override public void onModuleLoaded(ModuleLoadedParam param){
        targetProcess=!param.isSystemServer()&&supportedPackage(param.getProcessName());
        if(!targetProcess)return;
        GameAssets.moduleApk(getModuleApplicationInfo().sourceDir);
        try{String dir=getModuleApplicationInfo().nativeLibraryDir;if(dir!=null){System.load(dir+"/liboniimai_kanade.so");nativeLoaded=true;}}
        catch(Throwable e){log(Log.ERROR,"OniimaiKanade","Native library load failed",e);}
    }
    @Override public void onPackageReady(PackageReadyParam param){
        if(!targetProcess||installed||!supportedPackage(param.getPackageName()))return;
        try{
            if(!nativeLoaded)try{NativeLoader.load(getModuleApplicationInfo(),param.getApplicationInfo());nativeLoaded=true;}
            catch(Throwable e){log(Log.ERROR,"OniimaiKanade","Native module cache load failed",e);}
            if(NON_ROOT_TARGET.equals(param.getPackageName())){
                // NPatch changes the manifest package but preserves the original ARSC namespace.
                hook(android.content.res.Resources.class.getDeclaredMethod("getIdentifier",String.class,String.class,String.class)).intercept(chain->{
                    String name=(String)chain.getArg(0),type=(String)chain.getArg(1),pkg=(String)chain.getArg(2);
                    if(name!=null&&name.startsWith(NON_ROOT_TARGET+":"))name=TARGET+name.substring(NON_ROOT_TARGET.length());
                    if(NON_ROOT_TARGET.equals(pkg))pkg=TARGET;
                    return chain.proceed(new Object[]{name,type,pkg});
                });
            }
            Class<?> activity=param.getClassLoader().loadClass("com.unity3d.player.UnityPlayerActivity");
            try{
                // ReaderMode supplies its own callback. The separate legacy Beam
                // callback on Activity resume requires the game's missing NFC
                // permission, and is unused here. Actual tag I/O is performed by
                // our NFC-permissioned service; NFC service permission checks stay intact.
                Class<?> manager=Class.forName("android.nfc.NfcActivityManager");
                hook(manager.getDeclaredMethod("requestNfcServiceCallback")).intercept(chain->{
                    try{return chain.proceed();}catch(SecurityException missingLegacyPermission){return null;}
                });
            }catch(ReflectiveOperationException unavailable){log(Log.WARN,"OniimaiKanade","Legacy NFC callback hook unavailable");}
            if(UnityStartup.needsGles(android.os.Build.VERSION.SDK_INT,android.os.Build.MANUFACTURER,android.os.Build.BRAND)){
                // Must run before UnityPlayer constructs the native graphics device. No user setting.
                hook(activity.getDeclaredMethod("updateUnityCommandLineArguments",String.class)).intercept(chain->{
                    String args=(String)chain.proceed();
                    log(Log.INFO,"OniimaiKanade","Automatic OpenGL ES startup: Xiaomi Android 16+ swapchain workaround");
                    return UnityStartup.commandLine(args);
                });
            }
            hook(activity.getDeclaredMethod("onCreate",Bundle.class)).intercept(chain->{
                Object result=chain.proceed();
                try{if(session!=null)session.destroy();Activity host=(Activity)chain.getThisObject();UiLanguage.load(host.getSharedPreferences("oniimai_controller_v1",Activity.MODE_PRIVATE));session=new GameSession(host,nativeLoaded);}
                catch(Throwable e){log(Log.ERROR,"OniimaiKanade","Controller panel initialization failed",e);}
                return result;
            });
            hook(activity.getDeclaredMethod("dispatchKeyEvent",KeyEvent.class)).intercept(chain->{
                if(session!=null&&session.activity==chain.getThisObject()&&session.key((KeyEvent)chain.getArg(0)))return true;
                return chain.proceed();
            });
            hook(activity.getDeclaredMethod("onPause")).intercept(chain->{if(matches(chain.getThisObject()))session.pause();return chain.proceed();});
            hook(activity.getDeclaredMethod("onResume")).intercept(chain->{Object result=chain.proceed();if(matches(chain.getThisObject()))session.resume();return result;});
            hook(activity.getDeclaredMethod("onConfigurationChanged",android.content.res.Configuration.class)).intercept(chain->{Object result=chain.proceed();if(matches(chain.getThisObject()))session.layoutChanged();return result;});
            hook(activity.getDeclaredMethod("onWindowFocusChanged",boolean.class)).intercept(chain->{if(matches(chain.getThisObject())){if((Boolean)chain.getArg(0))session.focusGained();else session.focusLost();}return chain.proceed();});
            hook(activity.getDeclaredMethod("onDestroy")).intercept(chain->{if(matches(chain.getThisObject())){session.destroy();session=null;}return chain.proceed();});
            Method generic=Activity.class.getDeclaredMethod("dispatchGenericMotionEvent",MotionEvent.class);
            hook(generic).intercept(chain->{if(matches(chain.getThisObject())&&session.motion((MotionEvent)chain.getArg(0)))return true;return chain.proceed();});
            hook(Activity.class.getDeclaredMethod("setRequestedOrientation",int.class)).intercept(chain->{
                if(matches(chain.getThisObject())&&session.portraitLocked())return chain.proceed(new Object[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT});
                return chain.proceed();
            });
            installed=true;log(Log.INFO,"OniimaiKanade","API 102 controller module ready for "+param.getPackageName());
        }catch(Throwable e){log(Log.ERROR,"OniimaiKanade","Could not install activity hooks",e);}
    }
    private boolean matches(Object activity){return session!=null&&session.activity==activity;}
    @Override public boolean onHotReloading(HotReloadingParam param){return false;}
}
