package io.oniimai.kanade;

import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcF;
import android.nfc.tech.TagTechnology;
import java.io.IOException;

/** One cancelable tag connection. Used by direct NPatch and the permissioned service. */
final class PhoneTagReader implements AutoCloseable {
    private TagTechnology active;
    private boolean closed;
    private synchronized void use(TagTechnology tag)throws IOException{
        if(closed)throw new IOException("Canceled");active=tag;
    }
    PhoneCardReader.Result read(Tag tag){
        try{
            NfcF felica=NfcF.get(tag);MifareClassic classic=MifareClassic.get(tag);
            if(felica!=null){
                use(felica);felica.connect();felica.setTimeout(1000);
                return PhoneCardReader.felica(felica::transceive);
            }
            if(classic!=null){
                use(classic);classic.connect();classic.setTimeout(1000);
                return PhoneCardReader.classic(new PhoneCardReader.Classic(){
                    public boolean authenticate(boolean keyB,byte[] key)throws IOException{return keyB?classic.authenticateSectorWithKeyB(0,key):classic.authenticateSectorWithKeyA(0,key);}
                    public byte[] read(int block)throws IOException{return classic.readBlock(block);}
                });
            }
            return PhoneCardReader.failure(AimeChannel.UNSUPPORTED_TYPE);
        }catch(Exception error){return PhoneCardReader.failure(AimeChannel.READ_FAILED);}
        finally{close();}
    }
    public void close(){
        TagTechnology tag;synchronized(this){closed=true;tag=active;active=null;}
        if(tag!=null)try{tag.close();}catch(IOException ignored){}
    }
}
