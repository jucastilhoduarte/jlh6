package com.castilhoduarte.jlh6;

import java.util.ArrayList;
import java.util.List;

public class WifiBootCoreTest {
    static int passed = 0, failed = 0;
    static void check(String n, boolean c) {
        if (c) { passed++; System.out.println("  ok   " + n); }
        else { failed++; System.out.println("  FAIL " + n); }
    }

    static final class Rig {
        final FakeClock clock = new FakeClock();
        final VirtualScheduler sched = new VirtualScheduler(clock);
        final FakeWifiControl wifi = new FakeWifiControl();
        final FakeWifiBootStore store = new FakeWifiBootStore();
        final WifiBootCore core = new WifiBootCore(clock, sched, wifi, store);
    }

    public static void main(String[] a) {
        scenarioBounceHappy();
        scenarioSingleFlight();
        scenarioFailsafePastWindow();
        scenarioFailsafeMidWindow();
        scenarioOnStartNeutral();
        scenarioOnlyBootTurnsOff();
        scenarioCrashSafeOrdering();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // #1 — happy bounce: OFF now, ON after WIFI_OFF_MS, timestamp cleared
    static void scenarioBounceHappy() {
        Rig r = new Rig();
        r.core.onBoot();
        check("#1 boot: wifi OFF immediately", !r.wifi.enabled);
        check("#1 boot: reenable_at set", r.store.getReenableAt() == WifiBootCore.WIFI_OFF_MS);
        check("#1 boot: one enable scheduled", r.sched.pending() == 1);
        r.sched.advance(WifiBootCore.WIFI_OFF_MS);
        check("#1 boot: wifi ON after window", r.wifi.enabled);
        check("#1 boot: reenable_at cleared", r.store.getReenableAt() == 0);
    }

    // #2 — single-flight: a duplicate boot signal in the window does not re-disable
    static void scenarioSingleFlight() {
        Rig r = new Rig();
        r.core.onBoot();
        r.core.onBoot();
        check("#2 single-flight: wifi turned off once", r.wifi.offCalls == 1);
        check("#2 single-flight: one enable scheduled", r.sched.pending() == 1);
        r.sched.advance(WifiBootCore.WIFI_OFF_MS);
        check("#2 single-flight: wifi ON after window", r.wifi.enabled);
    }

    // #3 — failsafe: process died mid-window, reborn after window elapsed -> enable now
    static void scenarioFailsafePastWindow() {
        FakeClock clock = new FakeClock();
        VirtualScheduler sched = new VirtualScheduler(clock);
        FakeWifiControl wifi = new FakeWifiControl();
        wifi.enabled = false; // prior process left wifi off
        FakeWifiBootStore store = new FakeWifiBootStore();
        store.setReenableAt(WifiBootCore.WIFI_OFF_MS);
        clock.set(WifiBootCore.WIFI_OFF_MS + 60_000); // now past the window
        WifiBootCore core = new WifiBootCore(clock, sched, wifi, store);
        core.onStart();
        check("#3 failsafe: wifi ON immediately", wifi.enabled);
        check("#3 failsafe: reenable_at cleared", store.getReenableAt() == 0);
        check("#3 failsafe: nothing scheduled", sched.pending() == 0);
    }

    // #4 — reborn mid-window -> reschedule enable for the remaining time
    static void scenarioFailsafeMidWindow() {
        FakeClock clock = new FakeClock();
        VirtualScheduler sched = new VirtualScheduler(clock);
        FakeWifiControl wifi = new FakeWifiControl();
        wifi.enabled = false;
        FakeWifiBootStore store = new FakeWifiBootStore();
        store.setReenableAt(WifiBootCore.WIFI_OFF_MS);
        clock.set(60_000); // reborn 1 min into the window
        WifiBootCore core = new WifiBootCore(clock, sched, wifi, store);
        core.onStart();
        check("#4 mid-window: not yet enabled", !wifi.enabled);
        check("#4 mid-window: enable rescheduled", sched.pending() == 1);
        sched.advance(WifiBootCore.WIFI_OFF_MS - 60_000);
        check("#4 mid-window: wifi ON at window end", wifi.enabled);
        check("#4 mid-window: reenable_at cleared", store.getReenableAt() == 0);
    }

    // #5 — onStart with nothing in flight: wifi untouched
    static void scenarioOnStartNeutral() {
        Rig r = new Rig();
        r.core.onStart();
        check("#5 neutral: wifi untouched (still on)", r.wifi.enabled);
        check("#5 neutral: no off calls", r.wifi.offCalls == 0);
        check("#5 neutral: no on calls", r.wifi.onCalls == 0);
        check("#5 neutral: nothing scheduled", r.sched.pending() == 0);
    }

    // #6 — INV-WIFI: no onStart path ever turns wifi off; it can only enable
    static void scenarioOnlyBootTurnsOff() {
        Rig r = new Rig();
        r.core.onStart();                                  // neutral, no calls
        r.store.setReenableAt(WifiBootCore.WIFI_OFF_MS);
        r.clock.set(WifiBootCore.WIFI_OFF_MS + 1);         // window elapsed
        r.core.onStart();                                  // failsafe -> ON only
        check("#6 only-boot-off: onStart never turned wifi off", r.wifi.offCalls == 0);
        check("#6 only-boot-off: onStart enabled wifi (failsafe)", r.wifi.onCalls >= 1);
    }

    // #7 — INV-WIFI crash-safe ordering regression guard: a shared event log records the
    // relative order of wifi.setEnabled(...) vs store.setReenableAt(...) calls, so a refactor
    // that swaps either load-bearing pair (persist-before-off in onBoot, enable-before-clear
    // in enableNow) fails here even though final state and call counts stay unchanged.
    static void scenarioCrashSafeOrdering() {
        List<String> log = new ArrayList<>();
        FakeClock clock = new FakeClock();
        VirtualScheduler sched = new VirtualScheduler(clock);
        FakeWifiControl wifi = new FakeWifiControl(log);
        FakeWifiBootStore store = new FakeWifiBootStore(log);
        WifiBootCore core = new WifiBootCore(clock, sched, wifi, store);

        core.onBoot();
        int persistIdx = log.indexOf("setReenableAt(" + WifiBootCore.WIFI_OFF_MS + ")");
        int offIdx = log.indexOf("setEnabled(false)");
        check("#7 crash-safe: onBoot persists reenable_at before turning wifi off",
                persistIdx >= 0 && offIdx >= 0 && persistIdx < offIdx);

        sched.advance(WifiBootCore.WIFI_OFF_MS);
        int onIdx = log.lastIndexOf("setEnabled(true)");
        int clearIdx = log.lastIndexOf("setReenableAt(0)");
        check("#7 crash-safe: enableNow enables wifi before clearing reenable_at",
                onIdx >= 0 && clearIdx >= 0 && onIdx < clearIdx);
    }
}
