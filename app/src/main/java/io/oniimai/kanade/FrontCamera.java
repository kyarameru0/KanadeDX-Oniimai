package io.oniimai.kanade;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

/** Permission belongs to the game package, including an NPatch-cloned package. */
final class FrontCamera {
    private static final String KEY="front_camera_enabled";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final boolean nativeLoaded;
    private boolean foreground=true;
    FrontCamera(Activity activity,SharedPreferences prefs,boolean nativeLoaded){
        this.activity=activity;this.prefs=prefs;this.nativeLoaded=nativeLoaded;publish();
    }
    boolean enabled(){return prefs.getBoolean(KEY,false);}
    boolean rear(){return prefs.getBoolean("camera_rear",false);}
    void rear(boolean value){prefs.edit().putBoolean("camera_rear",value).apply();publish();}
    boolean mirror(){boolean rear=rear();return prefs.getBoolean(rear?"camera_mirror_rear":"camera_mirror_front",!rear);}
    void mirror(boolean value){prefs.edit().putBoolean(rear()?"camera_mirror_rear":"camera_mirror_front",value).apply();publish();}
    boolean permitted(){return activity.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;}
    void foreground(boolean value){foreground=value;publish();}
    void enable(boolean value){prefs.edit().putBoolean(KEY,value).apply();publish();if(value&&!permitted())request();}
    void request(){
        if(!enabled())return;
        try{activity.requestPermissions(new String[]{Manifest.permission.CAMERA},0x4f43);}
        catch(RuntimeException failure){Log.w("OniimaiKanade","Camera permission request unavailable: "+failure.getClass().getSimpleName());}
    }
    // Called on resume and at the existing 1-second native probe, never prompts.
    void publish(){if(nativeLoaded)try{NativeBridge.cameraAuthorization(enabled(),permitted(),foreground,rear(),mirror());}catch(UnsatisfiedLinkError ignored){}}
    String summary(){
        if(!enabled())return I18n.t(Msg.CAMERA_OFF);
        if(!activity.getPackageManager().hasSystemFeature(rear()?PackageManager.FEATURE_CAMERA:PackageManager.FEATURE_CAMERA_FRONT))return I18n.t(Msg.CAMERA_NO_FRONT);
        if(!permitted())return I18n.t(Msg.CAMERA_PERMISSION_NEEDED);
        int state=0;if(nativeLoaded)try{state=NativeBridge.cameraStatus();}catch(UnsatisfiedLinkError ignored){}
        switch(state){
            case -7:return I18n.t(Msg.CAMERA_LENS_RESTART);
            case -6:return I18n.t(Msg.CAMERA_SKIP_ENABLED);
            case -1:case -2:return I18n.t(Msg.CAMERA_UNAVAILABLE);
            case -3:return I18n.t(Msg.CAMERA_NO_FRONT);
            case -4:case -5:return I18n.t(Msg.CAMERA_READ_FAILED);
            case 2:return I18n.t(Msg.CAMERA_SELECTED);
            case 3:return I18n.t(Msg.CAMERA_STARTED);
            case 4:return I18n.t(Msg.CAMERA_CAPTURED);
            default:return I18n.t(Msg.CAMERA_RESTART);
        }
    }
}
