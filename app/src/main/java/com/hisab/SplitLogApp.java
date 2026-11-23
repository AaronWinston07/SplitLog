package com.hisab;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import utils.AppLockManager;

/** Locks the app on a fresh start and after being away longer than GRACE_MS. */
public class SplitLogApp extends Application implements Application.ActivityLifecycleCallbacks {

    private static final long GRACE_MS = 30_000L; // change this to lock sooner or later

    private int startedCount = 0;
    private long backgroundAt = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLockManager.setLocked(AppLockManager.isEnabled(this)); // fresh start = locked
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        boolean enabled = AppLockManager.isEnabled(this);

        // Coming back from the background after a while?
        if (startedCount == 0 && backgroundAt != 0 && enabled
                && System.currentTimeMillis() - backgroundAt > GRACE_MS) {
            AppLockManager.setLocked(true);
        }
        startedCount++;

        if (enabled && AppLockManager.isLocked()
                && !(activity instanceof LockActivity)
                && !AppLockManager.isLockScreenShowing()) {
            AppLockManager.setLockScreenShowing(true);
            Intent intent = new Intent(activity, LockActivity.class);
            intent.putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_UNLOCK);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedCount = Math.max(0, startedCount - 1);
        if (startedCount == 0) backgroundAt = System.currentTimeMillis();
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { }
    @Override public void onActivityResumed(Activity a) { }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
    @Override public void onActivityDestroyed(Activity a) { }
}