package org.mist.systemui.lockscreen.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.util.Log;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;

@SysUISingleton
public class SystemPropertiesWatcher {

    private static final String TAG = "MIST_LOCKSCREEN";
    private static final String ACTION_SETTINGS_CHANGED = "org.mist.systemui.lockscreen.SETTINGS_CHANGED";

    private final Context mContext;
    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SETTINGS_CHANGED.equals(intent.getAction())) {
                Log.d(TAG, "Received settings changed broadcast, restarting SystemUI");
                Process.killProcess(Process.myPid());
            }
        }
    };

    @Inject
    public SystemPropertiesWatcher(Context context) {
        mContext = context;
        registerReceiver();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_SETTINGS_CHANGED);
        mContext.registerReceiver(mSettingsReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "SystemPropertiesWatcher registered for broadcast");
    }

    public void destroy() {
        try {
            mContext.unregisterReceiver(mSettingsReceiver);
            Log.d(TAG, "SystemPropertiesWatcher unregistered");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }
    }
}
