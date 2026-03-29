# Channel Manager — 호텔 멀티 채널 예약 동기화 시스템

Spring WebFlux 학습 목적의 호텔 채널 매니저 프로젝트입니다.
동일한 비즈니스 로직을 **Kotlin**과 **Java**로 각각 구현하여 두 언어를 직접 비교할 수 있습니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 | Kotlin 2.3, Java 25 |
| 프레임워크 | Spring Boot 4.x, Spring WebFlux |
| 데이터 | PostgreSQL 17, Spring Data R2DBC |
| 마이그레이션 | Flyway |
| 빌드 | Gradle 9.4 (Kotlin DSL), 멀티 모듈 |
| 프론트 | HTML + Vanilla JS (EventSource) |
| 테스트 | JUnit 5, StepVerifier, WebTestClient |

## 프로젝트 구조

```
channel-manager/
├── channel-manager-kotlin/     # Kotlin 구현 (포트 8080)
├── channel-manager-java/       # Java 구현 (포트 8081)
├── channel-manager-common/     # 공유 리소스
│   └── src/main/resources/
│       ├── db/migration/       # Flyway SQL (V1~V7)
│       └── static/             # 대시보드 (HTML/CSS/JS)
└── docs/                       # Phase별 개념 정리 문서
```

## 실행 방법

### 1. PostgreSQL 기동

Docker Desktop이 실행 중인 상태에서:

```bash
docker compose up -d
```

컨테이너가 정상 실행되면 `channel_manager` 데이터베이스가 자동 생성됩니다.

### 2. DB 접속 정보

| 항목 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `5432` |
| Database | `channel_manager` |
| User | `postgres` |
| Password | `postgres` |

**CLI 접속:**
```bash
docker exec -it channel-manager-postgres psql -U postgres -d channel_manager
```

### 3. 애플리케이션 실행

```bash
# Kotlin (포트 8080)
./gradlew :channel-manager-kotlin:bootRun

# Java (포트 8081)
./gradlew :channel-manager-java:bootRun
```

앱 시작 시 Flyway가 자동으로 테이블과 샘플 데이터를 생성합니다.

### 4. 대시보드 접속

- Kotlin: http://localhost:8080/
- Java: http://localhost:8081/

### 5. 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 모듈별 테스트
./gradlew :channel-manager-kotlin:test
./gradlew :channel-manager-java:test
```

### 6. Docker 종료

```bash
# 컨테이너 중지 (데이터 유지)
docker compose stop

# 컨테이너 + 볼륨 삭제 (데이터 초기화)
docker compose down -v
```

## API 엔드포인트

### 재고 관리

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/inventories/{id}` | 재고 단건 조회 |
| GET | `/api/inventories?roomTypeId=&startDate=&endDate=` | 기간별 재고 조회 |
| POST | `/api/inventories` | 재고 생성 |
| POST | `/api/inventories/bulk` | 재고 일괄 생성 |
| PUT | `/api/inventories/{id}` | 재고 수정 |
| DELETE | `/api/inventories/{id}` | 재고 삭제 |

### 예약

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/reservations` | 예약 생성 (재고 차감 + 이벤트 기록) |
| DELETE | `/api/reservations/{id}` | 예약 취소 (재고 복구 + 보상 트랜잭션) |

### 시뮬레이터

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/simulator/start` | 채널 시뮬레이터 시작 (3초 간격 자동 예약) |
| POST | `/api/simulator/stop` | 채널 시뮬레이터 중지 |
| GET | `/api/simulator/status` | 시뮬레이터 상태 조회 |

### SSE 이벤트 스트림

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/events/stream` | SSE 실시간 이벤트 스트림 (text/event-stream) |
| GET | `/api/events?limit=50` | 최근 이벤트 목록 (JSON) |

### 통계

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/statistics/channels` | 채널별 예약/매출 통계 |
| GET | `/api/statistics/events` | 이벤트 타입별 발생 건수 |
| GET | `/api/statistics/rooms` | 객실 타입별 예약/매출 통계 |
| GET | `/api/statistics/summary` | 전체 요약 통계 |

## Phase 구성

| Phase | 내용 | 핵심 패턴 |
|-------|------|-----------|
| 1 | 프로젝트 초기화 & 도메인 설계 | R2DBC, Flyway, 멀티 모듈 |
| 2 | 인벤토리 관리 API | Mono/Flux CRUD |
| 3 | 채널 시뮬레이터 | Flux.interval, WebClient, Disposable |
| 4 | 스트림 통합 & 재고 동기화 | Sinks, Flux.merge, 비관적 잠금 (FOR UPDATE) |
| 5 | SSE 이벤트 발행 | ServerSentEvent, text/event-stream, heartbeat |
| 6 | 실시간 대시보드 | EventSource API, Vanilla JS, DOM 조작 |
| 7 | 예약 취소 & 재고 복구 | 보상 트랜잭션 (Compensating Transaction) |
| 8 | 통계 & 리포트 | groupBy, reduce, Mono.zip 병렬 집계 |

각 Phase의 상세 개념 설명은 `docs/` 디렉토리의 MD 파일을 참고하세요.

## 도메인 모델

```
Property (숙소)
  └── RoomType (객실 타입)
        ├── Inventory (날짜별 재고)
        └── Reservation (예약)

Channel (판매 채널: 자사, Booking.com, Agoda, Trip.com)
ChannelEvent (이벤트 로그: 생성/변경/취소/동기화)
```

## 샘플 데이터

Flyway V7 마이그레이션으로 자동 삽입됩니다:

- **숙소**: 서울신라호텔, 파라다이스 호텔 부산
- **객실**: Superior Double(35만), Deluxe Twin(48만), Executive Suite(98만) 등
- **채널**: DIRECT(자사), BOOKING(Booking.com), AGODA(Agoda), TRIP(Trip.com)
- **투숙객**: 김민준, James Wilson, 田中太郎

## 아키텍처

```
┌─ 브라우저 (대시보드) ────────────────────────┐
│  EventSource → SSE 실시간 이벤트 수신        │
│  fetch → REST API (시뮬레이터, 통계)         │
└──────────────────────────────────────────────┘
                    │
┌─ Spring WebFlux (Netty) ─────────────────────┐
│                                               │
│  Controller → Service → Repository → R2DBC   │
│                    │                          │
│              EventPublisher (Sinks)           │
│                ┌───┴───┐                      │
│       InventorySyncService  SSE Controller    │
│       (채널 동기화)        (브라우저 전송)     │
│                                               │
│  ChannelSimulator (Flux.interval + WebClient) │
└───────────────────────────────────────────────┘
                    │
┌─ PostgreSQL ─────────────────────────────────┐
│  properties, room_types, channels,           │
│  inventories, reservations, channel_events   │
└──────────────────────────────────────────────┘
```
