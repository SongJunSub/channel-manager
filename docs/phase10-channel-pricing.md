# Phase 10 — 채널별 가격 차등 (Channel Markup Rate)

## 1. 개요

현실의 호텔 채널 매니저에서는 **판매 채널에 따라 가격이 달라진다.**
OTA(Online Travel Agency)는 수수료를 부과하므로, 호텔은 채널별로 다른 판매가를 설정한다.

```
기본 가격 (Superior Double): 350,000원/박

채널별 판매가:
  자사 홈페이지 (DIRECT):  350,000원 (×1.00 — 수수료 없음)
  Booking.com (BOOKING):   402,500원 (×1.15 — 15% 마크업)
  Agoda (AGODA):           385,000원 (×1.10 — 10% 마크업)
  Trip.com (TRIP):         332,500원 (×0.95 — 5% 할인, 프로모션)
```

### markup_rate (마크업 비율)

| 값 | 의미 | 예시 |
|----|------|------|
| 1.000 | 기본 가격 그대로 (100%) | 자사 홈페이지 |
| 1.150 | 15% 인상 | Booking.com (수수료 반영) |
| 1.100 | 10% 인상 | Agoda |
| 0.950 | 5% 할인 | Trip.com (프로모션) |

## 2. 가격 계산 공식 변경

### Before (Phase 3)

```
totalPrice = basePrice × nights × roomQuantity
```

### After (Phase 10)

```
totalPrice = basePrice × nights × roomQuantity × markupRate
```

### 코드 변경

```kotlin
// Phase 3: 채널 무관 균일 가격
val totalPrice = roomType.basePrice
    .multiply(BigDecimal.valueOf(nights))
    .multiply(BigDecimal.valueOf(request.roomQuantity.toLong()))

// Phase 10: 채널별 마크업 적용
val totalPrice = roomType.basePrice
    .multiply(BigDecimal.valueOf(nights))
    .multiply(BigDecimal.valueOf(request.roomQuantity.toLong()))
    .multiply(channel.markupRate)  // 채널 마크업 비율 적용
```

## 3. DB 스키마 변경

### V8 마이그레이션

```sql
ALTER TABLE channels ADD COLUMN markup_rate DECIMAL(5, 3) NOT NULL DEFAULT 1.000;
```

- `DECIMAL(5, 3)`: 최대 99.999 (사실상 0.500 ~ 2.000 범위에서 사용)
- `DEFAULT 1.000`: 마크업 미설정 시 기본 가격 그대로

### 샘플 데이터 업데이트

```sql
UPDATE channels SET markup_rate = 1.000 WHERE channel_code = 'DIRECT';
UPDATE channels SET markup_rate = 1.150 WHERE channel_code = 'BOOKING';
UPDATE channels SET markup_rate = 1.100 WHERE channel_code = 'AGODA';
UPDATE channels SET markup_rate = 0.950 WHERE channel_code = 'TRIP';
```

## 4. 엔티티 변경

### Channel 엔티티에 markupRate 추가

```kotlin
// Kotlin
data class Channel(
    // ... 기존 필드
    val markupRate: BigDecimal = BigDecimal.ONE  // 기본값 1.0
)
```

```java
// Java
@Builder.Default
private BigDecimal markupRate = BigDecimal.ONE;  // 기본값 1.0
```

## 5. 핵심 학습 포인트

1. **DECIMAL 타입**: 금액/비율 처리에 float/double 대신 BigDecimal 사용
2. **Flyway 마이그레이션**: ALTER TABLE로 기존 테이블에 컬럼 추가
3. **기본값 전략**: DEFAULT 1.000으로 기존 데이터 호환성 유지
4. **비즈니스 로직 변경**: 가격 계산 공식에 채널 변수 추가
