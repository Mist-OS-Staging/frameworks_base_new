/*
 * Copyright (C) 2024 MistOS
 */

package com.android.internal.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.os.UserHandle;

public class SwitchStyleHelper {
    
    private static final String SWITCH_STYLE = "switch_style";
    private static final String SWITCH_PNG_ENABLED = "switch_png_enabled";
    
    public static Drawable getSwitchTrackDrawable(Context context) {
        if (isPngEnabled(context)) {
            return getPngDrawable(context, "track");
        }
        return getStyledDrawable(context, "switch_track_material");
    }
    
    public static Drawable getSwitchThumbDrawable(Context context) {
        if (isPngEnabled(context)) {
            return getPngDrawable(context, "thumb");
        }
        return getStyledDrawable(context, "switch_thumb_material");
    }
    
    private static boolean isPngEnabled(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                SWITCH_PNG_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
    }
    
    private static Drawable getPngDrawable(Context context, String type) {
        // PNG loading logic
        return null; // Fallback to default
    }
    
    private static Drawable getStyledDrawable(Context context, String name) {
        try {
            Resources res = context.getResources();
            int resId = res.getIdentifier(name, "drawable", "android");
            return res.getDrawable(resId, context.getTheme());
        } catch (Exception e) {
            return null;
        }
    }
}
