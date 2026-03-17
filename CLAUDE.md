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
