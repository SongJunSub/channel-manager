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

### Phase 14 - 동시성 테스트 ✅
- 개념 MD 작성 (phase14-concurrency-test.md)
- 동시 10건 예약: Flux.merge 병렬 실행 → FOR UPDATE 잠금으로 재고 정합성 보장
- 재고 부족 시 초과 예약 방지: 재고 0일 때 모든 추가 예약 400 실패
- 통합 테스트 (Kotlin 2개 + Java 2개)

### Phase 15 - Docker 이미지 빌드 (컨테이너화) ✅
- 개념 MD 작성 (phase15-docker.md)
- application.yml 환경변수화: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD (기본값으로 로컬 개발 호환)
- 멀티스테이지 Dockerfile (Kotlin + Java): eclipse-temurin:25-jdk-alpine(빌드) → 25-jre-alpine(실행)
- 레이어 캐싱 최적화: Gradle Wrapper → 빌드 설정 → 의존성 다운로드 → 소스 복사 → 빌드
- 보안: 비루트 사용자(appuser) 실행, JVM 컨테이너 메모리 인식(-XX:+UseContainerSupport)
- docker-compose.yml 확장: PostgreSQL + kotlin-app + java-app 통합 (healthcheck + depends_on)
- .dockerignore 추가: 빌드 결과물, IDE 설정, docs 제외
- 이미지 크기: Kotlin 181MB, Java 168MB
- 동작 확인: `docker compose up -d` → 3개 컨테이너 정상 기동, API 응답 확인

### Phase 27 - Resilience4j 서킷 브레이커 ✅
- 개념 MD 작성 (phase27-circuit-breaker.md)
- resilience4j-spring-boot3 + resilience4j-reactor 의존성 추가
- ResilienceConfig: CircuitBreaker + Retry 빈 (상태 전이/재시도 이벤트 로깅)
- ChannelSimulator: WebClient.post()에 CircuitBreakerOperator + RetryOperator 적용
- 서킷 브레이커: 10건 슬라이딩 윈도우, 실패율 50%, OPEN 10초, HALF_OPEN 3건
- 재시도: 최대 3회, 1초 간격
- 폴백: onErrorResume으로 최종 실패 시 로그 후 계속

### Phase 26 - GraphQL API ✅
- 개념 MD 작성 (phase26-graphql.md)
- spring-boot-starter-graphql 의존성 추가
- schema.graphqls: Query(reservation, reservations, statisticsSummary, channels) + Mutation(createReservation)
- ReservationGraphqlController: @QueryMapping, @MutationMapping, @SchemaMapping (연관 채널)
- GraphiQL IDE: /graphiql 접근 가능
- SecurityConfig: /graphql, /graphiql permitAll 추가
- 기존 REST API 유지 — GraphQL은 보완적 대안

### Phase 25 - Kafka 메시지 큐 연동 ✅
- 개념 MD 작성 (phase25-kafka.md)
- spring-kafka + reactor-kafka 의존성 추가
- KafkaEventProducer: 예약 생성/취소 시 reservation-events 토픽에 발행
- KafkaEventConsumer: @KafkaListener로 메시지 수신 + 로그 기록
- ReservationEventMessage: Kafka 메시지 DTO (key=channelCode, value=JSON)
- ReservationService: doOnNext에서 KafkaEventProducer 호출 추가
- docker-compose: Apache Kafka 3.9 KRaft 모드 서비스 추가 (Zookeeper 불필요)
- application.yml: Kafka bootstrap-servers, producer/consumer serializer 설정

### Phase 24 - 이벤트 소싱 + CQRS ✅
- 개념 MD 작성 (phase24-event-sourcing-cqrs.md)
- V10 마이그레이션: inventory_events 테이블 (이벤트 저장소)
- InventoryEvent 엔티티 + InventoryEventRepository (이벤트 조회/저장)
- InventoryCommandService: 재고 조정/초기화 이벤트 저장 (쓰기 모델)
- InventoryQueryService: 이벤트 재생으로 현재 상태 계산 (읽기 모델)
- InventoryEventController: CQRS API (adjust, history, events, snapshot)
- 이벤트 재생: delta 합산으로 가용 수량 계산 (fold/reduce)

### Phase 23 - WebSocket 실시간 양방향 통신 ✅
- 개념 MD 작성 (phase23-websocket.md)
- EventWebSocketHandler: EventPublisher(Sinks)를 WebSocket으로 브로드캐스트 (Kotlin + Java)
- WebSocketConfig: SimpleUrlHandlerMapping(/ws/events) + WebSocketHandlerAdapter
- SecurityConfig: /ws/** permitAll() 추가
- RateLimitFilter: /ws 경로 제외 추가
- 대시보드 JS: WebSocket 연결 옵션 추가 (?mode=ws로 전환, 자동 재연결)
- 기존 SSE 방식 유지 (기본값), WebSocket은 대안 연결 방식

### Phase 22 - 분산 추적 (Micrometer Tracing + Zipkin) ✅
- 개념 MD 작성 (phase22-distributed-tracing.md)
- micrometer-tracing-bridge-brave + zipkin-reporter-brave 의존성 추가
- application.yml: tracing.sampling.probability=1.0, zipkin endpoint 환경변수화
- logging.pattern.level: traceId, spanId 자동 포함
- docker-compose.yml: Zipkin 3 서비스 추가 (:9411) + ZIPKIN_ENDPOINT 환경변수
- 자동 계측: HTTP Server/Client, R2DBC, Redis, Reactor 체인

### Phase 21 - Spring Security + JWT 인증 ✅
- 개념 MD 작성 (phase21-security-jwt.md)
- spring-boot-starter-security + jjwt 0.12.6 의존성 추가
- V9 마이그레이션: users 테이블 (username, password BCrypt, role, enabled)
- User 엔티티 + UserRepository (findByUsername)
- JwtUtil: HMAC-SHA256 토큰 생성/검증/클레임 추출
- JwtAuthenticationFilter: Authorization Bearer 토큰 → SecurityContext
- SecurityConfig: 경로별 접근 제어 (공개/USER/ADMIN)
- AuthController: POST /api/auth/register (201) + POST /api/auth/login
- DataInitializer: 앱 시작 시 admin/user 샘플 계정 자동 생성 (BCrypt)
- AuthDto: AuthRequest + AuthResponse (Kotlin data class / Java record)
- TestSecurityConfig: 기존 22개 테스트 클래스에 보안 우회 적용

### Phase 20 - Kubernetes 배포 ✅
- 개념 MD 작성 (phase20-kubernetes.md)
- Kustomize 기반 K8s 매니페스트 (k8s/base/)
- Namespace: channel-manager
- ConfigMap: DB/Redis 접속 정보, Rate Limit 설정
- Secret: DB 비밀번호 (base64)
- PostgreSQL: Deployment(1 replica) + PVC(1Gi) + ClusterIP Service
- Redis: Deployment(1 replica, 리소스 제한) + ClusterIP Service
- Kotlin앱: Deployment(2 replicas, liveness/readiness probe) + LoadBalancer Service
- Java앱: Deployment(2 replicas, liveness/readiness probe) + LoadBalancer Service
- 배포: kubectl apply -k k8s/base/

### Phase 19 - 모니터링 (Actuator + Prometheus + Grafana) ✅
- 개념 MD 작성 (phase19-monitoring.md)
- spring-boot-starter-actuator + micrometer-registry-prometheus 의존성 추가
- application.yml: Actuator 엔드포인트 노출 (health, info, metrics, prometheus)
- 헬스 체크 상세 표시: show-details: always (DB, Redis 상태 포함)
- 커스텀 비즈니스 메트릭: reservations.created (채널별 태그), reservations.cancelled
- Prometheus (prom/prometheus:v3.4.0): 10초 간격 스크래핑, prometheus.yml 설정
- Grafana (grafana/grafana:11.6.0): Prometheus 데이터소스 자동 프로비저닝
- docker-compose.yml: Prometheus(:9090) + Grafana(:3000) 서비스 추가
- RateLimitFilter: /actuator/ 경로 Rate Limiting 제외 추가

### Phase 18 - Redis 캐싱 ✅
- 개념 MD 작성 (phase18-redis-caching.md)
- spring-boot-starter-data-redis-reactive 의존성 추가 (Lettuce 드라이버)
- RedisConfig: ReactiveRedisTemplate<String, String> 문자열 직렬화 + ObjectMapper 빈
- CacheService: Cache-Aside 패턴 (getOrLoad + evictStatisticsCache)
- StatisticsController: 4개 통계 API에 Redis 캐시 적용 (TTL 5분)
- ReservationService: 예약 생성/취소 시 통계 캐시 무효화 (doOnNext)
- docker-compose.yml: Redis 7 Alpine 서비스 추가 + healthcheck
- application.yml: Redis 호스트/포트 환경변수화 + Rate Limit 설정 외부화
- Testcontainers: Redis 컨테이너 추가 (com.redis:testcontainers-redis)
- RateLimitFilter: 설정값 외부화 (@Value) — 테스트 안정성 확보

### Phase 17 - Rate Limiting (API 호출 제한) ✅
- 개념 MD 작성 (phase17-rate-limiting.md)
- Bucket4j (bucket4j_jdk17-core:8.16.1) 의존성 추가
- RateLimitFilter (WebFilter): Token Bucket 알고리즘 기반 IP별 호출 제한 (Kotlin + Java)
- 정책: IP당 초당 50건, 초과 시 429 Too Many Requests + Retry-After 헤더
- 제외 경로: Swagger UI, OpenAPI 스펙, 대시보드, SSE 스트림
- 통합 테스트 (Kotlin 4개 + Java 4개): 정상 응답, 429 반환, Retry-After, 제외 경로

### Phase 16 - GitHub Actions CI/CD ✅
- 개념 MD 작성 (phase16-cicd.md)
- CI 워크플로우 (.github/workflows/ci.yml): push/PR 시 Kotlin/Java 모듈 병렬 빌드 + 테스트
- CD 워크플로우 (.github/workflows/cd.yml): main push 시 Docker 이미지 빌드 → GHCR 푸시
- 매트릭스 전략: Kotlin/Java 모듈 병렬 실행
- Gradle 캐싱: gradle/actions/setup-gradle@v4
- Docker BuildKit 캐싱: cache-from/cache-to type=gha
- 테스트 리포트 아티팩트 업로드 (실패 시에도 업로드)
- GHCR 태그 전략: latest + sha-{commit_hash}

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

## 커밋 전 필수 검증
- 커밋 & 푸시 전에 반드시 3개의 병렬 리뷰 에이전트를 실행한다.
- 3명의 독립된 개발자가 PR을 리뷰하는 것처럼, 각 에이전트가 Code Reuse / Code Quality / Efficiency **전체 관점**을 모두 검토한다.
- 즉, 관점별 1명이 아니라 3명이 각각 전체 리뷰를 수행하여 서로 다른 시각에서 이슈를 발견한다.
- 3명의 리뷰에서 발견된 이슈를 모두 수정한 후에만 커밋 & 푸시한다.
