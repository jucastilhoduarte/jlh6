package com.castilhoduarte.jlh6;

public final class FakeWifiBootStore implements WifiBootStore {
    private long reenableAt = 0;
    @Override public long getReenableAt() { return reenableAt; }
    @Override public void setReenableAt(long v) { reenableAt = v; }
}
