package com.castilhoduarte.jlh6;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Android adapter for {@link WifiBootCore}. Singleton, own HandlerThread, real WifiManager
 * and SharedPreferences. Fully independent of RouterManager. Primary mechanism is
 * WifiManager.setWifiEnabled (the exact call the Settings toggle makes; on this Android 9 /
 * targetSdk-28 unit a normal app with CHANGE_WIFI_STATE may call it, and the general toggle
 * spares the wlan2 hotspot / CarPlay). If the ROM blocks it, see the telnet fallback in the
 * plan's on-device verification task.
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
                            new WifiManagerControl(app),
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

    private static final class WifiManagerControl implements WifiControl {
        private final WifiManager wm;
        WifiManagerControl(Context app) {
            wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        }
        @Override public void setEnabled(boolean on) {
            try { if (wm != null) wm.setWifiEnabled(on); } catch (Throwable ignored) {}
        }
    }

    private static final class SharedPrefsWifiBootStore implements WifiBootStore {
        private final SharedPreferences p;
        SharedPrefsWifiBootStore(Context app) { p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
        @Override public long getReenableAt() { return p.getLong(KEY_REENABLE_AT, 0L); }
        @Override public void setReenableAt(long v) { p.edit().putLong(KEY_REENABLE_AT, v).commit(); }
    }
}
