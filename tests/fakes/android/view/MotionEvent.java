package android.view;
public final class MotionEvent {
    private final int source;
    public MotionEvent(int source){this.source=source;}
    public boolean isFromSource(int requested){return (source & requested)==requested;}
}
