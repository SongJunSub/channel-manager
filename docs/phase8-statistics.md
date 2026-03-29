# Phase 8 — 통계 & 리포트 (window, buffer, groupBy)

## 1. Flux 고급 연산자 개요

### 1.1 왜 Flux 연산자로 통계를 처리하는가?

일반적인 통계는 SQL의 `GROUP BY`, `COUNT`, `SUM`으로 처리한다.
하지만 Reactive 시스템에서는 **Flux 연산자로 스트림 레벨에서 집계**하는 패턴도 학습할 가치가 있다.

```
SQL 기반 통계:  DB → GROUP BY → 결과
Flux 기반 통계: DB → Flux → groupBy/reduce → 결과
```

두 방식을 모두 구현하여 비교한다:
- **SQL 집계**: `@Query`로 DB에서 바로 집계 (성능 우수, 대규모 데이터에 적합)
- **Flux 집계**: `groupBy`, `reduce`, `collectMap` 등으로 애플리케이션 레벨에서 집계 (학습 목적)

### 1.2 Phase 8에서 학습할 연산자

| 연산자 | 설명 | 사용 예 |
|--------|------|---------|
| `groupBy` | 키 함수 기준으로 스트림을 그룹별 서브스트림으로 분할 | 채널별 예약 그룹핑 |
| `reduce` | 스트림의 모든 요소를 하나로 집계 | 그룹별 카운트/합계 |
| `collectMap` | 스트림을 Map으로 수집 | 키-값 쌍으로 변환 |
| `collectList` | 스트림을 List로 수집 | 전체 결과를 리스트로 |
| `buffer` | N개씩 묶어서 List로 발행 | 배치 처리 |
| `window` | N개씩 묶어서 서브 Flux로 발행 | 시간/개수 기반 윈도우 |
| `count` | 요소 개수를 Mono\<Long\>으로 반환 | 총 건수 집계 |

## 2. groupBy — 그룹별 집계

### 2.1 기본 개념

`Flux.groupBy(keyFunction)`은 스트림을 **키 기준으로 여러 서브스트림(GroupedFlux)으로 분할**한다.

```
입력:  [예약A(BOOKING), 예약B(AGODA), 예약C(BOOKING), 예약D(DIRECT)]

groupBy(reservation -> reservation.channelId)
  ↓
그룹1 (BOOKING):  [예약A, 예약C]
그룹2 (AGODA):    [예약B]
그룹3 (DIRECT):   [예약D]
```

### 2.2 groupBy + flatMap + reduce 패턴

```kotlin
// 채널별 예약 건수 집계
reservationRepository.findByStatus(ReservationStatus.CONFIRMED)
    .groupBy { it.channelId }        // channelId 기준으로 그룹 분할
    .flatMap { group ->               // 각 그룹(GroupedFlux)에 대해
        group.count()                 // 그룹 내 요소 개수를 센다
            .map { count ->           // (channelId, count) 쌍으로 변환
                ChannelStatistics(
                    channelId = group.key()!!,
                    reservationCount = count
                )
            }
    }
```

**GroupedFlux\<K, V\>:**
- `groupBy()`의 반환 타입은 `Flux<GroupedFlux<K, V>>`이다
- `group.key()`: 이 그룹의 키 값 (예: channelId)
- GroupedFlux 자체가 Flux이므로 `count()`, `reduce()`, `collectList()` 등을 적용할 수 있다

### 2.3 Kotlin vs Java

```kotlin
// Kotlin — it 키워드로 암묵적 람다 파라미터
.groupBy { it.channelId }
.flatMap { group ->
    group.count().map { count -> Pair(group.key()!!, count) }
}
```

```java
// Java — 명시적 람다 파라미터
.groupBy(reservation -> reservation.getChannelId())
.flatMap(group ->
    group.count().map(count -> new ChannelStats(group.key(), count))
)
```

## 3. reduce — 누적 집계

### 3.1 기본 개념

`reduce(initial, accumulator)`는 스트림의 모든 요소를 **하나의 값으로 누적**한다.

```
입력:  [700000, 960000, 350000]

reduce(BigDecimal.ZERO, BigDecimal::add)
  ↓
결과: 2010000 (총 매출)
```

### 3.2 groupBy + reduce 조합

```kotlin
// 채널별 매출 합계
reservationRepository.findByStatus(ReservationStatus.CONFIRMED)
    .groupBy { it.channelId }
    .flatMap { group ->
        group
            .map { it.totalPrice ?: BigDecimal.ZERO }   // 금액 추출
            .reduce(BigDecimal.ZERO, BigDecimal::add)    // 합산
            .map { totalRevenue ->
                ChannelRevenue(channelId = group.key()!!, revenue = totalRevenue)
            }
    }
```

## 4. buffer — 배치 묶음

### 4.1 기본 개념

`buffer(N)`은 스트림의 요소를 **N개씩 묶어 List로 발행**한다.

```
입력:  [1, 2, 3, 4, 5, 6, 7]

buffer(3)
  ↓
출력: [1,2,3], [4,5,6], [7]
```

### 4.2 활용 예: 이벤트 배치 처리

```kotlin
// 이벤트를 10개씩 묶어서 처리
channelEventRepository.findAllByOrderByCreatedAtDesc()
    .buffer(10)                          // 10개씩 List로 묶음
    .map { batch ->                      // 각 배치에 대해
        EventBatchSummary(
            batchSize = batch.size,
            eventTypes = batch.map { it.eventType }.distinct()
        )
    }
```

## 5. window — 서브스트림 분할

### 5.1 buffer vs window 차이

| 특성 | buffer | window |
|------|--------|--------|
| 반환 타입 | `Flux<List<T>>` | `Flux<Flux<T>>` |
| 메모리 | List에 모두 적재 | 서브 Flux로 지연 처리 |
| 적합한 경우 | 작은 배치 | 대용량 스트림 |

```kotlin
// window: 서브 Flux로 분할 → flatMap으로 각 윈도우 처리
eventFlux
    .window(10)                          // 10개씩 서브 Flux로 분할
    .flatMap { windowFlux ->             // 각 윈도우에 대해
        windowFlux.count()               // 윈도우 내 요소 수 (항상 10 또는 나머지)
    }
```

## 6. collectMap — Map으로 수집

### 6.1 기본 개념

`collectMap(keyFunction, valueFunction)`은 스트림을 **Map\<K, V\>로 수집**한다.

```kotlin
// 이벤트 타입별 건수를 Map으로 수집
// 결과: {RESERVATION_CREATED=5, INVENTORY_UPDATED=3, CHANNEL_SYNCED=8}
channelEventRepository.findAll()
    .groupBy { it.eventType }
    .flatMap { group ->
        group.count().map { count -> group.key() to count }
    }
    .collectMap({ it.first }, { it.second })  // Mono<Map<EventType, Long>>
```

## 7. API 설계

### 7.1 통계 엔드포인트

| 메서드 | 경로 | 설명 | 핵심 연산자 |
|--------|------|------|-------------|
| GET | `/api/statistics/channels` | 채널별 예약/매출 통계 | groupBy + reduce |
| GET | `/api/statistics/events` | 이벤트 타입별 발생 건수 | groupBy + count |
| GET | `/api/statistics/rooms` | 객실 타입별 예약 통계 | groupBy + reduce |
| GET | `/api/statistics/summary` | 전체 요약 통계 | count + reduce |

### 7.2 응답 DTO 설계

```kotlin
// 채널별 통계
data class ChannelStatistics(
    val channelId: Long,        // 채널 ID
    val channelCode: String,    // 채널 코드 (BOOKING, AGODA 등)
    val channelName: String,    // 채널 이름
    val reservationCount: Long, // 예약 건수
    val cancelledCount: Long,   // 취소 건수
    val totalRevenue: BigDecimal // 총 매출
)

// 이벤트 타입별 통계
data class EventStatistics(
    val eventType: EventType,   // 이벤트 타입
    val count: Long             // 발생 건수
)

// 객실 타입별 통계
data class RoomTypeStatistics(
    val roomTypeId: Long,       // 객실 타입 ID
    val roomTypeName: String,   // 객실 타입 이름
    val reservationCount: Long, // 예약 건수
    val totalRevenue: BigDecimal // 총 매출
)

// 전체 요약
data class SummaryStatistics(
    val totalReservations: Long,     // 전체 예약 수
    val confirmedCount: Long,        // 확정 예약 수
    val cancelledCount: Long,        // 취소 예약 수
    val totalRevenue: BigDecimal,    // 총 매출 (확정 예약 기준)
    val totalEvents: Long,           // 전체 이벤트 수
    val activeChannels: Long         // 활성 채널 수
)
```

## 8. 구현 계획

### 새로 작성할 파일

| 파일 | 역할 |
|------|------|
| StatisticsService (Kotlin/Java) | Flux groupBy/reduce 기반 통계 집계 |
| StatisticsController (Kotlin/Java) | 4개 통계 REST 엔드포인트 |
| 통계 DTO (Kotlin/Java) | ChannelStatistics, EventStatistics, RoomTypeStatistics, SummaryStatistics |
| 통합 테스트 (Kotlin/Java) | 각 통계 API 검증 |

### 핵심 학습 포인트

1. **groupBy**: 스트림을 키 기준으로 분할하여 그룹별 서브스트림 생성
2. **reduce**: 서브스트림의 요소를 하나의 값으로 누적 집계
3. **collectMap**: 스트림을 Map으로 수집 (키-값 쌍)
4. **buffer vs window**: List 수집 vs 서브 Flux 분할의 차이
5. **GroupedFlux**: groupBy의 결과 타입 — key()로 그룹 키에 접근
6. **flatMap + count/reduce**: 그룹별 집계의 핵심 조합 패턴
