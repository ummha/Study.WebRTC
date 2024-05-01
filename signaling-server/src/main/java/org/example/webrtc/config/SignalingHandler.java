package org.example.webrtc.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 메시지를 처리할 WebSocketHandler 의 구현체.
 * 이 핸들러는 연결된 클라이언트로부터 메시지를 받고, 적절히 처리하여 다시 클라이언트에게 메시지를 보내는 기능을 수행
 */
//1. SDP 협상의 필요성
//**SDP(Session Description Protocol)**는 미디어 통신을 위한 세션 설정을 기술하는 포맷입니다. SDP 협상을 통해 두 피어는 서로의 미디어 포맷, 코덱 정보, 암호화 옵션, 통신을 위한 포트 번호 등을 교환합니다. 이 정보를 바탕으로 각 피어는 다음과 같은 항목을 결정하게 됩니다:
//
//미디어 타입: 오디오, 비디오, 데이터 등
//코덱 선택: 각 미디어 타입에 사용할 코덱
//트랜스포트 주소: 미디어 스트림을 송수신할 IP 주소와 포트
//보안 설정: 미디어 스트림의 암호화 및 인증 메커니즘
//SDP 협상은 WebRTC 연결 초기화 단계에서 발생하며, offer/answer 모델을 사용합니다. 한 피어가 SDP 오퍼를 생성하여 다른 피어에게 전송하고, 수신 피어는 이를 기반으로 SDP 앤서를 생성하여 응답합니다. 이 과정을 통해 두 피어는 서로의 미디어 설정에 동의하고, 통신이 가능한 상태를 확립합니다.
//
//        2. ICE 후보 처리의 필요성
//**ICE(Interactive Connectivity Establishment)**는 네트워크 연결을 위한 후보 경로들을 수집하고 테스트하여, 최적의 경로를 찾는 프로토콜입니다. ICE 프로세스는 다음과 같은 후보를 포함합니다:
//
//호스트 후보: 피어의 직접적인 로컬 네트워크 주소
//서버 반사 후보(STUN): 공용 인터넷에 위치한 피어의 주소를 반사하여 얻은 후보
//중계 후보(TURN): TURN 서버를 통해 미디어가 중계될 때 사용되는 후보
//ICE 프로세스의 주요 목적은 다음과 같습니다:
//
//방화벽과 NAT 트래버설: 대부분의 네트워크 환경은 방화벽이나 NAT(Network Address Translation)에 의해 보호됩니다. ICE는 이러한 네트워크 장벽을 넘어서 서로 통신할 수 있는 경로를 찾습니다.
//최적 경로 선택: 다양한 가능한 연결 경로 중에서 지연 시간이 가장 짧고 안정적인 경로를 선택합니다.
//ICE 후보 처리 과정은 연결 설정 중에 중요한 단계이며, 효율적인 미디어 스트림 전송을 위해 최적의 네트워크 경로를 확립하는 데 필수적입니다.
@Slf4j
@Component
public class SignalingHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    @NonNull
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        sessionStates.put(session.getId(), new SessionState(session.getId()));

        return session.receive()
                .flatMap(msg -> processMessage(session, msg.getPayloadAsText()))
                .onErrorResume(e -> handleError(session, e))
                .doFinally(signalType -> cleanUp(session, signalType))
                .then();
    }

    private Mono<Void> processMessage(WebSocketSession session, String jsonMessage) {
        try {
//            Map<String, String> message = objectMapper.readValue(jsonMessage, new TypeReference<Map<String, String>>() {});
            Map<String, String> message = objectMapper.readValue(jsonMessage, new TypeReference<>() {});
            String type = message.get("type");
            String data = message.get("data");

            String processedMessage = switch (type) {
                case "offer" ->
                        objectMapper.writeValueAsString(Map.of("type", "offerResponse", "data", "Offer received: " + data));
                case "answer" ->
                        objectMapper.writeValueAsString(Map.of("type", "answerResponse", "data", "Answer received: " + data));
                case "iceCandidate" ->
                        objectMapper.writeValueAsString(Map.of("type", "iceCandidateResponse", "data", "ICE Candidate received: " + data));
                default -> objectMapper.writeValueAsString(Map.of("type", "error", "data", "Unknown message type"));
            };

            return session.send(Mono.just(session.textMessage(processedMessage)));
        } catch (Exception e) {
            return handleError(session, e);
        }
    }

    private Mono<Void> handleError(WebSocketSession session, Throwable e) {
        log.error("Error handling message: {}", e.getMessage());
        return session.send(Mono.just(session.textMessage("{\"type\": \"error\", \"data\": \"" + e.getMessage() + "\"}")));
    }

    private void cleanUp(WebSocketSession session, SignalType signalType) {
        sessionStates.remove(session.getId());
        log.info("Session {} cleaned up after {}", session.getId(), signalType);
    }
}
