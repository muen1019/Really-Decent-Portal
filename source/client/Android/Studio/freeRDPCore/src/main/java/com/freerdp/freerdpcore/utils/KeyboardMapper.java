/*
   Modified 2026-11-05: support guarded client-owned captured-key repeat.
   Android Keyboard Mapping

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   Modified 2026-10-29: preserve physical key lifetimes before local IME processing.

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

// Modified 2026-10-31: add opt-in physical-Alt touchpad browser navigation.
package com.freerdp.freerdpcore.utils;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.freerdp.freerdpcore.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeyboardMapper
{
	public static final int KEYBOARD_TYPE_FUNCTIONKEYS = 1;
	public static final int KEYBOARD_TYPE_NUMPAD = 2;

	// defines key states for modifier keys - locked means on and no auto-release if an other key is
	// pressed
	public static final int KEYSTATE_ON = 1;
	public static final int KEYSTATE_LOCKED = 2;
	public static final int KEYSTATE_OFF = 3;
	final static int VK_LBUTTON = 0x01;
	final static int VK_RBUTTON = 0x02;
	final static int VK_CANCEL = 0x03;
	final static int VK_MBUTTON = 0x04;
	final static int VK_XBUTTON1 = 0x05;
	final static int VK_XBUTTON2 = 0x06;
	final static int VK_BACK = 0x08;
	final static int VK_TAB = 0x09;
	final static int VK_CLEAR = 0x0C;
	final static int VK_RETURN = 0x0D;
	final static int VK_SHIFT = 0x10;
	final static int VK_CONTROL = 0x11;
	final static int VK_MENU = 0x12;
	final static int VK_PAUSE = 0x13;
	final static int VK_CAPITAL = 0x14;
	final static int VK_KANA = 0x15;
	final static int VK_HANGUEL = 0x15;
	final static int VK_HANGUL = 0x15;
	final static int VK_JUNJA = 0x17;
	final static int VK_FINAL = 0x18;
	final static int VK_HANJA = 0x19;
	final static int VK_KANJI = 0x19;
	final static int VK_ESCAPE = 0x1B;
	final static int VK_CONVERT = 0x1C;
	final static int VK_NONCONVERT = 0x1D;
	final static int VK_ACCEPT = 0x1E;
	final static int VK_MODECHANGE = 0x1F;
	final static int VK_SPACE = 0x20;
	final static int VK_PRIOR = 0x21;
	final static int VK_NEXT = 0x22;
	final static int VK_END = 0x23;
	final static int VK_HOME = 0x24;
	final static int VK_LEFT = 0x25;
	final static int VK_UP = 0x26;
	final static int VK_RIGHT = 0x27;
	final static int VK_DOWN = 0x28;
	final static int VK_SELECT = 0x29;
	final static int VK_PRINT = 0x2A;
	final static int VK_EXECUTE = 0x2B;
	final static int VK_SNAPSHOT = 0x2C;
	final static int VK_INSERT = 0x2D;
	final static int VK_DELETE = 0x2E;
	final static int VK_HELP = 0x2F;
	final static int VK_KEY_0 = 0x30;
	final static int VK_KEY_1 = 0x31;
	final static int VK_KEY_2 = 0x32;
	final static int VK_KEY_3 = 0x33;
	final static int VK_KEY_4 = 0x34;
	final static int VK_KEY_5 = 0x35;
	final static int VK_KEY_6 = 0x36;
	final static int VK_KEY_7 = 0x37;
	final static int VK_KEY_8 = 0x38;
	final static int VK_KEY_9 = 0x39;
	final static int VK_KEY_A = 0x41;
	final static int VK_KEY_B = 0x42;
	final static int VK_KEY_C = 0x43;
	final static int VK_KEY_D = 0x44;
	final static int VK_KEY_E = 0x45;
	final static int VK_KEY_F = 0x46;
	final static int VK_KEY_G = 0x47;
	final static int VK_KEY_H = 0x48;
	final static int VK_KEY_I = 0x49;
	final static int VK_KEY_J = 0x4A;
	final static int VK_KEY_K = 0x4B;
	final static int VK_KEY_L = 0x4C;
	final static int VK_KEY_M = 0x4D;
	final static int VK_KEY_N = 0x4E;
	final static int VK_KEY_O = 0x4F;
	final static int VK_KEY_P = 0x50;
	final static int VK_KEY_Q = 0x51;
	final static int VK_KEY_R = 0x52;
	final static int VK_KEY_S = 0x53;
	final static int VK_KEY_T = 0x54;
	final static int VK_KEY_U = 0x55;
	final static int VK_KEY_V = 0x56;
	final static int VK_KEY_W = 0x57;
	final static int VK_KEY_X = 0x58;
	final static int VK_KEY_Y = 0x59;
	final static int VK_KEY_Z = 0x5A;
	final static int VK_LWIN = 0x5B;
	final static int VK_RWIN = 0x5C;
	final static int VK_APPS = 0x5D;
	final static int VK_SLEEP = 0x5F;
	final static int VK_NUMPAD0 = 0x60;
	final static int VK_NUMPAD1 = 0x61;
	final static int VK_NUMPAD2 = 0x62;
	final static int VK_NUMPAD3 = 0x63;
	final static int VK_NUMPAD4 = 0x64;
	final static int VK_NUMPAD5 = 0x65;
	final static int VK_NUMPAD6 = 0x66;
	final static int VK_NUMPAD7 = 0x67;
	final static int VK_NUMPAD8 = 0x68;
	final static int VK_NUMPAD9 = 0x69;
	final static int VK_MULTIPLY = 0x6A;
	final static int VK_ADD = 0x6B;
	final static int VK_SEPARATOR = 0x6C;
	final static int VK_SUBTRACT = 0x6D;
	final static int VK_DECIMAL = 0x6E;
	final static int VK_DIVIDE = 0x6F;
	final static int VK_F1 = 0x70;
	final static int VK_F2 = 0x71;
	final static int VK_F3 = 0x72;
	final static int VK_F4 = 0x73;
	final static int VK_F5 = 0x74;
	final static int VK_F6 = 0x75;
	final static int VK_F7 = 0x76;
	final static int VK_F8 = 0x77;
	final static int VK_F9 = 0x78;
	final static int VK_F10 = 0x79;
	final static int VK_F11 = 0x7A;
	final static int VK_F12 = 0x7B;
	final static int VK_F13 = 0x7C;
	final static int VK_F14 = 0x7D;
	final static int VK_F15 = 0x7E;
	final static int VK_F16 = 0x7F;
	final static int VK_F17 = 0x80;
	final static int VK_F18 = 0x81;
	final static int VK_F19 = 0x82;
	final static int VK_F20 = 0x83;
	final static int VK_F21 = 0x84;
	final static int VK_F22 = 0x85;
	final static int VK_F23 = 0x86;
	final static int VK_F24 = 0x87;
	final static int VK_NUMLOCK = 0x90;
	final static int VK_SCROLL = 0x91;
	final static int VK_LSHIFT = 0xA0;
	final static int VK_RSHIFT = 0xA1;
	final static int VK_LCONTROL = 0xA2;
	final static int VK_RCONTROL = 0xA3;
	final static int VK_LMENU = 0xA4;
	final static int VK_RMENU = 0xA5;
	final static int VK_BROWSER_BACK = 0xA6;
	final static int VK_BROWSER_FORWARD = 0xA7;
	final static int VK_BROWSER_REFRESH = 0xA8;
	final static int VK_BROWSER_STOP = 0xA9;
	final static int VK_BROWSER_SEARCH = 0xAA;
	final static int VK_BROWSER_FAVORITES = 0xAB;
	final static int VK_BROWSER_HOME = 0xAC;
	final static int VK_VOLUME_MUTE = 0xAD;
	final static int VK_VOLUME_DOWN = 0xAE;
	final static int VK_VOLUME_UP = 0xAF;
	final static int VK_MEDIA_NEXT_TRACK = 0xB0;
	final static int VK_MEDIA_PREV_TRACK = 0xB1;
	final static int VK_MEDIA_STOP = 0xB2;
	final static int VK_MEDIA_PLAY_PAUSE = 0xB3;
	final static int VK_LAUNCH_MAIL = 0xB4;
	final static int VK_LAUNCH_MEDIA_SELECT = 0xB5;
	final static int VK_LAUNCH_APP1 = 0xB6;
	final static int VK_LAUNCH_APP2 = 0xB7;
	final static int VK_OEM_1 = 0xBA;
	final static int VK_OEM_PLUS = 0xBB;
	final static int VK_OEM_COMMA = 0xBC;
	final static int VK_OEM_MINUS = 0xBD;
	final static int VK_OEM_PERIOD = 0xBE;
	final static int VK_OEM_2 = 0xBF;
	final static int VK_OEM_3 = 0xC0;
	final static int VK_ABNT_C1 = 0xC1;
	final static int VK_ABNT_C2 = 0xC2;
	final static int VK_OEM_4 = 0xDB;
	final static int VK_OEM_5 = 0xDC;
	final static int VK_OEM_6 = 0xDD;
	final static int VK_OEM_7 = 0xDE;
	final static int VK_OEM_8 = 0xDF;
	final static int VK_OEM_102 = 0xE2;
	final static int VK_PROCESSKEY = 0xE5;
	final static int VK_PACKET = 0xE7;
	final static int VK_ATTN = 0xF6;
	final static int VK_CRSEL = 0xF7;
	final static int VK_EXSEL = 0xF8;
	final static int VK_EREOF = 0xF9;
	final static int VK_PLAY = 0xFA;
	final static int VK_ZOOM = 0xFB;
	final static int VK_NONAME = 0xFC;
	final static int VK_PA1 = 0xFD;
	final static int VK_OEM_CLEAR = 0xFE;
	final static int VK_UNICODE = 0x80000000;
	final static int VK_EXT_KEY = 0x00000100;
	// key codes to switch between custom keyboard
	private final static int EXTKEY_KBFUNCTIONKEYS = 0x1100;
	private final static int EXTKEY_KBNUMPAD = 0x1101;
	// this flag indicates if we got a VK or a unicode character in our translation map
	private static final int KEY_FLAG_UNICODE = 0x80000000;
	// this flag indicates if the key is a toggle key (remains down when pressed and goes up if
	// pressed again)
	private static final int KEY_FLAG_TOGGLE = 0x40000000;
	private static int[] keymapAndroid;
	private static int[] keymapExt;
	private static boolean initialized = false;
	private KeyProcessingListener listener = null;
	private boolean shiftPressed = false;
	private boolean ctrlPressed = false;
	private boolean altPressed = false;
	private boolean winPressed = false;
	private boolean isShiftLocked = false;
	private boolean isCtrlLocked = false;
	private boolean isAltLocked = false;
	private boolean isWinLocked = false;
	private boolean isWinKeyDown = false;
	private boolean isWinComboUsed = false;
	private final Object uiModifierOwner = new Object();
	private final Object contentZoomControlOwner = new Object();
	private int contentZoomControlKey;
	private final PhysicalKeyState physicalKeys = new PhysicalKeyState((vk, down) -> {
		if (listener != null)
			listener.processVirtualKey(vk, down);
	});
	private final Map<String, KeyEventOrigin> processedCallbacks = new LinkedHashMap<>();
	// Exact completed-up receipts have no remote state and may safely survive a rebind.
	private final Set<String> completedKeyUps = new LinkedHashSet<>();

	public enum KeyEventOrigin
	{
		ACTIVITY,
		ACCESSIBILITY,
		PRE_IME
	}

	public void init(Context context)
	{
		if (initialized)
			return;

		keymapAndroid = new int[256];

		keymapAndroid[KeyEvent.KEYCODE_0] = VK_KEY_0;
		keymapAndroid[KeyEvent.KEYCODE_1] = VK_KEY_1;
		keymapAndroid[KeyEvent.KEYCODE_2] = VK_KEY_2;
		keymapAndroid[KeyEvent.KEYCODE_3] = VK_KEY_3;
		keymapAndroid[KeyEvent.KEYCODE_4] = VK_KEY_4;
		keymapAndroid[KeyEvent.KEYCODE_5] = VK_KEY_5;
		keymapAndroid[KeyEvent.KEYCODE_6] = VK_KEY_6;
		keymapAndroid[KeyEvent.KEYCODE_7] = VK_KEY_7;
		keymapAndroid[KeyEvent.KEYCODE_8] = VK_KEY_8;
		keymapAndroid[KeyEvent.KEYCODE_9] = VK_KEY_9;

		keymapAndroid[KeyEvent.KEYCODE_A] = VK_KEY_A;
		keymapAndroid[KeyEvent.KEYCODE_B] = VK_KEY_B;
		keymapAndroid[KeyEvent.KEYCODE_C] = VK_KEY_C;
		keymapAndroid[KeyEvent.KEYCODE_D] = VK_KEY_D;
		keymapAndroid[KeyEvent.KEYCODE_E] = VK_KEY_E;
		keymapAndroid[KeyEvent.KEYCODE_F] = VK_KEY_F;
		keymapAndroid[KeyEvent.KEYCODE_G] = VK_KEY_G;
		keymapAndroid[KeyEvent.KEYCODE_H] = VK_KEY_H;
		keymapAndroid[KeyEvent.KEYCODE_I] = VK_KEY_I;
		keymapAndroid[KeyEvent.KEYCODE_J] = VK_KEY_J;
		keymapAndroid[KeyEvent.KEYCODE_K] = VK_KEY_K;
		keymapAndroid[KeyEvent.KEYCODE_L] = VK_KEY_L;
		keymapAndroid[KeyEvent.KEYCODE_M] = VK_KEY_M;
		keymapAndroid[KeyEvent.KEYCODE_N] = VK_KEY_N;
		keymapAndroid[KeyEvent.KEYCODE_O] = VK_KEY_O;
		keymapAndroid[KeyEvent.KEYCODE_P] = VK_KEY_P;
		keymapAndroid[KeyEvent.KEYCODE_Q] = VK_KEY_Q;
		keymapAndroid[KeyEvent.KEYCODE_R] = VK_KEY_R;
		keymapAndroid[KeyEvent.KEYCODE_S] = VK_KEY_S;
		keymapAndroid[KeyEvent.KEYCODE_T] = VK_KEY_T;
		keymapAndroid[KeyEvent.KEYCODE_U] = VK_KEY_U;
		keymapAndroid[KeyEvent.KEYCODE_V] = VK_KEY_V;
		keymapAndroid[KeyEvent.KEYCODE_W] = VK_KEY_W;
		keymapAndroid[KeyEvent.KEYCODE_X] = VK_KEY_X;
		keymapAndroid[KeyEvent.KEYCODE_Y] = VK_KEY_Y;
		keymapAndroid[KeyEvent.KEYCODE_Z] = VK_KEY_Z;

		keymapAndroid[KeyEvent.KEYCODE_DEL] = VK_BACK;
		keymapAndroid[KeyEvent.KEYCODE_ENTER] = VK_RETURN;
		keymapAndroid[KeyEvent.KEYCODE_SPACE] = VK_SPACE;
		keymapAndroid[KeyEvent.KEYCODE_TAB] = VK_TAB;
		keymapAndroid[KeyEvent.KEYCODE_ESCAPE] = VK_ESCAPE;
		keymapAndroid[KeyEvent.KEYCODE_CAPS_LOCK] = VK_CAPITAL;
		//		keymapAndroid[KeyEvent.KEYCODE_SHIFT_LEFT] = VK_LSHIFT;
		//		keymapAndroid[KeyEvent.KEYCODE_SHIFT_RIGHT] = VK_RSHIFT;

		keymapAndroid[KeyEvent.KEYCODE_DPAD_DOWN] = VK_DOWN | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_DPAD_LEFT] = VK_LEFT | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_DPAD_RIGHT] = VK_RIGHT | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_DPAD_UP] = VK_UP | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_MOVE_HOME] = VK_HOME | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_MOVE_END] = VK_END | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_PAGE_UP] = VK_PRIOR | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_PAGE_DOWN] = VK_NEXT | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_FORWARD_DEL] = VK_DELETE | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_INSERT] = VK_INSERT | VK_EXT_KEY;

		keymapAndroid[KeyEvent.KEYCODE_MINUS] = VK_OEM_MINUS;
		keymapAndroid[KeyEvent.KEYCODE_EQUALS] = VK_OEM_PLUS;
		keymapAndroid[KeyEvent.KEYCODE_LEFT_BRACKET] = VK_OEM_4;
		keymapAndroid[KeyEvent.KEYCODE_RIGHT_BRACKET] = VK_OEM_6;
		keymapAndroid[KeyEvent.KEYCODE_BACKSLASH] = VK_OEM_5;
		keymapAndroid[KeyEvent.KEYCODE_SEMICOLON] = VK_OEM_1;
		keymapAndroid[KeyEvent.KEYCODE_APOSTROPHE] = VK_OEM_7;
		keymapAndroid[KeyEvent.KEYCODE_GRAVE] = VK_OEM_3;
		keymapAndroid[KeyEvent.KEYCODE_COMMA] = VK_OEM_COMMA;
		keymapAndroid[KeyEvent.KEYCODE_PERIOD] = VK_OEM_PERIOD;
		keymapAndroid[KeyEvent.KEYCODE_SLASH] = VK_OEM_2;

		keymapAndroid[KeyEvent.KEYCODE_F1] = VK_F1;
		keymapAndroid[KeyEvent.KEYCODE_F2] = VK_F2;
		keymapAndroid[KeyEvent.KEYCODE_F3] = VK_F3;
		keymapAndroid[KeyEvent.KEYCODE_F4] = VK_F4;
		keymapAndroid[KeyEvent.KEYCODE_F5] = VK_F5;
		keymapAndroid[KeyEvent.KEYCODE_F6] = VK_F6;
		keymapAndroid[KeyEvent.KEYCODE_F7] = VK_F7;
		keymapAndroid[KeyEvent.KEYCODE_F8] = VK_F8;
		keymapAndroid[KeyEvent.KEYCODE_F9] = VK_F9;
		keymapAndroid[KeyEvent.KEYCODE_F10] = VK_F10;
		keymapAndroid[KeyEvent.KEYCODE_F11] = VK_F11;
		keymapAndroid[KeyEvent.KEYCODE_F12] = VK_F12;

		keymapAndroid[KeyEvent.KEYCODE_NUM_LOCK] = VK_NUMLOCK;
		keymapAndroid[KeyEvent.KEYCODE_SCROLL_LOCK] = VK_SCROLL;
		keymapAndroid[KeyEvent.KEYCODE_SYSRQ] = VK_SNAPSHOT | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_BREAK] = VK_PAUSE;

		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_0] = VK_NUMPAD0;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_1] = VK_NUMPAD1;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_2] = VK_NUMPAD2;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_3] = VK_NUMPAD3;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_4] = VK_NUMPAD4;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_5] = VK_NUMPAD5;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_6] = VK_NUMPAD6;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_7] = VK_NUMPAD7;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_8] = VK_NUMPAD8;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_9] = VK_NUMPAD9;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_DIVIDE] = VK_DIVIDE | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_MULTIPLY] = VK_MULTIPLY;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_SUBTRACT] = VK_SUBTRACT;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_ADD] = VK_ADD;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_DOT] = VK_DECIMAL;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_COMMA] = VK_DECIMAL;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_ENTER] = VK_RETURN | VK_EXT_KEY;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_EQUALS] = KEY_FLAG_UNICODE | 61;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN] = KEY_FLAG_UNICODE | 40;
		keymapAndroid[KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN] = KEY_FLAG_UNICODE | 41;

		// special keys mapping
		keymapExt = new int[256];
		keymapExt[context.getResources().getInteger(R.integer.keycode_F1)] = VK_F1;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F2)] = VK_F2;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F3)] = VK_F3;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F4)] = VK_F4;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F5)] = VK_F5;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F6)] = VK_F6;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F7)] = VK_F7;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F8)] = VK_F8;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F9)] = VK_F9;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F10)] = VK_F10;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F11)] = VK_F11;
		keymapExt[context.getResources().getInteger(R.integer.keycode_F12)] = VK_F12;
		keymapExt[context.getResources().getInteger(R.integer.keycode_tab)] = VK_TAB;
		keymapExt[context.getResources().getInteger(R.integer.keycode_print)] = VK_PRINT;
		keymapExt[context.getResources().getInteger(R.integer.keycode_insert)] =
		    VK_INSERT | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_delete)] =
		    VK_DELETE | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_home)] = VK_HOME | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_end)] = VK_END | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_pgup)] =
		    VK_PRIOR | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_pgdn)] = VK_NEXT | VK_EXT_KEY;

		// numpad mapping
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_0)] = VK_NUMPAD0;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_1)] = VK_NUMPAD1;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_2)] = VK_NUMPAD2;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_3)] = VK_NUMPAD3;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_4)] = VK_NUMPAD4;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_5)] = VK_NUMPAD5;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_6)] = VK_NUMPAD6;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_7)] = VK_NUMPAD7;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_8)] = VK_NUMPAD8;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_9)] = VK_NUMPAD9;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_numlock)] = VK_NUMLOCK;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_add)] = VK_ADD;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_comma)] = VK_DECIMAL;
		keymapExt[context.getResources().getInteger(R.integer.keycode_comma)] = VK_OEM_COMMA;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_divide)] =
		    VK_DIVIDE | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_enter)] =
		    VK_RETURN | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_multiply)] =
		    VK_MULTIPLY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_subtract)] =
		    VK_SUBTRACT;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_equals)] =
		    (KEY_FLAG_UNICODE | 61);
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_left_paren)] =
		    (KEY_FLAG_UNICODE | 40);
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_right_paren)] =
		    (KEY_FLAG_UNICODE | 41);

		// cursor key codes
		keymapExt[context.getResources().getInteger(R.integer.keycode_up)] = VK_UP | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_down)] = VK_DOWN | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_left)] = VK_LEFT | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_right)] =
		    VK_RIGHT | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_enter)] =
		    VK_RETURN | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_backspace)] = VK_BACK;

		// shared keys
		keymapExt[context.getResources().getInteger(R.integer.keycode_win)] = VK_LWIN | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_menu)] = VK_APPS | VK_EXT_KEY;
		keymapExt[context.getResources().getInteger(R.integer.keycode_esc)] = VK_ESCAPE;

		/*		keymapExt[context.getResources().getInteger(R.integer.keycode_modifier_ctrl)] =
		   VK_LCONTROL; keymapExt[context.getResources().getInteger(R.integer.keycode_modifier_alt)]
		   = VK_LMENU;
		        keymapExt[context.getResources().getInteger(R.integer.keycode_modifier_shift)] =
		   VK_LSHIFT;
		*/
		// get custom keyboard key codes
		keymapExt[context.getResources().getInteger(R.integer.keycode_specialkeys_keyboard)] =
		    EXTKEY_KBFUNCTIONKEYS;
		keymapExt[context.getResources().getInteger(R.integer.keycode_numpad_keyboard)] =
		    EXTKEY_KBNUMPAD;

		keymapExt[context.getResources().getInteger(R.integer.keycode_toggle_shift)] =
		    (KEY_FLAG_TOGGLE | VK_LSHIFT);
		keymapExt[context.getResources().getInteger(R.integer.keycode_toggle_ctrl)] =
		    (KEY_FLAG_TOGGLE | VK_LCONTROL);
		keymapExt[context.getResources().getInteger(R.integer.keycode_toggle_alt)] =
		    (KEY_FLAG_TOGGLE | VK_LMENU);
		keymapExt[context.getResources().getInteger(R.integer.keycode_toggle_win)] =
		    (KEY_FLAG_TOGGLE | VK_LWIN);

		initialized = true;
	}

	public void reset(KeyProcessingListener listener)
	{
		// Do not send an old session's releases through a newly attached listener.
		releaseOwnedKeys(false);
		shiftPressed = false;
		ctrlPressed = false;
		altPressed = false;
		winPressed = false;
		isWinKeyDown = false;
		isWinComboUsed = false;
		setKeyProcessingListener(listener);
	}

	public void setKeyProcessingListener(KeyProcessingListener listener)
	{
		this.listener = listener;
	}

	/** Browser shortcuts require a real held Alt, never inferred or sticky-only metadata. */
	public boolean isPhysicalAltNavigationActive()
	{
		return listener != null && physicalFamilyPressed(VK_LMENU, VK_RMENU | VK_EXT_KEY) &&
		    !familyPressed(VK_LCONTROL, VK_RCONTROL | VK_EXT_KEY) &&
		    !familyPressed(VK_LSHIFT, VK_RSHIFT) && !familyPressed(VK_LWIN | VK_EXT_KEY, VK_RWIN | VK_EXT_KEY) &&
		    !physicalKeys.isPressed(VK_LEFT | VK_EXT_KEY) && !physicalKeys.isPressed(VK_RIGHT | VK_EXT_KEY);
	}

	/** Pulse only the arrow; physical Alt retains its original owner until its own up. */
	public boolean sendBrowserNavigationWithPhysicalAlt(boolean forward)
	{
		if (!isPhysicalAltNavigationActive()) return false;
		int arrow = (forward ? VK_RIGHT : VK_LEFT) | VK_EXT_KEY;
		Object owner = new Object();
		try { physicalKeys.acquire(owner, arrow); }
		finally { physicalKeys.release(owner, arrow, true); }
		return true;
	}

	/** Share an already-held left or right Ctrl with the zoom stream, or hold left Ctrl. */
	public void beginContentZoomControl()
	{
		if (listener == null || contentZoomControlKey != 0)
			return;
		int right = VK_RCONTROL | VK_EXT_KEY;
		contentZoomControlKey = !physicalKeys.isPressed(VK_LCONTROL) && physicalKeys.isPressed(right)
		    ? right : VK_LCONTROL;
		physicalKeys.acquire(contentZoomControlOwner, contentZoomControlKey);
	}

	public void endContentZoomControl(boolean sendKeyUp)
	{
		if (contentZoomControlKey == 0)
			return;
		int key = contentZoomControlKey;
		contentZoomControlKey = 0;
		physicalKeys.release(contentZoomControlOwner, key, sendKeyUp);
	}

	public boolean processAndroidKeyEvent(KeyEvent event)
	{
		return processAndroidKeyEvent(event, KeyEventOrigin.ACTIVITY);
	}

	/** Only physical mapped streams bypass the local IME; software text keeps its old path. */
	public boolean processPhysicalKeyEventBeforeIme(KeyEvent event)
	{
		if (event.getAction() == KeyEvent.ACTION_UP && hasOwnedKey(event))
			return processAndroidKeyEvent(event, KeyEventOrigin.PRE_IME);
		int vk = hardwareVirtualKey(event.getKeyCode());
		if (event.getAction() != KeyEvent.ACTION_DOWN || vk <= 0 ||
		    !(hasOwnedKey(event) || (isPhysicalKeyboardEvent(event) &&
		                             (vk & KEY_FLAG_UNICODE) == 0 && !event.isSymPressed())))
			return false;
		return processAndroidKeyEvent(event, KeyEventOrigin.PRE_IME);
	}

	/** Both Android dispatch entry points share this session-local stream state. */
	public boolean processAndroidKeyEvent(KeyEvent event, KeyEventOrigin origin)
	{
		if (event.getAction() == KeyEvent.ACTION_UP && completedKeyUps.contains(callbackIdentity(event)))
			return true;
		KeyEventOrigin previousOrigin = processedCallbacks.get(callbackIdentity(event));
		if (previousOrigin != null && previousOrigin != origin)
			return true;
		if (event.getAction() == KeyEvent.ACTION_UP && hasOwnedKey(event))
		{
			if (duplicateCallback(event, origin))
				return true;
			boolean consumed = physicalKeys.up(event.getDeviceId(), event.getKeyCode(), event.getDownTime(), true);
			if (consumed)
				rememberCompletedKeyUp(event);
			return consumed;
		}
		if (event.getAction() == KeyEvent.ACTION_UP && isPhysicalKeyboardEvent(event) &&
		    isHardwareModifier(event.getKeyCode()))
			return false; // An orphan hardware up must never trigger the virtual Win tap fallback.

		int hardwareVk = hardwareVirtualKey(event.getKeyCode());
		if (event.getAction() == KeyEvent.ACTION_DOWN && hardwareVk > 0 &&
		    (hasOwnedKey(event) || (isPhysicalKeyboardEvent(event) &&
		                          (hardwareVk & KEY_FLAG_UNICODE) == 0 && !event.isSymPressed())))
		{
			if (duplicateCallback(event, origin))
				return true;
			boolean modifier = isHardwareModifier(event.getKeyCode());
			physicalKeys.down(event.getDeviceId(), event.getKeyCode(), event.getDownTime(),
			                  event.getRepeatCount(), hardwareVk, modifier,
			                  modifier ? new int[0] : inferredModifiers(event));
			if (!modifier && isModifierPressed())
				resetModifierKeysAfterInput(false);
			return true;
		}

		switch (event.getAction())
		{
			// we only process down events
			case KeyEvent.ACTION_UP:
			{
				if (event.getKeyCode() == KeyEvent.KEYCODE_META_LEFT ||
				    event.getKeyCode() == KeyEvent.KEYCODE_META_RIGHT)
				{
					if (!isWinKeyDown)
						return false;
					isWinKeyDown = false;
					if (!isWinComboUsed)
					{
						physicalKeys.pulse(VK_LWIN | VK_EXT_KEY);
					}
					isWinComboUsed = false;
					return true;
				}
				return false;
			}

			case KeyEvent.ACTION_DOWN:
			{
				/* Physical Win key: buffer until release to distinguish tap vs combo. */
				if (event.getKeyCode() == KeyEvent.KEYCODE_META_LEFT ||
				    event.getKeyCode() == KeyEvent.KEYCODE_META_RIGHT)
				{
					isWinKeyDown = true;
					isWinComboUsed = false;
					return true;
				}

				if (isWinKeyDown)
					isWinComboUsed = true;

				boolean modifierActive = isModifierPressed();
				// if a modifier is pressed we will send a VK event (if possible) so that key
				// combinations will be recognized correctly. Otherwise we will send the unicode
				// key. At the end we will reset all modifiers and notify our listener.
				int vkcode = getVirtualKeyCode(event.getKeyCode());
				if ((vkcode & KEY_FLAG_UNICODE) != 0)
					listener.processUnicodeKey(vkcode & (~KEY_FLAG_UNICODE));
				// if we got a valid vkcode send it - except for letters/numbers if a modifier is
				// active
				else if (vkcode > 0 && !event.isSymPressed())
				{
					boolean sendCtrl = !familyPressed(VK_LCONTROL, VK_RCONTROL | VK_EXT_KEY) && event.isCtrlPressed();
					boolean sendAlt = !familyPressed(VK_LMENU, VK_RMENU | VK_EXT_KEY) && event.isAltPressed();
					boolean sendWin = !familyPressed(VK_LWIN | VK_EXT_KEY, VK_RWIN | VK_EXT_KEY) && isWinKeyDown;
					boolean sendShift = !familyPressed(VK_LSHIFT, VK_RSHIFT) && event.isShiftPressed();
					Object transientOwner = new Object();

					if (sendCtrl)
						physicalKeys.acquire(transientOwner, VK_LCONTROL);
					if (sendAlt)
						physicalKeys.acquire(transientOwner, VK_LMENU);
					if (sendWin)
						physicalKeys.acquire(transientOwner, VK_LWIN | VK_EXT_KEY);
					if (sendShift)
						physicalKeys.acquire(transientOwner, VK_LSHIFT);

					physicalKeys.pulse(vkcode);

					if (sendShift)
						physicalKeys.release(transientOwner, VK_LSHIFT, true);
					if (sendWin)
						physicalKeys.release(transientOwner, VK_LWIN | VK_EXT_KEY, true);
					if (sendAlt)
						physicalKeys.release(transientOwner, VK_LMENU, true);
					if (sendCtrl)
						physicalKeys.release(transientOwner, VK_LCONTROL, true);
				}
				else if (event.getUnicodeChar() != 0)
					listener.processUnicodeKey(event.getUnicodeChar());
				else
					return false;

				// reset any pending toggle states if a modifier was pressed
				if (modifierActive)
					resetModifierKeysAfterInput(false);
				return true;
			}

			case KeyEvent.ACTION_MULTIPLE:
			{
				String str = event.getCharacters();
				if (str != null)
				{
					for (int i = 0; i < str.length(); i++)
						listener.processUnicodeKey(str.charAt(i));
				}
				return true;
			}

			default:
				break;
		}
		return false;
	}

	private static String callbackIdentity(KeyEvent event)
	{
		return event.getDeviceId() + ":" + event.getKeyCode() + ":" + event.getDownTime() + ":" +
		    event.getEventTime() + ":" + event.getAction() + ":" + event.getRepeatCount() + ":" +
		    event.getSource() + ":" + event.getScanCode() + ":" + event.getMetaState() + ":" +
		    event.getFlags();
	}

	private boolean duplicateCallback(KeyEvent event, KeyEventOrigin origin)
	{
		String identity = callbackIdentity(event);
		KeyEventOrigin previous = processedCallbacks.get(identity);
		if (previous != null && previous != origin)
			return true;
		processedCallbacks.put(identity, origin);
		while (processedCallbacks.size() > 128)
			processedCallbacks.remove(processedCallbacks.keySet().iterator().next());
		return false;
	}

	private static boolean isPhysicalKeyboardEvent(KeyEvent event)
	{
		InputDevice device = event.getDevice();
		return device != null && !device.isVirtual() &&
		    device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_NONE &&
		    event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
		    (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0;
	}

	private static boolean isHardwareModifier(int keycode)
	{
		switch (keycode)
		{
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
			case KeyEvent.KEYCODE_META_LEFT:
			case KeyEvent.KEYCODE_META_RIGHT:
				return true;
			default:
				return false;
		}
	}

	private int hardwareVirtualKey(int keycode)
	{
		switch (keycode)
		{
			case KeyEvent.KEYCODE_SHIFT_LEFT: return VK_LSHIFT;
			case KeyEvent.KEYCODE_SHIFT_RIGHT: return VK_RSHIFT;
			case KeyEvent.KEYCODE_CTRL_LEFT: return VK_LCONTROL;
			case KeyEvent.KEYCODE_CTRL_RIGHT: return VK_RCONTROL | VK_EXT_KEY;
			case KeyEvent.KEYCODE_ALT_LEFT: return VK_LMENU;
			case KeyEvent.KEYCODE_ALT_RIGHT: return VK_RMENU | VK_EXT_KEY;
			case KeyEvent.KEYCODE_META_LEFT: return VK_LWIN | VK_EXT_KEY;
			case KeyEvent.KEYCODE_META_RIGHT: return VK_RWIN | VK_EXT_KEY;
			default: return getVirtualKeyCode(keycode);
		}
	}

	private boolean familyPressed(int left, int right)
	{
		return physicalKeys.isPressed(left) || physicalKeys.isPressed(right);
	}

	private boolean physicalFamilyPressed(int left, int right)
	{
		return physicalKeys.isPhysicalModifierPressed(left) || physicalKeys.isPhysicalModifierPressed(right);
	}

	private int[] inferredModifiers(KeyEvent event)
	{
		List<Integer> inferred = new ArrayList<>();
		int meta = event.getMetaState();
		if (event.isCtrlPressed() && !ctrlPressed && !physicalFamilyPressed(VK_LCONTROL, VK_RCONTROL | VK_EXT_KEY))
			inferred.add((meta & KeyEvent.META_CTRL_RIGHT_ON) != 0 ? VK_RCONTROL | VK_EXT_KEY : VK_LCONTROL);
		if (event.isAltPressed() && !altPressed && !physicalFamilyPressed(VK_LMENU, VK_RMENU | VK_EXT_KEY))
			inferred.add((meta & KeyEvent.META_ALT_RIGHT_ON) != 0 ? VK_RMENU | VK_EXT_KEY : VK_LMENU);
		if (event.isMetaPressed() && !winPressed && !physicalFamilyPressed(VK_LWIN | VK_EXT_KEY, VK_RWIN | VK_EXT_KEY))
			inferred.add((meta & KeyEvent.META_META_RIGHT_ON) != 0 ? VK_RWIN | VK_EXT_KEY : VK_LWIN | VK_EXT_KEY);
		if (event.isShiftPressed() && !shiftPressed && !physicalFamilyPressed(VK_LSHIFT, VK_RSHIFT))
			inferred.add((meta & KeyEvent.META_SHIFT_RIGHT_ON) != 0 ? VK_RSHIFT : VK_LSHIFT);
		// Transfer an unlocked UI sticky modifier to the accepted physical key until its up.
		if (ctrlPressed && !isCtrlLocked && !inferred.contains(VK_LCONTROL))
			inferred.add(VK_LCONTROL);
		if (altPressed && !isAltLocked && !inferred.contains(VK_LMENU))
			inferred.add(VK_LMENU);
		if (winPressed && !isWinLocked && !inferred.contains(VK_LWIN | VK_EXT_KEY))
			inferred.add(VK_LWIN | VK_EXT_KEY);
		if (shiftPressed && !isShiftLocked && !inferred.contains(VK_LSHIFT))
			inferred.add(VK_LSHIFT);
		int[] result = new int[inferred.size()];
		for (int i = 0; i < inferred.size(); i++)
			result[i] = inferred.get(i);
		return result;
	}

	/** Main-loop client repeat: send only from the actual live physical held map. */
	public boolean repeatHeldPhysicalKey(int device, int key, long downTime)
	{
		return physicalKeys.repeatHeld(device, key, downTime);
	}

	public boolean hasOwnedKey(KeyEvent event)
	{
		return physicalKeys.owns(event.getDeviceId(), event.getKeyCode(), event.getDownTime()) ||
		    (event.getAction() == KeyEvent.ACTION_UP && completedKeyUps.contains(callbackIdentity(event)));
	}

	/** Consume only a matching stream already accepted by this session, without sending input. */
	public boolean consumeOwnedKeyUp(KeyEvent event)
	{
		if (event.getAction() != KeyEvent.ACTION_UP)
			return false;
		String identity = callbackIdentity(event);
		if (completedKeyUps.contains(identity))
			return true;
		if (!physicalKeys.consumeReleasedUp(event.getDeviceId(), event.getKeyCode(), event.getDownTime()))
			return false;
		rememberCompletedKeyUp(event);
		return true;
	}

	private void rememberCompletedKeyUp(KeyEvent event)
	{
		completedKeyUps.add(callbackIdentity(event));
		// All accepted physical key-up origins converge here, including pre-IME and accessibility.
		if (listener != null && (event.getKeyCode() == KeyEvent.KEYCODE_ALT_LEFT ||
		                         event.getKeyCode() == KeyEvent.KEYCODE_ALT_RIGHT))
			listener.physicalAltReleased();
		while (completedKeyUps.size() > 128)
			completedKeyUps.remove(completedKeyUps.iterator().next());
	}

	public void releaseDeviceKeys(int deviceId, boolean sendKeyUps)
	{
		physicalKeys.releaseDevice(deviceId, sendKeyUps);
	}

	/** Call before detaching a transport; false discards state after transport loss. */
	public void releaseOwnedKeys(boolean sendKeyUps)
	{
		physicalKeys.releaseAll(sendKeyUps);
		contentZoomControlKey = 0;
		shiftPressed = ctrlPressed = altPressed = winPressed = false;
		isShiftLocked = isCtrlLocked = isAltLocked = isWinLocked = false;
		isWinKeyDown = isWinComboUsed = false;
		processedCallbacks.clear();
		if (listener != null)
			listener.modifiersChanged();
	}

	public void processCustomKeyEvent(int keycode)
	{
		int extCode = getExtendedKeyCode(keycode);
		if (extCode == 0)
			return;

		// toggle button pressed?
		if ((extCode & KEY_FLAG_TOGGLE) != 0)
		{
			processToggleButton(extCode & (~KEY_FLAG_TOGGLE), false);
			return;
		}

		// keyboard switch button pressed?
		if (extCode == EXTKEY_KBFUNCTIONKEYS || extCode == EXTKEY_KBNUMPAD)
		{
			switchKeyboard(extCode);
			return;
		}

		// nope - see if we got a unicode or vk
		if ((extCode & KEY_FLAG_UNICODE) != 0)
			listener.processUnicodeKey(extCode & (~KEY_FLAG_UNICODE));
		else
		{
			physicalKeys.pulse(extCode);
		}

		resetModifierKeysAfterInput(false);
	}

	// Locks a sticky modifier down. Returns false if the keycode is not a modifier.
	public boolean processCustomKeyLock(int keycode)
	{
		int extCode = getExtendedKeyCode(keycode);
		if ((extCode & KEY_FLAG_TOGGLE) == 0)
			return false;

		processToggleButton(extCode & (~KEY_FLAG_TOGGLE), true);
		return true;
	}

	public void processUnicodeFallback(int unicodeKey)
	{
		KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
		KeyEvent[] events = kcm.getEvents(new char[] { (char)unicodeKey });
		if (events == null)
			return;
		Object fallbackOwner = new Object();
		for (KeyEvent event : events)
		{
			int vkcode = fallbackVkcode(event.getKeyCode());
			if (vkcode > 0)
			{
				if (isHardwareModifier(event.getKeyCode()))
				{
					if (event.getAction() == KeyEvent.ACTION_DOWN)
						physicalKeys.acquire(fallbackOwner, vkcode);
					else
						physicalKeys.release(fallbackOwner, vkcode, true);
				}
				else if (event.getAction() == KeyEvent.ACTION_DOWN)
					physicalKeys.pulse(vkcode);
			}
		}
	}

	private int fallbackVkcode(int keycode)
	{
		switch (keycode)
		{
			case KeyEvent.KEYCODE_SHIFT_LEFT:
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
				return VK_LSHIFT;
			case KeyEvent.KEYCODE_ALT_LEFT:
			case KeyEvent.KEYCODE_ALT_RIGHT:
				return VK_LMENU;
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
				return VK_LCONTROL;
			default:
				return getVirtualKeyCode(keycode);
		}
	}

	public void sendAltF4()
	{
		Object owner = new Object();
		physicalKeys.acquire(owner, VK_LMENU);
		physicalKeys.pulse(VK_F4);
		physicalKeys.release(owner, VK_LMENU, true);
	}

	private boolean isModifierPressed()
	{
		return (shiftPressed || ctrlPressed || altPressed || winPressed);
	}

	public int getModifierState(int keycode)
	{
		int modifierCode = getExtendedKeyCode(keycode);

		// check and get real modifier keycode
		if ((modifierCode & KEY_FLAG_TOGGLE) == 0)
			return -1;
		modifierCode = modifierCode & (~KEY_FLAG_TOGGLE);

		switch (modifierCode)
		{
			case VK_LSHIFT:
			{
				return (shiftPressed ? (isShiftLocked ? KEYSTATE_LOCKED : KEYSTATE_ON)
				                     : KEYSTATE_OFF);
			}
			case VK_LCONTROL:
			{
				return (ctrlPressed ? (isCtrlLocked ? KEYSTATE_LOCKED : KEYSTATE_ON)
				                    : KEYSTATE_OFF);
			}
			case VK_LMENU:
			{
				return (altPressed ? (isAltLocked ? KEYSTATE_LOCKED : KEYSTATE_ON) : KEYSTATE_OFF);
			}
			case VK_LWIN:
			{
				return (winPressed ? (isWinLocked ? KEYSTATE_LOCKED : KEYSTATE_ON) : KEYSTATE_OFF);
			}
		}

		return -1;
	}

	private int getVirtualKeyCode(int keycode)
	{
		if (keycode >= 0 && keycode <= 0xFF)
			return keymapAndroid[keycode];
		return 0;
	}

	private int getExtendedKeyCode(int keycode)
	{
		if (keycode >= 0 && keycode <= 0xFF)
			return keymapExt[keycode];
		return 0;
	}

	// A tap arms the modifier for the next key only; a long press latches it until pressed again.
	private void processToggleButton(int keycode, boolean lock)
	{
		switch (keycode)
		{
			case VK_LSHIFT:
			{
				isShiftLocked = lock && !isShiftLocked;
				shiftPressed = applyModifier(VK_LSHIFT, shiftPressed, isShiftLocked, lock);
				break;
			}
			case VK_LCONTROL:
			{
				isCtrlLocked = lock && !isCtrlLocked;
				ctrlPressed = applyModifier(VK_LCONTROL, ctrlPressed, isCtrlLocked, lock);
				break;
			}
			case VK_LMENU:
			{
				isAltLocked = lock && !isAltLocked;
				altPressed = applyModifier(VK_LMENU, altPressed, isAltLocked, lock);
				break;
			}
			case VK_LWIN:
			{
				isWinLocked = lock && !isWinLocked;
				winPressed = applyModifier(VK_LWIN | VK_EXT_KEY, winPressed, isWinLocked, lock);
				break;
			}
		}
		listener.modifiersChanged();
	}

	// Reports the new pressed state only on a real change, so long pressing an already armed
	// modifier does not send a second key down.
	private boolean applyModifier(int vk, boolean pressed, boolean locked, boolean lock)
	{
		boolean target = lock ? locked : !pressed;
		if (target != pressed)
		{
			if (target)
				physicalKeys.acquire(uiModifierOwner, vk);
			else
				physicalKeys.release(uiModifierOwner, vk, true);
		}
		return target;
	}

	public void clearlAllModifiers()
	{
		resetModifierKeysAfterInput(true);
	}

	private void resetModifierKeysAfterInput(boolean force)
	{
		if (shiftPressed && (!isShiftLocked || force))
		{
			physicalKeys.release(uiModifierOwner, VK_LSHIFT, true);
			shiftPressed = false;
		}
		if (ctrlPressed && (!isCtrlLocked || force))
		{
			physicalKeys.release(uiModifierOwner, VK_LCONTROL, true);
			ctrlPressed = false;
		}
		if (altPressed && (!isAltLocked || force))
		{
			physicalKeys.release(uiModifierOwner, VK_LMENU, true);
			altPressed = false;
		}
		if (winPressed && (!isWinLocked || force))
		{
			physicalKeys.release(uiModifierOwner, VK_LWIN | VK_EXT_KEY, true);
			winPressed = false;
		}

		// drop the locks too, a stale lock flag would swallow the next long press
		if (force)
			isShiftLocked = isCtrlLocked = isAltLocked = isWinLocked = false;

		if (listener != null)
			listener.modifiersChanged();
	}

	private void switchKeyboard(int keycode)
	{
		switch (keycode)
		{
			case EXTKEY_KBFUNCTIONKEYS:
			{
				listener.switchKeyboard(KEYBOARD_TYPE_FUNCTIONKEYS);
				break;
			}

			case EXTKEY_KBNUMPAD:
			{
				listener.switchKeyboard(KEYBOARD_TYPE_NUMPAD);
				break;
			}

			default:
				break;
		}
	}

	// interface that gets called for input handling
	public interface KeyProcessingListener {
		void processVirtualKey(int virtualKeyCode, boolean down);

		void processUnicodeKey(int unicodeKey);

		void switchKeyboard(int keyboardType);

		void modifiersChanged();

		default void physicalAltReleased() {}
	}
}
