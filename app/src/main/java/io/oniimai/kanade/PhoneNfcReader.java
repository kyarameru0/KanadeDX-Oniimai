package io.oniimai.kanade;

import android.app.Activity;
import android.content.*;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.*;
import android.util.Log;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground game's ReaderMode callback + NFC-permissioned, bound tag I/O service. */
final class PhoneNfcReader implements ServiceConnection,NfcAdapter.ReaderCallback {
    private final Activity activity;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Messenger callback=new Messenger(new Handler(Looper.getMainLooper(),this::result));
    private final PhoneScan read=new PhoneScan();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final boolean direct;
    private PhoneTagReader current;
    private NfcAdapter adapter;
    private Messenger service;
    private boolean bound,registered,enabled,destroyed,bootstrapped;
    private long nextCheck,retryAt;
    private String status="";
    PhoneNfcReader(Activity activity){
        this.activity=activity;direct=activity.checkSelfPermission("android.permission.NFC")==android.content.pm.PackageManager.PERMISSION_GRANTED;
        try{adapter=NfcAdapter.getDefaultAdapter(activity);}catch(RuntimeException error){Log.w("OniimaiPhoneNfc","Adapter initialization: "+error.getClass().getSimpleName());}
        Log.i("OniimaiPhoneNfc","Adapter present="+(adapter!=null)+" permission="+direct);
    }
    String summary(){return status;}
    boolean supported(){return adapter!=null;}
    void update(boolean allowed,long now){
        enabled=allowed;
        if(destroyed)return;
        if(!allowed){pause();if(status.isEmpty())status=adapter==null?I18n.t(Msg.PHONE_NFC_INIT_FAILED):I18n.t(Msg.PHONE_NFC_AUTO);return;}
        if(now<nextCheck)return;nextCheck=now+200;
        if(adapter==null){status=I18n.t(Msg.PHONE_NFC_UNSUPPORTED);return;}
        if(read.token()!=0&&(read.expired(now)||NativeBridge.aimeGeneration()!=read.generation()||NativeBridge.aimeStatus()!=1)){
            if(read.expired(now)&&NativeBridge.aimeGeneration()==read.generation()&&NativeBridge.aimeStatus()==1)NativeBridge.aimeError(AimeChannel.READ_FAILED,read.generation());
            cancelRead();
        }
        if(!direct&&!bound&&now>=retryAt){
            try{bound=activity.bindService(new Intent().setClassName("io.oniimai.kanade","io.oniimai.kanade.PhoneNfcService"),this,Context.BIND_AUTO_CREATE);}
            catch(RuntimeException ignored){bound=false;}
            Log.i("OniimaiPhoneNfc","Permission service bind="+bound);
            if(!bound){
                retryAt=now+5000;status=I18n.t(Msg.PHONE_NFC_UPDATE_MODULE);
                if(!bootstrapped){bootstrapped=true;try{
                    activity.startActivityForResult(new Intent().setClassName("io.oniimai.kanade","io.oniimai.kanade.NfcBridgeAccess$Bootstrap"),0x4f4e);
                }catch(RuntimeException ignored){}}
                return;
            }
        }
        if(!direct&&service==null){status=I18n.t(Msg.PHONE_NFC_CONNECTING);return;}
        boolean scanning=NativeBridge.aimeStatus()==1;
        try{
            if(!adapter.isEnabled()){disableMode();status=I18n.t(Msg.PHONE_NFC_TURN_ON);return;}
            if(scanning&&!registered){
                Bundle options=new Bundle();options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,250);
                adapter.enableReaderMode(activity,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,options);
                registered=true;Log.i("OniimaiPhoneNfc","In-game NFC ReaderMode enabled; transport="+(direct?"direct":"bound-service"));
            }else if(!scanning)disableMode();
            status=scanning?I18n.t(Msg.PHONE_NFC_SCANNING):I18n.t(Msg.PHONE_NFC_AUTO);
        }catch(RuntimeException error){disableMode();status=I18n.t(Msg.PHONE_NFC_FAILED);Log.w("OniimaiPhoneNfc","ReaderMode unavailable: "+error.getClass().getSimpleName());}
    }
    @Override public void onTagDiscovered(Tag tag){ui.post(()->{
        if(destroyed||!enabled||!registered||(!direct&&service==null)||read.token()!=0||NativeBridge.aimeStatus()!=1)return;
        long ticket=read.begin(NativeBridge.aimeGeneration(),SystemClock.uptimeMillis());
        if(direct){
            PhoneTagReader task=new PhoneTagReader();current=task;
            io.execute(()->{PhoneCardReader.Result result=task.read(tag);ui.post(()->deliver(ticket,result.code,result.issue));});
            return;
        }
        Message request=Message.obtain(null,PhoneNfcService.READ);Bundle data=new Bundle();data.putParcelable("tag",tag);data.putLong("token",ticket);request.setData(data);request.replyTo=callback;
        try{service.send(request);}catch(RemoteException error){cancelRead();}
    });}
    private boolean result(Message message){
        Bundle data=message.getData();byte[] code=data.getByteArray("code");
        try{
            if(destroyed||message.what!=PhoneNfcService.RESULT||!enabled||read.token()==0||data.getLong("token")!=read.token())return true;
            // NPatch can return the embedded APK's archive ApplicationInfo (UID
            // 0/-1), not the installed service identity. Resolve the Binder UID
            // to installed packages instead; never trust a UID in the payload.
            // The reply Messenger remains private to the explicit service bind.
            String[] packages=message.sendingUid<0?null:activity.getPackageManager().getPackagesForUid(message.sendingUid);
            if(packages==null||!Arrays.asList(packages).contains("io.oniimai.kanade")){
                Log.w("OniimaiPhoneNfc","NFC result rejected: sender is not the installed module service");return true;
            }
            deliver(data.getLong("token"),code,data.getInt("issue"));
        }catch(Exception error){Log.w("OniimaiPhoneNfc","NFC result unavailable");}
        finally{if(code!=null)Arrays.fill(code,(byte)0);}
        return true;
    }
    private void deliver(long token,byte[] code,int issue){
        try{
            if(destroyed||!enabled)return;
            long scan=read.generation();
            if(!read.complete(token,NativeBridge.aimeGeneration(),NativeBridge.aimeStatus(),SystemClock.uptimeMillis()))return;
            current=null;
            if(code!=null){if(NativeBridge.submitAime(code,scan))Log.i("OniimaiPhoneNfc","Phone card delivered in game");}
            else if(issue>=1&&issue<=5){NativeBridge.aimeError(issue,scan);Log.i("OniimaiPhoneNfc","Phone read error delivered in game; issue="+issue);}
        }finally{if(code!=null)Arrays.fill(code,(byte)0);}
    }
    private void cancelRead(){
        if(read.token()!=0&&service!=null)try{Message m=Message.obtain(null,PhoneNfcService.CANCEL);Bundle b=new Bundle();b.putLong("token",read.token());m.setData(b);service.send(m);}catch(RemoteException ignored){}
        if(current!=null){current.close();current=null;}read.cancel();
    }
    private void disableMode(){if(registered){registered=false;try{adapter.disableReaderMode(activity);}catch(RuntimeException ignored){}}cancelRead();}
    void pause(){enabled=false;disableMode();}
    void destroy(){destroyed=true;pause();if(bound)try{activity.unbindService(this);}catch(RuntimeException ignored){}bound=false;service=null;io.shutdown();ui.removeCallbacksAndMessages(null);}
    @Override public void onServiceConnected(ComponentName name,IBinder binder){if(destroyed)return;service=new Messenger(binder);nextCheck=0;}
    @Override public void onServiceDisconnected(ComponentName name){service=null;disableMode();}
    @Override public void onBindingDied(ComponentName name){service=null;disableMode();if(bound)try{activity.unbindService(this);}catch(RuntimeException ignored){}bound=false;retryAt=SystemClock.uptimeMillis()+1000;}
    @Override public void onNullBinding(ComponentName name){onBindingDied(name);}
}
