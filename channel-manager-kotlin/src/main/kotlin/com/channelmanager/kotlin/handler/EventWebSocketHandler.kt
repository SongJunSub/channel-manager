package com.channelmanager.kotlin.handler // 핸들러 패키지

import com.channelmanager.kotlin.dto.EventResponse // 이벤트 응답 DTO
import com.channelmanager.kotlin.service.EventPublisher // 이벤트 발행 서비스 (Sinks)
import com.fasterxml.jackson.databind.ObjectMapper // JSON 직렬화
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.stereotype.Component // 빈 등록
import org.springframework.web.reactive.socket.WebSocketHandler // WebSocket 핸들러 인터페이스
import org.springframework.web.reactive.socket.WebSocketSession // WebSocket 세션
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.Duration // 시간 간격

// WebSocket 이벤트 핸들러 — EventPublisher의 이벤트를 WebSocket으로 실시간 전송한다
// Phase 5의 SSE(EventStreamController)와 동일한 EventPublisher(Sinks)를 구독하여
// 같은 이벤트를 WebSocket 프로토콜로 전달한다
// SSE와의 차이:
//   - SSE: 서버→클라이언트 단방향, HTTP 기반, EventSource API
//   - WebSocket: 양방향, ws:// 프로토콜, WebSocket API
// WebSocketHandler: Spring WebFlux의 WebSocket 핸들러 인터페이스
//   — handle(session): 연결이 수립되면 호출되어, 메시지 송수신을 처리한다
@Component
class EventWebSocketHandler(
    private val eventPublisher: EventPublisher, // Phase 4: Sinks 이벤트 허브
    private val objectMapper: ObjectMapper      // JSON 직렬화 (EventResponse → JSON 문자열)
) : WebSocketHandler {

    // SLF4J 로거
    companion object {
        private val log = LoggerFactory.getLogger(EventWebSocketHandler::class.java)
    }

    // handle — WebSocket 연결이 수립되면 호출된다
    // session: 개별 클라이언트와의 WebSocket 연결을 나타낸다
    //   — session.send(): 클라이언트에게 메시지 전송 (Publisher<WebSocketMessage> 받음)
    //   — session.receive(): 클라이언트로부터 메시지 수신 (Flux<WebSocketMessage> 반환)
    //   — session.textMessage(): 텍스트 WebSocket 메시지 생성
    // 반환: Mono<Void> — 연결이 종료되면 완료 시그널
    override fun handle(session: WebSocketSession): Mono<Void> {
        log.info("WebSocket 클라이언트 연결됨: {}", session.id)

        // 1. 서버 → 클라이언트: 이벤트 스트림을 WebSocket 메시지로 변환하여 전송
        // EventPublisher.getEventStream(): Sinks에서 발행되는 Flux<ChannelEvent>
        // .map: ChannelEvent → EventResponse → JSON 문자열 → WebSocketMessage 변환
        val output = eventPublisher.getEventStream()
            .map { event ->
                // ChannelEvent → EventResponse DTO → JSON 문자열
                // try-catch: 직렬화 실패 시 에러 메시지로 대체하여 스트림을 유지한다
                // catch 없이 예외가 전파되면 WebSocket 연결이 종료된다
                val json = try {
                    objectMapper.writeValueAsString(EventResponse.from(event))
                } catch (e: Exception) {
                    log.warn("WebSocket 이벤트 직렬화 실패", e)
                    """{"error":"직렬화 실패"}"""
                }
                // session.textMessage(): 텍스트 타입 WebSocket 프레임 생성
                session.textMessage(json)
            }
            .doOnCancel { log.info("WebSocket 클라이언트 연결 해제됨: {}", session.id) }

        // 2. 클라이언트 → 서버: 수신 메시지 처리 (ping/pong 등)
        // session.receive(): 클라이언트가 보내는 메시지를 Flux로 수신
        // 현재는 수신 메시지를 로그만 남기고 별도 처리하지 않는다
        // 향후 {"action":"subscribe","channel":"BOOKING"} 같은 필터링 명령을 처리할 수 있다
        val input = session.receive()
            .doOnNext { message ->
                log.debug("WebSocket 메시지 수신: {}", message.payloadAsText)
            }
            .then() // 수신 처리를 Mono<Void>로 완료 (결과는 무시)

        // 3. 송신(output)과 수신(input)을 동시에 처리
        // session.send(output): 이벤트 스트림을 클라이언트에 전송
        // Mono.zip(): 두 Mono를 병렬로 실행하고 모두 완료될 때까지 대기
        //   — 클라이언트가 연결을 끊으면 send와 receive 모두 종료된다
        return Mono.zip(session.send(output), input).then()
    }
}
