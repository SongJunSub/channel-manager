# Phase 24 — 이벤트 소싱 + CQRS

## 1. 이벤트 소싱이란?

### 1.1 기존 방식 vs 이벤트 소싱

```
기존 방식 (상태 저장):
  재고 = 10 → UPDATE inventories SET available = 8 WHERE id = 1
  → 이전 상태(10)는 사라짐 (덮어쓰기)

이벤트 소싱 (이벤트 저장):
  이벤트 1: 재고 생성 (totalQuantity=10)
  이벤트 2: 예약으로 2개 차감 (delta=-2)
  이벤트 3: 취소로 1개 복구 (delta=+1)
  → 현재 상태 = 이벤트를 순서대로 재생 → 10 - 2 + 1 = 9
```

- **기존 방식**: 현재 상태만 저장 → "왜 이렇게 됐는지" 추적 불가
- **이벤트 소싱**: 상태 변경의 원인(이벤트)을 모두 저장 → 전체 이력 추적 가능

### 1.2 이벤트 소싱의 장점

| 장점 | 설명 |
|------|------|
| **완전한 감사 로그** | 모든 변경의 원인, 시점, 주체를 추적 |
| **시간 여행** | 특정 시점의 상태를 이벤트 재생으로 복원 |
| **디버깅** | "왜 재고가 0인가?" → 이벤트 목록을 보면 원인 파악 |
| **이벤트 재처리** | 버그 수정 후 이벤트를 다시 재생하여 상태 복구 |

## 2. CQRS란?

### 2.1 Command Query Responsibility Segregation

```
기존 방식 (CRUD):
  Service
    ├── create()  ← 쓰기
    ├── read()    ← 읽기
    ├── update()  ← 쓰기
    └── delete()  ← 쓰기

CQRS:
  CommandService              QueryService
    ├── create()                ├── getById()
    ├── update()                ├── getByDateRange()
    └── delete()                └── getSummary()
    ↓ 이벤트 발행                  ↑ 이벤트 재생으로 현재 상태 계산
    EventStore ─────────────────┘
```

- **Command (쓰기)**: 상태를 변경하는 연산 → 이벤트를 저장
- **Query (읽기)**: 상태를 조회하는 연산 → 이벤트를 재생하여 현재 상태 계산
- 쓰기 모델과 읽기 모델을 **분리**하여 각각 최적화 가능

### 2.2 이 프로젝트에서의 CQRS

```
POST /api/inventory-events/adjust   ← Command (재고 조정 명령)
  ↓
InventoryCommandService
  ↓
이벤트 저장: inventory_events 테이블
  ↓
EventPublisher(Sinks)로 브로드캐스트

GET /api/inventory-events/{roomTypeId}?date=...  ← Query (이벤트 이력 조회)
GET /api/inventory-events/{roomTypeId}/snapshot   ← Query (현재 상태 = 이벤트 재생)
  ↓
InventoryQueryService
  ↓
이벤트 재생: 해당 날짜의 모든 이벤트를 순서대��� 적용 → 현재 수량 계산
```

## 3. 이벤트 저장소 설계

### 3.1 inventory_events 테이블

```sql
CREATE TABLE inventory_events (
    id             BIGSERIAL PRIMARY KEY,
    room_type_id   BIGINT NOT NULL,      -- 대상 객실 타입
    stock_date     DATE NOT NULL,         -- 대상 날짜
    event_type     VARCHAR(50) NOT NULL,  -- INVENTORY_INITIALIZED, ADJUSTED, RESERVED, CANCELLED
    delta          INT NOT NULL,          -- 수량 변화 (+/-) 
    reason         VARCHAR(200),          -- 변경 사유
    created_by     VARCHAR(50),           -- 변경 주체 (사용자, 시스템)
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 이벤트 타입

| 타입 | delta | 설명 |
|------|-------|------|
| `INVENTORY_INITIALIZED` | +N | 재고 최초 설정 (totalQuantity) |
| `INVENTORY_ADJUSTED` | +/-N | 관리자 수동 조정 |
| `INVENTORY_RESERVED` | -N | 예약으로 차감 |
| `INVENTORY_CANCELLED` | +N | 취소로 복구 |

### 3.3 이벤트 재생 (Event Replay)

```
roomTypeId=1, stockDate=2026-04-18 의 이벤트:

#1  INVENTORY_INITIALIZED  delta=+10   → 누적: 10
#2  INVENTORY_RESERVED     delta=-2    → 누적: 8
#3  INVENTORY_RESERVED     delta=-1    → 누적: 7
#4  INVENTORY_CANCELLED    delta=+1    → 누적: 8
#5  INVENTORY_ADJUSTED     delta=-3    → 누적: 5

현재 가용 수량 = 이벤트 delta 합계 = 10 - 2 - 1 + 1 - 3 = 5
```

## 4. 이 프로젝트의 구현

### 4.1 아키텍처

```
┌────────────────────────────��────────────────────┐
│                  Command Side                    │
│  POST /api/inventory-events/adjust               │
│  → InventoryCommandService                       │
│  → inventory_events 테이블��� INSERT               │
│  → EventPublisher(Sinks)에 이벤트 발행            │
└─────────────────────────────────────────────────┘
                        │
                   이벤트 스트림
                        │
┌─────────────────────────────────────────────────┐
│                   Query Side                     │
│  GET /api/inventory-events/{roomTypeId}          │
│  → InventoryQueryService                         │
│  → 이벤트 목록 조회 (이력)                         │
│                                                  │
│  GET /api/inventory-events/{roomTypeId}/snapshot  │
│  → 이벤트를 순서대로 재생 → 현재 수량 계산           │
└─────────────────────────────────────────────────┘
```

### 4.2 구현 파일

```
domain/
  └── InventoryEvent.kt (.java)        ← 이벤트 엔티티
repository/
  └── InventoryEventRepository.kt (.java) ← 이벤트 저장소 접근
dto/
  ├── InventoryAdjustRequest.kt (.java)  ← 재고 조정 명령 DTO
  └── InventorySnapshotResponse.kt (.java) ← 현재 상태 스냅샷 DTO
service/
  ├── InventoryCommandService.kt (.java) ← 쓰기: 이벤트 저장
  └── InventoryQueryService.kt (.java)   ← 읽기: 이벤트 재생
controller/
  └── InventoryEventController.kt (.java) ← CQRS API 엔드포인트
```
