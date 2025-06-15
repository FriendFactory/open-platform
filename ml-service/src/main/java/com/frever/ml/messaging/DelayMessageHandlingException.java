package com.frever.ml.messaging;

public class DelayMessageHandlingException extends RuntimeException {
    public int getDelay() {
        return delay;
    }

    protected int delay;

    public DelayMessageHandlingException(int delay) {
        super("Delay message handling", null, false, false);
        this.delay = delay;
    }
}
