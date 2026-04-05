# Phase 12 — 구조화 로깅 (JSON + MDC)

## 1. 구조화 로깅이란?

### 1.1 일반 로그 vs 구조화 로그

```
일반 로그 (plain text):
2026-03-29 14:23:01 INFO  ReservationService - 예약 생성 완료: id=42, channel=BOOKING

구조화 로그 (JSON):
{"timestamp":"2026-03-29T14:23:01","level":"INFO","logger":"ReservationService",
 "message":"예약 생성 완료","requestId":"abc-123","reservationId":42,"channel":"BOOKING"}
```

**구조화 로그의 장점:**
- **파싱 가능**: JSON이므로 로그 분석 도구(ELK, Grafana Loki)에서 검색/필터 가능
- **필드 기반 검색**: `reservationId=42` 같은 정확한 필터링
- **컨텍스트 추적**: requestId로 하나의 요청에 속한 모든 로그를 추적

### 1.2 MDC (Mapped Diagnostic Context)

MDC는 **스레드/요청별로 컨텍스트 데이터를 저장**하는 SLF4J 기능이다.

```
요청 A (requestId=abc-123)
  → Service 로그: requestId=abc-123
  → Repository 로그: requestId=abc-123

요청 B (requestId=def-456)
  → Service 로그: requestId=def-456
```

**WebFlux에서의 MDC:**
- 전통적인 MVC에서는 ThreadLocal 기반 MDC를 사용
- WebFlux(Reactor)에서는 스레드가 공유되므로 **Context**를 활용해야 함
- 간단한 접근: WebFilter에서 요청 시작 시 MDC에 값을 설정하고, 응답 후 제거

## 2. Logback 설정 (logback-spring.xml)

### 2.1 프로파일 기반 분리

```xml
<!-- 개발 환경: 사람이 읽기 쉬운 콘솔 로그 -->
<springProfile name="default">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss} %highlight(%-5level) %cyan(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>
</springProfile>

<!-- 운영 환경: JSON 구조화 로그 -->
<springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
</springProfile>
```

### 2.2 LogstashEncoder

`logstash-logback-encoder` 라이브러리가 제공하는 JSON 인코더:
- 모든 로그를 JSON 형식으로 출력
- MDC 필드를 자동으로 JSON에 포함
- 타임스탬프, 레벨, 로거, 메시지 등을 구조화

## 3. WebFilter — 요청별 MDC 설정

### 3.1 RequestLoggingFilter

```kotlin
@Component
class RequestLoggingFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = UUID.randomUUID().toString().substring(0, 8)
        MDC.put("requestId", requestId)
        MDC.put("method", exchange.request.method.name())
        MDC.put("path", exchange.request.path.value())

        return chain.filter(exchange)
            .doFinally { MDC.clear() }
    }
}
```

### 3.2 MDC 필드

| 필드 | 값 | 설명 |
|------|-----|------|
| requestId | UUID 8자리 | 요청 추적 ID |
| method | GET/POST/... | HTTP 메서드 |
| path | /api/reservations | 요청 경로 |

## 4. 핵심 학습 포인트

1. **logback-spring.xml**: Spring 프로파일 기반 로깅 설정
2. **LogstashEncoder**: JSON 구조화 로그 출력
3. **MDC**: 요청별 컨텍스트 데이터 추적
4. **WebFilter**: WebFlux에서 HTTP 요청/응답 가로채기
5. **프로파일 분리**: 개발(콘솔) vs 운영(JSON) 환경별 로그 형식
