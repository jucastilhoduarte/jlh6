package com.castilhoduarte.jlh6;

import java.io.IOException;

/**
 * One-shot Broadcom coexistence fix for the CarPlay brick.
 *
 * The single-radio chip (BCM4359) runs 2.4GHz STA (wlan0 → Starlink) and 5GHz SoftAP
 * (wlan2 → hotspot) concurrently. rsdb_mode ships as "1 auto": the firmware may drop
 * RSDB → MCC on its own and force the 5GHz AP off its channel (seen: 165 → 157) while a
 * client is associated, killing the AP at L2 (hostapd keeps lying "ENABLED", só o toggle
 * do hotspot conserta). Pinning rsdb_mode to a bare "1" (no "auto") stops the firmware
 * from ever falling back to MCC, so the AP never gets yanked.
 *
 * Not persistent — firmware resets to "1 auto" every reboot — so this runs on every
 * process birth (boot anchors: {@link JLH6App}, {@link BootReceiver},
 * {@link RouterAccessibilityService}). Idempotent: only bounces the radio (wl down/up)
 * while it still reads "auto"; once pinned it just verifies and returns, so reopening the
 * app mid-session never bounces wifi. Independent of the Starlink router.
 *
 * Reaches wl via the same root telnet as the router; the daemon is on loopback, so
 * wl down/up doesn't drop the telnet connection (confirmed on-device).
 */
final class WifiCoexFix {

    // Disabled for now — flip to re-enable the rsdb_mode pin fix.
    private static final boolean ENABLED = false;

    private static final int CONNECT_MS = 2_000;
    private static final int READ_MS = 12_000;
    private static final int ATTEMPTS = 24;
    private static final long RETRY_MS = 5_000L;

    // Wait until the 5GHz AP is up (chanspec reports a channel), then pin rsdb_mode. rsdb is
    // verified into $ok (exit 0 only once rsdb is a bare "1", pinned) — captured rather than
    // left as the trailing status so chanspec 165 can be re-asserted once more right after a
    // confirmed pin, belt-and-braces on belt-and-braces, without that second call's own exit
    // code overriding the real result. If the AP isn't up yet the command exits 1 so run()
    // retries. chanspec 165 is inert on a live AP (proved on-device) but harmless either way.
    private static final String CMD = String.join("; ",
            "c=$(wl -i wlan2 chanspec 2>/dev/null)",
            "case \"$c\" in *0x*) ;; *) exit 1;; esac",
            "if wl rsdb_mode 2>/dev/null | grep -q auto; then wl down && wl rsdb_mode 1 && wl up && sleep 3; fi",
            "wl -i wlan2 chanspec 165 >/dev/null 2>&1",
            "wl rsdb_mode | grep -qw 1 && ! wl rsdb_mode | grep -q auto; ok=$?",
            "wl -i wlan2 chanspec 165 >/dev/null 2>&1",
            "exit $ok");

    private WifiCoexFix() {}

    /** Fire-and-forget on a dedicated daemon thread; safe to call on every process birth. */
    static void apply() {
        if (!ENABLED) return;
        Thread t = new Thread(WifiCoexFix::run, "WifiCoexFix");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        for (int i = 0; i < ATTEMPTS; i++) {
            try (TelnetRoot telnet = new TelnetRoot(CONNECT_MS, READ_MS)) {
                if (telnet.exec(CMD).ok()) return;
            } catch (IOException | RuntimeException ignored) {
                // wifi/telnet not ready yet — fall through to retry
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
