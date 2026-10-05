package io.oniimai.kanade;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/**
 * Native external welcome screen in the cabinet's portrait layout (1080x1920 logical units):
 * a 1080x450 top panel, then the main circle at (540,1380), r=540. Everything outside those two
 * areas is behind the bezel.
 *
 * Deep charcoal openings and warm white type. The top panel follows a console home screen: a status bar
 * (mark, name, time), one highlighted game tile whose icon is a miniature of the light ring and whose
 * text carries the input and LED status, and a footer with the phone note and version. The circle
 * holds one thin ring of light just inside its edge
 * that carries the cabinet button LEDs ({@link LobbyLights}) and spills softly onto the circle: the light
 * travels round it while the game loads, the whole ring breathes when Start is available, and it fills
 * white when the game takes over the monitor. Faint ripples spread from the centre towards the ring,
 * clearer once Start is available, and a white ripple answers Start and the hand-off. Otherwise motion
 * is short: the ring draws itself in, text eases up, states cross-fade and Start gives press feedback. With system animations off every
 * transition is instant and the ring is static.
 */
final class CabinetLobbyView extends View {
    private static final int TILE=0xff1b1d21,TILE_ICON=0xff0f1012,TILE_EDGE=0xff2a2d33,SURFACE=0xff121315,TEXT=0xffedeae4,TEXT_2=0xff8f8a83,TEXT_3=0xff5c5853,INK=0xff121212,TRACK=0xff24262a;
    private static final int OK=0xff5ad08f,WAIT=0xfff2b45a,IDLE=0xff5c5853;
    private static final int BUTTON=TEXT,BUTTON_PRESSED=0xffcfcac2,BUTTON_EDGE=0xff2e2c2a;
    private static final float LEFT=72,RIGHT=1008,CX=540,CY=1380,R=540,RING_R=476;
    /** Ambient light from the ring: alpha at each radius (as a fraction of R), brightest at the ring itself. */
    private static final float[] SPILL_AT={0,0.5f,0.75f,RING_R/R,1},SPILL={0.04f,0.06f,0.12f,0.24f,0.1f};
    /** Colour samples round the light ring; a sweep gradient blends between them without seams. */
    private static final int SAMPLES=72;
    /** Ripples: three in flight, each crossing from RIPPLE_FROM to the ring in RIPPLE_MS; a start ripple takes BURST_MS. */
    private static final float RIPPLE_FROM=150;
    private static final long RIPPLE_MS=3600,BURST_MS=900;
    private static final long INTRO_MS=520,CIRCLE_DELAY=120,RING_DELAY=200,RING_DRAW_MS=800,FADE_MS=400,PRESS_MS=90;
    /** Hand-off length: DisplayOutput switches to the game once this has run, if the game is still ready. */
    static final long EXIT_MS=560;

    private final DashboardHost host;
    private final LobbyLights lights;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix transform=new Matrix(),inverse=new Matrix();
    private final RectF tile=new RectF(56,104,1024,352),tileIcon=new RectF(80,128,280,328),start=new RectF(320,1336,760,1436),ring=new RectF(CX-RING_R,CY-RING_R,CX+RING_R,CY+RING_R),scratch=new RectF();
    private final int[] light=new int[SAMPLES+1],stops=new int[SAMPLES+1];
    private final float[] positions=new float[SAMPLES+1];
    private final int[] spill=new int[SPILL.length];
    /** The ring's light spill changes only with the ring's average colour, so its shader is kept until then. */
    private Shader spillShader;
    private int spillColor=-1;
    /** Halo then line: stacked translucent strokes fall off like a soft glow without a blur. */
    private static final float[] RING_WIDTHS={40,28,18,10,3},RING_ALPHA={0.045f,0.045f,0.045f,0.05f,1},ICON_WIDTHS={16,5},ICON_ALPHA={0.14f,1};
    private final Fade titleFade=new Fade(),buttonFade=new Fade(),pressFade=new Fade();
    private boolean clockwise,ready,pressed,finishing;
    private long requestedAt=-1,shownAt=-1,finishAt=-1;
    /** Multiplies the alpha of everything drawn; used by the entrance, the exit and the cross-fades. */
    private float alpha=1;
    private String input="",led="";
    /** Wall time for the clock; host previews substitute a fixed time. */
    static java.util.function.LongSupplier wallClock=System::currentTimeMillis;
    /** The clock is formatted once a minute (or when the language changes), not every frame. */
    private long clockMinute=-1;
    private java.util.Locale clockLocale;
    private String clockTime="";
    private int inputTone,ledTone;

    CabinetLobbyView(Context context,DashboardHost host,boolean clockwise){
        super(context);this.host=host;this.clockwise=clockwise;GameAssets.bind(host.assetContext());
        LobbyLights shared=host.lobbyLights();lights=shared!=null?shared:new LobbyLights();
        setClickable(true);GameUi.buttonRole(this);
        if(android.os.Build.VERSION.SDK_INT>=29)setForceDarkAllowed(false);
        for(int k=0;k<=SAMPLES;k++)positions[k]=k/(float)SAMPLES;
        update(false);
    }
    void rotation(boolean value){clockwise=value;invalidate();}
    void update(boolean canStart){
        ready=canStart;input=host.connectionDescription();led=host.ledDescription();inputTone=host.inputTone();ledTone=host.ledTone();
        // The cabinet LEDs follow the lobby only while it is on screen, and not during the hand-off.
        if(isAttachedToWindow()&&!finishing)lights.mode(ready?LobbyLights.READY:LobbyLights.LOADING,SystemClock.uptimeMillis());
        setContentDescription(I18n.t(Msg.DISPLAY_LOBBY_STATUS)+". "+I18n.t(ready?Msg.DISPLAY_LOBBY_START:Msg.DISPLAY_LOBBY_LOADING));
        invalidate();
    }
    /**
     * The game is ready for the monitor: fill the ring (and the cabinet buttons) white and fade this screen
     * to black. {@code false} cancels a hand-off whose game stopped being ready, and restores the lobby.
     */
    void finish(boolean value){
        if(value==finishing)return;
        finishing=value;long now=SystemClock.uptimeMillis();
        if(value){finishAt=now;pressed=false;lights.complete(now);}
        else{finishAt=-1;if(isAttachedToWindow())lights.mode(ready?LobbyLights.READY:LobbyLights.LOADING,now);}
        invalidate();
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();shownAt=SystemClock.uptimeMillis();
        lights.mode(ready?LobbyLights.READY:LobbyLights.LOADING,shownAt);
    }
    @Override protected void onDetachedFromWindow(){
        // A running white hand-off keeps going on the cabinet buttons; LobbyLights ends it by itself.
        lights.mode(LobbyLights.OFF,SystemClock.uptimeMillis());
        super.onDetachedFromWindow();
    }
    private boolean enabledStart(){return ready&&!finishing&&(requestedAt<0||SystemClock.uptimeMillis()-requestedAt>1500);}
    private static boolean motion(){return android.animation.ValueAnimator.areAnimatorsEnabled();}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.BLACK);
        long now=SystemClock.uptimeMillis();boolean motion=motion();boolean enabled=enabledStart();
        float title=titleFade.value(ready,now,FADE_MS,motion);
        float button=buttonFade.value(enabled,now,FADE_MS,motion);
        float press=pressFade.value(pressed&&enabled,now,PRESS_MS,motion);
        // Exit: text and button leave first, then the white ring fades to black.
        float exit=!finishing?0:!motion?1:Math.min(1,(now-finishAt)/(float)EXIT_MS);
        float content=1-ease(Math.min(1,exit/0.4f)),shell=1-ease(Math.max(0,(exit-0.55f)/0.45f));
        transform.setValues(DisplayGeometry.surfaceMatrix(getWidth(),getHeight(),clockwise));
        int save=canvas.save();canvas.concat(transform);
        if(shell>0){
            boolean lit=sample(now,motion);
            drawSurfaces(canvas,now,motion,lit,shell);
            drawRipples(canvas,now,motion,title,shell);
            drawPanel(canvas,now,motion,shell*content,title,lit);
            drawRing(canvas,now,motion,lit,shell);
            drawCircle(canvas,now,motion,title,button,press,shell*content);
        }
        canvas.restoreToCount(save);
        alpha=1;
        // Next frame on the display's vsync: even pacing, one request per drawn frame (state updates only invalidate).
        if(motion&&isAttachedToWindow()&&shell>0)postInvalidateOnAnimation();
    }

    /** The two openings in deep charcoal; the ring's light spills onto the circle, strongest at its edge. */
    private void drawSurfaces(Canvas c,long now,boolean motion,boolean lit,float visible){
        alpha=intro(now,0,INTRO_MS,motion)*visible;fill(SURFACE);c.drawRect(0,0,1080,450,pen);
        alpha=intro(now,CIRCLE_DELAY,INTRO_MS,motion)*visible;fill(SURFACE);c.drawCircle(CX,CY,R,pen);
        if(!lit)return;
        int r=0,g=0,b=0;
        for(int k=0;k<SAMPLES;k++){r+=light[k]>>>16&255;g+=light[k]>>>8&255;b+=light[k]&255;}
        int glow=(r/SAMPLES)<<16|(g/SAMPLES)<<8|(b/SAMPLES);
        if(glow!=spillColor||spillShader==null){
            for(int i=0;i<SPILL.length;i++)spill[i]=Math.round(255*SPILL[i])<<24|glow;
            spillShader=new RadialGradient(CX,CY,R,spill,SPILL_AT,Shader.TileMode.CLAMP);spillColor=glow;
        }
        pen.setShader(spillShader);pen.setColor(tint(Color.WHITE));
        c.drawCircle(CX,CY,R,pen);pen.setShader(null);
    }

    /**
     * Ripples spread from the centre and fade before the ring, eased out like water. They are faint while
     * the game loads and clearer when Start is available; a white ripple follows each start flash and the hand-off.
     */
    private void drawRipples(Canvas c,long now,boolean motion,float ready,float visible){
        if(!motion)return;
        float in=intro(now,CIRCLE_DELAY+RING_DRAW_MS/2,INTRO_MS,motion)*visible;
        if(in<=0)return;
        pen.setStyle(Paint.Style.STROKE);pen.setShader(null);
        float strength=0.06f+0.12f*ready;int color=0xff000000|LobbyLights.ACCENT;
        for(int i=0;i<3;i++)ripple(c,((now+i*RIPPLE_MS/3)%RIPPLE_MS)/(float)RIPPLE_MS,strength*in,color);
        long flashed=lights.flashedAt();
        if(flashed>=0&&now-flashed<BURST_MS)ripple(c,(now-flashed)/(float)BURST_MS,0.4f*in,TEXT);
        if(finishing&&now-finishAt<BURST_MS)ripple(c,(now-finishAt)/(float)BURST_MS,0.5f*in,TEXT);
    }
    /** One ripple at progress t (0..1): a fine line with a soft band, appearing quickly and fading as it spreads. */
    private void ripple(Canvas c,float t,float strength,int color){
        float radius=RIPPLE_FROM+(RING_R-16-RIPPLE_FROM)*ease(t),fade=strength*Math.min(1,t*8)*(1-t)*(1-t);
        if(fade<=0.002f)return;
        alpha=fade*0.35f;pen.setStrokeWidth(14);pen.setColor(tint(color));c.drawCircle(CX,CY,radius,pen);
        alpha=fade;pen.setStrokeWidth(2);pen.setColor(tint(color));c.drawCircle(CX,CY,radius,pen);
    }

    /* ---------------- top panel ---------------- */
    /** Status bar, the highlighted game tile, and a footer, in the manner of a console home screen. */
    private void drawPanel(Canvas c,long now,boolean motion,float visible,float ready,boolean lit){
        float in=intro(now,0,INTRO_MS,motion);
        alpha=in*visible;
        int save=c.save();c.translate(0,18*(1-in));
        // Status bar: a small ring mark and the name on the left, the time on the right.
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(3);pen.setColor(tint(TEXT));c.drawCircle(80,50,12,pen);
        fill(TEXT);c.drawCircle(80,50,4,pen);
        text(c,"Oniimai",104,59,26,TEXT,true,Paint.Align.LEFT,400);
        refreshClock();text(c,clockTime,1024,59,26,TEXT,false,Paint.Align.RIGHT,300);
        drawTile(c,now,motion,ready,lit);
        // Footer, below a hairline.
        fill(TRACK);c.drawRect(56,383,1024,384.5f,pen);
        text(c,I18n.t(Msg.DISPLAY_LOBBY_PHONE),56,424,20,TEXT_3,false,Paint.Align.LEFT,600);
        text(c,"v"+BuildConfig.VERSION_NAME+"  ·  LSPosed API 102",1024,424,20,TEXT_3,false,Paint.Align.RIGHT,340);
        c.restoreToCount(save);
    }
    /**
     * The game tile. Its outer highlight follows the lobby: quiet while the game loads, a breathing blue once
     * Start is available, white during the hand-off. The icon is the light ring in miniature.
     */
    private void drawTile(Canvas c,long now,boolean motion,float ready,boolean lit){
        float base=alpha;
        int edge=finishing?TEXT:blend(TILE_EDGE,0xff000000|LobbyLights.ACCENT,ready);
        float pulse=finishing||!motion?1:1-ready*0.4f*(0.5f+0.5f*(float)Math.cos(now*Math.PI*2/1600));
        alpha=base*pulse;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(4);pen.setColor(tint(edge));
        scratch.set(tile.left-8,tile.top-8,tile.right+8,tile.bottom+8);c.drawRoundRect(scratch,34,34,pen);
        alpha=base;fill(TILE);c.drawRoundRect(tile,26,26,pen);
        fill(TILE_ICON);c.drawRoundRect(tileIcon,22,22,pen);
        float ix=tileIcon.centerX(),iy=tileIcon.centerY(),r=62;
        scratch.set(ix-r,iy-r,ix+r,iy+r);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.5f);pen.setColor(tint(TRACK));c.drawCircle(ix,iy,r,pen);
        if(lit)strokes(c,new SweepGradient(ix,iy,opaque(),positions),scratch,0,360,ICON_WIDTHS,ICON_ALPHA);
        float x=tileIcon.right+36,width=tile.right-28-x;
        text(c,"KanadeDX",x,198,42,TEXT,true,Paint.Align.LEFT,width);
        row(c,x,258,input,inputTone,width,now,motion);
        row(c,x,304,led,ledTone,width,now,motion);
    }
    /** A status dot and the first line of a status text. A waiting dot pulses. */
    private void row(Canvas c,float x,float baseline,String value,int tone,float width,long now,boolean motion){
        float base=alpha;
        if(tone==NativeSettings.WAIT&&motion)alpha=base*(0.45f+0.55f*(0.5f+0.5f*(float)Math.cos(now*Math.PI*2/1600)));
        fill(tone==NativeSettings.OK?OK:tone==NativeSettings.WAIT?WAIT:IDLE);c.drawCircle(x+6,baseline-9,6,pen);
        alpha=base;
        text(c,firstLine(value),x+24,baseline,24,20,TEXT_2,false,Paint.Align.LEFT,width-24);
    }
    /** The time as the phone shows it (12- or 24-hour, in the module language), formatted once a minute. */
    private void refreshClock(){
        long wall=wallClock.getAsLong(),minute=wall/60000;java.util.Locale locale=I18n.locale();
        if(minute==clockMinute&&locale.equals(clockLocale))return;
        clockMinute=minute;clockLocale=locale;
        String skeleton=android.text.format.DateFormat.is24HourFormat(getContext())?"Hm":"hm";
        clockTime=new java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale,skeleton),locale).format(new java.util.Date(wall));
    }
    private static String firstLine(String value){return value==null||value.isEmpty()?"—":value.split("\n",2)[0];}

    /* ---------------- main circle ---------------- */
    /**
     * Samples the button-LED pattern all the way round the ring. Sample k sits at 3 o'clock + k steps
     * clockwise (the sweep gradient's origin); button 1 is at -67.5°. Returns whether anything is lit.
     */
    private boolean sample(long now,boolean motion){
        int[] still=motion?null:lights.still();boolean lit=false;
        for(int k=0;k<SAMPLES;k++){
            double degrees=360.0*k/SAMPLES;
            light[k]=still!=null?still[0]:lights.at(now,(degrees+90)/45-0.5);
            lit|=light[k]!=0;
        }
        light[SAMPLES]=light[0];
        return lit;
    }
    /** A faint track and the sampled light as a fine line over a soft halo; on entry it draws itself in from 12 o'clock. */
    private void drawRing(Canvas c,long now,boolean motion,boolean lit,float visible){
        float drawn=intro(now,RING_DELAY,RING_DRAW_MS,motion);
        if(drawn<=0)return;
        alpha=visible;
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeCap(Paint.Cap.BUTT);pen.setShader(null);
        pen.setStrokeWidth(1.5f);pen.setColor(tint(TRACK));c.drawArc(ring,-90,360*drawn,false,pen);
        if(!lit)return;
        strokes(c,new SweepGradient(CX,CY,opaque(),positions),ring,-90,360*drawn,RING_WIDTHS,RING_ALPHA);
    }
    /** The sampled light as opaque gradient stops; each pass sets its strength through the paint alpha. */
    private int[] opaque(){for(int k=0;k<=SAMPLES;k++)stops[k]=0xff000000|light[k];return stops;}
    /** One shader for all passes of a ring: halo strokes, then the line. */
    private void strokes(Canvas c,Shader shader,RectF oval,float from,float sweep,float[] widths,float[] strengths){
        float base=alpha;
        pen.setStyle(Paint.Style.STROKE);pen.setShader(shader);
        for(int pass=0;pass<widths.length;pass++){
            alpha=base*strengths[pass];pen.setColor(tint(Color.WHITE));pen.setStrokeWidth(widths[pass]);
            c.drawArc(oval,from,sweep,false,pen);
        }
        pen.setShader(null);alpha=base;
    }
    /** State title, one line of guidance, the Start button and what happens next. */
    private void drawCircle(Canvas c,long now,boolean motion,float title,float button,float press,float visible){
        float in=intro(now,CIRCLE_DELAY,INTRO_MS,motion);
        float shown=in*visible;
        int save=c.save();c.translate(0,24*(1-in)-16*(1-visible));
        // Cross-fade the two states; the outgoing line drifts up, the incoming one settles from below.
        swap(c,I18n.t(Msg.DISPLAY_LOBBY_LOADING),I18n.t(Msg.DISPLAY_LOBBY_READY),1226,50,TEXT,true,title,shown);
        swap(c,I18n.t(Msg.DISPLAY_LOBBY_WAITING),I18n.t(Msg.DISPLAY_LOBBY_CONTROLLER),1280,26,TEXT_2,false,title,shown);
        alpha=shown;
        // Warm white when Start is available, an outline while it is not; pressed, it shrinks and dims.
        float inset=start.width()*0.015f*press;
        scratch.set(start.left+inset,start.top+inset,start.right-inset,start.bottom-inset);
        float radius=scratch.height()/2,base=alpha;
        alpha=base*button;fill(blend(BUTTON,BUTTON_PRESSED,press));c.drawRoundRect(scratch,radius,radius,pen);
        alpha=base*(1-button);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(2);pen.setColor(tint(BUTTON_EDGE));c.drawRoundRect(scratch,radius,radius,pen);
        alpha=base;
        text(c,I18n.t(Msg.DISPLAY_LOBBY_START),CX,start.centerY()+12,34*(1-0.03f*press),blend(TEXT_3,INK,button),true,Paint.Align.CENTER,start.width()-80);
        text(c,I18n.t(Msg.DISPLAY_LOBBY_TRANSITION),CX,1506,22,TEXT_3,false,Paint.Align.CENTER,720);
        c.restoreToCount(save);
    }
    /** The outgoing line leaves during the first 40% of the change, the incoming one arrives after it, so they never overlap. */
    private void swap(Canvas c,String from,String to,float baseline,float size,int color,boolean bold,float amount,float in){
        float out=Math.min(1,amount/0.4f),arrive=Math.max(0,(amount-0.4f)/0.6f);
        if(out<1){alpha=in*(1-out);text(c,from,CX,baseline-12*out,size,color,bold,Paint.Align.CENTER,780);}
        if(arrive>0){alpha=in*arrive;text(c,to,CX,baseline+12*(1-arrive),size,color,bold,Paint.Align.CENTER,780);}
    }

    /* ---------------- drawing helpers ---------------- */
    /** Entrance progress for an element that starts {@code delay} ms after the screen appears, eased out. */
    private float intro(long now,long delay,long duration,boolean motion){
        if(!motion||shownAt<0)return 1;
        return ease(Math.max(0,Math.min(1,(now-shownAt-delay)/(float)duration)));
    }
    private static float ease(float t){float u=1-t;return 1-u*u*u;}
    private void fill(int color){pen.setStyle(Paint.Style.FILL);pen.setColor(tint(color));}
    private int tint(int color){return Math.round((color>>>24)*Math.max(0,Math.min(1,alpha)))<<24|(color&0xffffff);}
    private static int blend(int from,int to,float amount){return 0xff000000|LobbyLights.mix(from,to,amount);}
    private void typeface(boolean bold,float size){
        pen.setTypeface(bold?GameAssets.semibold(getContext()):GameAssets.regular(getContext()));pen.setTextSize(size);
    }
    private float text(Canvas c,String value,float x,float y,float size,int color,boolean bold,Paint.Align align,float width){
        return text(c,value,x,y,size,size,color,bold,align,width);
    }
    /** Shrinks toward minSize to fit the width, then shortens with an ellipsis. Returns the drawn width. */
    private float text(Canvas c,String value,float x,float y,float size,float minSize,int color,boolean bold,Paint.Align align,float width){
        pen.setStyle(Paint.Style.FILL);pen.setColor(tint(color));pen.setTextAlign(align);typeface(bold,size);
        float measured=pen.measureText(value);
        if(measured>width&&minSize<size)pen.setTextSize(Math.max(minSize,size*width/measured));
        if(pen.measureText(value)>width){int n=pen.breakText(value,true,width-pen.measureText("…"),null);value=value.substring(0,n)+"…";}
        c.drawText(value,x,y,pen);
        return pen.measureText(value);
    }

    /** A 0..1 value easing toward a boolean target; it jumps when animations are off. */
    private static final class Fade {
        private float from,to;
        private long at=-1,duration;
        float value(boolean target,long now,long duration,boolean motion){
            float goal=target?1:0;this.duration=duration;
            if(at<0||!motion){from=to=goal;at=now-duration;return goal;}
            if(goal!=to){from=current(now);to=goal;at=now;}
            return current(now);
        }
        boolean moving(long now){return now-at<duration;}
        private float current(long now){return from+(to-from)*ease(Math.max(0,Math.min(1,(now-at)/(float)duration)));}
    }

    /* ---------------- input ---------------- */
    private boolean hit(float x,float y){
        transform.setValues(DisplayGeometry.surfaceMatrix(getWidth(),getHeight(),clockwise));
        if(!transform.invert(inverse))return false;
        float[] point={x,y};inverse.mapPoints(point);
        // A slightly larger touch target than the drawn button.
        return point[0]>=start.left-16&&point[0]<=start.right+16&&point[1]>=start.top-16&&point[1]<=start.bottom+16;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){pressed=enabledStart()&&hit(event.getX(),event.getY());invalidate();return pressed;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(!hit(event.getX(),event.getY()))pressed=false;invalidate();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){pressed=false;invalidate();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){boolean fire=pressed&&hit(event.getX(),event.getY());pressed=false;if(fire)performClick();invalidate();return true;}
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){
        super.performClick();if(!enabledStart())return false;
        if(host.requestStartup()){requestedAt=SystemClock.uptimeMillis();lights.pressed(requestedAt);}
        else Toast.makeText(getContext(),I18n.t(Msg.DISPLAY_LOBBY_RETRY),Toast.LENGTH_SHORT).show();
        invalidate();return true;
    }
}
