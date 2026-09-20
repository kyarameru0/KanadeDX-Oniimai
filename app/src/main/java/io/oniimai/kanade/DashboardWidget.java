package io.oniimai.kanade;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.FrameLayout;
import org.json.JSONObject;
/** Compose content; the parent board retains its tested drag and persistent grid model. */
final class DashboardWidget extends FrameLayout {
    private final WidgetState state=new WidgetState();
    private final DashboardHost host;
    private final String type;
    DashboardWidget(Context context,String type,DashboardHost host){
        super(context);this.host=host;this.type=type;
        addView(NativeDashboard.widget(host.activity(),type,host,state),new LayoutParams(-1,-1));
    }
    void setGridSize(int width,int height){state.setWidth(width);}
    void update(JSONObject frame,boolean stale,Bitmap cover){state.update(frame,stale,cover,host,type);}
}
