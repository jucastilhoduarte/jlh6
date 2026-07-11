package com.castilhoduarte.jlh6;

import android.app.Application;

/**
 * Runs whenever our process is created — including when Android relaunches us on boot
 * to bind the AccessibilityService. Restoring here means the router comes back up no
 * matter which component caused the process to spawn. Idempotent (RouterManager guards
 * against a double ping loop).
 */
public final class JLH6App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RouterManager.get().restoreIfEnabled(getApplicationContext());
    }
}
