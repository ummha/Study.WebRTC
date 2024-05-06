package org.example.webrtc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 설정 클래스
 * <li>웹 소켓을 사용하기 위한 설정 클래스</li>
 * <li> {@code @EnableWebSocketMessageBroker}을 통해 WebSocket 사용을 나타낸다.</li>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * <li>클라이언트가 웹 소켓 서버에 연결하는데 사용할 웹 소켓 엔드포인트를 등록한다.</li>
     * <li>withSockJS를 통해 웹 소켓을 지원하지 않는 브라우저에 대해 웹 소켓을 대체한다.</li>
     * <li>+ 메소드명에 STOMP 가 들어가는 경우 통신 프로토콜인 STOMP 구현에서 작동된다.</li>
     * @param registry STOMP 엔드포인트 등록 객체
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/signaling") // webSocket 접속시 endpoint 설정
                .setAllowedOrigins("http://localhost:8081")
                .withSockJS(); // 브라우저에서 WebSocket 을 지원하지 않는 경우에 대안으로 어플리케이션의 코드를 변경할 필요 없이 런타임에 필요할 때 대체하기 위해 설정
    }


    /**
     * 한 클라이언트에서 다른 클라이언트로 메시지를 라우팅하는데 사용될 메시지 브로커
     * @param registry 메시지 브로커 등록 객체
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/topic"으로 시작하되는 요정을 구독한 모든 사용자들에게 메시지를 broadcast 한다.
        registry.enableSimpleBroker("/topic"); // broker url 설정
        // "/app"으로 시작되는 메시지는 message-handling methods 로 라우팅된다.
        registry.setApplicationDestinationPrefixes("/app"); // send url 설정
    }
}
