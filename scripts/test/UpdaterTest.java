package com.castilhoduarte.jlh6;

public class UpdaterTest {

    static int passed = 0, failed = 0;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else { failed++; System.out.println("  FAIL " + name); }
    }

    public static void main(String[] args) {
        // buildCommand: comando de lançamento destacado, exato
        String cmd = Updater.buildCommand("https://example.com/x.sh");
        check("buildCommand: cd workdir", cmd.startsWith("cd /data/local/tmp &&"));
        check("buildCommand: curl url",
                cmd.contains("curl -fsSL https://example.com/x.sh -o jlh6_update.sh"));
        check("buildCommand: setsid detach", cmd.contains("setsid sh jlh6_update.sh"));
        check("buildCommand: stdio redirect",
                cmd.contains("> jlh6_update.log 2>&1 < /dev/null"));
        check("buildCommand: backgrounded", cmd.trim().endsWith("&"));

        // parseTagName
        String json = "{\"url\":\"x\",\"tag_name\":\"v1.0.42\",\"name\":\"JLH6 1.0.42\"}";
        check("parseTagName: extracts", "v1.0.42".equals(Updater.parseTagName(json)));
        check("parseTagName: missing -> null",
                Updater.parseTagName("{\"name\":\"x\"}") == null);
        check("parseTagName: null -> null", Updater.parseTagName(null) == null);

        // remoteVersionCode
        check("remoteVersionCode: v1.0.42 -> 42", Updater.remoteVersionCode("v1.0.42") == 42);
        check("remoteVersionCode: malformed -> -1", Updater.remoteVersionCode("v1.0.x") == -1);
        check("remoteVersionCode: null -> -1", Updater.remoteVersionCode(null) == -1);

        // isUpdateAvailable
        check("isUpdateAvailable: newer", Updater.isUpdateAvailable("v1.0.43", 42));
        check("isUpdateAvailable: equal -> false", !Updater.isUpdateAvailable("v1.0.42", 42));
        check("isUpdateAvailable: older -> false", !Updater.isUpdateAvailable("v1.0.41", 42));
        check("isUpdateAvailable: bad tag -> false", !Updater.isUpdateAvailable("garbage", 42));

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
