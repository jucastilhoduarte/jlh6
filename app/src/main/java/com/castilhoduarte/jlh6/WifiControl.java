package com.castilhoduarte.jlh6;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.List;

/**
 * Spike: control wifi tied to the router state so we can test on the car whether the app
 * can actually toggle wifi and associate to JLStarlink.
 *
 * Router enable  -> enableAndConnect(): wifi ON + connect to JLStarlink (ensure state).
 * Router disable -> disable(): wifi OFF.
 *
 * Verbose logcat under tag "JLH6Wifi" so each step's result is diagnosable on-device.
 * Not wired into RouterCore (keeps the pure state machine + its tests untouched).
 */
public final class WifiControl {

    private static final String TAG = "JLH6Wifi";
    private static final String SSID = "JLStarlink";
    private static final String QUOTED_SSID = "\"" + SSID + "\"";

    private WifiControl() {}

    /** Turn wifi on and connect to JLStarlink. */
    public static void enableAndConnect(Context ctx) {
        WifiManager wm = wifi(ctx);
        if (wm == null) return;
        try {
            if (wm.isWifiEnabled()) {
                Log.i(TAG, "wifi already enabled");
            } else {
                boolean ok = wm.setWifiEnabled(true);
                Log.i(TAG, "setWifiEnabled(true) -> " + ok);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setWifiEnabled(true) threw", t);
        }
        connectToJLStarlink(wm);
    }

    /** Turn wifi off. */
    public static void disable(Context ctx) {
        WifiManager wm = wifi(ctx);
        if (wm == null) return;
        try {
            boolean ok = wm.setWifiEnabled(false);
            Log.i(TAG, "setWifiEnabled(false) -> " + ok);
        } catch (Throwable t) {
            Log.w(TAG, "setWifiEnabled(false) threw", t);
        }
    }

    private static void connectToJLStarlink(WifiManager wm) {
        try {
            List<WifiConfiguration> nets = wm.getConfiguredNetworks();
            if (nets == null) {
                Log.w(TAG, "getConfiguredNetworks() null (no permission / not privileged); "
                        + "relying on the car's auto-connect");
                return;
            }
            Log.i(TAG, "configured networks visible: " + nets.size());
            for (WifiConfiguration c : nets) {
                if (QUOTED_SSID.equals(c.SSID) || SSID.equals(c.SSID)) {
                    Log.i(TAG, "found JLStarlink netId=" + c.networkId + " -> connecting");
                    boolean en = wm.enableNetwork(c.networkId, true); // disableOthers = true
                    boolean rc = wm.reconnect();
                    Log.i(TAG, "enableNetwork -> " + en + ", reconnect -> " + rc);
                    return;
                }
            }
            Log.w(TAG, "JLStarlink not among configured networks; relying on auto-connect");
        } catch (Throwable t) {
            Log.w(TAG, "connectToJLStarlink threw", t);
        }
    }

    private static WifiManager wifi(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm == null) Log.w(TAG, "no WifiManager");
        return wm;
    }
}
