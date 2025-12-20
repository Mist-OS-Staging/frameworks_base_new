/*
 * Copyright (C) 2024-2025 Lunaris AOSP
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
package com.android.systemui.statusbar.phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.android.systemui.media.MediaSessionManager;
import com.android.systemui.scrim.ScrimView;
import com.android.systemui.util.ScrimUtils;

public class MediaArtScrimController implements MediaSessionManager.MediaDataListener,
        ScrimUtils.ScrimEventListener {
    
    private static final String TAG = "MediaArtScrimController";
    private static final float BLUR_RADIUS = 130f;
    private static final float MIN_QS_EXPANSION_FOR_MEDIA_ART = 0.15f;
    private static final float MIN_QS_EXPANSION_FOR_REMOVAL = 0.05f;
    private static final int MAX_BITMAP_SIZE = 400;
    
    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final Handler mHandler;
    private final MediaSessionManager mMediaSessionManager;
    
    private ScrimView mNotificationsScrim;
    private ScrimView mScrimBehind;
    
    private boolean mMediaArtScrimEnabled = false;
    private Drawable mCurrentMediaArtwork;
    private boolean mHasActiveMedia = false;
    private RenderEffect mBlurEffect;
    private boolean mListening = false;
    private boolean mIsApplied = false;
    
    private float mOriginalBehindAlpha = -1f;
    private Drawable mOriginalScrimBackground = null;
    private int mOriginalTint = -1;
    private boolean mKeyguardShowing = false;
    private boolean mBouncerShowing = false;
    private boolean mKeyguardGoingAway = false;
    private float mQsExpansion = 0f;
    private float mLastAppliedExpansion = 0f;
    private Bitmap mCurrentBitmap = null;
    
    private Runnable mPendingStateUpdate = null;
    private static final long STATE_UPDATE_DELAY_MS = 30;

    private final ContentObserver mSettingsObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateMediaArtScrimEnabled();
        }
    };
    
    public MediaArtScrimController(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mHandler = new Handler(Looper.getMainLooper());
        
        mMediaSessionManager = MediaSessionManager.Companion.get();
        
        mBlurEffect = RenderEffect.createBlurEffect(
                BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.MIRROR);
        
        mContentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.QS_MEDIA_ART_SCRIM_ENABLED),
                false,
                mSettingsObserver,
                UserHandle.USER_ALL
        );
        
        updateMediaArtScrimEnabled();
    }
    
    public void attachViews(ScrimView notificationsScrim, ScrimView scrimBehind) {
        mNotificationsScrim = notificationsScrim;
        mScrimBehind = scrimBehind;
        saveOriginalScrimState();
    }
    
    private void saveOriginalScrimState() {
        if (mNotificationsScrim != null && mOriginalTint == -1) {
            mOriginalTint = mNotificationsScrim.getTint();
            mOriginalScrimBackground = mNotificationsScrim.getBackground();
        }
        if (mScrimBehind != null && mOriginalBehindAlpha < 0) {
            mOriginalBehindAlpha = mScrimBehind.getViewAlpha();
        }
    }
    
    private void updateMediaArtScrimEnabled() {
        boolean enabled = Settings.System.getIntForUser(
                mContentResolver,
                Settings.System.QS_MEDIA_ART_SCRIM_ENABLED,
                0,
                UserHandle.USER_CURRENT
        ) == 1;
        
        if (mMediaArtScrimEnabled != enabled) {
            mMediaArtScrimEnabled = enabled;
            
            if (enabled && !mListening) {
                mMediaSessionManager.addListener(this);
                ScrimUtils.get().addListener(this);
                mListening = true;
            } else if (!enabled && mListening) {
                mMediaSessionManager.removeListener(this);
                ScrimUtils.get().removeListener(this);
                mListening = false;
                restoreRegularScrim();
            }
            
            scheduleStateUpdate();
        }
    }
    
    private boolean shouldShowMediaArt() {
        if (!mMediaArtScrimEnabled) return false;
        if (mCurrentMediaArtwork == null || !mHasActiveMedia) return false;
        if (mKeyguardShowing) return false;
        if (mBouncerShowing) return false;
        if (mKeyguardGoingAway) return false;
        if (mQsExpansion < MIN_QS_EXPANSION_FOR_MEDIA_ART) return false;
        return true;
    }
    
    private boolean canShowMediaArt() {
        return mMediaArtScrimEnabled 
            && mCurrentMediaArtwork != null 
            && mHasActiveMedia
            && !mKeyguardShowing
            && !mBouncerShowing
            && !mKeyguardGoingAway;
    }
    
    @Override
    public void onAlbumArtChanged(Drawable drawable) {
        mCurrentMediaArtwork = drawable;
        if (mMediaArtScrimEnabled) {
            if (mIsApplied) {
                if (mPendingStateUpdate != null) {
                    mHandler.removeCallbacks(mPendingStateUpdate);
                }
                
                mPendingStateUpdate = () -> {
                    mPendingStateUpdate = null;
                    mIsApplied = false;
                    updateScrimState();
                };

                mHandler.postDelayed(mPendingStateUpdate, 150);
            } else {
                scheduleStateUpdate();
            }
        }
    }
    
    @Override
    public void onPlaybackStateChanged(int state) {
        boolean wasActive = mHasActiveMedia;
        mHasActiveMedia = (state == PlaybackState.STATE_PLAYING);
        
        if (!mHasActiveMedia) {
            mCurrentMediaArtwork = null;
            if (mIsApplied) {
                mHandler.post(() -> {
                    restoreRegularScrimImmediate();
                });
            } else {
                cleanupBitmap();
            }
        }
        
        if (mMediaArtScrimEnabled && wasActive != mHasActiveMedia) {
            scheduleStateUpdate();
        }
    }
    
    @Override
    public void onMediaColorsChanged(int color) {
    }
    
    @Override
    public void onMetadataChanged(String track, String artist) {
    }
    
    public void onPanelExpansionChanged(float expansion) {
        if (!mMediaArtScrimEnabled || mNotificationsScrim == null) {
            return;
        }
        
        float oldExpansion = mQsExpansion;
        mQsExpansion = expansion;
        
        if (!canShowMediaArt() && mIsApplied) {
            restoreRegularScrimImmediate();
            return;
        }
        
        boolean wasAboveThreshold = oldExpansion >= MIN_QS_EXPANSION_FOR_MEDIA_ART;
        boolean isAboveThreshold = expansion >= MIN_QS_EXPANSION_FOR_MEDIA_ART;
        
        if (expansion < MIN_QS_EXPANSION_FOR_REMOVAL && mIsApplied) {
            restoreRegularScrimImmediate();
            return;
        }
        
        if (wasAboveThreshold != isAboveThreshold) {
            if (mPendingStateUpdate != null) {
                mHandler.removeCallbacks(mPendingStateUpdate);
                mPendingStateUpdate = null;
            }
            updateScrimState();
        } else if (mIsApplied && shouldShowMediaArt()) {
            mNotificationsScrim.setAlpha(expansion);
            mLastAppliedExpansion = expansion;
        } else if (mIsApplied && !shouldShowMediaArt()) {
            restoreRegularScrimImmediate();
        }
    }

    private void scheduleStateUpdate() {
        if (mPendingStateUpdate != null) {
            mHandler.removeCallbacks(mPendingStateUpdate);
        }
        
        mPendingStateUpdate = () -> {
            mPendingStateUpdate = null;
            updateScrimState();
        };
        
        mHandler.postDelayed(mPendingStateUpdate, STATE_UPDATE_DELAY_MS);
    }
    
    private void updateScrimState() {
        if (mNotificationsScrim == null) {
            return;
        }
        
        if (shouldShowMediaArt()) {
            applyMediaArt();
        } else {
            restoreRegularScrimImmediate();
        }
    }
    
    private void applyMediaArt() {
        if (!shouldShowMediaArt()) {
            Log.d(TAG, "Skipping media art application - conditions not met");
            if (mIsApplied) {
                restoreRegularScrimImmediate();
            }
            return;
        }
        
        if (mIsApplied) {
            if (Math.abs(mLastAppliedExpansion - mQsExpansion) > 0.01f) {
                mNotificationsScrim.setAlpha(mQsExpansion);
                mLastAppliedExpansion = mQsExpansion;
            }
            return;
        }
        
        if (mOriginalTint == -1) {
            saveOriginalScrimState();
        }
        
        Bitmap bitmap = drawableToBitmap(mCurrentMediaArtwork);
        if (bitmap != null) {
            mNotificationsScrim.setBackground(null);
            
            cleanupBitmap();
            
            mCurrentBitmap = bitmap;
            BitmapDrawable blurredDrawable = new BitmapDrawable(
                    mContext.getResources(), bitmap);
            
            mNotificationsScrim.setMediaArtApplied(true);
            
            mNotificationsScrim.setBackground(blurredDrawable);
            mNotificationsScrim.setRenderEffect(mBlurEffect);
            mNotificationsScrim.setTint(android.graphics.Color.TRANSPARENT);
            mNotificationsScrim.setViewAlpha(mQsExpansion);
            
            mIsApplied = true;
            mLastAppliedExpansion = mQsExpansion;
            
            Log.d(TAG, "Applied media art to notifications scrim");
        } else {
            Log.e(TAG, "Failed to create bitmap from artwork");
        }
    }
    
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                try {
                    return scaleBitmapSafely(bitmap);
                } catch (Exception e) {
                    Log.e(TAG, "Error copying bitmap", e);
                    return null;
                }
            }
        }
        
        try {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            
            if (width <= 0 || height <= 0) {
                width = MAX_BITMAP_SIZE;
                height = MAX_BITMAP_SIZE;
            }
            
            if (width > MAX_BITMAP_SIZE || height > MAX_BITMAP_SIZE) {
                float scale = Math.min(
                    (float) MAX_BITMAP_SIZE / width,
                    (float) MAX_BITMAP_SIZE / height
                );
                width = (int) (width * scale);
                height = (int) (height * scale);
            }
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting drawable to bitmap", e);
            return null;
        }
    }
    
    private Bitmap scaleBitmapSafely(Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        
        int width = source.getWidth();
        int height = source.getHeight();
        
        if (width <= MAX_BITMAP_SIZE && height <= MAX_BITMAP_SIZE) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        
        float scale = Math.min(
            (float) MAX_BITMAP_SIZE / width,
            (float) MAX_BITMAP_SIZE / height
        );
        
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);
        
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(
                source, scaledWidth, scaledHeight, true);
            
            if (scaled == source) {
                return source.copy(Bitmap.Config.ARGB_8888, false);
            }
            
            Log.d(TAG, "Scaled bitmap from " + width + "x" + height 
                + " to " + scaledWidth + "x" + scaledHeight);
            return scaled;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory scaling bitmap", e);
            try {
                int smallWidth = Math.min(width / 2, MAX_BITMAP_SIZE);
                int smallHeight = Math.min(height / 2, MAX_BITMAP_SIZE);
                return Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to create fallback bitmap", ex);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scaling bitmap", e);
            return null;
        }
    }
    
    private void restoreRegularScrimImmediate() {
        if (mNotificationsScrim == null || !mIsApplied) {
            return;
        }
        
        if (mPendingStateUpdate != null) {
            mHandler.removeCallbacks(mPendingStateUpdate);
            mPendingStateUpdate = null;
        }
        
        mNotificationsScrim.setBackground(null);
        mNotificationsScrim.setRenderEffect(null);
        
        mIsApplied = false;
        mLastAppliedExpansion = 0f;
        
        cleanupBitmap();
        
        mOriginalTint = -1;
        mOriginalScrimBackground = null;
        mOriginalBehindAlpha = -1f;
        
        Log.d(TAG, "Restored regular scrim immediately");
    }
    
    private void restoreRegularScrim() {
        restoreRegularScrimImmediate();
    }

    private void cleanupBitmap() {
        if (mCurrentBitmap != null) {
            final int width = mCurrentBitmap.getWidth();
            final int height = mCurrentBitmap.getHeight();
            final boolean wasRecycled = mCurrentBitmap.isRecycled();
            if (!wasRecycled) {
                try {
                    mCurrentBitmap.recycle();
                    Log.d(TAG, "Recycled bitmap: " + width + "x" + height 
                        + " (~" + (width * height * 4 / 1024) + "KB)");
                } catch (Exception e) {
                    Log.e(TAG, "Error recycling bitmap", e);
                }
            } else {
                Log.w(TAG, "Attempted to recycle already recycled bitmap");
            }
            mCurrentBitmap = null;
        }
    }
    
    @Override
    public void onKeyguardShowingChanged(boolean showing) {
        mKeyguardShowing = showing;
        if (showing) {
            mHandler.post(this::restoreRegularScrimImmediate);
        } else {
            scheduleStateUpdate();
        }
    }
    
    @Override
    public void onPrimaryBouncerShowingChanged(boolean showing) {
        mBouncerShowing = showing;
        if (showing) {
            mHandler.post(this::restoreRegularScrimImmediate);
        } else {
            scheduleStateUpdate();
        }
    }
    
    @Override
    public void onKeyguardGoingAwayChanged(boolean goingAway) {
        mKeyguardGoingAway = goingAway;
        if (goingAway) {
            mHandler.post(this::restoreRegularScrimImmediate);
        } else {
            scheduleStateUpdate();
        }
    }
    
    @Override
    public void onKeyguardFadingAwayChanged(boolean fadingAway) {
        if (fadingAway) {
            mHandler.post(this::restoreRegularScrimImmediate);
        }
    }
    
    @Override
    public void onDozingChanged(boolean dozing) {
    }
    
    @Override
    public void setPulsing(boolean pulsing) {
    }
    
    @Override
    public void onExpandedFractionChanged(float expandedFraction) {
    }
    
    @Override
    public void onBarStateChanged(int state) {
    }
    
    @Override
    public void onQsVisibilityChanged(boolean visible) {
        if (!visible) {
            mHandler.post(this::restoreRegularScrimImmediate);
        }
    }
    
    @Override
    public void onScreenTurnedOff() {
        mHandler.post(this::restoreRegularScrimImmediate);
    }
    
    @Override
    public void onStartedWakingUp() {
        scheduleStateUpdate();
    }
    
    public boolean isEnabled() {
        return mMediaArtScrimEnabled;
    }
    
    public boolean isMediaArtApplied() {
        return mIsApplied;
    }
    
    public void destroy() {
        if (mPendingStateUpdate != null) {
            mHandler.removeCallbacks(mPendingStateUpdate);
            mPendingStateUpdate = null;
        }
        
        mContentResolver.unregisterContentObserver(mSettingsObserver);
        if (mListening) {
            mMediaSessionManager.removeListener(this);
            ScrimUtils.get().removeListener(this);
            mListening = false;
        }
        restoreRegularScrim();
        cleanupBitmap();
        mCurrentMediaArtwork = null;
        mOriginalScrimBackground = null;
        mBlurEffect = null;
    }
}
