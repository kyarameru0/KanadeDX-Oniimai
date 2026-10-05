package io.oniimai.kanade;

/**
 * What first-run setup says about the controller. It is worked out from the channels that really opened,
 * not from the ports that were chosen, so a connection that is missing touch or buttons never reads as
 * complete.
 */
final class SetupLink {
    static final int NOT_LOOKING=-1,LOOKING=0,PERMISSION=1,CONNECTED=2,FAILED=3,PARTIAL=4;
    static final int TOUCH=1,BUTTONS=2;

    private SetupLink(){}

    /**
     * The inputs open right now: touch when its port opened, buttons when the IO4 port opened (button mode 2)
     * or, with USB keyboard buttons (mode 1), once the controller is connected.
     */
    static int open(boolean connected,boolean touchOpen,boolean hidOpen,int buttonMode){
        if(!connected)return 0;
        return (touchOpen?TOUCH:0)|((buttonMode==2&&hidOpen)||buttonMode==1?BUTTONS:0);
    }

    /** The inputs the current settings ask for; a port turned off in Settings is not expected. */
    static int expected(boolean touchOff,int buttonMode,boolean hidOff){
        return (touchOff?0:TOUCH)|(buttonMode==1||(buttonMode==2&&!hidOff)?BUTTONS:0);
    }

    /** One of the states above. A connection counts as complete only when every expected input is open. */
    static int state(boolean connected,int open,int expected,boolean searching,boolean permissionPending,boolean refusedOrFailed){
        if(connected)return (open&expected)==expected?CONNECTED:PARTIAL;
        if(!searching)return NOT_LOOKING;
        if(permissionPending)return PERMISSION;
        return refusedOrFailed?FAILED:LOOKING;
    }

    /** Lets a slow question through at most once per period; the first call always goes through. */
    static final class Poll{
        private final long period;
        private boolean asked;
        private long at;
        Poll(long period){this.period=period;}
        boolean due(long now){
            if(asked&&now-at<period)return false;
            asked=true;at=now;return true;
        }
    }
}
