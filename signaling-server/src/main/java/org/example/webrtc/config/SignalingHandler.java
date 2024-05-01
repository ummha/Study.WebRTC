package org.example.webrtc.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 메시지를 처리할 WebSocketHandler 의 구현체.
 * 이 핸들러는 연결된 클라이언트로부터 메시지를 받고, 적절히 처리하여 다시 클라이언트에게 메시지를 보내는 기능을 수행
 */
@Slf4j
@Component
public class SignalingHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    @NonNull
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessionStates.put(session.getId(), new SessionState(session.getId()));
        log.info("sessionStates: {}", sessionStates);
        return session.receive()
                .flatMap(msg -> processMessage(session, msg.getPayloadAsText()))
                .onErrorResume(e -> handleError(session, e))
                .doFinally(signalType -> cleanUp(session, signalType))
                .then();
    }

    private Mono<Void> processMessage(WebSocketSession session, String jsonMessage) {
        try {
//            Map<String, String> message = objectMapper.readValue(jsonMessage, new TypeReference<Map<String, String>>() {});
            Map<String, Object> message = objectMapper.readValue(jsonMessage, new TypeReference<Map<String, Object>>() {});
            Object type = message.get("type");
            Object data = message.get("data");
            log.info("Received message {}", type);

            String processedMessage = switch ((String)type) {
                case "offer" ->
                        objectMapper.writeValueAsString(Map.of("type", "offerResponse", "data", data));
                case "answer" ->
                        objectMapper.writeValueAsString(Map.of("type", "answerResponse", "data", data));
                case "iceCandidate" ->
                        objectMapper.writeValueAsString(Map.of("type", "iceCandidateResponse", "data", data));
                default -> objectMapper.writeValueAsString(Map.of("type", "error", "data", "Unknown message type"));
            };
            sessionStates.values().stream()
                    .filter(sessionState -> !sessionState.getSessionId().equals(session.getId()))
                    .forEach(sessionState -> {
                        try {
                            session.send(Mono.just(session.textMessage(processedMessage))).subscribe();
                        } catch (Exception e) {
                            log.error("Error sending message {}", processedMessage, e);
                        }
                    });
            return Mono.empty();
        } catch (Exception e) {
//            handleError(session, e);
            return Mono.error(e);
        }
    }

    private Mono<Void> handleError(WebSocketSession session, Throwable e) {
        log.error("Error handling message: {}", e.getMessage());
        return session.send(Mono.just(session.textMessage("{\"type\":\"error\",\"data\":\"" + e.getMessage() + "\"}")));
    }

    private void cleanUp(WebSocketSession session, SignalType signalType) {
        sessionStates.remove(session.getId());
        log.info("Session {} cleaned up after {}", session.getId(), signalType);
    }
}
