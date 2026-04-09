# Phase 17 — Rate Limiting (API 호출 제한)

## 1. Rate Limiting이란?

### 1.1 왜 필요한가?

```
정상 사용자: 1초에 2~3번 API 호출
악의적 사용자: 1초에 1,000번 API 호출 (DDoS, 크롤링, 무차별 대입)
                 ↓
서버 과부하 → 정상 사용자도 서비스 이용 불가
```

Rate Limiting은 **일정 시간 동안 허용되는 API 호출 횟수를 제한**하는 기법이다.
제한을 초과하면 HTTP **429 Too Many Requests** 응답을 반환한다.

### 1.2 적용 사례

| 상황 | Rate Limiting |
|------|--------------|
| OTA 채널의 예약 요청 | 채널당 초당 10건 |
| 대시보드 조회 | IP당 초당 50건 |
| 시뮬레이터 제어 | IP당 분당 10건 |
| 로그인 시도 | IP당 5분에 5회 (Brute Force 방지) |

## 2. Token Bucket 알고리즘

### 2.1 개념

```
┌─────────────────────────────┐
│       Token Bucket          │
│                             │
│  버킷 용량: 10개             │
│  현재 토큰: ●●●●●●● (7개)   │
│                             │
│  토큰 충전: 1개/초           │
│                             │
└─────────────────────────────┘
         │
         ▼
요청 도착 → 토큰 1개 소비 → 요청 허용 ✅
토큰 없음 → 요청 거부 (429) ❌
```

- **버킷**: 토큰을 담는 그릇 (최대 용량이 정해져 있음)
- **토큰**: API 호출 권한 (1 요청 = 1 토큰)
- **충전**: 일정 주기로 토큰이 자동 추가됨 (초과 시 버림)
- **소비**: 요청 시 토큰 1개를 소비, 토큰이 없으면 거부

### 2.2 동작 예시

```
시간  토큰  요청      결과
─────────────────────────────
0초   10   -         (초기 상태)
1초   10   요청 3건   7개 남음 ✅
2초   8    요청 5건   3개 남음 ✅ (1개 충전됨)
3초   4    요청 5건   429 반환 ❌ (토큰 부족, 4건만 처리)
4초   1    -         (1개 충전됨)
5초   2    요청 1건   1개 남음 ✅
```

### 2.3 다른 알고리즘과 비교

| 알고리즘 | 특징 | 장점 | 단점 |
|---------|------|------|------|
| **Token Bucket** | 토큰 충전 방식 | 버스트 허용, 구현 간단 | 메모리 사용 |
| Fixed Window | 고정 시간 윈도우 카운터 | 구현 가장 간단 | 윈도우 경계에서 2배 트래픽 |
| Sliding Window | 슬라이딩 윈도우 | 윈도우 경계 문제 없음 | 구현 복잡 |
| Leaky Bucket | 고정 속도 출력 | 트래픽 평탄화 | 버스트 불가 |

Token Bucket은 **버스트(순간 폭주)를 허용하면서도 평균 속도를 제한**하기 때문에 API Rate Limiting에 가장 많이 사용된다.

## 3. Bucket4j 라이브러리

### 3.1 Bucket4j란?

- Java 기반 **Token Bucket 알고리즘** 구현 라이브러리
- JCache, Redis, Hazelcast 등 분산 환경 지원
- 이 프로젝트에서는 **로컬 인메모리 방식** 사용 (단일 인스턴스)

### 3.2 핵심 개념

```java
// Bucket 생성 — 10개 용량, 1초에 10개씩 충전
Bucket bucket = Bucket.builder()
    .addLimit(
        BandwidthBuilder.builder()
            .capacity(10)                           // 버킷 최대 용량
            .refillGreedy(10, Duration.ofSeconds(1)) // 1초마다 10개 충전
            .build()
    )
    .build();

// 토큰 소비 시도
if (bucket.tryConsume(1)) {
    // 요청 허용
} else {
    // 429 Too Many Requests
}
```

### 3.3 Bandwidth 설정

```
Bandwidth = 버킷의 충전 규칙

capacity(10) + refillGreedy(10, 1초)
→ 최대 10개 토큰, 1초마다 10개씩 한꺼번에 충전

capacity(100) + refillGreedy(100, 1분)
→ 최대 100개 토큰, 1분마다 100개씩 충전
```

- **refillGreedy**: 지정된 시간이 지나면 **한 번에** 토큰을 충전
- **refillIntervally**: 균등한 간격으로 **하나씩** 충전

## 4. WebFlux에서의 Rate Limiting

### 4.1 WebFilter로 구현

```
요청 → RequestLoggingFilter → RateLimitFilter → Controller
                                     │
                                     ├── 토큰 있음 → 요청 전달 ✅
                                     └── 토큰 없음 → 429 응답 ❌
```

- `WebFilter`로 모든 요청을 가로채서 Rate Limiting 적용
- `@Order`로 RequestLoggingFilter 다음에 실행되도록 설정

### 4.2 IP 기반 제한

```
클라이언트 A (192.168.1.1) → 자신만의 버킷 (10/초)
클라이언트 B (192.168.1.2) → 자신만의 버킷 (10/초)
클라이언트 C (10.0.0.1)    → 자신만의 버킷 (10/초)
```

- 클라이언트 IP별로 별도의 버킷을 관리
- `ConcurrentHashMap`으로 IP → Bucket 매핑

### 4.3 429 응답 형식

```json
HTTP/1.1 429 Too Many Requests
Retry-After: 1
Content-Type: application/json

{
  "message": "요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요."
}
```

- `Retry-After` 헤더: 클라이언트가 다시 시도할 수 있는 시간(초)
- 기존 `ErrorResponse` 형식과 동일한 JSON 구조

## 5. 이 프로젝트의 Rate Limiting 구성

### 5.1 제한 정책

```
모든 API 엔드포인트:
  - IP당 초당 50건 (버스트 허용)
  - 버킷 용량: 50개
  - 충전 속도: 50개/초

제외 경로:
  - /webjars/** (Swagger UI 정적 리소스)
  - /v3/api-docs (OpenAPI 스펙)
  - /swagger-ui.html (Swagger 리다이렉트)
  - / 및 /index.html (대시보드)
  - /api/events/stream (SSE 스트림 — 장기 연결)
```

### 5.2 구현 구조

```
config/
  └── RateLimitFilter.kt (.java)   ← WebFilter 구현
      ├── ConcurrentHashMap<IP, Bucket>  ← IP별 버킷 관리
      ├── 제외 경로 체크
      ├── 토큰 소비 시도
      └── 429 응답 생성 (Retry-After 헤더 포함)
```
