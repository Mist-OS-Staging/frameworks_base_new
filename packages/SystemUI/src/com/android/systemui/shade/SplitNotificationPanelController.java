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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.BlurUtils;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Production-ready controller for split notification panel functionality
 */
@SysUISingleton
public class SplitNotificationPanelController {
    private static final String TAG = "SplitNotificationPanelController";
    private static final String SPLIT_NOTIFICATION_PANEL_SETTING = "split_notification_panel";
    private static final String SPLIT_CONTAINER_TAG = "split_notification_container";
    private static final int DEBOUNCE_DELAY_MS = 100;
    
    private final Context mContext;
    private final Handler mMainHandler;
    private final Executor mBackgroundExecutor;
    private final ContentResolver mContentResolver;
    private final BlurUtils mBlurUtils;
    
    private boolean mSplitPanelEnabled = false;
    private boolean mIsInitialized = false;
    private NotificationShadeWindowView mShadeWindowView;
    private final Object mLock = new Object();
    private Runnable mPendingLayoutUpdate;
    private ContentObserver mSettingsObserver;

    @Inject
    public SplitNotificationPanelController(
            Context context, 
            @Main Handler mainHandler,
            @Background Executor backgroundExecutor,
            BlurUtils blurUtils) {
        mContext = context;
        mMainHandler = mainHandler;
        mBackgroundExecutor = backgroundExecutor;
        mContentResolver = context.getContentResolver();
        mBlurUtils = blurUtils;
        
        mSettingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                debounceLayoutUpdate();
            }
        };
    }
    
    public void init(@NonNull NotificationShadeWindowView shadeWindowView) {
        synchronized (mLock) {
            if (mIsInitialized) {
                Log.w(TAG, "Already initialized");
                return;
            }
            
            mShadeWindowView = shadeWindowView;
            
            try {
                // Ensure QS panel has correct initial height
                ensureQsPanelHeight();
                
                mContentResolver.registerContentObserver(
                        Settings.System.getUriFor(SPLIT_NOTIFICATION_PANEL_SETTING),
                        false, mSettingsObserver);
                
                mIsInitialized = true;
                updateSplitPanelState();
                
                Log.d(TAG, "Split notification panel controller initialized");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize split panel controller", e);
            }
        }
    }
    
    private void ensureQsPanelHeight() {
        try {
            View qsContainer = mShadeWindowView.findViewById(
                    mContext.getResources().getIdentifier("qs_frame", "id", "com.android.systemui"));
            
            if (qsContainer != null) {
                // Reset QS panel to proper height
                ViewGroup.LayoutParams params = qsContainer.getLayoutParams();
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    qsContainer.setLayoutParams(params);
                }
                
                // Reset padding and margins
                qsContainer.setPadding(0, 0, 0, 0);
                
                Log.d(TAG, "QS panel height reset to normal");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset QS panel height", e);
        }
    }
    
    public void destroy() {
        synchronized (mLock) {
            if (!mIsInitialized) return;
            
            try {
                mContentResolver.unregisterContentObserver(mSettingsObserver);
                
                if (mPendingLayoutUpdate != null) {
                    mMainHandler.removeCallbacks(mPendingLayoutUpdate);
                    mPendingLayoutUpdate = null;
                }
                
                mShadeWindowView = null;
                mIsInitialized = false;
                
                Log.d(TAG, "Split notification panel controller destroyed");
            } catch (Exception e) {
                Log.e(TAG, "Error during destroy", e);
            }
        }
    }
    
    private void debounceLayoutUpdate() {
        synchronized (mLock) {
            if (mPendingLayoutUpdate != null) {
                mMainHandler.removeCallbacks(mPendingLayoutUpdate);
            }
            
            mPendingLayoutUpdate = this::updateSplitPanelState;
            mMainHandler.postDelayed(mPendingLayoutUpdate, DEBOUNCE_DELAY_MS);
        }
    }
    
    private void updateSplitPanelState() {
        mBackgroundExecutor.execute(() -> {
            try {
                // Always enable split panel if user setting is enabled
                boolean userEnabled = Settings.System.getInt(mContentResolver, 
                        SPLIT_NOTIFICATION_PANEL_SETTING, 0) == 1;
                
                mMainHandler.post(() -> {
                    synchronized (mLock) {
                        if (mSplitPanelEnabled != userEnabled) {
                            mSplitPanelEnabled = userEnabled;
                            applySplitPanelLayout();
                            SplitNotificationBroadcaster.notifyLauncher(mContext, userEnabled);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating split panel state", e);
            }
        });
    }
    
    private void applySplitPanelLayout() {
        if (!mIsInitialized || mShadeWindowView == null) {
            Log.w(TAG, "Cannot apply layout - not initialized or view is null");
            return;
        }
        
        try {
            if (mSplitPanelEnabled) {
                enableSplitLayout();
            } else {
                disableSplitLayout();
            }
            Log.d(TAG, "Split panel layout applied: " + mSplitPanelEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply split panel layout", e);
        }
    }
    
    private void enableSplitLayout() {
        View notificationStack = mShadeWindowView.findViewById(
                mContext.getResources().getIdentifier("notification_stack_scroller", "id", "com.android.systemui"));
        View qsContainer = mShadeWindowView.findViewById(
                mContext.getResources().getIdentifier("qs_frame", "id", "com.android.systemui"));
        
        if (notificationStack == null || qsContainer == null) {
            Log.e(TAG, "Required views not found for split layout");
            return;
        }
        
        ViewGroup parent = (ViewGroup) notificationStack.getParent();
        if (parent == null) {
            Log.e(TAG, "Parent view not found");
            return;
        }
        
        // Check if already in split mode
        if (mShadeWindowView.findViewWithTag(SPLIT_CONTAINER_TAG) != null) {
            Log.d(TAG, "Already in split mode");
            return;
        }
        
        try {
            // Get screen dimensions for finite constraints
            int screenWidth = mContext.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = mContext.getResources().getDisplayMetrics().heightPixels;
            
            // Create split container with finite dimensions
            SplitNotificationContainer splitContainer = new SplitNotificationContainer(mContext, mBlurUtils);
            splitContainer.setOrientation(LinearLayout.HORIZONTAL);
            splitContainer.setLayoutParams(new ViewGroup.LayoutParams(screenWidth, screenHeight));
            splitContainer.setTag(SPLIT_CONTAINER_TAG);
            
            // Remove views from original parent
            parent.removeView(notificationStack);
            parent.removeView(qsContainer);
            
            // Create finite layout params for split panels
            int leftWidth = (int) (screenWidth * 0.6f);
            int rightWidth = screenWidth - leftWidth;
            
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                    leftWidth, screenHeight);
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                    rightWidth, screenHeight);
            
            // Add views to split container
            splitContainer.addView(notificationStack, leftParams);
            splitContainer.addView(qsContainer, rightParams);
            
            // Add split container to parent with finite constraints
            ViewGroup.LayoutParams containerParams = new ViewGroup.LayoutParams(screenWidth, screenHeight);
            parent.addView(splitContainer, containerParams);
            
            Log.d(TAG, "Split layout enabled with finite constraints: " + screenWidth + "x" + screenHeight);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling split layout", e);
            // Fallback: disable split mode if it fails
            mSplitPanelEnabled = false;
            Settings.System.putInt(mContentResolver, SPLIT_NOTIFICATION_PANEL_SETTING, 0);
        }
    }
    
    private void disableSplitLayout() {
        View splitContainer = mShadeWindowView.findViewWithTag(SPLIT_CONTAINER_TAG);
        
        if (!(splitContainer instanceof LinearLayout)) {
            Log.d(TAG, "No split container found to disable");
            return;
        }
        
        try {
            LinearLayout container = (LinearLayout) splitContainer;
            ViewGroup parent = (ViewGroup) container.getParent();
            
            if (parent == null || container.getChildCount() < 2) {
                Log.e(TAG, "Invalid container state for restoration");
                return;
            }
            
            View notificationStack = container.getChildAt(0);
            View qsContainer = container.getChildAt(1);
            
            // Remove from split container
            container.removeAllViews();
            parent.removeView(container);
            
            // Restore original layout params with proper QS height
            ViewGroup.LayoutParams notificationParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            
            ViewGroup.LayoutParams qsParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            
            notificationStack.setLayoutParams(notificationParams);
            qsContainer.setLayoutParams(qsParams);
            
            // Reset any padding/margins that might affect height
            qsContainer.setPadding(0, 0, 0, 0);
            if (qsContainer instanceof ViewGroup) {
                ViewGroup qsGroup = (ViewGroup) qsContainer;
                ViewGroup.MarginLayoutParams marginParams = 
                    (ViewGroup.MarginLayoutParams) qsGroup.getLayoutParams();
                if (marginParams != null) {
                    marginParams.topMargin = 0;
                    marginParams.bottomMargin = 0;
                }
            }
            
            // Add back to parent in original order
            parent.addView(qsContainer);
            parent.addView(notificationStack);
            
            Log.d(TAG, "Split layout disabled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error disabling split layout", e);
        }
    }
    
    public boolean isSplitPanelEnabled() {
        synchronized (mLock) {
            return mSplitPanelEnabled && mIsInitialized;
        }
    }
    
    public boolean isInitialized() {
        synchronized (mLock) {
            return mIsInitialized;
        }
    }
}
