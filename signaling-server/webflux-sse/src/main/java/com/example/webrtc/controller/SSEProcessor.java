package com.example.webrtc.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SSEProcessor {

    private final Map<String, Sinks.Many<ServerSentEvent<Object>>> sinks = new HashMap<>();

    /**
     * send notification to user
     */
    public void personalSend(MessageDto dto, String userId) {
        if(sinks.containsKey(userId)) {
            sinks.get(userId).tryEmitNext(ServerSentEvent.builder()
                    .event("message")
                    .data(dto)
                    .id(dto.getId())
                    .comment(dto.getMessage())
                    .build());
        }
    }

    /**
     * send notification to users
     */
    public void groupSend(MessageDto dto, List<String> userIds) {
        userIds.forEach(userId -> personalSend(dto, userId));
    }

    public Mono<Boolean> successMessageSend(String userId) {
        return Mono.just(userId)
                .flatMap(id -> {
                    if(sinks.containsKey(id)) {
                        sinks.get(id).tryEmitNext(ServerSentEvent.builder()
                                .event("config")
                                .data("connected successfully data")
                                .comment("connected successfully comment")
                                .build());
                        return Mono.just(true);
                    }
                    return Mono.error(new Exception("존재하지 않는 SSE 채널입니다."));
                });
    }

    /**
     * Connect to SSE
     */
    public Flux<ServerSentEvent<Object>> connect(String userId) {
        if (sinks.containsKey(userId)) {
            return sinks.get(userId).asFlux();
        }

        sinks.put(userId, Sinks.many().multicast().onBackpressureBuffer());
        return sinks.get(userId).asFlux().doOnCancel(() -> {
            log.info("##> SSE Notification cancelled by client: {}", userId);
            this.finish(userId);
        });
    }

    public void finish(String userId) {
        sinks.get(userId).tryEmitComplete();
        sinks.remove(userId);
    }
}
