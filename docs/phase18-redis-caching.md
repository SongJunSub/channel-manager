# Phase 18 — Redis 캐싱

## 1. 캐싱이란?

### 1.1 왜 캐싱이 필요한가?

```
캐시 없이:
클라이언트 → API → DB 쿼리 (매번 6개 쿼리 실행) → 응답
                    ↓
              평균 200ms (DB I/O)

캐시 사용:
클라이언트 → API → Redis 캐시 조회 (1회) → 응답
                    ↓
              평균 1~2ms (메모리)
```

- **DB 부하 감소**: 동일한 통계 쿼리가 매번 DB에 접근하는 것을 방지
- **응답 속도 향상**: 메모리 기반 조회는 디스크 기반 DB보다 100배 이상 빠름
- **비용 절감**: DB 커넥션, CPU 사용량 감소

### 1.2 캐싱 전략

| 전략 | 설명 | 적합한 경우 |
|------|------|------------|
| **Cache-Aside** | 앱이 캐시를 직접 관리 (조회→없으면→DB→캐시 저장) | 가장 일반적 |
| Read-Through | 캐시 라이브러리가 DB 조회를 대행 | 투명한 캐싱 원할 때 |
| Write-Through | 쓰기 시 캐시와 DB 동시 업데이트 | 읽기 일관성 중요 |
| Write-Behind | 쓰기를 캐시에만 하고 비동기로 DB 반영 | 쓰기 성능 중요 |

이 프로젝트에서는 **Cache-Aside** 전략을 사용한다:

```
조회 요청 도착
  ↓
캐시에 데이터가 있는가? ──Yes──→ 캐시 데이터 반환 (Cache Hit)
  │ No (Cache Miss)
  ↓
DB에서 조회
  ↓
결과를 캐시에 저장 (TTL 설정)
  ↓
결과 반환
```

## 2. Redis란?

### 2.1 개요

- **Re**mote **Di**ctionary **S**erver — 원격 딕셔너리 서버
- **인메모리** 키-값 저장소 (모든 데이터가 RAM에 저장)
- 단일 스레드 이벤트 루프 — 명령어 순차 처리 (원자성 보장)
- 주요 자료구조: String, Hash, List, Set, Sorted Set

### 2.2 왜 Redis인가?

| 비교 | 로컬 캐시 (ConcurrentHashMap) | Redis |
|------|------------------------------|-------|
| 속도 | 가장 빠름 (JVM 내부) | 매우 빠름 (네트워크 1~2ms) |
| 공유 | 단일 인스턴스 내 | 여러 인스턴스 간 공유 |
| TTL | 직접 구현 필요 | 내장 지원 (EXPIRE) |
| 영속성 | JVM 종료 시 소멸 | RDB/AOF 백업 가능 |
| 확장성 | 불가 | 클러스터 지원 |

이 프로젝트처럼 **Kotlin/Java 두 모듈이 같은 DB를 공유**하는 경우, Redis로 캐시도 공유하면 일관된 캐시 무효화가 가능하다.

## 3. Spring Data Redis Reactive

### 3.1 ReactiveRedisTemplate

Spring WebFlux 환경에서 Redis에 **논블로킹**으로 접근하는 템플릿이다.

```kotlin
// 캐시에서 조회 — Mono<String> 반환
redisTemplate.opsForValue().get("cache:summary")

// 캐시에 저장 — TTL 포함
redisTemplate.opsForValue().set("cache:summary", json, Duration.ofMinutes(5))

// 캐시 삭제
redisTemplate.delete("cache:summary")
```

- `opsForValue()`: String 자료구조 연산 (GET/SET)
- `opsForHash()`: Hash 자료구조 연산 (HGET/HSET)
- 모든 연산이 `Mono`/`Flux`를 반환 → Reactor 체인에 자연스럽게 통합

### 3.2 직렬화

```
객체 → JSON 문자열 → Redis 저장
Redis 조회 → JSON 문자열 → 객체 역직렬화
```

- `StringRedisSerializer`: 키를 문자열로 직렬화
- Jackson을 사용하여 값을 JSON으로 변환
- Redis에 저장된 데이터를 `redis-cli`로 읽을 수 있어 디버깅에 유리

## 4. TTL (Time To Live)

### 4.1 TTL이란?

```
SET cache:summary '{"total":100}' EX 300
                                     ↑
                                  300초(5분) 후 자동 삭제
```

- Redis 키에 **만료 시간**을 설정하는 기능
- TTL이 지나면 Redis가 자동으로 해당 키를 삭제
- 캐시 데이터가 오래된 정보(stale)로 남는 것을 방지

### 4.2 TTL 설정 기준

| 데이터 | TTL | 이유 |
|--------|-----|------|
| 전체 요약 통계 | 5분 | 자주 변하지만 정확한 실시간 불필요 |
| 채널별 통계 | 5분 | 예약 발생 시 변경 |
| 이벤트 통계 | 5분 | 이벤트 발생 시 변경 |
| 객실별 통계 | 5분 | 예약 발생 시 변경 |

## 5. 캐시 무효화 (Cache Invalidation)

### 5.1 언제 캐시를 삭제하는가?

```
예약 생성 → 통계가 변경됨 → 캐시 삭제
예약 취소 → 통계가 변경됨 → 캐시 삭제
```

- 데이터가 변경되면 캐시된 통계가 더 이상 정확하지 않다
- 변경 이벤트 발생 시 관련 캐시 키를 삭제하여 다음 조회 시 최신 데이터를 가져온다

### 5.2 무효화 전략

```
예약 생성/취소
  ↓
CacheService.evictStatisticsCache()
  ↓
Redis DEL cache:statistics:*
  ↓
다음 조회 시 DB에서 새로 조회 → 캐시 재저장
```

## 6. 이 프로젝트의 캐싱 구성

### 6.1 아키텍처

```
클라이언트
  ↓
StatisticsController
  ↓
CacheService (캐시 레이어)
  ├── Cache Hit → Redis에서 JSON 반환 → 역직렬화
  └── Cache Miss → StatisticsService → DB 조회 → 캐시 저장 → 반환
```

### 6.2 캐시 키 설계

```
cache:statistics:summary    — 전체 요약 통계
cache:statistics:channels   — 채널별 통계
cache:statistics:events     — 이벤트 타입별 통계
cache:statistics:rooms      — 객실 타입별 통계
```

### 6.3 구현 파일

```
config/
  └── RedisConfig.kt (.java)        ← ReactiveRedisTemplate 빈 설정
service/
  └── CacheService.kt (.java)       ← 캐시 조회/저장/무효화 로직
controller/
  └── StatisticsController.kt (.java) ← CacheService를 통해 캐시 적용
```
