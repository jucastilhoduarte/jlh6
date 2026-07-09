package com.castilhoduarte.jlh6;

public final class FakeWifiControl implements WifiControl {
    public boolean enabled = true;
    public int offCalls = 0, onCalls = 0;
    @Override public void setEnabled(boolean on) {
        enabled = on;
        if (on) onCalls++; else offCalls++;
    }
}
