package com.castilhoduarte.jlh6;

public final class FakeStore implements StateStore {
    private boolean enabled, autostart;
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean v) { enabled = v; }
    @Override public boolean isAutostart() { return autostart; }
    @Override public void setAutostart(boolean v) { autostart = v; }
}
