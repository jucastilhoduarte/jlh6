package com.castilhoduarte.jlh6;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

/**
 * Not used to inspect the UI — it exists purely as an autostart/keep-alive anchor.
 * The user enables it once in Android's Accessibility settings; from then on Android
 * relaunches our process on every boot and binds this service, firing
 * {@link #onServiceConnected()}, where we restore the router if it was left on.
 */
public final class RouterAccessibilityService extends AccessibilityService {

    /** True if this service is currently enabled in Settings (persists across reboots). */
    public static boolean isEnabled(Context ctx) {
        String flat = Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName mine = new ComponentName(ctx, RouterAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(flat);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (mine.equals(cn)) return true;
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        RouterManager.get().restoreIfEnabled(getApplicationContext());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
