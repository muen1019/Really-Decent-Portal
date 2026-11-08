/*
   Modified 2026-11-05: merge upstream 3.33.0 touch-route wheel dispatch while preserving custom input paths.
   Android Session view

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   Modified 2026-10-29: preserve physical key lifetimes before local IME processing.
   Modified 2026-10-30: defer AOSP legacy logical tap-drag until movement and retire its own contact.

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
   If a copy of the MPL was not distributed with this file, You can obtain one at
   http://mozilla.org/MPL/2.0/.
*/

// Modified 2026-10-30: preserve classified physical touchpad wheel resolution.

// Modified 2026-10-31: add opt-in physical-Alt touchpad browser navigation.
package com.freerdp.freerdpcore.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;

import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.utils.DoubleGestureDetector;
import com.freerdp.freerdpcore.utils.GestureDetector;
import com.freerdp.freerdpcore.utils.ScreenInputController;
import com.freerdp.freerdpcore.utils.PenInputController;
import com.freerdp.freerdpcore.utils.TouchpadWheelAccumulator;

import java.util.Stack;

public class SessionView extends View
{
	public static final float MAX_SCALE_FACTOR = 3.0f;
	public static final float MIN_SCALE_FACTOR = 0.75f;
	private static final String TAG = "SessionView";
	private static final float SCALE_FACTOR_DELTA = 0.0001f;
	private static final float TOUCH_SCROLL_DELTA = 10.0f;
	private int width;
	private int height;
	private BitmapDrawable surface;
	private Stack<Rect> invalidRegions;
	private int touchPointerPaddingWidth = 0;
	private int touchPointerPaddingHeight = 0;
	private SessionViewListener sessionViewListener = null;
	private OnZoomChangedListener zoomChangedListener = null;
	private boolean railMode = false;
	// helpers for scaling gesture handling
	private float scaleFactor = 1.0f;
	private Matrix scaleMatrix;
	private Matrix invScaleMatrix;
	private RectF invalidRegionF;
	private GestureDetector gestureDetector;
	private SessionState currentSession;

	// OS pixel factors convert classified physical touchpad travel to wheel units.
	private float notchPxX = 1.0f;
	private float notchPxY = 1.0f;
	private final TouchpadWheelAccumulator touchpadWheel = new TouchpadWheelAccumulator();
	private final TouchpadWheelAccumulator genericWheel = new TouchpadWheelAccumulator(true);
	private int touchpadScrollViewportWidth, touchpadScrollViewportHeight;
	private static final class BrowserNavigationContact
	{
		long downTime, time = -1;
		double x, y;
		boolean cancelled, finished, classified;
		float prefixX, prefixY;
		final java.util.Set<String> atTime = new java.util.HashSet<>();
	}
	private final SparseArray<BrowserNavigationContact> browserNavigationContacts = new SparseArray<>();
	private double touchpadPinchSteps;
	private int touchpadPinchDevice = -1;
	private long touchpadPinchDownTime;
	private long touchpadPinchSampleTime = -1;
	private static final class LegacyPointerContact
	{
		long downTime, eventTime;
		int pointerId, x, y, downX, downY;
		float rawX, rawY;
		boolean held;
	}
	private final SparseArray<LegacyPointerContact> legacyPointerContacts = new SparseArray<>();
	private static final double CONTENT_ZOOM_STEP = Math.log(1.1);
	private static final int MAX_CONTENT_ZOOM_STEPS_PER_EVENT = 8;

	private PenInputController penInputController;
	private int screenFingerDevice = -1;
	private long screenFingerDownTime;
	private int penCaptureDevice = -1;
	private long penCaptureDownTime;
	private int penCapturePointer;


	private ScreenInputController screenInputController;
	private ScreenInputMode screenInputMode = ScreenInputMode.TOUCHPAD;
	private boolean screenInputActive = true;
	private boolean showScreenPointer;
	private Bitmap screenCursorBitmap;
	private boolean showPenHoverPointer;
	private boolean penHoverInRange;
	private float penHoverX, penHoverY;
	private int penHoverDevice = -1, penHoverPointer = -1;
	private long penHoverTime;
	private boolean localPinchZoom;
	private final Paint screenPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private enum RemoteCursorState { UNRECEIVED, CUSTOM, DEFAULT, HIDDEN }
	private RemoteCursorState remoteCursorState = RemoteCursorState.UNRECEIVED;
	private PointerIcon remoteCursorIcon;
	private int[] cursorPixels = null;
	private int cursorWidth = 0;
	private int cursorHeight = 0;
	private int cursorHotX = 0;
	private int cursorHotY = 0;

	// private static final String TAG = "FreeRDP.SessionView";
	private DoubleGestureDetector doubleGestureDetector;
	public SessionView(Context context)
	{
		super(context);
		initSessionView(context);
	}

	public SessionView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initSessionView(context);
	}

	public SessionView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initSessionView(context);
	}

	private void initSessionView(Context context)
	{
		// Ensure the view is focusable so the soft keyboard can attach to it (required for API 30+)
		setFocusable(true);
		setFocusableInTouchMode(true);
		if (Build.VERSION.SDK_INT >= 33)
			setAutoHandwritingEnabled(false);

		invalidRegions = new Stack<>();
		gestureDetector = new GestureDetector(context, new SessionGestureListener(), null, true);
		ViewConfiguration vc = ViewConfiguration.get(context);
		notchPxX = Math.max(1.0f, vc.getScaledHorizontalScrollFactor());
		notchPxY = Math.max(1.0f, vc.getScaledVerticalScrollFactor());
		doubleGestureDetector =
		    new DoubleGestureDetector(context, null, new SessionDoubleGestureListener());

		scaleFactor = 1.0f;
		scaleMatrix = new Matrix();
		invScaleMatrix = new Matrix();
		invalidRegionF = new RectF();
	}

	/** Stylus icons are resolved separately from setPointerIcon's mouse icon. */
	@Override public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex)
	{
		int tool = event.getToolType(pointerIndex);
		if (!event.isFromSource(InputDevice.SOURCE_MOUSE) &&
		    event.isFromSource(InputDevice.SOURCE_STYLUS) &&
		    (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER))
			return remoteCursorState == RemoteCursorState.CUSTOM || remoteCursorState == RemoteCursorState.DEFAULT
			    ? remoteCursorIcon : PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_NULL);
		return super.onResolvePointerIcon(event, pointerIndex);
	}

	/* External Mouse Hover */
	@Override public boolean onHoverEvent(MotionEvent event)
	{
		if (handlePenInput(event))
			return true;
		if (ScreenInputController.isExternalPointerEvent(event) && sessionViewListener != null)
			sessionViewListener.onSessionViewPointerDevice(event.getDeviceId());
		if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE)
		{
			// Handle hover move event
			float x = event.getX();
			float y = event.getY();
			// Perform actions based on the hover position (x, y)
			MotionEvent mappedEvent = mapTouchEvent(event);
			sessionViewListener.onSessionViewMouseMove((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY());
			mappedEvent.recycle();
		}
		// Return true to indicate that you've handled the event
		return true;
	}

	public void setScaleGestureDetector(ScaleGestureDetector scaleGestureDetector)
	{
		doubleGestureDetector.setScaleGestureDetector(scaleGestureDetector);
	}

	public void setSessionViewListener(SessionViewListener sessionViewListener)
	{
		this.sessionViewListener = sessionViewListener;
	}


	/** Installs a native-independent sink; it owns only display-finger contacts/buttons. */
	public void setScreenInputSink(final ScreenInputController.Sink sink)
	{
		cancelScreenInput();
		if (sink == null) { screenInputController = null; return; }
		ViewConfiguration vc = ViewConfiguration.get(getContext());
		screenInputController = new ScreenInputController(new ScreenInputController.Sink() {
			public boolean isDirectTouchAvailable() { return sink.isDirectTouchAvailable(); }
			public boolean onTouchFrame(ScreenInputController.TouchFrame frame) { return sink.onTouchFrame(frame); }
			public void onMouseMove(int x, int y) { showScreenPointer = true; sink.onMouseMove(x,y); invalidate(); }
			public void onMouseButton(int button, boolean down, int x, int y) { showScreenPointer = true; sink.onMouseButton(button,down,x,y); invalidate(); }
			public void onScroll(boolean horizontal, boolean positive) { sink.onScroll(horizontal,positive); }
			public void onLocalZoom(float factor, float focusX, float focusY) {
				localPinchZoom = true;
				try { setZoom(Math.max(MIN_SCALE_FACTOR, Math.min(MAX_SCALE_FACTOR, scaleFactor * factor))); }
				finally { localPinchZoom = false; }
			}
		}, vc.getScaledTouchSlop(), Math.max(notchPxX, notchPxY), ViewConfiguration.getDoubleTapTimeout());
		screenInputController.setMode(screenInputMode);
		screenInputController.setActive(screenInputActive);
		if (width > 0 && height > 0) screenInputController.setGeometry(scaleFactor,width,height);
	}

	/** Installs an independent pen sink; changing finger mode never changes pen routing. */
	public void setPenInputSink(PenInputController.Sink sink)
	{
		cancelPenInput();
		penInputController = sink == null ? null : new PenInputController(sink);
		if (penInputController != null)
		{
			penInputController.setActive(screenInputActive);
			if (width > 0 && height > 0)
				penInputController.setGeometry(scaleFactor,width,height);
		}
	}

	private void updateParentInputCapture()
	{
		if (getParent() != null)
			getParent().requestDisallowInterceptTouchEvent(screenFingerDevice >= 0 || penCaptureDevice >= 0);
	}

	private boolean handleScreenFingerInput(MotionEvent event)
	{
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN)
		{
			screenFingerDevice = event.getDeviceId();
			screenFingerDownTime = event.getDownTime();
			updateParentInputCapture();
		}
		boolean handled = screenInputController.onTouchEvent(event);
		if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) &&
		    screenFingerDevice == event.getDeviceId() && screenFingerDownTime == event.getDownTime())
		{
			screenFingerDevice = -1;
			updateParentInputCapture();
		}
		return handled;
	}

	private boolean handlePenInput(MotionEvent event)
	{
		if (penInputController == null)
			return false;
		updatePenHoverPointer(event);
		boolean handled = penInputController.onMotionEvent(event);
		boolean hasPen = false;
		for (int i = 0; i < event.getPointerCount(); i++)
		{
			int tool = event.getToolType(i);
			hasPen |= tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
		}
		if (!handled && !hasPen)
			return false;
		int action = event.getActionMasked();
		int index = event.getActionIndex();
		int tool = event.getToolType(index);
		if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) &&
		    (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER))
		{
			penCaptureDevice = event.getDeviceId();
			penCaptureDownTime = event.getDownTime();
			penCapturePointer = event.getPointerId(index);
			updateParentInputCapture();
		}
		if (action == MotionEvent.ACTION_CANCEL)
		{
			if (screenInputController != null && screenFingerDevice == event.getDeviceId() &&
			    screenFingerDownTime == event.getDownTime())
			{
				screenInputController.cancelDevice(event.getDeviceId());
				screenFingerDevice = -1;
			}
		}
		else if (hasPen && screenInputController != null &&
		         (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN ||
		          action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_UP ||
		          action == MotionEvent.ACTION_UP))
		{
			MotionEvent fingers = screenFingerSubset(event);
			if (fingers != null)
			{
				try
				{
					if (ScreenInputController.isScreenFingerEvent(fingers))
						handleScreenFingerInput(fingers);
				}
				finally
				{
					fingers.recycle();
				}
			}
		}
		if (penCaptureDevice == event.getDeviceId() && penCaptureDownTime == event.getDownTime() &&
		    (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
		     (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(index) == penCapturePointer)))
			penCaptureDevice = -1;
		updateParentInputCapture();
		return true;
	}

	/** SDK-only pointer filtering: MotionEvent.split is not a public Android SDK API. */
	private MotionEvent screenFingerSubset(MotionEvent event)
	{
		int count = 0;
		for (int i = 0; i < event.getPointerCount(); i++)
			if (event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER)
				count++;
		if (count == 0)
			return null;
		int[] indices = new int[count];
		MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
		int changed = -1;
		for (int i = 0, n = 0; i < event.getPointerCount(); i++)
		{
			if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER)
				continue;
			indices[n] = i;
			properties[n] = new MotionEvent.PointerProperties();
			event.getPointerProperties(i,properties[n]);
			if (i == event.getActionIndex())
				changed = n;
			n++;
		}
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
		{
			if (changed < 0)
				action = MotionEvent.ACTION_MOVE;
			else if (count == 1)
				action = action == MotionEvent.ACTION_POINTER_DOWN ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
			else
				action |= changed << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		}
		int history = event.getHistorySize();
		MotionEvent result = MotionEvent.obtain(event.getDownTime(),
		    history > 0 ? event.getHistoricalEventTime(0) : event.getEventTime(),
		    history > 0 ? MotionEvent.ACTION_MOVE : action,count,properties,
		    screenFingerCoords(event,indices,history > 0 ? 0 : -1),event.getMetaState(),
		    event.getButtonState(),event.getXPrecision(),event.getYPrecision(),event.getDeviceId(),
		    event.getEdgeFlags(),event.getSource(),event.getFlags());
		for (int h = 1; h < history; h++)
			result.addBatch(event.getHistoricalEventTime(h),screenFingerCoords(event,indices,h),event.getMetaState());
		if (history > 0)
		{
			result.addBatch(event.getEventTime(),screenFingerCoords(event,indices,-1),event.getMetaState());
			result.setAction(action);
		}
		return result;
	}

	private MotionEvent.PointerCoords[] screenFingerCoords(MotionEvent event,int[] indices,int history)
	{
		MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[indices.length];
		for (int i = 0; i < indices.length; i++)
		{
			coords[i] = new MotionEvent.PointerCoords();
			if (history < 0)
				event.getPointerCoords(indices[i],coords[i]);
			else
				event.getHistoricalPointerCoords(indices[i],history,coords[i]);
		}
		return coords;
	}

	private void clearPenHoverPointer()
	{
		boolean wasVisible = showPenHoverPointer || penHoverInRange;
		showPenHoverPointer = false;
		penHoverInRange = false;
		penHoverDevice = -1;
		penHoverPointer = -1;
		if (wasVisible) invalidate();
	}

	private boolean systemShowsStylusPointer()
	{
		if (Build.VERSION.SDK_INT < 34) return false;
		InputManager manager = getContext().getSystemService(InputManager.class);
		return manager != null && manager.isStylusPointerIconEnabled();
	}

	/** Local feedback only: never moves the remote mouse or creates an input contact. */
	private void updatePenHoverPointer(MotionEvent event)
	{
		int action = event.getActionMasked();
		int index = event.getActionIndex();
		boolean same = penHoverDevice == event.getDeviceId() &&
		    penHoverPointer == event.getPointerId(index);
		if (action == MotionEvent.ACTION_CANCEL)
		{
			if (penHoverDevice == event.getDeviceId()) clearPenHoverPointer();
			return;
		}
		int tool = event.getToolType(index);
		if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER)
			return;
		if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN ||
		    action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
		{
			clearPenHoverPointer();
			return;
		}
		if (action == MotionEvent.ACTION_HOVER_EXIT)
		{
			if (same && event.getEventTime() >= penHoverTime) clearPenHoverPointer();
			return;
		}
		if (action != MotionEvent.ACTION_HOVER_ENTER && action != MotionEvent.ACTION_HOVER_MOVE)
			return;
		if (!screenInputActive || event.isFromSource(InputDevice.SOURCE_MOUSE) ||
		    !event.isFromSource(InputDevice.SOURCE_STYLUS))
		{
			clearPenHoverPointer();
			return;
		}
		if (same && event.getEventTime() < penHoverTime) return;
		float x = event.getX(index), y = event.getY(index);
		if (!Float.isFinite(x) || !Float.isFinite(y)) return;
		penHoverX = x;
		penHoverY = y;
		penHoverDevice = event.getDeviceId();
		penHoverPointer = event.getPointerId(index);
		penHoverTime = event.getEventTime();
		penHoverInRange = true;
		showPenHoverPointer = !systemShowsStylusPointer();
		invalidate();
	}

	public void cancelPenInput()
	{
		clearPenHoverPointer();
		if (penInputController != null)
			penInputController.cancel();
		penCaptureDevice = -1;
		updateParentInputCapture();
	}

	public void cancelPenInputDevice(int deviceId)
	{
		if (penHoverDevice == deviceId) clearPenHoverPointer();
		if (penInputController != null)
			penInputController.cancelDevice(deviceId);
		if (penCaptureDevice == deviceId)
			penCaptureDevice = -1;
		updateParentInputCapture();
	}

	public void setScreenInputMode(ScreenInputMode mode)
	{
		if (mode == null) throw new IllegalArgumentException("mode");
		screenInputMode = mode;
		if (screenInputController != null) screenInputController.setMode(mode);
		showScreenPointer = mode == ScreenInputMode.TOUCHPAD;
		invalidate();
	}
	public ScreenInputMode getScreenInputMode() { return screenInputMode; }
	public void cancelScreenInput() {
		resetTouchpadPinch();
		cancelTouchpadWheelInput();
		if (screenInputController != null) screenInputController.cancel();
		screenFingerDevice = -1;
		updateParentInputCapture();
	}
	public void setScreenInputActive(boolean active) {
		if (!active) cancelLegacyPointerInput();
		if (screenInputActive != active || !active) resetTouchpadPinch();
		if (screenInputActive != active || !active) cancelTouchpadWheelInput();
		screenInputActive = active;
		if (screenInputController != null) screenInputController.setActive(active);
		if (penInputController != null) penInputController.setActive(active);
		if (!active)
		{
			clearPenHoverPointer();
			screenFingerDevice = -1;
			penCaptureDevice = -1;
			updateParentInputCapture();
		}
	}
	public void cancelScreenInputDevice(int deviceId) {
		cancelLegacyPointerDevice(deviceId);
		if (touchpadPinchDevice == deviceId) resetTouchpadPinch();
		touchpadWheel.cancelDevice(deviceId);
		genericWheel.cancelDevice(deviceId);
		if (screenInputController != null) screenInputController.cancelDevice(deviceId);
		if (screenFingerDevice == deviceId) screenFingerDevice = -1;
		updateParentInputCapture();
	}
	public void updateScreenPointer(int x, int y) {
		if (screenInputController != null) screenInputController.setPointerPosition(x,y);
		showScreenPointer = false;
		invalidate();
	}
	public int getScreenPointerX() { return screenInputController == null ? 0 : screenInputController.getPointerX(); }
	public int getScreenPointerY() { return screenInputController == null ? 0 : screenInputController.getPointerY(); }
	@Override public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		if (!hasWindowFocus) cancelLegacyPointerInput();
		if (!hasWindowFocus)
		{
			cancelScreenInput();
			cancelPenInput();
		}
	}
	@Override protected void onDetachedFromWindow() {
		cancelLegacyPointerInput();
		cancelScreenInput();
		cancelPenInput();
		super.onDetachedFromWindow();
	}

	public void addInvalidRegion(Rect invalidRegion)
	{
		// correctly transform invalid region depending on current scaling
		invalidRegionF.set(invalidRegion);
		scaleMatrix.mapRect(invalidRegionF);
		invalidRegionF.roundOut(invalidRegion);

		invalidRegions.add(invalidRegion);
	}

	public void invalidateRegion()
	{
		invalidate(invalidRegions.pop());
	}

	public void onSurfaceChange(SessionState session)
	{
		cancelLegacyPointerInput();
		cancelScreenInput();
		cancelPenInput();
		surface = session.getSurface();
		Bitmap bitmap = surface.getBitmap();
		width = bitmap.getWidth();
		height = bitmap.getHeight();
		if (screenInputController != null) screenInputController.setGeometry(scaleFactor,width,height);
		if (penInputController != null) penInputController.setGeometry(scaleFactor,width,height);
		surface.setBounds(0, 0, width, height);

		setMinimumWidth(width);
		setMinimumHeight(height);

		requestLayout();
		currentSession = session;
	}

	public float getZoom()
	{
		return scaleFactor;
	}

	public void setRailMode(boolean rail)
	{
		if (railMode == rail)
			return;
		railMode = rail;
		invalidate();
	}

	public interface OnZoomChangedListener
	{
		void onZoomChanged(float zoom);
	}

	public void setOnZoomChangedListener(OnZoomChangedListener l)
	{
		zoomChangedListener = l;
	}

	public void setZoom(float factor)
	{
		if (!Float.isFinite(factor) || factor <= 0) return;
		if (scaleFactor != factor) cancelLegacyPointerInput();
		if (screenInputController != null && width > 0 && height > 0) {
			if (localPinchZoom) screenInputController.setLocalZoom(factor);
			else screenInputController.setGeometry(factor,width,height);
		}
		if (penInputController != null && width > 0 && height > 0)
			penInputController.setGeometry(factor,width,height);
		scaleFactor = factor;
		scaleMatrix.setScale(scaleFactor, scaleFactor);
		invScaleMatrix.setScale(1.0f / scaleFactor, 1.0f / scaleFactor);

		if (cursorPixels != null)
			applyScaledCursor();

		if (zoomChangedListener != null)
			zoomChangedListener.onZoomChanged(scaleFactor);

		requestLayout();
	}

	public boolean isAtMaxZoom()
	{
		return (scaleFactor > (MAX_SCALE_FACTOR - SCALE_FACTOR_DELTA));
	}

	public boolean isAtMinZoom()
	{
		return (scaleFactor < (MIN_SCALE_FACTOR + SCALE_FACTOR_DELTA));
	}

	public boolean zoomIn(float factor)
	{
		boolean res = true;
		float targetScale = scaleFactor + factor;
		if (targetScale > (MAX_SCALE_FACTOR - SCALE_FACTOR_DELTA))
		{
			targetScale = MAX_SCALE_FACTOR;
			res = false;
		}
		setZoom(targetScale);
		return res;
	}

	public boolean zoomOut(float factor)
	{
		boolean res = true;
		float targetScale = scaleFactor - factor;
		if (targetScale < (MIN_SCALE_FACTOR + SCALE_FACTOR_DELTA))
		{
			targetScale = MIN_SCALE_FACTOR;
			res = false;
		}
		setZoom(targetScale);
		return res;
	}

	public void setTouchPointerPadding(int width, int height)
	{
		touchPointerPaddingWidth = width;
		touchPointerPaddingHeight = height;
		requestLayout();
	}

	public int getTouchPointerPaddingWidth()
	{
		return touchPointerPaddingWidth;
	}

	public int getTouchPointerPaddingHeight()
	{
		return touchPointerPaddingHeight;
	}

	@Override public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		Log.v(TAG, width + "x" + height);
		this.setMeasuredDimension((int)(width * scaleFactor) + touchPointerPaddingWidth,
		                          (int)(height * scaleFactor) + touchPointerPaddingHeight);
	}

	@Override public void onDraw(@NonNull Canvas canvas)
	{
		super.onDraw(canvas);

		canvas.save();
		canvas.concat(scaleMatrix);
		canvas.drawColor(Color.BLACK);
		if (!railMode && surface != null)
		{
			surface.draw(canvas);
		}
		if (showScreenPointer && !penHoverInRange && penCaptureDevice < 0 &&
		    remoteCursorState != RemoteCursorState.HIDDEN && screenInputMode == ScreenInputMode.TOUCHPAD && screenInputController != null) {
			float px = screenInputController.getPointerX(), py = screenInputController.getPointerY();
			if (screenCursorBitmap != null) canvas.drawBitmap(screenCursorBitmap,px-cursorHotX,py-cursorHotY,null);
			else {
				Path arrow = new Path();arrow.moveTo(px,py);arrow.lineTo(px+6,py+21);arrow.lineTo(px+10,py+14);arrow.lineTo(px+17,py+12);arrow.close();
				screenPointerPaint.setColor(Color.WHITE);screenPointerPaint.setStyle(Paint.Style.FILL);canvas.drawPath(arrow,screenPointerPaint);
				screenPointerPaint.setColor(Color.BLACK);screenPointerPaint.setStyle(Paint.Style.STROKE);screenPointerPaint.setStrokeWidth(1.5f);canvas.drawPath(arrow,screenPointerPaint);
			}
		}
		canvas.restore();
		if (showPenHoverPointer && remoteCursorState == RemoteCursorState.CUSTOM && screenCursorBitmap != null)
		{
			// Only actual server-provided pixels; no local pen marker or invented brush.
			canvas.save();
			canvas.translate(penHoverX, penHoverY);
			canvas.scale(scaleFactor, scaleFactor);
			canvas.drawBitmap(screenCursorBitmap, -cursorHotX, -cursorHotY, null);
			canvas.restore();
		}
	}

	// perform mapping on the touch event's coordinates according to the current scaling
	private MotionEvent mapTouchEvent(MotionEvent event)
	{
		MotionEvent mappedEvent = MotionEvent.obtain(event);
		float[] coordinates = { mappedEvent.getX(), mappedEvent.getY() };
		invScaleMatrix.mapPoints(coordinates);
		mappedEvent.setLocation(coordinates[0], coordinates[1]);
		return mappedEvent;
	}

	// perform mapping on the double touch event's coordinates according to the current scaling
	private MotionEvent mapDoubleTouchEvent(MotionEvent event)
	{
		MotionEvent mappedEvent = MotionEvent.obtain(event);
		float[] coordinates = { (mappedEvent.getX(0) + mappedEvent.getX(1)) / 2,
			                    (mappedEvent.getY(0) + mappedEvent.getY(1)) / 2 };
		invScaleMatrix.mapPoints(coordinates);
		mappedEvent.setLocation(coordinates[0], coordinates[1]);
		return mappedEvent;
	}

	@Override public boolean onTouchEvent(MotionEvent event)
	{
		trackTouchpadWheelBoundary(event);
		if (handleBrowserNavigationBoundary(event)) return true;
		if (handleLegacyPointerContact(event)) return true;
		if (handleClassifiedTouchpadScroll(event))
			return true;
		if (handlePenInput(event))
			return true;
		trackTouchpadPinchBoundary(event);
		if (isTouchpadPinchEvent(event))
		{
			if (!screenInputActive || event.getButtonState() != 0)
			{
				resetTouchpadPinch();
				return true; // A pinch never becomes a drag or releases a held hardware button.
			}
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN)
				beginTouchpadPinch(event);
			else if (event.getDeviceId() == touchpadPinchDevice && event.getDownTime() == touchpadPinchDownTime &&
			         action == MotionEvent.ACTION_MOVE)
			{
				int remaining = MAX_CONTENT_ZOOM_STEPS_PER_EVENT;
				for (int i = 0; i < event.getHistorySize(); i++)
					remaining = applyTouchpadPinchSample(event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR, 0, i), event.getHistoricalEventTimeNanos(i), remaining);
				applyTouchpadPinchSample(event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR), event.getEventTimeNanos(), remaining);
			}
			else if (event.getDeviceId() == touchpadPinchDevice &&
			         (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL))
				resetTouchpadPinch();
			return true;
		}
		// A framework cancellation can lose source/tool metadata. Retire only the
		// named device; another pointer's screen stream or held mouse stays intact.
		if (event.getActionMasked() == MotionEvent.ACTION_CANCEL &&
		    event.getSource() == InputDevice.SOURCE_UNKNOWN)
		{
			cancelScreenInputDevice(event.getDeviceId());
			if (sessionViewListener != null)
				sessionViewListener.onSessionViewPointerCancel(event.getDeviceId());
			return true;
		}
		// Physical mouse events: bypass gesture detector entirely.
		// Buttons are handled in onGenericMotionEvent; hover moves in onHoverEvent.
		// Only ACTION_MOVE with a button held (drag) needs handling here.
		if (ScreenInputController.isExternalPointerEvent(event))
		{
			if (sessionViewListener != null)
				sessionViewListener.onSessionViewPointerDevice(event.getDeviceId());
			int action = event.getActionMasked();
			// Upstream 3.33.0: some ROMs deliver a physical wheel on the touch route.
			if (action == MotionEvent.ACTION_SCROLL)
				return onGenericMotionEvent(event);
			if (action == MotionEvent.ACTION_CANCEL)
			{
				if (sessionViewListener != null)
					sessionViewListener.onSessionViewPointerCancel(event.getDeviceId());
				return true;
			}
			if (action == MotionEvent.ACTION_MOVE && event.getButtonState() != 0)
			{
				MotionEvent mapped = mapTouchEvent(event);
				sessionViewListener.onSessionViewMouseMove((int)mapped.getX(), (int)mapped.getY());
				mapped.recycle();
				return true;
			}
			return true;
		}

		if (screenInputController != null && ScreenInputController.isScreenFingerEvent(event))
			return handleScreenFingerInput(event);

		boolean res = gestureDetector.onTouchEvent(event);
		res |= doubleGestureDetector.onTouchEvent(event);
		return res;
	}

	/** Retire only legacy logical contacts, without cancelling another device's mouse buttons. */
	public void cancelLegacyPointerInput()
	{
		while (legacyPointerContacts.size() != 0)
			cancelLegacyPointerDevice(legacyPointerContacts.keyAt(0));
	}

	private void cancelLegacyPointerDevice(int deviceId)
	{
		LegacyPointerContact contact = legacyPointerContacts.get(deviceId);
		legacyPointerContacts.remove(deviceId);
		if (contact != null && contact.held && sessionViewListener != null)
		{
			sessionViewListener.onSessionViewPointerDevice(deviceId);
			sessionViewListener.onSessionViewLeftTouch(contact.x, contact.y, false);
			sessionViewListener.onSessionViewEndTouch();
		}
	}

	// AOSP legacy POINTER mode reports logical TAP_DRAG with no physical button bit.
	// Its initial DOWN is indistinguishable from the two-finger PRESS hypothesis.
	// Defer left-down until a one-pointer NONE MOVE changes raw and session coordinates.
	// A pending-only UP is deliberately not converted to a click.
	private boolean handleLegacyPointerContact(MotionEvent event)
	{
		final int device = event.getDeviceId();
		final int action = event.getActionMasked();
		LegacyPointerContact contact = legacyPointerContacts.get(device);
		if (action == MotionEvent.ACTION_CANCEL)
		{
			cancelLegacyPointerDevice(device);
			return false;
		}
		final boolean eligible = sessionViewListener != null && screenInputActive &&
		    (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) &&
		    event.getPointerCount() == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
		    event.getClassification() == MotionEvent.CLASSIFICATION_NONE && event.getButtonState() == 0 &&
		    Float.isFinite(event.getX()) && Float.isFinite(event.getY());
		if (action == MotionEvent.ACTION_DOWN)
		{
			if (eligible) trackTouchpadPinchBoundary(event);
			if (contact != null && eligible && contact.downTime == event.getDownTime() &&
			    contact.pointerId == event.getPointerId(0)) return true;
			cancelLegacyPointerDevice(device);
			if (!eligible) return false;
			MotionEvent mapped = mapTouchEvent(event);
			contact = new LegacyPointerContact();
			contact.downTime = event.getDownTime(); contact.eventTime = event.getEventTime();
			contact.pointerId = event.getPointerId(0);
			contact.rawX = event.getX(); contact.rawY = event.getY();
			contact.downX = contact.x = (int)mapped.getX(); contact.downY = contact.y = (int)mapped.getY();
			mapped.recycle(); legacyPointerContacts.put(device, contact);
			return true;
		}
		if (contact == null || contact.downTime != event.getDownTime()) return false;
		if ((action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) &&
		    event.getActionButton() == MotionEvent.BUTTON_PRIMARY)
		{
			// An authoritative button stream takes over the same device's existing hold.
			legacyPointerContacts.remove(device);
			return action == MotionEvent.ACTION_BUTTON_PRESS && contact.held;
		}
		if (!eligible || contact.pointerId != event.getPointerId(0))
		{
			cancelLegacyPointerDevice(device);
			return false;
		}
		if (event.getEventTime() < contact.eventTime) return true;
		contact.eventTime = event.getEventTime();
		if (action == MotionEvent.ACTION_UP)
		{
			trackTouchpadPinchBoundary(event);
			cancelLegacyPointerDevice(device);
			return true;
		}
		if (action != MotionEvent.ACTION_MOVE) return false;
		if (event.getX() == contact.rawX && event.getY() == contact.rawY) return true;
		contact.rawX = event.getX(); contact.rawY = event.getY();
		MotionEvent mapped = mapTouchEvent(event);
		int x = (int)mapped.getX(), y = (int)mapped.getY(); mapped.recycle();
		if (x == contact.x && y == contact.y) return true;
		sessionViewListener.onSessionViewPointerDevice(device);
		if (!contact.held)
		{
			contact.held = true;
			sessionViewListener.onSessionViewBeginTouch();
			sessionViewListener.onSessionViewLeftTouch(contact.downX, contact.downY, true);
		}
		contact.x = x; contact.y = y;
		sessionViewListener.onSessionViewMouseMove(x, y);
		return true;
	}

	private static boolean isTouchpadPinchEvent(MotionEvent event)
	{
		if (Build.VERSION.SDK_INT < 34 || event.getClassification() != MotionEvent.CLASSIFICATION_PINCH)
			return false;
		return ScreenInputController.isExternalPointerEvent(event) ||
		    (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) && event.getPointerCount() > 0 &&
		     event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER);
	}

	private void resetTouchpadPinch()
	{
		touchpadPinchSteps = 0;
		touchpadPinchDevice = -1;
		touchpadPinchDownTime = 0;
		touchpadPinchSampleTime = -1;
	}

	private void beginTouchpadPinch(MotionEvent event)
	{
		if (touchpadPinchDevice == event.getDeviceId() && touchpadPinchDownTime == event.getDownTime())
			return;
		resetTouchpadPinch();
		touchpadPinchDevice = event.getDeviceId();
		touchpadPinchDownTime = event.getDownTime();
		touchpadPinchSampleTime = event.getEventTimeNanos();
	}

	private void trackTouchpadPinchBoundary(MotionEvent event)
	{
		if (Build.VERSION.SDK_INT < 34 || !screenInputActive || !ScreenInputController.isExternalPointerEvent(event))
			return;
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN)
			beginTouchpadPinch(event);
		else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) &&
		         event.getDeviceId() == touchpadPinchDevice && event.getDownTime() == touchpadPinchDownTime)
			resetTouchpadPinch();
	}

	private int applyTouchpadPinchSample(float factor, long time, int remaining)
	{
		if (time <= touchpadPinchSampleTime)
			return remaining; // Touch/generic duplicate callbacks and overlapping history are one sample.
		touchpadPinchSampleTime = time;
		return applyTouchpadPinchScale(factor, remaining);
	}

	private int applyTouchpadPinchScale(float factor, int remaining)
	{
		if (!Float.isFinite(factor) || factor <= 0)
			return remaining;
		touchpadPinchSteps += Math.log(factor) / CONTENT_ZOOM_STEP;
		touchpadPinchSteps = Math.max(-MAX_CONTENT_ZOOM_STEPS_PER_EVENT,
		                             Math.min(MAX_CONTENT_ZOOM_STEPS_PER_EVENT, touchpadPinchSteps));
		while (Math.abs(touchpadPinchSteps) >= 1 && remaining > 0)
		{
			boolean zoomIn = touchpadPinchSteps > 0;
			touchpadPinchSteps += zoomIn ? -1 : 1;
			remaining--;
			if (sessionViewListener != null)
				sessionViewListener.onSessionViewContentZoom(zoomIn);
		}
		if (remaining == 0)
			touchpadPinchSteps = 0; // Drop excess motion rather than replaying a delayed burst.
		return remaining;
	}

	public void cancelTouchpadWheelInput()
	{
		touchpadWheel.resetAll(); genericWheel.resetAll();
		cancelBrowserNavigationInput();
	}

	public void cancelBrowserNavigationInput()
	{
		for (int i = 0; i < browserNavigationContacts.size(); i++)
			browserNavigationContacts.valueAt(i).cancelled = true;
	}

	private static boolean isPenTool(MotionEvent event)
	{
		for (int i = 0; i < event.getPointerCount(); i++)
			if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS ||
			    event.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) return true;
		return false;
	}

	private static boolean isClassifiedTouchpadWheel(MotionEvent event)
	{
		// Preserve the OS classification contract, including vendor source/tool variations.
		return Build.VERSION.SDK_INT >= 34 && !isPenTool(event) &&
		    event.getClassification() == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE;
	}

	// AOSP fling-stop uses a pressureless fake DOWN/CANCEL, followed by a real
	// pressure-one contact which may share its complete input-frame timestamp.
	// Other pointer shapes provide no permission to reopen an equal-time stream.
	private static float classifiedTouchpadBoundaryPressure(MotionEvent event)
	{
		if (!isClassifiedTouchpadWheel(event) || !ScreenInputController.isExternalPointerEvent(event) ||
		    event.getPointerCount() != 1 || event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER ||
		    event.getButtonState() != 0 || event.getHistorySize() != 0 ||
		    event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE) != 0 ||
		    event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE) != 0 ||
		    event.getAxisValue(MotionEvent.AXIS_GESTURE_X_OFFSET) != 0 ||
		    event.getAxisValue(MotionEvent.AXIS_GESTURE_Y_OFFSET) != 0)
			return Float.NaN;
		float pressure = event.getPressure(0);
		return Float.isFinite(pressure) ? pressure : Float.NaN;
	}

	private void trackTouchpadWheelBoundary(MotionEvent event)
	{
		int action = event.getActionMasked();
		// Cancellation may omit source/tool metadata; only the matching owner can retire.
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
		{
			touchpadWheel.endIfOwned(event.getDeviceId(), event.getDownTime(), wheelEventTimeNanos(event),
			    action == MotionEvent.ACTION_CANCEL && classifiedTouchpadBoundaryPressure(event) == 0.0f);
			genericWheel.endIfOwned(event.getDeviceId(), event.getDownTime(), wheelEventTimeNanos(event));
			return;
		}
		if (!(isClassifiedTouchpadWheel(event) || ScreenInputController.isExternalPointerEvent(event)) || isPenTool(event)) return;
		if (action == MotionEvent.ACTION_DOWN)
			touchpadWheel.begin(event.getDeviceId(), event.getDownTime(), wheelEventTimeNanos(event),
			    classifiedTouchpadBoundaryPressure(event));
	}

	/** Both dispatch routes share one raw-distance ledger; normal mouse/finger/pen routes stay separate. */
	private boolean handleClassifiedTouchpadScroll(MotionEvent event)
	{
		if (!isClassifiedTouchpadWheel(event))
			return false;
		trackTouchpadPinchBoundary(event);
		if (sessionViewListener != null)
		{
			sessionViewListener.onSessionViewPointerDevice(event.getDeviceId());
			if (event.getActionMasked() == MotionEvent.ACTION_CANCEL)
				sessionViewListener.onSessionViewPointerCancel(event.getDeviceId());
		}
		if (!handleBrowserNavigation(event)) handleTouchpadScroll(event);
		return true;
	}

	// A logical NONE prefix remains available to legacy input until the OS resolves it.
	private boolean handleBrowserNavigationBoundary(MotionEvent event)
	{
		if (Build.VERSION.SDK_INT < 34) return false;
		int device = event.getDeviceId(), action = event.getActionMasked();
		BrowserNavigationContact contact = browserNavigationContacts.get(device);
		if (contact != null && (event.getButtonState() != 0 || action == MotionEvent.ACTION_BUTTON_PRESS))
			contact.cancelled = true;
		if (action == MotionEvent.ACTION_DOWN && event.getClassification() == MotionEvent.CLASSIFICATION_NONE &&
		    (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) &&
		    event.getPointerCount() == 1 && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
		    Float.isFinite(event.getX()) && Float.isFinite(event.getY()))
		{
			if (contact == null || event.getDownTime() > contact.downTime)
			{
				contact = new BrowserNavigationContact(); contact.downTime = event.getDownTime();
				contact.prefixX = event.getX(); contact.prefixY = event.getY();
				contact.cancelled = event.getButtonState() != 0 || sessionViewListener == null ||
				    !sessionViewListener.onSessionViewBrowserNavigationActive();
				browserNavigationContacts.put(device, contact);
			}
			return false; // Recording even an unauthorized prefix never consumes a legacy DOWN.
		}
		if (contact == null || contact.downTime != event.getDownTime()) return false;
		LegacyPointerContact legacy = legacyPointerContacts.get(device);
		if (legacy != null && legacy.held) contact.cancelled = true;
		if (action == MotionEvent.ACTION_MOVE && !isClassifiedTouchpadWheel(event) &&
		    (event.getX() != contact.prefixX || event.getY() != contact.prefixY)) contact.cancelled = true;
		if (action == MotionEvent.ACTION_CANCEL)
		{
			contact.cancelled = true; contact.finished = true;
			return false; // Preserve the existing device/button/pen cancellation dispatch.
		}
		if (action == MotionEvent.ACTION_UP)
		{
			if (!contact.classified) { contact.finished = true; return false; }
			if (!isClassifiedTouchpadWheel(event)) return handleBrowserNavigation(event);
		}
		return false;
	}

	// Navigation is armed only at an authorized DOWN, never at a later modifier press.
	private boolean handleBrowserNavigation(MotionEvent event)
	{
		int device = event.getDeviceId(), action = event.getActionMasked();
		BrowserNavigationContact contact = browserNavigationContacts.get(device);
		if (action == MotionEvent.ACTION_DOWN)
		{
			if (contact != null && event.getDownTime() <= contact.downTime)
			{
				if (event.getDownTime() == contact.downTime && !contact.classified)
				{
					if (contact.finished || contact.cancelled || sessionViewListener == null ||
					    !sessionViewListener.onSessionViewBrowserNavigationActive()) return false;
					contact.classified = true;
				}
				return true;
			}
			if (event.getButtonState() != 0 || sessionViewListener == null ||
			    !sessionViewListener.onSessionViewBrowserNavigationActive()) return false;
			contact = new BrowserNavigationContact(); contact.downTime = event.getDownTime(); contact.classified = true;
			browserNavigationContacts.put(device, contact); return true;
		}
		if (contact == null || contact.downTime != event.getDownTime()) return false;
		if (contact.finished) return true;
		if (!contact.classified)
		{
			if (contact.cancelled) return false;
			contact.classified = true;
		}
		if (event.getButtonState() != 0 || sessionViewListener == null ||
		    !sessionViewListener.onSessionViewBrowserNavigationActive()) contact.cancelled = true;
		if (action == MotionEvent.ACTION_CANCEL) { contact.cancelled = true; contact.finished = true; return true; }
		if (action == MotionEvent.ACTION_UP)
		{
			contact.finished = true; // Retire before the callback, including reentrant or duplicate UP.
			if (!contact.cancelled && Math.abs(contact.x) >= 96.0 * getResources().getDisplayMetrics().density &&
			    Math.abs(contact.x) >= 2.0 * Math.abs(contact.y))
				sessionViewListener.onSessionViewBrowserNavigate(contact.x > 0);
			return true;
		}
		if (action != MotionEvent.ACTION_MOVE || contact.cancelled) return true;
		int w = touchpadScrollViewportSize(true), h = touchpadScrollViewportSize(false);
		for (int i = 0; i < event.getHistorySize(); i++)
			sampleBrowserNavigation(contact, event, i, event.getHistoricalEventTimeNanos(i), w, h);
		sampleBrowserNavigation(contact, event, -1, event.getEventTimeNanos(), w, h);
		return true;
	}

	private void sampleBrowserNavigation(BrowserNavigationContact contact, MotionEvent event,
	                                     int history, long time, int viewportWidth, int viewportHeight)
	{
		if (contact.cancelled || time < 0 || time < contact.time) return;
		double dx = touchpadAxis(event, MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE, history);
		double dy = touchpadAxis(event, MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE, history);
		// One whole pair per sample, using the same local-viewport legacy policy as wheel input.
		if (dx == 0 && dy == 0)
		{
			double ox = touchpadAxis(event, MotionEvent.AXIS_GESTURE_X_OFFSET, history);
			double oy = touchpadAxis(event, MotionEvent.AXIS_GESTURE_Y_OFFSET, history);
			if (ox != 0 || oy != 0)
			{
				dx = ox * viewportWidth; dy = oy * viewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) dx = dy = Double.NaN;
			}
		}
		if (time > contact.time) { contact.time = time; contact.atTime.clear(); }
		if (!contact.atTime.add(Double.toHexString(dx) + ":" + Double.toHexString(dy))) return;
		if (!Double.isFinite(dx) || !Double.isFinite(dy)) { contact.cancelled = true; return; }
		contact.x += dx; contact.y += dy;
		if (!Double.isFinite(contact.x) || !Double.isFinite(contact.y)) contact.cancelled = true;
	}

	// DISTANCE is display pixels; legacy OFFSET is a fraction of the touchpad span.
	// Map a full legacy pad span to the local viewport, never the zoomed framebuffer.
	private int touchpadScrollViewportSize(boolean horizontal)
	{
		View reference = getRootView();
		for (android.view.ViewParent parent = getParent(); parent instanceof View; parent = parent.getParent())
			if (parent instanceof ScrollView2D) { reference = (View)parent; break; }
		return Math.max(0, horizontal
		    ? reference.getWidth() - reference.getPaddingLeft() - reference.getPaddingRight()
		    : reference.getHeight() - reference.getPaddingTop() - reference.getPaddingBottom());
	}

	private double touchpadAxis(MotionEvent event, int axis, int history)
	{
		return history < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, 0, history);
	}

	private int sampleTouchpadScroll(MotionEvent event, int history, long time, int remaining,
	                                  TouchpadWheelAccumulator.Sink sink)
	{
		double x = touchpadAxis(event, MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE, history);
		double y = touchpadAxis(event, MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE, history);
		// Prefer the complete DISTANCE pair. Never add both representations of a sample.
		if (x == 0 && y == 0)
		{
			double offsetX = touchpadAxis(event, MotionEvent.AXIS_GESTURE_X_OFFSET, history);
			double offsetY = touchpadAxis(event, MotionEvent.AXIS_GESTURE_Y_OFFSET, history);
			if (offsetX != 0 || offsetY != 0)
			{
				x = offsetX * touchpadScrollViewportWidth;
				y = offsetY * touchpadScrollViewportHeight;
				if (touchpadScrollViewportWidth <= 0 || touchpadScrollViewportHeight <= 0)
					x = y = Double.NaN; // No measured viewport: reject rather than guess a gain.
			}
		}
		return touchpadWheel.sample(event.getDeviceId(), event.getDownTime(), time, x, y,
		    notchPxX, notchPxY, remaining, sink);
	}

	private void handleTouchpadScroll(MotionEvent event)
	{
		if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return;
		int w = touchpadScrollViewportSize(true), h = touchpadScrollViewportSize(false);
		if (w != touchpadScrollViewportWidth || h != touchpadScrollViewportHeight)
			touchpadWheel.resetAll(); // Retire old timestamps before an OFFSET gain changes.
		touchpadScrollViewportWidth = w; touchpadScrollViewportHeight = h;
		WheelBatch batch = new WheelBatch();
		int remaining = TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT;
		for (int i = 0; i < event.getHistorySize(); i++)
			remaining = sampleTouchpadScroll(event, i, event.getHistoricalEventTimeNanos(i), remaining, batch);
		sampleTouchpadScroll(event, -1, event.getEventTimeNanos(), remaining, batch);
		sendTouchpadWheelBatch(batch);
	}

	/** Allocated and synchronously dispatched inside one wheel callback, never retained by this View. */
	private static final class WheelBatch implements TouchpadWheelAccumulator.Sink
	{
		final int[] horizontal = new int[TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT];
		final int[] vertical = new int[TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT];
		int count;
		@Override public void wheel(int h, int v)
		{
			if (count >= horizontal.length) throw new IllegalStateException("Wheel callback chunk budget exceeded");
			horizontal[count] = h; vertical[count] = v; count++;
		}
	}

	private void sendTouchpadWheelBatch(WheelBatch batch)
	{
		if (sessionViewListener != null && batch.count != 0)
			sessionViewListener.onSessionViewWheelBatch(batch.horizontal, batch.vertical, batch.count);
	}

	// Handle all physical mouse buttons here; finger taps come via onSingleTapUp.
	@Override public boolean onGenericMotionEvent(MotionEvent event)
	{
		trackTouchpadWheelBoundary(event);
		if (handleBrowserNavigationBoundary(event)) return true;
		if (handleLegacyPointerContact(event)) return true;
		if (handleClassifiedTouchpadScroll(event))
			return true;
		if (handlePenInput(event))
			return true;
		trackTouchpadPinchBoundary(event);
		if (isTouchpadPinchEvent(event))
			return onTouchEvent(event);
		final boolean isPointer = event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) || ScreenInputController.isExternalPointerEvent(event);
		if (!isPointer)
			return false;

		final boolean isMouse = ScreenInputController.isExternalPointerEvent(event);
		if (isMouse && sessionViewListener != null)
			sessionViewListener.onSessionViewPointerDevice(event.getDeviceId());
		int action = event.getActionMasked();
		// SOURCE_TOUCHPAD uses the POSITION class, so View may dispatch its hover here.
		if (isMouse && action == MotionEvent.ACTION_HOVER_MOVE) {
			MotionEvent mapped = mapTouchEvent(event);
			sessionViewListener.onSessionViewMouseMove((int)mapped.getX(), (int)mapped.getY());
			mapped.recycle();
			return true;
		}

		if (isMouse && (action == MotionEvent.ACTION_BUTTON_PRESS ||
		                action == MotionEvent.ACTION_BUTTON_RELEASE))
		{
			boolean down = action == MotionEvent.ACTION_BUTTON_PRESS;
			MotionEvent mapped = mapTouchEvent(event);
			int x = (int)mapped.getX();
			int y = (int)mapped.getY();
			mapped.recycle();

			switch (event.getActionButton())
			{
				case MotionEvent.BUTTON_PRIMARY:
					if (down)
						sessionViewListener.onSessionViewBeginTouch();
					sessionViewListener.onSessionViewLeftTouch(x, y, down);
					if (!down)
						sessionViewListener.onSessionViewEndTouch();
					return true;
				case MotionEvent.BUTTON_SECONDARY:
					if (down)
						sessionViewListener.onSessionViewBeginTouch();
					sessionViewListener.onSessionViewRightTouch(x, y, down);
					return true;
				case MotionEvent.BUTTON_TERTIARY:
					sessionViewListener.onSessionViewMiddleTouch(x, y, down);
					return true;
				case MotionEvent.BUTTON_BACK:
					sessionViewListener.onSessionViewXButton(x, y, 4, down);
					return true;
				case MotionEvent.BUTTON_FORWARD:
					sessionViewListener.onSessionViewXButton(x, y, 5, down);
					return true;
				default:
					return true; // consume unknown buttons silently
			}
		}

		if (handleGenericWheelEvent(event)) return true;

		return false;
	}

	/** Shared by View dispatch and Activity/InputManager fallback, so one OS sample is sent once. */
	public boolean handleGenericWheelEvent(MotionEvent event)
	{
		if (event.getActionMasked() != MotionEvent.ACTION_SCROLL || isPenTool(event) ||
		    !ScreenInputController.isExternalPointerEvent(event)) return false;
		trackTouchpadWheelBoundary(event);
		if (handleClassifiedTouchpadScroll(event)) return true;
		if (event.getClassification() == MotionEvent.CLASSIFICATION_PINCH) return false;
		if (sessionViewListener != null)
			sessionViewListener.onSessionViewPointerDevice(event.getDeviceId());
		WheelBatch batch = new WheelBatch();
		int remaining = TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT;
		for (int i = 0; i < event.getHistorySize(); i++)
			remaining = genericWheel.sample(event.getDeviceId(), event.getDownTime(), wheelHistoricalTimeNanos(event, i),
			    event.getHistoricalAxisValue(MotionEvent.AXIS_HSCROLL, 0, i),
			    -event.getHistoricalAxisValue(MotionEvent.AXIS_VSCROLL, 0, i),
			    1, 1, remaining, batch);
		genericWheel.sample(event.getDeviceId(), event.getDownTime(), wheelEventTimeNanos(event),
		    event.getAxisValue(MotionEvent.AXIS_HSCROLL), -event.getAxisValue(MotionEvent.AXIS_VSCROLL),
		    1, 1, remaining, batch);
		sendTouchpadWheelBatch(batch);
		return true;
	}

	// API 29-33 only expose milliseconds; conversion changes units, not precision.
	private static long wheelEventTimeNanos(MotionEvent event)
	{
		return Build.VERSION.SDK_INT >= 34 ? event.getEventTimeNanos() : event.getEventTime() * 1000000L;
	}
	private static long wheelHistoricalTimeNanos(MotionEvent event, int history)
	{
		return Build.VERSION.SDK_INT >= 34 ? event.getHistoricalEventTimeNanos(history) :
		    event.getHistoricalEventTime(history) * 1000000L;
	}

	public interface SessionViewListener
	{
		/** First refusal for physical keyboard streams, before local IME composition. */
		default boolean onSessionViewPreImeKeyEvent(android.view.KeyEvent event) { return false; }

		/** Signed wheel units; each axis uses its own RDP packet. */
		default boolean onSessionViewBrowserNavigationActive() { return false; }
		default void onSessionViewBrowserNavigate(boolean forward) {}
		default void onSessionViewWheelDelta(int horizontal, int vertical) {}
		/** Synchronous, caller-owned arrays; default listeners retain the legacy pair sequence. */
		default void onSessionViewWheelBatch(int[] horizontal, int[] vertical, int count)
		{
			if (horizontal == null || vertical == null || count < 0 ||
			    count > TouchpadWheelAccumulator.MAX_CHUNKS_PER_EVENT ||
			    count > horizontal.length || count > vertical.length)
				throw new IllegalArgumentException("Invalid wheel callback batch");
			for (int i = 0; i < count; i++) onSessionViewWheelDelta(horizontal[i], vertical[i]);
		}

		/** Physical touchpad content zoom; never changes the local desktop scale. */
		default void onSessionViewContentZoom(boolean zoomIn) {}
		/** Identifies external pointer streams without changing legacy direct callbacks. */
		default void onSessionViewPointerDevice(int deviceId) {}
		default void onSessionViewPointerCancel(int deviceId) {}
		/** Logical button 4 is back (XBUTTON1), 5 is forward (XBUTTON2). */
		default void onSessionViewXButton(int x, int y, int button, boolean down) {}

		void onSessionViewBeginTouch();

		void onSessionViewEndTouch();

		void onSessionViewLeftTouch(int x, int y, boolean down);

		void onSessionViewMiddleTouch(int x, int y, boolean down);

		void onSessionViewRightTouch(int x, int y, boolean down);

		void onSessionViewMove(int x, int y);

		void onSessionViewMouseMove(int x, int y);

		void onSessionViewScroll(boolean down);

		void onSessionViewHScroll(boolean right);
	}

	public void setRemoteCursor(int[] pixels, int width, int height, int hotX, int hotY)
	{
		if (pixels == null || width == 0 || height == 0)
		{
			remoteCursorState = RemoteCursorState.HIDDEN;
			cursorPixels = null;
			screenCursorBitmap = null;
			setPointerIcon(PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_NULL));
			invalidate();
			return;
		}
		remoteCursorState = RemoteCursorState.CUSTOM;
		cursorPixels = pixels;
		screenCursorBitmap = Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
		cursorWidth = width;
		cursorHeight = height;
		cursorHotX = hotX;
		cursorHotY = hotY;
		applyScaledCursor();
	}

	private void applyScaledCursor()
	{
		int scaledWidth = Math.max(1, (int)(cursorWidth * scaleFactor));
		int scaledHeight = Math.max(1, (int)(cursorHeight * scaleFactor));
		Bitmap bm =
		    Bitmap.createBitmap(cursorPixels, cursorWidth, cursorHeight, Bitmap.Config.ARGB_8888);
		Bitmap scaled = Bitmap.createScaledBitmap(bm, scaledWidth, scaledHeight, true);
		remoteCursorIcon = PointerIcon.create(scaled, cursorHotX * scaleFactor, cursorHotY * scaleFactor);
		setPointerIcon(remoteCursorIcon);
		invalidate();
	}

	public void setDefaultCursor()
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			new Handler(Looper.getMainLooper()).post(this::setDefaultCursor);
			return;
		}
		remoteCursorState = RemoteCursorState.DEFAULT;
		cursorPixels = null;
		screenCursorBitmap = null;
		remoteCursorIcon = PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_ARROW);
		setPointerIcon(remoteCursorIcon);
		invalidate();
	}

	private class SessionGestureListener extends GestureDetector.SimpleOnGestureListener
	{
		boolean longPressInProgress = false;

		public boolean onDown(MotionEvent e)
		{
			return true;
		}

		public boolean onUp(MotionEvent e)
		{
			sessionViewListener.onSessionViewEndTouch();
			return true;
		}

		public void onLongPress(MotionEvent e)
		{
			MotionEvent mappedEvent = mapTouchEvent(e);
			sessionViewListener.onSessionViewBeginTouch();
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), true);
			longPressInProgress = true;
		}

		public void onLongPressUp(MotionEvent e)
		{
			MotionEvent mappedEvent = mapTouchEvent(e);
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), false);
			longPressInProgress = false;
			sessionViewListener.onSessionViewEndTouch();
		}

		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
		{
			if (longPressInProgress)
			{
				MotionEvent mappedEvent = mapTouchEvent(e2);
				sessionViewListener.onSessionViewMove((int)mappedEvent.getX(),
				                                      (int)mappedEvent.getY());
				return true;
			}

			return false;
		}

		public boolean onDoubleTap(MotionEvent e)
		{
			// send 2nd click for double click
			MotionEvent mappedEvent = mapTouchEvent(e);
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), true);
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), false);
			return true;
		}

		public boolean onSingleTapUp(MotionEvent e)
		{
			// Physical mouse buttons are handled via ACTION_BUTTON_PRESS in onGenericMotionEvent.
			// If buttonState is non-zero this event came from a physical mouse button
			if (e.getButtonState() != 0)
				return false;

			// Finger touch -> left click
			MotionEvent mappedEvent = mapTouchEvent(e);
			sessionViewListener.onSessionViewBeginTouch();
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), true);
			sessionViewListener.onSessionViewLeftTouch((int)mappedEvent.getX(),
			                                           (int)mappedEvent.getY(), false);
			sessionViewListener.onSessionViewEndTouch();
			return true;
		}
	}

	private class SessionDoubleGestureListener
	    implements DoubleGestureDetector.OnDoubleGestureListener
	{
		private MotionEvent prevEvent = null;

		public boolean onDoubleTouchDown(MotionEvent e)
		{
			sessionViewListener.onSessionViewBeginTouch();
			prevEvent = MotionEvent.obtain(e);
			return true;
		}

		public boolean onDoubleTouchUp(MotionEvent e)
		{
			if (prevEvent != null)
			{
				prevEvent.recycle();
				prevEvent = null;
			}
			sessionViewListener.onSessionViewEndTouch();
			return true;
		}

		public boolean onDoubleTouchScroll(MotionEvent e1, MotionEvent e2)
		{
			// calc if user scrolled up or down (or if any scrolling happened at all)
			float deltaY = e2.getY() - prevEvent.getY();
			if (deltaY > TOUCH_SCROLL_DELTA)
			{
				sessionViewListener.onSessionViewScroll(true);
				prevEvent.recycle();
				prevEvent = MotionEvent.obtain(e2);
			}
			else if (deltaY < -TOUCH_SCROLL_DELTA)
			{
				sessionViewListener.onSessionViewScroll(false);
				prevEvent.recycle();
				prevEvent = MotionEvent.obtain(e2);
			}
			return true;
		}

		public boolean onDoubleTouchSingleTap(MotionEvent e)
		{
			// send single click
			MotionEvent mappedEvent = mapDoubleTouchEvent(e);
			sessionViewListener.onSessionViewRightTouch((int)mappedEvent.getX(),
			                                            (int)mappedEvent.getY(), true);
			sessionViewListener.onSessionViewRightTouch((int)mappedEvent.getX(),
			                                            (int)mappedEvent.getY(), false);
			return true;
		}
	}

	@Override public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event)
	{
		if (sessionViewListener != null && sessionViewListener.onSessionViewPreImeKeyEvent(event))
			return true;
		return super.onKeyPreIme(keyCode, event);
	}

	@Override public InputConnection onCreateInputConnection(EditorInfo outAttrs)
	{
		outAttrs.actionLabel = null;
		outAttrs.inputType = InputType.TYPE_NULL;
		outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI |
		                      EditorInfo.IME_FLAG_NO_FULLSCREEN;
		return new BaseInputConnection(this, false);
	}
}
