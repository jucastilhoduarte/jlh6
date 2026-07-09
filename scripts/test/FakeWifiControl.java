package com.castilhoduarte.jlh6;

import java.util.List;

public final class FakeWifiControl implements WifiControl {
    public boolean enabled = true;
    public int offCalls = 0, onCalls = 0;
    private final List<String> log; // optional shared ordering log; null = no logging

    public FakeWifiControl() { this(null); }
    public FakeWifiControl(List<String> log) { this.log = log; }

    @Override public void setEnabled(boolean on) {
        enabled = on;
        if (on) onCalls++; else offCalls++;
        if (log != null) log.add("setEnabled(" + on + ")");
    }
}
