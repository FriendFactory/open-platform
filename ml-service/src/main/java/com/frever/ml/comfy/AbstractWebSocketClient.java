package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyWebSocketConstant.MONITOR;
import static com.frever.ml.comfy.ComfyWebSocketConstant.PROGRESS;
import static com.frever.ml.comfy.ComfyWebSocketConstant.STATUS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frever.ml.comfy.dto.ComfyExecutionStart;
import com.frever.ml.comfy.dto.ComfyWebsocketMessage;
import com.frever.ml.dao.ComfyUiTaskDao;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.UUID;

public abstract class AbstractWebSocketClient {
    @Inject
    protected ObjectMapper objectMapper;
    @Inject
    protected ComfyUiTaskDao comfyUiTaskDao;

    protected abstract void handleWebsocketMessage(Session session, ComfyWebsocketMessage websocketMessage)
        throws JsonProcessingException;

    protected void onMessage(String message, Session session) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.contains(MONITOR) || message.contains(PROGRESS) || message.contains(STATUS)) {
            return;
        }
        Log.debugf("SessionId: %s, Received message: %s", session.getId(), message);
        try {
            ComfyWebsocketMessage websocketMessage = objectMapper.readValue(message, ComfyWebsocketMessage.class);
            handleWebsocketMessage(session, websocketMessage);
        } catch (IOException e) {
            Log.errorf(
                e,
                "SessionId: %s at %s, Failed to parse message: %s",
                session.getId(),
                getServerAddr(session),
                message
            );
        }
    }

    protected void handleExecutionStart(ComfyWebsocketMessage websocketMessage) throws JsonProcessingException {
        ComfyExecutionStart start = objectMapper.readValue(
            websocketMessage.data().toString(),
            ComfyExecutionStart.class
        );
        Log.infof("Received execution start: %s", start);
        int retry = 0;
        while (!comfyUiTaskDao.taskExists(UUID.fromString(start.promptId())) && retry <= 10) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.errorf(e, "Failed to wait for prompt %s", start.promptId());
            }
            retry++;
        }
        Log.infof("Retried %d times to wait for prompt %s", retry, start.promptId());
        comfyUiTaskDao.markTaskStarted(UUID.fromString(start.promptId()), start.getTimestamp());
    }

    protected static String getServerAddr(Session session) {
        var requestURI = session.getRequestURI();
        return requestURI.getHost() + ":" + requestURI.getPort();
    }
}
