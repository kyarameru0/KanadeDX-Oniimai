package io.oniimai.kanade;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Compact rectangular MIUI-style settings components. */
final class Miui {
    static final int BG=GameUi.PAGE, CARD=GameUi.PAPER, INK=GameUi.INK, MUTED=GameUi.MUTED,
        BLUE=GameUi.BLUE, PALE=GameUi.PALE, LINE=GameUi.LINE, GREEN=GameUi.GREEN;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    static TextView label(Context c,String value,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);t.setTypeface(bold?GameAssets.semibold(c):GameAssets.regular(c));return t;
    }
    static void gap(LinearLayout parent,int height){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));}
    static LinearLayout card(LinearLayout parent,String title){
        Context c=parent.getContext();LinearLayout card=column(c);card.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));card.setBackground(shape(c,CARD,22));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(c,12);parent.addView(card,lp);
        if(title!=null&&!title.isEmpty()){card.addView(label(c,title,17,INK,true));gap(card,12);}return card;
    }
    static void note(LinearLayout parent,String text){
        TextView t=label(parent.getContext(),text,12,MUTED,false);t.setLineSpacing(dp(parent.getContext(),3),1);parent.addView(t);gap(parent,8);
    }
    static Button action(Context c,String title,boolean primary,Runnable run){
        Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:BLUE);
        b.setTypeface(GameAssets.font(c));b.setIncludeFontPadding(false);if(primary)b.setTextColor(GameUi.ON_ACCENT);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));b.setElevation(0);b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007aff),shape(c,primary?BLUE:PALE,16),null));
        b.setOnClickListener(v->run.run());return b;
    }
    static Button addAction(LinearLayout parent,String title,boolean primary,Runnable run){
        Context c=parent.getContext();Button b=action(c,title,primary,run);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(c,48));lp.bottomMargin=dp(c,8);parent.addView(b,lp);return b;
    }
    static void segment(LinearLayout parent,String[] labels,int chosen,java.util.function.IntConsumer pick){
        Context c=parent.getContext();LinearLayout row=row(c);row.setPadding(dp(c,4),dp(c,4),dp(c,4),dp(c,4));row.setBackground(shape(c,LINE,17));
        for(int i=0;i<labels.length;i++){final int index=i;Button b=action(c,labels[i],i==chosen,()->pick.accept(index));
            if(i!=chosen){b.setBackground(shape(c,Color.TRANSPARENT,13));b.setTextColor(MUTED);}row.addView(b,new LinearLayout.LayoutParams(0,dp(c,40),1));}
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));gap(parent,12);
    }
}
