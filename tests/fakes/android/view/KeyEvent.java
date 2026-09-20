package android.view;
/** Small input-value fake; no claim of exercising Android's real event dispatch. */
public final class KeyEvent {
    public static final int ACTION_DOWN=0, ACTION_UP=1, ACTION_MULTIPLE=2, FLAG_SOFT_KEYBOARD=2;
    public static final int KEYCODE_BACK=4, KEYCODE_VOLUME_UP=24, KEYCODE_VOLUME_DOWN=25;
    private final InputDevice device;
    private final int id,code,action,repeat,flags,source;
    public KeyEvent(InputDevice device,int id,int code,int action,int repeat,int flags,int source){
        this.device=device;this.id=id;this.code=code;this.action=action;this.repeat=repeat;this.flags=flags;this.source=source;
    }
    public InputDevice getDevice(){return device;}
    public int getDeviceId(){return id;}
    public int getKeyCode(){return code;}
    public int getAction(){return action;}
    public int getRepeatCount(){return repeat;}
    public int getFlags(){return flags;}
    public boolean isFromSource(int requested){return (source & requested)==requested;}
    public boolean isSystem(){return code==KEYCODE_BACK||code==KEYCODE_VOLUME_UP||code==KEYCODE_VOLUME_DOWN;}
}
