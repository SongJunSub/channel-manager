# Phase 22 — 분산 추적 (Micrometer Tracing + Zipkin)

## 1. 분산 추적이란?

### 1.1 왜 분산 추적이 필요한가?

```
사용자 요청 → API Gateway → Kotlin앱 → PostgreSQL
                                     → Redis (캐시)
                          → Java앱  → PostgreSQL

"응답이 느려요" → 어디서 느린지? DB? Redis? 앱 로직?
```

모놀리식 앱에서는 하나의 로그를 보면 전체 흐름을 파악할 수 있다.
하지만 **여러 서비스**가 하나의 요청을 처리하면, 로그가 서비스별로 분산되어 전체 흐름을 추적하기 어렵다.

분산 추적은 **하나의 요청이 여러 서비스를 거치는 전체 경로를 시각화**하는 기술이다.

### 1.2 모니터링의 3대 축 — Traces의 위치

```
Metrics (Phase 19)   Logs (Phase 12)    Traces (Phase 22)
  "무엇이 느린가?"      "무슨 일이 있었나?"   "어떤 경로로 흘렀나?"
  CPU, 응답시간         에러 로그, 접근 로그  서비스 간 요청 흐름
  Prometheus + Grafana  Logback + MDC       Zipkin
```

## 2. 핵심 개념

### 2.1 Trace와 Span

```
Trace (하나의 요청 전체 추적)
 ┌──────────────────────────────────────────────────────┐
 │ Trace ID: abc123                                     │
 │                                                      │
 │ Span A: HTTP GET /api/statistics/summary  (100ms)    │
 │  ├── Span B: Redis GET cache:statistics:summary (2ms)│
 │  └── Span C: PostgreSQL SELECT reservations (80ms)   │
 │       └── Span D: PostgreSQL SELECT channels (15ms)  │
 │                                                      │
 └──────────────────────────────────────────────────────┘
```

- **Trace**: 하나의 사용자 요청에 대한 전체 추적 (고유 Trace ID)
- **Span**: Trace 내의 개별 작업 단위 (고유 Span ID)
- **Parent Span**: 상위 작업 (Span A는 Span B, C의 부모)
- **Root Span**: 최초 진입 Span (보통 HTTP 요청)

### 2.2 Trace ID 전파

```
Kotlin앱 (Trace: abc123, Span: 001)
  │
  │ HTTP Header: traceparent: 00-abc123-002-01
  ▼
Java앱 (Trace: abc123, Span: 002)  ← 같은 Trace ID를 공유
  │
  │ SQL Query
  ▼
PostgreSQL (Trace: abc123, Span: 003)
```

- Trace ID는 HTTP 헤더(`traceparent`)로 서비스 간 전파된다
- W3C Trace Context 표준: `traceparent: 00-{traceId}-{spanId}-{flags}`
- 모든 서비스가 같은 Trace ID를 사용하여 하나의 요청을 추적

### 2.3 샘플링

```
전체 요청 100건 중 10건만 추적 (sampling rate = 0.1)
→ 저장 비용 절감, 성능 영향 최소화
→ 프로덕션에서는 1~10% 샘플링이 일반적
→ 개발/학습에서는 100% 샘플링 (모든 요청 추적)
```

## 3. Micrometer Tracing

### 3.1 Micrometer Tracing이란?

```
애플리케이션 코드
      │
      ▼
┌──────────────────────┐
│  Micrometer Tracing  │  ← 벤더 중립적 추적 추상화
│  (SLF4J of Tracing)  │     (Micrometer가 Metrics의 추상화였듯,
│                      │      Micrometer Tracing은 추적의 추상화)
└──────────┬───────────┘
           │
      ┌────┴────┐
      ▼         ▼
   Brave      OpenTelemetry  ← 구현체 (트레이서)
      │         │
      ▼         ▼
   Zipkin    Jaeger/OTLP    ← 백엔드 (수집/시각화)
```

- Spring Boot 3.x+ 에서 공식 지원
- Brave (Zipkin의 트레이서)를 브릿지로 사용
- 코드 변경 없이 자동으로 HTTP 요청, R2DBC 쿼리, Redis 명령을 추적

### 3.2 자동 계측 (Auto-Instrumentation)

Spring Boot Actuator + Micrometer Tracing이 **자동으로** 추적하는 항목:

| 항목 | 설명 |
|------|------|
| HTTP Server | 수신 HTTP 요청 (WebFlux) |
| HTTP Client | WebClient 외부 호출 |
| R2DBC | 데이터베이스 쿼리 |
| Redis | Lettuce Redis 명령 |
| Reactor | Reactive 연산자 체인 |

### 3.3 로그에 Trace ID 자동 포함

```
기존 (Phase 12): [requestId: a1b2c3d4] 요청 시작: GET /api/statistics/summary
Phase 22 추가:   [traceId: abc123def456, spanId: 001] 요청 시작: GET /api/statistics/summary
```

- Micrometer Tracing이 MDC에 `traceId`, `spanId`를 자동 설정
- logback-spring.xml에서 `%X{traceId}` 패턴으로 출력 가능
- Zipkin UI에서 Trace ID로 검색 → 해당 요청의 전체 로그 추적

## 4. Zipkin

### 4.1 Zipkin이란?

```
앱 (Span 데이터) ──HTTP POST──▶ Zipkin Collector ──▶ Storage ──▶ Web UI
                 /api/v2/spans                     (인메모리)    :9411
```

- Twitter에서 개발한 **분산 추적 수집/시각화** 시스템
- Span 데이터를 수집하여 Trace 단위로 시각화
- 서비스 간 의존 관계 그래프 자동 생성
- 기본 포트: 9411

### 4.2 Zipkin UI 기능

```
┌─────────────────────────────────────────────────────┐
│                    Zipkin UI                         │
├─────────────────────────────────────────────────────┤
│ Service: kotlin-app          Duration: 120ms         │
│                                                     │
│ ├── GET /api/statistics/summary        [100ms] ████ │
│ │   ├── Redis GET cache:statistics      [2ms] █     │
│ │   ├── PostgreSQL SELECT reservations [60ms] ████  │
│ │   └── PostgreSQL SELECT channels     [20ms] ██    │
│                                                     │
│ Dependencies: kotlin-app → postgres, redis           │
└─────────────────────────────────────────────────────┘
```

## 5. 이 프로젝트의 분산 추적 구성

### 5.1 아키텍처

```
┌──────────┐                    ┌──────────┐
│kotlin-app│──Span 데이터 전송──▶│  Zipkin  │
│  :8080   │  (HTTP)            │  :9411   │
└──────────┘                    │          │
                                │  Web UI  │
┌──────────┐                    │          │
│ java-app │──Span 데이터 전송──▶│          │
│  :8081   │  (HTTP)            └──────────┘
└──────────┘
```

### 5.2 의존성

```
micrometer-tracing-bridge-brave     — Micrometer Tracing ↔ Brave 브릿지
zipkin-reporter-brave               — Brave → Zipkin HTTP 리포터
```

### 5.3 설정

```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 100% 샘플링 (개발환경, 프로덕션에서는 0.1 권장)
    propagation:
      type: w3c            # W3C Trace Context 전파 (traceparent 헤더)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans  # Zipkin 수집 엔드포인트
```
