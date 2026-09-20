package io.oniimai.kanade;

import java.util.Arrays;
import java.util.function.Predicate;

/** One delivery per physical presentation. No card value is persisted or exposed in diagnostics. */
final class AimePresence {
    private byte[] current;
    private boolean delivered;
    private long absentSince=-1;

    static byte[] bcd(String code) {
        if(code==null||code.length()!=20)return null;
        byte[] result=new byte[10];boolean nonzero=false;
        for(int i=0;i<20;i++){
            char c=code.charAt(i);if(c<'0'||c>'9')return null;
            int digit=c-'0';result[i/2]|=(byte)(digit<<((i&1)==0?4:0));nonzero|=digit!=0;
        }
        return nonzero?result:null;
    }
    boolean observe(boolean present,String code,long now,boolean ready,Predicate<byte[]> submit){
        if(!present){
            if(absentSince<0)absentSince=now;
            if(now-absentSince>=300)clear();
            return false;
        }
        absentSince=-1;
        byte[] next=bcd(code);
        if(next==null)return false;
        if(!Arrays.equals(current,next)){clear();current=next;}
        else Arrays.fill(next,(byte)0);
        if(delivered||!ready)return false;
        byte[] copy=current.clone();
        try{if(submit.test(copy)){delivered=true;return true;}}
        finally{Arrays.fill(copy,(byte)0);}
        return false;
    }
    void clear(){if(current!=null)Arrays.fill(current,(byte)0);current=null;delivered=false;absentSince=-1;}
}
