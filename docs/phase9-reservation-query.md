# Phase 9 — 예약 조회 API (필터링 & 페이징)

## 1. 개요

Phase 3에서 예약 생성(POST), Phase 7에서 예약 취소(DELETE)를 구현했다.
Phase 9에서는 **예약 조회 API**를 추가하여 CRUD의 R(Read)을 완성한다.

### 구현할 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/reservations/{id}` | 단건 조회 |
| GET | `/api/reservations` | 목록 조회 (필터 + 페이징) |

## 2. Optional @RequestParam — 동적 필터링

### 2.1 필수 vs 선택적 파라미터

```kotlin
// 필수 파라미터 — 값이 없으면 400 Bad Request
@RequestParam roomTypeId: Long

// 선택적 파라미터 — 값이 없으면 null (필터 무시)
@RequestParam(required = false) channelId: Long?

// 기본값이 있는 파라미터
@RequestParam(defaultValue = "0") page: Int
```

Phase 2의 InventoryController는 모든 파라미터가 필수였지만,
예약 조회에서는 **선택적 필터링**이 필요하다:
- `channelId` 없이 호출하면 모든 채널의 예약을 반환
- `status=CONFIRMED`만 지정하면 확정 예약만 반환
- 아무 필터 없이 호출하면 전체 예약을 반환

### 2.2 지원 필터

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `channelId` | Long | N | 특정 채널의 예약만 조회 |
| `status` | String | N | CONFIRMED 또는 CANCELLED |
| `startDate` | LocalDate | N | 체크인 시작일 (이후) |
| `endDate` | LocalDate | N | 체크인 종료일 (이전) |
| `page` | Int | N | 페이지 번호 (기본 0) |
| `size` | Int | N | 페이지 크기 (기본 20) |

## 3. R2DBC에서 동적 쿼리

### 3.1 @Query의 NULL 조건 패턴

R2DBC에서는 JPA의 Specification이나 QueryDSL을 사용할 수 없다.
대신 **NULL 체크 SQL 패턴**으로 선택적 필터를 구현한다:

```sql
SELECT * FROM reservations
WHERE (:channelId IS NULL OR channel_id = :channelId)
  AND (:status IS NULL OR status = :status)
  AND (:startDate IS NULL OR check_in_date >= :startDate)
  AND (:endDate IS NULL OR check_in_date <= :endDate)
ORDER BY created_at DESC
```

- `:channelId IS NULL` → 파라미터가 null이면 해당 조건을 무시
- `OR channel_id = :channelId` → 파라미터가 있으면 필터 적용
- 모든 파라미터가 null이면 전체 조회와 동일

### 3.2 애플리케이션 레벨 필터링 (대안)

@Query 없이 Flux 연산자로 필터링할 수도 있다:

```kotlin
reservationRepository.findAll()
    .filter { channelId == null || it.channelId == channelId }
    .filter { status == null || it.status == status }
    .skip(page * size)
    .take(size)
```

**비교:**
- SQL 필터링: DB에서 필터 → 네트워크 전송량 적음 (대규모 데이터에 적합)
- Flux 필터링: 전체 로드 후 메모리 필터 (소규모 데이터에 간편)

Phase 9에서는 **애플리케이션 레벨 필터링**을 사용한다:
- 학습 목적으로 Flux 연산자(filter, skip, take)를 활용
- 소규모 샘플 데이터에 적합

## 4. Reactive 페이징

### 4.1 skip + take 패턴

R2DBC에는 Spring Data JPA의 `Pageable`이 없다.
대신 Flux의 `skip()`과 `take()`로 페이징을 구현한다:

```kotlin
// page=2, size=10 → 20개 건너뛰고 10개 가져옴
flux.skip((page * size).toLong())  // offset
    .take(size.toLong())            // limit
```

### 4.2 Kotlin vs Java

```kotlin
// Kotlin — toLong() 변환 필요 (skip/take는 Long 타입)
.skip((page * size).toLong())
.take(size.toLong())
```

```java
// Java — int → long 자동 프로모션
.skip((long) page * size)
.take(size)
```

## 5. ChannelCode 풍부화 (Enrichment)

### 5.1 문제

`ReservationResponse`에는 `channelCode`가 포함되지만,
`Reservation` 엔티티에는 `channelId`만 있다.
R2DBC는 JOIN을 지원하지 않으므로 **별도 조회**가 필요하다.

### 5.2 flatMap 풍부화 패턴

```kotlin
reservationRepository.findAll()
    .flatMap { reservation ->
        channelRepository.findById(reservation.channelId)
            .map { channel ->
                ReservationResponse.from(reservation, channel.channelCode)
            }
    }
```

단건 조회에서는 1회 추가 쿼리로 충분하지만,
목록 조회에서는 N+1 문제가 발생할 수 있다.

### 5.3 N+1 최적화 — 캐시 패턴

채널 수가 적으므로(4개), 채널을 미리 Map으로 로드하여 N+1을 방지한다:

```kotlin
channelRepository.findAll()
    .collectMap({ it.id!! }, { it.channelCode })  // Map<Long, String>
    .flatMapMany { channelMap ->
        reservationFlux.map { reservation ->
            ReservationResponse.from(
                reservation,
                channelMap[reservation.channelId] ?: "UNKNOWN"
            )
        }
    }
```

## 6. 구현 계획

### 수정할 파일

| 파일 | 변경 내용 |
|------|-----------|
| ReservationService (Kotlin/Java) | getReservation(), listReservations() 추가 |
| ReservationController (Kotlin/Java) | GET 엔드포인트 2개 추가 |

### 핵심 학습 포인트

1. **Optional @RequestParam**: `required = false`로 선택적 필터링
2. **Flux filter/skip/take**: 애플리케이션 레벨 필터링 + 페이징
3. **N+1 방지**: collectMap으로 채널 정보를 미리 로드
4. **단건 조회**: findById + switchIfEmpty + flatMap 풍부화
