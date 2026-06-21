package com.castilhoduarte.jlh6;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollState = new Runnable() {
        @Override public void run() {
            updateRouterButton();
            if (RouterManager.get().getState() == RouterManager.State.STARTING) {
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.router_button).setOnClickListener(v -> onRouterTap());
        findViewById(R.id.settings_button).setOnClickListener(v -> openAndroidSettings());

        updateRouterButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(pollState);
        updateRouterButton();
        if (RouterManager.get().getState() == RouterManager.State.STARTING) {
            mainHandler.post(pollState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(pollState);
    }

    private void onRouterTap() {
        RouterManager mgr = RouterManager.get();
        if (mgr.getState() == RouterManager.State.DISABLED) {
            mgr.enable(this);
            mainHandler.post(pollState);
        } else {
            mainHandler.removeCallbacks(pollState);
            mgr.disable(this);
        }
        updateRouterButton();
    }

    private void updateRouterButton() {
        TextView tv = findViewById(R.id.router_status);
        switch (RouterManager.get().getState()) {
            case STARTING: tv.setText(R.string.router_starting); break;
            case ACTIVE:   tv.setText(R.string.router_active);   break;
            default:       tv.setText(R.string.router_disabled); break;
        }
    }

    private void openAndroidSettings() {
        Intent explicit = new Intent(Intent.ACTION_MAIN);
        explicit.setComponent(new ComponentName(
                "com.android.settings", "com.android.settings.Settings"));
        explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(explicit);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
