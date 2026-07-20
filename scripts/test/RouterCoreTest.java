package com.castilhoduarte.jlh6;

public class RouterCoreTest {
    static int passed = 0, failed = 0;
    static void check(String n, boolean c) {
        if (c) { passed++; System.out.println("  ok   " + n); }
        else { failed++; System.out.println("  FAIL " + n); }
    }

    static final class Rig {
        final FakeClock clock = new FakeClock();
        final VirtualScheduler sched = new VirtualScheduler(clock);
        final KernelShell kernel = new KernelShell();
        final FakeStore store = new FakeStore();
        final RouterCore core = new RouterCore(clock, sched, kernel, store);
    }

    public static void main(String[] a) {
        scenarioBasicActivation();
        scenarioActivationTimeout();
        scenarioInterfaceLate();
        scenarioRecovery();
        scenarioIntermittent();
        scenarioManualDisable();
        scenarioRebootDuringStarting();
        scenarioRebootWhileActive();
        scenarioStalePersistedState();
        scenarioIdempotency();
        scenarioConcurrentActivation();
        scenarioRapidToggle();
        scenarioRecoveryLoopProtection();
        scenarioPartialApplyTimeout();
        scenarioCommandStrings();
        scenarioOneOkActivation();
        scenarioFailThenOk();
        scenarioAutostartGateOff();
        scenarioAutostartGateOn();
        scenarioAutostartStickyOnDisable();
        scenarioCarPlayLocalPreserve();
        scenarioDnsRedirect();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // #1
    static void scenarioBasicActivation() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#1 activation: ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        check("#1 activation: fully applied (INV1)", r.kernel.fullyApplied());
        check("#1 activation: store enabled", r.store.isEnabled());
    }

    // #2 — activation timeout (ping never succeeds): give up clean, no phantom
    static void scenarioActivationTimeout() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(false); // ping always fails -> apply never runs
        r.core.enable();
        r.sched.advance(0);
        check("#2 timeout: STARTING initially", r.core.getState() == RouterCore.State.STARTING);
        r.sched.advance(700_000); // well past the 10-min timeout (600_000ms)
        check("#2 timeout: DISABLED", r.core.getState() == RouterCore.State.DISABLED);
        check("#2 timeout: enabled cleared", !r.store.isEnabled());
        check("#2 timeout: no phantom rules (INV2)", r.kernel.clean());
    }

    // #3 — WLAN/uplink unavailable then available
    static void scenarioInterfaceLate() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.kernel.setInterfacePresent("wlan0", false); // ping -I wlan0 fails: iface absent
        r.core.enable();
        r.sched.advance(0);
        check("#3 iface: STARTING while down", r.core.getState() == RouterCore.State.STARTING);
        check("#3 iface: clean while down (INV2)", r.kernel.clean());
        r.sched.advance(5_000);
        check("#3 iface: still STARTING", r.core.getState() == RouterCore.State.STARTING);
        r.kernel.setInterfacePresent("wlan0", true); // WLAN appears
        r.sched.advance(30_000);
        check("#3 iface: ACTIVE after up", r.core.getState() == RouterCore.State.ACTIVE);
        check("#3 iface: applied (INV1)", r.kernel.fullyApplied());
    }

    // #4 — connectivity recovery: 6 consecutive fails -> purge -> reactivate
    static void scenarioRecovery() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#4 recovery: ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        r.kernel.setUplinkUp(false);
        r.sched.advance(25_000); // monitor fails 1-5
        check("#4 recovery: ACTIVE after 5 fails", r.core.getState() == RouterCore.State.ACTIVE);
        r.sched.advance(5_000); // fail 6 -> recover(): purge + STARTING + schedule reactivate
        check("#4 recovery: STARTING after 6 fails", r.core.getState() == RouterCore.State.STARTING);
        check("#4 recovery: purged during recover (INV2)", r.kernel.clean());
        r.kernel.setUplinkUp(true);
        r.sched.advance(40_000); // reactivate (5s wait) -> doPing x6 -> apply -> ACTIVE
        check("#4 recovery: back ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        check("#4 recovery: reapplied (INV1)", r.kernel.fullyApplied());
    }

    // #5 — intermittent: 1 fail then ok, no purge
    static void scenarioIntermittent() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#5 intermittent: ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        r.kernel.setUplinkUp(false);
        r.sched.advance(5_000); // fail 1
        r.kernel.setUplinkUp(true);
        r.sched.advance(5_000); // ok -> counter resets
        check("#5 intermittent: stays ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        check("#5 intermittent: rules untouched (no purge)", r.kernel.fullyApplied());
    }

    // #6 — manual disable guardrail
    static void scenarioManualDisable() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#6 disable: ACTIVE first", r.core.getState() == RouterCore.State.ACTIVE);
        r.core.disable();
        r.sched.advance(0); // purge task runs
        check("#6 disable: DISABLED", r.core.getState() == RouterCore.State.DISABLED);
        check("#6 disable: purged (INV2)", r.kernel.clean());
        check("#6 disable: enabled cleared", !r.store.isEnabled());
        r.sched.advance(60_000); // ensure monitor does NOT continue
        check("#6 disable: no background loop", r.sched.pending() == 0);
        check("#6 disable: stays DISABLED", r.core.getState() == RouterCore.State.DISABLED);
    }

    // #7 — reboot during STARTING: store persists enabled, kernel fresh
    static void scenarioRebootDuringStarting() {
        Rig r = new Rig();
        r.store.setAutostart(true);
        r.kernel.setUplinkUp(false); // never activates -> stays STARTING
        r.core.enable();
        r.sched.advance(0);
        check("#7 reboot-starting: STARTING before reboot", r.core.getState() == RouterCore.State.STARTING);
        // reboot: keep store (enabled=true persisted), everything else fresh + clean kernel
        FakeClock c2 = new FakeClock();
        VirtualScheduler s2 = new VirtualScheduler(c2);
        KernelShell k2 = new KernelShell();
        k2.setUplinkUp(false);
        RouterCore core2 = new RouterCore(c2, s2, k2, r.store);
        core2.restoreIfEnabled();
        s2.advance(0);
        check("#7 reboot-starting: STARTING again", core2.getState() == RouterCore.State.STARTING);
        check("#7 reboot-starting: clean (INV2)", k2.clean());
        k2.setUplinkUp(true);
        s2.advance(30_000);
        check("#7 reboot-starting: ACTIVE when up", core2.getState() == RouterCore.State.ACTIVE);
    }

    // #8 — reboot while ACTIVE: never blind-trust persisted state
    static void scenarioRebootWhileActive() {
        Rig r = new Rig();
        r.store.setAutostart(true);
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#8 reboot-active: ACTIVE before reboot", r.core.getState() == RouterCore.State.ACTIVE);
        // reboot: netfilter cleared -> fresh clean kernel; store persists enabled=true
        FakeClock c2 = new FakeClock();
        VirtualScheduler s2 = new VirtualScheduler(c2);
        KernelShell k2 = new KernelShell();
        k2.setUplinkUp(true);
        RouterCore core2 = new RouterCore(c2, s2, k2, r.store);
        core2.restoreIfEnabled();
        check("#8 reboot-active: STARTING first (no blind ACTIVE)", core2.getState() == RouterCore.State.STARTING);
        check("#8 reboot-active: clean before re-ping (INV2)", k2.clean());
        s2.advance(30_000);
        check("#8 reboot-active: ACTIVE after re-ping+apply", core2.getState() == RouterCore.State.ACTIVE);
        check("#8 reboot-active: reapplied (INV1)", k2.fullyApplied());
    }

    // #9 — stale persisted state: enabled=true but no rules -> repair
    static void scenarioStalePersistedState() {
        FakeClock clock = new FakeClock();
        VirtualScheduler sched = new VirtualScheduler(clock);
        KernelShell kernel = new KernelShell();
        kernel.setUplinkUp(true); // clean kernel, no JLH6 rules
        FakeStore store = new FakeStore();
        store.setEnabled(true); // stale: claims enabled, reality has no rules
        store.setAutostart(true);
        RouterCore core = new RouterCore(clock, sched, kernel, store);
        core.restoreIfEnabled();
        check("#9 stale: STARTING (not blind ACTIVE)", core.getState() == RouterCore.State.STARTING);
        sched.advance(30_000);
        check("#9 stale: repaired to ACTIVE", core.getState() == RouterCore.State.ACTIVE);
        check("#9 stale: rules applied (INV1)", kernel.fullyApplied());
    }

    // #10 + #11 — purge/apply idempotency, exercising the real command strings
    static void scenarioIdempotency() {
        KernelShell k = new KernelShell();
        // apply once
        k.exec(RouterCore.applyCmd());
        check("#11 apply once: fully applied", k.fullyApplied());
        // apply again -> no duplicates
        k.exec(RouterCore.applyCmd());
        check("#11 apply twice: still 2/3/2 (no dup)",
                k.ipRuleCount() == 2 && k.natCount() == 3 && k.forwardCount() == 2);
        // purge with rules present
        check("#10 purge present: exit 0", k.exec(RouterCore.purgeCmd()).ok());
        check("#10 purge present: clean", k.clean());
        // purge again with no rules -> still safe/clean
        check("#10 purge absent: exit 0", k.exec(RouterCore.purgeCmd()).ok());
        check("#10 purge absent: still clean", k.clean());
    }

    // #13 — duplicate/concurrent activation: single-flight guard
    static void scenarioConcurrentActivation() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(false); // stay STARTING to observe scheduling
        r.core.enable();
        check("#13 concurrent: one task posted", r.sched.pending() == 1);
        r.core.enable(); // second rapid enable
        check("#13 concurrent: still one task (guard)", r.sched.pending() == 1);
    }

    // #14 — rapid toggle enable->disable->enable: latest intent wins
    static void scenarioRapidToggle() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();   // posts doPing
        r.core.disable();  // removeAll cancels doPing; posts purge; PURGING
        r.core.enable();   // re-posts doPing; STARTING
        r.sched.advance(30_000); // purge task then doPing x6 both drain in order
        check("#14 toggle: final ACTIVE (latest intent)", r.core.getState() == RouterCore.State.ACTIVE);
        check("#14 toggle: enabled persisted", r.store.isEnabled());
        check("#14 toggle: applied (INV1)", r.kernel.fullyApplied());
    }

    // #15 — recovery loop protection: bounded, single-flight, disable stops it
    static void scenarioRecoveryLoopProtection() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(30_000);
        r.kernel.setUplinkUp(false); // permanent failure
        for (int i = 0; i < 3; i++) r.sched.advance(60_000);
        check("#15 loop: bounded pending (<=2, no overlap)", r.sched.pending() <= 2);
        r.core.disable();
        r.sched.advance(0);
        check("#15 loop: disable stops loop", r.core.getState() == RouterCore.State.DISABLED);
        check("#15 loop: no pending after disable", r.sched.pending() == 0);
        check("#15 loop: clean after disable (INV2)", r.kernel.clean());
        r.sched.advance(120_000);
        check("#15 loop: stays DISABLED", r.core.getState() == RouterCore.State.DISABLED
                && r.sched.pending() == 0);
    }

    // #12 — partial apply that can never verify, driven to timeout: no phantom rules
    static void scenarioPartialApplyTimeout() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);           // ping ok -> apply runs
        r.kernel.setFailIpForwardWrite(true); // verify can never pass (ip_forward stuck 0)
        r.core.enable();
        r.sched.advance(0);
        check("#12 partial: not ACTIVE", r.core.getState() != RouterCore.State.ACTIVE);
        check("#12 partial: STARTING (retrying)", r.core.getState() == RouterCore.State.STARTING);
        r.sched.advance(700_000);             // drive past 10-min timeout
        check("#12 partial-timeout: DISABLED", r.core.getState() == RouterCore.State.DISABLED);
        check("#12 partial-timeout: NO phantom rules (INV2)", r.kernel.clean());
    }

    // #16 — command-string regression guard
    static void scenarioCommandStrings() {
        String ap = RouterCore.applyCmd();
        check("#16 apply: ip rule prio 17999", ap.contains("priority 17999"));
        check("#16 apply: masquerade", ap.contains("POSTROUTING") && ap.contains("MASQUERADE"));
        check("#16 apply: fwd wlan2->wlan0", ap.contains("-i wlan2 -o wlan0 -j ACCEPT"));
        check("#16 apply: established back-path", ap.contains("RELATED,ESTABLISHED"));
        check("#16 apply: ip_forward", ap.contains("/proc/sys/net/ipv4/ip_forward"));
        check("#16 apply: self-verify tail (&&)", ap.contains("&& iptables -C FORWARD"));
        String pu = RouterCore.purgeCmd();
        check("#16 purge: negated verify", pu.contains("! ip rule") && pu.contains("! iptables"));
        check("#16 purge: deletes nat", pu.contains("-t nat -D POSTROUTING"));
    }

    // #17 — one OK ping activates (ONLINE_OK_THRESHOLD=1): no multi-ping settle window;
    // apply + ACTIVE on the first verified ping. INV1 still holds.
    static void scenarioOneOkActivation() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.core.enable();
        r.sched.advance(0);       // ping 1 OK -> apply -> ACTIVE
        check("#17 one-ok: ACTIVE after 1 ping", r.core.getState() == RouterCore.State.ACTIVE);
        check("#17 one-ok: applied after 1 ping (INV1)", r.kernel.fullyApplied());
    }

    // #18 — a failed ping never activates (stays STARTING, kernel clean = INV2); the
    // first subsequent OK ping activates (ONLINE_OK_THRESHOLD=1).
    static void scenarioFailThenOk() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(false); // first ping fails
        r.core.enable();
        r.sched.advance(0);       // ping 1 FAIL
        check("#18 fail-then-ok: STARTING after fail", r.core.getState() == RouterCore.State.STARTING);
        check("#18 fail-then-ok: clean after fail (INV2)", r.kernel.clean());
        r.kernel.setUplinkUp(true);
        r.sched.advance(5_000);   // next ping OK -> apply -> ACTIVE
        check("#18 fail-then-ok: ACTIVE after first OK", r.core.getState() == RouterCore.State.ACTIVE);
        check("#18 fail-then-ok: applied (INV1)", r.kernel.fullyApplied());
    }

    // #20 — autostart OFF: enabled persisted but restore must NOT activate; kernel clean
    static void scenarioAutostartGateOff() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.store.setEnabled(true);
        r.store.setAutostart(false);
        r.core.restoreIfEnabled();
        r.sched.advance(30_000);
        check("#20 gate-off: stays DISABLED", r.core.getState() == RouterCore.State.DISABLED);
        check("#20 gate-off: kernel clean (INV2)", r.kernel.clean());
    }

    // #21 — autostart ON: restore activates normally
    static void scenarioAutostartGateOn() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.store.setEnabled(true);
        r.store.setAutostart(true);
        r.core.restoreIfEnabled();
        r.sched.advance(30_000);
        check("#21 gate-on: ACTIVE", r.core.getState() == RouterCore.State.ACTIVE);
        check("#21 gate-on: applied (INV1)", r.kernel.fullyApplied());
    }

    // #22 — autostart is sticky: manual disable() leaves the flag untouched
    static void scenarioAutostartStickyOnDisable() {
        Rig r = new Rig();
        r.kernel.setUplinkUp(true);
        r.store.setAutostart(true);
        r.core.enable();
        r.sched.advance(30_000);
        check("#22 sticky: ACTIVE first", r.core.getState() == RouterCore.State.ACTIVE);
        r.core.disable();
        r.sched.advance(0);
        check("#22 sticky: autostart still true after disable", r.store.isAutostart());
    }

    // #19 — CarPlay/local traffic preserve: a higher-priority rule keeps local
    // (non-default) destinations on the main table so wireless CarPlay on wlan2
    // survives; only genuine internet (default route) is diverted to Starlink.
    static void scenarioCarPlayLocalPreserve() {
        String ap = RouterCore.applyCmd();
        // local-preserve rule present, higher priority (lower number) than the diversion
        check("#19 apply: local-preserve rule lookup main",
                ap.contains("iif wlan2 lookup main"));
        check("#19 apply: suppress default route only", ap.contains("suppress_prefixlength 0"));
        check("#19 apply: local rule prio 17998 (before 17999)", ap.contains("priority 17998"));
        check("#19 apply: diversion still prio 17999", ap.contains("priority 17999"));
        check("#19 apply: local rule verified in tail",
                ap.contains("&& ip rule | grep -q 'iif wlan2 lookup main'"));

        // both rules land, idempotently, and purge removes both
        KernelShell k = new KernelShell();
        k.exec(RouterCore.applyCmd());
        check("#19 apply: two ip rules present (local + diversion)", k.ipRuleCount() == 2);
        check("#19 apply: fully applied (INV1)", k.fullyApplied());
        k.exec(RouterCore.applyCmd());
        check("#19 apply twice: still 2 ip rules (no dup)", k.ipRuleCount() == 2);
        k.exec(RouterCore.purgeCmd());
        check("#19 purge: both rules gone (INV2)", k.clean());

        String pu = RouterCore.purgeCmd();
        check("#19 purge: negated verify for local rule",
                pu.contains("! ip rule | grep -q 'iif wlan2 lookup main'"));
    }

    // #21 — DNS from wlan2 clients is redirected to a fixed public resolver (bypass native
    // tethering's dnsmasq, which forwards via whatever upstream it picked — stuck on a dead
    // vlan13, that path dies even though Starlink routing itself is fine).
    static void scenarioDnsRedirect() {
        String ap = RouterCore.applyCmd();
        check("#21 apply: dns redirect udp", ap.contains("-i wlan2 -p udp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));
        check("#21 apply: dns redirect tcp", ap.contains("-i wlan2 -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));
        check("#21 apply: dns redirect in PREROUTING", ap.contains("-t nat -I PREROUTING"));
        check("#21 apply: dns redirect verified in tail",
                ap.contains("&& iptables -t nat -C PREROUTING -i wlan2 -p udp --dport 53 -j DNAT --to-destination 8.8.8.8:53")
                        && ap.contains("&& iptables -t nat -C PREROUTING -i wlan2 -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));

        String pu = RouterCore.purgeCmd();
        check("#21 purge: dns redirect removed (udp)", pu.contains("-t nat -D PREROUTING -i wlan2 -p udp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));
        check("#21 purge: dns redirect removed (tcp)", pu.contains("-t nat -D PREROUTING -i wlan2 -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));
        check("#21 purge: dns redirect negated in tail",
                pu.contains("! iptables -t nat -C PREROUTING -i wlan2 -p udp --dport 53 -j DNAT --to-destination 8.8.8.8:53")
                        && pu.contains("! iptables -t nat -C PREROUTING -i wlan2 -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53"));

        KernelShell k = new KernelShell();
        k.exec(ap);
        check("#21 apply: fully applied incl. dns redirect (INV1)", k.fullyApplied());
        check("#21 apply: nat table has masquerade + 2 dns redirects", k.natCount() == 3);
        k.exec(ap);
        check("#21 apply twice: still 3 nat rules (no dup)", k.natCount() == 3);
        k.exec(pu);
        check("#21 purge: clean, incl. dns redirect (INV2)", k.clean());
    }
}
