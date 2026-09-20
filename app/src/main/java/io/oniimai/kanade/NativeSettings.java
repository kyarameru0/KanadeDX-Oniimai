package io.oniimai.kanade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** UI descriptions only. Callbacks stay in GameSession, next to the hardware state they control. */
final class NativeSettings {
    static final class Row {
        final String title, summary;
        final Runnable action;
        final Consumer<Boolean> toggle;
        final boolean checked;
        Row(String title,String summary,Runnable action,Consumer<Boolean> toggle,boolean checked){
            this.title=title;this.summary=summary;this.action=action;this.toggle=toggle;this.checked=checked;
        }
    }
    static final class Group {
        final String title;
        final List<Row> rows=new ArrayList<>();
        Group(String title){this.title=title;}
        void row(String title,String summary,Runnable action){rows.add(new Row(title,summary,action,null,false));}
        void note(String text){rows.add(new Row("",text,null,null,false));}
        void toggle(String title,String summary,boolean checked,Consumer<Boolean> action){rows.add(new Row(title,summary,null,action,checked));}
    }
}
