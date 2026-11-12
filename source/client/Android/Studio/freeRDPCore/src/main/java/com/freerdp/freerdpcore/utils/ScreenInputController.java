/*
 * Screen finger input controller. MPL-2.0; see LICENSE.
 * Constructed-event JVM tests do not establish physical-device compatibility.
 */
package com.freerdp.freerdpcore.utils;

import android.view.InputDevice;
import android.view.MotionEvent;
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
  /** positive means down for vertical and right for horizontal, before user scroll inversion. */
  void onScroll(boolean horizontal,boolean positive);
  /** Relative local-view zoom factor, focus in unscaled SessionView coordinates. */
  void onLocalZoom(float factor,float focusX,float focusY);
 }
 private final Sink sink;
 private final float slop,wheelPixels;
 private final long doubleTapMillis;
 private float scale=1, pointerX, pointerY;
 private int width=1,height=1,device=-1;
 private ScreenInputMode mode=ScreenInputMode.TOUCHPAD;
  private boolean tapDragEnabled = true;
 private boolean active=true,stream,ignoreRemainder;
 private float startX,startY,lastX,lastY;
 private long downTime;
 private boolean moved,secondTap,multiTail;
 private int maxPointers,lastTapCount;
 private float wheelX,wheelY,startSpan,lastSpan;
 private int multiKind;
 private int heldButton;
 private long lastTapTime=-100000;
 private float lastTapX,lastTapY;
 private final Map<Integer,int[]> contacts=new LinkedHashMap<>();
 public ScreenInputController(Sink sink,float slop,float wheelPixels,long doubleTapMillis) {
  if(sink==null)throw new IllegalArgumentException("sink");
  this.sink=sink;this.slop=Math.max(1,slop);this.wheelPixels=Math.max(1,wheelPixels);this.doubleTapMillis=Math.max(1,doubleTapMillis);
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
  setPointerPosition(getPointerX(),getPointerY());
 }
 public void setLocalZoom(float scale){if(Float.isFinite(scale)&&scale>0)this.scale=scale;}
 public void setPointerPosition(int x,int y){pointerX=clamp(x,width);pointerY=clamp(y,height);}
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
 public void cancelDevice(int id){if(stream&&device==id)cancel();}
 public void cancel(){
  if(!contacts.isEmpty()){
   int n=contacts.size(),i=0;int[] ids=new int[n],x=new int[n],y=new int[n],actions=new int[n];
   for(Map.Entry<Integer,int[]> c:contacts.entrySet()){ids[i]=c.getKey();x[i]=c.getValue()[0];y[i]=c.getValue()[1];actions[i++]=CANCEL;}
   sink.onTouchFrame(new TouchFrame(ids,x,y,actions));contacts.clear();
  }
  releaseButton();
  lastTapTime=-100000;
  if(stream)ignoreRemainder=true;
  stream=false;device=-1;
 }
 public boolean onTouchEvent(MotionEvent event){
  if(!isScreenFingerEvent(event))return false;
  int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_CANCEL){cancel();return true;}
  if(!active)return true;
  if(action==MotionEvent.ACTION_DOWN){if(stream)cancel();ignoreRemainder=false;stream=true;device=event.getDeviceId();}
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
 private void touchpad(MotionEvent e){
  int action=e.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN){
   startX=lastX=e.getX();startY=lastY=e.getY();downTime=e.getEventTime();moved=false;multiTail=false;maxPointers=1;wheelX=wheelY=0;
   secondTap=isSecondTap(1,downTime,startX,startY);
  }
  else if(action==MotionEvent.ACTION_POINTER_DOWN){
   if(e.getPointerCount()!=2){moved=true;multiTail=true;releaseButton();return;}
   releaseButton();maxPointers=2;startX=lastX=centerX(e);startY=lastY=centerY(e);startSpan=lastSpan=span(e);multiKind=0;
   secondTap=isSecondTap(2,e.getEventTime(),startX,startY);
  }
  else if(action==MotionEvent.ACTION_POINTER_UP){multiTail=true;releaseButton();}
  else if(action==MotionEvent.ACTION_MOVE&&!multiTail&&maxPointers==2&&e.getPointerCount()==2){
   float x=centerX(e),y=centerY(e),currentSpan=span(e);
   float travel=(float)Math.hypot(x-startX,y-startY);
   if(secondTap){
    if(travel>slop)moved=true;
    if(moved){if(heldButton==0){heldButton=BUTTON_RIGHT;sink.onMouseButton(heldButton,true,getPointerX(),getPointerY());}movePointer(x-lastX,y-lastY);lastX=x;lastY=y;}
   }else{
    if(multiKind==0){
     if(Math.abs(currentSpan-startSpan)>slop*2&&Math.abs(currentSpan-startSpan)>travel*1.2f)multiKind=2;
     else if(travel>slop)multiKind=1;
    }
    if(multiKind==2){moved=true;if(lastSpan>0&&currentSpan>0)sink.onLocalZoom(currentSpan/lastSpan,x,y);lastSpan=currentSpan;}
    else if(multiKind==1){moved=true;wheelX-=x-lastX;wheelY-=y-lastY;emitWheel();lastX=x;lastY=y;}
   }
  }
  else if(action==MotionEvent.ACTION_MOVE&&!multiTail&&maxPointers==1){
   if(Math.hypot(e.getX()-startX,e.getY()-startY)>slop)moved=true;
   if(moved){if(secondTap&&heldButton==0){heldButton=BUTTON_LEFT;sink.onMouseButton(heldButton,true,getPointerX(),getPointerY());}movePointer(e.getX()-lastX,e.getY()-lastY);lastX=e.getX();lastY=e.getY();}
  }
  else if(action==MotionEvent.ACTION_UP){
   if(heldButton!=0)releaseButton();
   else if(!moved&&e.getEventTime()-downTime<=500){
    int button=maxPointers==2?BUTTON_RIGHT:BUTTON_LEFT;
    sink.onMouseButton(button,true,getPointerX(),getPointerY());sink.onMouseButton(button,false,getPointerX(),getPointerY());
    if(!secondTap){lastTapTime=e.getEventTime();lastTapX=startX;lastTapY=startY;lastTapCount=maxPointers;}
    else lastTapTime=-100000;
   }else lastTapTime=-100000;
  }
 }
  private boolean isSecondTap(int count,long time,float x,float y){
   return tapDragEnabled && lastTapCount==count && time-lastTapTime<=doubleTapMillis &&
       Math.hypot(x-lastTapX,y-lastTapY)<=slop*2;
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
