package io.oniimai.kanade;

/** The controller's lights during first-run setup: what each step shows, and when setup's lighting applies. */
public final class SetupLightsTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static int brightness(int c){return (c>>>16&255)+(c>>>8&255)+(c&255);}
    public static void main(String[] args){
        SetupLights lights=new SetupLights();
        check(lights.frame(1000)==null&&lights.ceiling(1000)==-1&&!lights.active(),"nothing before setup opens");

        // Input check: a held button is white, a checked one blue, the rest barely lit.
        lights.update(SetupLights.INPUT,SetupLink.CONNECTED,1<<2,0b11,0,false,true,1000);
        int[] f=lights.frame(1100);
        check(f.length==8,"eight buttons");
        check(f[2]==SetupLights.WHITE,"held button is white");
        check(f[0]==SetupLights.scale(SetupLights.ACCENT,0.6f)&&f[1]==f[0],"checked buttons are blue");
        check(brightness(f[5])<brightness(f[0]),"unchecked buttons stay dim");
        lights.update(SetupLights.INPUT,SetupLink.CONNECTED,0,0xFF,1L<<20,true,true,1200);
        check(lights.frame(1300)[0]==SetupLights.scale(SetupLights.WHITE,0.45f),"touching lights the ring");

        // Lighting off in setup: dark, whatever the step.
        lights.update(SetupLights.LIGHTING,SetupLink.CONNECTED,0,0,0,false,false,2000);
        for(int c:lights.frame(5000))check(c==0,"lighting off is dark");

        // Lighting step: the preview colours, never brighter than the palette.
        lights.update(SetupLights.LIGHTING,SetupLink.CONNECTED,0,0,0,false,true,3000);
        for(long t:new long[]{3000,4200,6000,9000})for(int c:lights.frame(t)){
            check(c!=0,"preview lit at "+t);
            check(brightness(c)<=brightness(0xb07bff),"preview never brighter than the brightest palette colour");
        }
        check(!java.util.Arrays.equals(lights.frame(6000),lights.frame(7000)),"preview moves");

        // Connection: one light travels while looking; on connecting, a white fill that settles on the accent.
        lights.update(SetupLights.CONNECT,SetupLink.LOOKING,0,0,0,false,true,10000);
        int[] looking=lights.frame(10000);int brightest=0;
        for(int i=1;i<8;i++)if(brightness(looking[i])>brightness(looking[brightest]))brightest=i;
        check(brightest==0,"the travelling light starts at button 1");
        lights.update(SetupLights.CONNECT,SetupLink.CONNECTED,0,0,0,false,true,12000);
        check(lights.frame(12000+SetupLights.HOLD_MS-1)[7]==SetupLights.WHITE,"the fill reaches button 8");
        check(lights.frame(12000+SetupLights.SETTLE_MS)[0]==SetupLights.scale(SetupLights.ACCENT,0.3f),"the fill settles on the accent");
        lights.update(SetupLights.CONNECT,SetupLink.FAILED,0,0,0,false,true,14000);
        check(lights.frame(14000)[3]==SetupLights.scale(SetupLights.AMBER,0.2f),"failure is amber");

        // A new step restarts its timing; the same step does not.
        lights.update(SetupLights.DONE,SetupLink.CONNECTED,0,0,0,false,true,20000);
        check(lights.frame(20000)[0]==SetupLights.scale(SetupLights.ACCENT,0.3f),"done starts from the accent");
        lights.update(SetupLights.DONE,SetupLink.CONNECTED,0,0,0,false,true,20300);
        check(lights.frame(20300)[0]==SetupLights.WHITE,"same step keeps its timing");

        // Screen direction: a quiet steady light.
        lights.update(SetupLights.SCREEN,SetupLink.NOT_LOOKING,0,0,0,false,true,22000);
        for(int c:lights.frame(22500))check(c==SetupLights.scale(SetupLights.ACCENT,0.3f),"screen step is steady");
        lights.update(SetupLights.DONE,SetupLink.CONNECTED,0,0,0,false,true,20000);

        // The ceiling shows the average of the buttons.
        int[] now=lights.frame(25000);
        check(lights.ceiling(25000)==SetupLights.average(now),"ceiling is the average");
        check(SetupLights.average(new int[]{0xff0000,0x0000ff})==0x7f007f,"average per channel");

        // The final output: setup's colours replace the buttons; with setup's lighting off, the body, ring and side too.
        int[] game={0x111111,0x222222,0x333333,0x444444,0x555555,0x666666,0x777777,0x888888,128,64,32};
        int[] out=LedOutput.compose(game,null,null,false,-1);
        check(out[0]==0x111111&&out[8]==128&&out[9]==64&&out[10]==32,"the game's lights pass through");
        int[] welcome=new int[8];java.util.Arrays.fill(welcome,0x277af7);
        out=LedOutput.compose(game,welcome,null,false,-1);
        check(out[3]==0x277af7&&out[8]==128,"the welcome screen takes the buttons, the game keeps the body, ring and side");
        lights.update(SetupLights.LIGHTING,SetupLink.CONNECTED,0,0,0,false,false,40000);
        check(lights.dark(),"setup with its lighting off is dark");
        out=LedOutput.compose(game,welcome,lights.frame(40000),lights.dark(),-1);
        for(int c:out)check(c==0,"setup's lighting off darkens buttons, body, ring and side");
        lights.update(SetupLights.LIGHTING,SetupLink.CONNECTED,0,0,0,false,true,41000);
        check(!lights.dark(),"setup with its lighting on is not dark");
        out=LedOutput.compose(game,welcome,lights.frame(41000),lights.dark(),-1);
        check(out[0]!=0x277af7&&out[8]==128&&out[9]==64&&out[10]==32,"setup's colours take the buttons, the game keeps the rest");
        out=LedOutput.compose(game,welcome,lights.frame(41000),true,0xff0000);
        for(int c:out)check(c==0xff0000,"a colour test fills everything");

        lights.close();
        check(!lights.dark(),"closed setup is not dark");
        check(lights.frame(30000)==null&&lights.ceiling(30000)==-1,"closed setup leaves the lights to the game");
        System.out.println("PASS: "+checks+" setup lighting checks");
    }
}
