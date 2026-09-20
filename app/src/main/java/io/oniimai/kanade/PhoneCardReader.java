package io.oniimai.kanade;

import java.io.IOException;
import java.util.Arrays;

/** Read-only card operations, independent of the Android tag/lifecycle transport. */
final class PhoneCardReader {
    interface Felica { byte[] exchange(byte[] command) throws IOException; }
    interface Classic {
        boolean authenticate(boolean keyB, byte[] key) throws IOException;
        byte[] read(int block) throws IOException;
    }
    static final class Result {
        final byte[] code;
        final int issue;
        Result(byte[] code,int issue){this.code=code;this.issue=issue;}
    }
    static Result failure(int issue){return new Result(null,issue);}
    static Result decoded(String code){
        byte[] bcd=AimePresence.bcd(code);
        return bcd==null?failure(AimeChannel.INVALID_CARD):new Result(bcd,AimeChannel.NONE);
    }
    static Result classic(Classic card)throws IOException{
        // Same documented Aime sector keys as the working controller reader.
        if(!card.authenticate(true,new byte[]{0x57,0x43,0x43,0x46,0x76,0x32})&&
           !card.authenticate(false,new byte[]{0x60,(byte)0x90,(byte)0xd0,6,0x32,(byte)0xf5}))
            return failure(AimeChannel.UNSUPPORTED_TYPE);
        if(!AimeProtocol.aimeMifareLayout(card.read(1)))return failure(AimeChannel.INVALID_CARD);
        return decoded(AimeProtocol.accessCode(card.read(2)));
    }
    static Result felica(Felica card)throws IOException{
        // Android takes the NFC-F frame directly, without the USB reader's IDm prefix.
        // Select Lite-S explicitly: Android may initially discover a different system.
        byte[] poll=card.exchange(new byte[]{6,0,(byte)0x88,(byte)0xb4,1,0});
        if(poll==null||poll.length!=20)return failure(AimeChannel.READ_FAILED);
        byte[] identity=Arrays.copyOfRange(poll,2,18);
        int system=AimeProtocol.felicaSystem(poll,identity);
        if(system<0)return failure(AimeChannel.READ_FAILED);
        if(system!=0x88b4)return failure(AimeChannel.UNSUPPORTED_TYPE);
        byte[] id=Arrays.copyOf(identity,8);
        byte[] block=readFelica(card,id,0x82);
        if(block==null)return failure(AimeChannel.READ_FAILED);
        int dfc=AimeProtocol.felicaDfc(block,id);
        if(dfc<0)return failure(AimeChannel.READ_FAILED);
        if(dfc!=0x78)return failure(AimeChannel.UNSUPPORTED_TYPE);
        block=readFelica(card,id,0);
        return block==null?failure(AimeChannel.READ_FAILED):decoded(AimeFelica.accessCode(block));
    }
    private static byte[] readFelica(Felica card,byte[] id,int block)throws IOException{
        byte[] request=AimeProtocol.felicaRead(id,block);
        return AimeProtocol.felicaBlock(card.exchange(Arrays.copyOfRange(request,8,request.length)),id);
    }
}
