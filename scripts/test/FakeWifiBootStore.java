package com.castilhoduarte.jlh6;

import java.util.List;

public final class FakeWifiBootStore implements WifiBootStore {
    private long reenableAt = 0;
    private final List<String> log; // optional shared ordering log; null = no logging

    public FakeWifiBootStore() { this(null); }
    public FakeWifiBootStore(List<String> log) { this.log = log; }

    @Override public long getReenableAt() { return reenableAt; }
    @Override public void setReenableAt(long v) {
        reenableAt = v;
        if (log != null) log.add("setReenableAt(" + v + ")");
    }
}
