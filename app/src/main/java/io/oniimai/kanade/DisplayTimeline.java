package io.oniimai.kanade;

import java.util.ArrayDeque;

/**
 * Bounded, time-stamped record of external-output lifecycle events for the diagnostics copy.
 * Times are milliseconds since the game session started, so they line up with logcat and tombstones.
 * Entries hold only state names, sizes and durations: no card data or device serials.
 */
final class DisplayTimeline {
    static final int CAPACITY=48;
    private final long origin;
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    DisplayTimeline(long origin){this.origin=origin;}
    synchronized String add(long now,String event){
        String line="+"+(now-origin)+"ms "+event;
        if(entries.size()==CAPACITY)entries.removeFirst();
        entries.addLast(line);return line;
    }
    synchronized String dump(){
        StringBuilder b=new StringBuilder();
        for(String line:entries){if(b.length()>0)b.append('\n');b.append(line);}
        return b.toString();
    }
    synchronized int size(){return entries.size();}
}
