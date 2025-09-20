package org.mist.systemui.lockscreen;

import android.content.Context;
import android.util.Log;
import org.mist.systemui.lockscreen.type.bigboom.BigBoomClockController;
import org.mist.systemui.lockscreen.util.CustomLockscreenSettings;
import org.mist.systemui.lockscreen.type.smallcuteclock.SmallCuteClockController;
import org.mist.systemui.lockscreen.type.easterncharacters.EasternCharactersClockController;
import org.mist.systemui.lockscreen.type.texttime.TextTimeClockController;
import org.mist.systemui.lockscreen.type.thinlongclock.ThinLongClockController;
import org.mist.systemui.lockscreen.type.moremorethin.MoreMoreThinClockController;
import org.mist.systemui.lockscreen.type.normaltime.NormalTimeClockController;
import org.mist.systemui.lockscreen.type.runrunclock.RunrunClockController;
import org.mist.systemui.lockscreen.type.classicclock.ClassicClockController;

public class CustomLockScreenClockFactory {

    private static final String TAG = "MIST_LOCKSCREEN";

    public static ICustomLockScreenClock create(Context context) {
        if (!CustomLockscreenSettings.isEnabled()) {
            return null;
        }

        int clockType = CustomLockscreenSettings.getClockType();

        switch (clockType) {
            case 1:
                return new BigBoomClockController();
            case 2:
                return new SmallCuteClockController();
            case 3:
                return new EasternCharactersClockController();
            case 4:
                return new TextTimeClockController();
            case 5:
                return new ThinLongClockController();
            case 6:
                return new MoreMoreThinClockController();
            case 8:
                return new NormalTimeClockController();
            case 9:
                return new RunrunClockController();
            case 10:
                return new ClassicClockController();
            default:
                Log.w(TAG, "Unknown clock type: " + clockType);
                return null;
        }
    }
}
