# Phase 19 — 모니터링 (Actuator + Prometheus + Grafana)

## 1. 모니터링이란?

### 1.1 왜 모니터링이 필요한가?

```
운영 중인 시스템:
  "서버가 느려요" → 어디가 느린지? DB? 네트워크? 코드?
  "에러가 나요"   → 언제부터? 얼마나 자주? 어떤 패턴?
  "서버가 죽었어" → 왜 죽었는지? 메모리? CPU? 디스크?
```

모니터링은 **시스템의 상태를 실시간으로 관찰하고, 이상 징후를 조기에 발견**하는 활동이다.

### 1.2 모니터링의 3대 축

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   Metrics    │  │    Logs     │  │   Traces    │
│  (수치 데이터) │  │  (이벤트 기록) │  │ (요청 추적)  │
│  CPU, 메모리  │  │ 에러 로그    │  │ 분산 추적    │
│  요청 수, 지연 │  │ 접근 로그    │  │ 서비스 간 흐름│
└─────────────┘  └─────────────┘  └─────────────┘
   Phase 19          Phase 12          (향후)
```

- **Metrics**: 숫자로 표현되는 시스템 상태 (CPU 80%, 요청 100건/초, 응답 200ms)
- **Logs**: Phase 12에서 구현한 구조화 로깅 (MDC + JSON)
- **Traces**: 분산 시스템에서 요청의 전체 경로 추적 (향후 확장)

## 2. Spring Boot Actuator

### 2.1 Actuator란?

Spring Boot 애플리케이션의 **운영 정보를 HTTP 엔드포인트**로 노출하는 모듈이다.

```
GET /actuator/health    → {"status":"UP"}
GET /actuator/info      → 애플리케이션 정보
GET /actuator/metrics   → 사용 가능한 메트릭 목록
GET /actuator/prometheus → Prometheus 형식의 메트릭 데이터
```

### 2.2 주요 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `/actuator/health` | 애플리케이션 상태 (UP/DOWN) |
| `/actuator/health/readiness` | 준비 상태 (트래픽 수용 가능 여부) |
| `/actuator/health/liveness` | 생존 상태 (프로세스 정상 여부) |
| `/actuator/info` | 빌드 정보, Git 정보 |
| `/actuator/metrics` | Micrometer 메트릭 목록 |
| `/actuator/metrics/{name}` | 특정 메트릭 상세 |
| `/actuator/prometheus` | Prometheus 스크래핑 형식 메트릭 |

### 2.3 Health Indicator

```json
GET /actuator/health

{
  "status": "UP",
  "components": {
    "r2dbc": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

- Spring Boot가 **자동으로** DB, Redis, 디스크 등의 상태를 체크
- 하나라도 DOWN이면 전체 상태가 DOWN으로 변경

## 3. Micrometer

### 3.1 Micrometer란?

```
애플리케이션 코드
      │
      ▼
┌─────────────────┐
│   Micrometer    │  ← 벤더 중립적 메트릭 수집 라이브러리
│  (SLF4J of      │     (SLF4J가 로깅의 추상화인 것처럼,
│   Metrics)      │      Micrometer는 메트릭의 추상화)
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
Prometheus  Datadog   ← 백엔드별 레지스트리
```

- 애플리케이션에서는 Micrometer API로 메트릭을 기록
- 백엔드(Prometheus, Datadog 등)에 맞는 형식으로 자동 변환

### 3.2 메트릭 타입

| 타입 | 설명 | 예시 |
|------|------|------|
| **Counter** | 단조 증가하는 카운터 | 예약 생성 수, 에러 수 |
| **Gauge** | 현재 값 (증감 가능) | 활성 연결 수, 큐 크기 |
| **Timer** | 실행 시간 + 호출 수 | API 응답 시간 |
| **Distribution Summary** | 값의 분포 | 응답 크기 분포 |

### 3.3 자동 수집되는 메트릭

Spring Boot Actuator + Micrometer가 자동으로 수집하는 메트릭:

```
# JVM
jvm_memory_used_bytes          — JVM 메모리 사용량
jvm_threads_live_threads       — 활성 스레드 수
jvm_gc_pause_seconds           — GC 일시 정지 시간

# HTTP
http_server_requests_seconds   — HTTP 요청 처리 시간
http_server_requests_active    — 현재 처리 중인 요청 수

# R2DBC 커넥션 풀
r2dbc_pool_acquired            — 획득된 커넥션 수
r2dbc_pool_idle                — 유휴 커넥션 수

# Redis
lettuce_command_completion_seconds — Redis 명령 실행 시간

# 시스템
system_cpu_usage               — CPU 사용률
process_uptime_seconds         — 프로세스 업타임
```

## 4. Prometheus

### 4.1 Prometheus란?

```
Prometheus (시계열 DB)
  │
  ├── 주기적으로 메트릭 수집 (Pull 방식)
  │     GET /actuator/prometheus → 스크래핑
  │
  ├── 시계열 데이터 저장
  │     (타임스탬프 + 메트릭 이름 + 라벨 + 값)
  │
  └── PromQL로 쿼리
        rate(http_server_requests_seconds_count[5m])
```

- **Pull 방식**: Prometheus가 타겟 애플리케이션에서 메트릭을 주기적으로 가져옴
- **시계열 DB**: 시간축 기반 데이터 저장에 최적화
- **PromQL**: 메트릭 쿼리 언어 (rate, sum, avg 등)

### 4.2 Prometheus 메트릭 형식

```
# HELP http_server_requests_seconds Duration of HTTP server request handling
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="GET",uri="/api/statistics/summary",status="200"} 42
http_server_requests_seconds_sum{method="GET",uri="/api/statistics/summary",status="200"} 3.14
```

- `# HELP`: 메트릭 설명
- `# TYPE`: 메트릭 타입 (counter, gauge, summary, histogram)
- `라벨`: `{method="GET", uri="...", status="200"}` — 다차원 필터링

### 4.3 prometheus.yml (설정 파일)

```yaml
global:
  scrape_interval: 15s    # 15초마다 메트릭 수집

scrape_configs:
  - job_name: 'kotlin-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['kotlin-app:8080']

  - job_name: 'java-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['java-app:8081']
```

## 5. Grafana

### 5.1 Grafana란?

```
Prometheus (데이터) ──▶ Grafana (시각화) ──▶ 대시보드
```

- Prometheus에 저장된 메트릭을 **시각화**하는 웹 대시보드
- 그래프, 차트, 알림 등 풍부한 시각화 기능
- 기본 포트: 3000

### 5.2 주요 시각화

```
┌─────────────────────────────────────────────┐
│              Grafana Dashboard              │
├─────────────┬─────────────┬─────────────────┤
│ HTTP 요청/초 │ 응답 시간    │ 에러율          │
│ ████████    │  ▂▃▅▇▆▄▃   │ 0.01%           │
├─────────────┼─────────────┼─────────────────┤
│ JVM 메모리   │ GC 빈도     │ 활성 스레드      │
│ 256/512MB   │ 2회/분      │ 24개            │
├─────────────┼─────────────┼─────────────────┤
│ 예약 생성/분  │ 취소율      │ 채널별 예약      │
│ 45건        │ 5%          │ BOOKING: 60%    │
└─────────────┴─────────────┴─────────────────┘
```

## 6. 커스텀 비즈니스 메트릭

### 6.1 왜 커스텀 메트릭이 필요한가?

자동 수집 메트릭은 **인프라 수준** (JVM, HTTP, DB)만 제공한다.
**비즈니스 수준** 메트릭은 직접 구현해야 한다:

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `reservations_created_total` | Counter | 총 예약 생성 수 |
| `reservations_cancelled_total` | Counter | 총 예약 취소 수 |
| `reservations_by_channel_total` | Counter (태그) | 채널별 예약 수 |

### 6.2 구현 방법

```kotlin
// MeterRegistry: Micrometer의 메트릭 등록소
// counter(): Counter 메트릭 생성 (단조 증가)
// tag(): 다차원 라벨 추가 — Prometheus에서 {channel="BOOKING"}으로 필터링

meterRegistry.counter(
    "reservations.created",        // 메트릭 이름
    "channel", channel.channelCode // 태그 (라벨)
).increment()                      // 1 증가
```

## 7. 이 프로젝트의 모니터링 구성

### 7.1 전체 아키텍처

```
┌──────────┐    /actuator/prometheus     ┌────────────┐
│kotlin-app├──────────────────────────────▶│            │
│  :8080   │  15초마다 Pull               │ Prometheus │
└──────────┘                              │   :9090    │
                                          │            │
┌──────────┐    /actuator/prometheus     │            │    ┌─────────┐
│ java-app ├──────────────────────────────▶│            ├───▶│ Grafana │
│  :8081   │  15초마다 Pull               └────────────┘    │  :3000  │
└──────────┘                                                └─────────┘
```

### 7.2 구현 파일

```
config/
  └── MetricsConfig.kt (.java)     ← 커스텀 메트릭 태그 설정
service/
  └── ReservationService.kt (.java) ← 예약 생성/취소 시 Counter 증가
monitoring/
  ├── prometheus.yml               ← Prometheus 스크래핑 설정
  └── grafana/                     ← Grafana 프로비저닝 (선택)
docker-compose.yml                 ← Prometheus + Grafana 서비스 추가
```
