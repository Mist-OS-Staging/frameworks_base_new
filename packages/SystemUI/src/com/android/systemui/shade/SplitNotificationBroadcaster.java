package com.android.systemui.shade;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SplitNotificationBroadcaster {
    private static final String TAG = "SplitNotificationBroadcaster";
    private static final String ACTION_SPLIT_PANEL_STATE = "com.android.systemui.SPLIT_PANEL_STATE";
    private static final String EXTRA_ENABLED = "enabled";
    
    public static void notifyLauncher(Context context, boolean enabled) {
        try {
            Intent intent = new Intent(ACTION_SPLIT_PANEL_STATE);
            intent.putExtra(EXTRA_ENABLED, enabled);
            intent.setPackage("com.android.launcher3");
            context.sendBroadcast(intent);
            Log.d(TAG, "Broadcast sent to launcher: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send broadcast", e);
        }
    }
}
