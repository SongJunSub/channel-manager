# Channel Manager - 프로젝트 지침

## 프로젝트 개요
호텔 멀티 채널 예약 동기화 시스템 (Channel Manager)
- Spring WebFlux 학습 목적 프로젝트
- 호스피탈리티 도메인

## 기술 스택
- 언어: **Kotlin + Java** (동일 로직을 두 언어로 구현)
- 프레임워크: Spring Boot 4.x + WebFlux
- DB: PostgreSQL + Spring Data R2DBC
- 마이그레이션: Flyway
- 빌드: Gradle (Kotlin DSL)
- 프론트: HTML + Vanilla JS (EventSource)
- 테스트: JUnit 5 + StepVerifier

## 핵심 규칙

### 1. Kotlin & Java 동시 구현
- 모든 로직은 Kotlin 모듈과 Java 모듈에 각각 구현한다.
- 두 언어의 코드를 직접 비교할 수 있어야 한다.
- 구조: Gradle 멀티 모듈 (channel-manager-kotlin, channel-manager-java)

### 2. 줄별 상세 주석
- 모든 코드에 각 줄마다 상세한 주석을 작성한다.
- 해당 줄이 무엇을 하는지, 왜 필요한지를 설명한다.

### 3. Phase별 개념 MD 파일 작성
- 각 Phase 시작 전에 해당 단계에서 사용하는 개념을 docs/ 디렉토리에 MD 파일로 정리한다.
- WebFlux, R2DBC 등 개념을 모른다는 전제로 상세하게 작성한다.

### 4. Phase별 진행 흐름
```
① 개념 MD 작성 (이론 학습)
② Kotlin 코드 구현 (줄별 주석 포함)
③ Java 코드 구현 (줄별 주석 포함)
④ 동작 확인
```

## 프로젝트 구조
```
channel-manager/
├── build.gradle.kts              # 루트 공통 설정
├── settings.gradle.kts
├── docs/                         # Phase별 개념 정리 MD
│   ├── phase1-webflux-basics.md
│   ├── phase2-r2dbc.md
│   └── ...
├── channel-manager-kotlin/       # Kotlin 구현
│   ├── build.gradle.kts
│   └── src/main/kotlin/...
├── channel-manager-java/         # Java 구현
│   ├── build.gradle.kts
│   └── src/main/java/...
└── channel-manager-common/       # 공유 리소스 (schema.sql, static/ 등)
    └── src/main/resources/
```

## 도메인 모델
- Property: 숙소 정보
- RoomType: 객실 타입 (Standard, Deluxe, Suite)
- Inventory: 날짜별/객실타입별 재고
- Channel: 판매 채널 (DIRECT, Booking.com, Agoda, Trip.com)
- Reservation: 예약 정보
- ChannelEvent: 모든 변경 이력 (이벤트)

## Phase 구성
| Phase | 내용 | 핵심 패턴 |
|---|---|---|
| 1 | 프로젝트 초기화 & 도메인 설계 | R2DBC 설정 |
| 2 | 인벤토리 관리 API | Mono/Flux 기본 |
| 3 | 채널 시뮬레이터 | Flux.interval, WebClient |
| 4 | 스트림 통합 & 재고 동기화 | Flux.merge, Sinks, 동시성 |
| 5 | SSE 이벤트 발행 | SSE, Sinks → Flux |
| 6 | 실시간 대시보드 | EventSource, 프론트 |
| 7 | 예약 취소 & 재고 복구 | 보상 트랜잭션 |
| 8 | 통계 & 리포트 | window, buffer, groupBy |

## 진행 상황

### Phase 1 - 프로젝트 초기화 & 도메인 설계 ✅
- 개념 MD 작성 (phase1-webflux-basics.md, comparison 2개)
- Gradle 멀티 모듈 프로젝트 초기화
- 도메인 엔티티 구현 (Kotlin 6개 + Java 6개, @Schema 문서화)
- Flyway 마이그레이션 SQL (V1~V7, V7은 샘플 데이터)
- R2DBC Repository 정의 (Kotlin 6개 + Java 6개)
- Repository 통합 테스트 (Kotlin 6개 + Java 6개, StepVerifier)
- Docker PostgreSQL 환경 구성 (postgres:17-alpine)

### Phase 2 - 인벤토리 관리 API ✅
- 개념 MD 작성 (phase2-mono-flux.md)
- 인벤토리 CRUD + 일괄생성 API 구현 (Kotlin + Java, 6개 엔드포인트)
- DTO (record/data class), Service, Controller, GlobalExceptionHandler
- R2dbcConfig (@EnableR2dbcAuditing) 추가
- WebTestClient 통합 테스트 (Kotlin 11개 + Java 11개)
- 동작 확인 완료 (Kotlin:8080, Java:8081 동일 응답)

### Phase 3 - 채널 시뮬레이터 ✅
- 개념 MD 작성 (phase3-channel-simulator.md)
- 예약 API 구현 (Kotlin + Java, ReservationService/Controller)
- 채널 시뮬레이터 구현 (Kotlin + Java, ChannelSimulator, Flux.interval + WebClient)
- 시뮬레이터 제어 API (SimulatorController: 시작/중지/상태)
- WebClient 설정 (WebClientConfig)
- 통합 테스트 (Kotlin 11개 + Java 11개)
- 동작 확인 완료 (Kotlin:8080, Java:8081 시뮬레이터 정상 동작)

### Phase 4 - 스트림 통합 & 재고 동기화 ✅
- 개념 MD 작성 (phase4-stream-integration.md)
- EventPublisher: Sinks.many().multicast().onBackpressureBuffer() 이벤트 허브
- InventorySyncService: Flux.merge 이벤트 스트림 구독, 채널 동기화
- 비관적 잠금: FOR UPDATE 쿼리로 동시성 제어
- 통합 테스트 (Kotlin + Java, EventPublisher/InventorySyncService)

### Phase 5 - SSE 이벤트 발행 ✅
- 개념 MD 작성 (phase5-sse.md)
- EventResponse DTO (Kotlin data class + Java record)
- EventStreamController: GET /api/events/stream (SSE), GET /api/events (최근 이벤트)
- SSE 구현: ServerSentEvent<EventResponse>, heartbeat(30초), Flux.merge
- 통합 테스트 (Kotlin 6개 + Java 6개, StepVerifier + WebTestClient)

### Phase 6 - 실시간 대시보드 ✅
- 개념 MD 작성 (phase6-realtime-dashboard.md)
- 대시보드 HTML/CSS/JS (channel-manager-common/src/main/resources/static/)
- EventSource API로 SSE 실시간 이벤트 수신
- 이벤트 타입별 색상 뱃지, 통계 카운터, 시뮬레이터 제어
- 최근 이벤트 REST API 로드 (GET /api/events)
- Kotlin(8080) / Java(8081) 양쪽에서 동일하게 서빙

### Phase 7 - 예약 취소 & 재고 복구 ✅
- 개념 MD 작성 (phase7-compensation.md)
- ReservationService.cancelReservation() — 보상 트랜잭션 (Kotlin + Java)
- increaseInventory() — 재고 복구 (FOR UPDATE 비관적 잠금)
- ReservationController: DELETE /api/reservations/{id}
- 상태 전이: CONFIRMED → CANCELLED (이미 취소된 예약 재취소 400)
- RESERVATION_CANCELLED 이벤트 발행 → SSE → 대시보드 실시간 반영
- 통합 테스트 추가 (Kotlin 15개 + Java 15개, 취소 4개 추가)

### Phase 8 - 통계 & 리포트 ✅
- 개념 MD 작성 (phase8-statistics.md)
- StatisticsService: groupBy+count, flatMap+collectList+fold, Mono.zip 병렬 집계
- StatisticsController: 4개 통계 엔드포인트 (channels, events, rooms, summary)
- DTOs: ChannelStatistics, EventStatistics, RoomTypeStatistics, SummaryStatistics
- 통합 테스트 (Kotlin 6개 + Java 6개)

### Phase 9 - 예약 조회 API ✅
- 개념 MD 작성 (phase9-reservation-query.md)
- GET /api/reservations/{id} — 단건 조회 (채널 코드 풍부화)
- GET /api/reservations — 목록 조회 (channelId, status, startDate, endDate 필터 + page/size 페이징)
- N+1 방지: collectMap으로 채널 정보 미리 로드
- Flux.filter() + skip() + take() 애플리케이션 레벨 필터링/페이징
- 통합 테스트 추가 (Kotlin 20개 + Java 20개, 조회 5개 추가)

### Phase 10 - 채널별 가격 차등 ✅
- 개념 MD 작성 (phase10-channel-pricing.md)
- V8 마이그레이션: channels 테이블에 markup_rate 컬럼 추가
- Channel 엔티티에 markupRate 필드 추가 (BigDecimal, 기본값 1.0)
- ReservationService: totalPrice 계산에 channel.markupRate 곱셈 적용
- 채널별 마크업: DIRECT(1.0), BOOKING(1.15), AGODA(1.10), TRIP(0.95)

### Phase 11 - API 문서화 (SpringDoc Swagger) ✅
- 개념 MD 작성 (phase11-api-documentation.md)
- springdoc-openapi-starter-webflux-ui 의존성 추가
- OpenApiConfig 빈: API 제목, 설명, 버전 설정
- application.yml: springdoc 설정 (경로, 정렬)
- 19개 엔드포인트 자동 문서화, 기존 @Schema 자동 반영
- Swagger UI: /webjars/swagger-ui/index.html
- OpenAPI JSON: /v3/api-docs

### Phase 12 - 구조화 로깅 (JSON + MDC) ✅
- 개념 MD 작성 (phase12-structured-logging.md)
- logstash-logback-encoder 8.0 의존성 추가
- logback-spring.xml: default(콘솔) / prod(JSON) 프로파일 분리
- RequestLoggingFilter (WebFilter): MDC requestId 설정, 요청/응답 로깅, 처리 시간 측정
- MDC 필드: requestId, method, path

### Phase 13 - Testcontainers (테스트 DB 자동화) ✅
- 개념 MD 작성 (phase13-testcontainers.md)
- spring-boot-testcontainers + testcontainers:postgresql + testcontainers:r2dbc 의존성
- TestcontainersConfig: @ServiceConnection PostgreSQLContainer 자동 기동
- 22개 전체 테스트 클래스에 @Import(TestcontainersConfig) 적용
- Docker만 있으면 로컬 PostgreSQL 없이 테스트 가능

## 환경 정보
- Java: 25 (OpenJDK 25.0.2)
- Kotlin: 2.3.10
- Spring Boot: 4.0.3
- Gradle: 9.4.0
- PostgreSQL: 17-alpine (Docker)
- Kotlin 모듈 포트: 8080, Java 모듈 포트: 8081
- Spring Boot 4.x Flyway: `spring-boot-starter-flyway` + `spring.flyway.url` JDBC URL 명시 필요

## Git
- 리포지토리: https://github.com/SongJunSub/channel-manager
- 커밋 컨벤션: Conventional Commits
- Phase 완료 시마다 커밋 & 푸시
