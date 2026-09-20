package io.oniimai.kanade;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Read-only SEGA NFC framing, as documented by the reader reference credited at onii.cc. */
final class AimeProtocol {
    static final int BAUD=115200, ADDRESS=0, LED_ADDRESS=8;
    static final int FW=0x30, HW=0x32, START=0x40, STOP=0x41, DETECT=0x42, SELECT=0x43;
    static final int KEY_A=0x50, AUTH_A=0x51, READ=0x52, KEY_B=0x54, AUTH_B=0x55, NORMAL=0x62, FELICA=0x71;
    static final int LED_RGB=0x81, LED_NORMAL=0xf5;
    static final int MIFARE=0x10, FELICA_CARD=0x20;
    static final int STATUS_CARD_ERROR=1, STATUS_INVALID_COMMAND=3;
    static final byte[] EMPTY=new byte[0];
    private AimeProtocol() {}

    static byte[] request(int sequence,int command,byte[] payload) {
        return request(ADDRESS,sequence,command,payload);
    }
    static byte[] request(int address,int sequence,int command,byte[] payload) {
        if(address<0||address>255||sequence<0||sequence>255||command<0||command>255||payload==null||payload.length>250)
            throw new IllegalArgumentException("Invalid NFC request");
        byte[] body=new byte[5+payload.length];
        body[0]=(byte)body.length;body[1]=(byte)address;body[2]=(byte)sequence;body[3]=(byte)command;body[4]=(byte)payload.length;
        System.arraycopy(payload,0,body,5,payload.length);return frame(body);
    }
    static byte[] rgb(int value){if(value<0||value>0xffffff)throw new IllegalArgumentException("Reader RGB range");return new byte[]{(byte)(value>>16),(byte)(value>>8),(byte)value};}
    static byte[] frame(byte[] body) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(2*body.length+3);out.write(0xe0);int sum=0;
        for(byte b:body){int value=b&255;sum=(sum+value)&255;escape(out,value);}escape(out,sum);return out.toByteArray();
    }
    private static void escape(ByteArrayOutputStream out,int value){if(value==0xe0||value==0xd0){out.write(0xd0);out.write(value-1);}else out.write(value);}
    interface Listener {void accept(Reply reply);}
    static final class Reply {
        final int address,sequence,command,status;final byte[] payload;
        Reply(byte[] body,int length){address=body[1]&255;sequence=body[2]&255;command=body[3]&255;status=body[4]&255;payload=Arrays.copyOfRange(body,6,length);}
    }
    /** Handles fragmented/coalesced USB reads. Invalid frames never reach the request matcher. */
    static final class Parser {
        private final Listener listener;private final byte[] body=new byte[256];
        private int count,length,sum;private boolean active,escaped;
        volatile long validFrames,invalidFrames;
        Parser(Listener listener){this.listener=listener;}
        void feed(byte[] bytes,int size){for(int i=0;i<Math.min(size,bytes.length);i++)accept(bytes[i]&255);}
        private void reset(){active=false;escaped=false;count=length=sum=0;}
        private void accept(int value){
            if(value==0xe0){if(active&&(count>0||escaped))invalidFrames++;active=true;escaped=false;count=length=sum=0;return;}
            if(!active)return;
            if(escaped){escaped=false;if(value!=0xcf&&value!=0xdf){invalidFrames++;reset();return;}value++;}
            else if(value==0xd0){escaped=true;return;}
            if(count==0){length=value;if(length<6){invalidFrames++;reset();return;}}
            if(count==length){
                boolean valid=value==sum&&(body[5]&255)==length-6;
                Reply reply=valid?new Reply(body,length):null;if(valid)validFrames++;else invalidFrames++;reset();if(reply!=null)listener.accept(reply);return;
            }
            body[count++]=(byte)value;sum=(sum+value)&255;
        }
    }
    static final class Card {
        final int type;final byte[] id;
        Card(int type,byte[] id){this.type=type;this.id=id;}
    }
    /** Zero means absent; all advertised records and their exact lengths must be valid. */
    static Card[] cards(byte[] payload){
        if(payload==null||payload.length<1)throw new IllegalArgumentException("Empty NFC card list");
        int n=payload[0]&255;if(n>8)throw new IllegalArgumentException("NFC card count");
        Card[] result=new Card[n];int p=1;
        for(int i=0;i<n;i++){
            if(p+2>payload.length)throw new IllegalArgumentException("Truncated NFC card list");
            int type=payload[p++]&255,len=payload[p++]&255;
            if(len==0||len>32||p+len>payload.length)throw new IllegalArgumentException("Invalid NFC card length");
            if(type==FELICA_CARD&&len!=16)throw new IllegalArgumentException("Invalid FeliCa card length");
            result[i]=new Card(type,Arrays.copyOfRange(payload,p,p+len));p+=len;
        }
        if(p!=payload.length)throw new IllegalArgumentException("Trailing NFC card bytes");return result;
    }
    static byte[] block(byte[] uid,int number){if(uid==null||uid.length!=4)throw new IllegalArgumentException("MIFARE UID length");byte[] result=Arrays.copyOf(uid,5);result[4]=(byte)number;return result;}
    /** Compatible cards can keep custom block1 data; the stored code is in block2. */
    static boolean aimeMifareLayout(byte[] block){
        if(block==null||block.length!=16)return false;
        // Retain the explicit legacy Banapass exclusion. SBSD or an empty
        // marker cannot be required of user-provisioned Aime-code MIFARE1K.
        return !(block[2]=='N'&&block[3]=='B'&&block[4]=='G'&&block[5]=='I'&&block[6]=='C');
    }
    static String accessCode(byte[] block){
        if(block==null||block.length!=16)return null;char[] digits=new char[20];boolean nonzero=false;
        for(int i=0;i<10;i++){int b=block[6+i]&255,hi=b>>>4,lo=b&15;if(hi>9||lo>9)return null;digits[i*2]=(char)('0'+hi);digits[i*2+1]=(char)('0'+lo);nonzero|=b!=0;}
        return nonzero?new String(digits):null;
    }
    /** Manufacturer's Example.txt: addressed discovery, after a successful 42 report. */
    static byte[] felicaPoll(byte[] id){
        if(id==null||id.length!=8)throw new IllegalArgumentException("FeliCa IDm length");
        byte[] result=Arrays.copyOf(id,14);
        byte[] polling={6,0,(byte)0xff,(byte)0xff,1,15};
        System.arraycopy(polling,0,result,8,polling.length);return result;
    }
    /** Polling request code01 must return IDm, PMm and the big-endian system code. */
    static int felicaSystem(byte[] response,byte[] detected){
        if(response==null||detected==null||detected.length!=16||response.length!=20||response[0]!=20||response[1]!=1)return -1;
        for(int i=0;i<16;i++)if(response[2+i]!=detected[i])return -1;
        return ((response[18]&255)<<8)|(response[19]&255);
    }
    static byte[] felicaRead(byte[] id){return felicaRead(id,0);}
    /** Only SPAD0 and the read-only ID block are needed for Aime identification. */
    static byte[] felicaRead(byte[] id,int block){
        if(id==null||id.length!=8)throw new IllegalArgumentException("FeliCa IDm length");
        if(block!=0&&block!=0x82)throw new IllegalArgumentException("Unsupported Aime block");
        byte[] result=new byte[24];System.arraycopy(id,0,result,0,8);result[8]=0x10;result[9]=0x06;System.arraycopy(id,0,result,10,8);
        result[18]=1;result[19]=0x0b;result[20]=0;result[21]=1;result[22]=(byte)0x80;result[23]=(byte)block;return result;
    }
    /** ID block: IDm[8], DFC[2], reserved[6]. Never use IDm as an access code. */
    static int felicaDfc(byte[] block,byte[] id){
        if(block==null||block.length!=16||id==null||id.length!=8)return -1;
        for(int i=0;i<8;i++)if(block[i]!=id[i])return -1;
        return ((block[8]&255)<<8)|(block[9]&255);
    }
    static byte[] felicaBlock(byte[] response,byte[] id){
        if(response==null||id==null||id.length!=8||response.length!=29||(response[0]&255)!=29||response[1]!=7||response[10]!=0||response[11]!=0||response[12]!=1)return null;
        for(int i=0;i<8;i++)if(response[2+i]!=id[i])return null;
        return Arrays.copyOfRange(response,13,29);
    }
    /** A Read Without Encryption error contains IDm and status flags, but no block data. */
    static int felicaReadError(byte[] response,byte[] id){
        if(response==null||id==null||id.length!=8||response.length!=12||response[0]!=12||response[1]!=7||response[10]==0)return -1;
        for(int i=0;i<8;i++)if(response[2+i]!=id[i])return -1;
        return ((response[10]&255)<<8)|(response[11]&255);
    }
}
