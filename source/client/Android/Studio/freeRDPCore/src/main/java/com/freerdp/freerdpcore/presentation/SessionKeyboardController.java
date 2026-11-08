/*
   Session keyboard lifecycle

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.presentation;

// Small policy shared by the Activity callbacks. No input is accepted merely because an
// Activity is resumed: it must also own window focus. The owner performs native cleanup.
final class SessionKeyboardController
{
	interface Owner
	{
		void onKeyboardActiveChanged(boolean active);
		void detachKeyboardSession(boolean sendKeyUps);
	}

	private final Owner owner;
	private boolean resumed;
	private boolean focused;
	private boolean destroyed;

	SessionKeyboardController(Owner owner)
	{
		this.owner = owner;
	}

	void onResume()
	{
		resumed = true;
		refresh();
	}

	void onPause()
	{
		resumed = false;
		focused = false;
		refresh();
	}

	void onWindowFocusChanged(boolean focused)
	{
		this.focused = focused;
		refresh();
	}

	void onTransportClosed()
	{
		// The transport has already stopped. Only discard local state; never replay old
		// releases after another native instance is attached.
		owner.detachKeyboardSession(false);
	}

	void onDestroy()
	{
		destroyed = true;
		refresh();
		owner.detachKeyboardSession(true);
	}

	void refresh()
	{
		owner.onKeyboardActiveChanged(resumed && focused && !destroyed);
	}
}
