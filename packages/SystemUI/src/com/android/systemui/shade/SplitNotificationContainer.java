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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.statusbar.BlurUtils;

/**
 * Container for split notification panel with system-level blur support
 */
public class SplitNotificationContainer extends LinearLayout {
    private static final String TAG = "SplitNotificationContainer";
    private static final float BLUR_RADIUS = 25f;
    private static final float PANEL_ALPHA = 0.85f;
    
    private final BlurUtils mBlurUtils;
    private final Paint mBlurPaint;
    private final Paint mDividerPaint;
    
    private boolean mBlurEnabled = true;
    private float mTouchStartX;
    private boolean mIsTouchingLeftPanel;
    private int mDividerWidth;
    private int mPanelCornerRadius;

    public SplitNotificationContainer(@NonNull Context context, @NonNull BlurUtils blurUtils) {
        super(context);
        mBlurUtils = blurUtils;
        mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        init();
    }

    public SplitNotificationContainer(@NonNull Context context, @Nullable AttributeSet attrs, 
            @NonNull BlurUtils blurUtils) {
        super(context, attrs);
        mBlurUtils = blurUtils;
        mBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        
        // Use hardcoded dimensions
        mDividerWidth = (int) (8 * getResources().getDisplayMetrics().density); // 8dp
        mPanelCornerRadius = (int) (16 * getResources().getDisplayMetrics().density); // 16dp
        
        // Setup blur if supported
        setupBlur();
        
        // Setup divider paint with hardcoded color
        mDividerPaint.setColor(0x1A000000); // Semi-transparent black
        mDividerPaint.setStrokeWidth(2f);
        
        Log.d(TAG, "SplitNotificationContainer initialized with blur: " + mBlurEnabled);
    }
    
    private void setupBlur() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mBlurUtils.supportsBlursOnWindows()) {
                mBlurEnabled = true;
                
                // Create blur effect
                RenderEffect blurEffect = RenderEffect.createBlurEffect(
                        BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP);
                
                // Apply blur to background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRenderEffect(blurEffect);
                }
                
                // Set semi-transparent background
                setAlpha(PANEL_ALPHA);
                
                Log.d(TAG, "System blur enabled");
            } else {
                mBlurEnabled = false;
                Log.d(TAG, "System blur not supported, using fallback");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up blur", e);
            mBlurEnabled = false;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        
        if (getChildCount() >= 2) {
            drawPanelBackgrounds(canvas);
            drawDivider(canvas);
        }
    }
    
    private void drawPanelBackgrounds(@NonNull Canvas canvas) {
        try {
            View leftPanel = getChildAt(0);
            View rightPanel = getChildAt(1);
            
            if (leftPanel == null || rightPanel == null) return;
            
            // Draw left panel background (notifications) - light gray
            mBlurPaint.setColor(0xFFF5F5F5);
            
            canvas.drawRoundRect(
                    leftPanel.getLeft(), 
                    leftPanel.getTop(),
                    leftPanel.getRight(), 
                    leftPanel.getBottom(),
                    mPanelCornerRadius, 
                    mPanelCornerRadius, 
                    mBlurPaint);
            
            // Draw right panel background (quick settings) - slightly darker gray
            mBlurPaint.setColor(0xFFEEEEEE);
            
            canvas.drawRoundRect(
                    rightPanel.getLeft(), 
                    rightPanel.getTop(),
                    rightPanel.getRight(), 
                    rightPanel.getBottom(),
                    mPanelCornerRadius, 
                    mPanelCornerRadius, 
                    mBlurPaint);
                    
        } catch (Exception e) {
            Log.e(TAG, "Error drawing panel backgrounds", e);
        }
    }
    
    private void drawDivider(@NonNull Canvas canvas) {
        try {
            if (getChildCount() < 2) return;
            
            View leftPanel = getChildAt(0);
            View rightPanel = getChildAt(1);
            
            if (leftPanel == null || rightPanel == null) return;
            
            // Draw vertical divider between panels
            float dividerX = leftPanel.getRight() + (rightPanel.getLeft() - leftPanel.getRight()) / 2f;
            
            canvas.drawLine(
                    dividerX, 
                    getPaddingTop(),
                    dividerX, 
                    getHeight() - getPaddingBottom(),
                    mDividerPaint);
                    
        } catch (Exception e) {
            Log.e(TAG, "Error drawing divider", e);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        try {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchStartX = ev.getX();
                    // Determine which panel is being touched (60/40 split)
                    mIsTouchingLeftPanel = mTouchStartX < getWidth() * 0.6f;
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in touch intercept", e);
        }
        
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        try {
            // Route touch events to appropriate panel
            if (getChildCount() >= 2) {
                View targetPanel = mIsTouchingLeftPanel ? getChildAt(0) : getChildAt(1);
                
                if (targetPanel != null) {
                    // Translate touch coordinates to target panel's coordinate system
                    MotionEvent translatedEvent = MotionEvent.obtain(event);
                    translatedEvent.offsetLocation(-targetPanel.getLeft(), -targetPanel.getTop());
                    
                    boolean handled = targetPanel.dispatchTouchEvent(translatedEvent);
                    translatedEvent.recycle();
                    
                    if (handled) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in touch handling", e);
        }
        
        return super.onTouchEvent(event);
    }
    
    @Override
    protected void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        try {
            // Refresh blur settings on configuration change
            setupBlur();
            
            // Update colors for theme changes - use hardcoded colors
            mDividerPaint.setColor(0x1A000000); // Semi-transparent black
            
            invalidate();
            
            Log.d(TAG, "Configuration changed, refreshed blur and colors");
        } catch (Exception e) {
            Log.e(TAG, "Error handling configuration change", e);
        }
    }
    
    /**
     * Enable or disable blur effect
     */
    public void setBlurEnabled(boolean enabled) {
        if (mBlurEnabled != enabled) {
            mBlurEnabled = enabled;
            
            if (enabled) {
                setupBlur();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRenderEffect(null);
                }
                setAlpha(1.0f);
            }
            
            invalidate();
            Log.d(TAG, "Blur enabled: " + enabled);
        }
    }
    
    /**
     * Check if blur is currently enabled
     */
    public boolean isBlurEnabled() {
        return mBlurEnabled;
    }
    
    /**
     * Update blur radius dynamically
     */
    public void setBlurRadius(float radius) {
        try {
            if (mBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RenderEffect blurEffect = RenderEffect.createBlurEffect(
                        radius, radius, Shader.TileMode.CLAMP);
                setRenderEffect(blurEffect);
                
                Log.d(TAG, "Blur radius updated to: " + radius);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating blur radius", e);
        }
    }
}
