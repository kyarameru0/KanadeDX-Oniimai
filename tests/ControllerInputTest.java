package io.oniimai.kanade;
import android.os.Build;
import android.view.*;

public final class ControllerInputTest {
    static int checks;
    static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    static final InputDevice usb=new InputDevice(false,true,0x1234,1,2);
    static KeyEvent key(InputDevice device,int id,int code,int action,int repeat){
        return new KeyEvent(device,id,code,action,repeat,0,InputDevice.SOURCE_KEYBOARD);
    }
    public static void main(String[] args){
        ControllerInput guard=new ControllerInput();
        // The monitor checkbox is intentionally not an input to captures(). Every tab is protected.
        for(int code:new int[]{66,62,61,19,20,21,22,23,4,51,33,32,31,52,54,29,45}){
            for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP,KeyEvent.ACTION_MULTIPLE})
                check(guard.captures(key(usb,8,code,action,0)),"Controller must not reach menu: "+code+" / "+action);
            check(guard.captures(key(usb,8,code,KeyEvent.ACTION_DOWN,50)),"Long press must not navigate UI");
        }
        KeyEvent down=key(usb,8,66,KeyEvent.ACTION_DOWN,0),up=key(usb,8,66,KeyEvent.ACTION_UP,0);
        check(guard.changed(down),"Initial press visible to monitor");
        check(!guard.changed(down),"Duplicate down with repeatCount=0 is ignored");
        check(!guard.changed(key(usb,8,66,KeyEvent.ACTION_DOWN,1)),"Auto-repeat does not emit another press");
        check(guard.changed(key(usb,8,62,KeyEvent.ACTION_DOWN,0)),"Simultaneous second button preserved");
        check(guard.changed(up),"Release visible to monitor");
        check(!guard.changed(up),"Duplicate release ignored");
        check(guard.changed(down),"A new physical press is accepted immediately without timed debounce");
        check(!guard.changed(key(usb,8,66,KeyEvent.ACTION_MULTIPLE,4)),"Multiple action cannot emit presses");
        guard.clear();
        check(!guard.changed(key(usb,8,66,KeyEvent.ACTION_DOWN,2)),"Held repeat after resume does not rearm key learning");
        check(guard.changed(down),"Fresh press after focus reset works");
        check(guard.changed(key(usb,9,66,KeyEvent.ACTION_DOWN,0)),"Independent device press tracked separately");
        for(int api:new int[]{26,28,29,35}){
            Build.VERSION.SDK_INT=api;
            InputDevice phone=new InputDevice(false,false,0,0,1);
            for(int code:new int[]{4,24,25}){
                check(!guard.captures(key(phone,2,code,KeyEvent.ACTION_DOWN,0)),"Phone system key preserved on API "+api);
                check(guard.captures(key(usb,8,code,KeyEvent.ACTION_DOWN,0)),"USB system key captured on API "+api);
            }
        }
        check(!guard.captures(key(usb,-1,66,0,0)),"Virtual keyboard ID bypasses capture");
        check(!guard.captures(key(new InputDevice(true,false,0,0,2),3,66,0,0)),"Virtual input device bypasses capture");
        check(!guard.captures(new KeyEvent(usb,8,66,0,0,KeyEvent.FLAG_SOFT_KEYBOARD,InputDevice.SOURCE_KEYBOARD)),"IME action preserved");
        check(guard.captures(key(null,8,66,0,0)),"Device lookup loss cannot leak Enter");
        check(guard.captures(new KeyEvent(null,8,4,0,0,0,InputDevice.SOURCE_GAMEPAD)),"Gamepad Back captured without metadata");
        check(guard.captures(new MotionEvent(InputDevice.SOURCE_JOYSTICK)),"Joystick cannot synthesize D-pad navigation");
        check(guard.captures(new MotionEvent(InputDevice.SOURCE_GAMEPAD)),"Gamepad motion consumed");
        check(!guard.captures(new MotionEvent(InputDevice.SOURCE_TOUCHSCREEN)),"Phone touch unaffected");
        check(!guard.captures(new MotionEvent(InputDevice.SOURCE_MOUSE)),"Mouse scrolling unaffected");
        System.out.println("PASS: "+checks+" controller-input checks");
    }
}
