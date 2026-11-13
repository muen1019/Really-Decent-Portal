/*
   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   https://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.freerdp.freerdpcore.R;
import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.utils.ScreenInputController;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 37, qualifiers = "mdpi")
public class ScrollView2DCursorTest
{
	@Test public void croppedDesktopFollowsCursorNearBothEdges()
	{
		Viewport view = new Viewport(1200, 900);
		view.layout(600, 450);

		view.scroll.revealCursor(view.desktop, 300, 225);
		assertEquals(0, view.scroll.getScrollX());
		assertEquals(0, view.scroll.getScrollY());

		// These coordinates are still visible, but within the edge margin.
		view.scroll.revealCursor(view.desktop, 590, 440);
		assertTrue(view.scroll.getScrollX() > 0);
		assertTrue(view.scroll.getScrollY() > 0);
		assertTrue(view.scroll.getScrollX() < 50);
		assertTrue(view.scroll.getScrollY() < 50);

		view.scroll.revealCursor(view.desktop, 1199, 899);
		assertEquals(600, view.scroll.getScrollX());
		assertEquals(450, view.scroll.getScrollY());

		view.scroll.revealCursor(view.desktop, 0, 0);
		assertEquals(0, view.scroll.getScrollX());
		assertEquals(0, view.scroll.getScrollY());
	}

	@Test public void reducedHeightRevealsCursorAboveKeyboardWithoutMovingHorizontally()
	{
		Viewport view = new Viewport(600, 720);
		view.scroll.setPadding(20, 10, 20, 30);
		view.layout(640, 760);
		view.scroll.revealCursor(view.desktop, 300, 700);
		assertEquals(0, view.scroll.getScrollY());

		// A keyboard reserves 300 pixels of viewport height without resizing the desktop.
		view.layout(640, 460);
		view.scroll.revealCursor(view.desktop, 300, 700);
		int cursorOnScreen = view.content.getTop() + view.desktop.getTop() + 700 -
		    view.scroll.getScrollY();
		assertTrue(cursorOnScreen < view.scroll.getHeight() - view.scroll.getPaddingBottom());
		assertEquals(0, view.scroll.getScrollX());
		assertTrue(view.scroll.getScrollY() > 0);
		assertTrue(view.scroll.getScrollY() <= 300);

		view.layout(640, 760);
		view.scroll.revealCursor(view.desktop, 300, 700);
		assertEquals(0, view.scroll.getScrollY());
	}

	@Test public void fullyVisibleDesktopDoesNotPanAtItsEdges()
	{
		Viewport view = new Viewport(480, 320);
		view.layout(640, 480);

		view.scroll.revealCursor(view.desktop, 479, 319);
		view.scroll.revealCursor(view.desktop, 0, 0);

		assertEquals(0, view.scroll.getScrollX());
		assertEquals(0, view.scroll.getScrollY());
	}

	@Test public void viewportPanDoesNotTurnStationaryFingerIntoCursorMovement()
	{
		Activity context = Robolectric.buildActivity(Activity.class).setup().get();
		SessionView desktop = new SessionView(context);
		desktop.setId(R.id.sessionView);
		List<Point> moves = new ArrayList<>();
		desktop.setScreenInputSink(new ScreenInputController.Sink() {
			@Override public boolean isDirectTouchAvailable() { return false; }
			@Override public boolean onTouchFrame(ScreenInputController.TouchFrame frame) { return false; }
			@Override public boolean canCoastPointer() { return false; }
			@Override public void onMouseMove(int x, int y) { moves.add(new Point(x, y)); }
			@Override public void onMouseButton(int button, boolean down, int x, int y) {}
			@Override public void onScroll(boolean horizontal, boolean positive) {}
			@Override public void onLocalZoom(float factor, float x, float y) {}
		});
		SessionState state = new SessionState(1, (Uri)null);
		state.setSurface(new BitmapDrawable(context.getResources(),
		    Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)));
		desktop.onSurfaceChange(state);
		desktop.setZoom(1.5f);
		Viewport view = new Viewport(context, desktop, FrameLayout.LayoutParams.WRAP_CONTENT,
		    FrameLayout.LayoutParams.WRAP_CONTENT);
		context.setContentView(view.scroll);
		view.layout(600, 450);

		dispatchFinger(desktop, MotionEvent.ACTION_DOWN, 10, 200, 150);
		dispatchFinger(desktop, MotionEvent.ACTION_MOVE, 30, 230, 180);
		assertEquals(1, moves.size());
		assertEquals(new Point(620, 470), moves.get(0));

		// Run the same reveal used by the posted animation callback after touch dispatch.
		int[] before = new int[2];
		desktop.getLocationOnScreen(before);
		desktop.revealScreenCursor();
		assertTrue(view.scroll.getScrollX() > 0);
		assertTrue(view.scroll.getScrollY() > 0);
		int[] after = new int[2];
		desktop.getLocationOnScreen(after);
		assertEquals(view.scroll.getScrollX(), before[0] - after[0]);
		assertEquals(view.scroll.getScrollY(), before[1] - after[1]);
		dispatchFinger(desktop, MotionEvent.ACTION_MOVE, 45, 230, 180);
		assertEquals(1, moves.size());

		dispatchFinger(desktop, MotionEvent.ACTION_MOVE, 65, 260, 180);
		dispatchFinger(desktop, MotionEvent.ACTION_UP, 80, 260, 180);
		assertEquals(2, moves.size());
		assertEquals(new Point(640, 470), moves.get(1));

		int oldScrollY = view.scroll.getScrollY();
		view.layout(600, 300);
		assertTrue(view.scroll.getScrollY() > oldScrollY);
		assertTrue(705 - view.scroll.getScrollY() < 300);
		assertEquals(2, moves.size());
	}

	private static void dispatchFinger(SessionView desktop, int action, long time, int x, int y)
	{
		MotionEvent.PointerProperties finger = new MotionEvent.PointerProperties();
		finger.id = 0;
		finger.toolType = MotionEvent.TOOL_TYPE_FINGER;
		MotionEvent.PointerCoords position = new MotionEvent.PointerCoords();
		int[] origin = new int[2];
		desktop.getLocationOnScreen(origin);
		position.x = x - origin[0];
		position.y = y - origin[1];
		position.pressure = action == MotionEvent.ACTION_UP ? 0f : 1f;
		MotionEvent event = MotionEvent.obtain(10, time, action, 1,
		    new MotionEvent.PointerProperties[]{finger}, new MotionEvent.PointerCoords[]{position},
		    0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
		try
		{
			assertTrue(desktop.onTouchEvent(event));
		}
		finally
		{
			event.recycle();
		}
	}

	private static final class Viewport
	{
		final ScrollView2D scroll;
		final FrameLayout content;
		final View desktop;

		Viewport(int desktopWidth, int desktopHeight)
		{
			this(RuntimeEnvironment.getApplication(), new View(RuntimeEnvironment.getApplication()),
			    desktopWidth, desktopHeight);
		}

		Viewport(Context context, View desktop, int desktopWidth, int desktopHeight)
		{
			scroll = new ScrollView2D(context);
			content = new FrameLayout(context);
			this.desktop = desktop;
			content.addView(desktop, new FrameLayout.LayoutParams(desktopWidth, desktopHeight));
			scroll.addView(content, new FrameLayout.LayoutParams(
			    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
		}

		void layout(int width, int height)
		{
			scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
			               View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
			scroll.layout(0, 0, width, height);
		}
	}
}
