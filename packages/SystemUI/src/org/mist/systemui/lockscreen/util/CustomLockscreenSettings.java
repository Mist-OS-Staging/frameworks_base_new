package org.mist.systemui.lockscreen.util;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

public class CustomLockscreenSettings {

    private static final String TAG = "MIST_LOCKSCREEN";

    private static final String KEY_ENABLED = "custom_lockscreen_enable";
    private static final String KEY_TYPE = "custom_lockscreen_type";
    private static final String KEY_COLOR = "custom_lockscreen_color";
    private static final String KEY_HOUR_COLOR = "custom_lockscreen_hour_color";
    private static final String KEY_MINUTE_COLOR = "custom_lockscreen_minute_color";

    public static boolean isEnabled(Context context) {
        int enabled = Settings.System.getInt(context.getContentResolver(), 
                KEY_ENABLED, 0);
        Log.d(TAG, "Custom lockscreen enabled: " + (enabled == 1));
        return enabled == 1;
    }

    public static int getClockType(Context context) {
        int type = Settings.System.getInt(context.getContentResolver(), 
                KEY_TYPE, 0);
        Log.d(TAG, "Clock type: " + type);
        return type;
    }

    @Deprecated
    public static String getClockColor(Context context) {
        String color = Settings.System.getString(context.getContentResolver(), 
                KEY_COLOR);
        if (color == null || color.isEmpty()) {
            color = "white";
        }
        Log.d(TAG, "Clock color (deprecated): " + color);
        return color;
    }

    public static String getHourColor(Context context) {
        String hourColor = Settings.System.getString(context.getContentResolver(), 
                KEY_HOUR_COLOR);
        if (hourColor == null || hourColor.isEmpty()) {
            // Fallback to old color key
            hourColor = getClockColor(context);
        }
        Log.d(TAG, "Hour color: " + hourColor);
        return hourColor;
    }

    public static String getMinuteColor(Context context) {
        String minuteColor = Settings.System.getString(context.getContentResolver(), 
                KEY_MINUTE_COLOR);
        if (minuteColor == null || minuteColor.isEmpty()) {
            // Fallback to old color key
            minuteColor = getClockColor(context);
        }
        Log.d(TAG, "Minute color: " + minuteColor);
        return minuteColor;
    }

    public static boolean hasSeparateHourMinuteColors(Context context) {
        String hourColor = Settings.System.getString(context.getContentResolver(), 
                KEY_HOUR_COLOR);
        String minuteColor = Settings.System.getString(context.getContentResolver(), 
                KEY_MINUTE_COLOR);
        boolean hasSeparate = (hourColor != null && !hourColor.isEmpty()) || 
                             (minuteColor != null && !minuteColor.isEmpty());
        Log.d(TAG, "Has separate hour/minute colors: " + hasSeparate);
        return hasSeparate;
    }

    // Helper methods to set values (if needed from SystemUI)
    public static void setEnabled(Context context, boolean enabled) {
        Settings.System.putInt(context.getContentResolver(), KEY_ENABLED, enabled ? 1 : 0);
    }

    public static void setClockType(Context context, int type) {
        Settings.System.putInt(context.getContentResolver(), KEY_TYPE, type);
    }

    public static void setHourColor(Context context, String color) {
        Settings.System.putString(context.getContentResolver(), KEY_HOUR_COLOR, color);
    }

    public static void setMinuteColor(Context context, String color) {
        Settings.System.putString(context.getContentResolver(), KEY_MINUTE_COLOR, color);
    }
}
