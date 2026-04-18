# Phase 23 — WebSocket 실시간 양방향 통신

## 1. WebSocket이란?

### 1.1 SSE vs WebSocket

```
SSE (Phase 5):                    WebSocket (Phase 23):
서버 → 클라이언트 (단방향)          서버 ↔ 클라이언트 (양방향)
  ↓                                ↕
HTTP 기반 (text/event-stream)     독립 프로토콜 (ws:// 또는 wss://)
자동 재연결 (EventSource)         수동 재연결 (개발자 구현)
텍스트만 전송                      텍스트 + 바이너리
간단한 알림, 이벤트 스트림          채팅, 게임, 실시간 대시보드
```

| 비교 | SSE | WebSocket |
|------|-----|-----------|
| 방향 | 서버→클라이언트 (단방향) | 양방향 |
| 프로토콜 | HTTP | ws:// (별도 프로토콜) |
| 연결 | HTTP 롱 폴링 | TCP 영구 연결 |
| 재연결 | 자동 (EventSource) | 수동 구현 |
| 바이너리 | 불가 | 가능 |
| 적합한 경우 | 알림, 이벤트 스트림 | 채팅, 협업, 양방향 제어 |

### 1.2 WebSocket 연결 흐름

```
1. HTTP 핸드셰이크 (Upgrade 요청)
   GET /ws/events HTTP/1.1
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Key: dGhlIHNhbXBsZQ==

2. 서버 응답 (101 Switching Protocols)
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade

3. WebSocket 연결 수립 — 이후 양방향 메시지 교환
   클라이언트 ↔ 서버 (프레임 단위 양방향 통신)
```

## 2. Spring WebFlux WebSocket

### 2.1 핵심 인터페이스

| 인터페이스 | 역할 | 비유 |
|-----------|------|------|
| `WebSocketHandler` | WebSocket 메시지 처리 로직 | Controller |
| `WebSocketSession` | 개별 WebSocket 연결 세션 | HttpSession |
| `WebSocketMessage` | 전송/수신되는 메시지 | Request/Response Body |
| `HandlerMapping` | URL → Handler 매핑 | @RequestMapping |

### 2.2 WebSocketHandler 구조

```kotlin
class EventWebSocketHandler : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        // session.receive() — 클라이언트로부터 메시지 수신 (Flux<WebSocketMessage>)
        // session.send() — 클라이언트에게 메시지 전송 (Publisher<WebSocketMessage>)

        val output = eventPublisher.getEventStream()
            .map { event -> session.textMessage(json) }

        return session.send(output)
    }
}
```

### 2.3 SSE와의 코드 비교

```
SSE (EventStreamController):
  @GetMapping("/api/events/stream", produces = TEXT_EVENT_STREAM)
  fun streamEvents(): Flux<ServerSentEvent<EventResponse>>

WebSocket (EventWebSocketHandler):
  override fun handle(session: WebSocketSession): Mono<Void>
  → session.send(eventFlux.map { session.textMessage(json) })
```

## 3. WebFlux WebSocket 설정

### 3.1 HandlerMapping + HandlerAdapter

```
Spring MVC:     @Controller + @MessageMapping
Spring WebFlux: WebSocketHandler + SimpleUrlHandlerMapping
```

WebFlux에서는 **어노테이션 기반 WebSocket이 없다**.
`SimpleUrlHandlerMapping`으로 URL을 수동 매핑해야 한다.

```kotlin
@Bean
fun webSocketHandlerMapping(): HandlerMapping {
    val map = mapOf("/ws/events" to eventWebSocketHandler)
    return SimpleUrlHandlerMapping(map, -1) // -1: 높은 우선순위
}

@Bean
fun webSocketHandlerAdapter(): WebSocketHandlerAdapter =
    WebSocketHandlerAdapter()
```

## 4. 양방향 통신 활용

### 4.1 클라이언트 → 서버 메시지

```
클라이언트가 WebSocket으로 명령을 전송:
  {"action": "subscribe", "channel": "BOOKING"}
  {"action": "ping"}

서버가 수신하여 처리:
  session.receive()
    .filter { msg -> msg.type == TEXT }
    .map { msg -> parseCommand(msg.payloadAsText) }
```

### 4.2 이 프로젝트에서의 활용

```
서버 → 클라이언트: 이벤트 스트림 (예약 생성/취소, 재고 변경)
클라이언트 → 서버: ping/pong (연결 유지 확인)
```

- 기존 SSE 스트림을 WebSocket으로도 제공
- 대시보드에서 SSE/WebSocket 중 선택 가능

## 5. 이 프로젝트의 WebSocket 구성

### 5.1 아키텍처

```
브라우저 대시보드
  ├── SSE (/api/events/stream)    ← 기존 Phase 5 (유지)
  └── WebSocket (/ws/events)      ← Phase 23 (새로 추가)
        │
        ▼
  EventWebSocketHandler
        │
        ▼
  EventPublisher (Sinks) ← 동일한 이벤트 소스 공유
```

### 5.2 구현 파일

```
config/
  └── WebSocketConfig.kt (.java)       ← HandlerMapping + HandlerAdapter
handler/
  └── EventWebSocketHandler.kt (.java) ← WebSocket 메시지 처리
static/
  └── js/dashboard.js                  ← WebSocket 연결 옵션 추가
```

### 5.3 SecurityConfig 업데이트

```
/ws/** → permitAll()  (WebSocket 핸드셰이크는 인증 없이)
```
