# Phase 14 — 동시성 테스트 (부하 테스트)

## 1. 왜 동시성 테스트가 필요한가?

호텔 예약 시스템에서 **동시에 같은 객실을 예약**하는 경우가 빈번하다.
여러 OTA 채널에서 동시에 예약 요청이 들어올 때, 재고가 정확하게 차감되어야 한다.

### Lost Update 문제

```
시간   스레드A (BOOKING)              스레드B (AGODA)
─────────────────────────────────────────────────────
T1     재고 조회: available=10
T2                                    재고 조회: available=10
T3     재고 차감: 10-1=9 저장
T4                                    재고 차감: 10-1=9 저장  ← Lost Update!
─────────────────────────────────────────────────────
결과: 2건 예약했지만 재고는 1만 차감됨 (9 vs 기대값 8)
```

Phase 4에서 `SELECT ... FOR UPDATE` 비관적 잠금으로 이 문제를 해결했다.
Phase 14에서는 이 잠금이 실제로 동작하는지 **동시 요청으로 검증**한다.

## 2. 테스트 전략

### 2.1 동시 예약 테스트

```
10개의 동시 예약 요청 → 같은 날짜/객실 타입
  ↓
FOR UPDATE 잠금에 의해 순차 처리
  ↓
검증: 성공 건수 + 실패 건수 = 10
      재고 차감량 = 성공 건수
      최종 재고 = 초기 재고 - 성공 건수
```

### 2.2 Reactor의 Mono.zip 병렬 실행

```kotlin
// 10개의 Mono를 동시에 실행
val requests = (1..10).map { i ->
    webClient.post()
        .uri("/api/reservations")
        .bodyValue(request.copy(guestName = "Guest$i"))
        .retrieve()
        .toEntity(ReservationResponse::class.java)
        .onErrorResume { Mono.empty() }  // 실패해도 계속 진행
}

// Flux.merge로 동시 실행 → 결과 수집
Flux.merge(requests)
    .collectList()
    .block()
```

### 2.3 CountDownLatch 패턴

```kotlin
val latch = CountDownLatch(concurrency)
val results = ConcurrentLinkedQueue<Result>()

repeat(concurrency) { i ->
    thread {
        val result = webClient.post().uri("/api/reservations").exchange()
        results.add(result)
        latch.countDown()
    }
}

latch.await(30, TimeUnit.SECONDS)
// 결과 검증
```

## 3. 핵심 검증 포인트

| 검증 항목 | 기대 결과 |
|-----------|-----------|
| 동시 10건 예약 요청 | 재고가 충분하면 10건 모두 성공 |
| 재고 정합성 | 최종 재고 = 초기 - 성공 건수 (정확히 일치) |
| 재고 부족 시 | 일부 성공 + 일부 400 Bad Request |
| 동시 취소 | 같은 예약 동시 취소 시 1건만 성공 |

## 4. 핵심 학습 포인트

1. **Flux.merge**: 여러 Mono를 병렬로 실행하여 동시 요청 시뮬레이션
2. **FOR UPDATE 검증**: 비관적 잠금이 실제로 동시성 문제를 방지하는지 확인
3. **재고 정합성**: 동시 요청 후 DB 상태가 정확한지 검증
4. **에러 내성**: 일부 요청이 실패해도 전체 테스트가 중단되지 않도록 처리
