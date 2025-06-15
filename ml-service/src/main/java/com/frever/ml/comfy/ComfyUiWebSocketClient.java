package com.frever.ml.comfy;

import static com.frever.ml.comfy.ComfyUiManager.RUNNING;
import static com.frever.ml.comfy.ComfyWebSocketConstant.EXECUTING;
import static com.frever.ml.comfy.ComfyWebSocketConstant.EXECUTION_ERROR;
import static com.frever.ml.comfy.ComfyWebSocketConstant.EXECUTION_START;
import static com.frever.ml.comfy.ComfyWebSocketConstant.EXECUTION_SUCCESS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.frever.ml.comfy.dto.ComfyExecuting;
import com.frever.ml.comfy.dto.ComfyExecutionError;
import com.frever.ml.comfy.dto.ComfyExecutionSuccess;
import com.frever.ml.comfy.dto.ComfyWebsocketMessage;
import com.frever.ml.comfy.messaging.ComfyUiCloseSessionSignal;
import com.frever.ml.comfy.messaging.ComfyUiPromptCompleted;
import com.frever.ml.comfy.messaging.ComfyUiSessionClosed;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

@ClientEndpoint
@ApplicationScoped
public class ComfyUiWebSocketClient extends AbstractWebSocketClient {
    @Inject
    Event<ComfyUiSessionClosed> comfyUiSessionClosedEvent;
    @Inject
    Event<ComfyUiCloseSessionSignal> comfyUiCloseSessionSignalEvent;
    @Inject
    Event<ComfyUiPromptCompleted> comfyUiPromptCompletedEvent;
    @Inject
    Event<ComfyExecutionError> comfyExecutionErrorEvent;

    @OnOpen
    public void open(Session session) {
        var serverAddr = getServerAddr(session);
        Log.infof("Connected to ComfyUi websockets, sessionId: %s, server addr: %s", session.getId(), serverAddr);
    }

    @OnMessage
    public void message(String message, Session session) {
        super.onMessage(message, session);
    }

    protected void handleWebsocketMessage(Session session, ComfyWebsocketMessage websocketMessage)
        throws JsonProcessingException {
        var serverAddr = getServerAddr(session);
        switch (websocketMessage.type()) {
            case EXECUTING -> {
                ComfyExecuting executing =
                    objectMapper.readValue(websocketMessage.data().toString(), ComfyExecuting.class);
                Log.infof("From %s, Received executing: %s", serverAddr, executing);
                if (executing.node() == null && executing.displayNode() == null && executing.promptId() != null) {
                    Log.infof("On %s, Prompt %s is completed", serverAddr, executing.promptId());
                    comfyUiPromptCompletedEvent.fire(new ComfyUiPromptCompleted(serverAddr, executing.promptId()));
                }
                if (executing.node() != null && executing.displayNode() == null && executing.promptId() != null) {
                    Log.infof("On %s, No prompt Id, but executing, let's try reconnect.", serverAddr);
                    comfyUiCloseSessionSignalEvent.fire(new ComfyUiCloseSessionSignal(serverAddr));
                }
            }
            case EXECUTION_SUCCESS -> {
                ComfyExecutionSuccess success = objectMapper.readValue(
                    websocketMessage.data().toString(),
                    ComfyExecutionSuccess.class
                );
                Log.infof("From %s, Received execution success: %s", serverAddr, success);
                comfyUiPromptCompletedEvent.fire(new ComfyUiPromptCompleted(serverAddr, success.promptId()));
            }
            case EXECUTION_START -> handleExecutionStart(websocketMessage);
            case EXECUTION_ERROR -> handleExecutionError(websocketMessage);
            case null, default -> {
                Log.infof("From %s, Received unknown message: %s", serverAddr, websocketMessage);
            }
        }
    }

    private void handleExecutionError(ComfyWebsocketMessage websocketMessage) throws JsonProcessingException {
        ComfyExecutionError error = objectMapper.readValue(
            websocketMessage.data().toString(),
            ComfyExecutionError.class
        );
        Log.infof("Received execution error: %s", error);
        comfyExecutionErrorEvent.fire(error);
    }

    @OnClose
    public void close(Session session, CloseReason closeReason) {
        Log.infof("Session %s at %s closed due to: %s", session.getId(), getServerAddr(session), closeReason);
        if (!RUNNING) {
            Log.info("Not running, will not reconnect.");
            return;
        }
        comfyUiSessionClosedEvent.fire(new ComfyUiSessionClosed(getServerAddr(session)));
    }

    @OnError
    public void error(Session session, Throwable throwable) {
        Log.errorf(throwable, "Session %s at %s got error", session.getId(), getServerAddr(session));
    }
}
