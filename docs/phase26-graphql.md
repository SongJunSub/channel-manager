# Phase 26 — GraphQL API

## 1. GraphQL이란?

### 1.1 REST vs GraphQL

```
REST:
  GET /api/reservations/1        → 예약 전체 필드 반환 (Over-fetching)
  GET /api/reservations/1        → guestName만 필요한데 전부 옴
  GET /api/reservations          → 목록 가져온 후
  GET /api/channels/3            → 채널 정보 별도 요청 (Under-fetching, N+1)

GraphQL:
  POST /graphql
  { query: "{ reservation(id: 1) { guestName channel { channelName } } }" }
  → 요청한 필드만 정확히 반환 (No Over/Under-fetching)
```

| 비교 | REST | GraphQL |
|------|------|---------|
| 엔드포인트 | 리소스별 여러 개 (/api/reservations, /api/channels) | 단일 (/graphql) |
| 응답 형태 | 서버가 결정 (고정) | 클라이언트가 결정 (유연) |
| Over-fetching | 불필요한 필드도 모두 반환 | 요청한 필드만 반환 |
| Under-fetching | 관련 데이터 별도 요청 필요 | 한 쿼리로 연관 데이터 포함 |
| 적합한 경우 | CRUD, 단순 API | 복잡한 관계, 모바일 앱 |

### 1.2 GraphQL 핵심 개념

```
┌──────────────┐
│   Schema     │ ← 타입 정의 (스키마 우선 개발)
│  type Query  │
│  type Mutation│
│  type Type   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Resolver   │ ← 각 필드를 실제 데이터로 해석
│  @QueryMapping│
│  @MutationMapping│
│  @SchemaMapping│
└──────────────┘
```

- **Schema**: API의 타입과 연산을 정의하는 계약 (SDL: Schema Definition Language)
- **Query**: 읽기 연산 (REST의 GET)
- **Mutation**: 쓰기 연산 (REST의 POST/PUT/DELETE)
- **Resolver**: 스키마의 각 필드를 실제 데이터로 매핑하는 함수

## 2. GraphQL 스키마 (SDL)

### 2.1 타입 정의

```graphql
# 예약 타입 — REST의 ReservationResponse에 대응
type Reservation {
    id: ID!                  # Non-null ID
    guestName: String!       # Non-null String
    checkInDate: String!     # 날짜 (GraphQL에는 Date 타입이 없어 String 사용)
    checkOutDate: String!
    totalPrice: Float
    status: String!
    channel: Channel         # 연관 타입 — 한 쿼리로 함께 조회 가능
}

type Channel {
    id: ID!
    channelCode: String!
    channelName: String!
}
```

### 2.2 Query / Mutation

```graphql
type Query {
    # 예약 단건 조회
    reservation(id: ID!): Reservation

    # 예약 목록 조회
    reservations(channelId: Long, status: String): [Reservation!]!

    # 통계 요약
    statisticsSummary: StatisticsSummary!
}

type Mutation {
    # 예약 생성
    createReservation(input: CreateReservationInput!): Reservation!
}
```

## 3. Spring for GraphQL

### 3.1 Spring GraphQL + WebFlux

```
spring-boot-starter-graphql
  ├── GraphQL Java (graphql-java) — GraphQL 실행 엔진
  ├── Spring GraphQL — Spring 통합 (어노테이션 기반)
  └── GraphiQL — 브라우저 기반 GraphQL IDE (/graphiql)
```

- Spring Boot 3.x+ 에서 공식 지원
- WebFlux와 완벽 통합 — `Mono`/`Flux` 반환 타입 자동 처리
- 기존 Repository/Service를 그대로 사용

### 3.2 어노테이션 기반 리졸버

```kotlin
@Controller  // Spring GraphQL은 @Controller를 사용 (@RestController 아님)
class ReservationGraphqlController {

    @QueryMapping  // type Query { reservation(id: ID!): Reservation }
    fun reservation(@Argument id: Long): Mono<Reservation> = ...

    @MutationMapping  // type Mutation { createReservation(...): Reservation }
    fun createReservation(@Argument input: CreateReservationInput): Mono<Reservation> = ...

    @SchemaMapping(typeName = "Reservation", field = "channel")  // 연관 필드 리졸버
    fun channel(reservation: Reservation): Mono<Channel> = ...
}
```

### 3.3 GraphiQL — 브라우저 IDE

```
http://localhost:8080/graphiql

┌─────────────────────────────────────┐
│           GraphiQL IDE              │
│                                     │
│ Query:                              │
│ {                                   │
│   reservation(id: 1) {              │
│     guestName                       │
│     checkInDate                     │
│     channel {                       │
│       channelName                   │
│     }                               │
│   }                                 │
│ }                                   │
│                                     │
│ Result:                             │
│ { "data": { "reservation": {       │
│     "guestName": "김민준",           │
│     "checkInDate": "2026-04-20",    │
│     "channel": {                    │
│       "channelName": "Booking.com"  │
│     }                               │
│ } } }                               │
└─────────────────────────────────────┘
```

## 4. 이 프로젝트의 GraphQL 구성

### 4.1 스키마 설계

```
Query:
  - reservation(id: ID!): Reservation          — 예약 단건 조회
  - reservations: [Reservation!]!              — 예약 목록
  - statisticsSummary: StatisticsSummary!      — 전체 요약 통계

Mutation:
  - createReservation(input: ...): Reservation! — 예약 생성

Type:
  - Reservation (+ channel 연관 필드)
  - Channel
  - StatisticsSummary
```

### 4.2 구현 파일

```
resources/graphql/
  └── schema.graphqls              ← GraphQL 스키마 정의 (SDL)
controller/
  └── ReservationGraphqlController.kt (.java) ← Query/Mutation/SchemaMapping 리졸버
```
