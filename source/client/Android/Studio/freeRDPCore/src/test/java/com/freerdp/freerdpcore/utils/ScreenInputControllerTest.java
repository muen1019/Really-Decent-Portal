/*
 * SPDX-License-Identifier: MPL-2.0
 * Original additions for the DeskQuay Android client.
 * Distribution change notice: 2026-10-29.
 * Upstream basis: FreeRDP 3.32.2 commit 97eff6ddfbbfaa8e42a126d362686bd001ff8bdc.
 * Existing copyright and attribution notices are retained.
 */

package com.freerdp.freerdpcore.utils;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.ViewConfiguration;
import com.freerdp.freerdpcore.presentation.ScreenInputMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowSystemClock;
import java.util.*;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=37)
public class ScreenInputControllerTest {
 static class Output implements ScreenInputController.Sink {
  boolean available=true,accept=true; List<ScreenInputController.TouchFrame> frames=new ArrayList<>(); List<String> mouse=new ArrayList<>();
  public boolean isDirectTouchAvailable(){return available;}
  public boolean onTouchFrame(ScreenInputController.TouchFrame f){frames.add(f);return accept;}
  public void onMouseMove(int x,int y){mouse.add("move:"+x+","+y);}
  public void onMouseButton(int b,boolean d,int x,int y){mouse.add("button:"+b+":"+d+":"+x+","+y);}
  public void onScroll(boolean h,boolean p){mouse.add("wheel:"+h+":"+p);}
  public void onLocalZoom(float f,float x,float y){mouse.add("zoom:"+f);}
 }
 static MotionEvent event(long time,int action,int[] ids,float... xy) {
  return fingerEvent(7, time, action, ids, xy);
 }

 private static MotionEvent fingerEvent(int deviceId,long time,int action,int[] ids,float... xy) {
  MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[ids.length]; MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[ids.length];
  for(int i=0;i<ids.length;i++){pp[i]=new MotionEvent.PointerProperties();pp[i].id=ids[i];pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i]=new MotionEvent.PointerCoords();pc[i].x=xy[i*2];pc[i].y=xy[i*2+1];pc[i].pressure=1;pc[i].size=1;}
  return MotionEvent.obtain(1,time,action,ids.length,pp,pc,0,0,1,1,deviceId,0,InputDevice.SOURCE_TOUCHSCREEN,0);
 }
 static void send(ScreenInputController c,long time,int action,int[] ids,float... xy){MotionEvent e=event(time,action,ids,xy);try{assertTrue(c.onTouchEvent(e));}finally{e.recycle();}}
 @Test public void directTouchMapsAbsoluteCoordinatesWithoutMouseFallback(){
  Output o=new Output(); ScreenInputController c=new ScreenInputController(o,8,40,300); c.setGeometry(2,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,MotionEvent.ACTION_DOWN,new int[]{9},200,300);
  assertEquals(1,o.frames.size()); assertArrayEquals(new int[]{9},o.frames.get(0).ids); assertArrayEquals(new int[]{100},o.frames.get(0).x); assertArrayEquals(new int[]{150},o.frames.get(0).y);assertArrayEquals(new int[]{0},o.frames.get(0).actions);assertTrue(o.mouse.isEmpty());
 }
 @Test public void directTouchTracksAllContactsAndCancelsTheirLatestCoordinates(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(2,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,MotionEvent.ACTION_DOWN,new int[]{9},200,300);
  send(c,20,MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{9,4},220,320,600,700);
  send(c,30,MotionEvent.ACTION_MOVE,new int[]{4,9},640,740,240,340);
  send(c,40,MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{4,9},660,760,260,360);
  c.cancel();
  assertEquals(5,o.frames.size());
  assertArrayEquals(new int[]{1,0},o.frames.get(1).actions);
  assertArrayEquals(new int[]{320,120},o.frames.get(2).x);
  assertArrayEquals(new int[]{1,2},o.frames.get(3).actions);
  assertArrayEquals(new int[]{4},o.frames.get(4).ids);assertArrayEquals(new int[]{330},o.frames.get(4).x);assertArrayEquals(new int[]{3},o.frames.get(4).actions);
  c.cancel();assertEquals(5,o.frames.size());assertTrue(o.mouse.isEmpty());
 }
 @Test public void fingerTouchpadMovesRelativelyAndClicksAtExistingCursor(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(2,1000,700);c.setPointerPosition(500,350);
  send(c,10,MotionEvent.ACTION_DOWN,new int[]{0},100,100);
  send(c,20,MotionEvent.ACTION_MOVE,new int[]{0},140,120);
  send(c,30,MotionEvent.ACTION_UP,new int[]{0},140,120);
  long tap = SystemClock.uptimeMillis();
  send(c,tap,MotionEvent.ACTION_DOWN,new int[]{0},800,900);
  send(c,tap+50,MotionEvent.ACTION_UP,new int[]{0},800,900);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
  assertEquals(Arrays.asList("move:520,360","button:1:true:520,360","button:1:false:520,360"),o.mouse);assertTrue(o.frames.isEmpty());
 }

 private static void advance(long milliseconds) {
  // Step the clock one simulated display refresh at a time to deliver paused VSYNCs.
  while (milliseconds > 0) {
   long step = Math.min(milliseconds, 16);
   ShadowSystemClock.advanceBy(Duration.ofMillis(step));
   shadowOf(Looper.getMainLooper()).idle();
   milliseconds -= step;
  }
 }

 @Before public void configureDisplayFrames() {
  ShadowChoreographer.setPaused(true);
  ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
 }

 private static void swipeFingerAndLift(ScreenInputController controller) {
  int[] finger = {0};
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, finger, 500, 300);
  for (int step = 1; step <= 4; step++) {
   advance(16);
   send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, finger,
       500 - step * 32, 300 + step * 16);
  }
  advance(8);
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, finger, 372, 364);
 }

 @Test public void fastFingerSwipeCoastsInItsDirectionAndDecelerates() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(2, 4000, 3000);
  swipeFingerAndLift(controller);
  int releasedX = controller.getPointerX();
  int releasedY = controller.getPointerY();
  int movesBeforeCoast = output.mouse.size();

  advance(64);
  int firstX = controller.getPointerX();
  assertTrue(firstX < releasedX);
  assertTrue(controller.getPointerY() > releasedY);
  advance(64);
  int secondX = controller.getPointerX();
  assertTrue(secondX < firstX);
  assertTrue(firstX - secondX < releasedX - firstX);
  advance(172);
  int lateX = controller.getPointerX();
  advance(100);
  int laterX = controller.getPointerX();
  assertTrue("A fast release should still be easing after 300 ms", laterX < lateX);
  assertTrue(lateX - laterX < firstX - secondX);
  advance(2000);
  int settled = output.mouse.size();
  advance(500);
  assertEquals(settled, output.mouse.size());
  assertTrue(output.mouse.size() > movesBeforeCoast);
  assertTrue(output.mouse.stream().allMatch(event -> event.startsWith("move:")));
 }

 @Test public void coastingContinuesAlongAnUnblockedAxisAtTheDesktopEdge() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  controller.setPointerPosition(130, 1500);
  swipeFingerAndLift(controller);
  int releasedY = controller.getPointerY();
  advance(80);
  assertEquals(0, controller.getPointerX());
  assertTrue(controller.getPointerY() > releasedY);
  int edgeY = controller.getPointerY();
  advance(100);
  assertEquals(0, controller.getPointerX());
  assertTrue(controller.getPointerY() > edgeY);
 }

 @Test public void touchingTheScreenStopsCursorCoastingImmediately() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  swipeFingerAndLift(controller);
  int releasedX = controller.getPointerX();
  advance(48);
  int interruptedX = controller.getPointerX();
  assertTrue(interruptedX < releasedX);
  int movesBeforeTouch = output.mouse.size();

  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, new int[]{0}, 372, 364);
  advance(250);
  assertEquals(interruptedX, controller.getPointerX());
  assertEquals(movesBeforeTouch, output.mouse.size());
 }

 @Test public void virtualTouchscreenCanCoastAndBeCancelledByDevice() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  int[] finger = {0};
  int[] actions = {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE,
      MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP};
  int[] positions = {400, 440, 480, 520, 520};
  for (int i = 0; i < actions.length; i++) {
   advance(16);
   MotionEvent event = fingerEvent(-1, SystemClock.uptimeMillis(), actions[i], finger, positions[i], 300);
   try { assertTrue(controller.onTouchEvent(event)); }
   finally { event.recycle(); }
  }
  int releasedX = controller.getPointerX();
  advance(64);
  int stoppedX = controller.getPointerX();
  assertTrue(stoppedX > releasedX);
  controller.cancelDevice(-1);
  advance(350);
  assertEquals(stoppedX, controller.getPointerX());
 }

 @Test public void liftingAfterPausingDoesNotCoastFromStaleMovement() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  int[] finger = {0};
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, finger, 500, 300);
  advance(16);
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, finger, 550, 300);
  advance(16);
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, finger, 600, 300);
  advance(250);
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, finger, 600, 300);

  advance(1000);
  assertEquals(Arrays.asList("move:2050,1500", "move:2100,1500"), output.mouse);
 }

 @Test public void slowFingerMovementUsesPrecisionGainAndStopsOnRelease() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  int[] finger = {0};
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, finger, 500, 300);
  for (int step = 1; step <= 10; step++) {
   advance(20);
   send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, finger, 500 + step * 2, 300);
  }
  advance(8);
  send(controller, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, finger, 520, 300);
  int movesBeforeRelease = output.mouse.size();
  assertTrue(movesBeforeRelease > 0);

  advance(1000);
  assertEquals(2017, controller.getPointerX());
  assertEquals(movesBeforeRelease, output.mouse.size());
 }

 @Test public void theSameFingerTravelMovesTheCursorFartherAtHigherSpeed() {
  int slow = cursorTravelBeforeRelease(105);
  int medium = cursorTravelBeforeRelease(30);
  int fast = cursorTravelBeforeRelease(12);
  assertTrue(slow < medium);
  assertTrue(medium < fast);
  assertTrue(fast <= 240);
 }

 private static int cursorTravelBeforeRelease(int intervalMillis) {
  ScreenInputController controller = new ScreenInputController(new Output(), 8, 40, 300, 16, 2);
  controller.setGeometry(1, 4000, 3000);
  int[] finger = {0};
  long start = SystemClock.uptimeMillis();
  send(controller, start, MotionEvent.ACTION_DOWN, finger, 500, 300);
  for (int step = 1; step <= 6; step++) {
   send(controller, start + step * intervalMillis, MotionEvent.ACTION_MOVE, finger,
       500 + step * 40, 300);
  }
  return controller.getPointerX() - 2000;
 }

 @Test public void disconnectingStopsCursorCoastingImmediately() {
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 4000, 3000);
  swipeFingerAndLift(controller);
  int releasedX = controller.getPointerX();
  advance(48);
  assertTrue(controller.getPointerX() < releasedX);
  int movesBeforeDisconnect = output.mouse.size();

  controller.setActive(false);
  advance(250);
  assertEquals(movesBeforeDisconnect, output.mouse.size());
 }
 @Test public void secondTapDragKeepsLeftDownUntilReleaseAndCancelEndsOnlyItsDrag(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setPointerPosition(500,350);
  send(c,10,0,new int[]{0},100,100);send(c,30,1,new int[]{0},100,100);
  send(c,100,0,new int[]{0},100,100);send(c,130,2,new int[]{0},120,100);
  assertEquals(Arrays.asList("button:1:true:500,350","move:520,350"),o.mouse);
  c.cancel();c.cancel();send(c,150,1,new int[]{0},120,100);
  assertEquals(Arrays.asList("button:1:true:500,350","move:520,350","button:1:false:520,350"),o.mouse);
 }
 @Test public void quickSecondTapDragSendsOnlyOneRemotePress(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};

  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 90, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 100, MotionEvent.ACTION_MOVE, finger, 103, 100);
  send(controller, 110, MotionEvent.ACTION_MOVE, finger, 120, 100);
  send(controller, 120, MotionEvent.ACTION_MOVE, finger, 140, 100);
  send(controller, 130, MotionEvent.ACTION_UP, finger, 140, 100);

  advance(1000);
  assertEquals(Arrays.asList("button:1:true:500,350", "move:520,350", "move:540,350",
      "button:1:false:540,350"), output.mouse);
 }
 @Test public void shiftedSecondTapUsesTheDoubleTapRadiusAndStartsDraggingWithoutDwell(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300, 100);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};

  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 90, MotionEvent.ACTION_DOWN, finger, 140, 100);
  send(controller, 95, MotionEvent.ACTION_MOVE, finger, 145, 100);
  assertTrue(output.mouse.isEmpty());
  send(controller, 100, MotionEvent.ACTION_MOVE, finger, 150, 100);
  send(controller, 110, MotionEvent.ACTION_UP, finger, 150, 100);

  assertEquals(Arrays.asList("button:1:true:500,350", "move:510,350",
      "button:1:false:510,350"), output.mouse);
 }

 @Test public void secondTouchOutsideTheDoubleTapRadiusMovesWithoutDragging(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300, 100);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};

  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 90, MotionEvent.ACTION_DOWN, finger, 205, 100);
  send(controller, 100, MotionEvent.ACTION_MOVE, finger, 220, 100);
  send(controller, 110, MotionEvent.ACTION_UP, finger, 220, 100);

  assertEquals(Arrays.asList("button:1:true:500,350", "button:1:false:500,350",
      "move:513,350"), output.mouse);
 }

 @Test public void quickDoubleClickIsSentAfterTheSecondFingerLifts(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};
  long tap = SystemClock.uptimeMillis();

  send(controller, tap, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, tap + 20, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, tap + 70, MotionEvent.ACTION_DOWN, finger, 100, 100);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
  assertTrue(output.mouse.isEmpty());

  send(controller, tap + 350, MotionEvent.ACTION_UP, finger, 100, 100);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
  assertEquals(Arrays.asList("button:1:true:500,350", "button:1:false:500,350",
      "button:1:true:500,350", "button:1:false:500,350"), output.mouse);
 }

 @Test public void singleTapIsSentExactlyOnceWhenNoSecondTapArrives(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  long tap = SystemClock.uptimeMillis();

  send(controller, tap, MotionEvent.ACTION_DOWN, new int[]{0}, 100, 100);
  send(controller, tap + 20, MotionEvent.ACTION_UP, new int[]{0}, 100, 100);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(319));
  assertTrue(output.mouse.isEmpty());
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
  assertEquals(Arrays.asList("button:1:true:500,350", "button:1:false:500,350"), output.mouse);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
  assertEquals(2, output.mouse.size());
 }

 @Test public void leavingTouchpadModeDropsAnUnresolvedTap(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  long tap = SystemClock.uptimeMillis();

  send(controller, tap, MotionEvent.ACTION_DOWN, new int[]{0}, 100, 100);
  send(controller, tap + 20, MotionEvent.ACTION_UP, new int[]{0}, 100, 100);
  controller.setMode(ScreenInputMode.DIRECT_TOUCH);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));

  assertTrue(output.mouse.isEmpty());
 }

 @Test public void disablingTapDragPreservesDoubleClickAndUnheldMovement(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  controller.setTapDragEnabled(false);
  int[] finger = {0};

  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 100, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 130, MotionEvent.ACTION_UP, finger, 100, 100);
  assertEquals(Arrays.asList("button:1:true:500,350", "button:1:false:500,350",
      "button:1:true:500,350", "button:1:false:500,350"), output.mouse);

  output.mouse.clear();
  send(controller, 1000, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 1030, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 1100, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 1130, MotionEvent.ACTION_MOVE, finger, 120, 100);
  send(controller, 1150, MotionEvent.ACTION_UP, finger, 120, 100);
  assertEquals(Arrays.asList("button:1:true:500,350", "button:1:false:500,350",
      "move:517,350"), output.mouse);
 }
 @Test public void disablingTapDragReleasesAnActiveDragOnce(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};
  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 100, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 130, MotionEvent.ACTION_MOVE, finger, 120, 100);
  assertEquals(2, output.mouse.size());

  controller.setTapDragEnabled(false);
  assertEquals(3, output.mouse.size());
  assertEquals("button:1:false:520,350", output.mouse.get(2));
  controller.setTapDragEnabled(false);
  send(controller, 150, MotionEvent.ACTION_UP, finger, 120, 100);
  assertEquals(3, output.mouse.size());
 }
 static void twoTap(ScreenInputController c,long t){
  send(c,t,0,new int[]{0},100,100);send(c,t+10,5|(1<<8),new int[]{0,1},100,100,200,100);
  send(c,t+20,6|(1<<8),new int[]{0,1},100,100,200,100);send(c,t+30,1,new int[]{0},100,100);
 }
 @Test public void twoFingerTapClicksRightWithoutLeftClick(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setPointerPosition(500,350);
  long tap = SystemClock.uptimeMillis();
  twoTap(c,tap);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(330));
  assertEquals(Arrays.asList("button:2:true:500,350","button:2:false:500,350"),o.mouse);assertTrue(o.frames.isEmpty());
 }
 @Test public void stationarySingleFingerHoldRightClicksOnRelease(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  long tap = SystemClock.uptimeMillis();
  long hold = ViewConfiguration.getLongPressTimeout();

  send(controller, tap, MotionEvent.ACTION_DOWN, new int[]{0}, 100, 100);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(hold));
  assertTrue(output.mouse.isEmpty());
  send(controller, tap + hold, MotionEvent.ACTION_UP, new int[]{0}, 100, 100);

  assertEquals(Arrays.asList("button:2:true:500,350", "button:2:false:500,350"), output.mouse);
 }

 @Test public void pausingBeforeMovingThePointerDoesNotClick(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  long tap = SystemClock.uptimeMillis();
  long hold = ViewConfiguration.getLongPressTimeout() + 20;

  send(controller, tap, MotionEvent.ACTION_DOWN, new int[]{0}, 100, 100);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(hold));
  send(controller, tap + hold, MotionEvent.ACTION_MOVE, new int[]{0}, 120, 100);
  send(controller, tap + hold + 10, MotionEvent.ACTION_UP, new int[]{0}, 120, 100);

  assertEquals(Collections.singletonList("move:517,350"), output.mouse);
 }

 @Test public void quickMovementDeliveredOnLiftIsNotMistakenForDoubleClick(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);
  int[] finger = {0};

  send(controller, 10, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 30, MotionEvent.ACTION_UP, finger, 100, 100);
  send(controller, 90, MotionEvent.ACTION_DOWN, finger, 100, 100);
  send(controller, 110, MotionEvent.ACTION_UP, finger, 120, 100);

  assertEquals(Arrays.asList("button:1:true:500,350", "move:520,350",
      "button:1:false:520,350"), output.mouse);
 }

 @Test public void pinchAfterTwoFingerTapStillZoomsLocally(){
  Output output = new Output();
  ScreenInputController controller = new ScreenInputController(output, 8, 40, 300);
  controller.setGeometry(1, 1000, 700);

  twoTap(controller, 10);
  send(controller, 100, MotionEvent.ACTION_DOWN, new int[]{0}, 100, 100);
  send(controller, 110, MotionEvent.ACTION_POINTER_DOWN | (1 << 8), new int[]{0, 1}, 100, 100, 200, 100);
  send(controller, 130, MotionEvent.ACTION_MOVE, new int[]{0, 1}, 50, 100, 250, 100);
  send(controller, 150, MotionEvent.ACTION_POINTER_UP | (1 << 8), new int[]{0, 1}, 50, 100, 250, 100);
  send(controller, 160, MotionEvent.ACTION_UP, new int[]{0}, 50, 100);

  assertEquals(Arrays.asList("button:2:true:500,350", "button:2:false:500,350", "zoom:2.0"), output.mouse);
 }

 @Test public void twoFingerSlideSendsWheelNotPointerOrButtons(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);
  send(c,10,0,new int[]{0},100,200);send(c,20,5|(1<<8),new int[]{0,1},100,200,200,200);
  send(c,30,2,new int[]{0,1},100,150,200,150);
  send(c,40,6|(1<<8),new int[]{0,1},100,150,200,150);send(c,50,1,new int[]{0},100,150);
  advance(1000);
  assertEquals(Collections.singletonList("wheel:false:true"),o.mouse);
 }
 @Test public void secondTwoFingerTapDragEndsWhenFingersLift(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setPointerPosition(500,350);
  twoTap(c,10);
  send(c,100,0,new int[]{0},100,100);send(c,110,5|(1<<8),new int[]{0,1},100,100,200,100);
  send(c,130,2,new int[]{0,1},120,100,220,100);send(c,150,6|(1<<8),new int[]{0,1},120,100,220,100);send(c,160,1,new int[]{0},120,100);
  assertEquals(Arrays.asList("button:2:true:500,350","move:520,350","button:2:false:520,350"),o.mouse);
 }
 @Test public void pinchZoomsLocallyWithoutMouseOrNativeTouch(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);
  send(c,10,0,new int[]{0},100,100);send(c,20,5|(1<<8),new int[]{0,1},100,100,200,100);
  send(c,30,2,new int[]{0,1},50,100,250,100);
  send(c,40,6|(1<<8),new int[]{0,1},50,100,250,100);send(c,50,1,new int[]{0},50,100);
  assertEquals(Collections.singletonList("zoom:2.0"),o.mouse);assertTrue(o.frames.isEmpty());
 }
 @Test public void switchingModeCancelsOldContactsAndRequiresFreshDown(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);c.setMode(ScreenInputMode.TOUCHPAD);
  send(c,20,2,new int[]{0},200,200);send(c,30,1,new int[]{0},200,200);
  assertEquals(2,o.frames.size());assertArrayEquals(new int[]{3},o.frames.get(1).actions);assertTrue(o.mouse.isEmpty());
  long tap = SystemClock.uptimeMillis();
  send(c,tap,0,new int[]{0},800,600);send(c,tap+10,1,new int[]{0},800,600);
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(310));
  assertEquals(2,o.mouse.size());
 }
 @Test public void unavailableOrSuspendedNativeTouchNeverSilentlyBecomesMouse(){
  Output o=new Output();o.available=false;ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);o.available=true;send(c,20,2,new int[]{0},120,120);send(c,30,1,new int[]{0},120,120);
  assertTrue(o.frames.isEmpty());assertTrue(o.mouse.isEmpty());
  send(c,40,0,new int[]{0},100,100);o.available=false;send(c,50,2,new int[]{0},120,120);o.available=true;send(c,60,1,new int[]{0},120,120);
  assertEquals(2,o.frames.size());assertArrayEquals(new int[]{3},o.frames.get(1).actions);assertTrue(o.mouse.isEmpty());
 }
 @Test public void failedNativeEnqueueCancelsAndNeedsNewGesture(){
  Output o=new Output();o.accept=false;ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);o.accept=true;send(c,20,2,new int[]{0},120,120);send(c,30,1,new int[]{0},120,120);
  assertEquals(2,o.frames.size());assertArrayEquals(new int[]{3},o.frames.get(1).actions);assertTrue(o.mouse.isEmpty());
 }
 @Test public void focusPauseDeactivationCancelsThenIgnoresOldStreamAfterResume(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);c.setActive(false);c.setActive(true);send(c,20,2,new int[]{0},120,120);send(c,30,1,new int[]{0},120,120);
  assertEquals(2,o.frames.size());send(c,40,0,new int[]{0},100,100);assertEquals(3,o.frames.size());
 }
 @Test public void geometryChangeCancelsBeforeNewCoordinateMappingAndClampsBounds(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},200,300);c.setGeometry(2,1000,700);send(c,20,1,new int[]{0},200,300);
  assertArrayEquals(new int[]{200},o.frames.get(1).x);assertArrayEquals(new int[]{3},o.frames.get(1).actions);
  send(c,30,0,new int[]{0},-10,9000);assertArrayEquals(new int[]{0},o.frames.get(2).x);assertArrayEquals(new int[]{699},o.frames.get(2).y);
 }
 @Test public void deviceRemovalOnlyCancelsMatchingFingerOwner(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);c.cancelDevice(8);assertEquals(1,o.frames.size());c.cancelDevice(7);assertEquals(2,o.frames.size());assertArrayEquals(new int[]{3},o.frames.get(1).actions);
 }
 @Test public void horizontalSlideAccumulatesSmallDistancesAndNeverClicks(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);
  send(c,10,0,new int[]{0},100,100);send(c,20,5|(1<<8),new int[]{0,1},100,100,200,100);
  send(c,30,2,new int[]{0,1},80,100,180,100);send(c,40,2,new int[]{0,1},50,100,150,100);
  send(c,50,6|(1<<8),new int[]{0,1},50,100,150,100);send(c,60,1,new int[]{0},50,100);
  assertEquals(Collections.singletonList("wheel:true:true"),o.mouse);
 }
 @Test public void mouseTouchpadAndStylusSourcesRemainOutsideFingerController(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  for(int source:new int[]{InputDevice.SOURCE_MOUSE,InputDevice.SOURCE_TOUCHPAD,InputDevice.SOURCE_STYLUS}){
   MotionEvent e=event(10,0,new int[]{0},100,100);e.setSource(source);try{assertFalse(c.onTouchEvent(e));}finally{e.recycle();}
  }
  assertTrue(o.frames.isEmpty());assertTrue(o.mouse.isEmpty());
 }
 @Test public void systemCancelWithoutToolMetadataStillReleasesDisplayContacts(){
  Output o=new Output();ScreenInputController c=new ScreenInputController(o,8,40,300);c.setGeometry(1,1000,700);c.setMode(ScreenInputMode.DIRECT_TOUCH);
  send(c,10,0,new int[]{0},100,100);
  MotionEvent.PointerProperties pp=new MotionEvent.PointerProperties();pp.id=0;pp.toolType=MotionEvent.TOOL_TYPE_UNKNOWN;
  MotionEvent.PointerCoords pc=new MotionEvent.PointerCoords();pc.x=100;pc.y=100;
  MotionEvent e=MotionEvent.obtain(1,20,MotionEvent.ACTION_CANCEL,1,new MotionEvent.PointerProperties[]{pp},new MotionEvent.PointerCoords[]{pc},0,0,1,1,7,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{assertTrue(c.onTouchEvent(e));}finally{e.recycle();}
  assertEquals(2,o.frames.size());assertArrayEquals(new int[]{3},o.frames.get(1).actions);
 }
}
