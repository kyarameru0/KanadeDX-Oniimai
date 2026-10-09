package io.oniimai.kanade;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import java.util.IdentityHashMap;

/** Adds a CPU-readable output to the game's existing camera, never opens a second camera. */
final class CameraFrameSource implements AutoCloseable {
    static final ThreadLocal<CameraFrameSource> STARTING=new ThreadLocal<>();
    private static final IdentityHashMap<Object,CameraFrameSource> SOURCES=new IdentityHashMap<>();
    private static final IdentityHashMap<Surface,CameraFrameSource> TARGETS=new IdentityHashMap<>();
    final ImageReader reader;
    final Surface output;
    private final HandlerThread worker;
    private final long token;
    private volatile boolean closed;
    private boolean reported;

    private CameraFrameSource(Rect size){
        token=NativeBridge.cameraFrameStart();
        if(token==0)throw new IllegalStateException("Camera adapter inactive");
        worker=new HandlerThread("OniimaiCameraPixels");worker.start();
        ImageReader created=null;
        try{
            created=ImageReader.newInstance(size.width(),size.height(),ImageFormat.YUV_420_888,2);
            reader=created;output=reader.getSurface();
            reader.setOnImageAvailableListener(this::receive,new Handler(worker.getLooper()));
        }catch(RuntimeException failure){if(created!=null)created.close();worker.quitSafely();NativeBridge.cameraFrameEnd(token);throw failure;}
    }
    static synchronized CameraFrameSource start(Object wrapper,Rect size){
        if(!NativeBridge.cameraFrameAllowed()||size==null||size.width()<32||size.height()<32||size.width()>4096||size.height()>4096)return null;
        CameraFrameSource existing=SOURCES.get(wrapper);
        if(existing!=null)return existing;
        CameraFrameSource source=new CameraFrameSource(size);SOURCES.put(wrapper,source);return source;
    }
    static synchronized void stop(Object wrapper){
        CameraFrameSource source=SOURCES.remove(wrapper);
        if(source!=null){TARGETS.values().removeIf(value->value==source);source.close();}
    }
    static synchronized void associate(Surface original,CameraFrameSource source){TARGETS.put(original,source);}
    static synchronized Surface extraTarget(Surface original){
        CameraFrameSource source=TARGETS.get(original);return source==null||source.closed?null:source.output;
    }
    private void receive(ImageReader input){
        if(closed)return;
        try(Image image=input.acquireLatestImage()){
            if(image==null||closed||!NativeBridge.cameraFrameAllowed()||image.getFormat()!=ImageFormat.YUV_420_888)return;
            Image.Plane[] p=image.getPlanes();Rect crop=image.getCropRect();
            if(p.length!=3||crop.left!=0||crop.top!=0||crop.width()!=image.getWidth()||crop.height()!=image.getHeight())return;
            boolean accepted=NativeBridge.cameraFrameYuv(token,image.getWidth(),image.getHeight(),
                p[0].getBuffer().slice(),p[0].getRowStride(),p[0].getPixelStride(),
                p[1].getBuffer().slice(),p[1].getRowStride(),p[1].getPixelStride(),
                p[2].getBuffer().slice(),p[2].getRowStride(),p[2].getPixelStride());
            if(accepted&&!reported){reported=true;Log.i("OniimaiKanade","Front camera CPU stream ready: "+image.getWidth()+"x"+image.getHeight());}
        }catch(IllegalStateException ignored){/* The game closed the camera/session. */}
        catch(RuntimeException error){if(!reported){reported=true;Log.w("OniimaiKanade","Camera frame unavailable: "+error.getClass().getSimpleName());}}
    }
    @Override public void close(){
        if(closed)return;closed=true;NativeBridge.cameraFrameEnd(token);
        reader.close();worker.quitSafely();
    }
}
