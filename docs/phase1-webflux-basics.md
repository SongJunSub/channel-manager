# Phase 1: WebFlux & R2DBC 기본 개념

## 목차
1. [전통적인 웹 서버(Servlet) vs Reactive 웹 서버(WebFlux)](#1-전통적인-웹-서버servlet-vs-reactive-웹-서버webflux)
2. [왜 Reactive가 필요한가?](#2-왜-reactive가-필요한가)
3. [Reactor 핵심 개념: Mono와 Flux](#3-reactor-핵심-개념-mono와-flux)
4. [R2DBC란 무엇인가?](#4-r2dbc란-무엇인가)
5. [Spring Data R2DBC](#5-spring-data-r2dbc)
6. [Flyway와 R2DBC](#6-flyway와-r2dbc)
7. [도메인 모델 설계](#7-도메인-모델-설계)

---

## 1. 전통적인 웹 서버(Servlet) vs Reactive 웹 서버(WebFlux)

### 1-1. Servlet 기반 (Spring MVC)

전통적인 Spring MVC는 **Servlet** 위에서 동작한다.

```
[클라이언트 요청] → [톰캣 스레드 풀] → [스레드 1개가 요청 처리] → [응답 반환]
```

**동작 방식:**
- 클라이언트가 HTTP 요청을 보내면, 서버는 **스레드 풀**에서 스레드 1개를 꺼내 그 요청에 할당한다.
- 그 스레드는 요청 처리가 **완전히 끝날 때까지** 점유된다.
- DB 조회, 외부 API 호출 등 I/O 작업이 있으면 스레드는 **결과가 올 때까지 대기(블로킹)** 한다.
- 처리가 끝나면 응답을 반환하고, 스레드는 풀에 돌아간다.

**문제점:**
- 스레드 하나가 I/O 대기 중에도 **아무 일도 못 하고 묶여 있다.**
- 동시 요청이 많아지면 스레드가 부족해지고, 새 요청은 **큐에서 대기**해야 한다.
- 스레드를 늘리면 메모리 사용량이 급증한다. (스레드 1개 ≈ 1MB 스택 메모리)

```
예시: 스레드 풀 크기 = 200

200개의 요청이 동시에 DB 조회(각 100ms)를 한다면?
→ 200개의 스레드가 모두 100ms 동안 블로킹
→ 201번째 요청은 스레드가 반환될 때까지 대기
```

### 1-2. Reactive 기반 (Spring WebFlux)

Spring WebFlux는 **Netty** 위에서 동작하며, **이벤트 루프(Event Loop)** 방식을 사용한다.

```
[클라이언트 요청] → [이벤트 루프] → [작업 등록] → [I/O 완료 시 콜백] → [응답 반환]
```

**동작 방식:**
- 소수의 스레드(보통 CPU 코어 수만큼)가 **이벤트 루프**를 돌며 요청을 받는다.
- I/O 작업이 필요하면, 작업을 **등록만 하고 스레드를 반환**한다. (블로킹하지 않음)
- I/O가 완료되면 **이벤트가 발생**하고, 이벤트 루프가 나머지 처리를 이어간다.
- 스레드가 대기하지 않으므로, 적은 스레드로도 **수많은 동시 요청을 처리**할 수 있다.

```
예시: 이벤트 루프 스레드 = 8개 (8코어 CPU)

1000개의 요청이 동시에 DB 조회를 한다면?
→ 8개의 스레드가 1000개의 요청을 번갈아 처리
→ I/O 대기 중인 요청의 스레드는 다른 요청 처리에 활용
→ 스레드 부족 문제가 발생하지 않음
```

### 1-3. 비유로 이해하기

| | Servlet (Spring MVC) | Reactive (WebFlux) |
|---|---|---|
| 비유 | **은행 창구** | **카페 주문** |
| 설명 | 창구 직원이 한 고객의 업무가 끝날 때까지 붙어있음 | 주문받고 → 다음 손님 주문 → 음료 완성되면 호출 |
| 스레드 | 요청당 1개 점유 | 소수 스레드가 이벤트 기반으로 처리 |
| I/O 대기 | 스레드가 블로킹됨 | 스레드를 반환하고 완료 시 콜백 |
| 장점 | 코드가 직관적, 디버깅 쉬움 | 높은 동시성, 적은 리소스 |
| 단점 | 동시성 한계, 메모리 소비 큼 | 코드 복잡, 디버깅 어려움 |

### 1-4. 언제 WebFlux를 쓰는가?

**WebFlux가 적합한 경우:**
- I/O 바운드 작업이 많은 경우 (DB 조회, 외부 API 호출 등)
- 높은 동시 접속을 처리해야 하는 경우
- SSE(Server-Sent Events), WebSocket 등 스트리밍이 필요한 경우
- **← 이 프로젝트: 멀티 채널 실시간 예약 동기화**

**Spring MVC가 적합한 경우:**
- CPU 바운드 작업이 많은 경우 (복잡한 계산)
- 팀이 Reactive에 익숙하지 않은 경우
- 기존 블로킹 라이브러리를 많이 사용하는 경우

---

## 2. 왜 Reactive가 필요한가?

### 2-1. Reactive Streams 표준

Reactive Streams는 **비동기 스트림 처리의 표준 명세**이다. 다음 4개의 인터페이스로 구성된다.

```java
// 데이터를 발행하는 쪽
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> s);
}

// 데이터를 수신하는 쪽
public interface Subscriber<T> {
    void onSubscribe(Subscription s);   // 구독 시작 시 호출
    void onNext(T t);                   // 데이터가 도착할 때마다 호출
    void onError(Throwable t);          // 에러 발생 시 호출
    void onComplete();                  // 모든 데이터 발행 완료 시 호출
}

// 구독을 제어하는 연결 고리
public interface Subscription {
    void request(long n);               // n개의 데이터를 요청 (Backpressure)
    void cancel();                      // 구독 취소
}

// Publisher이면서 Subscriber인 중간 처리자
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

### 2-2. Backpressure (배압)

Backpressure는 **수신자가 발행자에게 "나 지금 이만큼만 처리할 수 있어"라고 알려주는 메커니즘**이다.

```
[빠른 생산자] ──데이터──→ [느린 소비자]

Backpressure 없을 때:
  생산자가 초당 1000개 → 소비자가 초당 100개 처리 → 메모리 폭발 💥

Backpressure 있을 때:
  소비자: "10개만 보내줘" → 생산자: 10개 전송 → 소비자 처리 완료 → "다음 10개 보내줘"
```

**이 프로젝트에서의 의미:**
- 여러 채널(OTA_A, OTA_B)에서 예약이 동시에 쏟아질 때
- 시스템이 처리할 수 있는 만큼만 받아서 처리
- 메모리 과부하 방지

### 2-3. Reactive 프로그래밍의 핵심 원칙

1. **비동기(Asynchronous):** 작업 완료를 기다리지 않고 다음 작업을 진행한다.
2. **논블로킹(Non-blocking):** 스레드를 점유하지 않고 I/O 완료 이벤트를 기다린다.
3. **데이터 스트림(Data Stream):** 데이터를 하나의 흐름(스트림)으로 처리한다.
4. **Backpressure:** 소비자가 처리 속도를 제어한다.

---

## 3. Reactor 핵심 개념: Mono와 Flux

Spring WebFlux는 **Project Reactor** 라이브러리를 사용한다. Reactor는 Reactive Streams 명세의 구현체이다.

### 3-1. Mono: 0개 또는 1개의 데이터

`Mono<T>`는 **최대 1개의 데이터**를 발행하는 Publisher이다.

```
Mono<T>:  ──[데이터 0~1개]──|  (완료)
                            또는
           ──[에러]──X        (에러)
```

**사용 시점:**
- DB에서 1건 조회 (findById)
- 1건 저장/수정/삭제
- 외부 API 단건 호출

```kotlin
// Kotlin 예시
fun findHotelById(id: Long): Mono<Hotel> {
    return hotelRepository.findById(id)  // 결과가 0개(없음) 또는 1개
}
```

```java
// Java 예시
public Mono<Hotel> findHotelById(Long id) {
    return hotelRepository.findById(id); // 결과가 0개(없음) 또는 1개
}
```

### 3-2. Flux: 0개 ~ N개의 데이터

`Flux<T>`는 **0개부터 무한개까지**의 데이터를 발행하는 Publisher이다.

```
Flux<T>:  ──[1]──[2]──[3]──...──[N]──|  (완료)
                                       또는
           ──[1]──[2]──[에러]──X        (에러)
```

**사용 시점:**
- DB에서 여러 건 조회 (findAll)
- 실시간 이벤트 스트림 (SSE)
- 주기적 데이터 발행 (Flux.interval)

```kotlin
// Kotlin 예시
fun findAllHotels(): Flux<Hotel> {
    return hotelRepository.findAll()  // 0개 ~ N개의 호텔 목록
}
```

```java
// Java 예시
public Flux<Hotel> findAllHotels() {
    return hotelRepository.findAll(); // 0개 ~ N개의 호텔 목록
}
```

### 3-3. 핵심 연산자 (Operator)

Mono와 Flux는 데이터를 변환하고 조합하는 다양한 **연산자**를 제공한다.

#### 변환 (Transforming)

```kotlin
// map: 데이터를 1:1 변환
Mono.just("hello")
    .map { it.uppercase() }        // "HELLO"

// flatMap: 데이터를 비동기 변환 (Publisher 반환)
Mono.just(1L)
    .flatMap { id -> hotelRepository.findById(id) }  // Mono<Hotel>
```

**map vs flatMap 차이:**
```
map:     [A] → 동기 변환 → [B]              (값 → 값)
flatMap: [A] → 비동기 변환 → Mono<B> → [B]   (값 → Publisher → 값)
```

- `map`: 단순 변환 (문자열 변환, 타입 변환 등)
- `flatMap`: DB 조회, API 호출 등 비동기 작업이 필요할 때

#### 필터링 (Filtering)

```kotlin
// filter: 조건에 맞는 데이터만 통과
Flux.just(1, 2, 3, 4, 5)
    .filter { it % 2 == 0 }        // 2, 4
```

#### 조합 (Combining)

```kotlin
// zip: 여러 Publisher의 결과를 조합
Mono.zip(
    hotelRepository.findById(1L),          // Mono<Hotel>
    roomTypeRepository.findByHotelId(1L)   // Flux<RoomType> → collectList → Mono<List>
).map { tuple ->
    HotelDetail(tuple.t1, tuple.t2)        // 두 결과를 합쳐서 새 객체 생성
}
```

#### 에러 처리 (Error Handling)

```kotlin
// onErrorReturn: 에러 시 기본값 반환
hotelRepository.findById(id)
    .onErrorReturn(Hotel.empty())

// onErrorResume: 에러 시 대체 Publisher로 전환
hotelRepository.findById(id)
    .onErrorResume { error ->
        Mono.error(NotFoundException("Hotel not found: $id"))
    }
```

### 3-4. 구독 (Subscribe) - 아무도 구독하지 않으면 아무 일도 일어나지 않는다

**가장 중요한 개념:** Mono와 Flux는 **구독(subscribe)하기 전까지 아무 일도 하지 않는다.**

```kotlin
// ❌ 이 코드는 아무 일도 하지 않는다!
hotelRepository.findById(1L)    // Mono를 만들었지만 구독하지 않음

// ✅ 구독해야 실제로 실행된다
hotelRepository.findById(1L)
    .subscribe { hotel -> println(hotel) }
```

**Spring WebFlux에서는 프레임워크가 자동으로 구독한다:**
```kotlin
@GetMapping("/hotels/{id}")
fun getHotel(@PathVariable id: Long): Mono<Hotel> {
    return hotelRepository.findById(id)   // 반환만 하면 WebFlux가 자동 구독
}
```

### 3-5. Cold vs Hot Publisher

```
Cold Publisher (차가운 발행자):
- 구독할 때마다 처음부터 데이터를 발행한다.
- 예: DB 조회 → 구독자마다 각각 쿼리 실행
- Mono.just(), Flux.fromIterable() 등

Hot Publisher (뜨거운 발행자):
- 구독 여부와 관계없이 데이터를 발행한다.
- 구독 시점 이후의 데이터만 수신한다.
- 예: 실시간 이벤트 스트림 → Phase 5에서 SSE에 활용
- Sinks, Flux.share() 등
```

---

## 4. R2DBC란 무엇인가?

### 4-1. JDBC vs R2DBC

**JDBC (Java Database Connectivity):**
```
[애플리케이션] → [JDBC 드라이버] → [DB]
                  ↑ 블로킹!
                  스레드가 쿼리 결과 올 때까지 대기
```

- 전통적인 DB 접근 방식
- **동기/블로킹**: 쿼리를 보내고 결과가 올 때까지 스레드가 대기
- Spring MVC + JPA/MyBatis에서 사용

**R2DBC (Reactive Relational Database Connectivity):**
```
[애플리케이션] → [R2DBC 드라이버] → [DB]
                  ↑ 논블로킹!
                  쿼리 보내고 스레드 반환, 결과 오면 콜백
```

- Reactive를 위한 DB 접근 방식
- **비동기/논블로킹**: 쿼리를 보내고 스레드를 반환, 결과가 오면 이벤트로 처리
- Spring WebFlux + Spring Data R2DBC에서 사용

### 4-2. 왜 R2DBC가 필요한가?

WebFlux에서 JDBC를 사용하면 **Reactive의 의미가 없어진다:**

```
[요청] → [이벤트 루프 스레드] → [JDBC 호출] → 스레드 블로킹! → Reactive 장점 상실
```

WebFlux의 논블로킹 파이프라인을 끝까지 유지하려면, DB 접근도 논블로킹이어야 한다.
이것이 R2DBC가 존재하는 이유이다.

### 4-3. R2DBC의 특징

| 항목 | JDBC | R2DBC |
|---|---|---|
| 처리 방식 | 동기/블로킹 | 비동기/논블로킹 |
| 반환 타입 | `List<T>`, `T` | `Flux<T>`, `Mono<T>` |
| 커넥션 풀 | HikariCP | r2dbc-pool |
| ORM | JPA/Hibernate | Spring Data R2DBC (경량) |
| Lazy Loading | 지원 | 미지원 |
| 연관관계 매핑 | `@OneToMany` 등 | 직접 처리 |
| 트랜잭션 | `@Transactional` | `@Transactional` (Reactive 버전) |

### 4-4. R2DBC의 제한사항

R2DBC는 JPA에 비해 **기능이 제한적**이다. 이는 의도적인 설계이다.

1. **Lazy Loading 없음:** 연관 엔티티를 자동으로 불러오지 않는다.
2. **연관관계 매핑 없음:** `@OneToMany`, `@ManyToOne` 같은 어노테이션이 없다.
3. **캐시 없음:** JPA의 1차/2차 캐시가 없다.
4. **스키마 자동 생성 없음:** `ddl-auto`가 없으므로 Flyway 등을 사용해야 한다.

**왜 이런 제한이 있는가?**
- Reactive의 핵심은 **논블로킹**이다.
- Lazy Loading은 내부적으로 추가 쿼리를 블로킹으로 실행하므로, Reactive와 맞지 않다.
- 대신, 필요한 데이터를 **명시적으로 조회**하고 **조합**하는 방식을 사용한다.

```kotlin
// JPA 방식 (Lazy Loading으로 자동 로딩)
val hotel = hotelRepository.findById(1L)
val roomTypes = hotel.roomTypes  // 자동으로 추가 쿼리 실행 (블로킹)

// R2DBC 방식 (명시적으로 조회하고 조합)
hotelRepository.findById(1L)
    .flatMap { hotel ->
        roomTypeRepository.findByHotelId(hotel.id!!)
            .collectList()
            .map { roomTypes -> HotelDetail(hotel, roomTypes) }
    }
```

---

## 5. Spring Data R2DBC

### 5-1. 의존성

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")    // WebFlux
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc") // Spring Data R2DBC
    runtimeOnly("org.postgresql:r2dbc-postgresql")                            // PostgreSQL R2DBC 드라이버
}
```

### 5-2. 설정

```yaml
# application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/channel_manager
    username: postgres
    password: postgres
```

**URL 형식 차이:**
```
JDBC:  jdbc:postgresql://localhost:5432/channel_manager
R2DBC: r2dbc:postgresql://localhost:5432/channel_manager
```

### 5-3. 엔티티 정의

```kotlin
// Kotlin
@Table("hotels")                           // 매핑할 테이블명
data class Hotel(
    @Id                                    // PK 지정
    val id: Long? = null,                  // null이면 새 엔티티 (INSERT), 아니면 기존 엔티티 (UPDATE)
    val name: String,                      // 호텔명
    val address: String,                   // 주소
    @CreatedDate                           // 생성 시각 자동 기록
    val createdAt: LocalDateTime? = null
)
```

```java
// Java
@Table("hotels")                            // 매핑할 테이블명
public class Hotel {
    @Id                                     // PK 지정
    private Long id;                        // null이면 새 엔티티 (INSERT)
    private String name;                    // 호텔명
    private String address;                 // 주소
    @CreatedDate                            // 생성 시각 자동 기록
    private LocalDateTime createdAt;
}
```

### 5-4. Repository 정의

```kotlin
// Kotlin
interface HotelRepository : ReactiveCrudRepository<Hotel, Long> {
    // 기본 제공 메서드:
    // findById(id): Mono<Hotel>
    // findAll(): Flux<Hotel>
    // save(entity): Mono<Hotel>
    // deleteById(id): Mono<Void>

    // 커스텀 쿼리 메서드
    fun findByName(name: String): Mono<Hotel>
}
```

```java
// Java
public interface HotelRepository extends ReactiveCrudRepository<Hotel, Long> {
    Mono<Hotel> findByName(String name);
}
```

**주목:** JPA의 `JpaRepository`와 달리 `ReactiveCrudRepository`를 상속한다.
반환 타입이 `List<T>` 대신 `Flux<T>`, `T` 대신 `Mono<T>`이다.

---

## 6. Flyway와 R2DBC

### 6-1. 왜 Flyway가 필요한가?

R2DBC는 JPA의 `ddl-auto` 기능이 없다. 테이블을 자동으로 생성/수정해주지 않으므로, **DB 마이그레이션 도구**가 필요하다.

**Flyway**는 SQL 파일 기반의 DB 마이그레이션 도구이다.
- 버전 관리된 SQL 파일을 순서대로 실행한다.
- 이미 실행된 마이그레이션은 다시 실행하지 않는다.
- `flyway_schema_history` 테이블로 실행 이력을 관리한다.

### 6-2. 마이그레이션 파일 규칙

```
src/main/resources/db/migration/
├── V1__create_hotels_table.sql        # 버전 1: 호텔 테이블 생성
├── V2__create_room_types_table.sql    # 버전 2: 객실 타입 테이블 생성
├── V3__create_inventories_table.sql   # 버전 3: 재고 테이블 생성
└── ...
```

**파일명 규칙:** `V{버전}__{설명}.sql`
- `V` 접두사 필수
- 버전 번호 (정수, 순서대로)
- `__` 더블 언더스코어 구분자
- 설명 (snake_case)

### 6-3. Flyway + R2DBC 통합

Flyway 자체는 JDBC 기반이므로, R2DBC 프로젝트에서는 **JDBC 드라이버를 추가로 포함**해야 한다.

```kotlin
// build.gradle.kts
dependencies {
    // R2DBC (애플리케이션 런타임)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    // Flyway (마이그레이션 실행용, JDBC 필요)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")  // JDBC 드라이버 (Flyway용)
}
```

**동작 흐름:**
```
애플리케이션 시작
  → Flyway가 JDBC로 마이그레이션 실행 (테이블 생성/수정)
  → 이후 애플리케이션은 R2DBC로 데이터 접근 (논블로킹)
```

---

## 7. 도메인 모델 설계

### 7-1. 이 프로젝트의 도메인

호텔 멀티 채널 예약 동기화 시스템의 핵심 엔티티들이다.

```
[Hotel] 1 ──── N [RoomType] 1 ──── N [Inventory]
                                          |
                                          N
                                     [Reservation]
                                          |
[Channel] ────────────────────────────────┘

[ChannelEvent] ← 모든 변경사항을 이벤트로 기록
```

### 7-2. 엔티티 설명

| 엔티티 | 설명 | 예시 |
|---|---|---|
| **Hotel** | 호텔 정보 | 서울 그랜드 호텔 |
| **RoomType** | 객실 타입 | Standard, Deluxe, Suite |
| **Inventory** | 날짜별/객실타입별 재고 | 2026-03-15 Deluxe 잔여 5개 |
| **Channel** | 판매 채널 | DIRECT(자사), OTA_A, OTA_B |
| **Reservation** | 예약 정보 | 채널, 객실, 날짜, 수량, 상태 |
| **ChannelEvent** | 변경 이벤트 | 재고 변경, 예약 생성/취소 등 |

### 7-3. 테이블 설계

```sql
-- 호텔
CREATE TABLE hotels (
    id          BIGSERIAL PRIMARY KEY,       -- 호텔 PK (자동 증가)
    name        VARCHAR(200) NOT NULL,       -- 호텔명
    address     VARCHAR(500),                -- 주소
    created_at  TIMESTAMP DEFAULT NOW()      -- 생성 시각
);

-- 객실 타입
CREATE TABLE room_types (
    id          BIGSERIAL PRIMARY KEY,       -- 객실 타입 PK
    hotel_id    BIGINT NOT NULL,             -- 호텔 FK
    name        VARCHAR(100) NOT NULL,       -- 객실 타입명 (Standard, Deluxe, Suite)
    capacity    INT NOT NULL DEFAULT 2,      -- 수용 인원
    base_price  DECIMAL(10,2) NOT NULL,      -- 기본 가격
    created_at  TIMESTAMP DEFAULT NOW(),     -- 생성 시각
    CONSTRAINT fk_room_types_hotels FOREIGN KEY (hotel_id) REFERENCES hotels(id)
);

-- 날짜별 재고
CREATE TABLE inventories (
    id              BIGSERIAL PRIMARY KEY,   -- 재고 PK
    room_type_id    BIGINT NOT NULL,         -- 객실 타입 FK
    stock_date      DATE NOT NULL,           -- 재고 날짜
    total_quantity  INT NOT NULL,            -- 전체 객실 수
    available_quantity INT NOT NULL,         -- 예약 가능 수량
    created_at      TIMESTAMP DEFAULT NOW(), -- 생성 시각
    updated_at      TIMESTAMP DEFAULT NOW(), -- 수정 시각
    CONSTRAINT fk_inventories_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id),
    CONSTRAINT uq_inventories_room_date UNIQUE (room_type_id, stock_date)  -- 객실타입+날짜 유니크
);

-- 판매 채널
CREATE TABLE channels (
    id          BIGSERIAL PRIMARY KEY,       -- 채널 PK
    code        VARCHAR(50) NOT NULL UNIQUE, -- 채널 코드 (DIRECT, OTA_A, OTA_B)
    name        VARCHAR(200) NOT NULL,       -- 채널명
    is_active   BOOLEAN DEFAULT TRUE,        -- 활성 상태
    created_at  TIMESTAMP DEFAULT NOW()      -- 생성 시각
);

-- 예약
CREATE TABLE reservations (
    id              BIGSERIAL PRIMARY KEY,       -- 예약 PK
    channel_id      BIGINT NOT NULL,             -- 채널 FK
    room_type_id    BIGINT NOT NULL,             -- 객실 타입 FK
    check_in_date   DATE NOT NULL,               -- 체크인 날짜
    check_out_date  DATE NOT NULL,               -- 체크아웃 날짜
    guest_name      VARCHAR(200) NOT NULL,       -- 투숙객 이름
    quantity        INT NOT NULL DEFAULT 1,      -- 예약 객실 수
    status          VARCHAR(50) NOT NULL,        -- 예약 상태 (CONFIRMED, CANCELLED)
    total_price     DECIMAL(12,2),               -- 총 금액
    created_at      TIMESTAMP DEFAULT NOW(),     -- 생성 시각
    updated_at      TIMESTAMP DEFAULT NOW(),     -- 수정 시각
    CONSTRAINT fk_reservations_channels FOREIGN KEY (channel_id) REFERENCES channels(id),
    CONSTRAINT fk_reservations_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id)
);

-- 채널 이벤트 (모든 변경 이력)
CREATE TABLE channel_events (
    id              BIGSERIAL PRIMARY KEY,       -- 이벤트 PK
    event_type      VARCHAR(100) NOT NULL,       -- 이벤트 타입 (INVENTORY_UPDATED, RESERVATION_CREATED 등)
    channel_id      BIGINT,                      -- 관련 채널 FK (nullable)
    reservation_id  BIGINT,                      -- 관련 예약 FK (nullable)
    room_type_id    BIGINT,                      -- 관련 객실 타입 FK (nullable)
    payload         TEXT,                        -- 이벤트 상세 데이터 (JSON)
    created_at      TIMESTAMP DEFAULT NOW(),     -- 이벤트 발생 시각
    CONSTRAINT fk_events_channels FOREIGN KEY (channel_id) REFERENCES channels(id),
    CONSTRAINT fk_events_reservations FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT fk_events_room_types FOREIGN KEY (room_type_id) REFERENCES room_types(id)
);

-- 인덱스
CREATE INDEX idx_inventories_room_date ON inventories(room_type_id, stock_date);
CREATE INDEX idx_reservations_channel ON reservations(channel_id);
CREATE INDEX idx_reservations_room_type ON reservations(room_type_id);
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_channel_events_type ON channel_events(event_type);
CREATE INDEX idx_channel_events_created ON channel_events(created_at);
```

---

## 8. 실무 코드 비교: JPA + QueryDSL vs R2DBC + DatabaseClient

이 장에서는 실제 호텔 CRS(Central Reservation System) 프로젝트의 `ProfileQueryRepository.findProfiles()` 메서드를 분석하고,
동일한 패턴을 R2DBC + DatabaseClient로 어떻게 전환하는지 비교한다.

### 8-1. 원본 코드 분석 (JPA + QueryDSL)

CRS 프로젝트의 `findProfiles()`는 프로필 목록을 조회하는 메서드이다.
다수의 LEFT JOIN, 동적 조건, 서브쿼리, 페이징을 포함하는 **실무에서 흔히 볼 수 있는 복잡한 쿼리**이다.

```java
// === CRS 프로젝트 원본: JPA + QueryDSL 방식 ===

@Repository
@RequiredArgsConstructor
public class ProfileQueryRepository {

    private final JPAQueryFactory queryFactory;  // QueryDSL의 쿼리 팩토리 (블로킹)

    public Page<ProfilesResponseDto> findProfiles(ProfileParamVo paramVo) {
        // 1. 페이징 정보 생성
        PageRequest pageRequest = QueryUtils.createPageable(
            paramVo.getPageNo(), paramVo.getPageSize(),
            paramVo.getOrderBy(), paramVo.getOrderType()
        );

        // 2. 데이터 조회 쿼리 (블로킹 - 스레드가 결과 올 때까지 대기)
        List<ProfilesResponseDto> content = queryFactory
            .select(
                Projections.bean(ProfilesResponseDto.class,  // DTO로 직접 매핑
                    profile.id,
                    profile.profileType,
                    profile.mfResortProfileId,
                    // 이름 결합: firstName + " " + lastName
                    profile.individualName.nameFirst
                        .concat(" ")
                        .concat(profile.individualName.nameSur).as("engName"),
                    profile.individualName.nameFirst.as("firstName"),
                    profile.individualName.nameSur.as("lastName"),
                    profile.individualName.namePrefix.as("namePrefix"),
                    profile.dateOfBirth.as("birth"),
                    profilePhoneNumber.phoneNumber.as("phone"),        // JOIN된 테이블 필드
                    profileElectronicAddress.eAddress.as("email"),      // JOIN된 테이블 필드
                    profilePostalAddress.countryCode.as("nationality"), // JOIN된 테이블 필드
                    profile.gender,
                    profileMembership.accountId.as("memberNo"),         // JOIN된 테이블 필드
                    profileMembership.programCode.as("grade"),
                    profileMembership.levelCode.as("level")
                )
            )
            .from(profile)
            // 대표 전화번호 JOIN (mfPrimaryYN = "Y")
            .leftJoin(profilePhoneNumber)
                .on(profilePhoneNumber.profile.eq(profile)
                    .and(profilePhoneNumber.mfPrimaryYN.eq("Y")))
            // 대표 이메일 JOIN
            .leftJoin(profileElectronicAddress)
                .on(profileElectronicAddress.profile.eq(profile)
                    .and(profileElectronicAddress.mfPrimaryYN.eq("Y")))
            // 대표 주소 JOIN
            .leftJoin(profilePostalAddress)
                .on(profilePostalAddress.profile.eq(profile)
                    .and(profilePostalAddress.mfPrimaryYN.eq("Y")))
            // 최신 멤버십 JOIN (복잡한 서브쿼리로 가장 최근 멤버십 1건 선택)
            .leftJoin(profileMembership)
                .on(profileMembership.profile.eq(profile)
                    .and(profileMembership.mfInactiveDate.isNull())
                    .and(profileMembership.programCode.isNotNull())
                    .and(profileMembership.accountId.isNotNull())
                    .and(profileMembership.id.eq(
                        JPAExpressions.select(profileMembership.id.max())
                            .from(profileMembership)
                            .where(/* 최신 멤버십 필터 조건 */))))
            // 동적 WHERE 조건 - null이면 해당 조건이 무시된다 (QueryDSL의 핵심 장점)
            .where(
                eqPropertyId(paramVo.getPropertyId()),           // propertyId가 null이면 조건 무시
                eqProfileType(paramVo.getProfileType()),         // profileType이 null이면 조건 무시
                eqMfResortProfileId(paramVo.getMfResortProfileId()),
                eqMemberNo(paramVo.getMemberNo()),
                eqFirstName(paramVo.getFirstName()),
                eqLastName(paramVo.getLastName()),
                eqProfilePhone(paramVo.getPhone()),
                profile.mfInactiveDate.isNull()                  // 비활성 프로필 제외 (고정 조건)
            )
            .orderBy(profile.id.desc())
            .offset(pageRequest.getOffset())
            .limit(pageRequest.getPageSize())
            .fetch();   // 블로킹 호출 - List<DTO> 반환

        // 3. 전체 건수 조회 (블로킹)
        Long total = queryFactory
            .select(profile.count())
            .from(profile)
            .leftJoin(profilePhoneNumber)
                .on(profilePhoneNumber.profile.eq(profile)
                    .and(profilePhoneNumber.mfPrimaryYN.eq("Y")))
            .where(/* 동일한 동적 조건 */)
            .fetchOne();   // 블로킹 호출 - Long 반환

        // 4. Page 객체로 감싸서 반환
        return new PageImpl<>(content, pageRequest, total != null ? total : 0L);
    }
}
```

**동적 조건 클래스 (QueryDSL BooleanExpression 패턴):**

```java
// QueryDSL에서 동적 조건을 처리하는 표준 패턴
// null을 반환하면 QueryDSL의 where()가 해당 조건을 자동으로 무시한다
public class ProfileQueryCondition {

    // propertyId가 null이면 null 반환 → 조건에서 제외
    public static BooleanExpression eqPropertyId(Long propertyId) {
        return propertyId == null ? null : profile.propertyId.eq(propertyId);
    }

    public static BooleanExpression eqProfileType(ProfileType profileType) {
        return profileType == null ? null : profile.profileType.eq(profileType);
    }

    // 전화번호 조건 - 하이픈 제거 후 비교하는 복잡한 로직도 포함
    public static BooleanExpression eqProfilePhone(String phone) {
        if (phone == null) return null;
        String normalizedPhone = phone.replace("-", "");
        // 하이픈 포함/미포함 모두 매칭
        return profilePhoneNumber.phoneNumber.eq(phone)
            .or(profilePhoneNumber.phoneNumber.eq(normalizedPhone));
    }

    // 대소문자 무시 비교
    public static BooleanExpression eqFirstName(String firstName) {
        return firstName == null ? null
            : profile.individualName.nameFirst.lower().eq(firstName.toLowerCase());
    }

    // EXISTS 서브쿼리를 사용하는 복잡한 조건
    public static BooleanExpression eqMemberNo(String memberNo) {
        return memberNo == null ? null : JPAExpressions.selectOne()
            .from(profileMembership)
            .where(profileMembership.profile.eq(profile)
                .and(profileMembership.accountId.eq(memberNo))
                .and(profileMembership.mfInactiveDate.isNull()))
            .exists();
    }
}
```

### 8-2. R2DBC + DatabaseClient로 전환

위 코드를 이 프로젝트(channel-manager)의 도메인에 맞게 R2DBC로 전환한다.
`findProfiles()`의 패턴을 **예약 목록 조회 (`findReservations`)**로 매핑하여 비교한다.

**도메인 매핑:**

| CRS (Profile) | Channel Manager (Reservation) |
|---|---|
| Profile | Reservation |
| ProfilePhoneNumber (LEFT JOIN) | Channel (LEFT JOIN) |
| ProfileElectronicAddress (LEFT JOIN) | RoomType (LEFT JOIN) |
| ProfileMembership (LEFT JOIN + 서브쿼리) | Inventory (LEFT JOIN) |
| ProfileParamVo (검색 조건) | ReservationSearchParam (검색 조건) |
| ProfilesResponseDto (응답 DTO) | ReservationListDto (응답 DTO) |

#### Java (R2DBC + DatabaseClient)

```java
// === Channel Manager: R2DBC + DatabaseClient 방식 ===
// 이 프로젝트에서 채택한 쿼리 작성 방식

@Repository                                                        // Spring Bean 등록
public class ReservationQueryRepository {

    private final DatabaseClient databaseClient;                   // R2DBC의 쿼리 실행 클라이언트 (논블로킹)

    public ReservationQueryRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;                      // 생성자 주입
    }

    // 예약 목록 조회 - 채널, 객실타입 JOIN + 동적 조건 + 페이징
    // CRS의 findProfiles()와 동일한 패턴을 R2DBC로 표현한 메서드
    // 반환 타입: Page<DTO> (블로킹) → Flux<DTO> (논블로킹)
    public Flux<ReservationListDto> findReservations(ReservationSearchParam param) {

        // 1. SQL을 직접 작성한다 (QueryDSL의 .select().from().leftJoin() 대신)
        // QueryDSL: Projections.bean() → SQL: SELECT 절에 별칭 지정
        // QueryDSL: .leftJoin().on() → SQL: LEFT JOIN ... ON
        StringBuilder sql = new StringBuilder("""
            SELECT r.id,
                   r.guest_name,
                   r.check_in_date,
                   r.check_out_date,
                   r.quantity,
                   r.status,
                   r.total_price,
                   c.code AS channel_code,
                   c.name AS channel_name,
                   rt.name AS room_type_name,
                   rt.base_price,
                   i.available_quantity
            FROM reservations r
            LEFT JOIN channels c ON c.id = r.channel_id
            LEFT JOIN room_types rt ON rt.id = r.room_type_id
            LEFT JOIN inventories i ON i.room_type_id = r.room_type_id
                AND i.stock_date = r.check_in_date
            WHERE 1=1
            """);
        // WHERE 1=1: 동적 조건을 AND로 추가하기 위한 기법
        // QueryDSL은 null 조건을 자동 무시하지만, Native SQL은 직접 분기해야 한다

        // 2. 동적 조건 추가 (QueryDSL의 BooleanExpression 패턴 대신)
        // CRS: eqPropertyId(paramVo.getPropertyId()) → null 반환 시 자동 무시
        // R2DBC: if (param != null) 분기로 SQL에 조건 추가
        Map<String, Object> binds = new HashMap<>();               // 바인드 파라미터를 담을 Map

        // CRS의 eqProfileType()에 대응
        if (param.getChannelId() != null) {                        // 채널 ID 조건 (null이면 무시)
            sql.append(" AND r.channel_id = :channelId");          // SQL에 조건 추가
            binds.put("channelId", param.getChannelId());          // 바인드 파라미터 등록
        }

        // CRS의 eqFirstName()에 대응 (대소문자 무시 검색)
        if (param.getGuestName() != null) {                        // 투숙객 이름 조건
            sql.append(" AND LOWER(r.guest_name) LIKE :guestName");// LOWER()로 대소문자 무시
            binds.put("guestName",                                 // LIKE 패턴으로 부분 검색
                "%" + param.getGuestName().toLowerCase() + "%");
        }

        if (param.getStatus() != null) {                          // 예약 상태 조건
            sql.append(" AND r.status = :status");
            binds.put("status", param.getStatus());
        }

        if (param.getRoomTypeId() != null) {                      // 객실 타입 조건
            sql.append(" AND r.room_type_id = :roomTypeId");
            binds.put("roomTypeId", param.getRoomTypeId());
        }

        if (param.getCheckInDate() != null) {                     // 체크인 날짜 조건
            sql.append(" AND r.check_in_date >= :checkInDate");
            binds.put("checkInDate", param.getCheckInDate());
        }

        if (param.getCheckOutDate() != null) {                    // 체크아웃 날짜 조건
            sql.append(" AND r.check_out_date <= :checkOutDate");
            binds.put("checkOutDate", param.getCheckOutDate());
        }

        // 3. 정렬 + 페이징 (QueryDSL의 .orderBy().offset().limit() 대신)
        sql.append(" ORDER BY r.id DESC");                         // 최신순 정렬
        sql.append(" LIMIT :limit OFFSET :offset");                // 페이징
        binds.put("limit", param.getPageSize());                   // 페이지 크기
        binds.put("offset",                                        // 시작 위치 계산
            (long) param.getPageNo() * param.getPageSize());

        // 4. 쿼리 실행 (블로킹 .fetch() 대신 논블로킹 .all())
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());

        for (Map.Entry<String, Object> entry : binds.entrySet()) { // 바인드 파라미터 설정
            spec = spec.bind(entry.getKey(), entry.getValue());
        }

        // QueryDSL의 Projections.bean() 대신 row.get()으로 수동 매핑
        return spec.map((row, metadata) -> new ReservationListDto(
            row.get("id", Long.class),                             // 예약 ID
            row.get("guest_name", String.class),                   // 투숙객 이름
            row.get("check_in_date", LocalDate.class),             // 체크인 날짜
            row.get("check_out_date", LocalDate.class),            // 체크아웃 날짜
            row.get("quantity", Integer.class),                    // 예약 객실 수
            row.get("status", String.class),                       // 예약 상태
            row.get("total_price", BigDecimal.class),              // 총 금액
            row.get("channel_code", String.class),                 // 채널 코드 (JOIN)
            row.get("channel_name", String.class),                 // 채널명 (JOIN)
            row.get("room_type_name", String.class),               // 객실 타입명 (JOIN)
            row.get("base_price", BigDecimal.class),               // 기본 가격 (JOIN)
            row.get("available_quantity", Integer.class)            // 잔여 재고 (JOIN)
        )).all();                                                  // Flux<DTO> 반환 (논블로킹)
    }

    // 전체 건수 조회 (CRS의 .select(profile.count()).fetchOne() 대신)
    // 반환 타입: Long (블로킹) → Mono<Long> (논블로킹)
    public Mono<Long> countReservations(ReservationSearchParam param) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM reservations r WHERE 1=1");

        Map<String, Object> binds = new HashMap<>();

        // findReservations와 동일한 동적 조건 적용
        if (param.getChannelId() != null) {
            sql.append(" AND r.channel_id = :channelId");
            binds.put("channelId", param.getChannelId());
        }
        if (param.getStatus() != null) {
            sql.append(" AND r.status = :status");
            binds.put("status", param.getStatus());
        }
        // ... (동일한 조건들)

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : binds.entrySet()) {
            spec = spec.bind(entry.getKey(), entry.getValue());
        }

        return spec.map(row -> row.get(0, Long.class))            // COUNT 결과를 Long으로 매핑
            .one();                                                // Mono<Long> 반환
    }
}
```

#### Kotlin (R2DBC + DatabaseClient)

```kotlin
// === Channel Manager: Kotlin 버전 ===

@Repository                                                        // Spring Bean 등록
class ReservationQueryRepository(
    private val databaseClient: DatabaseClient                     // 생성자 주입 (Kotlin primary constructor)
) {

    // 예약 목록 조회 - Kotlin의 문자열 템플릿과 buildString 활용
    fun findReservations(param: ReservationSearchParam): Flux<ReservationListDto> {
        val binds = mutableMapOf<String, Any>()                    // 바인드 파라미터 (mutableMap 사용)

        // buildString으로 SQL 조합 (StringBuilder보다 간결한 Kotlin 관용구)
        val sql = buildString {
            append("""
                SELECT r.id, r.guest_name, r.check_in_date, r.check_out_date,
                       r.quantity, r.status, r.total_price,
                       c.code AS channel_code, c.name AS channel_name,
                       rt.name AS room_type_name, rt.base_price,
                       i.available_quantity
                FROM reservations r
                LEFT JOIN channels c ON c.id = r.channel_id
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                LEFT JOIN inventories i ON i.room_type_id = r.room_type_id
                    AND i.stock_date = r.check_in_date
                WHERE 1=1
            """.trimIndent())

            // 동적 조건 - Kotlin의 ?.let으로 null 안전하게 처리
            // CRS의 BooleanExpression eqProfileType()과 동일한 역할
            param.channelId?.let {                                 // channelId가 null이 아닐 때만 실행
                append(" AND r.channel_id = :channelId")
                binds["channelId"] = it                            // 바인드 파라미터 등록
            }

            param.guestName?.let {                                 // 투숙객 이름 조건
                append(" AND LOWER(r.guest_name) LIKE :guestName")
                binds["guestName"] = "%${it.lowercase()}%"         // Kotlin 문자열 템플릿 사용
            }

            param.status?.let {                                    // 예약 상태 조건
                append(" AND r.status = :status")
                binds["status"] = it
            }

            param.roomTypeId?.let {                                // 객실 타입 조건
                append(" AND r.room_type_id = :roomTypeId")
                binds["roomTypeId"] = it
            }

            param.checkInDate?.let {                               // 체크인 날짜 조건
                append(" AND r.check_in_date >= :checkInDate")
                binds["checkInDate"] = it
            }

            param.checkOutDate?.let {                              // 체크아웃 날짜 조건
                append(" AND r.check_out_date <= :checkOutDate")
                binds["checkOutDate"] = it
            }

            // 페이징
            append(" ORDER BY r.id DESC")
            append(" LIMIT :limit OFFSET :offset")
            binds["limit"] = param.pageSize                        // 페이지 크기
            binds["offset"] = param.pageNo.toLong() * param.pageSize // 시작 위치
        }

        // 쿼리 실행 - Kotlin의 fold로 바인드 파라미터를 순차 적용
        return binds.entries.fold(databaseClient.sql(sql)) { spec, (key, value) ->
            spec.bind(key, value)                                  // 각 파라미터를 바인드
        }.map { row, _ ->                                          // row 매핑 (metadata는 사용하지 않으므로 _)
            ReservationListDto(
                id = row.get("id", Long::class.java)!!,            // !! = null이 아님을 단언
                guestName = row.get("guest_name", String::class.java)!!,
                checkInDate = row.get("check_in_date", LocalDate::class.java)!!,
                checkOutDate = row.get("check_out_date", LocalDate::class.java)!!,
                quantity = row.get("quantity", Int::class.java)!!,
                status = row.get("status", String::class.java)!!,
                totalPrice = row.get("total_price", BigDecimal::class.java),
                channelCode = row.get("channel_code", String::class.java),
                channelName = row.get("channel_name", String::class.java),
                roomTypeName = row.get("room_type_name", String::class.java),
                basePrice = row.get("base_price", BigDecimal::class.java),
                availableQuantity = row.get("available_quantity", Int::class.java)
            )
        }.all()                                                    // Flux<ReservationListDto> 반환
    }

    // 전체 건수 조회
    fun countReservations(param: ReservationSearchParam): Mono<Long> {
        // findReservations와 동일한 동적 조건 로직
        // ...
        return databaseClient.sql("SELECT COUNT(*) FROM reservations r WHERE 1=1 ...")
            .map { row, _ -> row.get(0, Long::class.java)!! }     // COUNT 결과 매핑
            .one()                                                 // Mono<Long> 반환
    }
}
```

### 8-3. 패턴별 상세 비교

#### 동적 조건 처리

```
[JPA + QueryDSL]
  BooleanExpression을 반환하는 static 메서드
  → null 반환 시 where()가 자동 무시
  → 타입 세이프 (컴파일 타임 체크)

[R2DBC + DatabaseClient]
  if (param != null) 분기로 SQL 문자열에 조건 추가
  → 직접 null 체크 필요
  → SQL 문자열 기반 (런타임 체크)
```

| 항목 | QueryDSL BooleanExpression | DatabaseClient 동적 조건 |
|---|---|---|
| null 처리 | `return null` → 자동 무시 | `if (param != null)` 분기 |
| 타입 안전성 | 컴파일 타임 체크 | 런타임 체크 |
| 재사용성 | static 메서드로 분리 | 헬퍼 메서드로 분리 가능 |
| 서브쿼리 | `JPAExpressions` | SQL 서브쿼리 직접 작성 |

#### JOIN 처리

```
[JPA + QueryDSL]
  .leftJoin(profilePhoneNumber)
      .on(profilePhoneNumber.profile.eq(profile)
          .and(profilePhoneNumber.mfPrimaryYN.eq("Y")))
  → 메서드 체인으로 표현
  → Q클래스가 자동 생성

[R2DBC + DatabaseClient]
  LEFT JOIN channels c ON c.id = r.channel_id
  → SQL을 직접 작성
  → Q클래스 없음, 컬럼명 직접 지정
```

#### 반환 타입 비교

```
[JPA + QueryDSL]  (블로킹)
  데이터: List<DTO>    ← .fetch()
  건수:  Long          ← .fetchOne()
  페이징: Page<DTO>    ← new PageImpl<>(content, pageable, total)

[R2DBC + DatabaseClient]  (논블로킹)
  데이터: Flux<DTO>    ← .all()
  건수:  Mono<Long>    ← .one()
  페이징: Mono<Page<DTO>> ← Mono.zip(data.collectList(), count).map(...)
```

#### R2DBC에서 Page 객체 조합하기

```java
// CRS 방식: 블로킹으로 단순 조합
List<DTO> content = queryFactory.select(...).fetch();     // 블로킹
Long total = queryFactory.select(count()).fetchOne();      // 블로킹
return new PageImpl<>(content, pageRequest, total);        // 즉시 반환

// R2DBC 방식: Reactive로 비동기 조합
Mono<Page<ReservationListDto>> page = Mono.zip(
    findReservations(param).collectList(),    // Flux → Mono<List> 변환
    countReservations(param)                  // Mono<Long>
).map(tuple -> new PageImpl<>(               // 두 결과가 모두 도착하면 조합
    tuple.getT1(),                           // List<DTO>
    PageRequest.of(param.getPageNo(), param.getPageSize()),
    tuple.getT2()                            // total count
));
```

### 8-4. 정리: R2DBC를 선택하면 포기하는 것과 얻는 것

**포기하는 것:**
- QueryDSL의 타입 세이프 쿼리 빌더 (Q클래스 자동 생성)
- `BooleanExpression`의 null 자동 무시
- `Projections.bean()`의 자동 DTO 매핑
- Lazy Loading, 연관관계 자동 매핑

**얻는 것:**
- **논블로킹 I/O**: DB 쿼리 중 스레드를 점유하지 않음
- **높은 동시성**: 적은 스레드로 많은 요청 처리
- **Backpressure**: 소비자가 처리 속도를 제어
- **스트리밍**: Flux로 대용량 데이터를 스트림 처리 가능 (SSE 등)

**이 프로젝트의 선택:**
- 복잡한 쿼리 → `DatabaseClient` + Native SQL (실무에서 가장 많이 사용)
- 단순 CRUD → `ReactiveCrudRepository`
- 고정 조건 쿼리 → `@Query` 어노테이션

---

## 다음 단계

이 개념을 바탕으로 Phase 1에서는 다음을 구현한다:

1. **Gradle 멀티 모듈 프로젝트** 초기화
2. **도메인 엔티티** (Kotlin & Java) 구현
3. **Flyway 마이그레이션** SQL 작성
4. **R2DBC Repository** 정의
5. **애플리케이션 설정** (application.yml)
6. 동작 확인
