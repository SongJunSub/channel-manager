# Phase 4: 스트림 통합 & 재고 동기화 — Sinks, Flux.merge, 동시성 제어

## 목차
1. [Phase 4의 목표](#1-phase-4의-목표)
2. [Sinks — 프로그래밍 방식의 이벤트 발행](#2-sinks--프로그래밍-방식의-이벤트-발행)
3. [Flux.merge — 여러 스트림 통합](#3-fluxmerge--여러-스트림-통합)
4. [동시성 제어 — Reactive에서의 경쟁 조건 해결](#4-동시성-제어--reactive에서의-경쟁-조건-해결)
5. [Channel Manager에 적용 — 아키텍처 설계](#5-channel-manager에-적용--아키텍처-설계)
6. [이벤트 발행 서비스 설계](#6-이벤트-발행-서비스-설계)
7. [재고 동기화 서비스 설계](#7-재고-동기화-서비스-설계)
8. [Backpressure 전략](#8-backpressure-전략)
9. [테스트 전략](#9-테스트-전략)
10. [Kotlin vs Java 비교 — Sinks & 동시성 코드 스타일](#10-kotlin-vs-java-비교--sinks--동시성-코드-스타일)

---

## 1. Phase 4의 목표

### Phase 3까지의 데이터 흐름 (현재)

Phase 3에서는 예약이 생성될 때 이벤트를 **DB에만 저장**한다:

```
[시뮬레이터] → [예약 API] → [ReservationService]
                                 ├─ 재고 차감 (DB)
                                 ├─ 예약 저장 (DB)
                                 └─ 이벤트 저장 (DB) ← 여기서 끝!
                                                        아무도 이 이벤트를 구독하지 않는다
```

**문제점:**
- 이벤트가 DB에 쌓이기만 하고, **실시간으로 반응하는 구독자가 없다**
- 다른 채널에 재고 변경을 **즉시 알려줄 수 없다**
- 실시간 대시보드에 **이벤트를 밀어넣을 수 없다**

### Phase 4의 데이터 흐름 (목표)

```
[시뮬레이터] → [예약 API] → [ReservationService]
                                 ├─ 재고 차감 (DB)
                                 ├─ 예약 저장 (DB)
                                 ├─ 이벤트 저장 (DB)
                                 └─ Sinks에 이벤트 발행 ← ★ 새로 추가!
                                        │
                                        ▼
                                 [EventStream (Flux)]
                                        │
                              ┌─────────┼─────────┐
                              ▼         ▼         ▼
                        [재고 동기화] [로그 출력] [SSE 대시보드]
                                                  (Phase 5)
```

**Sinks**가 이벤트를 **메모리 내 스트림**으로 발행하면,
여러 구독자가 **실시간으로 반응**할 수 있다.

---

## 2. Sinks — 프로그래밍 방식의 이벤트 발행

### 2-1. Sinks란?

`Sinks`는 Reactor 3.4+에서 도입된 **프로그래밍 방식의 이벤트 발행기**다.
기존 `Flux.create()` / `FluxProcessor`를 대체한다.

**핵심 개념:**
- **Sinks = 이벤트를 밀어넣는(push) 쪽**
- **Flux = 이벤트를 꺼내는(pull/subscribe) 쪽**
- Sinks에 데이터를 넣으면 → 구독 중인 모든 Flux에 전달된다

```
[생산자]                              [소비자]
   │                                     │
   ├─ sinks.tryEmitNext(event1) ──→  ┌── subscriber1 (재고 동기화)
   ├─ sinks.tryEmitNext(event2) ──→  ├── subscriber2 (로그 출력)
   └─ sinks.tryEmitNext(event3) ──→  └── subscriber3 (SSE 대시보드)
```

### 2-2. Sinks의 종류

Sinks는 **발행하는 아이템 수**에 따라 3가지 종류가 있다:

| 종류 | 설명 | 사용 시점 |
|---|---|---|
| `Sinks.one()` | 단일 값 발행 후 완료 | 1회성 결과 전달 |
| `Sinks.empty()` | 값 없이 완료/에러만 | 시그널 전달 |
| `Sinks.many()` | 여러 값을 계속 발행 | **이벤트 스트림 (우리가 사용할 것)** |

### 2-3. Sinks.many()의 3가지 모드

```java
// 1. multicast — 구독자 간 이벤트 공유 (Hot Stream)
//    구독 이후 발행된 이벤트만 수신
//    구독 전 발행된 이벤트는 유실된다
Sinks.Many<Event> sink = Sinks.many().multicast().onBackpressureBuffer();

// 2. unicast — 단 하나의 구독자만 허용
//    두 번째 구독 시 에러 발생
Sinks.Many<Event> sink = Sinks.many().unicast().onBackpressureBuffer();

// 3. replay — 과거 이벤트를 재전송 (Hot Stream with Cache)
//    새 구독자에게도 이전 이벤트를 전달
Sinks.Many<Event> sink = Sinks.many().replay().limit(100); // 최근 100개 캐시
Sinks.Many<Event> sink = Sinks.many().replay().all();      // 전체 캐시
```

**Channel Manager에서의 선택: `multicast().onBackpressureBuffer()`**

이유:
- 여러 구독자(재고 동기화, 로그, SSE)가 **동시에 같은 이벤트를 수신**해야 한다 → multicast
- 구독 이전 이벤트는 DB에 저장되어 있으므로 **캐싱이 불필요**하다 → replay 불필요
- 이벤트 처리 속도가 느린 구독자를 위해 **버퍼**를 제공한다 → onBackpressureBuffer

### 2-4. 이벤트 발행 방법

```java
Sinks.Many<ChannelEvent> sinks = Sinks.many().multicast().onBackpressureBuffer();

// tryEmitNext — 실패 시 EmitResult 반환 (비동기 안전)
Sinks.EmitResult result = sinks.tryEmitNext(event);
if (result.isFailure()) {
    log.warn("이벤트 발행 실패: {}", result);
}

// emitNext — 실패 시 재시도 핸들러 사용
sinks.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
```

**`tryEmitNext` vs `emitNext`:**

| 메서드 | 실패 시 동작 | 스레드 안전 | 사용 시점 |
|---|---|---|---|
| `tryEmitNext()` | EmitResult 반환 | O (lock-free) | **권장 — 비동기 환경** |
| `emitNext()` | 핸들러에 위임 | O (내부 spin loop) | 재시도 로직 필요 시 |

### 2-5. 이벤트 구독 방법

```java
// Sinks → Flux 변환
Flux<ChannelEvent> eventStream = sinks.asFlux();

// 구독자 1: 로그 출력
eventStream.subscribe(event -> log.info("이벤트 수신: {}", event));

// 구독자 2: 재고 동기화
eventStream
    .filter(event -> event.getEventType() == EventType.RESERVATION_CREATED)
    .subscribe(event -> syncInventory(event));

// 구독자 3: SSE (Phase 5)
// Controller에서 Flux를 그대로 반환하면 SSE 스트림이 된다
```

### 2-6. Hot vs Cold Stream

이 개념은 Sinks를 이해하는 데 핵심적이다:

```
Cold Stream (Flux.just, Flux.fromIterable)
─────────────────────────────────────────
- 구독할 때마다 처음부터 데이터를 발행한다
- 구독자마다 독립적인 데이터 시퀀스를 받는다
- 비유: 녹화 영상 (VOD) — 각자 처음부터 본다

Hot Stream (Sinks, Flux.interval)
─────────────────────────────────
- 구독 여부와 관계없이 데이터가 발행된다
- 모든 구독자가 같은 데이터를 공유한다
- 구독 이전에 발행된 데이터는 받을 수 없다 (multicast 모드)
- 비유: 생방송 (라이브) — 지금부터 보기 시작
```

```java
// Cold Stream 예시 — 구독마다 1,2,3을 새로 발행
Flux<Integer> cold = Flux.just(1, 2, 3);
cold.subscribe(v -> log.info("A: {}", v)); // A: 1, A: 2, A: 3
cold.subscribe(v -> log.info("B: {}", v)); // B: 1, B: 2, B: 3

// Hot Stream 예시 — Sinks로 발행한 이벤트를 모든 구독자가 동시에 수신
Sinks.Many<Integer> hot = Sinks.many().multicast().onBackpressureBuffer();
Flux<Integer> stream = hot.asFlux();
stream.subscribe(v -> log.info("A: {}", v)); // A가 먼저 구독
hot.tryEmitNext(1); // A: 1
stream.subscribe(v -> log.info("B: {}", v)); // B가 나중에 구독
hot.tryEmitNext(2); // A: 2, B: 2  (둘 다 수신)
hot.tryEmitNext(3); // A: 3, B: 3  (둘 다 수신)
// B는 1을 받지 못했다 — 구독 이전에 발행되었기 때문
```

---

## 3. Flux.merge — 여러 스트림 통합

### 3-1. Flux.merge란?

`Flux.merge`는 **여러 Flux를 하나의 Flux로 합쳐서**, 어느 소스에서든 이벤트가 오면 즉시 전달한다.

```
Flux A:  ──1──2──────3──────→
Flux B:  ─────────4──────5──→
Flux C:  ──────6───────7────→
                  │
            Flux.merge(A, B, C)
                  │
                  ▼
Result:  ──1──2──6──4──3──7──5──→
         (이벤트 도착 순서대로 합쳐짐)
```

### 3-2. merge vs concat vs zip 비교

```java
// merge — 동시에 구독, 도착 순서대로 합침 (인터리빙)
Flux.merge(fluxA, fluxB, fluxC)
// 결과: A1, B1, A2, C1, B2, ... (도착 순)

// concat — 순차적으로 구독, 첫 번째 완료 후 두 번째 시작
Flux.concat(fluxA, fluxB, fluxC)
// 결과: A1, A2, A3, B1, B2, B3, C1, C2, C3 (순서 보장)

// zip — 각 소스에서 하나씩 묶어서 발행 (가장 느린 소스 속도)
Flux.zip(fluxA, fluxB)
// 결과: (A1,B1), (A2,B2), (A3,B3) (쌍으로 묶임)
```

| 연산자 | 구독 방식 | 순서 | 완료 조건 | 사용 시점 |
|---|---|---|---|---|
| `merge` | **동시** 구독 | 도착 순 | 모두 완료 | **실시간 이벤트 통합** |
| `concat` | 순차 구독 | 소스 순서 | 모두 완료 | 순서가 중요한 배치 |
| `zip` | 동시 구독 | 쌍으로 묶음 | 하나라도 완료 | 두 결과를 조합할 때 |

### 3-3. Channel Manager에서의 활용

```java
// 여러 채널의 이벤트 스트림을 하나로 합친다
Flux<ChannelEvent> bookingEvents = sinks.asFlux()
    .filter(e -> e.getChannelCode().equals("BOOKING"));

Flux<ChannelEvent> agodaEvents = sinks.asFlux()
    .filter(e -> e.getChannelCode().equals("AGODA"));

Flux<ChannelEvent> directEvents = sinks.asFlux()
    .filter(e -> e.getChannelCode().equals("DIRECT"));

// merge로 통합 — 어느 채널이든 이벤트가 오면 즉시 처리
Flux<ChannelEvent> allEvents = Flux.merge(
    bookingEvents, agodaEvents, directEvents
);

allEvents
    .filter(e -> e.getEventType() == EventType.RESERVATION_CREATED)
    .subscribe(e -> syncInventoryToOtherChannels(e));
```

### 3-4. mergeWith — 인스턴스 메서드 버전

```java
// 정적 메서드 (Flux.merge)
Flux<Event> merged = Flux.merge(streamA, streamB, streamC);

// 인스턴스 메서드 (mergeWith)
Flux<Event> merged = streamA.mergeWith(streamB).mergeWith(streamC);

// 두 가지는 동일한 결과를 반환한다
// 2~3개: mergeWith가 가독성 좋음
// 4개 이상: Flux.merge가 가독성 좋음
```

---

## 4. 동시성 제어 — Reactive에서의 경쟁 조건 해결

### 4-1. 왜 동시성이 문제가 되는가?

채널 시뮬레이터가 3초마다 예약을 생성하면, 동시에 같은 객실/날짜의 재고를 차감할 수 있다:

```
[예약 A] roomType=1, date=3/20 → 재고 읽기: 10 → 계산: 10-1=9 → 저장: 9
[예약 B] roomType=1, date=3/20 → 재고 읽기: 10 → 계산: 10-1=9 → 저장: 9
                                                                       ↑
                                                              둘 다 9를 저장!
                                                              실제로는 8이어야 함
```

이것이 **Lost Update (갱신 분실)** 문제다.
두 트랜잭션이 같은 데이터를 동시에 읽고, 각자 수정하면 하나의 수정이 사라진다.

### 4-2. 해결 방법 1: 비관적 잠금 (Pessimistic Lock)

```sql
-- SELECT ... FOR UPDATE로 행을 잠근다
-- 다른 트랜잭션은 이 행을 읽지 못하고 대기한다
SELECT * FROM inventories
WHERE room_type_id = 1 AND stock_date = '2026-03-20'
FOR UPDATE;
```

```
[예약 A] SELECT FOR UPDATE → 잠금 획득 → 10-1=9 저장 → 잠금 해제
[예약 B] SELECT FOR UPDATE → 대기... → 잠금 획득 → 9-1=8 저장 → 잠금 해제
```

**장점:** 확실한 정합성 보장
**단점:** R2DBC에서는 `SELECT FOR UPDATE`를 직접 지원하지 않는다 → `@Query` 필요

### 4-3. 해결 방법 2: 낙관적 잠금 (Optimistic Lock)

```java
// @Version 필드를 사용한다
@Table("inventories")
public class Inventory {
    @Id Long id;
    @Version Long version;  // ← 버전 필드 추가
    int availableQuantity;
}
```

```sql
-- 수정할 때 version도 함께 확인한다
UPDATE inventories
SET available_quantity = 9, version = 2
WHERE id = 1 AND version = 1;
-- version이 일치하지 않으면 0 rows affected → 충돌 감지!
```

```
[예약 A] 읽기 (version=1) → 9 저장 (version=2) → 성공
[예약 B] 읽기 (version=1) → 9 저장 (version=2) → 실패! (이미 version=2)
         → 재시도 → 읽기 (version=2, quantity=9) → 8 저장 (version=3) → 성공
```

**장점:** 잠금을 잡지 않으므로 성능이 좋다 (충돌이 드물 때)
**단점:** 충돌 시 재시도 로직이 필요하다

### 4-4. 해결 방법 3: 원자적 업데이트 (Atomic Update)

```sql
-- 읽지 않고 바로 업데이트 (DB 레벨에서 원자적)
UPDATE inventories
SET available_quantity = available_quantity - 1
WHERE room_type_id = 1
  AND stock_date = '2026-03-20'
  AND available_quantity >= 1;  -- 조건부 차감
```

```
[예약 A] UPDATE ... SET qty = qty - 1 WHERE qty >= 1 → 10→9, 1 row affected
[예약 B] UPDATE ... SET qty = qty - 1 WHERE qty >= 1 → 9→8, 1 row affected
```

**장점:** 가장 간결하고, DB가 동시성을 보장한다
**단점:** 복잡한 비즈니스 로직 (검증, 조건부 처리)을 SQL에 넣기 어렵다

### 4-5. Channel Manager에서의 선택

현재 Phase 3에서 `concatMap`을 사용하여 **순차 처리**하고 있지만,
이것만으로는 **서로 다른 요청** 간의 동시성을 해결하지 못한다.

Phase 4에서는 **DB 레벨 비관적 잠금 (`SELECT FOR UPDATE`)**을 도입한다:

```java
// InventoryRepository에 커스텀 쿼리 추가
@Query("SELECT * FROM inventories " +
       "WHERE room_type_id = :roomTypeId AND stock_date = :stockDate " +
       "FOR UPDATE")
Mono<Inventory> findByRoomTypeIdAndStockDateForUpdate(
    Long roomTypeId, LocalDate stockDate
);
```

**이유:**
1. `@Transactional` 범위 내에서 잠금이 자동 해제된다
2. 재고 차감은 **짧은 트랜잭션**이므로 잠금 대기가 길지 않다
3. 재시도 로직이 필요 없어 코드가 간결하다

---

## 5. Channel Manager에 적용 — 아키텍처 설계

### 5-1. Phase 4 전체 아키텍처

```
                    ┌──────────────────────────────────┐
                    │       EventPublisher (Sinks)      │
                    │  Sinks.many().multicast()          │
                    └──────────┬───────────────────────┘
                               │ tryEmitNext(event)
                    ┌──────────┴───────────────────────┐
                    │                                    │
          ┌─────────▼──────────┐              ┌─────────▼──────────┐
          │  ReservationService │              │  InventoryService   │
          │  (예약 생성 시 발행)  │              │  (재고 변경 시 발행)  │
          └────────────────────┘              └────────────────────┘
                               │
                    ┌──────────▼───────────────────────┐
                    │       EventStream (Flux)           │
                    │  sinks.asFlux()                    │
                    └──────────┬───────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌────────────────┐ ┌──────────┐  ┌───────────────┐
     │ InventorySyncSvc│ │ 로그 출력 │  │ SSE (Phase 5)  │
     │ (재고 동기화)    │ │          │  │                │
     └────────────────┘ └──────────┘  └───────────────┘
```

### 5-2. 새로 추가할 클래스

| 클래스 | 역할 |
|---|---|
| `EventPublisher` | Sinks를 감싸고, 이벤트 발행 + Flux 제공 |
| `InventorySyncService` | 이벤트 스트림을 구독하여 재고 동기화 수행 |

### 5-3. 기존 클래스 수정

| 클래스 | 변경 내용 |
|---|---|
| `ReservationService` | EventPublisher를 주입받아 이벤트 발행 추가 |
| `InventoryService` | EventPublisher를 주입받아 재고 변경 이벤트 발행 추가 |
| `InventoryRepository` | `FOR UPDATE` 쿼리 메서드 추가 |

---

## 6. 이벤트 발행 서비스 설계

### 6-1. EventPublisher 클래스

```java
@Service
public class EventPublisher {

    // Sinks.many().multicast() — 여러 구독자가 동시에 이벤트를 수신
    // onBackpressureBuffer() — 구독자 처리 속도가 느리면 버퍼에 저장
    private final Sinks.Many<ChannelEvent> sinks =
        Sinks.many().multicast().onBackpressureBuffer();

    // 이벤트 발행 — ReservationService, InventoryService에서 호출
    public void publish(ChannelEvent event) {
        Sinks.EmitResult result = sinks.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("이벤트 발행 실패: {}", result);
        }
    }

    // 이벤트 스트림 — 구독자가 Flux로 수신
    // 여러 번 호출해도 같은 Sinks에서 나온 이벤트를 공유한다
    public Flux<ChannelEvent> getEventStream() {
        return sinks.asFlux();
    }
}
```

### 6-2. ReservationService 수정

```java
// 기존 코드 (Phase 3)
channelEventRepository.save(channelEvent)
    .thenReturn(reservation)

// 변경 코드 (Phase 4)
channelEventRepository.save(channelEvent)
    .doOnNext(savedEvent -> eventPublisher.publish(savedEvent))  // ★ 추가
    .thenReturn(reservation)
```

**`doOnNext`를 사용하는 이유:**
- `doOnNext`는 스트림의 흐름을 변경하지 않는 **부수 효과(side-effect)** 연산자다
- DB 저장 후 Sinks에 발행하는 것은 부수 효과에 해당한다
- 발행 실패가 전체 트랜잭션을 롤백시키지 않도록 fire-and-forget으로 처리한다

---

## 7. 재고 동기화 서비스 설계

### 7-1. 왜 재고 동기화가 필요한가?

실제 호텔 채널 매니저에서는 한 채널에서 예약이 발생하면,
**다른 모든 채널에 변경된 재고를 알려야** 한다:

```
[Booking.com에서 예약]
  → 재고 10 → 9로 차감
  → Agoda에 알림: "3/20 재고가 9로 변경됨"
  → Trip.com에 알림: "3/20 재고가 9로 변경됨"
  → 자사 홈페이지 업데이트: "3/20 재고 9실"
```

이 프로젝트에서는 실제 OTA API를 호출하지 않으므로,
**CHANNEL_SYNCED 이벤트를 발행**하여 동기화를 시뮬레이션한다.

### 7-2. InventorySyncService 설계

```java
@Service
public class InventorySyncService {

    // @PostConstruct 또는 ApplicationReadyEvent로 이벤트 스트림 구독 시작
    // RESERVATION_CREATED 이벤트가 발생하면:
    // 1. 해당 채널 이외의 활성 채널 목록을 조회한다
    // 2. 각 채널에 대해 CHANNEL_SYNCED 이벤트를 생성한다
    // 3. 이벤트를 DB에 저장하고 Sinks에 발행한다
    public void startSync() {
        eventPublisher.getEventStream()
            .filter(event ->
                event.getEventType() == EventType.RESERVATION_CREATED
            )
            .flatMap(event -> syncToOtherChannels(event))
            .subscribe();
    }
}
```

### 7-3. Flux.merge를 활용한 다중 이벤트 소스 통합

향후 여러 종류의 이벤트 소스가 생길 수 있다:

```java
// 예약 이벤트 스트림
Flux<ChannelEvent> reservationEvents = eventPublisher.getEventStream()
    .filter(e -> e.getEventType() == EventType.RESERVATION_CREATED);

// 재고 변경 이벤트 스트림
Flux<ChannelEvent> inventoryEvents = eventPublisher.getEventStream()
    .filter(e -> e.getEventType() == EventType.INVENTORY_UPDATED);

// 취소 이벤트 스트림 (Phase 7)
Flux<ChannelEvent> cancelEvents = eventPublisher.getEventStream()
    .filter(e -> e.getEventType() == EventType.RESERVATION_CANCELLED);

// 모든 재고에 영향을 주는 이벤트를 통합
Flux<ChannelEvent> inventoryAffectingEvents = Flux.merge(
    reservationEvents, cancelEvents, inventoryEvents
);

// 통합 스트림을 구독하여 재고 동기화
inventoryAffectingEvents.subscribe(event -> syncInventory(event));
```

---

## 8. Backpressure 전략

### 8-1. Backpressure란?

**Backpressure (배압)**는 생산자(publisher)가 소비자(subscriber)보다 **빠르게 이벤트를 발행**할 때
발생하는 문제를 해결하는 메커니즘이다.

```
[생산자] 초당 1000개 발행
        │
        ▼
[소비자] 초당 100개 처리 가능
        → 900개/초 처리 못함!
        → 메모리 고갈 위험!
```

### 8-2. Reactor의 Backpressure 전략

```java
// 1. onBackpressureBuffer (기본) — 버퍼에 저장
//    메모리 여유가 있을 때 사용
sinks.many().multicast().onBackpressureBuffer();

// 2. onBackpressureBuffer(maxSize) — 제한된 버퍼
//    버퍼 초과 시 에러 발생
sinks.many().multicast().onBackpressureBuffer(256);

// 3. onBackpressureDrop — 처리 못하면 버림
//    실시간성이 중요하고 데이터 유실 허용 시
flux.onBackpressureDrop(dropped ->
    log.warn("이벤트 버림: {}", dropped)
);

// 4. onBackpressureLatest — 최신 값만 유지
//    중간 값은 무시하고 가장 최근 값만 전달
flux.onBackpressureLatest();
```

### 8-3. Channel Manager에서의 전략

```java
// EventPublisher — 무한 버퍼 (이벤트 유실 방지)
Sinks.many().multicast().onBackpressureBuffer();
// 이유: 채널 동기화 이벤트는 유실되면 안 된다
// 호텔 예약 시스템에서 재고 불일치는 오버부킹으로 이어진다

// SSE 구독자 (Phase 5) — 최신 값만 (대시보드는 최신 상태만 필요)
eventStream.onBackpressureLatest();
```

---

## 9. 테스트 전략

### 9-1. EventPublisher 테스트

```java
@Test
void 이벤트를_발행하면_구독자가_수신한다() {
    EventPublisher publisher = new EventPublisher();
    ChannelEvent event = new ChannelEvent(/* ... */);

    // 구독 먼저 시작
    StepVerifier.create(publisher.getEventStream().take(1))
        .then(() -> publisher.publish(event))  // 구독 후 발행
        .expectNextMatches(e -> e.getEventType() == EventType.RESERVATION_CREATED)
        .verifyComplete();
}
```

### 9-2. 동시성 테스트

```java
@Test
void 동시에_같은_재고를_차감해도_정합성이_유지된다() {
    // 10개의 예약을 동시에 생성
    Flux.range(1, 10)
        .flatMap(i -> reservationService.createReservation(request))
        .collectList()
        .block();

    // 재고 확인: 초기 15 - 10 = 5
    Inventory inventory = inventoryRepository
        .findByRoomTypeIdAndStockDate(1L, testDate)
        .block();
    assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
}
```

### 9-3. 재고 동기화 테스트

```java
@Test
void 예약_생성_시_다른_채널에_동기화_이벤트가_발생한다() {
    // BOOKING 채널로 예약 생성
    reservationService.createReservation(bookingRequest).block();

    // CHANNEL_SYNCED 이벤트 확인
    StepVerifier.create(
        channelEventRepository.findByEventType(EventType.CHANNEL_SYNCED)
    )
        .expectNextCount(2)  // DIRECT, AGODA에 각각 동기화
        .thenConsumeWhile(x -> true)
        .verifyComplete();
}
```

---

## 10. Kotlin vs Java 비교 — Sinks & 동시성 코드 스타일

### 10-1. EventPublisher

**Kotlin:**
```kotlin
@Service
class EventPublisher {
    // Kotlin에서는 타입 추론으로 더 간결하다
    private val sinks = Sinks.many().multicast().onBackpressureBuffer<ChannelEvent>()

    fun publish(event: ChannelEvent) {
        val result = sinks.tryEmitNext(event)
        if (result.isFailure) {  // Kotlin: .isFailure 프로퍼티 접근
            log.warn("이벤트 발행 실패: {}", result)
        }
    }

    // Flux<ChannelEvent>를 반환 — 구독자가 이벤트 수신
    fun getEventStream(): Flux<ChannelEvent> = sinks.asFlux()
}
```

**Java:**
```java
@Service
public class EventPublisher {
    // Java에서는 제네릭을 명시해야 한다
    private final Sinks.Many<ChannelEvent> sinks =
        Sinks.many().multicast().onBackpressureBuffer();

    public void publish(ChannelEvent event) {
        Sinks.EmitResult result = sinks.tryEmitNext(event);
        if (result.isFailure()) {  // Java: .isFailure() 메서드 호출
            log.warn("이벤트 발행 실패: {}", result);
        }
    }

    public Flux<ChannelEvent> getEventStream() {
        return sinks.asFlux();
    }
}
```

### 10-2. 이벤트 구독 시작

**Kotlin:**
```kotlin
@Service
class InventorySyncService(
    private val eventPublisher: EventPublisher,  // primary constructor 주입
    private val channelRepository: ChannelRepository
) {
    // @PostConstruct로 빈 초기화 시 구독 시작
    @PostConstruct
    fun startSync() {
        eventPublisher.getEventStream()
            .filter { it.eventType == EventType.RESERVATION_CREATED }  // it 키워드
            .flatMap { event -> syncToOtherChannels(event) }
            .subscribe()
    }
}
```

**Java:**
```java
@Service
@RequiredArgsConstructor
public class InventorySyncService {
    private final EventPublisher eventPublisher;  // Lombok 생성자 주입
    private final ChannelRepository channelRepository;

    @PostConstruct
    public void startSync() {
        eventPublisher.getEventStream()
            .filter(event -> event.getEventType() == EventType.RESERVATION_CREATED)
            .flatMap(event -> syncToOtherChannels(event))
            .subscribe();
    }
}
```

### 10-3. FOR UPDATE 쿼리

**Kotlin:**
```kotlin
interface InventoryRepository : ReactiveCrudRepository<Inventory, Long> {
    // 기존 메서드 유지
    fun findByRoomTypeIdAndStockDate(roomTypeId: Long, stockDate: LocalDate): Mono<Inventory>

    // 비관적 잠금 쿼리 추가
    @Query("SELECT * FROM inventories " +
           "WHERE room_type_id = :roomTypeId AND stock_date = :stockDate " +
           "FOR UPDATE")
    fun findByRoomTypeIdAndStockDateForUpdate(
        roomTypeId: Long, stockDate: LocalDate
    ): Mono<Inventory>
}
```

**Java:**
```java
public interface InventoryRepository extends ReactiveCrudRepository<Inventory, Long> {
    // 기존 메서드 유지
    Mono<Inventory> findByRoomTypeIdAndStockDate(Long roomTypeId, LocalDate stockDate);

    // 비관적 잠금 쿼리 추가
    @Query("SELECT * FROM inventories " +
           "WHERE room_type_id = :roomTypeId AND stock_date = :stockDate " +
           "FOR UPDATE")
    Mono<Inventory> findByRoomTypeIdAndStockDateForUpdate(
        Long roomTypeId, LocalDate stockDate
    );
}
```

### 10-4. doOnNext — 부수 효과

**Kotlin:**
```kotlin
// doOnNext 내부에서 it 키워드로 간결하게 표현
channelEventRepository.save(channelEvent)
    .doOnNext { eventPublisher.publish(it) }  // it = savedEvent
    .thenReturn(reservation)
```

**Java:**
```java
// Java에서는 명시적 파라미터 이름 필요
channelEventRepository.save(channelEvent)
    .doOnNext(savedEvent -> eventPublisher.publish(savedEvent))
    // 또는 메서드 참조: .doOnNext(eventPublisher::publish)
    .thenReturn(reservation);
```

---

## 요약 — Phase 4 핵심 개념 체크리스트

| 개념 | 설명 | 사용처 |
|---|---|---|
| Sinks.many().multicast() | 여러 구독자에게 이벤트를 브로드캐스트 | EventPublisher |
| tryEmitNext() | 스레드 안전한 이벤트 발행 | ReservationService → Sinks |
| asFlux() | Sinks를 구독 가능한 Flux로 변환 | InventorySyncService, SSE |
| Flux.merge() | 여러 이벤트 소스를 하나로 통합 | 다중 채널 이벤트 통합 |
| Hot vs Cold Stream | Sinks는 Hot Stream (구독 전 이벤트 유실) | 실시간 이벤트 시스템 |
| doOnNext() | 스트림 흐름을 변경하지 않는 부수 효과 | DB 저장 후 Sinks 발행 |
| SELECT FOR UPDATE | 비관적 잠금으로 동시성 문제 해결 | 재고 차감 시 경쟁 조건 방지 |
| Backpressure | 생산 속도 > 소비 속도 문제 해결 | onBackpressureBuffer |
| @PostConstruct | 빈 초기화 시 이벤트 구독 시작 | InventorySyncService |
