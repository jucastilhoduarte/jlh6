package com.castilhoduarte.jlh6;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Android adapter for {@link WifiBootCore}. Singleton, own HandlerThread, SharedPreferences.
 * Fully independent of RouterManager / the router — the router never touches wifi; only this
 * boot bounce does. Wifi is toggled via {@link TelnetWifiControl} (`svc wifi` over root
 * telnet) because WifiManager.setWifiEnabled is blocked on this ROM for a third-party app.
 */
public final class WifiBootManager {

    private static final String PREFS = "wifiboot";
    private static final String KEY_REENABLE_AT = "reenable_at";

    private static volatile WifiBootManager instance;

    public static WifiBootManager get() {
        if (instance == null) {
            synchronized (WifiBootManager.class) {
                if (instance == null) instance = new WifiBootManager();
            }
        }
        return instance;
    }

    private final Handler bg;
    private volatile WifiBootCore core;

    private WifiBootManager() {
        HandlerThread t = new HandlerThread("WifiBootManager");
        t.start();
        bg = new Handler(t.getLooper());
    }

    private WifiBootCore core(Context ctx) {
        if (core == null) {
            synchronized (this) {
                if (core == null) {
                    Context app = ctx.getApplicationContext();
                    core = new WifiBootCore(
                            System::currentTimeMillis,
                            new HandlerScheduler(bg),
                            new TelnetWifiControl(),
                            new SharedPrefsWifiBootStore(app));
                }
            }
        }
        return core;
    }

    public void onBoot(Context ctx) { WifiBootCore c = core(ctx); bg.post(c::onBoot); }
    public void onStart(Context ctx) { WifiBootCore c = core(ctx); bg.post(c::onStart); }

    private static final class HandlerScheduler implements Scheduler {
        private final Handler h;
        HandlerScheduler(Handler h) { this.h = h; }
        @Override public void post(Runnable r) { h.post(r); }
        @Override public void postDelayed(Runnable r, long delayMs) { h.postDelayed(r, delayMs); }
        @Override public void removeAll() { h.removeCallbacksAndMessages(null); }
        @Override public void sleep(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static final class SharedPrefsWifiBootStore implements WifiBootStore {
        private final SharedPreferences p;
        SharedPrefsWifiBootStore(Context app) { p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
        @Override public long getReenableAt() { return p.getLong(KEY_REENABLE_AT, 0L); }
        @Override public void setReenableAt(long v) { p.edit().putLong(KEY_REENABLE_AT, v).commit(); }
    }
}
