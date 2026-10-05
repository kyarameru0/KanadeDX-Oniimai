package io.oniimai.kanade;

/** First-run setup's connection state: partial connections, refused permission, and the display poll. */
public final class SetupLinkTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args){
        int both=SetupLink.TOUCH|SetupLink.BUTTONS;
        int io4=SetupLink.expected(false,2,false);
        check(io4==both,"IO4 setup expects touch and buttons");
        check(SetupLink.expected(true,2,false)==SetupLink.BUTTONS,"touch turned off is not expected");
        check(SetupLink.expected(false,2,true)==SetupLink.TOUCH,"IO4 port turned off is not expected");
        check(SetupLink.expected(false,0,false)==SetupLink.TOUCH,"no button input expects touch only");
        check(SetupLink.expected(false,1,true)==both,"keyboard buttons do not need the IO4 port");

        check(SetupLink.open(false,true,true,2)==0,"nothing is open before connecting");
        check(SetupLink.open(true,true,true,2)==both,"both channels open");
        check(SetupLink.open(true,false,true,2)==SetupLink.BUTTONS,"touch failed to open: buttons only");
        check(SetupLink.open(true,true,false,2)==SetupLink.TOUCH,"IO4 missing: touch only");
        check(SetupLink.open(true,true,true,0)==SetupLink.TOUCH,"an IO4 port opened for lighting is not button input");
        check(SetupLink.open(true,true,false,1)==both,"keyboard buttons count once connected");

        // Touch failed to open but IO4 did: the session is connected, yet setup must not call it complete.
        int open=SetupLink.open(true,false,true,2);
        check(SetupLink.state(true,open,io4,true,false,false)==SetupLink.PARTIAL,"buttons without touch is partial");
        check(SetupLink.state(true,SetupLink.open(true,true,false,2),io4,true,false,false)==SetupLink.PARTIAL,"touch without buttons is partial");
        check(SetupLink.state(true,both,io4,true,false,false)==SetupLink.CONNECTED,"everything expected is open");
        check(SetupLink.state(true,SetupLink.TOUCH,SetupLink.expected(false,2,true),false,false,false)==SetupLink.CONNECTED,"touch alone is complete when IO4 is off");
        check(SetupLink.state(true,0,io4,true,false,false)==SetupLink.PARTIAL,"connected with no input open is partial");
        check(SetupLink.state(true,both,io4,false,false,false)==SetupLink.CONNECTED,"already connected when setup reopens");
        check(SetupLink.state(false,0,io4,false,false,false)==SetupLink.NOT_LOOKING,"setup has not reached the connection step");
        check(SetupLink.state(false,0,io4,true,false,false)==SetupLink.LOOKING,"looking");
        check(SetupLink.state(false,0,io4,true,true,false)==SetupLink.PERMISSION,"waiting for permission");
        check(SetupLink.state(false,0,io4,true,true,true)==SetupLink.PERMISSION,"a new permission request outranks an old failure");
        check(SetupLink.state(false,0,io4,true,false,true)==SetupLink.FAILED,"refused or failed");

        // The first question is always asked, whatever the uptime; then at most once per period.
        for(long now:new long[]{0L,1L,1000L,86400000L,Long.MAX_VALUE}){
            SetupLink.Poll poll=new SetupLink.Poll(1000);
            check(poll.due(now),"first poll at "+now);
            check(!poll.due(now),"same instant at "+now);
        }
        SetupLink.Poll poll=new SetupLink.Poll(1000);
        check(poll.due(5000),"first");
        check(!poll.due(5999),"inside the period");
        check(poll.due(6000),"period elapsed");
        check(!poll.due(6500),"inside the next period");
        check(poll.due(60000),"long gap");
        System.out.println("PASS: "+checks+" setup connection-state checks");
    }
}
