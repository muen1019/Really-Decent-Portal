/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package com.freerdp.freerdpcore.utils;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.InputDeviceBuilder;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 37)
public class KeyboardMapperTest
{
	private static final int DEVICE = 31;
	private final List<String> sent = new ArrayList<>();
	private KeyboardMapper mapper;
	private long time;

	private KeyboardMapper.KeyProcessingListener recorder(List<String> target)
	{
		return new KeyboardMapper.KeyProcessingListener() {
			@Override public void processVirtualKey(int vk, boolean down) { target.add(vk + (down ? "+" : "-")); }
			@Override public void processUnicodeKey(int unicode) { target.add("U:" + unicode); }
			@Override public void switchKeyboard(int keyboard) { target.add("K:" + keyboard); }
			@Override public void modifiersChanged() { }
		};
	}

	@Before
	public void setUp()
	{
		Context app = RuntimeEnvironment.getApplication();
		Resources base = app.getResources();
		// The host harness generates R.integer from the real values/integers.xml.
		// Only resources are supplied here; KeyEvent and InputDevice run in Robolectric.
		Resources integers = new Resources(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()) {
			@Override public int getInteger(int id) { return id; }
		};
		Context context = new ContextWrapper(app) {
			@Override public Resources getResources() { return integers; }
		};
		InputDevice keyboard = InputDeviceBuilder.newBuilder().setId(DEVICE).setName("Physical keyboard fixture")
		    .setExternal(true).setSources(InputDevice.SOURCE_KEYBOARD)
		    .setKeyboardType(InputDevice.KEYBOARD_TYPE_ALPHABETIC).build();
		Shadows.shadowOf(app.getSystemService(InputManager.class)).addInputDevice(keyboard);
		assertFalse(InputDevice.getDevice(DEVICE).isVirtual());
		assertEquals(InputDevice.KEYBOARD_TYPE_ALPHABETIC, InputDevice.getDevice(DEVICE).getKeyboardType());
		mapper = new KeyboardMapper();
		mapper.init(context);
		mapper.reset(recorder(sent));
		time = 1000;
	}

	private KeyEvent event(int action, int key, long downTime, int repeat, int meta)
	{
		KeyEvent result = new KeyEvent(downTime, ++time, action, key, repeat, meta, DEVICE, 0, 0, InputDevice.SOURCE_KEYBOARD);
		// Robolectric 4.17 ShadowInputEvent.getDevice() uses a fixture field rather than
		// resolving the registered device ID as Android InputEvent does.
		Shadows.shadowOf(result).setDevice(InputDevice.getDevice(DEVICE));
		assertNotNull(result.getDevice());
		assertFalse(result.getDevice().isVirtual());
		assertEquals(InputDevice.KEYBOARD_TYPE_ALPHABETIC, result.getDevice().getKeyboardType());
		assertTrue(result.isFromSource(InputDevice.SOURCE_KEYBOARD));
		assertEquals(0, result.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD);
		return result;
	}

	private void down(int key, long downTime, int meta)
	{
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, key, downTime, 0, meta)));
	}

	private void up(int key, long downTime, int meta)
	{
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_UP, key, downTime, 0, meta)));
	}

	private void expected(String... events)
	{
		assertEquals(Arrays.asList(events), sent);
	}

	@Test
	public void allEightPhysicalModifiersHaveOneRealDownAndUpIncludingRepeat()
	{
		int[] keys = { KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
		    KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_ALT_LEFT,
		    KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT };
		int[] vk = { 160, 161, 162, 419, 164, 421, 347, 348 };
		for (int i = 0; i < keys.length; i++)
		{
			sent.clear();
			down(keys[i], time, 0);
			long downTime = time - 1;
			assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, keys[i], downTime, 1, 0)));
			expected(vk[i] + "+");
			up(keys[i], downTime, 0);
			expected(vk[i] + "+", vk[i] + "-");
		}
	}

	@Test
	public void altTabKeepsBothKeysDownUntilEachActualRelease()
	{
		down(KeyEvent.KEYCODE_ALT_LEFT, 100, KeyEvent.META_ALT_ON);
		down(KeyEvent.KEYCODE_TAB, 110, KeyEvent.META_ALT_ON);
		expected("164+", "9+");
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 110, 1, KeyEvent.META_ALT_ON)));
		up(KeyEvent.KEYCODE_TAB, 110, KeyEvent.META_ALT_ON);
		down(KeyEvent.KEYCODE_TAB, 120, KeyEvent.META_ALT_ON);
		up(KeyEvent.KEYCODE_TAB, 120, KeyEvent.META_ALT_ON);
		up(KeyEvent.KEYCODE_ALT_LEFT, 100, 0);
		expected("164+", "9+", "9+", "9-", "9+", "9-", "164-");
	}

	@Test
	public void winTabDoesNotBufferWinOrCreateAReleaseTimeTap()
	{
		down(KeyEvent.KEYCODE_META_RIGHT, 100, KeyEvent.META_META_ON | KeyEvent.META_META_RIGHT_ON);
		down(KeyEvent.KEYCODE_TAB, 110, KeyEvent.META_META_ON | KeyEvent.META_META_RIGHT_ON);
		expected("348+", "9+");
		up(KeyEvent.KEYCODE_TAB, 110, KeyEvent.META_META_ON);
		up(KeyEvent.KEYCODE_META_RIGHT, 100, 0);
		expected("348+", "9+", "9-", "348-");
	}

	@Test
	public void shiftAloneAndShiftAUseIndependentPhysicalStreams()
	{
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 100, KeyEvent.META_SHIFT_ON);
		up(KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0);
		down(KeyEvent.KEYCODE_SHIFT_RIGHT, 110, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);
		down(KeyEvent.KEYCODE_A, 120, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);
		up(KeyEvent.KEYCODE_A, 120, KeyEvent.META_SHIFT_ON);
		up(KeyEvent.KEYCODE_SHIFT_RIGHT, 110, 0);
		expected("160+", "160-", "161+", "65+", "65-", "161-");
	}

	@Test
	public void shiftArrowAndRightControlUseTheExistingExtendedKeyContract()
	{
		down(KeyEvent.KEYCODE_SHIFT_RIGHT, 100, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);
		down(KeyEvent.KEYCODE_DPAD_LEFT, 110, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON);
		up(KeyEvent.KEYCODE_DPAD_LEFT, 110, KeyEvent.META_SHIFT_ON);
		up(KeyEvent.KEYCODE_SHIFT_RIGHT, 100, 0);
		down(KeyEvent.KEYCODE_CTRL_RIGHT, 120, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON);
		down(KeyEvent.KEYCODE_C, 130, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON);
		up(KeyEvent.KEYCODE_C, 130, KeyEvent.META_CTRL_ON);
		up(KeyEvent.KEYCODE_CTRL_RIGHT, 120, 0);
		expected("161+", "293+", "293-", "161-", "419+", "67+", "67-", "419-");
	}

	@Test
	public void heldMappedKeyNeverFallsBackToUnicodeWhenMetadataChanges()
	{
		down(KeyEvent.KEYCODE_A, 100, 0);
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 100, 1, KeyEvent.META_SYM_ON)));
		up(KeyEvent.KEYCODE_A, 100, KeyEvent.META_SYM_ON | KeyEvent.META_SHIFT_ON);
		expected("65+", "65+", "65-");
	}

	@Test
	public void sameCallbackAcrossAccessibilityAndActivityIsSentOnlyOnce()
	{
		KeyEvent down = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 100, 0, 0);
		KeyEvent repeat = event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 100, 1, 0);
		KeyEvent up = event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 100, 0, 0);
		for (KeyEvent callback : new KeyEvent[] { down, repeat, up })
		{
			assertTrue(mapper.processAndroidKeyEvent(callback, KeyboardMapper.KeyEventOrigin.ACCESSIBILITY));
			assertTrue(mapper.processAndroidKeyEvent(new KeyEvent(callback), KeyboardMapper.KeyEventOrigin.ACTIVITY));
		}
		expected("9+", "9+", "9-");
	}

	@Test
	public void laterRepeatsAreNotDeduplicatedByKeyOrDownTime()
	{
		down(KeyEvent.KEYCODE_A, 100, 0);
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 100, 1, 0)));
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 100, 2, 0)));
		up(KeyEvent.KEYCODE_A, 100, 0);
		expected("65+", "65+", "65+", "65-");
	}

	@Test
	public void uiLockAndPhysicalShiftCannotReleaseEachOther()
	{
		assertTrue(mapper.processCustomKeyLock(8)); // real keycode_toggle_shift value
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0);
		assertTrue(mapper.processCustomKeyLock(8)); // Remove UI owner; physical Shift remains.
		expected("160+");
		up(KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0);
		expected("160+", "160-");

		sent.clear();
		assertTrue(mapper.processCustomKeyLock(8));
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 200, 0);
		up(KeyEvent.KEYCODE_SHIFT_LEFT, 200, 0);
		expected("160+");
		mapper.clearlAllModifiers();
		expected("160+", "160-");
	}

	@Test
	public void uiStickyTransferLastsThroughThePhysicalLetterRepeatAndUp()
	{
		mapper.processCustomKeyEvent(8);
		down(KeyEvent.KEYCODE_A, 100, 0);
		expected("160+", "65+");
		assertEquals(KeyboardMapper.KEYSTATE_OFF, mapper.getModifierState(8));
		assertTrue(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 100, 1, 0)));
		up(KeyEvent.KEYCODE_A, 100, 0);
		expected("160+", "65+", "65+", "65-", "160-");
	}

	@Test
	public void cleanupConsumesLateUpWithoutWritingToTheNewListener()
	{
		down(KeyEvent.KEYCODE_ALT_LEFT, 100, KeyEvent.META_ALT_ON);
		down(KeyEvent.KEYCODE_TAB, 110, KeyEvent.META_ALT_ON);
		mapper.releaseOwnedKeys(true);
		mapper.releaseOwnedKeys(true);
		expected("164+", "9+", "9-", "164-");
		List<String> replacement = new ArrayList<>();
		mapper.reset(recorder(replacement));
		assertTrue(mapper.consumeOwnedKeyUp(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 110, 0, 0)));
		assertTrue(mapper.consumeOwnedKeyUp(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, 100, 0, 0)));
		assertTrue(replacement.isEmpty());
		down(KeyEvent.KEYCODE_A, 200, 0);
		up(KeyEvent.KEYCODE_A, 200, 0);
		assertEquals(Arrays.asList("65+", "65-"), replacement);
	}

	@Test
	public void canceledUpReleasesAndUnknownUpHasNoRemoteSideEffect()
	{
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 100, KeyEvent.META_SHIFT_ON);
		KeyEvent canceled = KeyEvent.changeFlags(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0, 0), KeyEvent.FLAG_CANCELED);
		assertTrue(mapper.processAndroidKeyEvent(canceled));
		assertFalse(mapper.processAndroidKeyEvent(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 200, 0, 0)));
		expected("160+", "160-");
	}

	@Test
	public void virtualKeyboardStillUsesLegacyPulseAndUnicodeMapping()
	{
		KeyEvent soft = new KeyEvent(100, 110, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0,
		    -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD, InputDevice.SOURCE_KEYBOARD);
		assertTrue(mapper.processAndroidKeyEvent(soft));
		assertFalse(mapper.processAndroidKeyEvent(KeyEvent.changeAction(soft, KeyEvent.ACTION_UP)));
		expected("65+", "65-");
		sent.clear();
		down(KeyEvent.KEYCODE_NUMPAD_EQUALS, 200, 0);
		expected("U:61");
	}

	@Test
	public void actionMultipleTextRetainsTheExistingUnicodePath()
	{
		assertTrue(mapper.processAndroidKeyEvent(new KeyEvent(100, "中文", -1, KeyEvent.FLAG_SOFT_KEYBOARD)));
		expected("U:20013", "U:25991");
	}

	@Test
	public void deviceRemovalRetainsUiLockUntilExplicitUiCleanup()
	{
		mapper.processCustomKeyLock(8);
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0);
		down(KeyEvent.KEYCODE_A, 110, 0);
		mapper.releaseDeviceKeys(DEVICE, true);
		expected("160+", "65+", "65-");
		mapper.clearlAllModifiers();
		expected("160+", "65+", "65-", "160-");
	}

	@Test
	public void overlappingMetadataFallbacksDoNotReleaseEachOtherEarly()
	{
		// This is a metadata fallback, not evidence of a physical Shift down callback.
		down(KeyEvent.KEYCODE_A, 100, KeyEvent.META_SHIFT_ON);
		down(KeyEvent.KEYCODE_B, 110, KeyEvent.META_SHIFT_ON);
		up(KeyEvent.KEYCODE_A, 100, 0);
		expected("160+", "65+", "66+", "65-");
		up(KeyEvent.KEYCODE_B, 110, 0);
		expected("160+", "65+", "66+", "65-", "66-", "160-");
	}

	@Test
	public void softKeyboardFlagOnARealDeviceDoesNotOptIntoPhysicalHold()
	{
		KeyEvent soft = KeyEvent.changeFlags(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 100, 0, 0), KeyEvent.FLAG_SOFT_KEYBOARD);
		Shadows.shadowOf(soft).setDevice(InputDevice.getDevice(DEVICE));
		assertNotNull(soft.getDevice());
		assertTrue((soft.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0);
		assertTrue(mapper.processAndroidKeyEvent(soft));
		expected("65+", "65-");
		assertFalse(mapper.hasOwnedKey(soft));
	}

	@Test
	public void consumedCleanupEscUpCopiesRemainOwnedForRemappedBackDispatch()
	{
		down(KeyEvent.KEYCODE_ESCAPE, 100, 0);
		mapper.releaseOwnedKeys(true);
		KeyEvent up = event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 100, 0, 0);
		assertTrue(mapper.consumeOwnedKeyUp(up));
		assertTrue(mapper.hasOwnedKey(new KeyEvent(up)));
		assertTrue(mapper.consumeOwnedKeyUp(new KeyEvent(up)));
		expected("27+", "27-");
		List<String> replacement = new ArrayList<>();
		mapper.reset(recorder(replacement));
		assertTrue(mapper.hasOwnedKey(new KeyEvent(up)));
		assertTrue(mapper.consumeOwnedKeyUp(new KeyEvent(up)));
		assertTrue(mapper.processAndroidKeyEvent(new KeyEvent(up), KeyboardMapper.KeyEventOrigin.ACTIVITY));
		assertTrue(replacement.isEmpty());
	}

	@Test
	public void inactiveConsumeCannotSilentlyDiscardAnActiveHeldKey()
	{
		down(KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0);
		KeyEvent up = event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 100, 0, 0);
		assertFalse(mapper.consumeOwnedKeyUp(up));
		assertTrue(mapper.processAndroidKeyEvent(up));
		expected("160+", "160-");
	}
}
