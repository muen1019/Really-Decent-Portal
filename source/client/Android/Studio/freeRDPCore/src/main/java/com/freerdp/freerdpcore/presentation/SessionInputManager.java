/*
   Modified 2026-11-05: merge upstream 3.33.0 legacy move throttle with device and button-owner cleanup.
   Modified 2026-11-05: support guarded client-owned captured-key repeat.
   Android Session Input Manager
   Modified 2026-10-30: retire pending legacy hardware tap-drag at session lifecycle boundaries.

   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   Modified 2026-10-29: preserve physical key lifetimes before local IME processing.

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

// Modified 2026-10-30: preserve classified physical touchpad wheel resolution.

// Modified 2026-10-31: add opt-in physical-Alt touchpad browser navigation.
package com.freerdp.freerdpcore.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.freerdp.freerdpcore.utils.KeyboardMapper;
import com.freerdp.freerdpcore.utils.Mouse;
import com.freerdp.freerdpcore.utils.TouchpadWheelAccumulator;
import com.freerdp.freerdpcore.utils.ScreenInputController;
import com.freerdp.freerdpcore.utils.PenInputController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SessionInputManager
    implements SessionView.SessionViewListener, TouchPointerView.TouchPointerListener,
               KeyboardMapper.KeyProcessingListener, ExtendedKeyboardView.Listener,
               InputManager.InputDeviceListener, ScreenInputController.Sink, PenInputController.Sink
{
	private static final String TAG = "FreeRDP.SessionInputManager";

	private static final int SCROLLING_TIMEOUT = 16;
	private static final int SCROLLING_DISTANCE = 12;
	private static final int SCROLLING_EDGE_MARGIN = 16;
	private static final int MOVE_EVENT_THROTTLE_MS = 16;

	private static final int MSG_SEND_MOVE_EVENT = 1;
	private static final int MSG_SCROLLING_REQUESTED = 2;
	private static final int MSG_SCREEN_INPUT_STATUS = 3;

	private final Context context;
	private final KeyboardMapper keyboardMapper;
	private final ScrollView2D scrollView;
	private final SessionView sessionView;
	private final TouchPointerView touchPointerView;
	private final ExtendedKeyboardView keyboard;
	private final PinchZoomListener pinchZoomListener = new PinchZoomListener();

	// Native FreeRDP instance handle. 0 until attachSession() is called (i.e. before connect).
	private long instance = 0;
	private Bitmap bitmap;
	private int screenWidth;
	private int screenHeight;
	private long lastMoveSentTime = 0;
	// we asked the IME to show; the window has not necessarily animated it in yet
	private boolean softInputRequested = false;
	// the IME reported a non-zero inset, i.e. it really is on screen
	private boolean softInputVisible = false;

	private final Handler handler;
	private final InputManager deviceManager;
	private boolean listeningForDevices;
	private boolean keyboardInputActive;
	private final MouseButtonOwnership mouseButtons = new MouseButtonOwnership();
	private final Set<MouseNavigationStream> mouseNavigationKeys = new LinkedHashSet<>();
	private final Set<MouseNavigationStream> completedMouseNavigationKeys = new LinkedHashSet<>();
	private final Map<Integer, Integer> mouseMotionButtons = new HashMap<>();
	private int mouseX;
	private int mouseY;
	private Integer pointerDeviceId;
	private Integer delayedMoveDeviceId;
	private Runnable screenInputStateListener;

	public SessionInputManager(Context context, ScrollView2D scrollView, SessionView sessionView,
	                           TouchPointerView touchPointerView, ExtendedKeyboardView keyboard)
	{
		this.context = context;
		this.scrollView = scrollView;
		this.sessionView = sessionView;
		this.touchPointerView = touchPointerView;
		this.keyboard = keyboard;
		this.handler = new InputHandler();
		this.deviceManager = (InputManager)context.getSystemService(Context.INPUT_SERVICE);

		this.keyboardMapper = new KeyboardMapper();
		this.keyboardMapper.init(context);

		keyboard.setListener(this);
		// The old small touch-pointer overlay is replaced by whole-screen modes.
		touchPointerView.setVisibility(View.INVISIBLE);
		sessionView.setTouchPointerPadding(0, 0);
		sessionView.setScreenInputSink(this);
		sessionView.setPenInputSink(this);
		sessionView.setScreenInputMode(ApplicationSettingsActivity.getScreenInputMode(context));
		sessionView.setScreenInputActive(false);
	}

	// Binds this manager to a live FreeRDP session. Until called, all input events are dropped.
	public void attachSession(long instance, Bitmap surface)
	{
		// Release against the old transport before replacing its handle. Cleanup leaves only
		// matching-up tombstones, so an old stream cannot release a key in the new session.
		releaseOwnedKeys(this.instance != 0);
		cancelScreenInput();
		cancelPenInput();
		releaseMouseButtons(this.instance != 0);
		cancelPendingEvents();
		this.instance = instance;
		this.bitmap = surface;
		this.lastMoveSentTime = 0;
		keyboardMapper.reset(this);
		refreshScreenInputStatus();
		if (instance != 0 && deviceManager != null && !listeningForDevices)
		{
			deviceManager.registerInputDeviceListener(this, handler);
			listeningForDevices = true;
		}
	}

	// A failed/disconnected transport must never receive delayed key-up events on reconnect.
	public void detachSession(boolean sendKeyUps)
	{
		releaseOwnedKeys(sendKeyUps && instance != 0);
		// A dead transport must not receive releases. Retire it before the gesture
		// controller invokes its cancellation callbacks.
		long oldInstance = instance;
		if (!sendKeyUps)
			instance = 0;
		cancelScreenInput();
		cancelPenInput();
		releaseMouseButtons(sendKeyUps && oldInstance != 0);
		instance = 0;
		sessionView.setScreenInputActive(false);
		bitmap = null;
		cancelPendingEvents();
		refreshScreenInputStatus();
		if (deviceManager != null && listeningForDevices)
		{
			deviceManager.unregisterInputDeviceListener(this);
			listeningForDevices = false;
		}
	}

	public void setKeyboardInputActive(boolean active)
	{
		// Retire live contacts while the old session is still eligible to send CANCEL.
		sessionView.setScreenInputActive(active && instance != 0);
		keyboardInputActive = active;
		if (!active)
		{
			releaseOwnedKeys(instance != 0);
			cancelScreenInput();
			cancelPenInput();
			releaseMouseButtons(instance != 0);
			cancelPendingEvents();
		}
		refreshScreenInputStatus();
	}

	public void releaseOwnedKeys(boolean sendKeyUps)
	{
		keyboardMapper.releaseOwnedKeys(sendKeyUps && instance != 0);
	}

	public boolean consumeOwnedKeyUp(KeyEvent event)
	{
		if (event.getAction() == KeyEvent.ACTION_UP &&
		    completedMouseNavigationKeys.contains(new MouseNavigationStream(event)))
			return true;
		return keyboardMapper.consumeOwnedKeyUp(event);
	}

	/** Lifecycle gate and held-key repeat happen in one main-loop operation. */
	public boolean repeatCapturedKey(int device, int key, long downTime)
	{
		return instance != 0 && keyboardInputActive &&
		    keyboardMapper.repeatHeldPhysicalKey(device, key, downTime);
	}

	public boolean hasOwnedKey(KeyEvent event)
	{
		MouseNavigationStream stream = new MouseNavigationStream(event);
		return mouseNavigationKeys.contains(stream) || completedMouseNavigationKeys.contains(stream) ||
		    keyboardMapper.hasOwnedKey(event);
	}

	@Override public void onInputDeviceAdded(int deviceId)
	{
	}

	@Override public void onInputDeviceChanged(int deviceId)
	{
		sessionView.cancelBrowserNavigationInput();
		retirePointerDevice(deviceId);
		sessionView.cancelScreenInputDevice(deviceId);
		sessionView.cancelPenInputDevice(deviceId);
	}

	@Override public void onInputDeviceRemoved(int deviceId)
	{
		sessionView.cancelBrowserNavigationInput();
		keyboardMapper.releaseDeviceKeys(deviceId, instance != 0);
		retirePointerDevice(deviceId);
		sessionView.cancelScreenInputDevice(deviceId);
		sessionView.cancelPenInputDevice(deviceId);
	}

	private void retirePointerDevice(int deviceId)
	{
		retireMouseNavigationKeys(deviceId);
		mouseMotionButtons.remove(deviceId);
		sendMouseReleases(mouseButtons.releaseDevice(deviceId));
		if (delayedMoveDeviceId != null && delayedMoveDeviceId == deviceId)
			cancelDelayedMoveEvent();
		if (pointerDeviceId != null && pointerDeviceId == deviceId)
			pointerDeviceId = null;
	}

	// Called when the session bitmap is created or replaced (OnSettingsChanged / OnGraphicsResize).
	public void setBitmap(Bitmap bitmap)
	{
		this.bitmap = bitmap;
	}

	// Returns a listener that can be wired into a ScaleGestureDetector for pinch-to-zoom.
	public ScaleGestureDetector.OnScaleGestureListener getPinchZoomListener()
	{
		return pinchZoomListener;
	}

	// Called once the screen dimensions are known (onGlobalLayout) and on bindSession.
	public void setScreenSize(int width, int height)
	{
		if (screenWidth != width || screenHeight != height)
			cancelScreenInput();
		this.screenWidth = width;
		this.screenHeight = height;
	}

	// Shows or hides the key bar together with the system IME.
	public void toggleKeyboard()
	{
		if (keyboard.getVisibility() == View.VISIBLE)
		{
			hideKeyboards();
		}
		else
		{
			keyboard.setExpanded(false, false);
			keyboard.setVisibility(View.VISIBLE);
			setSoftInputState(true);
		}
	}

	// Called from onPause and back-press handling.
	public void hideKeyboards()
	{
		keyboard.setExpanded(false, false);
		keyboard.setVisibility(View.GONE);
		setSoftInputState(false);
		keyboardMapper.clearlAllModifiers();
		// the IME dismiss animation may re-show the nav bar after the refresh above
		scrollView.post(this::refreshSystemBars);
	}

	// Returns true if the back press was consumed by the keyboard.
	public boolean handleKeyboardBack()
	{
		if (keyboard.getVisibility() != View.VISIBLE)
			return false;

		if (keyboard.isExpanded())
		{
			keyboard.setExpanded(false, false);
			// deliberately no IME here, so the next back press hides the bar as well
			refreshSystemBars();
			scrollView.requestApplyInsets();
			return true;
		}

		hideKeyboards();
		return true;
	}

	// True if the system soft keyboard (IME) is up or on its way up.
	public boolean isSoftInputActive()
	{
		return keyboard.getVisibility() == View.VISIBLE && (softInputRequested || softInputVisible);
	}

	// Fed from the window insets listener. The IME only counts as gone once it has been seen on
	// screen: the insets pass right after showSoftInput() still reports a zero inset.
	public void onImeVisibilityChanged(boolean visible)
	{
		if (visible == softInputVisible)
			return;
		softInputVisible = visible;
		if (!visible)
			softInputRequested = false;
		refreshSystemBars();
	}

	private void refreshSystemBars()
	{
		if (context instanceof SessionActivity)
			((SessionActivity)context).hideSystemBars();
	}

	private void setSoftInputState(boolean state)
	{
		softInputRequested = state;
		if (!state)
			softInputVisible = false;
		InputMethodManager mgr =
		    (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);

		if (state)
		{
			sessionView.requestFocus();
			mgr.showSoftInput(sessionView, InputMethodManager.SHOW_IMPLICIT);
		}
		else
		{
			mgr.hideSoftInputFromWindow(sessionView.getWindowToken(), 0);
		}
		refreshSystemBars();
		scrollView.requestApplyInsets();
	}

	// Cancels any pending delayed-move events; called on connection failure / disconnect.
	public void cancelPendingEvents()
	{
		sessionView.cancelLegacyPointerInput();
		cancelDelayedMoveEvent();
		sessionView.cancelTouchpadWheelInput();
		handler.removeMessages(MSG_SCROLLING_REQUESTED);
		handler.removeMessages(MSG_SCREEN_INPUT_STATUS);
	}

	// Forwards a physical-mouse scroll event (e.g. external mouse wheel) into the session.
	public boolean onGenericMotionEvent(MotionEvent e)
	{
		if (instance == 0) return false;
		return sessionView.handleGenericWheelEvent(e);
	}

	@Override public boolean onSessionViewPreImeKeyEvent(KeyEvent event)
	{
		if (instance == 0 || !keyboardInputActive)
			return keyboardMapper.consumeOwnedKeyUp(event);
		return keyboardMapper.processPhysicalKeyEventBeforeIme(event);
	}

	// Forwards an Android hardware-keyboard event into the session.
	public boolean onAndroidKeyEvent(KeyEvent event)
	{
		return onAndroidKeyEvent(event, KeyboardMapper.KeyEventOrigin.ACTIVITY);
	}

	public boolean onAndroidKeyEvent(KeyEvent event, KeyboardMapper.KeyEventOrigin origin)
	{
		if (onMouseNavigationKeyEvent(event))
			return true;
		if (instance == 0 || !keyboardInputActive)
			return keyboardMapper.consumeOwnedKeyUp(event);
		return keyboardMapper.processAndroidKeyEvent(event, origin);
	}

	/** Mouse side keys are mouse buttons, even when Android also emits a KeyEvent. */
	public boolean onMouseNavigationKeyEvent(KeyEvent event)
	{
		int action = event.getAction();
		if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)
			return false;
		MouseNavigationStream stream = new MouseNavigationStream(event);
		if (completedMouseNavigationKeys.contains(stream))
			return true; // Includes duplicate callbacks and late ups after focus/session cleanup.
		for (MouseNavigationStream owned : new ArrayList<>(mouseNavigationKeys))
		{
			if (!owned.equals(stream))
				continue;
			if (action == KeyEvent.ACTION_UP && isSelfTimestampMouseUp(event) &&
			    event.getEventTime() <= owned.lastKeyEventTime)
				return true; // Equal timestamps cannot prove an UP belongs after this DOWN.
			if (action == KeyEvent.ACTION_UP)
				completeMouseNavigationKey(owned);
			else
				owned.lastKeyEventTime = Math.max(owned.lastKeyEventTime, event.getEventTime());
			return true; // Repeats and Motion/Key/Accessibility duplicates never pulse a side button.
		}
		if ((event.getKeyCode() != KeyEvent.KEYCODE_BACK &&
		     event.getKeyCode() != KeyEvent.KEYCODE_FORWARD) ||
		    (!event.isFromSource(InputDevice.SOURCE_MOUSE) &&
		     !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)))
			return false;
		// A recognized mouse side key must not become local Android Back. Inactive sessions
		// consume it without creating ownership; a new stream requires an initial down.
		if (action == KeyEvent.ACTION_UP)
		{
			// AOSP synthesizeButtonKey uses each edge's own eventTime as downTime.
			// Only an unpaired key-only stream can use this narrow compatibility form.
			if (isSelfTimestampMouseUp(event))
			{
				if (instance != 0 && keyboardInputActive)
				{
					for (MouseNavigationStream owned : new ArrayList<>(mouseNavigationKeys))
					{
						if (owned.device == stream.device && owned.key == stream.key &&
						    owned.scanCode == 0 && owned.allowSelfTimestampUp && !owned.motionPaired &&
						    !hasMouseMotionLease(owned.device, owned.button()) &&
						    event.getEventTime() > owned.downTime &&
						    event.getEventTime() > owned.lastKeyEventTime)
						{
							completeMouseNavigationKey(owned);
							break;
						}
					}
				}
				// Remember the UP's own signature as well as any completed DOWN stream.
				// Its duplicate must not acquire a later key or Motion hold.
				rememberMouseNavigationKey(stream);
			}
			return true;
		}
		if (instance == 0 || !keyboardInputActive || event.getRepeatCount() != 0)
			return true;
		stream.motionPaired = hasMouseMotionLease(stream.device, stream.button());
		for (MouseNavigationStream old : new ArrayList<>(mouseNavigationKeys))
		{
			if (old.device == stream.device && old.key == stream.key)
			{
				if (stream.downTime <= old.downTime || event.getEventTime() <= old.lastKeyEventTime)
					return true; // An older delayed down cannot replace a newer hold.
				mouseNavigationKeys.remove(old);
				rememberMouseNavigationKey(old);
				// Without an old UP, a later self-timestamp UP cannot identify its generation.
				// Keep exact matching and Motion cleanup, but do not guess for this replacement.
				stream.allowSelfTimestampUp = false;
				if (!hasMouseMotionLease(old.device, old.button()))
				{
					onSessionViewPointerDevice(old.device);
					sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, old.button(), false, mouseX, mouseY);
				}
			}
		}
		mouseNavigationKeys.add(stream);
		onSessionViewPointerDevice(stream.device);
		if (!sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, stream.button(), true, mouseX, mouseY))
		{
			mouseNavigationKeys.remove(stream);
			rememberMouseNavigationKey(stream);
		}
		return true;
	}

	private static boolean isSelfTimestampMouseUp(KeyEvent event)
	{
		return event.getAction() == KeyEvent.ACTION_UP && event.getScanCode() == 0 &&
		    event.getRepeatCount() == 0 && !event.isCanceled() &&
		    event.getDownTime() == event.getEventTime();
	}

	private boolean hasMouseMotionLease(int deviceId, int button)
	{
		return (mouseMotionButtons.getOrDefault(deviceId, 0) & MouseButtonOwnership.bit(button)) != 0;
	}

	private void completeMouseNavigationKey(MouseNavigationStream stream)
	{
		mouseNavigationKeys.remove(stream);
		rememberMouseNavigationKey(stream);
		// Exact stream matching retains the existing Motion/Key first-release deduplication.
		// The self-timestamp caller separately requires an unpaired key-only stream.
		onSessionViewPointerDevice(stream.device);
		sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, stream.button(), false, mouseX, mouseY);
	}

	private void rememberMouseNavigationKey(MouseNavigationStream stream)
	{
		completedMouseNavigationKeys.add(stream);
		while (completedMouseNavigationKeys.size() > 128)
			completedMouseNavigationKeys.remove(completedMouseNavigationKeys.iterator().next());
	}

	private void retireMouseNavigationKeys(Integer deviceId)
	{
		retireMouseNavigationKeys(deviceId, 0);
	}

	private void retireMouseNavigationKeys(Integer deviceId, int button)
	{
		for (MouseNavigationStream stream : new ArrayList<>(mouseNavigationKeys))
		{
			if ((deviceId == null || stream.device == deviceId) &&
			    (button == 0 || stream.button() == button))
			{
				mouseNavigationKeys.remove(stream);
				rememberMouseNavigationKey(stream);
			}
		}
	}

	private static final class MouseNavigationStream
	{
		final int device;
		final int key;
		final long downTime;
		final int scanCode;
		long lastKeyEventTime;
		boolean motionPaired;
		boolean allowSelfTimestampUp = true;

		MouseNavigationStream(KeyEvent event)
		{
			device = event.getDeviceId();
			key = event.getKeyCode();
			downTime = event.getDownTime();
			scanCode = event.getScanCode();
			lastKeyEventTime = event.getEventTime();
		}

		int button() { return key == KeyEvent.KEYCODE_BACK ? 4 : 5; }

		@Override public boolean equals(Object other)
		{
			if (!(other instanceof MouseNavigationStream)) return false;
			MouseNavigationStream stream = (MouseNavigationStream)other;
			return device == stream.device && key == stream.key && downTime == stream.downTime;
		}

		@Override public int hashCode()
		{
			return 31 * (31 * device + key) + Long.hashCode(downTime);
		}
	}

	// Handles a long-press on the BACK key by disconnecting the active session.
	// Returns true if the event was consumed.
	public boolean onAndroidKeyLongPress(int keyCode)
	{
		if (instance == 0)
			return false;
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			LibFreeRDP.disconnect(instance);
			return true;
		}
		return false;
	}

	// If the "use back as Alt+F4" preference is enabled, sends Alt+F4 and returns true.
	public boolean handleBackAsAltF4()
	{
		if (instance == 0)
			return false;
		if (!ApplicationSettingsActivity.getUseBackAsAltf4(context))
			return false;
		keyboardMapper.sendAltF4();
		return true;
	}

	// The toolbar changes only screen fingers; hardware mouse/trackpad routing is unchanged.
	public void toggleTouchPointer()
	{
		setScreenInputMode(getScreenInputMode() == ScreenInputMode.TOUCHPAD
		                       ? ScreenInputMode.DIRECT_TOUCH
		                       : ScreenInputMode.TOUCHPAD);
	}

	public ScreenInputMode getScreenInputMode()
	{
		return sessionView.getScreenInputMode();
	}

	public void setScreenInputMode(ScreenInputMode mode)
	{
		if (mode == getScreenInputMode())
			return;
		cancelScreenInput();
		sessionView.setScreenInputMode(mode);
		ApplicationSettingsActivity.setScreenInputMode(context, mode);
		refreshScreenInputStatus();
	}

	public int getScreenInputNativeState()
	{
		return instance == 0 ? 0 : LibFreeRDP.getNativeTouchState(instance);
	}

	public void setScreenInputStateListener(Runnable listener)
	{
		screenInputStateListener = listener;
		refreshScreenInputStatus();
	}

	private void refreshScreenInputStatus()
	{
		handler.removeMessages(MSG_SCREEN_INPUT_STATUS);
		if (screenInputStateListener != null)
			screenInputStateListener.run();
		if (keyboardInputActive && instance != 0)
			handler.sendEmptyMessageDelayed(MSG_SCREEN_INPUT_STATUS, 1000);
	}

	public void cancelScreenInput()
	{
		sessionView.cancelScreenInput();
		if (instance != 0)
			LibFreeRDP.cancelTouchEvents(instance);
		releaseMouseOwner(MouseButtonOwnership.SCREEN);
		cancelDelayedMoveEvent();
	}

	public void cancelPenInput()
	{
		sessionView.cancelPenInput();
		if (instance != 0)
			LibFreeRDP.cancelPenEvents(instance);
	}

	@Override public int getNativePenState()
	{
		return keyboardInputActive && instance != 0 ? LibFreeRDP.getNativePenState(instance) : 0;
	}

	@Override public int getNegotiatedVersion()
	{
		return instance == 0 ? 0 : LibFreeRDP.getNativePenVersion(instance);
	}

	@Override public boolean onPenFrame(PenInputController.PenFrame frame)
	{
		if (instance == 0)
			return false;
		if (frame.action == PenInputController.CANCEL)
			return LibFreeRDP.cancelPenEvents(instance);
		if (!keyboardInputActive)
			return false;
		int penFlags = frame.toolType == MotionEvent.TOOL_TYPE_ERASER ? 0x0002 : 0;
  	if ((frame.buttonState & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
  		penFlags |= 0x0001;
		return LibFreeRDP.sendPenFrame(instance,frame.action,frame.x,frame.y,frame.pressure,penFlags);
	}

	// ScreenInputController.Sink

	@Override public boolean isDirectTouchAvailable()
	{
		return keyboardInputActive && instance != 0 && getScreenInputNativeState() == 1;
	}

	@Override public boolean onTouchFrame(ScreenInputController.TouchFrame frame)
	{
		return instance != 0 &&
		    LibFreeRDP.sendTouchFrame(instance, frame.ids, frame.actions, frame.x, frame.y);
	}

	@Override public void onMouseMove(int x, int y)
	{
		if (instance == 0)
			return;
		updateMousePosition(x, y);
		LibFreeRDP.sendCursorEvent(instance, x, y, Mouse.getMoveEvent());
	}

	@Override public void onMouseButton(int button, boolean down, int x, int y)
	{
		sendOwnedMouseButton(MouseButtonOwnership.SCREEN, button, down, x, y);
	}

	@Override public void onScroll(boolean horizontal, boolean positive)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendCursorEvent(instance, 0, 0,
		                          horizontal ? Mouse.getHScrollEvent(context, positive)
		                                     : Mouse.getScrollEvent(context, positive));
	}

	@Override public void onLocalZoom(float factor, float focusX, float focusY)
	{
		// SessionView's sink wrapper applies pinch zoom in the current gesture without
		// restarting its contact stream; ordinary setZoom() intentionally cancels it.
	}

	// ****************************************************************************
	// Private helpers

	private void updateMousePosition(int x, int y)
	{
		mouseX = x;
		mouseY = y;
	}

	private void updateHardwareMousePosition(int x, int y)
	{
		updateMousePosition(x, y);
		sessionView.updateScreenPointer(x, y);
	}

	private boolean sendOwnedMouseButton(int owner, int button, boolean down, int x, int y)
	{
		if (instance == 0)
			return false;
		// No older coalesced move may cross a button edge from any input owner.
		cancelDelayedMoveEvent();
		if (owner == MouseButtonOwnership.HARDWARE)
			updateHardwareMousePosition(x, y);
		else
			updateMousePosition(x, y);
		Integer deviceId = owner == MouseButtonOwnership.HARDWARE ? pointerDeviceId : null;
		int transition = deviceId != null
		    ? mouseButtons.updateHardware(deviceId, button, down)
		    : mouseButtons.update(owner, button, down);
		if (transition == 0 || sendMouseButton(button, transition > 0))
			return true;
		// A rejected enqueue must not leave an unsent down owned by this device.
		if (deviceId != null)
			mouseButtons.updateHardware(deviceId, button, !down);
		else
			mouseButtons.update(owner, button, !down);
		return false;
	}

	private boolean sendMouseButton(int button, boolean down)
	{
		if (instance == 0)
			return false;
		if (button == 4 || button == 5)
			// A captured release may lie outside the View; it still needs legal wire coordinates.
			return LibFreeRDP.sendExtendedCursorEvent(instance,
			    Math.max(0, Math.min(0xFFFF, mouseX)), Math.max(0, Math.min(0xFFFF, mouseY)),
			    Mouse.getExtendedButtonEvent(button, down));
		int flags = button == 1 ? Mouse.getLeftButtonEvent(context, down)
		                       : button == 2 ? Mouse.getRightButtonEvent(context, down)
		                                     : Mouse.getMiddleButtonEvent(down);
		return LibFreeRDP.sendCursorEvent(instance, mouseX, mouseY, flags);
	}

	private void releaseMouseOwner(int owner)
	{
		sendMouseReleases(mouseButtons.releaseOwner(owner));
	}

	private void releaseMouseButtons(boolean sendUps)
	{
		retireMouseNavigationKeys(null);
		mouseMotionButtons.clear();
		int releases = mouseButtons.clear();
		pointerDeviceId = null;
		if (sendUps)
			sendMouseReleases(releases);
	}

	private void sendMouseReleases(int releases)
	{
		for (int button = 1; button <= 5; button++)
		{
			if ((releases & MouseButtonOwnership.bit(button)) != 0)
				sendMouseButton(button, false);
		}
	}

	private void sendDelayedMoveEvent(int x, int y)
	{
		// Only the legacy gesture fallback uses this 16 ms coalescer.
		// Physical pointer and explicit screen-engine movement retain direct JNI dispatch.
		final long now = SystemClock.uptimeMillis();
		final long elapsed = now - lastMoveSentTime;
		cancelDelayedMoveEvent();
		if (elapsed >= MOVE_EVENT_THROTTLE_MS)
		{
			lastMoveSentTime = now;
			LibFreeRDP.sendCursorEvent(instance, x, y, Mouse.getMoveEvent());
		}
		else
		{
			delayedMoveDeviceId = pointerDeviceId;
			final long delay = MOVE_EVENT_THROTTLE_MS - elapsed;
			handler.sendMessageDelayed(Message.obtain(null, MSG_SEND_MOVE_EVENT, x, y), delay);
		}
	}

	private void cancelDelayedMoveEvent()
	{
		handler.removeMessages(MSG_SEND_MOVE_EVENT);
		delayedMoveDeviceId = null;
	}

	private Point mapScreenCoordToSessionCoord(int x, int y)
	{
		View container = scrollView.getChildCount() > 0 ? scrollView.getChildAt(0) : sessionView;
		int mappedX = (int)((float)(x - container.getLeft() + scrollView.getScrollX()) /
		                    sessionView.getZoom());
		int mappedY = (int)((float)(y - container.getTop() + scrollView.getScrollY()) /
		                    sessionView.getZoom());
		if (bitmap != null)
		{
			if (mappedX < 0)
				mappedX = 0;
			if (mappedY < 0)
				mappedY = 0;
			if (mappedX > bitmap.getWidth())
				mappedX = bitmap.getWidth();
			if (mappedY > bitmap.getHeight())
				mappedY = bitmap.getHeight();
		}
		return new Point(mappedX, mappedY);
	}

	// ****************************************************************************
	// SessionView.SessionViewListener

	@Override public void onSessionViewPointerDevice(int deviceId)
	{
		pointerDeviceId = deviceId;
	}

	@Override public void onSessionViewPointerCancel(int deviceId)
	{
		retirePointerDevice(deviceId);
	}

	@Override public void onSessionViewBeginTouch()
	{
		scrollView.setScrollEnabled(false);
	}

	@Override public void onSessionViewEndTouch()
	{
		scrollView.setScrollEnabled(true);
	}

	@Override public void onSessionViewLeftTouch(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, 1, down, x, y);
	}

	@Override public void onSessionViewMiddleTouch(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, 3, down, x, y);
	}

	@Override public void onSessionViewRightTouch(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, 2, down, x, y);
	}

	@Override public void onSessionViewXButton(int x, int y, int button, boolean down)
	{
		if (button != 4 && button != 5)
			throw new IllegalArgumentException("Unsupported extended mouse button");
		if (down)
		{
			if (sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, button, true, x, y) &&
			    pointerDeviceId != null)
			{
				int held = mouseMotionButtons.getOrDefault(pointerDeviceId, 0);
				mouseMotionButtons.put(pointerDeviceId, held | MouseButtonOwnership.bit(button));
				for (MouseNavigationStream stream : mouseNavigationKeys)
				{
					if (stream.device == pointerDeviceId && stream.button() == button)
						stream.motionPaired = true;
				}
			}
			return;
		}
		// Motion and KeyEvent are two Android representations of the same side key.
		// Once Motion ends it, an older matching Key up must not release a fresh hold.
		if (pointerDeviceId != null)
		{
			int held = mouseMotionButtons.getOrDefault(pointerDeviceId, 0) & ~MouseButtonOwnership.bit(button);
			if (held == 0)
				mouseMotionButtons.remove(pointerDeviceId);
			else
				mouseMotionButtons.put(pointerDeviceId, held);
			retireMouseNavigationKeys(pointerDeviceId, button);
		}
		sendOwnedMouseButton(MouseButtonOwnership.HARDWARE, button, false, x, y);
	}

	@Override public void onSessionViewMove(int x, int y)
	{
		if (instance == 0)
			return;
		updateHardwareMousePosition(x, y);
		sendDelayedMoveEvent(x, y);
	}

	@Override public void onSessionViewMouseMove(int x, int y)
	{
		if (instance == 0)
			return;
		updateHardwareMousePosition(x, y);
		LibFreeRDP.sendCursorEvent(instance, x, y, Mouse.getMoveEvent());
	}

	@Override public boolean onSessionViewBrowserNavigationActive()
	{
		return instance != 0 && keyboardInputActive &&
		    PreferenceManager.getDefaultSharedPreferences(context).getBoolean("ui.alt_touchpad_navigation", false) &&
		    keyboardMapper.isPhysicalAltNavigationActive();
	}

	@Override public void onSessionViewBrowserNavigate(boolean forward)
	{
		if (onSessionViewBrowserNavigationActive())
			keyboardMapper.sendBrowserNavigationWithPhysicalAlt(forward);
	}

	@Override public void physicalAltReleased()
	{
		sessionView.cancelBrowserNavigationInput();
	}

	/** Same-time Chromium compatibility: preserve each axis sequence across one callback's chunks. */
	@Override public void onSessionViewWheelBatch(int[] horizontal, int[] vertical, int count)
	{
		if (instance == 0) return;
		if (horizontal == null || vertical == null || count < 0 ||
		    count > TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT ||
		    count > horizontal.length || count > vertical.length)
			throw new IllegalArgumentException("Invalid wheel callback batch");
		for (int i = 0; i < count; i++)
			if (horizontal[i] < -255 || horizontal[i] > 255 || vertical[i] < -255 || vertical[i] > 255)
				throw new IllegalArgumentException("Wheel chunk outside inversion-safe range");
		// No summation or deferred queue: each signed chunk remains a separate RDP packet.
		for (int i = 0; i < count; i++)
			if (vertical[i] != 0)
				LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getScrollEvent(context, vertical[i]));
		for (int i = 0; i < count; i++)
			if (horizontal[i] != 0)
				LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getHScrollEvent(context, horizontal[i]));
	}

	@Override public void onSessionViewWheelDelta(int horizontal, int vertical)
	{
		if (instance == 0) return;
		if (horizontal < -255 || horizontal > 255 || vertical < -255 || vertical > 255)
			throw new IllegalArgumentException("Wheel chunk outside inversion-safe range");
		// Compatibility candidate for Chromium's same-time HWHEEL -> WHEEL rewrite:
		// https://chromium.googlesource.com/chromium/src/+/refs/heads/main/ui/views/win/hwnd_message_handler.cc#3515
		// Equal native timestamps across successive pairs still need real-session validation.
		if (vertical != 0)
			LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getScrollEvent(context, vertical));
		if (horizontal != 0)
			LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getHScrollEvent(context, horizontal));
	}

	@Override public void onSessionViewScroll(boolean down)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getScrollEvent(context, down));
	}


	@Override public void onSessionViewContentZoom(boolean zoomIn)
	{
		if (instance == 0 || !keyboardInputActive)
			return;
		keyboardMapper.withControlModifier(() ->
		    LibFreeRDP.sendCursorEvent(instance, mouseX, mouseY, Mouse.getContentZoomEvent(zoomIn)));
	}

	@Override public void onSessionViewHScroll(boolean right)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getHScrollEvent(context, right));
	}

	// ****************************************************************************
	// TouchPointerView.TouchPointerListener

	@Override public void onTouchPointerClose()
	{
		touchPointerView.setVisibility(View.INVISIBLE);
		sessionView.setTouchPointerPadding(0, 0);
	}

	@Override public void onTouchPointerLeftClick(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		Point p = mapScreenCoordToSessionCoord(x, y);
		LibFreeRDP.sendCursorEvent(instance, p.x, p.y, Mouse.getLeftButtonEvent(context, down));
	}

	@Override public void onTouchPointerRightClick(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		Point p = mapScreenCoordToSessionCoord(x, y);
		LibFreeRDP.sendCursorEvent(instance, p.x, p.y, Mouse.getRightButtonEvent(context, down));
	}

	@Override public void onTouchPointerMiddleClick(int x, int y, boolean down)
	{
		if (instance == 0)
			return;
		Point p = mapScreenCoordToSessionCoord(x, y);
		LibFreeRDP.sendCursorEvent(instance, p.x, p.y, Mouse.getMiddleButtonEvent(down));
	}

	@Override public void onTouchPointerMove(int x, int y)
	{
		if (instance == 0)
			return;
		Point p = mapScreenCoordToSessionCoord(x, y);
		LibFreeRDP.sendCursorEvent(instance, p.x, p.y, Mouse.getMoveEvent());

		if (ApplicationSettingsActivity.getAutoScrollTouchPointer(context) &&
		    !handler.hasMessages(MSG_SCROLLING_REQUESTED))
		{
			handler.sendEmptyMessageDelayed(MSG_SCROLLING_REQUESTED, SCROLLING_TIMEOUT);
		}
	}

	@Override public void onTouchPointerMoveEnd()
	{
		handler.removeMessages(MSG_SCROLLING_REQUESTED);
	}

	@Override public void onTouchPointerScroll(int amount)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getScrollEvent(context, amount));
	}

	@Override public void onTouchPointerHScroll(int amount)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendCursorEvent(instance, 0, 0, Mouse.getHScrollEvent(context, amount));
	}

	@Override public void onTouchPointerToggleKeyboard()
	{
		toggleKeyboard();
	}

	@Override public void onTouchPointerResetScrollZoom()
	{
		sessionView.setZoom(1.0f);
		scrollView.scrollTo(0, 0);
	}

	// ****************************************************************************
	// KeyboardMapper.KeyProcessingListener

	@Override public void processVirtualKey(int virtualKeyCode, boolean down)
	{
		if (instance == 0)
			return;
		LibFreeRDP.sendKeyEvent(instance, virtualKeyCode, down);
	}

	@Override public void processUnicodeKey(int unicodeKey)
	{
		if (instance == 0)
			return;
		if (LibFreeRDP.isUnicodeInputSupported(instance))
		{
			LibFreeRDP.sendUnicodeKeyEvent(instance, unicodeKey, true);
			LibFreeRDP.sendUnicodeKeyEvent(instance, unicodeKey, false);
		}
		else
			keyboardMapper.processUnicodeFallback(unicodeKey);
	}

	@Override public void switchKeyboard(int keyboardType)
	{
		switch (keyboardType)
		{
			case KeyboardMapper.KEYBOARD_TYPE_FUNCTIONKEYS:
				keyboard.selectPage(ExtendedKeyboardView.PAGE_SPECIAL);
				break;

			case KeyboardMapper.KEYBOARD_TYPE_NUMPAD:
				keyboard.selectPage(ExtendedKeyboardView.PAGE_NUM);
				break;

			default:
				break;
		}
	}

	@Override public void modifiersChanged()
	{
		keyboard.refreshModifiers();
	}

	// ****************************************************************************
	// ExtendedKeyboardView.Listener

	@Override public void onKey(int keycode)
	{
		keyboardMapper.processCustomKeyEvent(keycode);
	}

	@Override public void onKeyLock(int keycode)
	{
		keyboardMapper.processCustomKeyLock(keycode);
	}

	@Override public int getModifierState(int keycode)
	{
		return keyboardMapper.getModifierState(keycode);
	}

	@Override public void onExpandedChanged(boolean expanded)
	{
		// the expanded panel replaces the system IME; collapsing brings it back
		setSoftInputState(!expanded);
	}

	// ****************************************************************************
	// Internal delayed-event handler

	private class InputHandler extends Handler
	{
		InputHandler()
		{
			super(Looper.getMainLooper());
		}

		@Override public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MSG_SEND_MOVE_EVENT:
					delayedMoveDeviceId = null;
					if (instance == 0)
						break;
					lastMoveSentTime = SystemClock.uptimeMillis();
					LibFreeRDP.sendCursorEvent(instance, msg.arg1, msg.arg2, Mouse.getMoveEvent());
					break;

				case MSG_SCROLLING_REQUESTED:
				{
					int scrollX = 0;
					int scrollY = 0;
					float[] pointerPos = touchPointerView.getPointerPosition();
					final int ow = touchPointerView.getWidth();
					final int oh = touchPointerView.getHeight();
					final int pw = touchPointerView.getPointerWidth();
					final int ph = touchPointerView.getPointerHeight();

					if (pointerPos[0] >= ow - pw - SCROLLING_EDGE_MARGIN)
						scrollX = SCROLLING_DISTANCE;
					else if (pointerPos[0] <= SCROLLING_EDGE_MARGIN)
						scrollX = -SCROLLING_DISTANCE;

					if (pointerPos[1] >= oh - ph - SCROLLING_EDGE_MARGIN)
						scrollY = SCROLLING_DISTANCE;
					else if (pointerPos[1] <= SCROLLING_EDGE_MARGIN)
						scrollY = -SCROLLING_DISTANCE;

					scrollView.scrollBy(scrollX, scrollY);

					final int maxX = sessionView.getWidth() - scrollView.getWidth();
					final int maxY = sessionView.getHeight() - scrollView.getHeight();
					if ((scrollX < 0 && scrollView.getScrollX() <= 0) ||
					    (scrollX > 0 && scrollView.getScrollX() >= maxX))
						scrollX = 0;
					if ((scrollY < 0 && scrollView.getScrollY() <= 0) ||
					    (scrollY > 0 && scrollView.getScrollY() >= maxY))
						scrollY = 0;

					if (scrollX != 0 || scrollY != 0)
						handler.sendEmptyMessageDelayed(MSG_SCROLLING_REQUESTED, SCROLLING_TIMEOUT);
					break;
				}
				case MSG_SCREEN_INPUT_STATUS:
					refreshScreenInputStatus();
					break;
			}
		}
	}

	// ****************************************************************************
	// Pinch-to-zoom listener (wired into SessionView's ScaleGestureDetector)

	private class PinchZoomListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
	{
		private float scaleFactor = 1.0f;

		@Override public boolean onScaleBegin(ScaleGestureDetector detector)
		{
			scrollView.setScrollEnabled(false);
			return true;
		}

		@Override public boolean onScale(ScaleGestureDetector detector)
		{
			// calc scale factor
			scaleFactor *= detector.getScaleFactor();
			scaleFactor = Math.max(SessionView.MIN_SCALE_FACTOR,
			                       Math.min(scaleFactor, SessionView.MAX_SCALE_FACTOR));
			sessionView.setZoom(scaleFactor);

			if (!sessionView.isAtMinZoom() && !sessionView.isAtMaxZoom())
			{
				// transform scroll origin to the new zoom space
				float transOriginX = scrollView.getScrollX() * detector.getScaleFactor();
				float transOriginY = scrollView.getScrollY() * detector.getScaleFactor();

				// transform center point to the zoomed space
				float transCenterX =
				    (scrollView.getScrollX() + detector.getFocusX()) * detector.getScaleFactor();
				float transCenterY =
				    (scrollView.getScrollY() + detector.getFocusY()) * detector.getScaleFactor();

				// scroll by the difference between the distance of the
				// transformed center/origin point and their old distance
				// (focusX/Y)
				scrollView.scrollBy((int)((transCenterX - transOriginX) - detector.getFocusX()),
				                    (int)((transCenterY - transOriginY) - detector.getFocusY()));
			}

			return true;
		}

		@Override public void onScaleEnd(ScaleGestureDetector de)
		{
			scrollView.setScrollEnabled(true);
		}
	}
}
