# Phase 3: 채널 시뮬레이터 — Flux.interval & WebClient

## 목차
1. [채널 시뮬레이터란?](#1-채널-시뮬레이터란)
2. [Flux.interval — 주기적 이벤트 생성](#2-fluxinterval--주기적-이벤트-생성)
3. [WebClient — 논블로킹 HTTP 클라이언트](#3-webclient--논블로킹-http-클라이언트)
4. [채널 시뮬레이터 아키텍처](#4-채널-시뮬레이터-아키텍처)
5. [예약 요청 시뮬레이션 설계](#5-예약-요청-시뮬레이션-설계)
6. [랜덤 데이터 생성 전략](#6-랜덤-데이터-생성-전략)
7. [시뮬레이터 제어 — 시작/중지 API](#7-시뮬레이터-제어--시작중지-api)
8. [Disposable과 구독 생명주기 관리](#8-disposable과-구독-생명주기-관리)
9. [예약 처리 서비스 설계](#9-예약-처리-서비스-설계)
10. [테스트 전략](#10-테스트-전략)
11. [Kotlin vs Java 비교 — 시뮬레이터 코드 스타일](#11-kotlin-vs-java-비교--시뮬레이터-코드-스타일)

---

## 1. 채널 시뮬레이터란?

### 실제 호텔 채널 매니저의 동작

실제 호텔 운영에서는 여러 OTA(Online Travel Agency)를 통해 객실을 판매한다:

```
[Booking.com]  ──예약 요청──→  [Channel Manager]  ←──예약 요청──  [Agoda]
[Trip.com]     ──예약 요청──→  [Channel Manager]  ←──예약 요청──  [자사 홈페이지]
```

각 OTA에서 예약이 들어오면:
1. Channel Manager가 예약을 접수한다
2. 해당 날짜의 재고(Inventory)를 차감한다
3. 다른 모든 채널에 변경된 재고를 동기화한다

### 이 프로젝트에서의 시뮬레이터

실제 OTA API를 연동하지 않고, **가상의 예약 요청을 주기적으로 생성**하여 OTA의 동작을 시뮬레이션한다.

```
[채널 시뮬레이터]
    │
    ├─ 3초마다: Booking.com에서 예약 요청 생성
    ├─ 5초마다: Agoda에서 예약 요청 생성
    └─ 4초마다: 자사 홈페이지에서 예약 요청 생성
    │
    ↓ (WebClient로 내부 API 호출)
    │
[예약 처리 API]
    │
    ├─ 예약(Reservation) 생성
    ├─ 재고(Inventory) 차감
    └─ 이벤트(ChannelEvent) 기록
```

**핵심 기술:**
- `Flux.interval`: 주기적으로 이벤트를 발행하여 OTA 예약을 시뮬레이션
- `WebClient`: 논블로킹 HTTP 클라이언트로 내부 예약 API를 호출

---

## 2. Flux.interval — 주기적 이벤트 생성

### 2-1. 기본 사용법

`Flux.interval`은 지정한 간격으로 0, 1, 2, 3, ... 숫자를 무한히 발행한다.

```java
// 1초마다 0, 1, 2, 3, ... 발행
Flux.interval(Duration.ofSeconds(1))
    .subscribe(count -> System.out.println("Count: " + count));
```

```
0초: Count: 0
1초: Count: 1
2초: Count: 2
3초: Count: 3
...  (무한 반복)
```

### 2-2. 시작 지연(initialDelay) 설정

```java
// 5초 후 시작, 이후 3초마다 발행
Flux.interval(Duration.ofSeconds(5), Duration.ofSeconds(3))
    .subscribe(count -> System.out.println("Count: " + count));
```

```
5초: Count: 0  (5초 대기 후 첫 발행)
8초: Count: 1  (이후 3초 간격)
11초: Count: 2
...
```

### 2-3. 무한 스트림 제한

`Flux.interval`은 무한 스트림이므로, 필요에 따라 제한할 수 있다.

```java
// take: 처음 10개만
Flux.interval(Duration.ofSeconds(1))
    .take(10)  // 10개 발행 후 완료

// takeUntil: 조건 충족 시 중지
Flux.interval(Duration.ofSeconds(1))
    .takeUntil(count -> count >= 100)  // 100 이상이면 중지

// takeWhile: 조건이 참인 동안만
Flux.interval(Duration.ofSeconds(1))
    .takeWhile(count -> isRunning)  // isRunning이 false면 중지
```

### 2-4. Flux.interval의 스레드 모델

**중요:** `Flux.interval`은 별도 스레드(Schedulers.parallel())에서 동작한다.

```java
Flux.interval(Duration.ofSeconds(1))
    .doOnNext(count -> System.out.println(Thread.currentThread().getName()))
    .subscribe();
// 출력: parallel-1, parallel-1, parallel-1, ...
```

이는 Netty 이벤트 루프 스레드를 점유하지 않으므로 WebFlux 환경에서 안전하다.

### 2-5. 채널 시뮬레이터에서의 활용

```java
// Booking.com 시뮬레이터: 3초마다 예약 요청
Flux.interval(Duration.ofSeconds(3))
    .flatMap(tick -> createRandomReservation("BOOKING"))
    .subscribe();

// Agoda 시뮬레이터: 5초마다 예약 요청
Flux.interval(Duration.ofSeconds(5))
    .flatMap(tick -> createRandomReservation("AGODA"))
    .subscribe();
```

---

## 3. WebClient — 논블로킹 HTTP 클라이언트

### 3-1. WebClient란?

WebClient는 Spring WebFlux의 **논블로킹 HTTP 클라이언트**다. Spring MVC의 `RestTemplate` 대체제이다.

| | RestTemplate | WebClient |
|---|---|---|
| **블로킹** | O (스레드 점유) | X (논블로킹) |
| **반환 타입** | `T` (직접 값) | `Mono<T>` / `Flux<T>` |
| **Spring 5+** | 유지보수 모드 | 권장 |
| **WebFlux 환경** | 사용 불가 (블로킹) | 사용 가능 |

### 3-2. WebClient 생성

```java
// 방법 1: 기본 URL 지정
WebClient webClient = WebClient.builder()
    .baseUrl("http://localhost:8080")
    .build();

// 방법 2: Spring Bean으로 등록
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("http://localhost:8080")
            .build();
    }
}
```

### 3-3. GET 요청

```java
// 단건 조회 — Mono<T>
Mono<InventoryResponse> inventory = webClient.get()
    .uri("/api/inventories/{id}", 1L)   // URL 경로 변수
    .retrieve()                          // 응답 추출 시작
    .bodyToMono(InventoryResponse.class); // 본문을 Mono로 변환

// 목록 조회 — Flux<T>
Flux<InventoryResponse> inventories = webClient.get()
    .uri(uriBuilder -> uriBuilder
        .path("/api/inventories")
        .queryParam("roomTypeId", 1L)
        .queryParam("startDate", "2026-03-15")
        .queryParam("endDate", "2026-03-19")
        .build())
    .retrieve()
    .bodyToFlux(InventoryResponse.class); // 본문을 Flux로 변환
```

### 3-4. POST 요청

```java
// 예약 생성 요청
Mono<ReservationResponse> result = webClient.post()
    .uri("/api/reservations")
    .contentType(MediaType.APPLICATION_JSON) // Content-Type 설정
    .bodyValue(reservationRequest)           // 요청 본문
    .retrieve()
    .bodyToMono(ReservationResponse.class);
```

### 3-5. 에러 처리

```java
webClient.post()
    .uri("/api/reservations")
    .bodyValue(request)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, response ->
        response.bodyToMono(String.class)
            .flatMap(body -> Mono.error(new RuntimeException("클라이언트 에러: " + body)))
    )
    .onStatus(HttpStatusCode::is5xxServerError, response ->
        Mono.error(new RuntimeException("서버 에러"))
    )
    .bodyToMono(ReservationResponse.class);
```

### 3-6. retrieve() vs exchange()

| | retrieve() | exchangeToMono() |
|---|---|---|
| **간결함** | 간결 (권장) | 복잡 |
| **에러 처리** | 자동 (4xx/5xx → 예외) | 수동 |
| **리소스 관리** | 자동 | 수동 (body를 반드시 소비해야 함) |
| **사용 시점** | 일반적인 경우 | 응답 헤더/상태 코드 직접 제어 필요 시 |

### 3-7. 채널 시뮬레이터에서의 활용

시뮬레이터는 WebClient로 **자기 자신의 예약 API**를 호출한다:

```java
// 시뮬레이터가 내부 예약 API를 호출하는 흐름
Flux.interval(Duration.ofSeconds(3))
    .flatMap(tick -> {
        ReservationCreateRequest request = generateRandomRequest("BOOKING");
        return webClient.post()
            .uri("/api/reservations")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ReservationResponse.class)
            .doOnNext(res -> log.info("예약 생성: {}", res))
            .onErrorResume(e -> {
                log.warn("예약 실패: {}", e.getMessage());
                return Mono.empty();  // 실패해도 시뮬레이터 중단 안 함
            });
    })
    .subscribe();
```

---

## 4. 채널 시뮬레이터 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│  ChannelSimulator (서비스)                                    │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ Booking.com     │  │ Agoda           │  │ DIRECT       │ │
│  │ Flux.interval   │  │ Flux.interval   │  │ Flux.interval│ │
│  │ (3초)           │  │ (5초)           │  │ (4초)        │ │
│  └────────┬────────┘  └────────┬────────┘  └──────┬───────┘ │
│           │                     │                   │         │
│           └─────────────────────┼───────────────────┘         │
│                                 ↓                             │
│                        [랜덤 예약 생성]                        │
│                                 │                             │
│                                 ↓                             │
│                     WebClient.post()                          │
│                  /api/reservations                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  ReservationService                                         │
│                                                              │
│  1. 채널 존재 & 활성 확인                                     │
│  2. 객실 타입 존재 확인                                       │
│  3. 재고 확인 & 차감 (availableQuantity - roomQuantity)       │
│  4. 예약(Reservation) 저장                                   │
│  5. 이벤트(ChannelEvent) 기록                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 컴포넌트 목록

| 컴포넌트 | 역할 | 파일 |
|----------|------|------|
| `ReservationCreateRequest` | 예약 생성 요청 DTO | dto/ |
| `ReservationResponse` | 예약 응답 DTO | dto/ |
| `ReservationService` | 예약 생성 + 재고 차감 + 이벤트 기록 | service/ |
| `ReservationController` | 예약 API 엔드포인트 | controller/ |
| `ChannelSimulator` | 주기적 예약 요청 생성 (Flux.interval + WebClient) | simulator/ |
| `SimulatorController` | 시뮬레이터 시작/중지 API | controller/ |

---

## 5. 예약 요청 시뮬레이션 설계

### 예약 생성 API

시뮬레이터가 호출할 예약 API를 먼저 설계한다.

```
POST /api/reservations
```

**요청:**
```json
{
    "channelCode": "BOOKING",
    "roomTypeId": 2,
    "checkInDate": "2026-03-20",
    "checkOutDate": "2026-03-22",
    "guestName": "James Wilson",
    "roomQuantity": 1
}
```

**응답 (201 Created):**
```json
{
    "id": 10,
    "channelId": 2,
    "channelCode": "BOOKING",
    "roomTypeId": 2,
    "checkInDate": "2026-03-20",
    "checkOutDate": "2026-03-22",
    "guestName": "James Wilson",
    "roomQuantity": 1,
    "status": "CONFIRMED",
    "totalPrice": 960000,
    "createdAt": "2026-03-16T14:30:00"
}
```

### 예약 처리 흐름

```
1. channelCode로 Channel 조회 → isActive 확인
2. roomTypeId로 RoomType 조회 → basePrice 가져옴
3. checkInDate ~ checkOutDate 범위의 Inventory 조회
4. 각 날짜의 availableQuantity >= roomQuantity 확인
5. 각 날짜의 재고 차감 (availableQuantity -= roomQuantity)
6. Reservation 저장 (status=CONFIRMED, totalPrice=basePrice×숙박일수×객실수)
7. ChannelEvent 기록 (RESERVATION_CREATED)
```

---

## 6. 랜덤 데이터 생성 전략

시뮬레이터는 현실적인 예약을 생성해야 한다.

### 랜덤 요소

| 항목 | 범위 | 설명 |
|------|------|------|
| 채널 | BOOKING, AGODA, DIRECT | 활성 채널 중 선택 (TRIP 제외) |
| 객실 타입 | ID 1~5 | DB에 등록된 객실 타입 중 랜덤 |
| 체크인 | 오늘 ~ 30일 후 | 가까운 미래 날짜 |
| 숙박 일수 | 1~3박 | 일반적인 숙박 기간 |
| 투숙객 이름 | 리스트에서 랜덤 | 다국적 이름 |
| 객실 수 | 1~2실 | 일반적인 예약 수량 |

### 투숙객 이름 풀

```java
List<String> GUEST_NAMES = List.of(
    "김민준", "이서윤", "박지호", "최수아", "정도현",
    "James Wilson", "Emily Johnson", "Michael Brown",
    "田中太郎", "佐藤花子", "鈴木一郎"
);
```

---

## 7. 시뮬레이터 제어 — 시작/중지 API

시뮬레이터는 서버 기동 시 자동 시작하지 않고, **API로 제어**한다.

### API 설계

| HTTP | URL | 설명 |
|------|-----|------|
| POST | `/api/simulator/start` | 시뮬레이터 시작 |
| POST | `/api/simulator/stop` | 시뮬레이터 중지 |
| GET | `/api/simulator/status` | 시뮬레이터 실행 상태 조회 |

### 상태 관리

```java
@Service
public class ChannelSimulator {
    private Disposable simulatorDisposable;  // 구독 핸들
    private boolean running = false;         // 실행 상태

    public void start() {
        if (running) return;  // 이미 실행 중이면 무시
        running = true;

        simulatorDisposable = Flux.interval(Duration.ofSeconds(3))
            .flatMap(tick -> createRandomReservation())
            .subscribe();
    }

    public void stop() {
        if (!running) return;
        running = false;
        simulatorDisposable.dispose();  // 구독 취소 → 스트림 중지
    }
}
```

---

## 8. Disposable과 구독 생명주기 관리

### 8-1. Disposable이란?

`subscribe()`를 호출하면 `Disposable` 객체가 반환된다. 이 객체로 **구독을 취소**(스트림 중지)할 수 있다.

```java
Disposable disposable = Flux.interval(Duration.ofSeconds(1))
    .subscribe(count -> System.out.println(count));

// ... 10초 후
disposable.dispose();  // 구독 취소 → 더 이상 이벤트 발행 안 함
```

### 8-2. 왜 직접 subscribe()를 호출하는가?

Phase 2에서 "비즈니스 로직에서 subscribe()를 직접 호출하면 안 된다"고 했다. 하지만 시뮬레이터는 예외적인 경우다:

| 상황 | subscribe() 사용 | 이유 |
|------|------------------|------|
| Controller 반환 | X (프레임워크가 함) | HTTP 요청-응답 흐름 |
| Service 내부 | X (체인으로 연결) | 데이터 흐름 유지 |
| **백그라운드 작업** | **O (직접 호출)** | **HTTP 요청과 무관한 독립 실행** |

시뮬레이터는 HTTP 요청이 없어도 **백그라운드에서 독립적으로 실행**되므로, 직접 subscribe()를 호출해야 한다.

### 8-3. Disposable 안전 패턴

```java
private Disposable disposable;

public void start() {
    // 기존 구독이 있으면 먼저 정리
    if (disposable != null && !disposable.isDisposed()) {
        disposable.dispose();
    }

    disposable = Flux.interval(Duration.ofSeconds(3))
        .flatMap(this::processReservation)
        .doOnError(e -> log.error("시뮬레이터 에러", e))
        .onErrorResume(e -> Mono.empty())  // 에러 발생해도 스트림 유지
        .subscribe();
}

public void stop() {
    if (disposable != null && !disposable.isDisposed()) {
        disposable.dispose();
    }
}
```

### 8-4. onErrorResume의 중요성

Reactive 스트림에서 에러가 발생하면 **스트림이 종료**된다. 시뮬레이터는 에러가 나도 계속 동작해야 하므로, **각 예약 시도에서 에러를 개별적으로 처리**해야 한다.

```java
// ❌ 잘못된 패턴: 하나의 예약 실패 → 전체 시뮬레이터 중단
Flux.interval(Duration.ofSeconds(3))
    .flatMap(tick -> createReservation())  // 이 안에서 에러 → 전체 스트림 종료!
    .subscribe();

// ✅ 올바른 패턴: 각 예약 시도에서 개별 에러 처리
Flux.interval(Duration.ofSeconds(3))
    .flatMap(tick -> createReservation()
        .onErrorResume(e -> {             // 이 예약만 실패, 시뮬레이터는 계속
            log.warn("예약 실패: {}", e.getMessage());
            return Mono.empty();
        })
    )
    .subscribe();
```

---

## 9. 예약 처리 서비스 설계

### ReservationService — 핵심 비즈니스 로직

예약 처리는 여러 테이블에 걸친 트랜잭션이 필요하다:

```java
@Transactional
public Mono<ReservationResponse> createReservation(ReservationCreateRequest request) {
    return channelRepository.findByChannelCode(request.channelCode())    // 1. 채널 조회
        .switchIfEmpty(Mono.error(new NotFoundException("채널 없음")))
        .filter(Channel::getIsActive)                                     // 2. 활성 확인
        .switchIfEmpty(Mono.error(new BadRequestException("비활성 채널")))
        .flatMap(channel ->
            roomTypeRepository.findById(request.roomTypeId())            // 3. 객실 타입 조회
                .switchIfEmpty(Mono.error(new NotFoundException("객실 타입 없음")))
                .flatMap(roomType ->
                    decreaseInventory(request)                           // 4. 재고 차감
                        .then(saveReservation(channel, roomType, request))// 5. 예약 저장
                        .flatMap(reservation ->
                            saveEvent(channel, reservation)              // 6. 이벤트 기록
                                .thenReturn(reservation)
                        )
                )
        )
        .map(ReservationResponse::from);                                 // 7. DTO 변환
}
```

### 재고 차감 로직

체크인 ~ 체크아웃 기간의 모든 날짜에서 재고를 차감한다:

```java
private Flux<Inventory> decreaseInventory(ReservationCreateRequest request) {
    // checkInDate ~ checkOutDate-1 범위의 날짜를 생성
    // (체크아웃 당일은 숙박하지 않으므로 제외)
    return Flux.fromStream(
            request.checkInDate().datesUntil(request.checkOutDate())
        )
        .concatMap(date ->
            inventoryRepository.findByRoomTypeIdAndStockDate(
                request.roomTypeId(), date
            )
            .switchIfEmpty(Mono.error(new NotFoundException(
                date + " 재고 없음")))
            .flatMap(inventory -> {
                if (inventory.getAvailableQuantity() < request.roomQuantity()) {
                    return Mono.error(new BadRequestException(
                        date + " 재고 부족"));
                }
                inventory.setAvailableQuantity(
                    inventory.getAvailableQuantity() - request.roomQuantity()
                );
                return inventoryRepository.save(inventory);
            })
        );
}
```

---

## 10. 테스트 전략

### 시뮬레이터 테스트

시뮬레이터는 시간 기반(Flux.interval)이므로 테스트가 어렵다. 두 가지 접근법을 사용한다:

#### 접근법 1: 예약 API 직접 테스트 (WebTestClient)

시뮬레이터가 호출하는 예약 API를 직접 테스트한다.

```java
@Test
void 예약_생성_성공() {
    var request = new ReservationCreateRequest(
        "BOOKING", 1L,
        LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 17),
        "James Wilson", 1
    );

    webTestClient.post()
        .uri("/api/reservations")
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody(ReservationResponse.class)
        .consumeWith(result -> {
            var response = result.getResponseBody();
            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.totalPrice()).isPositive();
        });
}
```

#### 접근법 2: 시뮬레이터 시작/중지 API 테스트

```java
@Test
void 시뮬레이터_시작_후_상태_확인() {
    // 시작
    webTestClient.post()
        .uri("/api/simulator/start")
        .exchange()
        .expectStatus().isOk();

    // 상태 확인
    webTestClient.get()
        .uri("/api/simulator/status")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.running").isEqualTo(true);

    // 중지
    webTestClient.post()
        .uri("/api/simulator/stop")
        .exchange()
        .expectStatus().isOk();
}
```

---

## 11. Kotlin vs Java 비교 — 시뮬레이터 코드 스타일

### WebClient 요청

```kotlin
// Kotlin
webClient.post()
    .uri("/api/reservations")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(ReservationResponse::class.java)
    .doOnNext { log.info("예약 생성: ${it.id}") }     // 문자열 템플릿
    .onErrorResume { e ->                              // 람다 파라미터 명시
        log.warn("예약 실패: ${e.message}")
        Mono.empty()                                    // return 키워드 불필요
    }
```

```java
// Java
webClient.post()
    .uri("/api/reservations")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(ReservationResponse.class)
    .doOnNext(res -> log.info("예약 생성: {}", res.id()))  // SLF4J 플레이스홀더
    .onErrorResume(e -> {                                  // 화살표 람다
        log.warn("예약 실패: {}", e.getMessage());
        return Mono.empty();                               // return 필수
    });
```

### 시뮬레이터 서비스

```kotlin
// Kotlin
@Service
class ChannelSimulator(
    private val webClient: WebClient                   // 생성자 주입 (자동)
) {
    private var disposable: Disposable? = null          // nullable 타입

    fun start() {
        if (disposable?.isDisposed == false) return     // safe call
        disposable = Flux.interval(Duration.ofSeconds(3))
            .flatMap { createRandomReservation() }      // it 사용 안 함
            .subscribe()
    }

    fun stop() {
        disposable?.dispose()                           // safe call로 null 안전
    }
}
```

```java
// Java
@Service
@RequiredArgsConstructor
public class ChannelSimulator {

    private final WebClient webClient;                  // Lombok 생성자 주입
    private Disposable disposable;                      // null 가능 (참조 타입)

    public void start() {
        if (disposable != null && !disposable.isDisposed()) return;  // null 체크
        disposable = Flux.interval(Duration.ofSeconds(3))
            .flatMap(tick -> createRandomReservation())  // tick 파라미터 명시
            .subscribe();
    }

    public void stop() {
        if (disposable != null && !disposable.isDisposed()) {  // null 체크 필수
            disposable.dispose();
        }
    }
}
```

**주요 차이:**
- Kotlin: `?.` (safe call)로 null 체크 간결, `it` 암시적 파라미터, `var`로 가변 선언
- Java: 명시적 null 체크, 모든 람다 파라미터 명시, Lombok으로 생성자 주입

### 랜덤 데이터 생성

```kotlin
// Kotlin
private fun generateGuestName(): String =
    GUEST_NAMES.random()                                // 컬렉션 확장 함수

private fun generateCheckInDate(): LocalDate =
    LocalDate.now().plusDays((1..30).random().toLong())  // 범위 표현식
```

```java
// Java
private String generateGuestName() {
    return GUEST_NAMES.get(
        ThreadLocalRandom.current().nextInt(GUEST_NAMES.size())  // ThreadLocalRandom
    );
}

private LocalDate generateCheckInDate() {
    int daysAhead = ThreadLocalRandom.current().nextInt(1, 31);  // 1~30
    return LocalDate.now().plusDays(daysAhead);
}
```

---

## 핵심 요약

| 개념 | 설명 | Phase 3 활용 |
|------|------|-------------|
| `Flux.interval` | 주기적 이벤트 발행 | OTA 예약 시뮬레이션 |
| `WebClient` | 논블로킹 HTTP 클라이언트 | 내부 예약 API 호출 |
| `Disposable` | 구독 핸들 (취소 가능) | 시뮬레이터 시작/중지 |
| `onErrorResume` | 에러 복구 | 예약 실패 시 시뮬레이터 유지 |
| `@Transactional` | 트랜잭션 | 예약+재고차감 원자성 |

### 구현 순서

1. DTO 정의 — `ReservationCreateRequest`, `ReservationResponse`
2. `ReservationService` — 예약 생성 + 재고 차감 + 이벤트 기록
3. `ReservationController` — 예약 API (`POST /api/reservations`)
4. `ChannelSimulator` — Flux.interval + WebClient + 랜덤 데이터 생성
5. `SimulatorController` — 시뮬레이터 제어 API (시작/중지/상태)
6. 테스트 — 예약 API + 시뮬레이터 제어 테스트
