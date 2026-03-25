# Phase 5 — SSE (Server-Sent Events) 실시간 이벤트 스트리밍

## 1. SSE란 무엇인가?

### 1.1 개요

SSE(Server-Sent Events)는 **서버에서 클라이언트로 단방향 실시간 데이터를 전송**하는 웹 표준 기술이다.
HTTP 프로토콜 위에서 동작하며, 서버가 클라이언트에게 이벤트를 "푸시"할 수 있다.

```
┌─────────┐         HTTP 연결 유지 (text/event-stream)         ┌──────────┐
│  서버   │ ──────── data: {"type":"RESERVATION_CREATED"} ──→  │ 클라이언트│
│ (Spring │ ──────── data: {"type":"INVENTORY_UPDATED"}   ──→  │ (브라우저)│
│ WebFlux)│ ──────── data: {"type":"CHANNEL_SYNCED"}      ──→  │          │
└─────────┘                                                     └──────────┘
    ↑ 서버만 데이터를 보낸다 (단방향)
```

### 1.2 SSE vs WebSocket vs 폴링

| 특성 | SSE | WebSocket | HTTP 폴링 |
|------|-----|-----------|-----------|
| **방향** | 서버 → 클라이언트 (단방향) | 양방향 | 클라이언트 → 서버 (요청마다) |
| **프로토콜** | HTTP/1.1, HTTP/2 | ws:// (별도 프로토콜) | HTTP |
| **자동 재연결** | 내장 (브라우저가 자동으로) | 직접 구현해야 함 | 해당 없음 |
| **데이터 형식** | 텍스트 (UTF-8) | 텍스트 + 바이너리 | 자유 |
| **방화벽** | HTTP라 통과 쉬움 | ws:// 차단 가능 | 통과 쉬움 |
| **적합한 사례** | 알림, 대시보드, 로그 | 채팅, 게임 | 이메일 확인 |

**왜 SSE를 선택하는가?**
- 채널 매니저는 **서버→클라이언트 단방향 이벤트 전달**만 필요하다
- 예약 생성, 재고 변경, 채널 동기화 이벤트를 대시보드에 실시간으로 표시한다
- WebSocket은 양방향이 필요할 때 쓰며, 우리 시나리오에서는 과하다(overkill)
- SSE는 HTTP 위에서 동작하여 기존 인프라(로드밸런서, 프록시)와 호환된다

## 2. SSE 프로토콜 상세

### 2.1 HTTP 응답 형식

SSE 응답은 `Content-Type: text/event-stream`으로 전송된다.
HTTP 연결을 끊지 않고 유지하면서, 서버가 이벤트를 줄 단위로 보낸다.

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

event: RESERVATION_CREATED
id: 42
data: {"eventType":"RESERVATION_CREATED","channelId":1,"guestName":"김민준"}

event: CHANNEL_SYNCED
id: 43
data: {"eventType":"CHANNEL_SYNCED","channelId":2,"roomTypeId":1}

```

### 2.2 SSE 이벤트 필드

| 필드 | 설명 | 예시 |
|------|------|------|
| `data:` | **필수.** 이벤트 데이터 (여러 줄 가능) | `data: {"type":"CREATED"}` |
| `event:` | 이벤트 타입 이름 (클라이언트가 addEventListener로 구분) | `event: RESERVATION_CREATED` |
| `id:` | 이벤트 ID (재연결 시 Last-Event-ID 헤더로 전송) | `id: 42` |
| `retry:` | 재연결 대기 시간(ms) (브라우저 기본값: 3초) | `retry: 5000` |

```
각 이벤트는 빈 줄(\n\n)로 구분된다
필드 내 줄바꿈은 여러 data: 라인으로 표현한다

data: 첫 번째 줄
data: 두 번째 줄

→ 클라이언트에서는 "첫 번째 줄\n두 번째 줄"로 수신
```

### 2.3 자동 재연결

SSE의 핵심 장점 중 하나는 **브라우저가 자동으로 재연결**한다는 것이다.

```
1. 서버가 연결을 끊거나 네트워크 오류 발생
2. 브라우저가 retry 시간(기본 3초) 후 자동 재연결
3. 재연결 시 Last-Event-ID 헤더에 마지막으로 받은 id 값을 전송
4. 서버는 이 ID 이후의 이벤트만 전송 가능 (구현에 따라)
```

## 3. Spring WebFlux에서 SSE 구현

### 3.1 ServerSentEvent<T> 타입

Spring WebFlux는 `ServerSentEvent<T>` 클래스를 제공하여 SSE 프로토콜의 모든 필드를 제어할 수 있다.

```kotlin
// ServerSentEvent 빌더 패턴
ServerSentEvent.builder<EventResponse>()
    .id("42")                              // id: 42
    .event("RESERVATION_CREATED")          // event: RESERVATION_CREATED
    .data(eventResponse)                   // data: {"eventType":...}
    .retry(Duration.ofSeconds(5))          // retry: 5000
    .build()
```

**ServerSentEvent를 사용하는 이유:**
- `data`만 보내려면 `Flux<T>`를 반환해도 된다 (Spring이 자동으로 `data:` 래핑)
- 하지만 `event`, `id`, `retry` 필드를 제어하려면 `Flux<ServerSentEvent<T>>`가 필요하다
- 우리는 `event` 타입으로 이벤트를 구분하고, `id`로 재연결을 지원해야 한다

### 3.2 Controller에서 SSE 엔드포인트

```kotlin
// Kotlin — SSE 엔드포인트 예시
@GetMapping("/api/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun streamEvents(): Flux<ServerSentEvent<EventResponse>> {
    return eventPublisher.getEventStream()     // Sinks → Flux<ChannelEvent>
        .map { event ->                        // ChannelEvent → ServerSentEvent 변환
            ServerSentEvent.builder<EventResponse>()
                .id(event.id.toString())       // 이벤트 ID
                .event(event.eventType.name)   // 이벤트 타입
                .data(EventResponse.from(event)) // 응답 DTO
                .build()
        }
}
```

```java
// Java — SSE 엔드포인트 예시
@GetMapping(value = "/api/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<EventResponse>> streamEvents() {
    return eventPublisher.getEventStream()
        .map(event -> ServerSentEvent.<EventResponse>builder()
            .id(event.getId().toString())
            .event(event.getEventType().name())
            .data(EventResponse.from(event))
            .build());
}
```

### 3.3 produces = TEXT_EVENT_STREAM_VALUE

```kotlin
@GetMapping("/api/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
```

- `produces`: 이 엔드포인트가 생성하는 Content-Type을 명시한다
- `TEXT_EVENT_STREAM_VALUE` = `"text/event-stream"`
- Spring WebFlux는 이 설정을 보고:
  1. HTTP 응답의 `Content-Type`을 `text/event-stream`으로 설정한다
  2. 연결을 끊지 않고 유지한다 (keep-alive)
  3. Flux의 각 요소를 SSE 형식(`data:`, `event:`, `id:`)으로 직렬화한다

### 3.4 Heartbeat (연결 유지)

SSE 연결은 장시간 유지되므로, 프록시나 로드밸런서가 유휴 연결을 끊을 수 있다.
이를 방지하기 위해 **heartbeat(하트비트)** 이벤트를 주기적으로 전송한다.

```kotlin
// 30초마다 빈 comment를 보내 연결을 유지한다
val heartbeat = Flux.interval(Duration.ofSeconds(30))
    .map {
        ServerSentEvent.builder<EventResponse>()
            .comment("heartbeat")  // : heartbeat (comment는 클라이언트에서 무시)
            .build()
    }

// 실제 이벤트 스트림과 하트비트를 merge
return Flux.merge(eventStream, heartbeat)
```

**comment 필드:**
- `:` 으로 시작하는 줄은 SSE 프로토콜에서 주석(comment)으로 취급된다
- 클라이언트의 이벤트 핸들러에 전달되지 않지만, 연결은 유지된다
- 프록시/로드밸런서가 "이 연결은 살아있다"고 인식하도록 한다

## 4. Sinks → Flux → SSE 전체 흐름

Phase 4에서 구현한 Sinks 아키텍처 위에 SSE를 추가하는 구조:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         이벤트 발행 소스                                │
│                                                                         │
│  ReservationService ──→ eventPublisher.publish(RESERVATION_CREATED)    │
│  InventoryService   ──→ eventPublisher.publish(INVENTORY_UPDATED)     │
│  InventorySyncService──→ eventPublisher.publish(CHANNEL_SYNCED)       │
│                                                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                    EventPublisher (Sinks Hub)                           │
│  Sinks.many().multicast().onBackpressureBuffer<ChannelEvent>()        │
│                         │                                               │
│                    sinks.asFlux()                                       │
│                         │                                               │
│              ┌──────────┼──────────┐                                   │
│              ▼                     ▼                                    │
│   InventorySyncService    EventStreamController (Phase 5 NEW)          │
│   (Phase 4 — 내부 구독자)   (SSE — 외부 브라우저 구독자)               │
│                                    │                                    │
│                           Flux<ChannelEvent>                           │
│                                    │                                    │
│                           .map { → ServerSentEvent }                   │
│                                    │                                    │
│                           text/event-stream                            │
│                                    │                                    │
│                                    ▼                                    │
│                           브라우저 (EventSource)                       │
└─────────────────────────────────────────────────────────────────────────┘
```

## 5. 클라이언트 — EventSource API

### 5.1 EventSource란?

`EventSource`는 **브라우저 내장 API**로, SSE 서버에 연결하여 이벤트를 수신한다.
별도 라이브러리 없이 모든 모던 브라우저에서 사용 가능하다.

```javascript
// EventSource 생성 — SSE 엔드포인트에 연결
const eventSource = new EventSource('/api/events/stream');

// 기본 메시지 수신 (event 타입이 지정되지 않은 이벤트)
eventSource.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('수신:', data);
};

// 특정 이벤트 타입 수신 (event: RESERVATION_CREATED)
eventSource.addEventListener('RESERVATION_CREATED', function(event) {
    const data = JSON.parse(event.data);
    console.log('예약 생성:', data);
});

// 에러 처리 (네트워크 오류, 서버 연결 끊김)
eventSource.onerror = function(event) {
    console.log('SSE 에러, 자동 재연결 시도...');
    // 브라우저가 자동으로 재연결한다 (별도 코드 불필요)
};

// 연결 종료 (더 이상 이벤트를 받지 않을 때)
eventSource.close();
```

### 5.2 EventSource 속성

| 속성/이벤트 | 설명 |
|-------------|------|
| `readyState` | 0=CONNECTING, 1=OPEN, 2=CLOSED |
| `onopen` | 연결 성공 시 호출 |
| `onmessage` | event 타입이 없는 이벤트 수신 시 호출 |
| `onerror` | 에러/연결 끊김 시 호출 |
| `addEventListener(type, fn)` | 특정 event 타입 수신 시 호출 |
| `close()` | 연결 종료 |

### 5.3 event 타입에 따른 수신 분기

```
서버가 event: 필드를 설정하면 → addEventListener로 수신
서버가 event: 필드를 설정하지 않으면 → onmessage로 수신
```

우리 시스템에서는 `event:` 필드에 EventType(RESERVATION_CREATED 등)을 설정하므로,
클라이언트에서 `addEventListener`로 이벤트 타입별 처리를 구분할 수 있다.

## 6. Kotlin vs Java 비교

### 6.1 ServerSentEvent 빌더

```kotlin
// Kotlin — 타입 파라미터를 메서드에 명시
ServerSentEvent.builder<EventResponse>()
    .id(event.id.toString())
    .event(event.eventType.name)    // enum의 name 프로퍼티
    .data(EventResponse.from(event))
    .build()
```

```java
// Java — 타입 파라미터를 <> 앞에 명시 (메서드 앞 제네릭)
ServerSentEvent.<EventResponse>builder()
    .id(event.getId().toString())
    .event(event.getEventType().name())  // enum의 name() 메서드
    .data(EventResponse.from(event))
    .build();
```

**차이점:**
- Kotlin: `builder<T>()` — 타입 파라미터가 메서드 이름 뒤에 위치
- Java: `<T>builder()` — 타입 파라미터가 메서드 이름 앞에 위치 (타입 증인, type witness)
- Kotlin: `event.eventType.name` — 프로퍼티 접근
- Java: `event.getEventType().name()` — 메서드 호출

### 6.2 produces 설정

```kotlin
// Kotlin — 배열 리터럴
@GetMapping("/api/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
```

```java
// Java — 문자열 (단일 값이면 배열 불필요)
@GetMapping(value = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

### 6.3 DTO 변환

```kotlin
// Kotlin — companion object의 팩토리 메서드
data class EventResponse(...) {
    companion object {
        fun from(event: ChannelEvent): EventResponse = EventResponse(...)
    }
}
```

```java
// Java — record의 static 팩토리 메서드
public record EventResponse(...) {
    public static EventResponse from(ChannelEvent event) {
        return new EventResponse(...);
    }
}
```

## 7. 테스트

### 7.1 WebTestClient로 SSE 테스트

```kotlin
// SSE 스트림 수신 테스트
webTestClient.get()
    .uri("/api/events/stream")
    .accept(MediaType.TEXT_EVENT_STREAM)   // Accept: text/event-stream
    .exchange()
    .expectStatus().isOk()
    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
    .returnResult<ServerSentEvent<String>>()
    .responseBody                           // Flux<ServerSentEvent<String>>
    .take(1)                                // 첫 번째 이벤트만 수신
    .next()                                 // Mono<ServerSentEvent<String>>
```

**SSE 테스트 시 주의사항:**
- SSE는 무한 스트림이므로 `.take(N)`으로 수신 개수를 제한해야 한다
- 타임아웃을 설정하지 않으면 테스트가 무한 대기할 수 있다
- StepVerifier의 `thenCancel()`로 구독을 취소할 수 있다

## 8. Phase 5 구현 계획

### 구현할 컴포넌트

| 컴포넌트 | Kotlin | Java | 역할 |
|----------|--------|------|------|
| EventResponse DTO | data class | record | ChannelEvent → API 응답 변환 |
| EventStreamController | ✅ | ✅ | SSE 스트림 + 최근 이벤트 조회 |
| 통합 테스트 | ✅ | ✅ | SSE 연결 및 이벤트 수신 검증 |

### API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/events/stream` | SSE 실시간 이벤트 스트림 |
| GET | `/api/events` | 최근 이벤트 목록 조회 (DB) |

### 핵심 학습 포인트 정리

1. **SSE 프로토콜**: `text/event-stream`, `data:`, `event:`, `id:` 필드
2. **ServerSentEvent<T>**: Spring WebFlux의 SSE 전용 래퍼 타입
3. **Sinks → Flux → SSE**: Phase 4의 Sinks를 브라우저까지 연결하는 전체 흐름
4. **EventSource API**: 브라우저 내장 SSE 클라이언트
5. **Heartbeat**: 프록시/로드밸런서의 유휴 타임아웃 방지
6. **자동 재연결**: SSE의 핵심 장점 — 브라우저가 알아서 재연결한다
