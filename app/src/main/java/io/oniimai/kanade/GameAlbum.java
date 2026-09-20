package io.oniimai.kanade;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Decodes a copied, revisioned game jacket off the UI thread. No Unity calls here. */
final class GameAlbum {
    private static final int MAX_BYTES=1024*1024;
    private static native byte[] nativePng(long revision);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private boolean closed;
    private String lastJson;
    private long wantedRevision,loadedRevision;
    private int wantedMusic=-1,loadedMusic=-1;
    private Bitmap bitmap;

    /** UI thread; copies/decodes only once for each distinct native revision. */
    void update(String json){
        if(closed||json==null||json.equals(lastJson))return;
        lastJson=json;int music=-1;long revision=0;
        try{JSONObject value=new JSONObject(json);if(value.optBoolean("game",false)){
            music=value.optInt("musicId",-1);revision=value.optLong("albumRevision",0);}}
        catch(Exception ignored){}
        if(music==wantedMusic&&revision==wantedRevision)return;
        wantedMusic=music;wantedRevision=revision;
        if(music<0||revision<=0){bitmap=null;loadedMusic=-1;loadedRevision=0;return;}
        final int requestedMusic=music;final long requestedRevision=revision;
        try{decoder.execute(()->{
            Bitmap decoded=null;
            try{
                byte[] png=nativePng(requestedRevision);
                if(png!=null&&png.length>=24&&png.length<=MAX_BYTES){
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(png,0,png.length,bounds);
                    if(bounds.outWidth==256&&bounds.outHeight==256){
                        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                        decoded=BitmapFactory.decodeByteArray(png,0,png.length,options);
                    }
                }
            }catch(UnsatisfiedLinkError|RuntimeException|OutOfMemoryError ignored){/* Optional artwork must not terminate gameplay. */}
            final Bitmap result=decoded;
            ui.post(()->{
                if(closed||wantedMusic!=requestedMusic||wantedRevision!=requestedRevision){if(result!=null)result.recycle();return;}
                bitmap=result;loadedMusic=requestedMusic;loadedRevision=requestedRevision;
            });
        });}catch(RejectedExecutionException ignored){}
    }
    Bitmap bitmap(){return !closed&&loadedMusic==wantedMusic&&loadedRevision==wantedRevision?bitmap:null;}
    void close(){closed=true;decoder.shutdownNow();bitmap=null;lastJson=null;}
}
