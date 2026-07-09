# JLH6 — contexto para o Claude

## Superpowers

**Sempre use superpowers** para qualquer tarefa não trivial neste projeto:

- Nova feature ou mudança de comportamento → `superpowers:brainstorming` primeiro
- Implementação com múltiplas tarefas → `superpowers:subagent-driven-development`
- Só implementação direta (plano já claro) → `superpowers:writing-plans`

## O que é isso

Aplicativo Android para a head unit pessoal de um carro Haval/GWM. Uma tela:

1. **Configurações** (botão, topo) — abre `com.android.settings/.Settings`
2. **Starlink Router** (botão) — roteia tráfego do hotspot (`wlan2`) via Starlink (`wlan0`) usando iptables + ip rule via telnet root
3. **Ativar ao ligar o carro** (switch, abaixo do botão router) — autostart opcional (default OFF). Quando ligado, o router religa sozinho no boot / ao abrir o app. Recuperação automática **não** é mais opcional (sempre ON via const `AUTO_RECOVERY`, sem UI).

Além disso, **escondido (sem UI) e independente do router**: um *cold-start wifi bounce* — no boot do carro o app desliga o wifi e religa depois de 5 min, pra matar o glitch que derruba o CarPlay quando a Starlink pega sinal. Ver seção **Cold-start wifi bounce**.

## Regras absolutas — nunca quebre estas

- **Zero dependências de terceiros.** Apenas Android SDK. Apenas Java. Sem AndroidX, sem Compose, sem Kotlin, sem Shizuku, sem nada do Jetpack.
- `android.useAndroidX=false` em `gradle.properties` — deve permanecer assim.
- `minSdk = targetSdk = 28` — deliberado.
- `compileSdk = 35`, AGP 8.7.3, Gradle 8.14.3.
- PR → somente build debug. Merge em `main` → release assinada + `gh release create`.

## Arquivos principais

| Caminho | O que é |
|---------|---------|
| `app/src/main/java/com/castilhoduarte/jlh6/MainActivity.java` | Única Activity. Poll de estado a cada 500ms **enquanto estado ≠ DISABLED**. 1º tap em ativar checa accessibility; sem ele → dialog que abre `ACTION_ACCESSIBILITY_SETTINGS`. Switch "Ativar ao ligar o carro" (persiste `autostart` via `setAutostart`; `onResume` sincroniza o estado do switch sem disparar o listener). `onResume` também chama `WifiBootManager.onStart` (failsafe do bounce). |
| `app/src/main/java/com/castilhoduarte/jlh6/RouterManager.java` | Singleton. State machine: DISABLED/STARTING/ACTIVE/PURGING. HandlerThread para background. Inclui o monitor de saúde (recuperação automática) e a rotina de `recover`. |
| `app/src/main/java/com/castilhoduarte/jlh6/TelnetRoot.java` | Cliente telnet mínimo para `127.0.0.1:23`. Sentinelas `__HR_BEG__`/`__HR_END__$?`. |
| `app/src/main/java/com/castilhoduarte/jlh6/JLH6App.java` | Application. `onCreate` → `restoreIfEnabled` + `WifiBootManager.onStart` (failsafe do bounce). Roda sempre que o processo nasce (inclusive religado no boot pelo accessibility). |
| `app/src/main/java/com/castilhoduarte/jlh6/RouterAccessibilityService.java` | Âncora de autostart, habilitado **manualmente** pelo usuário na config do Android. `isEnabled()` checa o estado; `onServiceConnected` → `restoreIfEnabled` + `WifiBootManager.onStart` (failsafe do bounce). |
| `app/src/main/java/com/castilhoduarte/jlh6/BootReceiver.java` | Reforço. `exported=true`. BOOT_COMPLETED / QUICKBOOT_POWERON → `restoreIfEnabled` + `WifiBootManager.onBoot` (inicia o bounce). MY_PACKAGE_REPLACED → `restoreIfEnabled` + `onStart` (failsafe; update de app não é boot de carro). |
| `app/src/main/java/com/castilhoduarte/jlh6/WifiBootCore.java` | **Lógica pura** do cold-start wifi bounce (off → espera `WIFI_OFF_MS` → on) + failsafe por timestamp + single-flight. Testável em JDK (Clock/Scheduler/WifiControl/WifiBootStore injetados). |
| `app/src/main/java/com/castilhoduarte/jlh6/WifiBootManager.java` | Adapter Android do bounce. Singleton, `HandlerThread` próprio, `SharedPreferences("wifiboot")`. `onBoot`/`onStart`. **Independente do router.** |
| `app/src/main/java/com/castilhoduarte/jlh6/WifiControl.java` | Interface `setEnabled(boolean)`. |
| `app/src/main/java/com/castilhoduarte/jlh6/TelnetWifiControl.java` | Impl de `WifiControl` via `svc wifi enable/disable` no **root telnet** (`WifiManager` bloqueado nessa ROM). Log em `JLH6Wifi`. |
| `app/src/main/java/com/castilhoduarte/jlh6/WifiBootStore.java` | Interface do timestamp `reenable_at`. |
| `scripts/test/WifiBootCoreTest.java` | Testes JDK puro do bounce: bounce feliz, single-flight, failsafe (pós-janela / mid-window), onStart neutro, INV-WIFI, ordem crash-safe. |
| `app/src/main/java/com/castilhoduarte/jlh6/Updater.java` | Android-free. Helpers puros (compara versão do release GitHub vs `versionCode` local; monta o comando de lançamento destacado) + adapters de I/O (`checkUpdate` via GitHub API, `triggerUpdate` via telnet). |
| `scripts/test/UpdaterTest.java` | Testes JDK puro das funções puras do `Updater`. |
| `scripts/install-app.sh` | Instala o JLH6 via exploit Frida (bypass de pm install). Termina com `am start` pra reabrir o app atualizado (usado pelo botão de update in-app). |
| `scripts/install-apk.sh` | Instala qualquer APK via exploit Frida. |
| `scripts/test/TelnetRootTest.java` | 15 testes do TelnetRoot. JDK puro, sem Gradle. |

## Testes & TDD (obrigatório)

Suite de testes em `scripts/test/` roda em JDK puro (sem Gradle, sem libs). Rode tudo com:

```sh
bash scripts/run.sh
```

- `RouterCore` (lógica pura, sem `android.*`) é o coração testável. `RouterManager` é só o adapter Android (liga `Clock`/`Scheduler`/`Shell`/`StateStore` reais).
- `KernelShell` (em `scripts/test/`) **interpreta os comandos reais** (`applyCmd`/`purgeCmd`/ping) contra um kernel simulado; `VirtualScheduler` dá tempo virtual determinístico.
- **Toda edição em `RouterCore` ou na lógica de rede segue TDD:** escreva o teste vermelho primeiro em `RouterCoreTest.java`, veja falhar, implemente o mínimo, rode `scripts/run.sh` até verde, só então commit.
- `WifiBootCore` é outro núcleo puro testável (bounce), com `WifiBootCoreTest.java` + fakes (`FakeWifiControl`, `FakeWifiBootStore`). Mesma regra de TDD.
- **Invariantes que nunca podem regredir:** INV1 — nunca rotear pra Starlink sem ping OK (nunca `ACTIVE` sem apply verificado); INV2 — fora de `ACTIVE`, zero regras JLH6 (sem fantasmas que travem o hotspot `wlan2` normal).
- CI roda `scripts/run.sh` em todo PR. Não faça merge com teste vermelho.

## Topologia de rede

| Interface | Papel |
|-----------|-------|
| `wlan2` | Hotspot da multimídia (clientes do carro) |
| `wlan0` | Starlink (uplink externo) |
| tabela `wlan0` | Tabela de roteamento separada com rota default via Starlink |
| prioridade `17998` | ip rule de preservação local (`lookup main suppress_prefixlength 0`) — mantém tráfego local do `wlan2` (CarPlay wireless + clientes do hotspot) na tabela `main`; checada **antes** do desvio |
| prioridade `17999` | ip rule de desvio (só internet — destinos sem rota específica no `main` → tabela `wlan0`) |

## State machine do RouterManager

```
DISABLED → [tap] → STARTING → [1 ping OK + apply verificado] → ACTIVE
STARTING → [ping falha] → STARTING (reagenda doPing, zera consecutiveOks)
STARTING → [ping OK mas apply não verifica] → STARTING (reagenda doPing, até timeout)
STARTING → [10min sem ping/apply] → DISABLED (salva enabled=false)
STARTING → [tap] → PURGING → DISABLED
ACTIVE   → [tap] → PURGING → DISABLED
ACTIVE   → [recuperação auto: 6 pings falham] → STARTING (purge → espera 5s → reativa)
```

- Ping loop: `ping -I wlan0 -c 1 -W 2 8.8.8.8` a cada 5s. Ativação exige **1 ping OK** (`ONLINE_OK_THRESHOLD=1`) antes de aplicar regras; qualquer falha zera `consecutiveOks` e reagenda. (O monitor de recuperação usa `RECOVERY_FAIL_THRESHOLD=6` falhas pra disparar recover.)
- Timeout STARTING: 10 minutos
- Estado persistido: `SharedPreferences("router", "enabled")` e `SharedPreferences("router", "autostart")`
- No boot: `restoreIfEnabled` só entra STARTING se `enabled=true` **e** `autostart=true` (autostart default OFF; sem ele o router só liga por tap manual); nunca aplica regras sem 1 ping OK verificado; o monitor de recovery rearma sozinho ao chegar em ACTIVE (sempre ON)
- Tap durante PURGING: ignorado
- **apply/purge idempotentes e auto-verificados**: cada comando termina com uma cláusula de verificação (o `$?` final = estado confirmado) e roda via `execVerified` (retry com backoff, captura `Throwable`). `ACTIVE` só é marcado após apply **verificado** — nunca em apply parcial. `disable`/`recover` repetem o purge até verificar limpo. Verificação é por nome de regra → correta mesmo se `wlan0`/`wlan2` sumirem. Constantes: `ONLINE_OK_THRESHOLD=1`, `APPLY_ATTEMPTS=3`, `PURGE_ATTEMPTS=4`, `VERIFY_BACKOFF_MS=500`.

## Recuperação automática (auto-recovery)

**Sempre ON** — const `AUTO_RECOVERY=true` no `RouterCore`, sem toggle na UI (considerada essencial demais pra ser opcional). Atua enquanto o router está ligado pelo usuário.

- **Monitor de saúde** (`doMonitor`): armado sempre que `state==ACTIVE` (gate no const `AUTO_RECOVERY`). Faz o mesmo ping (`ping -I wlan0 ... 8.8.8.8`) a cada 5s e conta falhas consecutivas. Roda no mesmo `HandlerThread`, separado do loop de ativação (`doPing`).
- **Gatilho**: 6 falhas consecutivas (`RECOVERY_FAIL_THRESHOLD`) → `recover()`.
- **Recovery** (`recover`): `state=STARTING` → `execPurge()` (cleanup existente) → espera 5s → reativa via `startPingLoop` (revalida com ping, reaplica regras, volta a ACTIVE → rearma o monitor). Reusa STARTING ("ATIVANDO...") na UI — sem estado novo. Limite: cada reativação herda o timeout de 10min do `doPing`.
- **Pontos de armado**: caminho de sucesso do `doPing` (cobre 1ª ativação, recovery e boot).
- **Precedência manual (override)**:
  - `disable()` (tap manual no botão) para o monitor e cancela callbacks do `bg`.
  - A lambda de reativação do `recover` rechecka `KEY_ENABLED` antes de religar — se o usuário desativou durante a espera pós-purge, recovery não sobrepõe o OFF manual.

## Autostart (religar no boot)

**Opcional** — switch "Ativar ao ligar o carro" (pref `autostart`, default OFF). `restoreIfEnabled` só religa se `enabled && autostart`; gate único cobre boot **e** abrir o app. Com autostart OFF o router nunca liga sozinho — só por tap manual. O `enable()` manual ignora o flag.

Mecanismo em camadas, ancorado no **AccessibilityService** (Android trava autostart de apps de terceiros; um accessibility habilitado é religado pelo sistema todo boot e fica imune a kill/limites de background):

1. **Âncora** — `RouterAccessibilityService`, habilitado **manualmente uma vez** pelo usuário. UX: 1º tap no botão ativar → `RouterAccessibilityService.isEnabled` checa; se desligado → dialog → `ACTION_ACCESSIBILITY_SETTINGS` → usuário liga "JLH6" na lista → volta → tap de novo ativa. Persiste em secure settings entre boots. Sem telnet.
2. No boot o sistema religa o processo → `JLH6App.onCreate` e `RouterAccessibilityService.onServiceConnected` chamam `restoreIfEnabled`.
3. **Reforço**: `BootReceiver` (`exported=true`) em BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED.

Sem foreground service, sem notificação permanente. O loop de ping roda no processo ancorado pelo accessibility (prioridade perceptível → não é morto).

> **Bug histórico:** `BootReceiver` era `exported="false"` → BOOT_COMPLETED vem do `system_server` (uid ≠ app) e nunca era entregue. Receiver de boot **tem** que ser `exported="true"`.

## Cold-start wifi bounce

**Escondido, sem UI. Independente do router** (o router **não** mexe no wifi). Resolve um glitch do próprio wifi do carro: com o `wlan0` associado à `JLStarlink`, quando a Starlink adquire sinal depois de um tempo conectado, o CarPlay (no `wlan2`) cai. Manter o `wlan0` desligado durante a janela de cold-start da Starlink evita o glitch. **Confirmado em campo: o glitch parou.**

- **Comportamento**: no boot do carro → wifi **OFF** → espera `WIFI_OFF_MS` (const em `WifiBootCore`, **5 min**) → wifi **ON**. Sem ping, sem gate. Independente das flags `enabled`/`autostart` do router.
- **Mecanismo**: `svc wifi enable/disable` via **root telnet** (`TelnetWifiControl`, reusa `TelnetRoot`). `WifiManager.setWifiEnabled` é **bloqueado** nessa ROM pra app de terceiro — chama `getCountryCode()` que exige `CONNECTIVITY_INTERNAL` (`signature|privileged`, ungrantable ao uid do app; `SecurityException` no logcat). O shell root (uid 0) passa.
- **Failsafe (timestamp)**: `SharedPreferences("wifiboot")` chave `reenable_at`. Grava `reenable_at = agora+WIFI_OFF_MS` **antes** de desligar; religa **antes** de limpar (crash-safe). Sobrevive a morte de processo: qualquer nascer de processo (`onStart`) religa se a janela já passou, ou reagenda se ainda no prazo.
- **INV-WIFI**: o único ponto que desliga o wifi é `onBoot()`; toda outra transição só liga. Nunca deixa o wifi off de forma persistente.
- **Gatilhos**: `BootReceiver` (BOOT_COMPLETED / QUICKBOOT_POWERON) → `WifiBootManager.onBoot` = **único** que inicia o bounce (evento de boot real). Todo o resto (`MY_PACKAGE_REPLACED`, `JLH6App.onCreate`, `RouterAccessibilityService.onServiceConnected`, `MainActivity.onResume`) → `onStart` = **só failsafe** (abrir o app dirigindo nunca desliga o wifi).
- **Arquitetura**: `WifiBootCore` (lógica pura, tempo virtual nos testes) + `WifiBootManager` (adapter). Diagnóstico de campo: `logcat -s JLH6Wifi` (`svc wifi disable -> exit=0` no boot, `svc wifi enable -> exit=0` após a janela).
- **Histórico**: a 1ª tentativa (dia anterior) usava `WifiManager` → bloqueada pela ROM. Trocado pra `svc wifi` via root telnet → funciona.

## Comandos telnet executados

### Apply (ativar roteamento — idempotente + auto-verificado)
```sh
echo 1 > /proc/sys/net/ipv4/ip_forward
# ip rule de preservação local (prio 17998, checada ANTES do desvio): manda ingress do wlan2 pra
# tabela main mas suprime a rota default → só destinos com rota específica (subnet local conectado)
# casam. Mantém CarPlay wireless + tráfego local do hotspot no wlan2; internet cai pro desvio abaixo.
while ip rule | grep -q 'iif wlan2 lookup main'; do ip rule del from all iif wlan2 lookup main suppress_prefixlength 0 priority 17998 2>/dev/null || break; done
ip rule add from all iif wlan2 lookup main suppress_prefixlength 0 priority 17998
# ip rule de desvio idempotente: remove todas as nossas regras, depois adiciona uma
while ip rule | grep -q 'iif wlan2 lookup wlan0'; do ip rule del from all iif wlan2 lookup wlan0 priority 17999 2>/dev/null || break; done
ip rule add from all iif wlan2 lookup wlan0 priority 17999
iptables -t nat -C POSTROUTING -o wlan0 -j MASQUERADE 2>/dev/null || iptables -t nat -I POSTROUTING 1 -o wlan0 -j MASQUERADE
iptables -C FORWARD -i wlan2 -o wlan0 -j ACCEPT 2>/dev/null || iptables -I FORWARD 1 -i wlan2 -o wlan0 -j ACCEPT
iptables -C FORWARD -i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -I FORWARD 1 -i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -j ACCEPT
# verificação (comando final → $?): forwarding on E as 2 ip rules + 3 regras iptables presentes
grep -qx 1 /proc/sys/net/ipv4/ip_forward 2>/dev/null && ip rule | grep -q 'iif wlan2 lookup main' && ip rule | grep -q 'iif wlan2 lookup wlan0' && iptables -t nat -C POSTROUTING -o wlan0 -j MASQUERADE 2>/dev/null && iptables -C FORWARD -i wlan2 -o wlan0 -j ACCEPT 2>/dev/null && iptables -C FORWARD -i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null
```

### Purge (desativar — loop até limpo + auto-verificado)
```sh
while ip rule | grep -q "iif wlan2 lookup main"; do ip rule del ... priority 17998; done
while ip rule | grep -q "iif wlan2 lookup wlan0"; do ip rule del ...; done
while iptables -t nat -C POSTROUTING ...; do iptables -t nat -D POSTROUTING ...; done
while iptables -C FORWARD -i wlan2 ...; do iptables -D FORWARD ...; done
while iptables -C FORWARD -i wlan0 ...; do iptables -D FORWARD ...; done
# verificação (comando final → $?): NENHUMA das nossas regras presente
! ip rule | grep -q 'iif wlan2 lookup main' && ! ip rule | grep -q 'iif wlan2 lookup wlan0' && ! iptables -t nat -C POSTROUTING -o wlan0 -j MASQUERADE 2>/dev/null && ! iptables -C FORWARD -i wlan2 -o wlan0 -j ACCEPT 2>/dev/null && ! iptables -C FORWARD -i wlan0 -o wlan2 -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null
```

## Exploit Frida (por que existe)

A head unit bloqueia `pm install` de APKs externos. Os scripts injetam no `system_server` via Frida para remover essa restrição durante a instalação.

Binários do exploit em: `https://github.com/jucastilhoduarte/jlh6/releases/tag/exploit-bins`

## CI (`.github/workflows/build.yml`)

- **PR**: `assembleDebug`
- **Push em main**: `assembleRelease` assinado + `gh release create`

Secrets: `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.

## Design da UI

Tema escuro, landscape, 21:9. Sem ActionBar. Empilhado verticalmente, centralizado, de cima para baixo: botão **Configurações** (engrenagem + texto), botão **Starlink Router** (wifi + texto), **switch** "Ativar ao ligar o carro" (autostart). Botões retangulares.

Canto superior direito: **versão atual (`vX.X.X`)** sempre visível + **ícone de update** sempre visível. Ícone habilitado só quando o último release do GitHub tem `versionCode` maior. Tap com router DISABLED → dialog de confirmação → dispara o `install-app.sh` remoto destacado via telnet (sucesso = `pm install -r` mata o app, e o `am start` do script o reabre atualizado). Tap com router ligado → dialog "desative o Starlink Router antes de atualizar". Durante o update toda a UI fica bloqueada, ícone vira spinner e o label troca para "Atualizando para vX.X.X". Watchdog de 120s recupera a falha-sem-morte (restaura o ícone). Checagem de versão roda em `onResume` (background, fail-safe: rede/parse ruim → ícone desabilitado).

## Pacote / assinatura

- `applicationId = com.castilhoduarte.jlh6`
- Assinado com chave pessoal do dono. Keystore em `~/Desktop/haval-actions-secrets`.
