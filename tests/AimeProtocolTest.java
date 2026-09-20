package io.oniimai.kanade;

import java.util.*;

public final class AimeProtocolTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static byte[] hex(String text){byte[] b=new byte[text.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return b;}
    static byte[] response(int address,int seq,int cmd,int status,byte[] payload){byte[] body=new byte[6+payload.length];body[0]=(byte)body.length;body[1]=(byte)address;body[2]=(byte)seq;body[3]=(byte)cmd;body[4]=(byte)status;body[5]=(byte)payload.length;System.arraycopy(payload,0,body,6,payload.length);return AimeProtocol.frame(body);}
    public static void main(String[] args){
        check(Arrays.equals(AimeProtocol.request(9,0x30,new byte[0]),hex("e005000930003e")),"Public firmware request with independently calculated checksum");
        check(Arrays.equals(AimeProtocol.request(0,0x40,new byte[]{3}),hex("e00600004001034a")),"Reader start enables Type A and FeliCa");
        check(Arrays.equals(AimeProtocol.request(0,0x50,hex("6090d00632f5")),hex("e00b000050066090d0cf0632f54e")),"Authentication request escapes the documented D0 key byte");
        check(Arrays.equals(AimeProtocol.request(8,0,0xf5,new byte[0]),hex("e0050800f50002")),"Reader LED normal-mode command addresses node08");
        check(Arrays.equals(AimeProtocol.request(8,0,0x81,AimeProtocol.rgb(0xffe0d0)),hex("e00808008103ffd0dfd0cf43")),"Reader RGB uses command81, RGB order, checksum and escaping");
        for(int invalid:new int[]{-1,0x1000000})try{AimeProtocol.rgb(invalid);throw new AssertionError("RGB outside24bits");}catch(IllegalArgumentException expected){checks++;}
        List<AimeProtocol.Reply> replies=new ArrayList<>();AimeProtocol.Parser parser=new AimeProtocol.Parser(replies::add);
        byte[] frame=response(0,0xd0,0x42,0,hex("011004e0d01122"));
        for(byte b:frame)parser.feed(new byte[]{b},1);
        check(replies.size()==1&&replies.get(0).sequence==0xd0,"Fragmented sequence escaping");
        check(Arrays.equals(replies.get(0).payload,hex("011004e0d01122")),"Fragmented payload escaping");
        for(int sequence=0;sequence<256;sequence++){
            byte[] payload=new byte[sequence%250];Arrays.fill(payload,(byte)sequence);
            byte[] wire=response(0,sequence,0x42,0,payload);int previous=replies.size();
            parser.feed(wire,wire.length);check(replies.size()==previous+1&&Arrays.equals(replies.get(previous).payload,payload),"All sequence values, escaped lengths, checksum and maximum payloads");
        }
        int previous=replies.size();byte[] corrupt=frame.clone();corrupt[corrupt.length-1]^=1;parser.feed(corrupt,corrupt.length);
        check(replies.size()==previous,"Bad checksum discarded");
        byte[] malformed=AimeProtocol.frame(hex("07000042000200"));parser.feed(malformed,malformed.length);
        check(replies.size()==previous,"Inconsistent advertised payload length discarded");
        parser.feed(hex("e005010203e008d055"),8);parser.feed(frame,frame.length);
        check(replies.size()==previous+1,"Malformed length and escape recover at new sync");
        check(parser.invalidFrames==4&&parser.validFrames==replies.size(),"Parser numerical diagnostics count corruption without exposing payloads");
        byte[] maximum=new byte[249];maximum[248]=(byte)0xe0;byte[] maximumFrame=response(0,255,0x42,0,maximum);parser.feed(maximumFrame,maximumFrame.length);
        check(replies.get(replies.size()-1).payload.length==249,"Largest response bounded without overflow");
        check(AimeProtocol.cards(hex("00")).length==0,"No card report");
        AimeProtocol.Card[] cards=AimeProtocol.cards(hex("0210040102030420100123456789abcdef00f1000000014300"));
        check(cards.length==2&&cards[0].type==0x10&&cards[1].id.length==16,"Multiple card report preserves types without choosing randomly");
        for(String bad:new String[]{"","0001","011004010203","0120080102030405060708","010000","09"}){
            try{AimeProtocol.cards(hex(bad));throw new AssertionError("Malformed card list accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        check("00123456789012345678".equals(AimeProtocol.accessCode(hex("00000000000000123456789012345678"))),"BCD preserves leading zero digits");
        check(AimeProtocol.accessCode(new byte[16])==null,"Empty card code rejected");
        check(AimeProtocol.accessCode(hex("000000000000fa123456789012345678"))==null,"Non decimal nibbles rejected");
        check(AimeProtocol.accessCode(new byte[15])==null,"Truncated block rejected");
        check(AimeProtocol.aimeMifareLayout(new byte[16]),"Arduino-Aime-Reader example permits empty block1");
        check(AimeProtocol.aimeMifareLayout(hex("53425344000000000000000000000000")),"Original SBSD layout remains supported");
        check(!AimeProtocol.aimeMifareLayout(hex("00004e42474943000000000000000000")),"Another brand's nonempty marker remains excluded");
        check(!AimeProtocol.aimeMifareLayout(null)&&!AimeProtocol.aimeMifareLayout(new byte[15]),"Missing or truncated marker is not a blank block");
        for(int i=0;i<16;i++){byte[] malformedMarker=new byte[16];malformedMarker[i]=1;check(AimeProtocol.aimeMifareLayout(malformedMarker),"Compatible card custom block1 is allowed; block2 is independently validated");}
        byte[] id=hex("0123456789abcdef"),read=AimeProtocol.felicaRead(id);
        check(Arrays.equals(AimeProtocol.felicaPoll(id),hex("0123456789abcdef0600ffff010f")),"Arduino-Aime-Reader addressed FeliCa Polling capture vector");
        byte[] detected=hex("0123456789abcdef00f1000000014300");
        byte[] poll=hex("14010123456789abcdef00f100000001430088b4");
        check(AimeProtocol.felicaSystem(poll,detected)==0x88b4,"segatools system code is big endian and includes IDm and PMm");
        for(int offset:new int[]{0,1,2,9,10,17}){byte[] changed=poll.clone();changed[offset]^=1;check(AimeProtocol.felicaSystem(changed,detected)==-1,"Malformed or another card's polling response rejected");}
        check(AimeProtocol.felicaSystem(Arrays.copyOf(poll,19),detected)==-1,"Truncated polling response rejected");
        check(Arrays.equals(AimeProtocol.felicaRead(id,0x82),hex("0123456789abcdef10060123456789abcdef010b00018082")),"Public read-only manufacturer ID block vector");
        check(AimeProtocol.felicaDfc(hex("0123456789abcdef0078000000000000"),id)==0x78,"Arcade Docs SEGA manufacturer DFC");
        check(AimeProtocol.felicaDfc(hex("0123456789abcdee0078000000000000"),id)==-1,"ID block must belong to the same detected card");
        for(int unsupported:new int[]{-1,1,0x80,0x86,0x100})try{AimeProtocol.felicaRead(id,unsupported);throw new AssertionError("Unexpected block read");}catch(IllegalArgumentException expected){checks++;}
        check(Arrays.equals(read,hex("0123456789abcdef10060123456789abcdef010b00018000")),"FeliCa read only service000B block0 command vector");
        byte[] felica=hex("1d070123456789abcdef000001893d691806eccfef90c8773d41eee231");
        byte[] encrypted=AimeProtocol.felicaBlock(felica,id);
        check(encrypted!=null,"FeliCa verified response selected");
        check("50101234567890123456".equals(AimeFelica.accessCode(encrypted)),"Synthetic vector from independent public forward tables decodes to genuine format");
        // Synthetic encrypted fixtures from the upstream forward tables; not real card data.
        check("50001234567890123456".equals(AimeFelica.accessCode(hex("75ae0e9a0531cfef90c82e2319068b31"))),"Limited-edition SEGA Aime issuer500 remains supported");
        String[][] excluded={
            {"510","6628766bd0eccfef9034b0310114f831"},
            {"520","8c3e6bddd0eccfef90c8c8d1f0979931"},
            {"530","965a08daeca7cfef90778f0339bbc631"},
            {"499","95fc73a847ff10ca903413f28b0c7b31"},
            {"502","7e66eab1ca5dafca90c877b8b0687331"},
            {"999","e33733b2ca5dafca9034aac7339f5931"}
        };
        for(String[] fixture:excluded){
            byte[] data=hex(fixture[1]);
            check((fixture[0]+"01234567890123456").equals(AimeProtocol.accessCode(AimeFelica.decode(data))),"Excluded fixture is valid BCD, not rejected because it is malformed");
            check(AimeFelica.accessCode(data)==null,"Only SEGA Aime issuer500/501 can pass the game handoff filter");
        }
        byte[] wrongId=id.clone();wrongId[7]^=1;check(AimeProtocol.felicaBlock(felica,wrongId)==null,"FeliCa reply for another card rejected");
        felica[10]=1;check(AimeProtocol.felicaBlock(felica,id)==null,"FeliCa error status rejected");felica[10]=0;felica[12]=2;
        check(AimeProtocol.felicaBlock(felica,id)==null,"Unexpected FeliCa block count rejected");
        byte[] rejected=hex("0c070123456789abcdefffa1");
        check(AimeProtocol.felicaReadError(rejected,id)==0xffa1,"Missing FeliCa service reports matching 12-byte error response without block count");
        check(AimeProtocol.felicaBlock(rejected,id)==null,"A FeliCa service error is never accepted as card data");
        check(AimeProtocol.felicaReadError(rejected,wrongId)==-1,"A different card's error cannot classify this card as unsupported");
        for(String bad:new String[]{"0d070123456789abcdefffa1","0c090123456789abcdefffa1","0c070123456789abcdef00a1","0b070123456789abcdefff","0d070123456789abcdefffa100"})
            check(AimeProtocol.felicaReadError(hex(bad),id)==-1,"Malformed, unrelated or zero-error FeliCa reply is not a valid service rejection");
        check(AimeProtocol.felicaReadError(hex("0c070123456789abcdef01a2"),id)==0x01a2,"Other FeliCa read statuses remain distinguishable from missing service");
        check(AimeFelica.accessCode(new byte[16])==null,"Arbitrary FeliCa memory never becomes fabricated access code");
        System.out.println("PASS: "+checks+" Aime protocol checks");
    }
}
