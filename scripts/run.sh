#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/com/castilhoduarte/jlh6"
TEST="$ROOT/scripts/test"
OUT="$(mktemp -d)"

javac -d "$OUT" \
  "$SRC/Clock.java" "$SRC/Scheduler.java" "$SRC/Shell.java" "$SRC/StateStore.java" \
  "$SRC/RouterCore.java" "$SRC/TelnetRoot.java" "$SRC/Updater.java" \
  "$SRC/WifiControl.java" "$SRC/WifiBootStore.java" "$SRC/WifiBootCore.java" \
  "$TEST/FakeClock.java" "$TEST/FakeStore.java" "$TEST/VirtualScheduler.java" \
  "$TEST/KernelShell.java" \
  "$TEST/FakeWifiControl.java" "$TEST/FakeWifiBootStore.java" \
  "$TEST/VirtualSchedulerTest.java" "$TEST/KernelShellTest.java" \
  "$TEST/RouterCoreTest.java" "$TEST/TelnetRootTest.java" "$TEST/UpdaterTest.java" \
  "$TEST/WifiBootCoreTest.java"

echo "== VirtualSchedulerTest =="; java -cp "$OUT" com.castilhoduarte.jlh6.VirtualSchedulerTest
echo "== KernelShellTest =="     ; java -cp "$OUT" com.castilhoduarte.jlh6.KernelShellTest
echo "== RouterCoreTest =="      ; java -cp "$OUT" com.castilhoduarte.jlh6.RouterCoreTest
echo "== TelnetRootTest =="      ; java -cp "$OUT" com.castilhoduarte.jlh6.TelnetRootTest
echo "== UpdaterTest =="         ; java -cp "$OUT" com.castilhoduarte.jlh6.UpdaterTest
echo "== WifiBootCoreTest =="    ; java -cp "$OUT" com.castilhoduarte.jlh6.WifiBootCoreTest
echo "ALL TESTS PASSED"
