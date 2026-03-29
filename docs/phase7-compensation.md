# Phase 7 — 예약 취소 & 재고 복구 (보상 트랜잭션)

## 1. 보상 트랜잭션이란?

### 1.1 개요

보상 트랜잭션(Compensating Transaction)은 **이전에 완료된 작업을 되돌리는 역작업(reverse operation)**이다.
일반적인 DB 롤백과 달리, 이미 커밋된 트랜잭션의 결과를 새로운 트랜잭션으로 복구한다.

```
Phase 4 (예약 생성)                    Phase 7 (예약 취소 — 보상)
─────────────────                      ─────────────────────────
① 재고 차감 (10 → 9)                  ① 재고 복구 (9 → 10)
② 예약 저장 (CONFIRMED)               ② 예약 상태 변경 (CANCELLED)
③ 이벤트 기록 (RESERVATION_CREATED)    ③ 이벤트 기록 (RESERVATION_CANCELLED)
```

### 1.2 왜 보상 트랜잭션인가?

호텔 예약 시스템에서 예약 취소는 단순 DELETE가 아니다:
- 예약 **기록은 유지**해야 한다 (삭제하면 이력이 사라진다)
- 예약이 차감한 **재고를 복구**해야 한다 (안 하면 영원히 팔리지 않는 객실이 생긴다)
- 취소 **이벤트를 기록**해야 한다 (대시보드에 실시간 반영)

이 세 작업이 하나의 트랜잭션으로 묶여야 한다 — 재고 복구가 실패하면 예약 상태도 원복되어야 한다.

## 2. 예약 취소 흐름

### 2.1 전체 Reactive 체인

```
cancelReservation(reservationId)
│
├─ 1. reservationRepository.findById(id)
│     └─ 없으면 → NotFoundException (404)
│
├─ 2. 상태 확인: status == CONFIRMED?
│     └─ 이미 CANCELLED → BadRequestException (400)
│
├─ 3. channelRepository.findById(reservation.channelId)
│     └─ 채널 코드 조회 (DTO 응답에 필요)
│
├─ 4. increaseInventory(reservation)
│     └─ 체크인~체크아웃 기간의 각 날짜에 대해:
│           ├─ findByRoomTypeIdAndStockDateForUpdate (비관적 잠금)
│           └─ availableQuantity += roomQuantity (재고 복구)
│
├─ 5. reservationRepository.save(status = CANCELLED)
│     └─ 예약 상태를 CANCELLED로 변경
│
├─ 6. channelEventRepository.save(RESERVATION_CANCELLED)
│     └─ 취소 이벤트 기록
│     └─ doOnNext → eventPublisher.publish() (SSE 브로드캐스트)
│
└─ 7. ReservationResponse 반환 (status=CANCELLED)
```

### 2.2 createReservation과의 대칭 비교

| 단계 | 생성 (Phase 4) | 취소 (Phase 7) |
|------|----------------|----------------|
| 1 | 요청 검증 (날짜, 수량) | 예약 조회 + 상태 확인 |
| 2 | 채널 조회 + 활성 확인 | 채널 조회 (코드 가져오기) |
| 3 | 객실 타입 조회 | — (이미 reservation에 있음) |
| 4 | **재고 차감** (decrease) | **재고 복구** (increase) |
| 5 | 예약 저장 (CONFIRMED) | 예약 저장 (CANCELLED) |
| 6 | 이벤트: RESERVATION_CREATED | 이벤트: RESERVATION_CANCELLED |
| 7 | DTO 변환 | DTO 변환 |

## 3. 재고 복구 (Increase Inventory)

### 3.1 decreaseInventory와의 대칭

```kotlin
// Phase 4: 재고 차감 (생성 시)
inventory.availableQuantity - request.roomQuantity

// Phase 7: 재고 복구 (취소 시) — 정확히 반대
inventory.availableQuantity + reservation.roomQuantity
```

### 3.2 비관적 잠금 유지

```sql
SELECT * FROM inventories
WHERE room_type_id = :roomTypeId AND stock_date = :stockDate
FOR UPDATE
```

취소 시에도 **동일한 FOR UPDATE 잠금**을 사용한다:
- 동시에 같은 날짜의 재고를 복구/차감하는 요청이 충돌할 수 있다
- 잠금 없이 복구하면 Lost Update가 발생할 수 있다

### 3.3 상한 검증

```kotlin
if (inventory.availableQuantity + reservation.roomQuantity > inventory.totalQuantity) {
    // 이 상황은 정상적인 경우에 발생하지 않는다
    // 데이터 무결성이 깨진 경우 — 방어적 검증
    Mono.error(BadRequestException("재고 복구 시 총 수량을 초과할 수 없습니다."))
}
```

## 4. 예약 상태 전이

```
생성 시:  (없음) ──→ CONFIRMED
취소 시:  CONFIRMED ──→ CANCELLED
재취소:   CANCELLED ──→ ❌ BadRequestException (이미 취소됨)
```

- CONFIRMED → CANCELLED만 허용
- CANCELLED 상태의 예약을 다시 취소하려 하면 400 에러
- Phase 7에서는 취소된 예약을 다시 CONFIRMED로 되돌리는 기능은 구현하지 않는다

## 5. 이벤트 페이로드

### RESERVATION_CANCELLED 이벤트

```json
{
    "guestName": "김민준",
    "roomQuantity": 1,
    "checkIn": "2026-03-15",
    "checkOut": "2026-03-17"
}
```

Phase 6 대시보드에서 RESERVATION_CANCELLED 이벤트 타입은 이미 등록되어 있으므로,
취소 이벤트가 발생하면 자동으로 대시보드에 빨간색 뱃지로 표시된다.

## 6. API 설계

### DELETE /api/reservations/{id}

| 항목 | 값 |
|------|-----|
| Method | DELETE |
| URL | `/api/reservations/{id}` |
| Path Variable | `id` — 취소할 예약 ID |
| 성공 응답 | 200 OK + ReservationResponse (status=CANCELLED) |
| 에러: 없는 예약 | 404 Not Found |
| 에러: 이미 취소 | 400 Bad Request |

**왜 DELETE를 사용하는가?**
- REST 관점에서 예약 "리소스"를 제거하는 의미론적 행위
- 실제 DB 삭제는 아니지만, 비즈니스 관점에서 예약이 무효화된다
- 응답 본문으로 취소된 예약 정보를 반환하여 클라이언트가 확인할 수 있다

## 7. Kotlin vs Java 차이점

### 예약 상태 변경

```kotlin
// Kotlin — data class의 copy()로 불변 객체의 일부 필드만 변경
val cancelled = reservation.copy(status = ReservationStatus.CANCELLED)
reservationRepository.save(cancelled)
```

```java
// Java — Lombok @Data의 setter로 직접 필드 변경 (mutable)
reservation.setStatus(ReservationStatus.CANCELLED);
reservationRepository.save(reservation);
```

### 재고 복구

```kotlin
// Kotlin — copy()로 가용 수량 증가
inventory.copy(availableQuantity = inventory.availableQuantity + roomQuantity)
```

```java
// Java — setter로 가용 수량 증가
inventory.setAvailableQuantity(inventory.getAvailableQuantity() + roomQuantity);
```

## 8. 구현 계획

### 수정할 파일

| 파일 | 변경 내용 |
|------|-----------|
| ReservationService (Kotlin/Java) | cancelReservation() + increaseInventory() 추가 |
| ReservationController (Kotlin/Java) | DELETE /api/reservations/{id} 엔드포인트 추가 |

### 새로 작성할 파일

없음 — 기존 파일에 메서드만 추가한다.

### 핵심 학습 포인트

1. **보상 트랜잭션**: 이미 커밋된 작업을 새 트랜잭션으로 되돌리는 패턴
2. **대칭적 Reactive 체인**: decrease ↔ increase의 정확한 대칭 구조
3. **상태 머신**: CONFIRMED → CANCELLED 단방향 전이
4. **비관적 잠금 재사용**: 복구 시에도 FOR UPDATE로 동시성 보장
5. **이벤트 소싱 연계**: 취소 이벤트 → SSE → 대시보드 실시간 반영
