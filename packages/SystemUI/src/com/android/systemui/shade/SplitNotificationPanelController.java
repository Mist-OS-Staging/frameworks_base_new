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
            // Use hardcoded 60/40 split ratio
            float notificationRatio = 0.6f;
            float qsRatio = 0.4f;
            
            // Create split container with blur background
            SplitNotificationContainer splitContainer = new SplitNotificationContainer(mContext, mBlurUtils);
            splitContainer.setOrientation(LinearLayout.HORIZONTAL);
            splitContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            splitContainer.setTag(SPLIT_CONTAINER_TAG);
            
            // Store original layout params
            ViewGroup.LayoutParams originalNotificationParams = notificationStack.getLayoutParams();
            ViewGroup.LayoutParams originalQsParams = qsContainer.getLayoutParams();
            
            // Remove views from parent
            parent.removeView(notificationStack);
            parent.removeView(qsContainer);
            
            // Set up notification panel (left side)
            LinearLayout.LayoutParams notificationParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, notificationRatio);
            int margin = (int) (4 * mContext.getResources().getDisplayMetrics().density); // 4dp
            notificationParams.setMarginEnd(margin);
            notificationStack.setLayoutParams(notificationParams);
            
            // Set up QS panel (right side)
            LinearLayout.LayoutParams qsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, qsRatio);
            qsParams.setMarginStart(margin);
            qsContainer.setLayoutParams(qsParams);
            
            // Add to split container
            splitContainer.addView(notificationStack);
            splitContainer.addView(qsContainer);
            
            // Store original params in a simple way - use the container's tag to store both params
            splitContainer.setTag(new ViewGroup.LayoutParams[]{originalNotificationParams, originalQsParams});
            
            // Add to parent
            parent.addView(splitContainer);
            
            Log.d(TAG, "Split layout enabled successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error enabling split layout", e);
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
            
            // Get original layout params from stored array
            ViewGroup.LayoutParams originalNotificationParams = null;
            ViewGroup.LayoutParams originalQsParams = null;
            
            Object tag = container.getTag();
            if (tag instanceof ViewGroup.LayoutParams[]) {
                ViewGroup.LayoutParams[] params = (ViewGroup.LayoutParams[]) tag;
                if (params.length >= 2) {
                    originalNotificationParams = params[0];
                    originalQsParams = params[1];
                }
            }
            
            // Remove from split container
            container.removeAllViews();
            parent.removeView(container);
            
            // Restore original layout params
            if (originalNotificationParams != null) {
                notificationStack.setLayoutParams(originalNotificationParams);
            }
            if (originalQsParams != null) {
                qsContainer.setLayoutParams(originalQsParams);
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
