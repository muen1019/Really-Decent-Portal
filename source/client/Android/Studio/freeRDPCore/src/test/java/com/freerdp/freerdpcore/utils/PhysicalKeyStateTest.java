/* This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package com.freerdp.freerdpcore.utils;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PhysicalKeyStateTest
{
	private final List<String> sent = new ArrayList<>();
	private final PhysicalKeyState state = new PhysicalKeyState((vk, down) -> sent.add(vk + (down ? "+" : "-")));
	private static final int[] NONE = new int[0];

	private void expected(String... events)
	{
		assertEquals(Arrays.asList(events), sent);
	}

	@Test
	public void altRemainsHeldAcrossTwoTabPressesAndAnOrdinaryRepeat()
	{
		state.down(1, 57, 100, 0, 164, true, NONE);
		state.down(1, 61, 110, 0, 9, false, NONE);
		expected("164+", "9+"); // No synthetic Tab or Alt up on either down.
		state.down(1, 61, 110, 1, 9, false, NONE);
		state.up(1, 61, 110, true);
		state.down(1, 61, 150, 0, 9, false, NONE);
		state.up(1, 61, 150, true);
		state.up(1, 57, 100, true);
		expected("164+", "9+", "9+", "9-", "9+", "9-", "164-");
	}

	@Test
	public void modifierRepeatDoesNotDuplicateDownOrInventAReleaseTap()
	{
		state.down(1, 117, 100, 0, 347, true, NONE);
		state.down(1, 117, 100, 1, 347, true, NONE);
		state.down(1, 117, 100, 2, 347, true, NONE);
		state.up(1, 117, 100, true);
		expected("347+", "347-");
	}

	@Test
	public void repeatAndUpUseTheVirtualKeyAcceptedByTheFirstDown()
	{
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.down(1, 29, 100, 1, 99, false, new int[] { 160 });
		state.up(1, 29, 100, true);
		expected("65+", "65+", "65-");
	}

	@Test
	public void twoDevicesCannotReleaseEachOthersModifier()
	{
		state.down(1, 59, 100, 0, 160, true, NONE);
		state.down(2, 59, 101, 0, 160, true, NONE);
		state.up(1, 59, 100, true);
		expected("160+");
		state.up(2, 59, 101, true);
		expected("160+", "160-");
	}

	@Test
	public void twoDevicesOrdinaryDownsAreBothSentButOnlyTheLastUpReleases()
	{
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.down(2, 29, 110, 0, 65, false, NONE);
		expected("65+", "65+");
		state.up(1, 29, 100, true);
		expected("65+", "65+");
		assertTrue(state.isPressed(65));
		state.up(2, 29, 110, true);
		expected("65+", "65+", "65-");
	}

	@Test
	public void leftAndRightHaveIndependentVirtualKeys()
	{
		state.down(1, 59, 100, 0, 160, true, NONE);
		state.down(1, 60, 101, 0, 161, true, NONE);
		state.up(1, 59, 100, true);
		assertTrue(state.isPressed(161));
		state.up(1, 60, 101, true);
		expected("160+", "161+", "160-", "161-");
	}

	@Test
	public void stickyAndPhysicalOwnersDoNotReleaseEachOther()
	{
		Object ui = new Object();
		state.acquire(ui, 160);
		state.down(1, 59, 100, 0, 160, true, NONE);
		state.release(ui, 160, true);
		expected("160+");
		state.up(1, 59, 100, true);
		expected("160+", "160-");

		sent.clear();
		state.acquire(ui, 160);
		state.down(1, 59, 200, 0, 160, true, NONE);
		state.up(1, 59, 200, true);
		expected("160+");
		state.release(ui, 160, true);
		expected("160+", "160-");
	}

	@Test
	public void missingModifierMetadataIsOwnedUntilTheOrdinaryUp()
	{
		state.down(1, 29, 100, 0, 65, false, new int[] { 160 });
		state.down(2, 59, 101, 0, 160, true, NONE);
		state.up(1, 29, 100, true);
		expected("160+", "65+", "65-");
		state.up(2, 59, 101, true);
		expected("160+", "65+", "65-", "160-");
	}

	@Test
	public void cleanupReleasesOrdinaryKeysBeforeModifiersAndIsIdempotent()
	{
		state.down(1, 57, 100, 0, 164, true, NONE);
		state.down(1, 61, 110, 0, 9, false, NONE);
		state.releaseAll(true);
		state.releaseAll(true);
		expected("164+", "9+", "9-", "164-");
		assertTrue(state.up(1, 61, 110, true));
		assertTrue(state.up(1, 57, 100, true));
		assertFalse(state.up(1, 57, 100, true));
		expected("164+", "9+", "9-", "164-");
	}

	@Test
	public void disconnectedCleanupDoesNotSendOldReleasesToANewTransport()
	{
		state.down(1, 59, 100, 0, 160, true, NONE);
		state.releaseAll(false);
		state.down(1, 59, 200, 0, 160, true, NONE);
		assertTrue(state.up(1, 59, 100, true));
		assertTrue(state.isPressed(160));
		state.up(1, 59, 200, true);
		expected("160+", "160+", "160-");
	}

	@Test
	public void deviceRemovalPreservesAnotherDevicesAndUiOwners()
	{
		Object ui = new Object();
		state.acquire(ui, 160);
		state.down(1, 59, 100, 0, 160, true, NONE);
		state.down(1, 29, 101, 0, 65, false, NONE);
		state.down(2, 61, 102, 0, 9, false, NONE);
		state.releaseDevice(1, true);
		expected("160+", "65+", "9+", "65-");
		assertTrue(state.isPressed(9));
		assertTrue(state.isPressed(160));
		state.releaseAll(true);
		expected("160+", "65+", "9+", "65-", "9-", "160-");
	}

	@Test
	public void lateRepeatsAndUnknownUpsCannotInventAPressAfterCleanup()
	{
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.releaseAll(true);
		state.down(1, 29, 100, 1, 65, false, NONE);
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.down(2, 29, 200, 1, 65, false, NONE);
		assertFalse(state.up(3, 29, 300, true));
		expected("65+", "65-");
	}

	@Test
	public void newDownTimeRepairsAMissingUpWithoutAcceptingTheOldUp()
	{
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.down(1, 29, 200, 0, 65, false, NONE);
		state.up(1, 29, 100, true);
		assertTrue(state.isPressed(65));
		state.up(1, 29, 200, true);
		expected("65+", "65-", "65+", "65-");
	}

	@Test
	public void legacyPulseCannotReleaseAPhysicalOwner()
	{
		state.down(1, 29, 100, 0, 65, false, NONE);
		state.pulse(65);
		expected("65+", "65+");
		state.up(1, 29, 100, true);
		expected("65+", "65+", "65-");
	}
}
