/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Performance monitoring utility for split notification panel operations
 */
@SysUISingleton
public class SplitNotificationPanelPerformanceMonitor {
    private static final String TAG = "SplitPanelPerf";
    private static final long PERFORMANCE_THRESHOLD_MS = 16; // One frame at 60fps
    
    private long mLayoutStartTime;
    private long mTouchStartTime;
    private int mLayoutOperationCount = 0;
    private int mSlowLayoutCount = 0;

    @Inject
    public SplitNotificationPanelPerformanceMonitor() {}
    
    /**
     * Start timing a layout operation
     */
    public void startLayoutTiming() {
        mLayoutStartTime = SystemClock.elapsedRealtime();
    }
    
    /**
     * End timing a layout operation and log if it's slow
     */
    public void endLayoutTiming(@NonNull String operation) {
        if (mLayoutStartTime == 0) return;
        
        long duration = SystemClock.elapsedRealtime() - mLayoutStartTime;
        mLayoutOperationCount++;
        
        if (duration > PERFORMANCE_THRESHOLD_MS) {
            mSlowLayoutCount++;
            Log.w(TAG, "Slow layout operation: " + operation + " took " + duration + "ms");
        }
        
        mLayoutStartTime = 0;
    }
    
    /**
     * Start timing a touch operation
     */
    public void startTouchTiming() {
        mTouchStartTime = SystemClock.elapsedRealtime();
    }
    
    /**
     * End timing a touch operation
     */
    public void endTouchTiming(@NonNull String operation) {
        if (mTouchStartTime == 0) return;
        
        long duration = SystemClock.elapsedRealtime() - mTouchStartTime;
        
        if (duration > PERFORMANCE_THRESHOLD_MS) {
            Log.w(TAG, "Slow touch operation: " + operation + " took " + duration + "ms");
        }
        
        mTouchStartTime = 0;
    }
    
    /**
     * Get performance statistics
     */
    public void logPerformanceStats() {
        if (mLayoutOperationCount > 0) {
            float slowPercentage = (float) mSlowLayoutCount / mLayoutOperationCount * 100;
            Log.i(TAG, "Performance stats - Total layouts: " + mLayoutOperationCount + 
                    ", Slow layouts: " + mSlowLayoutCount + " (" + slowPercentage + "%)");
        }
    }
    
    /**
     * Reset performance counters
     */
    public void reset() {
        mLayoutOperationCount = 0;
        mSlowLayoutCount = 0;
        mLayoutStartTime = 0;
        mTouchStartTime = 0;
    }
}
