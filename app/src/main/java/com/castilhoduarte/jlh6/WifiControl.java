package com.castilhoduarte.jlh6;

/** Turns the STA wifi client (wlan0) on/off. Android impl uses WifiManager.setWifiEnabled. */
public interface WifiControl {
    void setEnabled(boolean on);
}
