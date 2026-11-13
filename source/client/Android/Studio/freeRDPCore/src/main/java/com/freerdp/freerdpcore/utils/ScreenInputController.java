/*
 * Screen finger input controller. MPL-2.0; see LICENSE.
 * Constructed-event JVM tests do not establish physical-device compatibility.
 */
package com.freerdp.freerdpcore.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.freerdp.freerdpcore.presentation.ScreenInputMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns only fingers on the display. External mouse/touchpad/stylus stay with the caller. */
public final class ScreenInputController {
 public static final int DOWN=0, UPDATE=1, UP=2, CANCEL=3;
 public static final int BUTTON_LEFT=1, BUTTON_RIGHT=2;
 public static final class TouchFrame {
  public final int[] ids,x,y,actions;
  public TouchFrame(int[] ids,int[] x,int[] y,int[] actions) {
   if(ids.length!=x.length || ids.length!=y.length || ids.length!=actions.length)
    throw new IllegalArgumentException("Contact arrays must have equal lengths");
   this.ids=ids.clone();this.x=x.clone();this.y=y.clone();this.actions=actions.clone();
  }
 }
 public interface Sink {
  boolean isDirectTouchAvailable(); boolean onTouchFrame(TouchFrame frame);
  void onMouseMove(int x,int y); void onMouseButton(int button,boolean down,int x,int y);
  /** Whether the cursor is free to change speed, including after release. */
  default boolean canCoastPointer() { return true; }
  /** positive means down for vertical and right for horizontal, before user scroll inversion. */
  void onScroll(boolean horizontal,boolean positive);
  /** Relative local-view zoom factor, focus in unscaled SessionView coordinates. */
  void onLocalZoom(float factor,float focusX,float focusY);
 }
 private static final long TWO_FINGER_TAP_MAX_DURATION_MS = 500;
 private static final double COAST_DECAY_SECONDS = 0.22;
 private static final double COAST_DRAG_OFFSET_DP_PER_SECOND = 105;
 private static final float COAST_RELEASE_GAIN = 0.77f;
 private static final double COAST_MIN_REMAINING_SCREEN_PIXELS = 0.5;
 private static final int RELEASE_MOTION_MAX_AGE_MS = 70;
 private static final float PRECISION_GAIN = 0.84f;
 private static final float PRECISION_SPEED_DP_PER_SECOND = 500f;
 private static final float FULL_GAIN_SPEED_DP_PER_SECOND = 1100f;
 private static final float MAX_COAST_DP_PER_SECOND = 2600f;

 private static final class PendingTap {
  final long upTime;
  final int button, deviceId, cursorX, cursorY;
  final float touchX, touchY;

  PendingTap(int button, long upTime, int deviceId, int cursorX, int cursorY, float touchX, float touchY) {
   this.button = button;
   this.upTime = upTime;
   this.deviceId = deviceId;
   this.cursorX = cursorX;
   this.cursorY = cursorY;
   this.touchX = touchX;
   this.touchY = touchY;
  }
 }

 private final Sink sink;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private final Runnable sendPendingClick = this::flushPendingClick;
 private final Choreographer.FrameCallback coastFrame = ignored -> advanceCoast();
 private final float density;
 private long swipeSampleTime, lastSwipeMotion, lastCoastFrame;
 private float swipeSampleX, swipeSampleY, swipeVelocityX, swipeVelocityY;
 private int coastDevice;
 private boolean trackingSwipe, coasting;
 private float coastXVelocity, coastYVelocity;
 private PendingTap pendingTap;
 private final float slop,wheelPixels,doubleTapSlop;
 private final long doubleTapMillis;
 private float scale=1, pointerX, pointerY;
 private int width=1,height=1,device=-1;
 private ScreenInputMode mode=ScreenInputMode.TOUCHPAD;
  private boolean tapDragEnabled = true;
 private boolean active=true,stream,ignoreRemainder;
 private float startX,startY,lastX,lastY;
 private long downTime;
 private boolean moved,multiTail;
 private int maxPointers;
 private float wheelX,wheelY,startSpan,lastSpan;
 private int multiKind;
 private int heldButton;
 private final Map<Integer,int[]> contacts=new LinkedHashMap<>();
 public ScreenInputController(Sink sink,float slop,float wheelPixels,long doubleTapMillis) {
  this(sink, slop, wheelPixels, doubleTapMillis, Math.max(1, slop) * 2);
 }

 public ScreenInputController(Sink sink, float slop, float wheelPixels, long doubleTapMillis,
                              float doubleTapSlop) {
  this(sink, slop, wheelPixels, doubleTapMillis, doubleTapSlop, 1f);
 }

 public ScreenInputController(Sink sink, float slop, float wheelPixels, long doubleTapMillis,
                              float doubleTapSlop, float density) {
  if (sink == null) throw new IllegalArgumentException("sink");
  this.sink = sink;
  this.slop = Math.max(1, slop);
  this.wheelPixels = Math.max(1, wheelPixels);
  this.doubleTapMillis = Math.max(1, doubleTapMillis);
  this.doubleTapSlop = Math.max(this.slop, doubleTapSlop);
  this.density = density;
 }
 public ScreenInputMode getMode(){return mode;}
 public void setMode(ScreenInputMode mode) {
  if(mode==null)throw new IllegalArgumentException("mode");
  if(this.mode!=mode){cancel();this.mode=mode;}
 }
  public void setTapDragEnabled(boolean enabled) {
   if (tapDragEnabled == enabled) return;
   tapDragEnabled = enabled;
   if (mode == ScreenInputMode.TOUCHPAD) cancel();
  }
 public void setActive(boolean active){if(!active)cancel();this.active=active;}
 public void setGeometry(float scale,int width,int height) {
  if(!Float.isFinite(scale)||scale<=0||width<=0||height<=0)throw new IllegalArgumentException("geometry");
  if(this.scale!=scale||this.width!=width||this.height!=height)cancel();
  boolean first=this.width==1&&this.height==1;
  this.scale=scale;this.width=width;this.height=height;
  if(first){pointerX=width/2f;pointerY=height/2f;}
  pointerX=clamp(pointerX,width);pointerY=clamp(pointerY,height);
 }
 public void setLocalZoom(float scale){if(Float.isFinite(scale)&&scale>0)this.scale=scale;}
 public void setPointerPosition(int x,int y){
  stopPointerCoast();
  clearSwipeVelocity();
  flushPendingClick();
  pointerX=clamp(x,width);pointerY=clamp(y,height);
 }
 public int getPointerX(){return Math.round(pointerX);}
 public int getPointerY(){return Math.round(pointerY);}
 private static int clamp(float x,int limit){return Math.max(0,Math.min(limit-1,Math.round(x)));}
 public static boolean isExternalPointerEvent(MotionEvent e){
  if(e.isFromSource(InputDevice.SOURCE_MOUSE)||e.isFromSource(InputDevice.SOURCE_TOUCHPAD))return true;
  for(int i=0;i<e.getPointerCount();i++)if(e.getToolType(i)==MotionEvent.TOOL_TYPE_MOUSE)return true;
  return false;
 }
 public static boolean isScreenFingerEvent(MotionEvent e){
  if(!e.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)||isExternalPointerEvent(e)||e.getClassification()==MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE)return false;
  for(int i=0;i<e.getPointerCount();i++) {
   int tool=e.getToolType(i);
   // Framework-generated cancellation can omit finger metadata; never admit mouse/stylus.
   if(tool!=MotionEvent.TOOL_TYPE_FINGER && !(e.getActionMasked()==MotionEvent.ACTION_CANCEL && tool==MotionEvent.TOOL_TYPE_UNKNOWN))return false;
  }
  return true;
 }
 public void cancelDevice(int id){
  if (coasting && coastDevice == id) stopPointerCoast();
  if ((stream && device == id) || (pendingTap != null && pendingTap.deviceId == id)) cancel();
 }
 public void cancel(){
  stopPointerCoast();
  clearSwipeVelocity();
  clearPendingClick();
  if(!contacts.isEmpty()){
   int n=contacts.size(),i=0;int[] ids=new int[n],x=new int[n],y=new int[n],actions=new int[n];
   for(Map.Entry<Integer,int[]> c:contacts.entrySet()){ids[i]=c.getKey();x[i]=c.getValue()[0];y[i]=c.getValue()[1];actions[i++]=CANCEL;}
   sink.onTouchFrame(new TouchFrame(ids,x,y,actions));contacts.clear();
  }
  releaseButton();
  if(stream)ignoreRemainder=true;
  stream=false;device=-1;
 }
 public boolean onTouchEvent(MotionEvent event){
  if(!isScreenFingerEvent(event))return false;
  int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_CANCEL){cancel();return true;}
  if(!active)return true;
  if(action==MotionEvent.ACTION_DOWN){stopPointerCoast();if(stream)cancel();ignoreRemainder=false;stream=true;device=event.getDeviceId();}
  if(!stream||ignoreRemainder)return true;
  if(event.getDeviceId()!=device){cancel();return true;}
  if(mode==ScreenInputMode.DIRECT_TOUCH)direct(event);
  else touchpad(event);
  if(action==MotionEvent.ACTION_UP){stream=false;device=-1;}
  return true;
 }
 private void direct(MotionEvent event){
  if(!sink.isDirectTouchAvailable()){cancel();ignoreRemainder=true;return;}
  int action=event.getActionMasked(),index=event.getActionIndex();
  int n=event.getPointerCount();int[] ids=new int[n],x=new int[n],y=new int[n],actions=new int[n];
  for(int i=0;i<n;i++){
   ids[i]=event.getPointerId(i);x[i]=clamp(event.getX(i)/scale,width);y[i]=clamp(event.getY(i)/scale,height);
   actions[i]=((action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)&&i==index)?DOWN:
     ((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&i==index)?UP:UPDATE;
   contacts.put(ids[i],new int[]{x[i],y[i]});
  }
  if(!sink.onTouchFrame(new TouchFrame(ids,x,y,actions))){cancel();ignoreRemainder=true;return;}
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)contacts.remove(event.getPointerId(index));
 }
 private void touchpad(MotionEvent event) {
  int action = event.getActionMasked();
  if (action == MotionEvent.ACTION_DOWN) {
   startX = lastX = event.getX();
   startY = lastY = event.getY();
   downTime = event.getEventTime();
   moved = multiTail = false;
   maxPointers = 1;
   wheelX = wheelY = 0;
   trackingSwipe = true;
   swipeSampleX = startX;
   swipeSampleY = startY;
   swipeSampleTime = lastSwipeMotion = event.getEventTime();
   if (hasPendingTap(BUTTON_LEFT)) matchPendingTap(event.getEventTime(), startX, startY);
  } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
   clearSwipeVelocity();
   if (event.getPointerCount() != 2) {
    flushPendingClick();
    moved = multiTail = true;
    releaseButton();
    return;
   }
   releaseButton();
   maxPointers = 2;
   startX = lastX = centerX(event);
   startY = lastY = centerY(event);
   startSpan = lastSpan = span(event);
   multiKind = 0;
   if (!moved && hasPendingTap(BUTTON_RIGHT)) {
    matchPendingTap(event.getEventTime(), startX, startY);
   } else {
    flushPendingClick();
   }
  } else if (action == MotionEvent.ACTION_POINTER_UP) {
   multiTail = true;
   releaseButton();
  } else if (action == MotionEvent.ACTION_MOVE && !multiTail) {
   if (maxPointers == 1) {
    trackSwipeMotion(event);
    moveSingleFinger(event);
   }
   else if (event.getPointerCount() == 2) moveTwoFingers(event);
  } else if (action == MotionEvent.ACTION_UP) {
   if (maxPointers == 1) {
    moveSingleFinger(event);
    startCoast(event);
   }
   clearSwipeVelocity();
   finishTouchpadGesture(event.getEventTime());
  }
 }

 private boolean hasPendingTap(int button) {
  return pendingTap != null && pendingTap.button == button;
 }

 private void matchPendingTap(long time, float x, float y) {
  long gap = time - pendingTap.upTime;
  float tapRadius = pendingTap.button == BUTTON_LEFT ? doubleTapSlop : slop * 2;
  if (pendingTap.deviceId == device && gap >= 0 && gap < doubleTapMillis &&
      Math.hypot(x - pendingTap.touchX, y - pendingTap.touchY) <= tapRadius) {
   handler.removeCallbacks(sendPendingClick);
  } else {
   flushPendingClick();
  }
 }

 private void moveSingleFinger(MotionEvent event) {
  float x = event.getX(), y = event.getY();
  if (Math.hypot(x - startX, y - startY) > slop) moved = true;
  if (!moved) return;
  if (hasPendingTap(BUTTON_LEFT)) beginDrag(BUTTON_LEFT);
  else flushPendingClick();
  float gain = 1;
  if (trackingSwipe && heldButton == 0 && sink.canCoastPointer()) {
   double speed = event.getEventTime() - lastSwipeMotion <= RELEASE_MOTION_MAX_AGE_MS
       ? Math.hypot(swipeVelocityX, swipeVelocityY) : 0;
   gain = PRECISION_GAIN + (1 - PRECISION_GAIN) * swipeSpeedBlend(speed);
  }
  moveTouchpadPointer(x, y, gain);
 }

 private void moveTwoFingers(MotionEvent event) {
  float x = centerX(event), y = centerY(event), currentSpan = span(event);
  double travel = Math.hypot(x - startX, y - startY);
  if (heldButton == BUTTON_RIGHT) {
   moveTouchpadPointer(x, y);
   return;
  }

  if (multiKind == 0) {
   float spanChange = Math.abs(currentSpan - startSpan);
   if (spanChange > slop * 2 && spanChange > travel * 1.2f) {
    flushPendingClick();
    multiKind = 2;
   } else if (travel > slop) {
    if (hasPendingTap(BUTTON_RIGHT)) {
     moved = true;
     beginDrag(BUTTON_RIGHT);
     moveTouchpadPointer(x, y);
     return;
    }
    multiKind = 1;
   }
  }
  if (multiKind == 2) {
   moved = true;
   if (lastSpan > 0 && currentSpan > 0) sink.onLocalZoom(currentSpan / lastSpan, x, y);
   lastSpan = currentSpan;
  } else if (multiKind == 1) {
   moved = true;
   wheelX -= x - lastX;
   wheelY -= y - lastY;
   emitWheel();
   lastX = x;
   lastY = y;
  }
 }

 private void beginDrag(int button) {
  clearPendingClick();
  heldButton = button;
  sink.onMouseButton(button, true, getPointerX(), getPointerY());
 }

 private void moveTouchpadPointer(float x, float y) {
  moveTouchpadPointer(x, y, 1);
 }

 private void moveTouchpadPointer(float x, float y, float gain) {
  if (x != lastX || y != lastY) movePointer((x - lastX) * gain, (y - lastY) * gain);
  lastX = x;
  lastY = y;
 }

 private void trackSwipeMotion(MotionEvent event) {
  if (!trackingSwipe) return;
  for (int i = 0; i < event.getHistorySize(); i++)
   trackSwipeSample(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i));
  trackSwipeSample(event.getX(), event.getY(), event.getEventTime());
 }

 private void trackSwipeSample(float x, float y, long time) {
  long elapsed = time - swipeSampleTime;
  if (elapsed <= 0) return;
  float dx = x - swipeSampleX, dy = y - swipeSampleY;
  if (dx != 0 || dy != 0) lastSwipeMotion = time;
  // Short low-pass filter to avoid reacting to jitter in the last sample.
  float weight = (float)-Math.expm1(-elapsed / 25.0);
  swipeVelocityX += (dx * 1000 / elapsed - swipeVelocityX) * weight;
  swipeVelocityY += (dy * 1000 / elapsed - swipeVelocityY) * weight;
  swipeSampleX = x;
  swipeSampleY = y;
  swipeSampleTime = time;
 }

 private void clearSwipeVelocity() {
  trackingSwipe = false;
  swipeVelocityX = swipeVelocityY = 0;
 }

 private float swipeSpeedBlend(double speed) {
  double speedDp = speed / density;
  double t = Math.max(0, Math.min(1, (speedDp - PRECISION_SPEED_DP_PER_SECOND) /
      (FULL_GAIN_SPEED_DP_PER_SECOND - PRECISION_SPEED_DP_PER_SECOND)));
  return (float)(t * t * (3 - 2 * t));
 }

 private void startCoast(MotionEvent up) {
  long motionAge = up.getEventTime() - lastSwipeMotion;
  if (!trackingSwipe || !moved || heldButton != 0 || multiTail ||
      (up.getFlags() & MotionEvent.FLAG_CANCELED) != 0 ||
      motionAge < 0 || motionAge > RELEASE_MOTION_MAX_AGE_MS || !sink.canCoastPointer()) return;

  float vx = swipeVelocityX, vy = swipeVelocityY;
  double speed = Math.hypot(vx, vy);
  if (!Double.isFinite(speed)) return;
  float blend = swipeSpeedBlend(speed);
  if (blend <= 0) return;

  float gain = COAST_RELEASE_GAIN * blend *
      (float)Math.min(1, MAX_COAST_DP_PER_SECOND * density / speed) / scale;
  coastXVelocity = vx * gain;
  coastYVelocity = vy * gain;
  coastDevice = device;
  coasting = true;
  lastCoastFrame = SystemClock.uptimeMillis();
  Choreographer.getInstance().postFrameCallback(coastFrame);
 }

 private void advanceCoast() {
  if (!coasting || !sink.canCoastPointer()) {
   stopPointerCoast();
   return;
  }
  long now = SystemClock.uptimeMillis();
  double elapsed = (now - lastCoastFrame) / 1000.0;
  lastCoastFrame = now;
  double speed = Math.hypot(coastXVelocity, coastYVelocity);
  double dragOffset = COAST_DRAG_OFFSET_DP_PER_SECOND * density / scale;
  // Integrate speed-proportional drag plus a small constant resistance so speed reaches zero.
  double timeToStop = COAST_DECAY_SECONDS * Math.log1p(speed / dragOffset);
  double duration = Math.min(elapsed, timeToStop);
  double drag = (speed + dragOffset) * -Math.expm1(-duration / COAST_DECAY_SECONDS);
  float travelPerVelocity = (float)((drag * COAST_DECAY_SECONDS - dragOffset * duration) / speed);
  int oldX = getPointerX(), oldY = getPointerY();
  pointerX = Math.max(0, Math.min(width - 1, pointerX + coastXVelocity * travelPerVelocity));
  pointerY = Math.max(0, Math.min(height - 1, pointerY + coastYVelocity * travelPerVelocity));
  double speedRatio = Math.max(0, 1 - drag / speed);
  coastXVelocity *= speedRatio;
  coastYVelocity *= speedRatio;
  if ((pointerX <= 0 && coastXVelocity < 0) || (pointerX >= width - 1 && coastXVelocity > 0))
   coastXVelocity = 0;
  if ((pointerY <= 0 && coastYVelocity < 0) || (pointerY >= height - 1 && coastYVelocity > 0))
   coastYVelocity = 0;
  if (getPointerX() != oldX || getPointerY() != oldY) sink.onMouseMove(getPointerX(), getPointerY());
  double remainingSpeed = Math.hypot(coastXVelocity, coastYVelocity);
  double remainingTime = COAST_DECAY_SECONDS * Math.log1p(remainingSpeed / dragOffset);
  double remaining = (remainingSpeed * COAST_DECAY_SECONDS - dragOffset * remainingTime) * scale;
  if (coasting && remaining >= COAST_MIN_REMAINING_SCREEN_PIXELS) {
   Choreographer.getInstance().postFrameCallback(coastFrame);
  } else {
   stopPointerCoast();
  }
 }

 public void stopPointerCoast() {
  if (coasting) Choreographer.getInstance().removeFrameCallback(coastFrame);
  coasting = false;
 }

 private void finishTouchpadGesture(long upTime) {
  if (heldButton != 0) {
   releaseButton();
   return;
  }
  if (moved) return;

  boolean singleFinger = maxPointers == 1;
  int button = singleFinger ? BUTTON_LEFT : BUTTON_RIGHT;
  long duration = upTime - downTime;
  boolean longContact = singleFinger ? duration >= ViewConfiguration.getLongPressTimeout()
      : duration > TWO_FINGER_TAP_MAX_DURATION_MS;
  boolean matchedTap = hasPendingTap(button);
  flushPendingClick();
  if (longContact) {
   // Resolve a stationary hold at lift so pausing before a pointer move never clicks.
   if (singleFinger && !matchedTap) click(BUTTON_RIGHT, getPointerX(), getPointerY());
  } else if (tapDragEnabled && !matchedTap) {
   // The remote OS can see a double click on the second button-down. Keep the
   // first tap local until another tap completes or the user starts a drag.
   pendingTap = new PendingTap(button, upTime, device, getPointerX(), getPointerY(), startX, startY);
   handler.postAtTime(sendPendingClick, upTime + doubleTapMillis);
  } else {
   click(button, getPointerX(), getPointerY());
  }
 }

 private void flushPendingClick() {
  PendingTap tap = pendingTap;
  if (tap == null) return;
  clearPendingClick();
  click(tap.button, tap.cursorX, tap.cursorY);
 }

 private void clearPendingClick() {
  if (pendingTap == null) return;
  handler.removeCallbacks(sendPendingClick);
  pendingTap = null;
 }

 private void click(int button, int x, int y) {
  sink.onMouseButton(button, true, x, y);
  sink.onMouseButton(button, false, x, y);
 }
 private static float span(MotionEvent e){return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
 private static float centerX(MotionEvent e){return (e.getX(0)+e.getX(1))/2;}
 private static float centerY(MotionEvent e){return (e.getY(0)+e.getY(1))/2;}
 private void movePointer(float dx,float dy){pointerX=Math.max(0,Math.min(width-1,pointerX+dx/scale));pointerY=Math.max(0,Math.min(height-1,pointerY+dy/scale));sink.onMouseMove(getPointerX(),getPointerY());}
 private void releaseButton(){if(heldButton!=0){sink.onMouseButton(heldButton,false,getPointerX(),getPointerY());heldButton=0;}}
 private void emitWheel(){
  while(Math.abs(wheelY)>=wheelPixels){boolean down=wheelY>0;sink.onScroll(false,down);wheelY+=down?-wheelPixels:wheelPixels;}
  while(Math.abs(wheelX)>=wheelPixels){boolean right=wheelX>0;sink.onScroll(true,right);wheelX+=right?-wheelPixels:wheelPixels;}
 }
}
