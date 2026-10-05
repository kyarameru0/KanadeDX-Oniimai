// Modified 2026-09-29 (UI refinement pass, see CHANGES-UI.md). Original: KanadeDX-Oniimai 1.1.0-rc5 @ 1c9c518b.
package io.oniimai.kanade;

import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;

/** In-app widget board. Editing uses a private draft; only Save changes the stored board. */
final class DashboardView extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String KEY = "dashboardLayoutV1";
    private static final String DRAFT_KEY = KEY + "Draft";
    private static final String[] TYPES = {"song","score","judgments","timing","sensors","connection","clock"};
    private final DashboardHost host;
    private final boolean preview;
    private final Runnable close;
    private final LinearLayout header, toolbar;
    private final TextView hint;
    private final ScrollView scroll;
    private final Board board;
    private DashboardLayout layout;
    private boolean editing;
    private int viewportHeight;
    private int selected = -1;
    private boolean attached;
    private String previousJson="";
    private JSONObject frame=new JSONObject();
    private final Rect visibleBounds=new Rect();
    private final Runnable refresh = new Runnable() {
        public void run() { if(!refreshVisible())return;host.updateDashboard(DashboardView.this); postDelayed(this, 150); }
    };
    private boolean refreshVisible(){return attached&&isShown()&&getWindowVisibility()==View.VISIBLE&&hasWindowFocus();}
    private void scheduleRefresh(){removeCallbacks(refresh);if(refreshVisible())post(refresh);}
    private DashboardLayout loadLayout(String key){
        DashboardLayout parsed=DashboardLayout.parse(host.prefs().getString(key,""));
        if(parsed.retiredWidgetsRemoved())host.prefs().edit().putString(key,parsed.encode()).apply();
        return parsed;
    }

    DashboardView(DashboardHost host, boolean preview, Runnable close) {
        super(host.activity()); this.host=host;GameAssets.bind(host.assetContext()); this.preview=preview; this.close=close;
        editing=host.prefs().contains(DRAFT_KEY);
        layout=loadLayout(editing?DRAFT_KEY:KEY);
        setOrientation(VERTICAL); setBackgroundColor(NativeDashboard.pageColor(host.activity())); setClickable(true);
        if(android.os.Build.VERSION.SDK_INT>=29)setForceDarkAllowed(false);
        if(android.os.Build.VERSION.SDK_INT>=30)setOnApplyWindowInsetsListener((v,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            setPadding(safe.left,safe.top,safe.right,safe.bottom); return insets;
        });
        header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header);
        hint=GameUi.text(getContext(),"",12,GameUi.MUTED,false);
        hint.setPadding(dp(20),0,dp(20),dp(12));addView(hint);
        scroll=new ScrollView(getContext()){
            @Override protected void onMeasure(int ws,int hs){
                viewportHeight=Math.max(0,MeasureSpec.getSize(hs)-getPaddingTop()-getPaddingBottom());
                super.onMeasure(ws,hs);
            }
        };scroll.setFillViewport(true);scroll.setClipToPadding(true);
        // No space below the widgets: the action bar's own even padding is the gap, so its buttons sit midway
        // between the widgets and the bottom edge, one card gap from each.
        scroll.setPadding(dp(16),dp(6),dp(16),0);scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        scroll.setVerticalFadingEdgeEnabled(false);
        board=new Board();scroll.addView(board,new ScrollView.LayoutParams(-1,-2));
        addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        toolbar=new LinearLayout(getContext());toolbar.setGravity(Gravity.CENTER_VERTICAL);
        addView(toolbar);
        chrome();board.rebuild();
    }
    private int dp(float n){return GameUi.dp(getContext(),n);}
    static String title(String type){switch(type){
        case "song":return I18n.t(Msg.WIDGET_SONG_TITLE);
        case "score":return I18n.t(Msg.WIDGET_SCORE_TITLE);
        case "judgments":return I18n.t(Msg.WIDGET_JUDGMENTS_TITLE);
        case "timing":return "FAST / LATE";
        case "sensors":return I18n.t(Msg.WIDGET_SENSORS_TITLE);
        case "connection":return I18n.t(Msg.WIDGET_CONNECTION_TITLE);
        default:return I18n.t(Msg.WIDGET_CLOCK_TITLE);
    }}
    private static String description(String type){switch(type){
        case "song":return I18n.t(Msg.WIDGET_SONG_DESCRIPTION);
        case "score":return I18n.t(Msg.WIDGET_SCORE_DESCRIPTION);
        case "judgments":return I18n.t(Msg.WIDGET_JUDGMENTS_DESCRIPTION);
        case "timing":return I18n.t(Msg.WIDGET_TIMING_DESCRIPTION);
        case "sensors":return I18n.t(Msg.WIDGET_SENSORS_DESCRIPTION);
        case "connection":return I18n.t(Msg.WIDGET_CONNECTION_DESCRIPTION);
        default:return I18n.t(Msg.WIDGET_CLOCK_DESCRIPTION);
    }}
    private void chrome(){
        header.removeAllViews();toolbar.removeAllViews();
        hint.setVisibility(View.GONE);
        header.addView(NativeDashboard.header(host.activity(),editing,preview,host.demo(),()->{if(editing)save();else edit(-1);}),new LinearLayout.LayoutParams(-1,-2));
        String[] labels=editing?new String[]{I18n.t(Msg.DASHBOARD_ADD),I18n.t(Msg.DASHBOARD_ARRANGE),I18n.t(Msg.COMMON_CANCEL)}:preview?new String[]{I18n.t(Msg.COMMON_GO_BACK)}:new String[]{I18n.t(Msg.DASHBOARD_SWITCH_TO_PHONE),I18n.t(Msg.COMMON_SETTINGS)};
        Runnable[] actions=editing?new Runnable[]{this::catalog,this::arrange,this::confirmExit}:preview?new Runnable[]{close}:new Runnable[]{close,host::showSettings};
        toolbar.addView(NativeDashboard.footer(host.activity(),labels,actions,false),new LinearLayout.LayoutParams(-1,-2));
    }
    private void edit(int id){if(!editing){layout=layout.copy();editing=true;host.setDashboardEditing(true);}selected=id;chrome();board.rebuild();}
    private void save(){host.prefs().edit().putString(KEY,layout.encode()).apply();finish();toast(I18n.t(Msg.DASHBOARD_SAVED));}
    private void finish(){editing=false;host.setDashboardEditing(false);host.prefs().edit().remove(DRAFT_KEY).apply();selected=-1;layout=loadLayout(KEY);chrome();board.rebuild();}
    boolean back(){
        if(!editing)return false;
        confirmExit();return true;
    }
    /** Unchanged drafts close silently; otherwise ask, so one stray tap on Cancel cannot discard work. */
    private void confirmExit(){
        if(!dirty()){finish();return;}
        host.choose(I18n.t(Msg.DASHBOARD_EXIT_TITLE),new String[]{I18n.t(Msg.DASHBOARD_EXIT_SAVE),I18n.t(Msg.DASHBOARD_EXIT_DISCARD),I18n.t(Msg.DASHBOARD_EXIT_CONTINUE)},-1,i->{if(i==0)save();else if(i==1)finish();});
    }
    private boolean dirty(){return !layout.encode().equals(DashboardLayout.parse(host.prefs().getString(KEY,"")).encode());}
    private void catalog(){
        String[] options=new String[TYPES.length];
        for(int i=0;i<TYPES.length;i++){
            StringBuilder dimensions=new StringBuilder();for(int[] size:DashboardLayout.sizes(TYPES[i])){if(dimensions.length()>0)dimensions.append("  ·  ");dimensions.append(size[0]).append("×").append(size[1]);}
            options[i]=title(TYPES[i])+"  ·  "+dimensions+"\n"+description(TYPES[i]);
        }
        host.choose(I18n.t(Msg.DASHBOARD_ADD_WIDGET),options,-1,i->{
            DashboardLayout.Item item=layout.add(TYPES[i]);
            if(item==null){toast(I18n.t(Msg.DASHBOARD_LIMIT));return;}
            selected=item.id;board.rebuild();board.post(()->scroll.smoothScrollTo(0,board.rowTop(item.y)));
        });
    }
    private void arrange(){
        host.choose(I18n.t(Msg.DASHBOARD_ARRANGE_TITLE),new String[]{I18n.t(Msg.DASHBOARD_ARRANGE_ALL_2X2),I18n.t(Msg.DASHBOARD_ARRANGE_PACK),I18n.t(Msg.DASHBOARD_ARRANGE_DEFAULT),I18n.t(Msg.DASHBOARD_ARRANGE_PLAY),I18n.t(Msg.DASHBOARD_ARRANGE_INPUT_TEST),I18n.t(Msg.DASHBOARD_ARRANGE_CLEAR)},-1,i->{
            if(i==0)layout.compact();
            else if(i==1)layout.pack();
            else if(i==2)layout=DashboardLayout.defaults();
            else{
                for(DashboardLayout.Item item:new ArrayList<>(layout.items()))layout.remove(item.id);
                String[] types=i==3?new String[]{"song","score","timing","judgments"}:i==4?new String[]{"connection","sensors","clock"}:new String[0];
                for(String type:types)layout.add(type);
            }
            selected=-1;board.rebuild();scroll.smoothScrollTo(0,0);
        });
    }
    private void options(int id){
        DashboardLayout.Item item=layout.get(id);if(item==null)return;select(id);
        host.choose(title(item.type),new String[]{I18n.t(Msg.DASHBOARD_WIDGET_RESIZE)+"  ·  "+item.w+" × "+item.h,I18n.t(Msg.DASHBOARD_WIDGET_MOVE_EARLIER),I18n.t(Msg.DASHBOARD_WIDGET_MOVE_LATER),I18n.t(Msg.DASHBOARD_WIDGET_DUPLICATE),I18n.t(Msg.DASHBOARD_WIDGET_DELETE)},-1,i->{
            if(i==0){sizes(id);return;}
            if(i==1||i==2){if(!layout.moveBy(id,i==1?-1:1))toast(I18n.t(Msg.DASHBOARD_WIDGET_CANNOT_MOVE));}
            else if(i==3){DashboardLayout.Item added=layout.add(item.type);if(added!=null)selected=added.id;else toast(I18n.t(Msg.DASHBOARD_WIDGET_NO_ROOM));}
            else layout.remove(id);
            board.rebuild();
        });
    }
    private void sizes(int id){
        DashboardLayout.Item item=layout.get(id);if(item==null)return;
        int[][] sizes=DashboardLayout.sizes(item.type);String[] names=new String[sizes.length];int checked=-1;
        for(int n=0;n<sizes.length;n++){int[] s=sizes[n];names[n]=s[0]+" × "+s[1]+"  ·  "+(s[0]!=s[1]?I18n.t(Msg.DASHBOARD_SIZE_WIDE):s[0]==4?I18n.t(Msg.DASHBOARD_SIZE_LARGE_SQUARE):I18n.t(Msg.DASHBOARD_SIZE_SQUARE));if(item.w==s[0]&&item.h==s[1])checked=n;}
        host.choose(I18n.t(Msg.DASHBOARD_SIZE_TITLE),names,checked,i->{
            int[] size=sizes[i];if(!resizeAndPack(id,size[0],size[1]))toast(I18n.t(Msg.DASHBOARD_SIZE_NO_ROOM));board.rebuild();
        });
    }
    private boolean resizeAndPack(int id,int w,int h){
        return layout.resizeAndPack(id,w,h);
    }
    private void toast(String message){Toast.makeText(getContext(),message,Toast.LENGTH_SHORT).show();}
    private void select(int id){selected=id;for(int n=0;n<board.getChildCount();n++)board.getChildAt(n).invalidate();}
    void update(String json,boolean stale,Bitmap cover){
        if(json==null)json="{}";
        if(!previousJson.equals(json)){
            try{frame=new JSONObject(json);}catch(Exception ignored){frame=new JSONObject();}
            previousJson=json;
        }
        for(int i=0;i<board.getChildCount();i++){
            Tile tile=(Tile)board.getChildAt(i);
            if(tile.getWidth()==0||tile.getLocalVisibleRect(visibleBounds))tile.content.update(frame,stale,cover);
        }
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){if(KEY.equals(key)&&!editing){layout=loadLayout(KEY);board.rebuild();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;host.setDashboardEditing(editing);host.prefs().registerOnSharedPreferenceChangeListener(this);scheduleRefresh();requestApplyInsets();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);scheduleRefresh();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);scheduleRefresh();}
    @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);scheduleRefresh();}
    @Override protected void onDetachedFromWindow(){attached=false;if(editing)host.prefs().edit().putString(DRAFT_KEY,layout.encode()).apply();host.setDashboardEditing(false);removeCallbacks(refresh);host.prefs().unregisterOnSharedPreferenceChangeListener(this);super.onDetachedFromWindow();}

    private final class Board extends ViewGroup {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cellWidth,cellHeight,gridLeft;
        private final int gap=dp(10);
        private int ghostX,ghostY,ghostW,ghostH;
        private boolean ghost,valid;
        Board(){super(DashboardView.this.getContext());setWillNotDraw(false);setClipChildren(false);setClipToPadding(false);setOnLongClickListener(v->{edit(-1);return true;});}
        int rowTop(int row){return Math.round(row*(cellHeight+gap));}
        private boolean compactBoard(){
            java.util.List<DashboardLayout.Item> items=layout.items();
            if(items.isEmpty()||layout.rows()!=2*((items.size()+1)/2))return false;
            for(DashboardLayout.Item item:items)if(item.w!=2||item.h!=2||(item.x!=0&&item.x!=2)||item.y%2!=0)return false;
            return true;
        }
        void rebuild(){removeAllViews();ghost=false;for(DashboardLayout.Item item:layout.items())addView(new Tile(item));requestLayout();invalidate();host.updateDashboard(DashboardView.this);}
        @Override protected void onMeasure(int ws,int hs){
            int width=MeasureSpec.getSize(ws);cellWidth=Math.max(1,(width-gap*3)/4f);
            gridLeft=0;
            // Recover the last whole row when only a small width reduction is needed.
            // Editing and deliberately spaced layouts retain their normal cell geometry.
            if(!editing&&compactBoard()&&viewportHeight>0){
                float fitCell=(viewportHeight-2-(layout.rows()-1)*gap)/(float)layout.rows();
                float fitWidth=fitCell*4+gap*3;
                if(fitCell<cellWidth&&fitWidth>=width*.88f&&fitCell*2+gap>=dp(148)){
                    cellWidth=fitCell;gridLeft=(width-fitWidth)/2;
                }
            }
            cellHeight=cellWidth*Math.max(1f,1f+(getResources().getConfiguration().fontScale-1f)*.85f);
            for(int i=0;i<getChildCount();i++){
                Tile tile=(Tile)getChildAt(i);DashboardLayout.Item item=layout.get(tile.id);
                tile.measure(MeasureSpec.makeMeasureSpec(Math.round(item.w*cellWidth+(item.w-1)*gap),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(item.h*cellHeight+(item.h-1)*gap),MeasureSpec.EXACTLY));
            }
            int rows=Math.max(editing?5:1,layout.rows()+(editing?4:0));
            setMeasuredDimension(width,Math.max(MeasureSpec.getSize(hs),rowTop(rows)-gap));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            for(int i=0;i<getChildCount();i++){Tile tile=(Tile)getChildAt(i);DashboardLayout.Item item=layout.get(tile.id);int x=Math.round(gridLeft+item.x*(cellWidth+gap)),y=rowTop(item.y);tile.layout(x,y,x+tile.getMeasuredWidth(),y+tile.getMeasuredHeight());}
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            boolean night=GameUi.night(getContext());
            if(editing){paint.setColor(GameUi.line(night));for(float y=cellHeight/2;y<getHeight();y+=cellHeight+gap)for(float x=gridLeft+cellWidth/2;x<getWidth()-gridLeft;x+=cellWidth+gap)canvas.drawCircle(x,y,dp(2),paint);}
            if(getChildCount()==0){paint.setColor(GameUi.muted(night));paint.setTypeface(GameAssets.font(getContext()));paint.setTextSize(dp(16));paint.setTextAlign(Paint.Align.CENTER);String text=I18n.t(Msg.DASHBOARD_EMPTY);float width=paint.measureText(text);if(width>getWidth()-dp(16))paint.setTextSize(paint.getTextSize()*(getWidth()-dp(16))/width);canvas.drawText(text,getWidth()/2f,dp(95),paint);}
        }
        @Override protected void dispatchDraw(Canvas canvas){
            super.dispatchDraw(canvas);if(!ghost)return;
            RectF target=new RectF(gridLeft+ghostX*(cellWidth+gap),rowTop(ghostY),gridLeft+ghostX*(cellWidth+gap)+ghostW*cellWidth+(ghostW-1)*gap,rowTop(ghostY)+ghostH*cellHeight+(ghostH-1)*gap);
            int tone=valid?GameUi.accent(GameUi.night(getContext())):GameUi.ERROR;float radius=dp(GameUi.CARD_RADIUS_DP);
            paint.setStyle(Paint.Style.FILL);paint.setColor(tone&0x00ffffff|0x22000000);canvas.drawRoundRect(target,radius,radius,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(tone);canvas.drawRoundRect(target,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        }
        void propose(int id,int x,int y,int w,int h,boolean resizing){
            ghost=true;ghostX=x;ghostY=y;ghostW=w;ghostH=h;
            DashboardLayout test=layout.copy();valid=resizing?test.resizeAndPack(id,w,h):test.move(id,x,y);
            if(valid&&resizing){DashboardLayout.Item target=test.get(id);ghostX=target.x;ghostY=target.y;}invalidate();
        }
    }
    private final class Tile extends FrameLayout {
        final int id;
        final DashboardWidget content;
        private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX,downY,rawX,rawY;
        private int initialScroll;
        private boolean dragging,resizing;
        private final int[] scrollLocation=new int[2];
        private final Runnable edgeScroll=new Runnable(){public void run(){
            if(!dragging||!isAttachedToWindow())return;
            scroll.getLocationOnScreen(scrollLocation);float y=rawY-scrollLocation[1];
            int before=scroll.getScrollY();if(y<dp(70))scroll.scrollBy(0,-dp(12));else if(y>scroll.getHeight()-dp(70))scroll.scrollBy(0,dp(12));
            if(before!=scroll.getScrollY())updateGesture();postDelayed(this,32);
        }};
        private void updateGesture(){
            DashboardLayout.Item item=layout.get(id);if(item==null)return;
            float dx=rawX-downX,dy=rawY-downY+scroll.getScrollY()-initialScroll;
            setAlpha(.9f);setScaleX(1.03f);setScaleY(1.03f);setElevation(dp(8));
            if(resizing){int[] size=DashboardLayout.nearestSize(item.type,item.w+dx/(board.cellWidth+board.gap),item.h+dy/(board.cellHeight+board.gap));board.propose(id,item.x,item.y,size[0],size[1],true);}
            else{int x=Math.max(0,Math.min(4-item.w,item.x+Math.round(dx/(board.cellWidth+board.gap))));int y=Math.max(0,Math.min(DashboardLayout.MAX_ROWS-item.h,item.y+Math.round(dy/(board.cellHeight+board.gap))));board.propose(id,x,y,item.w,item.h,false);setTranslationX(dx);setTranslationY(dy);}
        }
        Tile(DashboardLayout.Item item){
            super(DashboardView.this.getContext());id=item.id;setWillNotDraw(false);
            content=new DashboardWidget(getContext(),item.type,host);content.setGridSize(item.w,item.h);addView(content,new FrameLayout.LayoutParams(-1,-1));
            setFocusable(true);setClickable(true);setContentDescription(DashboardView.title(item.type)+" · "+item.w+" × "+item.h);
            setOnLongClickListener(v->{edit(id);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);return true;});
            setOnClickListener(v->{if(editing)options(id);});
            setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,I18n.t(Msg.DASHBOARD_EDIT_TITLE)));}
            });
        }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return true;}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(!editing)return super.onTouchEvent(event);
            DashboardLayout.Item item=layout.get(id);if(item==null)return true;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    removeCallbacks(edgeScroll);select(id);rawX=downX=event.getRawX();rawY=downY=event.getRawY();initialScroll=scroll.getScrollY();dragging=false;
                    resizing=event.getX()>getWidth()-dp(40)&&event.getY()>getHeight()-dp(40);
                    getParent().requestDisallowInterceptTouchEvent(true);return true;
                case MotionEvent.ACTION_MOVE:
                    rawX=event.getRawX();rawY=event.getRawY();float dx=rawX-downX,dy=rawY-downY+scroll.getScrollY()-initialScroll;
                    if(!dragging&&Math.hypot(dx,dy)<ViewConfiguration.get(getContext()).getScaledTouchSlop())return true;
                    if(!dragging){dragging=true;post(edgeScroll);}updateGesture();
                    return true;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(edgeScroll);getParent().requestDisallowInterceptTouchEvent(false);
                    if(dragging){boolean done=board.valid&&(resizing?layout.resizeAndPack(id,board.ghostW,board.ghostH):layout.move(id,board.ghostX,board.ghostY));if(!done)toast(I18n.t(Msg.DASHBOARD_DROP_HINT));board.rebuild();}
                    else performClick();return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(edgeScroll);dragging=false;resizing=false;getParent().requestDisallowInterceptTouchEvent(false);setAlpha(1);setScaleX(1);setScaleY(1);setElevation(dp(1));setTranslationX(0);setTranslationY(0);board.ghost=false;board.invalidate();return true;
                default:return true;
            }
        }
        @Override public boolean performClick(){return super.performClick();}
        @Override protected void onDetachedFromWindow(){removeCallbacks(edgeScroll);super.onDetachedFromWindow();}
        @Override protected void dispatchDraw(Canvas canvas){
            super.dispatchDraw(canvas);if(!editing)return;
            int accent=GameUi.accent(GameUi.night(getContext()));
            if(DashboardView.this.selected==id){
                float inset=dp(1),radius=dp(GameUi.CARD_RADIUS_DP);
                pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(2));pen.setColor(accent);
                canvas.drawRoundRect(inset,inset,getWidth()-inset,getHeight()-inset,radius,radius,pen);
            }
            pen.setStyle(Paint.Style.FILL);pen.setColor(accent);pen.setStrokeWidth(dp(2));pen.setStrokeCap(Paint.Cap.ROUND);
            float r=getWidth()-dp(13),b=getHeight()-dp(13);canvas.drawLine(r-dp(13),b,r,b-dp(13),pen);canvas.drawLine(r-dp(6),b,r,b-dp(6),pen);
            for(int n=0;n<3;n++)canvas.drawCircle(getWidth()-dp(17),dp(12+n*5),dp(1.3f),pen);
            DashboardLayout.Item item=layout.get(id);if(item!=null){pen.setTypeface(GameAssets.font(getContext()));pen.setTextSize(dp(11));pen.setTextAlign(Paint.Align.RIGHT);canvas.drawText(item.w+"×"+item.h,getWidth()-dp(31),dp(24),pen);}
        }
    }
}
