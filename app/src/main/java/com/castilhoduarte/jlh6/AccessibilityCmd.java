package com.castilhoduarte.jlh6;

/**
 * Pure shell-command builders for self-granting our AccessibilityService over the root
 * telnet shell. No Android dependencies, so it stays unit-testable with plain JDK
 * (see scripts/test/AccessibilityCmdTest.java), like {@link TelnetRoot}'s sentinels.
 */
final class AccessibilityCmd {

    private AccessibilityCmd() {}

    /**
     * Appends {@code component} to {@code enabled_accessibility_services} only if absent
     * (preserving any services already enabled), then flips {@code accessibility_enabled}
     * on. Idempotent — safe to run on every launch. {@code component} is a flattened
     * {@code pkg/pkg.ServiceClass} string.
     */
    static String enable(String component) {
        return "cur=$(settings get secure enabled_accessibility_services); "
            + "case \":$cur:\" in "
            + "*\":" + component + ":\"*) ;; "
            + "*) if [ -z \"$cur\" ] || [ \"$cur\" = null ]; then "
            + "settings put secure enabled_accessibility_services '" + component + "'; "
            + "else settings put secure enabled_accessibility_services \"$cur:" + component + "\"; fi;; "
            + "esac; "
            + "settings put secure accessibility_enabled 1";
    }
}
