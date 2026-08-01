package com.tickethelper.engine;

public class GrabConfig {
    public int sessionIndex = 0;
    public int ticketIndex = 0;
    public int clickInterval = 500;
    public int maxRetry = 30;

    @Override
    public String toString() {
        return "GrabConfig{" +
                "sessionIndex=" + sessionIndex +
                ", ticketIndex=" + ticketIndex +
                ", clickInterval=" + clickInterval +
                ", maxRetry=" + maxRetry +
                '}';
    }
}
