package org.mist.systemui.lockscreen.di;

import org.mist.systemui.lockscreen.CustomLockscreenClockManager;
import org.mist.systemui.lockscreen.NativeLockscreenViewHider;
import org.mist.systemui.lockscreen.util.SystemPropertiesWatcher;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import android.content.Context;
import com.android.systemui.dagger.SysUISingleton;
import org.mist.systemui.lockscreen.sections.CustomClockSection;
import org.mist.systemui.lockscreen.CustomLockscreenRepository;

@Module
public class MistLockscreenModule {

    @Provides
    @SysUISingleton
    public NativeLockscreenViewHider provideNativeLockscreenViewHider(Context context) {
        return new NativeLockscreenViewHider(context);
    }
    
    @Provides
    @SysUISingleton
    public CustomLockscreenClockManager provideCustomLockscreenClockManager(
            Context context, 
            NativeLockscreenViewHider nativeLockscreenViewHider,
            SystemPropertiesWatcher propertiesWatcher
    ) {
        return new CustomLockscreenClockManager(context, nativeLockscreenViewHider, propertiesWatcher);
    }

    @Provides
    @SysUISingleton
    public CustomClockSection provideCustomClockSection(CustomLockscreenClockManager manager) {
        return new CustomClockSection(manager);
    }

    @Provides
    @SysUISingleton
    public CustomLockscreenRepository provideCustomLockscreenRepository(Context context) {
        return new CustomLockscreenRepository(context);
    }
    
    @Provides
    @SysUISingleton
    public SystemPropertiesWatcher provideSystemPropertiesWatcher(Context context) {
        return new SystemPropertiesWatcher(context);
    }
}
