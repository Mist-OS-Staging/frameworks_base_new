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
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.qs.QSFragment;

import javax.inject.Inject;

/**
 * Controller for managing split notification panel functionality
 * Separates notifications (left) and quick settings (right) similar to OneUI/OxygenOS
 */
@SysUISingleton
public class SplitNotificationPanelController {
    private static final String SPLIT_NOTIFICATION_PANEL_SETTING = "split_notification_panel";
    
    private final Context mContext;
    private final Handler mHandler;
    private final ContentResolver mContentResolver;
    
    private boolean mSplitPanelEnabled = false;
    private NotificationShadeWindowView mShadeWindowView;
    private NotificationStackScrollLayout mNotificationStackScrollLayout;
    private QSFragment mQSFragment;
    
    private final ContentObserver mSettingsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSplitPanelState();
        }
    };

    @Inject
    public SplitNotificationPanelController(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mContentResolver = context.getContentResolver();
    }
    
    public void init(NotificationShadeWindowView shadeWindowView) {
        mShadeWindowView = shadeWindowView;
        
        // Register settings observer
        mContentResolver.registerContentObserver(
                Settings.System.getUriFor(SPLIT_NOTIFICATION_PANEL_SETTING),
                false, mSettingsObserver);
        
        updateSplitPanelState();
    }
    
    public void destroy() {
        mContentResolver.unregisterContentObserver(mSettingsObserver);
    }
    
    private void updateSplitPanelState() {
        boolean configEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enableSplitNotificationPanel);
        boolean userEnabled = Settings.System.getInt(mContentResolver, 
                SPLIT_NOTIFICATION_PANEL_SETTING, 0) == 1;
        
        boolean enabled = configEnabled && userEnabled;
        
        if (mSplitPanelEnabled != enabled) {
            mSplitPanelEnabled = enabled;
            applySplitPanelLayout();
        }
    }
    
    private void applySplitPanelLayout() {
        if (mShadeWindowView == null) return;
        
        if (mSplitPanelEnabled) {
            enableSplitLayout();
        } else {
            disableSplitLayout();
        }
    }
    
    private void enableSplitLayout() {
        // Find the notification stack and QS container
        View notificationStack = mShadeWindowView.findViewById(
                com.android.systemui.R.id.notification_stack_scroller);
        View qsContainer = mShadeWindowView.findViewById(
                com.android.systemui.R.id.qs_frame);
        
        if (notificationStack != null && qsContainer != null) {
            ViewGroup parent = (ViewGroup) notificationStack.getParent();
            
            // Get split ratio from configuration
            float notificationRatio = mContext.getResources().getFraction(
                    com.android.internal.R.fraction.config_splitNotificationPanelRatio, 1, 1);
            float qsRatio = 1.0f - notificationRatio;
            
            // Create horizontal container for split layout
            LinearLayout splitContainer = new LinearLayout(mContext);
            splitContainer.setOrientation(LinearLayout.HORIZONTAL);
            splitContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Remove views from current parent
            parent.removeView(notificationStack);
            parent.removeView(qsContainer);
            
            // Set up left side (notifications)
            LinearLayout.LayoutParams notificationParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, notificationRatio);
            notificationParams.setMarginEnd(8); // Add margin between panels
            notificationStack.setLayoutParams(notificationParams);
            
            // Set up right side (quick settings)
            LinearLayout.LayoutParams qsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, qsRatio);
            qsParams.setMarginStart(8); // Add margin between panels
            qsContainer.setLayoutParams(qsParams);
            
            // Add views to split container
            splitContainer.addView(notificationStack);
            splitContainer.addView(qsContainer);
            
            // Add split container to parent
            parent.addView(splitContainer);
            
            // Store reference for later restoration
            splitContainer.setTag("split_notification_container");
        }
    }
    
    private void disableSplitLayout() {
        // Find and remove split container, restore original layout
        View splitContainer = mShadeWindowView.findViewWithTag("split_notification_container");
        
        if (splitContainer instanceof LinearLayout) {
            LinearLayout container = (LinearLayout) splitContainer;
            ViewGroup parent = (ViewGroup) container.getParent();
            
            // Get the notification stack and QS container
            View notificationStack = container.getChildAt(0);
            View qsContainer = container.getChildAt(1);
            
            // Remove from split container
            container.removeAllViews();
            parent.removeView(container);
            
            // Restore original layout params
            notificationStack.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            qsContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            
            // Add back to parent in original order
            parent.addView(qsContainer);
            parent.addView(notificationStack);
        }
    }
    
    public boolean isSplitPanelEnabled() {
        return mSplitPanelEnabled;
    }
}
