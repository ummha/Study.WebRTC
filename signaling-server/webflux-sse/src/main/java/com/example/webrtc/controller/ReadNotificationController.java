package com.example.webrtc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReadNotificationController {
    private final SSEProcessor sseProcessor;

    // SSE는 text/event-stream 으로 처리해야 하기 때문에 적용시켜야 함.
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> sseConnect(@RequestHeader("userId") String userId) {
        return sseProcessor.connect(userId);
    }

    @GetMapping(value = "/sse/connect")
    public Mono<ResponseEntity<Boolean>> sseSuccessConnection(@RequestHeader("userId") String userId) {
        return sseProcessor.successMessageSend(userId)
                .map(isSuccess -> new ResponseEntity<>(isSuccess, HttpStatus.OK));
    }

    @GetMapping(value = "/send/{userId}")
    public Mono<ResponseEntity<Boolean>> sendMessage(@PathVariable("userId") String userId) {
        MessageDto messageDto = new MessageDto();
        messageDto.setId(userId);
        messageDto.setMessage("new message sent");
        messageDto.setLevel("1");
        sseProcessor.personalSend(messageDto, userId);
        return Mono.just(new ResponseEntity<>(true, HttpStatus.OK));
    }

    @PostMapping(value = "/send")
    public Mono<ResponseEntity<Boolean>> sendMessageGroup(@RequestBody SendGroup request) {
        log.info("{}", request);
        MessageDto messageDto = new MessageDto();
        messageDto.setId(UUID.randomUUID().toString());
        messageDto.setMessage("new message sent");
        messageDto.setLevel("1");
        sseProcessor.groupSend(messageDto, request.getTo());
        return Mono.just(new ResponseEntity<>(true, HttpStatus.OK));
    }
}
