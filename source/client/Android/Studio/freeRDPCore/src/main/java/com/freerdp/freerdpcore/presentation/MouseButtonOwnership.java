/*
   Mouse button ownership for physical and screen input

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import java.util.HashMap;
import java.util.Map;

/** A screen gesture must not release a button still held by the physical mouse. */
final class MouseButtonOwnership
{
	static final int HARDWARE = 0;
	static final int SCREEN = 1;
	private final int[] buttons = new int[2];
	private final Map<Integer, Integer> hardwareDevices = new HashMap<>();

	int updateHardware(int deviceId, int button, boolean down)
	{
		int bit = bit(button);
		int before = heldButtons();
		int held = hardwareDevices.getOrDefault(deviceId, 0);
		held = down ? held | bit : held & ~bit;
		if (held == 0)
			hardwareDevices.remove(deviceId);
		else
			hardwareDevices.put(deviceId, held);
		int after = heldButtons();
		return (before & bit) == (after & bit) ? 0 : (down ? 1 : -1);
	}

	int releaseDevice(int deviceId)
	{
		int before = heldButtons();
		hardwareDevices.remove(deviceId);
		return before & ~heldButtons();
	}

	int heldButtons()
	{
		int held = buttons[HARDWARE] | buttons[SCREEN];
		for (int deviceButtons : hardwareDevices.values())
			held |= deviceButtons;
		return held;
	}

	/** Returns 1 for a new remote down, -1 for the last remote up, or 0. */
	int update(int owner, int button, boolean down)
	{
		checkOwner(owner);
		int bit = bit(button);
		int before = heldButtons();
		if (down)
			buttons[owner] |= bit;
		else
			buttons[owner] &= ~bit;
		int after = heldButtons();
		return (before & bit) == (after & bit) ? 0 : (down ? 1 : -1);
	}

	/** Returns the remote-up bit mask after retiring only this owner's buttons. */
	int releaseOwner(int owner)
	{
		checkOwner(owner);
		int before = heldButtons();
		buttons[owner] = 0;
		return before & ~heldButtons();
	}

	/** Clears a disconnected stream without transferring ownership to a new session. */
	int clear()
	{
		int before = heldButtons();
		buttons[HARDWARE] = 0;
		buttons[SCREEN] = 0;
		hardwareDevices.clear();
		return before;
	}

	static int bit(int button)
	{
		if (button < 1 || button > 5)
			throw new IllegalArgumentException("Unsupported mouse button");
		return 1 << (button - 1);
	}

	private static void checkOwner(int owner)
	{
		if (owner != HARDWARE && owner != SCREEN)
			throw new IllegalArgumentException("Unsupported mouse owner");
	}
}
