package com.castilhoduarte.jlh6;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Not used to inspect the UI — it exists purely as an autostart/keep-alive anchor.
 * When enabled (self-granted via {@link RouterManager#ensureAccessibilityAnchor}),
 * Android relaunches our process on every boot and binds this service, firing
 * {@link #onServiceConnected()}, where we restore the router if it was left on.
 */
public final class RouterAccessibilityService extends AccessibilityService {

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
