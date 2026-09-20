package io.oniimai.kanade;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;

/** Grant a data-free URI capability so Android package visibility permits the game to bind. */
public final class NfcBridgeAccess {
    private static final Uri URI=Uri.parse("content://io.oniimai.kanade.nfcbridge/connection");
    static void grant(Context context){
        for(String game:new String[]{"app.KanadeDX","app.KanadeDX.oniimai"})try{
            context.getPackageManager().getApplicationInfo(game,0);
            context.grantUriPermission(game,URI,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(Exception ignored){}
    }
    /** A zero-content provider: the grant exposes no files, cards, preferences or queries. */
    public static final class Provider extends ContentProvider {
        public boolean onCreate(){return true;}
        public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
        public String getType(Uri uri){return "vnd.android.cursor.item/vnd.oniimai.nfcbridge";}
        public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
        public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
        public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    }
    public static final class Updated extends BroadcastReceiver {
        public void onReceive(Context context,Intent intent){if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))grant(context);}
    }
    /** First-install bootstrap only. No window, no NFC operation, finishes inside onCreate. */
    public static final class Bootstrap extends Activity {
        public void onCreate(Bundle state){super.onCreate(state);
            if(PhoneNfcService.allowedPackages(new String[]{getCallingPackage()}))grant(this);
            finish();
        }
    }
}
