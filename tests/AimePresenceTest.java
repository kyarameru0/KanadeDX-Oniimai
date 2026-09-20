package io.oniimai.kanade;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Synthetic values are used only in this offline test; never submitted to a game/server. */
public final class AimePresenceTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        String first="01234567890123456789",second="98765432109876543210";
        check(AimePresence.bcd(null)==null,"missing code rejected");
        check(AimePresence.bcd("0123")==null,"truncated code rejected");
        check(AimePresence.bcd("00000000000000000000")==null,"zero code rejected");
        check(AimePresence.bcd("0123456789012345678A")==null,"hex digit is not decimal BCD");
        check(AimePresence.bcd("0123456789012345678１")==null,"non-ASCII digit rejected");
        check(Arrays.equals(AimePresence.bcd(first),new byte[]{1,0x23,0x45,0x67,(byte)0x89,1,0x23,0x45,0x67,(byte)0x89}),"20 digits packed in card order");
        AimePresence state=new AimePresence();AtomicInteger accepted=new AtomicInteger();AtomicReference<byte[]> reference=new AtomicReference<>();
        Predicate<byte[]> submit=bytes->{reference.set(bytes);accepted.incrementAndGet();return true;};
        check(!state.observe(true,first,0,false,submit)&&accepted.get()==0,"card outside scanning screen is not submitted");
        check(!state.observe(true,first,100,true,bytes->false),"rejected queue does not consume presentation");
        check(state.observe(true,first,200,true,submit)&&accepted.get()==1,"card delivered when game begins reading");
        check(Arrays.equals(reference.get(),new byte[10]),"temporary submission buffer erased");
        for(int i=0;i<100;i++)check(!state.observe(true,first,300+i*180,true,submit),"held card deduplicated");
        state.observe(false,null,20000,false,submit);
        check(!state.observe(true,first,20100,true,submit),"brief RF dropout does not repeat login");
        state.observe(false,null,20200,false,submit);state.observe(false,null,20500,false,submit);
        check(state.observe(true,first,20600,true,submit)&&accepted.get()==2,"same card rearmed after confirmed removal");
        check(state.observe(true,second,20700,true,submit)&&accepted.get()==3,"different card can replace previous presentation");
        check(!state.observe(true,null,20800,true,submit),"unsupported card is never submitted");
        check(!state.observe(true,second,20900,true,submit),"read failure cannot clear held-card latch");
        state.clear();check(state.observe(true,first,21000,true,submit),"explicit fresh session clears presentation");
        state.clear();
        System.out.println("PASS: "+checks+" Aime card-presence/deduplication checks");
    }
}
