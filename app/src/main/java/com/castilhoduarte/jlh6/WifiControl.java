package com.castilhoduarte.jlh6;

import android.util.Log;

/**
 * Spike: control wifi tied to the router state, via root telnet.
 *
 * WifiManager.setWifiEnabled is blocked on this ROM for a third-party app — it internally
 * calls getCountryCode() which enforces CONNECTIVITY_INTERNAL (signature|privileged, ungrantable
 * to uid 10004). So we go through the root telnet the app already uses for iptables:
 * `svc wifi enable`/`disable` runs as root (uid 0 = all permissions) and passes the check.
 *
 * Router enable  -> `svc wifi enable`  : radio ON; the car auto-connects to JLStarlink.
 * Router disable -> `svc wifi disable` : radio OFF.
 *
 * Verbose logcat under tag "JLH6Wifi" so each step's exit/output is diagnosable on-device.
 * Not wired into RouterCore (keeps the pure state machine + its tests untouched).
 */
public final class WifiControl {

    private static final String TAG = "JLH6Wifi";
    private static final int CONNECT_MS = 2_000;
    private static final int READ_MS = 6_000;

    private WifiControl() {}

    /** Turn wifi on (car auto-connects to JLStarlink). */
    public static void enableAndConnect() {
        run("svc wifi enable");
    }

    /** Turn wifi off. */
    public static void disable() {
        run("svc wifi disable");
    }

    private static void run(String cmd) {
        try (TelnetRoot t = new TelnetRoot(CONNECT_MS, READ_MS)) {
            TelnetRoot.Result r = t.exec(cmd);
            Log.i(TAG, cmd + " -> exit=" + r.exitCode + " out=[" + r.output + "]");
        } catch (Throwable e) {
            Log.w(TAG, cmd + " failed", e);
        }
    }
}
