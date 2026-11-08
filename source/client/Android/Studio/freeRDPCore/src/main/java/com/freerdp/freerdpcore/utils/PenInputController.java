/* SPDX-License-Identifier: MPL-2.0 */
package com.freerdp.freerdpcore.utils;

import android.view.MotionEvent;
import java.util.LinkedHashSet;

/** Independent P0 pen translator. No JNI or device compatibility is implied. */
public final class PenInputController {
 public static final int DOWN=0,UPDATE=1,UP=2,CANCEL=3,HOVER_ENTER=4,HOVER_UPDATE=5,HOVER_EXIT=6;
 public static final int UNAVAILABLE=0,READY=1,SUSPENDED=2,NEGOTIATING=3;
 public static final int PROTOCOL_V200=0x00020000;
 /** Android-local owner fields must never be copied into wire deviceId (single pen uses0). */
 public static final class PenFrame {
  public final int deviceId,pointerId,toolType,action,x,y,pressure,buttonState;
  public final long downTimeMillis,eventTimeNanos;
  private PenFrame(int device,int pointer,int tool,int action,int x,int y,int pressure,long down,long time,int buttons){
   this.deviceId=device;this.pointerId=pointer;this.toolType=tool;this.action=action;
   this.x=x;this.y=y;this.pressure=pressure;this.downTimeMillis=down;this.eventTimeNanos=time;this.buttonState=buttons;
  }
 }
 public interface Sink {
  int getNativePenState(); int getNegotiatedVersion(); boolean onPenFrame(PenFrame frame);
 }
 private final Sink sink;
 private float scale=1;
 private int width=1,height=1,device,pointer,tool,x,y,pressure,buttonState;
 private long downTime,lastTime;
 private boolean owned,active=true,geometry;
 private boolean inRange;
 private int rangeDevice,rangePointer,rangeTool,rangeX,rangeY,rangeButtons;
 private long rangeTime;
 private static final class Stream {
  final int device,pointer;final long down;
  Stream(int device,long down,int pointer){this.device=device;this.down=down;this.pointer=pointer;}
  @Override public boolean equals(Object value){return value instanceof Stream&&device==((Stream)value).device&&down==((Stream)value).down&&pointer==((Stream)value).pointer;}
  @Override public int hashCode(){return 31*(31*device+Long.hashCode(down))+pointer;}
 }
 private final LinkedHashSet<Stream> retired=new LinkedHashSet<>();
 private void remember(Stream stream){retired.add(stream);while(retired.size()>32)retired.remove(retired.iterator().next());}
 private void retire(){if(owned)remember(new Stream(device,downTime,pointer));owned=false;}
 private boolean retiredTerminal(Stream incoming,int incomingTool){
  for(Stream stream:retired)if(stream.device==incoming.device&&stream.down==incoming.down&&
      (stream.pointer==incoming.pointer||incomingTool==MotionEvent.TOOL_TYPE_UNKNOWN))return true;
  return false;
 }
 public PenInputController(Sink sink){if(sink==null)throw new IllegalArgumentException("sink");this.sink=sink;}
 public void setGeometry(float scale,int width,int height){
  if(!Float.isFinite(scale)||scale<=0||width<=0||height<=0)throw new IllegalArgumentException("geometry");
  if(this.scale!=scale||this.width!=width||this.height!=height)cancel();
  this.scale=scale;this.width=width;this.height=height;geometry=true;
 }
 public void setActive(boolean active){if(!active)cancel();this.active=active;}
 public void cancel(){if(owned){if(ready())emit(CANCEL,x,y,pressure,lastTime);retire();inRange=false;}else endRange();}
 public void cancelDevice(int deviceId){if((owned&&device==deviceId)||(inRange&&rangeDevice==deviceId))cancel();}
 private static boolean penTool(int tool){return tool==MotionEvent.TOOL_TYPE_STYLUS||tool==MotionEvent.TOOL_TYPE_ERASER;}
 private boolean ready(){return sink.getNativePenState()==READY&&sink.getNegotiatedVersion()>=PROTOCOL_V200;}
 private boolean emit(int action,int px,int py,int p,long time){
  return emit(action,px,py,p,time,tool);
 }
 private boolean emit(int action,int px,int py,int p,long time,int candidateTool){
  return emit(action,px,py,p,time,candidateTool,buttonState);
 }
 private boolean emit(int action,int px,int py,int p,long time,int candidateTool,int candidateButtons){
  if(!sink.onPenFrame(new PenFrame(device,pointer,candidateTool,action,px,py,p,downTime,time,candidateButtons)))return false;
  x=px;y=py;pressure=p;lastTime=time;tool=candidateTool;buttonState=candidateButtons;
  if(action!=CANCEL){inRange=true;rangeDevice=device;rangePointer=pointer;rangeTool=candidateTool;rangeX=px;rangeY=py;rangeTime=time;rangeButtons=candidateButtons;}
  return true;
 }
 private void finish(int px,int py,long time){
  finish(px,py,time,tool,0);
 }
 private void finish(int px,int py,long time,int candidateTool,int candidateButtons){
  time=Math.max(lastTime,time);
  if((px!=x||py!=y)&&!emit(UPDATE,px,py,pressure,time)){cancel();return;}
  if(!emit(UP,px,py,0,time,candidateTool,candidateButtons))cancel();else retire();
 }
 private static long timeNanos(MotionEvent event,int history){
  if(android.os.Build.VERSION.SDK_INT>=34)return history<0?event.getEventTimeNanos():event.getHistoricalEventTimeNanos(history);
  return (history<0?event.getEventTime():event.getHistoricalEventTime(history))*1000000L;
 }
 private boolean rangeFrame(int action,int px,int py,long time,int candidateTool,int candidateDevice,int candidatePointer,int buttons){
  if(!sink.onPenFrame(new PenFrame(candidateDevice,candidatePointer,candidateTool,action,px,py,0,0,time,buttons)))return false;
  rangeX=px;rangeY=py;rangeTime=time;rangeTool=candidateTool;rangeDevice=candidateDevice;rangePointer=candidatePointer;rangeButtons=buttons;
  return true;
 }
 private void endRange(){
  if(inRange&&ready())rangeFrame(HOVER_EXIT,rangeX,rangeY,rangeTime,rangeTool,rangeDevice,rangePointer,0);
  inRange=false;
 }
 private boolean hover(MotionEvent event,int index,int action){
  if(owned)return true;
  if(!active||!geometry||!ready()){inRange=false;return true;}
  int candidateDevice=event.getDeviceId(),candidatePointer=event.getPointerId(index),candidateTool=event.getToolType(index);
  boolean same=inRange&&rangeDevice==candidateDevice&&rangePointer==candidatePointer;
  if(action==MotionEvent.ACTION_HOVER_EXIT){if(same&&timeNanos(event,-1)>=rangeTime)endRange();return true;}
  float rawX=event.getX(index),rawY=event.getY(index);
  if(!Float.isFinite(rawX)||!Float.isFinite(rawY))return true;
  int px=Math.max(0,Math.min(width-1,Math.round(rawX/scale)));
  int py=Math.max(0,Math.min(height-1,Math.round(rawY/scale)));
  long time=timeNanos(event,-1);int buttons=event.getButtonState();
  if(same&&action==MotionEvent.ACTION_HOVER_ENTER)return true;
  if(same&&(time<rangeTime||(time==rangeTime&&buttons==rangeButtons)))return true;
  if(!same){endRange();inRange=rangeFrame(HOVER_ENTER,px,py,time,candidateTool,candidateDevice,candidatePointer,buttons);}
  else if(!rangeFrame(HOVER_UPDATE,px,py,time,candidateTool,candidateDevice,candidatePointer,buttons))endRange();
  return true;
 }
 private void updateSample(MotionEvent event,int index,int history){
  long time=timeNanos(event,history);if(time<=lastTime)return;
  float rawX=history<0?event.getX(index):event.getHistoricalX(index,history);
  float rawY=history<0?event.getY(index):event.getHistoricalY(index,history);
  float rawP=history<0?event.getPressure(index):event.getHistoricalPressure(index,history);
  if(!Float.isFinite(rawX)||!Float.isFinite(rawY)||!Float.isFinite(rawP))return;
  int px=Math.max(0,Math.min(width-1,Math.round(rawX/scale)));
  int py=Math.max(0,Math.min(height-1,Math.round(rawY/scale)));
  if(!emit(UPDATE,px,py,Math.round(Math.max(0,Math.min(1,rawP))*1024),time,event.getToolType(index),event.getButtonState()))cancel();
 }
 public boolean onMotionEvent(MotionEvent event){
  int index=event.getActionIndex();
  int action=event.getActionMasked();
  if((action==MotionEvent.ACTION_HOVER_ENTER||action==MotionEvent.ACTION_HOVER_MOVE||action==MotionEvent.ACTION_HOVER_EXIT)&&penTool(event.getToolType(index)))return hover(event,index,action);
  if((action==MotionEvent.ACTION_BUTTON_PRESS||action==MotionEvent.ACTION_BUTTON_RELEASE)&&penTool(event.getToolType(index))){
   if(action==MotionEvent.ACTION_BUTTON_RELEASE&&(!inRange||rangeDevice!=event.getDeviceId()||rangePointer!=event.getPointerId(index)))return true;
   if(owned){
    if(device!=event.getDeviceId()||pointer!=event.getPointerId(index))return true;
    if(!active||!geometry||!ready()){retire();inRange=false;return true;}
    long time=timeNanos(event,-1);int buttons=event.getButtonState();
    if(time<lastTime||(time==lastTime&&buttons==buttonState))return true;
    float rawX=event.getX(index),rawY=event.getY(index),rawP=event.getPressure(index);
    if(!Float.isFinite(rawX)||!Float.isFinite(rawY)||!Float.isFinite(rawP))return true;
    int px=Math.max(0,Math.min(width-1,Math.round(rawX/scale))),py=Math.max(0,Math.min(height-1,Math.round(rawY/scale)));
    if(!emit(UPDATE,px,py,Math.round(Math.max(0,Math.min(1,rawP))*1024),time,event.getToolType(index),buttons))cancel();
    return true;
   }
   return hover(event,index,MotionEvent.ACTION_HOVER_MOVE);
  }
  Stream incoming=new Stream(event.getDeviceId(),event.getDownTime(),event.getPointerId(index));
  // ACTION_CANCEL retires this whole motion set, not actionIndex zero alone.
  if(action==MotionEvent.ACTION_CANCEL&&owned&&device==incoming.device&&downTime==incoming.down){cancel();return true;}
  if(owned&&action==MotionEvent.ACTION_MOVE&&device==incoming.device&&downTime==incoming.down){
   int ownerIndex=event.findPointerIndex(pointer);if(ownerIndex>=0)index=ownerIndex;
   incoming=new Stream(event.getDeviceId(),event.getDownTime(),event.getPointerId(index));
  }
  boolean terminal=action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_UP;
  boolean metadataLost=!penTool(event.getToolType(index));
  // Retire an already-owned terminal before source/tool classification. The
  // framework may supply UNKNOWN tool/source or omit the old pointer id.
  if(terminal&&metadataLost){
   if(owned&&device==incoming.device&&downTime==incoming.down&&
      (event.getPointerId(index)==pointer||(event.getToolType(index)==MotionEvent.TOOL_TYPE_UNKNOWN&&
       (action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP)))){
    if(ready()){if(action==MotionEvent.ACTION_CANCEL||(event.getFlags()&MotionEvent.FLAG_CANCELED)!=0)cancel();else finish(x,y,timeNanos(event,-1));}
    else retire();
    return true;
   }
   if(retiredTerminal(incoming,event.getToolType(index)))return true;
  }
  if(!penTool(event.getToolType(index)))return false;
  if(retired.contains(incoming))return true;
  if(!active||!geometry||!ready()){retire();inRange=false;remember(incoming);return true;}
  if(action==MotionEvent.ACTION_MOVE){
   if(!owned){remember(incoming);return true;}
   if(device!=incoming.device||downTime!=incoming.down||event.getPointerId(index)!=pointer){cancel();remember(incoming);return true;}
  }
  if(owned&&device==incoming.device&&downTime==incoming.down&&pointer==event.getPointerId(index)){
   if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)return true;
   if(action==MotionEvent.ACTION_CANCEL||((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&
      (event.getFlags()&MotionEvent.FLAG_CANCELED)!=0)){cancel();return true;}
  }
  boolean finite=Float.isFinite(event.getX(index))&&Float.isFinite(event.getY(index))&&Float.isFinite(event.getPressure(index));
  if((action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)&&!finite){cancel();remember(incoming);return true;}
  int px=Math.max(0,Math.min(width-1,Math.round(event.getX(index)/scale)));
  int py=Math.max(0,Math.min(height-1,Math.round(event.getY(index)/scale)));
  int p=Math.round(Math.max(0,Math.min(1,event.getPressure(index)))*1024);
  long time=timeNanos(event,-1);
  if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){if(owned)cancel();else if(inRange&&rangeDevice!=event.getDeviceId())endRange();owned=true;device=event.getDeviceId();pointer=event.getPointerId(index);tool=event.getToolType(index);downTime=event.getDownTime();if(!emit(DOWN,px,py,p,time,tool,event.getButtonState()))retire();}
  else if(owned&&action==MotionEvent.ACTION_MOVE){for(int h=0;h<event.getHistorySize()&&owned;h++)updateSample(event,index,h);if(owned)updateSample(event,index,-1);}
  else if(owned&&device==incoming.device&&downTime==incoming.down&&pointer==event.getPointerId(index)&&
          (action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)){if(!finite){px=x;py=y;}finish(px,py,time,event.getToolType(index),event.getButtonState());}
  return true;
 }
}
