package com.frever.platform.timers.messaging;

public class MessageWithUnknownSubjectException extends RuntimeException {
    public MessageWithUnknownSubjectException() {
        super("Unknown message subject", null, false, false);
    }
}
