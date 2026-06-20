#!/bin/sh

# install-any.sh - instala qualquer APK na head unit usando o exploit Frida.
#
# Uso:
#   sh install-any.sh <url-do-apk>
#
# A URL e obrigatoria. Aceita .apk direto ou .zip contendo .apk.
# GH_TOKEN (opcional, via env): Bearer no download (URLs privadas/artifacts).
#
# Por que o exploit e necessario: a multimidia bloqueia pm install de APKs
# externos. A injecao Frida no system_server remove essa restricao durante
# a instalacao.

set -u

REPO="https://github.com/jucastilhoduarte/starlinkrouter"
WORK="/data/local/tmp"
ROLLBACK_ENABLED=true

log() { echo "[$1] $(date +%H:%M:%S) $2"; }
die() { log "ERR" "$1"; exit 1; }

cleanup() {
    rc=$?
    [ "$rc" -eq 0 ] && exit 0
    [ "$ROLLBACK_ENABLED" = false ] && exit 0
    log "INFO" "Rollback..."
    rm -rf "$WORK/unz" 2>/dev/null || true
    rm -f "$WORK/artifact.bin" "$WORK/target.apk" 2>/dev/null || true
    log "INFO" "Rollback concluido"
}
trap cleanup EXIT

download() {
    log "INFO" "Baixando $3..."
    if [ -n "${GH_TOKEN:-}" ]; then
        curl -L --fail --progress-bar -H "Authorization: Bearer $GH_TOKEN" -o "$2" "$1" \
            || die "Falha no download de $3"
    else
        curl -L --fail --progress-bar -o "$2" "$1" || die "Falha no download de $3"
    fi
    [ -s "$2" ] || die "$3 vazio/inexistente"
}

download_cached() {
    [ -f "$2" ] && [ -s "$2" ] && { log "INFO" "$3 ja existe (cache)"; return; }
    download "$1" "$2" "$3"
}

main() {
    URL="${1:-}"
    [ -n "$URL" ] || die "Uso: sh install-any.sh <url-do-apk>"

    cd "$WORK" || die "Falha ao acessar $WORK"

    # --- Fase 1: binarios do exploit (cacheados) ---
    log "INFO" "Fase 1: Downloads do exploit"
    download_cached "$REPO/releases/download/exploit-bins/fridaserver.rar" "fridaserver" "fridaserver"
    download_cached "$REPO/releases/download/exploit-bins/fridainject.rar" "fridainject" "fridainject"
    download_cached "$REPO/releases/download/exploit-bins/system_server.js" "system_server.js" "system_server.js"
    chmod +x fridaserver fridainject || die "Falha nas permissoes"

    # --- Fase 2: fridaserver (idempotente) ---
    log "INFO" "Fase 2: fridaserver"
    if pgrep fridaserver >/dev/null 2>&1; then
        log "INFO" "fridaserver ja rodando"
    else
        [ -x "./fridaserver" ] || die "fridaserver nao executavel"
        setsid ./fridaserver >/dev/null 2>&1 < /dev/null &
        sleep 2
        pgrep fridaserver >/dev/null 2>&1 || die "fridaserver nao iniciou"
        log "INFO" "fridaserver iniciado"
    fi

    # --- Fase 3: injecao no system_server ---
    log "INFO" "Fase 3: Injecao system_server"
    [ -f "system_server.js" ] || die "system_server.js nao encontrado"
    SYSTEM_PID=$(pidof system_server) || die "system_server nao encontrado"
    ./fridainject -p "$SYSTEM_PID" -s system_server.js &
    sleep 2
    log "INFO" "Injecao disparada (system_server pid=$SYSTEM_PID)"

    # --- Fase 4: baixar e resolver o APK alvo ---
    log "INFO" "Fase 4: Baixar APK ($URL)"
    rm -rf unz 2>/dev/null || true
    rm -f artifact.bin target.apk 2>/dev/null || true
    download "$URL" "artifact.bin" "artifact"

    APK=""
    if command -v unzip >/dev/null 2>&1 && \
       unzip -l artifact.bin 2>/dev/null | grep -qiE '\.apk *$'; then
        log "INFO" "Zip detectado, extraindo .apk"
        mkdir -p unz
        unzip -o artifact.bin -d unz >/dev/null 2>&1 || die "Falha ao extrair o zip"
        APK=$(find unz -type f -name '*.apk' | head -n1)
        [ -n "$APK" ] || die "Nenhum .apk dentro do zip"
    else
        mv -f artifact.bin target.apk || die "Falha ao preparar apk"
        APK="$WORK/target.apk"
    fi
    [ -s "$APK" ] || die "APK final vazio"

    # --- Fase 5: instalar ---
    log "INFO" "Fase 5: Instalando APK"
    pm install -r "$APK" || pm install "$APK" || die "Falha na instalacao"

    # --- limpeza ---
    rm -rf unz 2>/dev/null || true
    rm -f artifact.bin target.apk 2>/dev/null || true
    ROLLBACK_ENABLED=false

    log "INFO" "APK instalado com sucesso"
}

main "$@"
