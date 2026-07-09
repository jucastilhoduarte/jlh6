package com.castilhoduarte.jlh6;

/** Persists when wifi should be re-enabled (epoch ms). 0 = no bounce in flight. */
public interface WifiBootStore {
    long getReenableAt();
    void setReenableAt(long epochMs);
}
