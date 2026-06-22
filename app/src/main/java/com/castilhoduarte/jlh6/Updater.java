package com.castilhoduarte.jlh6;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Updater {

    public static final String SCRIPT_RAW_URL =
            "https://raw.githubusercontent.com/jucastilhoduarte/jlh6/main/scripts/install-app.sh";
    public static final String RELEASES_API =
            "https://api.github.com/repos/jucastilhoduarte/jlh6/releases/latest";

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private Updater() {}

    /** Extrai o campo tag_name de um JSON de release do GitHub. */
    public static String parseTagName(String json) {
        if (json == null) return null;
        Matcher m = TAG.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Último segmento numérico da tag (ex. "v1.0.42" -> 42). -1 se não parseável. */
    public static int remoteVersionCode(String tag) {
        if (tag == null) return -1;
        String t = tag.trim();
        int dot = t.lastIndexOf('.');
        String seg = dot >= 0 ? t.substring(dot + 1) : t;
        try {
            return Integer.parseInt(seg);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Há update se o versionCode remoto for estritamente maior que o local. */
    public static boolean isUpdateAvailable(String tag, int localVersionCode) {
        return remoteVersionCode(tag) > localVersionCode;
    }

    /**
     * Comando telnet que baixa o install-app.sh e o roda DESTACADO: o `&` em conjunto
     * com setsid + stdio redirecionado faz o script sobreviver à morte do app (que o
     * próprio pm install -r provoca no fim).
     */
    public static String buildCommand(String rawUrl) {
        return "cd /data/local/tmp && curl -fsSL " + rawUrl
                + " -o jlh6_update.sh && setsid sh jlh6_update.sh"
                + " > jlh6_update.log 2>&1 < /dev/null &";
    }
}
