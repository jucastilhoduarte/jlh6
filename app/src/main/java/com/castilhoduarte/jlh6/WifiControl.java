package com.castilhoduarte.jlh6;

/** Turns wifi on/off. Android impl runs `svc wifi enable/disable` via root telnet. */
public interface WifiControl {
    void setEnabled(boolean on);
}
