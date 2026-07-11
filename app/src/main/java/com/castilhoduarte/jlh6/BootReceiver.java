package com.castilhoduarte.jlh6;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reinforcement trigger for autostart — the primary anchor is the AccessibilityService,
 * which already relaunches the process on boot. This covers reboot + app-update cases.
 * The process stays alive because the AccessibilityService is bound; we restore directly
 * (no background service, which API 26+ would refuse to start from here anyway).
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                RouterManager.get().restoreIfEnabled(context.getApplicationContext());
                break;
            default:
                break;
        }
    }
}
