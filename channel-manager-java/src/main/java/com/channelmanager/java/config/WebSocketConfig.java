package com.channelmanager.java.config; // 설정 패키지

import com.channelmanager.java.handler.EventWebSocketHandler; // WebSocket 이벤트 핸들러
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성
import org.springframework.context.annotation.Bean; // 빈 등록
import org.springframework.context.annotation.Configuration; // 설정 클래스
import org.springframework.web.reactive.HandlerMapping; // URL → Handler 매핑 인터페이스
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping; // URL 수동 매핑
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter; // WebSocket 어댑터

import java.util.Map; // Map

// WebSocket 설정 — URL 매핑 및 어댑터 등록
// Kotlin에서는 class WebSocketConfig(val eventWebSocketHandler)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final EventWebSocketHandler eventWebSocketHandler;

    // URL → WebSocketHandler 매핑
    // /ws/events 경로로 WebSocket 연결이 들어오면 EventWebSocketHandler가 처리한다
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        var map = Map.of("/ws/events", (Object) eventWebSocketHandler);
        // SimpleUrlHandlerMapping: URL 패턴 → Handler 매핑
        // -1: 높은 우선순위 — RequestMapping 기반 컨트롤러보다 먼저 WebSocket 핸드셰이크 처리
        return new SimpleUrlHandlerMapping(map, -1);
    }

    // WebSocketHandlerAdapter — WebSocketHandler를 Spring WebFlux에 연결하는 어댑터
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
