package android.view;
public final class InputDevice {
    public static final int SOURCE_KEYBOARD=0x101, SOURCE_DPAD=0x201, SOURCE_GAMEPAD=0x401,
        SOURCE_TOUCHSCREEN=0x1002, SOURCE_MOUSE=0x2002, SOURCE_JOYSTICK=0x1000010;
    public static final int KEYBOARD_TYPE_ALPHABETIC=2;
    public final boolean virtual,external;
    private final int vendor,product,type;
    public InputDevice(boolean virtual,boolean external,int vendor,int product,int type){
        this.virtual=virtual;this.external=external;this.vendor=vendor;this.product=product;this.type=type;
    }
    public boolean isVirtual(){return virtual;}
    public boolean isExternal(){
        if(android.os.Build.VERSION.SDK_INT<29)throw new AssertionError("isExternal called below API 29");
        return external;
    }
    public int getVendorId(){return vendor;}
    public int getProductId(){return product;}
    public int getKeyboardType(){return type;}
}
