package com.castilhoduarte.jlh6;

public class AccessibilityCmdTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) {
        String comp = "com.castilhoduarte.jlh6/com.castilhoduarte.jlh6.RouterAccessibilityService";
        String cmd = AccessibilityCmd.enable(comp);

        // Single logical line — TelnetRoot.exec wraps it in a subshell; no raw newlines.
        check("single line", !cmd.contains("\n"));

        // Reads current value before mutating (preserves other enabled services).
        check("reads current", cmd.contains("cur=$(settings get secure enabled_accessibility_services)"));

        // Idempotent: skips when the component is already present.
        check("idempotent guard", cmd.contains("*\":" + comp + ":\"*) ;;"));

        // Empty/"null" current -> set bare component (no leading colon).
        check("empty -> bare", cmd.contains("enabled_accessibility_services '" + comp + "'"));

        // Non-empty current -> append after a colon, keeping existing services.
        check("append with colon", cmd.contains("enabled_accessibility_services \"$cur:" + comp + "\""));

        // Flips the master switch on.
        check("master switch", cmd.trim().endsWith("settings put secure accessibility_enabled 1"));

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
