package com.channelmanager.kotlin.config // 설정 패키지

import com.channelmanager.kotlin.handler.EventWebSocketHandler // WebSocket 이벤트 핸들러
import org.springframework.context.annotation.Bean // 빈 등록
import org.springframework.context.annotation.Configuration // 설정 클래스
import org.springframework.web.reactive.HandlerMapping // URL → Handler 매핑 인터페이스
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping // URL 수동 매핑
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter // WebSocket 어댑터

// WebSocket 설정 — URL 매핑 및 어댑터 등록
// Spring WebFlux에서는 @Controller + @MessageMapping 방식의 WebSocket이 없다
// SimpleUrlHandlerMapping으로 URL을 WebSocketHandler에 수동 매핑해야 한다
// WebSocketHandlerAdapter: Spring WebFlux가 WebSocketHandler를 인식하도록 하는 어댑터
@Configuration
class WebSocketConfig(
    private val eventWebSocketHandler: EventWebSocketHandler // WebSocket 이벤트 핸들러
) {

    // URL → WebSocketHandler 매핑
    // /ws/events 경로로 WebSocket 연결이 들어오면 EventWebSocketHandler가 처리한다
    // SimpleUrlHandlerMapping: URL 패턴을 Handler에 매핑하는 Spring의 기본 구현
    //   — 두 번째 파라미터(-1): 우선순위 — RouterFunction보다 먼저 평가되도록 높은 우선순위 설정
    //   — RequestMapping 기반 컨트롤러보다 먼저 WebSocket 핸드셰이크를 처리해야 한다
    @Bean
    fun webSocketHandlerMapping(): HandlerMapping {
        // URL 패턴 → Handler 매핑
        val map = mapOf("/ws/events" to eventWebSocketHandler)
        // SimpleUrlHandlerMapping(map, order): URL 매핑 + 우선순위
        return SimpleUrlHandlerMapping(map, -1)
    }

    // WebSocketHandlerAdapter — WebSocketHandler를 Spring WebFlux에 연결하는 어댑터
    // 이 빈이 없으면 Spring WebFlux가 WebSocketHandler를 인식하지 못하고
    // "No suitable handler found" 에러가 발생한다
    // Spring MVC에서는 @EnableWebSocket으로 자동 등록되지만,
    // WebFlux에서는 명시적으로 빈을 등록해야 한다
    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter =
        WebSocketHandlerAdapter()
}
