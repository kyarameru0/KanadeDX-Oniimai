package io.oniimai.kanade;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class PhoneNfcTest {
    private static int checks;
    private static void check(boolean condition,String text){checks++;if(!condition)throw new AssertionError(text);}
    private static byte[] hex(String s){byte[] bytes=new byte[s.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return bytes;}
    private static final byte[] CODE=hex("50101234567890123456");
    private static final class Classic implements PhoneCardReader.Classic {
        boolean keyA=true,keyB=true;int attempts;byte[] marker=new byte[16],block=hex("00000000000050101234567890123456");
        List<Integer> reads=new ArrayList<>();
        public boolean authenticate(boolean b,byte[] key){attempts++;check(Arrays.equals(key,b?hex("574343467632"):hex("6090d00632f5")),"Aime key for selected auth");return b?keyB:keyA;}
        public byte[] read(int n){reads.add(n);return n==1?marker:block;}
    }
    public static void main(String[] args)throws Exception{
        Classic card=new Classic();
        check(Arrays.equals(PhoneCardReader.classic(card).code,CODE),"MIFARE actual block2 BCD");
        check(card.attempts==1&&card.reads.equals(Arrays.asList(1,2)),"Key B then read only blocks1/2");
        card=new Classic();card.keyB=false;
        check(Arrays.equals(PhoneCardReader.classic(card).code,CODE)&&card.attempts==2,"Key A fallback after rejected B");
        card=new Classic();card.keyB=card.keyA=false;
        check(PhoneCardReader.classic(card).issue==AimeChannel.UNSUPPORTED_TYPE&&card.reads.isEmpty(),"No block access after failed auth");
        card=new Classic();System.arraycopy("NBGIC".getBytes("US-ASCII"),0,card.marker,2,5);
        check(PhoneCardReader.classic(card).issue==AimeChannel.INVALID_CARD&&card.reads.size()==1,"Banapass marker excluded");
        card=new Classic();card.block=new byte[16];check(PhoneCardReader.classic(card).code==null,"Zero code excluded");
        card=new Classic();card.block[15]=(byte)0xff;check(PhoneCardReader.classic(card).code==null,"Malformed BCD excluded");
        card=new Classic();card.marker[0]=17;check(PhoneCardReader.classic(card).code!=null,"Custom MIFARE1K marker retained");
        final byte[][] commands={hex("060088b40100"),hex("10060123456789abcdef010b00018082"),hex("10060123456789abcdef010b00018000")};
        final byte[][] replies={hex("14010123456789abcdef00f100000001430088b4"),hex("1d070123456789abcdef0000010123456789abcdef0078000000000000"),hex("1d070123456789abcdef000001893d691806eccfef90c8773d41eee231")};
        int[] step={0};PhoneCardReader.Result result=PhoneCardReader.felica(command->{int n=step[0]++;check(Arrays.equals(command,commands[n]),"Direct Android NFC-F frame "+n);return replies[n];});
        check(Arrays.equals(result.code,CODE)&&step[0]==3,"FeliCa real code, no UID conversion");
        for(int index:new int[]{0,1,2}){
            final int broken=index;step[0]=0;
            check(PhoneCardReader.felica(command->{int n=step[0]++;return n==broken?new byte[]{1}:replies[n];}).code==null,"Truncated stage rejected "+index);
        }
        step[0]=0;check(PhoneCardReader.felica(command->{int n=step[0]++;byte[] reply=replies[n].clone();if(n==1)reply[21]=1;return reply;}).issue==AimeChannel.UNSUPPORTED_TYPE,"Non SEGA DFC rejected before SPAD read");
        check(step[0]==2,"Non Aime stops early");
        try{PhoneCardReader.felica(command->{throw new IOException("lost");});throw new AssertionError("Expected transport failure");}catch(IOException expected){checks++;}
        for(String code:new String[]{null,"","00000000000000000000","5010123456789012345X","123"})check(PhoneCardReader.decoded(code).code==null,"Invalid decoded value rejected");
        PhoneScan guard=new PhoneScan();long token=guard.begin(11,100);
        check(token!=0&&guard.begin(11,101)==0,"Only one in-flight tag");
        check(!guard.complete(token-1,11,1,102)&&guard.token()==token,"Stale callback cannot steal current request");
        check(guard.complete(token,11,1,103),"Current result delivered");
        check(!guard.complete(token,11,1,104),"Duplicate result discarded");
        token=guard.begin(11,200);check(!guard.complete(token,12,1,201),"New game scan rejects stale card");
        token=guard.begin(12,300);check(!guard.complete(token,12,2,301),"Controller won: phone result discarded");
        token=guard.begin(12,400);check(!guard.complete(token,12,0,401),"Closed game scan discards result");
        token=guard.begin(13,500);check(!guard.expired(6499)&&guard.expired(6500),"Read deadline");
        check(!guard.complete(token,13,1,6500),"Expired result discarded");
        token=guard.begin(13,7000);guard.cancel();check(!guard.complete(token,13,1,7001),"Background/settings/cancel discards read");
        long fresh=guard.begin(13,8000);check(fresh!=token&&!guard.complete(token,13,1,8001)&&guard.complete(fresh,13,1,8002),"Canceled callback cannot complete later scan");
        System.out.println("PASS: "+checks+" phone NFC read and handoff checks");
    }
}
