/*
 * Copyright 2026 Really Decent Portal contributors.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.freerdp.freerdpcore.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Physical touchpad distances become signed wheel units, retaining sub-unit residue. */
public final class TouchpadWheelAccumulator {
    public interface Sink { void wheel(int horizontal, int vertical); }
    // Work policy, not a protocol limit: at most 64 adjacent H/V pairs per callback.
    public static final int MAX_CHUNKS_PER_EVENT = 64;
    private static final int MAX_CHUNK = 255;
    private static final double MAX_SAMPLE_UNITS = MAX_CHUNK * MAX_CHUNKS_PER_EVENT;
    private static final class Sample {
        final long x, y;
        Sample(double dx, double dy) { x=Double.doubleToLongBits(dx); y=Double.doubleToLongBits(dy); }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Sample)) return false;
            Sample value=(Sample)other; return x==value.x && y==value.y;
        }
        @Override public int hashCode() { return Long.hashCode(x)*31+Long.hashCode(y); }
    }
    private static final class Stream {
        long downTime;
        long time=-1;
        long startTime=-1;
        long terminalTime=-1;
        long retiredThroughTime=-1;
        boolean closed;
        boolean pressurelessPlaceholder;
        boolean acceptedSample;
        boolean cancelledPlaceholder;
        double remainderX, remainderY, factorX, factorY;
        final Set<Sample> atTime=new HashSet<>();
        Stream(long downTime) { this.downTime=downTime; }
        void clearRemainder() { remainderX=0; remainderY=0; }
        void reset() {
            clearRemainder(); retiredThroughTime=Math.max(retiredThroughTime,time);
            cancelledPlaceholder=false;
        }
        void cancel(boolean continuous) {
            reset();
            terminalTime=Math.max(terminalTime,Math.max(startTime,time));
            if (!continuous) closed=true;
        }
    }
    private final Map<Integer,Stream> streams=new HashMap<>();
    private final boolean continuous;
    public TouchpadWheelAccumulator() { this(false); }
    /** Scroll wheels have no required DOWN and may keep downTime=0 across focus changes. */
    public TouchpadWheelAccumulator(boolean continuous) { this.continuous=continuous; }
    private Stream stream(int device, long downTime) {
        Stream current=streams.get(device);
        if (!continuous && current!=null && downTime<current.downTime) return null;
        if (current==null || (!continuous && current.downTime!=downTime)) {
            current=new Stream(downTime); streams.put(device,current);
        }
        return current;
    }
    public void begin(int device, long downTime) { stream(device,downTime); }
    /** A new DOWN after a terminal edge may have the same millisecond downTime. */
    public void begin(int device, long downTime, long boundaryTime) {
        begin(device,downTime,boundaryTime,Float.NaN);
    }
    /** Pressure is provided only for a validated zero-distance classified contact. */
    public void begin(int device, long downTime, long boundaryTime, float classifiedPressure) {
        if (boundaryTime<0) return;
        Stream previous=streams.get(device);
        Stream current=stream(device,downTime);
        if (current==null) return;
        if (current!=previous) {
            current.startTime=boundaryTime;
            current.pressurelessPlaceholder=classifiedPressure==0.0f;
            return;
        }
        // Duplicate DOWN callbacks in an open stream must retain its residue.
        // A cancelled stream never reopens merely because a MOVE arrives later.
        boolean sameTimeRealContact=current.cancelledPlaceholder && !current.acceptedSample &&
            classifiedPressure==1.0f && boundaryTime==current.startTime &&
            boundaryTime==current.terminalTime;
        if (!continuous && current.closed &&
            (boundaryTime>Math.max(current.terminalTime,current.time) || sameTimeRealContact)) {
            Stream fresh=new Stream(downTime);
            fresh.startTime=boundaryTime;
            fresh.pressurelessPlaceholder=classifiedPressure==0.0f;
            // AOSP's pressureless fling-stop DOWN/CANCEL has no movement. Its
            // terminal timestamp must not reject the real first MOVE in that frame.
            if (!sameTimeRealContact)
                fresh.retiredThroughTime=Math.max(current.retiredThroughTime,current.terminalTime);
            streams.put(device,fresh);
        }
    }
    public void end(int device, long downTime) {
        Stream current=stream(device,downTime); if(current!=null)current.cancel(continuous);
    }
    public void endIfOwned(int device, long downTime) {
        Stream current=streams.get(device);
        if(current!=null && current.downTime==downTime)current.cancel(continuous);
    }
    /** An older UP/CANCEL cannot retire a restarted generation with the same downTime. */
    public void endIfOwned(int device, long downTime, long boundaryTime) {
        endIfOwned(device,downTime,boundaryTime,false);
    }
    /** The caller distinguishes a validated pressureless CANCEL from ordinary UP. */
    public void endIfOwned(int device, long downTime, long boundaryTime, boolean pressurelessCancel) {
        Stream current=streams.get(device);
        if(current==null || current.downTime!=downTime || boundaryTime<0 ||
           boundaryTime<Math.max(current.startTime,current.time)) return;
        boolean placeholderCancelled=!continuous && pressurelessCancel &&
            current.pressurelessPlaceholder && !current.acceptedSample &&
            boundaryTime==current.startTime;
        current.cancel(continuous);
        current.terminalTime=Math.max(current.terminalTime,boundaryTime);
        current.retiredThroughTime=Math.max(current.retiredThroughTime,boundaryTime);
        current.cancelledPlaceholder=placeholderCancelled;
    }
    public void cancelDevice(int device) {
        Stream current=streams.get(device);if(current!=null)current.cancel(continuous);
    }
    public void cancelAll() { for(Stream current:streams.values())current.cancel(continuous); }
    /** Lifecycle boundaries clear residue/stale samples without turning off physical wheels. */
    public void resetAll() { for(Stream current:streams.values())current.reset(); }

    /** Returns remaining output budget. Old/duplicate samples cannot replay after cancellation. */
    public int sample(int device, long downTime, long time, double dx, double dy,
                      double factorX, double factorY, int remaining, Sink sink) {
        Stream current=stream(device,downTime);
        if (current==null || current.closed || time<current.startTime || time<current.time || time<0 ||
            time<=current.retiredThroughTime) return remaining;
        if (time>current.time) { current.time=time; current.atTime.clear(); }
        if (!current.atTime.add(new Sample(dx,dy))) return remaining;
        current.acceptedSample=true;
        if (continuous) current.downTime=downTime;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(factorX) ||
            !Double.isFinite(factorY) || factorX<=0 || factorY<=0) {
            current.clearRemainder(); return remaining;
        }
        if (factorX!=current.factorX || factorY!=current.factorY) {
            current.clearRemainder(); current.factorX=factorX; current.factorY=factorY;
        }
        double nextX=current.remainderX+dx*120.0/factorX;
        double nextY=current.remainderY-dy*120.0/factorY;
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY) ||
            Math.abs(nextX)>MAX_SAMPLE_UNITS || Math.abs(nextY)>MAX_SAMPLE_UNITS) {
            current.clearRemainder(); return remaining;
        }
        int x=(int)nextX, y=(int)nextY;
        int chunks=Math.max((Math.abs(x)+MAX_CHUNK-1)/MAX_CHUNK,
                            (Math.abs(y)+MAX_CHUNK-1)/MAX_CHUNK);
        if (chunks>remaining) { current.clearRemainder(); return 0; }
        current.remainderX=nextX-x; current.remainderY=nextY-y;
        int previousX=0, previousY=0;
        for(int i=1;i<=chunks;i++) {
            int partX=(int)((long)x*i/chunks), partY=(int)((long)y*i/chunks);
            sink.wheel(partX-previousX,partY-previousY);
            previousX=partX; previousY=partY;
        }
        return remaining-chunks;
    }
}
