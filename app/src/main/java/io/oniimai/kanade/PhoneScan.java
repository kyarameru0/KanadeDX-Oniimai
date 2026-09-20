package io.oniimai.kanade;

/** Exactly one pending read, valid only in the still-current game scan. Main-thread owned. */
final class PhoneScan {
    private long sequence,pending,scan,deadline;
    long begin(long generation,long now){if(pending!=0)return 0;scan=generation;deadline=now+6000;return pending=++sequence;}
    long token(){return pending;}
    long generation(){return scan;}
    boolean expired(long now){return pending!=0&&now>=deadline;}
    boolean complete(long token,long generation,int game,long now){
        if(token==0||token!=pending)return false;
        boolean valid=generation==scan&&game==1&&now<deadline;pending=0;return valid;
    }
    void cancel(){pending=0;}
}
