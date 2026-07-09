package com.castilhoduarte.jlh6;

import android.util.Log;

/**
 * {@link WifiControl} via root telnet: `svc wifi enable/disable`.
 *
 * WifiManager.setWifiEnabled is blocked on this ROM for a third-party app — it internally
 * calls getCountryCode() which enforces CONNECTIVITY_INTERNAL (signature|privileged,
 * ungrantable to a normal uid; confirmed in logcat). The root shell (uid 0) passes, so we
 * reuse the telnet the app already uses for iptables. Verbose logcat under tag "JLH6Wifi".
 */
public final class TelnetWifiControl implements WifiControl {

    private static final String TAG = "JLH6Wifi";
    private static final int CONNECT_MS = 2_000;
    private static final int READ_MS = 6_000;

    @Override
    public void setEnabled(boolean on) {
        String cmd = "svc wifi " + (on ? "enable" : "disable");
        try (TelnetRoot t = new TelnetRoot(CONNECT_MS, READ_MS)) {
            TelnetRoot.Result r = t.exec(cmd);
            Log.i(TAG, cmd + " -> exit=" + r.exitCode + " out=[" + r.output + "]");
        } catch (Throwable e) {
            Log.w(TAG, cmd + " failed", e);
        }
    }
}
