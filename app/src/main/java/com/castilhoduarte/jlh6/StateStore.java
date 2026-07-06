package com.castilhoduarte.jlh6;

public interface StateStore {
    boolean isEnabled();
    void setEnabled(boolean v);
    boolean isAutostart();
    void setAutostart(boolean v);
}
