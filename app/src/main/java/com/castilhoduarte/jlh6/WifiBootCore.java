package com.castilhoduarte.jlh6;

/**
 * Cold-start wifi bounce: on car boot, turn wifi OFF, wait WIFI_OFF_MS, turn it back ON.
 * Keeps wlan0 disassociated during Starlink's unstable cold-start window so the
 * signal-acquisition event never disrupts wireless CarPlay on wlan2.
 *
 * Fully independent of the router (RouterCore). Timestamp-based failsafe survives a
 * process death mid-window: any process start re-checks the store and re-enables wifi if
 * the window already elapsed. INV-WIFI: the ONLY path that turns wifi off is onBoot();
 * every other transition can only turn it on. Never leaves wifi persistently off.
 */
public final class WifiBootCore {

    static final long WIFI_OFF_MS = 3 * 60 * 1000L; // 3 min

    private final Clock clock;
    private final Scheduler scheduler;
    private final WifiControl wifi;
    private final WifiBootStore store;

    public WifiBootCore(Clock clock, Scheduler scheduler, WifiControl wifi, WifiBootStore store) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.wifi = wifi;
        this.store = store;
    }

    /** Car boot: the only entry point that starts a bounce (turns wifi off). */
    public void onBoot() {
        long now = clock.nowMs();
        if (store.getReenableAt() > now) return;   // bounce already in flight (single-flight)
        long reenableAt = now + WIFI_OFF_MS;
        store.setReenableAt(reenableAt);            // persist BEFORE turning off (crash-safe)
        wifi.setEnabled(false);
        scheduler.postDelayed(this::enableNow, WIFI_OFF_MS);
    }

    /** Any process start: failsafe only. Never turns wifi off; never starts a bounce. */
    public void onStart() {
        long reenableAt = store.getReenableAt();
        if (reenableAt == 0) return;                // nothing in flight
        long now = clock.nowMs();
        if (now >= reenableAt) {
            enableNow();                            // window elapsed (missed callback): recover
        } else {
            scheduler.postDelayed(this::enableNow, reenableAt - now); // reborn mid-window
        }
    }

    private void enableNow() {
        wifi.setEnabled(true);                      // enable BEFORE clearing (crash-safe)
        store.setReenableAt(0);
    }
}
