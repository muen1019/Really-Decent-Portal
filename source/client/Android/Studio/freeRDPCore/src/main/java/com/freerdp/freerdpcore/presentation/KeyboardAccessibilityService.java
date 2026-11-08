/*
   Physical Keyboard Accessibility Service
   Modified 2026-11-05: leave ordinary physical streams to Android native key repeat;
   repeat captured ordinary keys with a guarded main-loop single producer.

   Copyright 2026 Ibrahim Sevinc <ibrahim.sevinc.mail@gmail.com>

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.presentation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import com.freerdp.freerdpcore.utils.KeyboardMapper;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class KeyboardAccessibilityService extends AccessibilityService
{
	// Weak references permit a consumed stream's late up to be swallowed after pause without
	// keeping a destroyed Activity alive. The mapper remains the only stream/deduplication ledger.
	private final Set<SessionActivity> routedSessions =
	    Collections.newSetFromMap(new WeakHashMap<SessionActivity, Boolean>());
	private final Set<SessionActivity> mouseRoutedSessions =
	    Collections.newSetFromMap(new WeakHashMap<SessionActivity, Boolean>());

	// Only streams accepted here belong to this filter. View/pre-IME ownership is
	// deliberately not enough: swallowing that stream's UP would strand native repeat.
	private static final int MAX_CAPTURED_KEYS = 256;
	private final Map<KeyStream, CapturedKey> capturedKeys = new HashMap<>();
	private final Handler repeatHandler = new Handler(Looper.getMainLooper());
	private CapturedKey repeatOwner;
	private static final int WAITING = 0, CLIENT = 1, PHYSICAL = 2, STOPPED = 3;

	@Override public boolean onKeyEvent(KeyEvent event)
	{
		SessionActivity session = SessionActivity.activeSession;
		KeyEvent routedEvent = remapPhysicalEscape(event);
		// Keep the raw identity and the originally routed key separately: physical
		// ESC may start as BACK, and its UP can advertise changed source metadata.
		KeyStream stream = new KeyStream(event);
		CapturedKey captured = capturedKeys.get(stream);
		// Even an ordinary key that returns to native dispatch retires the old client
		// timer. Modifiers do not steal repeat ownership, and an old UP cannot cancel
		// the newer stream. Other devices share this same latest-key policy.
		if (captured == null && event.getAction() == KeyEvent.ACTION_DOWN &&
		    event.getRepeatCount() == 0 && physicalKeyboard(event) && !isModifier(event.getKeyCode()))
			retireClientTimer();
		if (captured != null)
		{
			if (event.getAction() == KeyEvent.ACTION_UP)
			{
				stopRepeat(captured);
				capturedKeys.remove(stream);
			}
			routedEvent = withKeyCode(event, captured.keyCode);
			SessionActivity previous = captured.session.get();
			if (previous == null)
			{
				stopRepeat(captured);
				return true;
			}
			if (previous != session)
			{
				stopRepeat(captured);
				previous.consumeOwnedKeyUp(routedEvent);
				return true; // Never send an old stream into a new/background transport.
			}
			if (event.getAction() == KeyEvent.ACTION_DOWN && captured.repeatable)
			{
				if (event.getRepeatCount() == 0 && event.getEventTime() == captured.initialEventTime)
					return true; // The identical initial callback cannot steal producer ownership.
				if (captured.producer == CLIENT || captured.producer == STOPPED)
					return true; // A stream has only one repeat producer until its own UP.
				captured.producer = PHYSICAL;
				if (repeatOwner == captured)
					retireClientTimer();
			}
			previous.handleKeyEvent(routedEvent, KeyboardMapper.KeyEventOrigin.ACCESSIBILITY);
			return true; // The original DOWN was consumed; never leak its later edges locally.
		}
		// Mouse side buttons retain their existing mouse-button routing, including the
		// narrow Android form whose UP has its own downTime. They do not enter the
		// keyboard ledger: their existing manager owns that compatibility protocol.
		if (event.getAction() == KeyEvent.ACTION_UP &&
		    (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_FORWARD))
		{
			for (SessionActivity previous : mouseRoutedSessions)
			{
				if (previous != session && previous.consumeOwnedKeyUp(event))
					return true;
			}
		}
		if (session != null && session.handleMouseNavigationKeyEvent(event))
		{
			if (session.hasOwnedKey(event))
			{
				mouseRoutedSessions.add(session);
				routedSessions.add(session);
			}
			return true;
		}
		// Decide the route only on the initial DOWN. Ordinary DOWN/repeat/UP must all
		// reach InputDispatcher, even if modifiers or source metadata change mid-hold.
		if (session == null || event.getAction() != KeyEvent.ACTION_DOWN ||
		    event.getRepeatCount() != 0 || !needsSystemCapture(routedEvent))
			return false;
		capturedKeys.entrySet().removeIf(entry -> entry.getValue().session.get() == null);
		// A malformed stream that never releases must not grow this filter without
		// bound. Decide to pass through before forwarding anything to RDP.
		if (capturedKeys.size() >= MAX_CAPTURED_KEYS)
			return false;

		boolean consumed = session.handleKeyEvent(routedEvent, KeyboardMapper.KeyEventOrigin.ACCESSIBILITY);
		if (consumed)
			rememberCapturedKey(session, event, routedEvent);
		return consumed;
	}

	private void rememberCapturedKey(SessionActivity session, KeyEvent raw, KeyEvent routed)
	{
		if (session.hasOwnedKey(routed))
		{
			CapturedKey captured = new CapturedKey(session, raw, routed.getKeyCode());
			capturedKeys.put(captured.stream, captured);
			routedSessions.add(session);
			if (captured.repeatable)
			{
				int timeout = ViewConfiguration.getKeyRepeatTimeout();
				int delay = ViewConfiguration.getKeyRepeatDelay();
				if (timeout > 0 && delay > 0)
				{
					retireClientTimer();
					captured.delay = delay;
					captured.task = () -> repeatCaptured(captured);
					repeatOwner = captured;
					repeatHandler.postDelayed(captured.task, timeout);
				}
			}
		}
	}

	private void repeatCaptured(CapturedKey captured)
	{
		if (repeatOwner != captured || capturedKeys.get(captured.stream) != captured ||
		    (captured.producer != WAITING && captured.producer != CLIENT))
			return;
		SessionActivity session = captured.session.get();
		// This atomic main-loop call uses the actual held map, not hasOwnedKey's
		// released tombstones. Focus/device/disconnect cleanup already removes held.
		if (session == null || session != SessionActivity.activeSession ||
		    !session.repeatCapturedKey(captured.stream.device, captured.keyCode, captured.stream.downTime))
		{
			stopRepeat(captured);
			return;
		}
		captured.producer = CLIENT;
		// No repeat counter is incremented, so long holds cannot overflow into an
		// initial DOWN. Public positive timing also prevents a zero-delay busy loop.
		repeatHandler.postDelayed(captured.task, captured.delay);
	}

	private void retireClientTimer()
	{
		if (repeatOwner != null)
		{
			repeatHandler.removeCallbacks(repeatOwner.task);
			repeatOwner = null;
		}
	}

	private void stopRepeat(CapturedKey captured)
	{
		if (repeatOwner == captured)
			retireClientTimer();
		captured.producer = STOPPED;
	}

	private static boolean physicalKeyboard(KeyEvent event)
	{
		InputDevice device = event.getDevice();
		return device != null && !device.isVirtual() &&
		    device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_NONE &&
		    (device.getSources() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
		    event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
		    (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0;
	}

	private static boolean isModifier(int code)
	{
		return code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT ||
		    code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT ||
		    code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT ||
		    code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT;
	}

	private static KeyEvent withKeyCode(KeyEvent event, int keyCode)
	{
		if (event.getKeyCode() == keyCode)
			return event;
		return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
		                    keyCode, event.getRepeatCount(), event.getMetaState(), event.getDeviceId(),
		                    event.getScanCode(), event.getFlags(), event.getSource());
	}

	private static final class CapturedKey
	{
		final WeakReference<SessionActivity> session;
		final int keyCode;
		final KeyStream stream;
		final boolean repeatable;
		final long initialEventTime;
		int producer = WAITING;
		long delay;
		Runnable task;
		CapturedKey(SessionActivity session, KeyEvent raw, int keyCode)
		{
			this.session = new WeakReference<>(session);
			this.keyCode = keyCode;
			this.stream = new KeyStream(raw);
			this.repeatable = !isModifier(keyCode);
			this.initialEventTime = raw.getEventTime();
		}
	}

	private static boolean needsSystemCapture(KeyEvent event)
	{
		InputDevice device = event.getDevice();
		if (device == null || device.isVirtual() ||
		    device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_NONE ||
		    (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
		    !event.isFromSource(InputDevice.SOURCE_KEYBOARD) ||
		    (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0)
			return false;
		switch (event.getKeyCode())
		{
			case KeyEvent.KEYCODE_VOLUME_UP:
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_POWER:
				return false;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
			case KeyEvent.KEYCODE_META_LEFT:
			case KeyEvent.KEYCODE_META_RIGHT:
			case KeyEvent.KEYCODE_ESCAPE:
				return true;
			default:
				return event.isAltPressed() || event.isCtrlPressed() || event.isMetaPressed();
		}
	}

	private static final class KeyStream
	{
		final int device, key;
		final long downTime;
		KeyStream(KeyEvent event)
		{
			device = event.getDeviceId();
			key = event.getKeyCode();
			downTime = event.getDownTime();
		}
		@Override public boolean equals(Object other)
		{
			if (!(other instanceof KeyStream)) return false;
			KeyStream stream = (KeyStream)other;
			return device == stream.device && key == stream.key && downTime == stream.downTime;
		}
		@Override public int hashCode()
		{
			return 31 * (31 * device + key) + Long.hashCode(downTime);
		}
	}

	static KeyEvent remapPhysicalEscape(KeyEvent event)
	{
		if (event.isFromSource(InputDevice.SOURCE_MOUSE) ||
		    event.isFromSource(InputDevice.SOURCE_TOUCHPAD))
			return event;
		/* Some tablets map the physical ESC key to KEYCODE_BACK (scancode 1). */
		if (event.getScanCode() == 1 && event.getKeyCode() != KeyEvent.KEYCODE_ESCAPE)
			return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
			                    KeyEvent.KEYCODE_ESCAPE, event.getRepeatCount(), event.getMetaState(),
			                    event.getDeviceId(), event.getScanCode(), event.getFlags(),
			                    event.getSource());
		return event;
	}

	@Override public void onServiceConnected()
	{
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.packageNames = new String[] { getApplicationContext().getPackageName() };
		info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
		info.notificationTimeout = 100;
		info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
		setServiceInfo(info);
	}

	@Override public void onAccessibilityEvent(AccessibilityEvent event)
	{
	}

	@Override public void onInterrupt()
	{
		releaseRoutedKeys();
	}

	@Override public boolean onUnbind(Intent intent)
	{
		releaseRoutedKeys();
		return super.onUnbind(intent);
	}

	@Override public void onDestroy()
	{
		releaseRoutedKeys();
		routedSessions.clear();
		mouseRoutedSessions.clear();
		capturedKeys.clear();
		super.onDestroy();
	}

	private void releaseRoutedKeys()
	{
		repeatHandler.removeCallbacksAndMessages(null);
		repeatOwner = null;
		for (CapturedKey captured : capturedKeys.values())
			captured.producer = STOPPED;
		for (SessionActivity session : routedSessions)
			session.releaseOwnedKeys();
	}
}
