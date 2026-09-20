package io.oniimai.kanade;

import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.HashSet;
import java.util.Set;

/** Keeps physical controller input out of the UI, independently of the monitor toggle. */
final class ControllerInput {
    private final Set<Long> held = new HashSet<>();

    boolean captures(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (event.getDeviceId() <= 0 || (device != null && device.isVirtual())
                || (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return false;
        // Preserve the phone's own Back/volume/system keys. A controller's Back is input.
        boolean peripheral = device != null && (Build.VERSION.SDK_INT >= 29 ? device.isExternal()
                : device.getVendorId() != 0 || device.getProductId() != 0
                || device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        boolean gamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK);
        return !event.isSystem() || peripheral || gamepad;
    }

    boolean captures(MotionEvent event) {
        // Consume axes before Android can turn them into repeated D-pad navigation.
        return event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_GAMEPAD);
    }

    boolean changed(KeyEvent event) {
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return event.getRepeatCount() == 0 && held.add(key);
        }
        if (event.getAction() == KeyEvent.ACTION_UP) return held.remove(key);
        return false;
    }

    void clear() { held.clear(); }
}
