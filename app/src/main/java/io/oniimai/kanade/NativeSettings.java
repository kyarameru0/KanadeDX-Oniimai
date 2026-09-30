package io.oniimai.kanade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** UI descriptions only. Callbacks stay in GameSession, next to the hardware state they control. */
final class NativeSettings {
    /** Live-status tones: the settings screen tints the status panel so state reads at a glance. */
    static final int NONE=-1, INFO=0, OK=1, WAIT=2;
    static final class Row {
        final String title, summary;
        final Runnable action;
        final Consumer<Boolean> toggle;
        final boolean checked;
        final int tone;
        Row(String title,String summary,Runnable action,Consumer<Boolean> toggle,boolean checked,int tone){
            this.title=title;this.summary=summary;this.action=action;this.toggle=toggle;this.checked=checked;this.tone=tone;
        }
    }
    static final class Group {
        final String title;
        final List<Row> rows=new ArrayList<>();
        Group(String title){this.title=title;}
        void row(String title,String summary,Runnable action){rows.add(new Row(title,summary,action,null,false,NONE));}
        void note(String text){rows.add(new Row("",text,null,null,false,NONE));}
        /** Live state, shown at the top of the group whatever its position; the first line is the headline. */
        void status(String text,int tone){rows.add(new Row("",text,null,null,false,tone));}
        void toggle(String title,String summary,boolean checked,Consumer<Boolean> action){rows.add(new Row(title,summary,null,action,checked,NONE));}
    }
}
