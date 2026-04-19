# Phase 27 — Resilience4j 서킷 브레이커

## 1. 서킷 브레이커란?

### 1.1 왜 서킷 브레이커가 필요한가?

```
서킷 브레이커 없이:
  서비스 A → 서비스 B (장애) → 타임아웃 30초 대기 → 실패
  서비스 A → 서비스 B (장애) → 타임아웃 30초 대기 → 실패
  (모든 요청이 30초씩 대기 → 스레드 풀 고갈 → 서비스 A도 장애)

서킷 브레이커:
  서비스 A → [서킷 OPEN] → 즉시 폴백 응답 (0.01초)
  (서비스 B를 호출하지 않음 → 자원 낭비 방지 → 서비스 A 보호)
```

- **장애 전파 차단**: 하나의 서비스 장애가 전체 시스템으로 퍼지는 것을 방지
- **빠른 실패 (Fail Fast)**: 장애 중인 서비스 호출을 즉시 차단하여 응답 지연 방지
- **자동 복구**: 주기적으로 서비스 상태를 확인하여 정상화되면 호출 재개

### 1.2 서킷 브레이커 상태 전이

```
┌─────────┐  실패율 > 임계값  ┌─────────┐  대기 시간 경과  ┌───────────┐
│ CLOSED  │ ───────────────▶ │  OPEN   │ ──────────────▶ │ HALF_OPEN │
│ (정상)  │                  │ (차단)   │                  │ (시험 호출)│
└─────────┘                  └─────────┘                  └───────────┘
    ▲                                                        │ │
    │          시험 호출 성공                                    │ │ 시험 호출 실패
    └──────────────────────────────────────────────────────────┘ │
                                                    ┌───────────┘
                                                    ▼
                                               ┌─────────┐
                                               │  OPEN   │
                                               └─────────┘
```

- **CLOSED (정상)**: 모든 요청을 통과시킴 → 실패율 모니터링
- **OPEN (차단)**: 모든 요청을 즉시 거부 → 폴백 응답 반환 → 대기 시간 경과 후 HALF_OPEN
- **HALF_OPEN (시험)**: 제한된 수의 요청만 통과 → 성공하면 CLOSED, 실패하면 OPEN

## 2. Resilience4j

### 2.1 Resilience4j란?

```
Netflix Hystrix (EOL)  →  Resilience4j (후속)
  - 모놀리식 라이브러리      - 모듈형 (필요한 것만 선택)
  - Rx Java 기반            - Java 8+ / Reactor / RxJava 지원
  - 유지보수 중단            - 활발한 개발
```

| 모듈 | 설명 |
|------|------|
| **CircuitBreaker** | 서킷 브레이커 (장애 차단) |
| **Retry** | 자동 재시도 |
| **RateLimiter** | 호출 빈도 제한 |
| **TimeLimiter** | 타임아웃 |
| **Bulkhead** | 격벽 (동시 호출 수 제한) |

### 2.2 Reactor 통합

```kotlin
// Resilience4j + Reactor: 연산자로 서킷 브레이커 적용
webClient.post()
    .uri("/api/reservations")
    .retrieve()
    .bodyToMono(ReservationResponse::class.java)
    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))  // 서킷 브레이커 적용
    .transformDeferred(RetryOperator.of(retry))                    // 재시도 적용
    .onErrorResume { fallback() }                                  // 폴백
```

## 3. 설정 항목

### 3.1 서킷 브레이커 설정

```yaml
resilience4j:
  circuitbreaker:
    instances:
      reservationApi:
        sliding-window-size: 10         # 최근 10번 호출 기준 실패율 계산
        failure-rate-threshold: 50      # 실패율 50% 이상이면 OPEN
        wait-duration-in-open-state: 10s # OPEN 상태 10초 유지 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3  # HALF_OPEN에서 3번 시험
        minimum-number-of-calls: 5      # 최소 5번 호출 후에 실패율 계산 시작
```

### 3.2 재시도 설정

```yaml
resilience4j:
  retry:
    instances:
      reservationApi:
        max-attempts: 3                 # 최대 3회 시도
        wait-duration: 1s               # 재시도 간 대기 시간
```

## 4. 이 프로젝트의 적용

### 4.1 적용 대상

```
ChannelSimulator
  └── webClient.post("/api/reservations")  ← 서킷 브레이커 + 재시도 적용
        │
        ├── 성공 → 로그 기록
        ├── 재시도 → 1초 후 재시도 (최대 3회)
        ├── 서킷 OPEN → 즉시 폴백 ("서킷 열림, 요청 차단")
        └── 최종 실패 → 폴백 ("예약 실패")
```

### 4.2 구현 파일

```
config/
  └── ResilienceConfig.kt (.java)    ← CircuitBreaker + Retry 빈 설정
simulator/
  └── ChannelSimulator.kt (.java)    ← WebClient 호출에 서킷 브레이커 적용
```
