package com.example.webrtc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class WebsocketConfig {
    private final SignalMessageHandler signalMessageHandler;

    /**
     * 웹 소켓 핸들러 매핑 객체를 빈으로 등록하는 메소드로서,
     * 시그널 메시지 핸들러 클래스를 "/signal" 이라는 웹 소켓 요청(ws)이 올 경우 사용하겠다는 내용이다.
     * @return 핸들러 매핑
     */
    @Bean
    HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/signal", signalMessageHandler);
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(1);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }
}
