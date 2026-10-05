package io.oniimai.kanade;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LobbyLightsTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static int brightest(int[] colors){int best=0;for(int i=1;i<8;i++)if((colors[i]&255)>(colors[best]&255))best=i;return best;}
    public static void main(String[] args)throws Exception{
        LobbyLights lights=new LobbyLights();
        check(lights.frame(0)==null&&lights.still()==null,"no lobby, no override");
        lights.pressed(0);check(lights.frame(0)==null,"a press while hidden is ignored");

        lights.mode(LobbyLights.LOADING,1000);
        int[] frame=lights.frame(1000);
        check(frame.length==8&&frame[0]==LobbyLights.ACCENT,"loading light starts at button 1");
        check(frame[1]==LobbyLights.scale(LobbyLights.ACCENT,LobbyLights.FLOOR),"buttons ahead of the light stay faint");
        check((frame[7]&255)>(frame[1]&255)&&(frame[7]&255)<(frame[0]&255),"a tail follows the light");
        check(brightest(lights.frame(1000+LobbyLights.STEP_MS))==1&&brightest(lights.frame(1000+3*LobbyLights.STEP_MS))==3,"light moves clockwise");
        check(brightest(lights.frame(1000+8*LobbyLights.STEP_MS))==0,"light wraps around the ring");

        lights.mode(LobbyLights.READY,5000);
        int floor=LobbyLights.scale(LobbyLights.ACCENT,0.55f)&255;
        for(long t=5000;t<5000+LobbyLights.WAVE_MS;t+=100)for(int color:lights.frame(t))check((color&255)>=floor-1,"every button stays lit while Start is available");
        check(lights.frame(5000)[0]==LobbyLights.ACCENT&&brightest(lights.frame(5000+LobbyLights.WAVE_MS/4))==2,"ready wave runs clockwise");
        check(Arrays.equals(lights.still(),new int[]{LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f),LobbyLights.scale(LobbyLights.ACCENT,0.8f)}),"static stand-in");

        // Start from a controller: the game leaves its start screen, READY drops to LOADING.
        lights.mode(LobbyLights.LOADING,9000);
        for(int color:lights.frame(9000))check(color==LobbyLights.FLASH,"accepted start flashes every button");
        int[] fading=lights.frame(9000+LobbyLights.FLASH_MS/2);check((fading[3]>>>16&255)<255&&(fading[3]>>>16&255)>(LobbyLights.ACCENT>>>16),"flash fades out");
        check(lights.frame(9000+LobbyLights.FLASH_MS)[0]!=LobbyLights.FLASH,"flash ends");

        // Start from the screen: one flash at the press, none again when the game then leaves READY.
        lights.mode(LobbyLights.READY,20000);lights.pressed(20100);
        check(lights.frame(20100)[5]==LobbyLights.FLASH,"on-screen press flashes");
        lights.mode(LobbyLights.LOADING,20500);
        check((lights.frame(20600)[4]>>>16&255)<128,"no second flash right after an on-screen press");
        lights.mode(LobbyLights.READY,30000);lights.mode(LobbyLights.LOADING,30000+LobbyLights.REPEAT_MS+500);
        check(lights.frame(30000+LobbyLights.REPEAT_MS+500)[0]==LobbyLights.FLASH,"a later start flashes again");

        lights.mode(LobbyLights.OFF,40000);check(lights.frame(40000)==null,"closing the lobby returns the buttons to the game");
        lights.mode(LobbyLights.LOADING,41000);check(lights.frame(41000)[2]!=LobbyLights.FLASH,"a new lobby does not inherit an old press");
        // Hand-off to the game: white sweeps from button 1, holds, fades out, then the game's lighting returns.
        lights.mode(LobbyLights.LOADING,49000);int[] before=lights.frame(50000);
        lights.complete(50000);
        check(Arrays.equals(lights.frame(50000),before),"hand-off starts from the pattern on show");
        check((lights.frame(50060)[0]>>>16&255)>(lights.frame(50060)[7]>>>16&255)+60,"white fills from button 1");
        for(int color:lights.frame(50000+LobbyLights.HOLD_END_MS-60))check(color==LobbyLights.FLASH,"whole ring white before the fade");
        lights.pressed(50300);lights.mode(LobbyLights.OFF,50300);
        check(lights.frame(50000+LobbyLights.HOLD_END_MS-60)[3]==LobbyLights.FLASH,"closing the screen does not cut the hand-off short");
        int dimming=lights.frame(50800)[4]>>>16&255;check(dimming>0&&dimming<255,"white fades out");
        check(lights.frame(50000+LobbyLights.COMPLETE_MS)==null,"game lighting returns after the hand-off");
        lights.mode(LobbyLights.OFF,51200);check(lights.mode()==LobbyLights.OFF,"closed once the hand-off has run");
        lights.complete(52000);check(lights.frame(52000)==null,"no hand-off without a welcome screen");
        lights.mode(LobbyLights.READY,53000);lights.complete(53000);
        check(lights.frame(53000)[0]==LobbyLights.ACCENT,"hand-off from ready starts from the ready wave");
        lights.mode(LobbyLights.LOADING,54500);check(lights.frame(54500)[0]!=LobbyLights.FLASH&&lights.mode()==LobbyLights.LOADING,"a new lobby can replace a hand-off");
        // The screen samples the same pattern between buttons.
        lights.mode(LobbyLights.READY,60000);
        for(int i=0;i<8;i++)check(lights.at(61234,i)==lights.frame(61234)[i],"ring positions at the buttons match the LEDs");
        int left=lights.at(61234,2)&255,mid=lights.at(61234,2.5)&255,right=lights.at(61234,3)&255;
        check(mid>=Math.min(left,right)-1&&mid<=Math.max(left,right)+1,"the ring blends between neighbouring buttons");
        check(lights.at(61234,8.25)==lights.at(61234,0.25)&&lights.at(61234,-0.5)==lights.at(61234,7.5),"positions wrap round the ring");
        lights.mode(LobbyLights.OFF,62000);check(lights.at(62000,1)==0&&!lights.active(62000),"dark when inactive");
        check(LobbyLights.mix(0x000000,0xffffff,0.5f)==0x808080&&LobbyLights.scale(0xffffff,2)==0xffffff&&LobbyLights.scale(0xffffff,-1)==0,"colour helpers clamp");

        // The LED worker: lobby colours replace only the game's button colours, then hand them back.
        I18n.language("en");
        List<byte[]> packets=Collections.synchronizedList(new ArrayList<byte[]>());
        UsbIo.Cdc.opened=port->{port.onWrite=encoded->{
            byte[] p=LedTest.decode(encoded);packets.add(p);
            if((p[3]&255)!=0x7d){byte[] data=(p[3]&255)==0xf0?"15070-04xx".getBytes(StandardCharsets.US_ASCII):new byte[0];port.receive(LedTest.ack(p[0]&255,p[3]&255,1,1,data));}
        };};
        LobbyLights shared=new LobbyLights();
        LedOutput output=new LedOutput(new UsbManager(),true,shared);
        try{
            output.settings(100,0,false,true);output.start(new UsbIo.Port(),17,0);
            LedOutputTest.await(()->LedOutputTest.rgb(packets,0)==0xff0000&&LedOutputTest.rgb(packets,7)==0xffffff,"game colours before the lobby");
            shared.mode(LobbyLights.READY,SystemClock.uptimeMillis());
            LedOutputTest.await(()->{int c=LedOutputTest.rgb(packets,0);return c!=0xff0000&&(c&255)>(c>>>16&255);},"lobby blue replaces the game's button colours");
            LedOutputTest.await(()->LedOutputTest.has(packets,0x39),"cabinet channels still sent");
            check((LedOutputTest.last(packets,0x39)[4]&255)==128,"cabinet FET keeps the game's value");
            // Before the game sends lighting, the lobby pattern is what the board shows: report it as linked.
            NativeBridge.snapshot[0]=0;
            LedOutputTest.await(()->output.summary().startsWith(I18n.t(Msg.LED_STATUS_LINKED)+"\n"),"linked while the lobby drives the buttons");
            shared.mode(LobbyLights.OFF,SystemClock.uptimeMillis());
            LedOutputTest.await(()->output.summary().startsWith(I18n.t(Msg.LED_STATUS_WAITING_GAME)),"waiting for the game again once the lobby closes");
            NativeBridge.snapshot[0]=255;
            LedOutputTest.await(()->LedOutputTest.rgb(packets,0)==0xff0000&&LedOutputTest.rgb(packets,7)==0xffffff,"game colours return when the lobby closes");
            shared.mode(LobbyLights.LOADING,SystemClock.uptimeMillis());
            LedOutputTest.await(()->LedOutputTest.rgb(packets,0)!=0xff0000,"lobby shown again");
            shared.complete(SystemClock.uptimeMillis());shared.mode(LobbyLights.OFF,SystemClock.uptimeMillis());
            LedOutputTest.await(()->LedOutputTest.rgb(packets,0)==0xffffff&&LedOutputTest.rgb(packets,7)==0xffffff,"hand-off lights every button white after the screen closed");
            LedOutputTest.await(()->LedOutputTest.rgb(packets,0)==0xff0000&&LedOutputTest.rgb(packets,7)==0xffffff,"game colours return after the hand-off");
        }finally{output.destroy();NativeBridge.snapshot[0]=255;}
        System.out.println("PASS: "+checks+" welcome-screen button lighting checks");
    }
}
