/*
   Modified 2026-11-05: support guarded client-owned captured-key repeat.
   Android Keyboard Mapping

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session-local ownership of remote keys; deliberately independent of Android dispatch. */
final class PhysicalKeyState
{
	interface Sink
	{
		void send(int virtualKey, boolean down);
	}

	private static final int MAX_RELEASED_STREAMS = 128;
	private final Sink sink;
	private final Map<Stream, HeldKey> held = new LinkedHashMap<>();
	private final Map<Integer, Set<Object>> owners = new LinkedHashMap<>();
	private final Set<Stream> released = new LinkedHashSet<>();

	PhysicalKeyState(Sink sink)
	{
		this.sink = sink;
	}

	private static final class Stream
	{
		final int device;
		final int key;
		final long downTime;

		Stream(int device, int key, long downTime)
		{
			this.device = device;
			this.key = key;
			this.downTime = downTime;
		}

		@Override
		public boolean equals(Object value)
		{
			if (!(value instanceof Stream))
				return false;
			Stream other = (Stream)value;
			return device == other.device && key == other.key && downTime == other.downTime;
		}

		@Override
		public int hashCode()
		{
			return 31 * (31 * device + key) + Long.hashCode(downTime);
		}
	}

	private static final class HeldKey
	{
		final int vk;
		final boolean modifier;
		final List<Integer> inferredModifiers = new ArrayList<>();

		HeldKey(int vk, boolean modifier)
		{
			this.vk = vk;
			this.modifier = modifier;
		}
	}

	/** Atomically repeat only a currently held ordinary key, never a released tombstone. */
	boolean repeatHeld(int device, int key, long downTime)
	{
		HeldKey existing = held.get(new Stream(device, key, downTime));
		if (existing == null || existing.modifier)
			return false;
		sink.send(existing.vk, true);
		return true;
	}

	boolean owns(int device, int key, long downTime)
	{
		Stream stream = new Stream(device, key, downTime);
		return held.containsKey(stream) || released.contains(stream);
	}

	boolean isPressed(int vk)
	{
		return owners.containsKey(vk);
	}

	boolean isPhysicalModifierPressed(int vk)
	{
		for (HeldKey key : held.values())
		{
			if (key.modifier && key.vk == vk)
				return true;
		}
		return false;
	}

	void acquire(Object owner, int vk)
	{
		Set<Object> keyOwners = owners.get(vk);
		if (keyOwners == null)
		{
			keyOwners = new LinkedHashSet<>();
			owners.put(vk, keyOwners);
			keyOwners.add(owner); // Record the send attempt before calling the transport.
			sink.send(vk, true);
		}
		else
			keyOwners.add(owner);
	}

	void release(Object owner, int vk, boolean send)
	{
		Set<Object> keyOwners = owners.get(vk);
		if (keyOwners == null || !keyOwners.remove(owner))
			return;
		if (keyOwners.isEmpty())
		{
			owners.remove(vk);
			if (send)
				sink.send(vk, false);
		}
	}

	void pulse(int vk)
	{
		if (isPressed(vk))
		{
			sink.send(vk, true);
			return; // A pulse must not release a key held by a physical or UI owner.
		}
		Object owner = new Object();
		acquire(owner, vk);
		release(owner, vk, true);
	}

	void down(int device, int key, long downTime, int repeat, int vk, boolean modifier,
	          int[] inferredModifiers)
	{
		Stream stream = new Stream(device, key, downTime);
		HeldKey existing = held.get(stream);
		if (existing != null)
		{
			if (!existing.modifier)
				sink.send(existing.vk, true);
			return;
		}
		// A late repeat after focus loss must not recreate an already released stream.
		if (repeat > 0 || released.contains(stream))
			return;

		// Repair a missing up before a fresh down for the same physical key.
		for (Stream old : new ArrayList<>(held.keySet()))
		{
			if (old.device == device && old.key == key)
				releaseStream(old, true);
		}
		HeldKey owned = new HeldKey(vk, modifier);
		held.put(stream, owned);
		for (int inferred : inferredModifiers)
		{
			owned.inferredModifiers.add(inferred);
			acquire(owned, inferred);
		}
		boolean alreadyPressed = isPressed(vk);
		acquire(owned, vk);
		// Distinct physical ordinary-key downs are input, even when another device still
		// owns the same VK. Only the final owner's up may release that remote key.
		if (!modifier && alreadyPressed)
			sink.send(vk, true);
	}

	boolean up(int device, int key, long downTime, boolean send)
	{
		Stream stream = new Stream(device, key, downTime);
		if (held.containsKey(stream))
		{
			releaseStream(stream, send);
			released.remove(stream);
			return true;
		}
		return released.remove(stream);
	}

	boolean consumeReleasedUp(int device, int key, long downTime)
	{
		return released.remove(new Stream(device, key, downTime));
	}

	private void releaseStream(Stream stream, boolean send)
	{
		HeldKey owned = held.remove(stream);
		if (owned == null)
			return;
		rememberReleased(stream);
		release(owned, owned.vk, send);
		for (int i = owned.inferredModifiers.size() - 1; i >= 0; i--)
			release(owned, owned.inferredModifiers.get(i), send);
	}

	private void rememberReleased(Stream stream)
	{
		released.add(stream);
		while (released.size() > MAX_RELEASED_STREAMS)
			released.remove(released.iterator().next());
	}

	void releaseDevice(int device, boolean send)
	{
		// Release ordinary keys before their held modifiers, independent of press order.
		for (boolean modifier : new boolean[] { false, true })
		{
			for (Stream stream : new ArrayList<>(held.keySet()))
			{
				if (stream.device == device && held.get(stream).modifier == modifier)
					releaseStream(stream, send);
			}
		}
	}

	void releaseAll(boolean send)
	{
		for (boolean modifier : new boolean[] { false, true })
		{
			for (Stream stream : new ArrayList<>(held.keySet()))
			{
				if (held.get(stream).modifier == modifier)
					releaseStream(stream, send);
			}
		}
		// Remaining owners belong to UI sticky/lock or a synchronous legacy pulse.
		for (int vk : new ArrayList<>(owners.keySet()))
		{
			owners.remove(vk);
			if (send)
				sink.send(vk, false);
		}
	}
}
