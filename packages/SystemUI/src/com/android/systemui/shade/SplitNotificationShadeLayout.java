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
import android.content.res.Configuration;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Custom layout for split notification panel
 * Handles touch events and layout for split notification/quick settings
 */
public class SplitNotificationShadeLayout extends LinearLayout {
    private static final String SPLIT_NOTIFICATION_PANEL_SETTING = "split_notification_panel";
    
    private boolean mSplitPanelEnabled = false;
    private View mNotificationPanel;
    private View mQuickSettingsPanel;
    private float mTouchStartX;
    private boolean mIsTouchingNotificationSide;

    public SplitNotificationShadeLayout(Context context) {
        super(context);
        init();
    }

    public SplitNotificationShadeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SplitNotificationShadeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        updateSplitPanelState();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        // Find child views
        if (getChildCount() >= 2) {
            mNotificationPanel = getChildAt(0);
            mQuickSettingsPanel = getChildAt(1);
        }
        
        updateLayout();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSplitPanelState();
        updateLayout();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mSplitPanelEnabled) {
            return super.onInterceptTouchEvent(ev);
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchStartX = ev.getX();
                mIsTouchingNotificationSide = mTouchStartX < getWidth() * 0.6f;
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mSplitPanelEnabled) {
            return super.onTouchEvent(event);
        }

        // Route touch events to appropriate panel
        if (mIsTouchingNotificationSide && mNotificationPanel != null) {
            return mNotificationPanel.dispatchTouchEvent(event);
        } else if (!mIsTouchingNotificationSide && mQuickSettingsPanel != null) {
            return mQuickSettingsPanel.dispatchTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    private void updateSplitPanelState() {
        boolean enabled = Settings.System.getInt(getContext().getContentResolver(),
                SPLIT_NOTIFICATION_PANEL_SETTING, 0) == 1;
        
        if (mSplitPanelEnabled != enabled) {
            mSplitPanelEnabled = enabled;
            updateLayout();
        }
    }

    private void updateLayout() {
        if (mNotificationPanel == null || mQuickSettingsPanel == null) {
            return;
        }

        if (mSplitPanelEnabled) {
            // Split layout: notifications 60%, quick settings 40%
            setOrientation(HORIZONTAL);
            
            LayoutParams notificationParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.6f);
            notificationParams.setMarginEnd(dpToPx(8));
            mNotificationPanel.setLayoutParams(notificationParams);
            
            LayoutParams qsParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.4f);
            qsParams.setMarginStart(dpToPx(8));
            mQuickSettingsPanel.setLayoutParams(qsParams);
            
            // Ensure proper visibility
            mNotificationPanel.setVisibility(VISIBLE);
            mQuickSettingsPanel.setVisibility(VISIBLE);
        } else {
            // Standard layout: vertical stacking
            setOrientation(VERTICAL);
            
            LayoutParams notificationParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            mNotificationPanel.setLayoutParams(notificationParams);
            
            LayoutParams qsParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            mQuickSettingsPanel.setLayoutParams(qsParams);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    public boolean isSplitPanelEnabled() {
        return mSplitPanelEnabled;
    }

    public void setSplitPanelEnabled(boolean enabled) {
        if (mSplitPanelEnabled != enabled) {
            mSplitPanelEnabled = enabled;
            updateLayout();
        }
    }
}
