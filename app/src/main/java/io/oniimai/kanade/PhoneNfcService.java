package io.oniimai.kanade;

import android.app.Service;
import android.content.Intent;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcF;
import android.nfc.tech.TagTechnology;
import android.os.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bound service: NFC I/O runs under the module's NFC permission, with no Activity. */
public final class PhoneNfcService extends Service {
    static final int READ=1,CANCEL=2,RESULT=3;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Messenger binder=new Messenger(new Handler(Looper.getMainLooper(),this::handle));
    private volatile PhoneTagReader current;
    private volatile int epoch;
    private boolean busy,destroyed;
    private int owner=-1;
    private long token;
    static boolean allowedPackages(String[] packages){
        if(packages==null)return false;
        for(String p:packages)if("app.KanadeDX".equals(p)||"app.KanadeDX.oniimai".equals(p))return true;
        return false;
    }
    private boolean handle(Message message){
        if(destroyed||!allowedPackages(getPackageManager().getPackagesForUid(message.sendingUid)))return true;
        Bundle data=message.getData();data.setClassLoader(Tag.class.getClassLoader());
        long request=data.getLong("token");
        if(message.what==CANCEL){if(message.sendingUid==owner&&request==token)cancel();return true;}
        if(message.what!=READ||message.replyTo==null||busy)return true;
        Tag tag=data.getParcelable("tag");if(tag==null)return true;
        busy=true;owner=message.sendingUid;token=request;
        final int ticket=++epoch;final Messenger reply=message.replyTo;
        final PhoneTagReader task=new PhoneTagReader();current=task;
        io.execute(()->{
            if(ticket!=epoch){task.close();return;}
            PhoneCardReader.Result result=task.read(tag);
            final PhoneCardReader.Result completed=result;
            ui.post(()->{
                try{
                    if(destroyed||ticket!=epoch)return;
                    busy=false;
                    Message response=Message.obtain(null,RESULT);Bundle body=new Bundle();
                    body.putLong("token",request);body.putByteArray("code",completed.code);body.putInt("issue",completed.issue);
                    response.setData(body);reply.send(response);
                }catch(RemoteException ignored){}
                finally{if(completed.code!=null)Arrays.fill(completed.code,(byte)0);}
            });
        });
        return true;
    }
    private void closeTag(){PhoneTagReader task=current;current=null;if(task!=null)task.close();}
    private void cancel(){epoch++;closeTag();busy=false;owner=-1;}
    @Override public IBinder onBind(Intent intent){return binder.getBinder();}
    @Override public boolean onUnbind(Intent intent){cancel();return false;}
    @Override public void onDestroy(){destroyed=true;cancel();io.shutdown();super.onDestroy();}
}
