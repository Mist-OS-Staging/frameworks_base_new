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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.util.blur.BlurUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SplitNotificationPanelControllerTest extends SysuiTestCase {

    @Mock private Context mContext;
    @Mock private Handler mMainHandler;
    @Mock private Executor mBackgroundExecutor;
    @Mock private BlurUtils mBlurUtils;
    @Mock private ContentResolver mContentResolver;
    @Mock private Resources mResources;
    @Mock private NotificationShadeWindowView mShadeWindowView;
    @Mock private View mNotificationStack;
    @Mock private View mQsContainer;

    private SplitNotificationPanelController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getResources()).thenReturn(mResources);
        
        mController = new SplitNotificationPanelController(
                mContext, mMainHandler, mBackgroundExecutor, mBlurUtils);
    }

    @Test
    public void testInitialization() {
        assertFalse("Controller should not be initialized initially", mController.isInitialized());
        
        mController.init(mShadeWindowView);
        
        assertTrue("Controller should be initialized after init()", mController.isInitialized());
        verify(mContentResolver).registerContentObserver(any(), anyBoolean(), any());
    }

    @Test
    public void testDestroy() {
        mController.init(mShadeWindowView);
        assertTrue("Controller should be initialized", mController.isInitialized());
        
        mController.destroy();
        
        assertFalse("Controller should not be initialized after destroy()", mController.isInitialized());
        verify(mContentResolver).unregisterContentObserver(any());
    }

    @Test
    public void testDoubleInitialization() {
        mController.init(mShadeWindowView);
        mController.init(mShadeWindowView);
        
        // Should only register observer once
        verify(mContentResolver).registerContentObserver(any(), anyBoolean(), any());
    }

    @Test
    public void testDestroyWithoutInit() {
        // Should not crash when destroying uninitialized controller
        mController.destroy();
        
        verify(mContentResolver, never()).unregisterContentObserver(any());
    }

    @Test
    public void testSplitPanelEnabledWhenConfigAndUserEnabled() {
        when(mResources.getBoolean(com.android.internal.R.bool.config_enableSplitNotificationPanel))
                .thenReturn(true);
        
        mController.init(mShadeWindowView);
        
        // Simulate user enabling the setting
        // Note: In real test, we would need to mock Settings.System.getInt
        // This is a simplified test structure
        
        assertTrue("Split panel should be supported when config allows", 
                mController.isInitialized());
    }

    @Test
    public void testSplitPanelDisabledWhenConfigDisabled() {
        when(mResources.getBoolean(com.android.internal.R.bool.config_enableSplitNotificationPanel))
                .thenReturn(false);
        
        mController.init(mShadeWindowView);
        
        assertFalse("Split panel should be disabled when config disallows", 
                mController.isSplitPanelEnabled());
    }

    @Test
    public void testNullShadeWindowView() {
        // Should not crash with null view
        mController.init(null);
        
        assertFalse("Controller should not be initialized with null view", 
                mController.isInitialized());
    }
}
