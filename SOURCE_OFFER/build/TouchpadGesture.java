// SPDX-License-Identifier: Apache-2.0

import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.lang.reflect.Method;

/** Injects touch events as the ADB shell, scoped to the tested app's UID. */
public final class TouchpadGesture {
    private final Object manager;
    private final Method inject;
    private final int uid;
    private long downTime;
    private int contacts;

    private TouchpadGesture(int uid) throws Exception {
        this.uid = uid;
        Class<?> type = Class.forName("android.hardware.input.InputManagerGlobal");
        manager = type.getMethod("getInstance").invoke(null);
        inject = type.getMethod("injectInputEvent", InputEvent.class, int.class, int.class);
    }

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 2000 || args.length != 4) {
            throw new IllegalArgumentException("ADB shell usage: gesture uid x y");
        }
        int uid = Integer.parseInt(args[1]);
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);
        if (uid < 10000 || uid >= 20000) {
            throw new IllegalArgumentException("Expected an app UID in Android user 0.");
        }
        TouchpadGesture gesture = new TouchpadGesture(uid);
        try {
            switch (args[0]) {
                case "move": gesture.swipe(x, y, 96, 0, false); break;
                case "click": gesture.tap(x, y); break;
                case "drag":
                    gesture.tap(x, y);
                    SystemClock.sleep(Math.min(80, ViewConfiguration.getDoubleTapTimeout() / 3));
                    gesture.swipe(x, y, 144, 0, false);
                    break;
                case "scroll-vertical": gesture.swipe(x, y, 0, -192, true); break;
                case "scroll-horizontal": gesture.swipe(x, y, -192, 0, true); break;
                default: throw new IllegalArgumentException("Unknown gesture.");
            }
        } finally {
            if (gesture.contacts != 0) {
                gesture.send(MotionEvent.ACTION_CANCEL, gesture.contacts, x, y);
            }
        }
    }

    private void tap(int x, int y) throws Exception {
        send(MotionEvent.ACTION_DOWN, 1, x, y);
        SystemClock.sleep(40);
        send(MotionEvent.ACTION_UP, 1, x, y);
    }

    private void swipe(int x, int y, int dx, int dy, boolean twoFingers) throws Exception {
        send(MotionEvent.ACTION_DOWN, 1, x, y);
        int count = twoFingers ? 2 : 1;
        int second = 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        if (twoFingers) {
            send(MotionEvent.ACTION_POINTER_DOWN | second, count, x, y);
        }
        for (int step = 1; step <= 8; step++) {
            SystemClock.sleep(24);
            send(MotionEvent.ACTION_MOVE, count, x + dx * step / 8, y + dy * step / 8);
        }
        if (twoFingers) {
            send(MotionEvent.ACTION_POINTER_UP | second, count, x + dx, y + dy);
        }
        send(MotionEvent.ACTION_UP, 1, x + dx, y + dy);
    }

    private void send(int action, int count, float x, float y) throws Exception {
        int masked = action & MotionEvent.ACTION_MASK;
        long now = SystemClock.uptimeMillis();
        if (masked == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] points = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            points[i] = new MotionEvent.PointerCoords();
            points[i].x = x + 64 * i;
            points[i].y = y;
            points[i].pressure = 1;
        }
        MotionEvent event = MotionEvent.obtain(downTime, now, action, count, properties, points,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0, 0, MotionEvent.CLASSIFICATION_NONE);
        try {
            if (!Boolean.TRUE.equals(inject.invoke(manager, event, 2, uid))) {
                throw new IllegalStateException("Android rejected input to the app UID.");
            }
            contacts = switch (masked) {
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0;
                case MotionEvent.ACTION_POINTER_UP -> count - 1;
                default -> count;
            };
        } finally {
            event.recycle();
        }
    }
}
