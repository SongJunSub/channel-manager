package com.channelmanager.java.handler; // 핸들러 패키지

import com.channelmanager.java.dto.EventResponse; // 이벤트 응답 DTO
import com.channelmanager.java.service.EventPublisher; // 이벤트 발행 서비스 (Sinks)
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 직렬화
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.stereotype.Component; // 빈 등록
import org.springframework.web.reactive.socket.WebSocketHandler; // WebSocket 핸들러 인터페이스
import org.springframework.web.reactive.socket.WebSocketSession; // WebSocket 세션
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// WebSocket 이벤트 핸들러 — EventPublisher의 이벤트를 WebSocket으로 실시간 전송한다
// Kotlin에서는 class EventWebSocketHandler(...) : WebSocketHandler이지만,
// Java에서는 implements WebSocketHandler이다
@Component
@RequiredArgsConstructor
public class EventWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventWebSocketHandler.class);

    private final EventPublisher eventPublisher; // Phase 4: Sinks 이벤트 허브
    private final ObjectMapper objectMapper;     // JSON 직렬화

    // handle — WebSocket 연결이 수립되면 호출된다
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket 클라이언트 연결됨: {}", session.getId());

        // 1. 서버 → 클라이언트: 이벤트 스트림을 WebSocket 메시지로 전송
        var output = eventPublisher.getEventStream()
            .map(event -> {
                try {
                    // ChannelEvent → EventResponse DTO → JSON 문자열
                    var json = objectMapper.writeValueAsString(EventResponse.from(event));
                    return session.textMessage(json);
                } catch (Exception e) {
                    // JSON 직렬화 실패 시 에러 메시지로 대체
                    return session.textMessage("{\"error\":\"직렬화 실패\"}");
                }
            })
            .doOnCancel(() -> log.info("WebSocket 클라이언트 연결 해제됨: {}", session.getId()));

        // 2. 클라이언트 → 서버: 수신 메시지 처리 (ping/pong 등)
        var input = session.receive()
            .doOnNext(message ->
                log.debug("WebSocket 메시지 수신: {}", message.getPayloadAsText())
            )
            .then();

        // 3. 송신(output)과 수신(input)을 동시에 처리
        return Mono.zip(session.send(output), input).then();
    }
}
