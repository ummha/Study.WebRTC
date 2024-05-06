package com.example.webrtc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Objects;

/**
 * What is Sinks?
 * Sinks는 Flux와 Mono의 의미 체계를 이용하여 Reactor Stream의 Signal들을 프로그래밍적으로 Push 할 수 있게 해주는 구성체이다.
 *
 * 독립적인 Sinks는 Sinks.EmitResult enum을 내보내는 tryEmit Method를 진행할때, 시도한 신호가 싱크의 사양 또는 상태와 일치하지 않을 경우
 * 원자적으로 실패할 수 있다.
 *
 * Sinks는 Sinks.Many Builder, Sinks.One factories의 Collection으로 내보낼 수 있다.
 * 안전하지 않은 사용으로 구성되지 않는 한, Sinks는 동시 접근과 여러 시도 중 한 번의 빠른 실패를 감지해서 스레드가 안전하다.
 * 반면 안전하지 않은 Sinks의 외부에서 동기화될 것으로 예상된다. (일반적으로 Subscriber 또는 operator 같은 Reactive Stream-compliant context
 * 으로부터 호출되어 진행된다, 즉 Sinks 자체에서 동시 액세스를 감지하는 오버헤드를 제거해도 괜찮다.)
 */
@Slf4j
@Component
public class SignalMessageHandler implements WebSocketHandler {
    /*
     * factory 에서 메시지를 보관하고 찍어내어 전달할 수 있는 객체
     */
    Sinks.Many<WebSocketMessage> factory = // 발행자
            Sinks.many() // 여러 건의 데이터 전송, ManySpec에 따라 emit 전달
                    .multicast() // 다수의 Subscriber에게 broadcast 형식으로 Signal을 보내는 Sink.Many를 생성
                    .onBackpressureBuffer();
    /**
     * directXXX();
     * direct: 요소를 생성하는 데 직접적으로 메인 스레드를 사용, 메인 스레드의 부담이 커지고, I/O 등의 블로킹 작업이 발생할 경우 전체 애플리케이션 성능에 영향을 줄 수 있다.
     * AllOrNothing: 요소를 처리할 수 없는 Subscriber가 존재할 경우, 해당 요소를 처리할 수 있는 다른 Subscriber 에게도 전달되지 않는다. 모두 요소를 처리해야 데이터가 전달된다.
     * BestEffort: 요소를 처리하지 못하는 Subscriber가 존재하더라도, 처리할 수 있는 Subscriber에게는 데이터를 전달하려고 최대한 노력한다.
     * onBackpressureBuffer: warmup 에서 생긴 데이터들을 Buffer에 저장하여 관리한 뒤, 첫 Subscriber로 의해 hot sequence 변경되어 Buffer에 있던 데이터들을 전달받는다.
     *                       hot sequence 에서는 multicast 방식ㅇ으로 진행되어서 모든 Subscriber에게 데이터를 전달한다.
     */

    /*
     * 실제로 전달하는 객체
     */
    Flux<WebSocketMessage> deliveryCar = factory.asFlux(); // 구독을 위한 객체
    /*
     *
     */
    Sinks.EmitFailureHandler company = ((signalType, emitResult) -> emitResult.equals(Sinks.EmitResult.FAIL_NON_SERIALIZED));

    @Override
    public Mono<Void> handle(WebSocketSession session) {

        log.info("##> connection start : {}", session.getId());

        session.receive() // 메시지를 수신하는 행동의 정의를 시작한다.
                .map(WebSocketMessage::getPayloadAsText) // 수신한 데이터를 텍스트로 바꿔서,
                .concatMap(arg -> { // 특정 조건을 통해 메시지를 처리한다.
                    /* 아래 코드와 같이 조건에 따라 메시지를 처리할 수 있다.
                    if(arg.equals("DB에서 조회하고 없으면 NULL을 널어서 메시지를 못 받게 하는 ... 조건 겉은 프로세스")) {
                        return Mono.justOrEmpty(null); // 또는 session.close() 통해 강제 종료
                    }
                    */
                    return Mono.justOrEmpty(session.textMessage(arg));
                })
                .doOnError(arg -> { // 오류에 대한 정의
                    log.error("##> connection error : {}", arg.getMessage());
                })
                .doFinally(arg -> { // 커넥션이 끊긴 경우에 대한 정의
                    log.warn("##> connection end : {}", session.getId());
                })
                .subscribe((webSocketMessage) -> { // 서버가 사용자에게 메시지를 받으면 할 행동에 대한 정의
                    if (webSocketMessage != null) {
                        factory.emitNext(webSocketMessage, company); // 메시지 전송
                    }
                });

        return session.send(
                Mono.delay(Duration.ofMillis(10))
                        .thenMany(deliveryCar.filter(Objects::nonNull)
                                .map(it -> session.textMessage(it.getPayloadAsText()))
                        ));
    }
}
