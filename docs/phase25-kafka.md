# Phase 25 — Kafka 메시지 큐 연동

## 1. 메시지 큐란?

### 1.1 동기 vs 비동기 통신

```
동기 (기존 방식):
  예약 생성 → 재고 차감 → 이벤트 기록 → SSE 발행 → 응답
  (모든 작업이 하나의 HTTP 요청 안에서 순차 실행)

비동기 (메시지 큐):
  예약 생성 → Kafka에 메시지 발행 → 응답 (즉시)
                    │
                    ├── Consumer A: 재고 차감
                    ├── Consumer B: 이벤트 기록
                    └── Consumer C: SSE/WebSocket 알림
```

- **동기**: 호출자가 결과를 기다림 → 간단하지만 느림
- **비동기**: 메시지만 보내고 즉시 반환 → 빠르지만 결과 확인이 복잡

### 1.2 왜 메시지 큐가 필요한가?

| 문제 | 메시지 큐 해결 |
|------|--------------|
| 서비스 간 강결합 | 메시지로 느슨한 결합 — 생산자/소비자가 서로 모름 |
| 부하 급증 시 장애 | 버퍼링 — 메시지를 큐에 쌓아두고 천천히 처리 |
| 장애 전파 | 격리 — 소비자가 죽어도 생산자는 정상 |
| 순서 보장 | 파티션 내 순서 보장 — 같은 키의 메시지는 순서대로 처리 |

## 2. Apache Kafka

### 2.1 Kafka란?

```
Producer → Topic (파티션으로 분할) → Consumer Group
  │            │                        │
  │     ┌──────┴──────┐         ┌───────┴───────┐
  │     │ Partition 0 │         │ Consumer A    │
  │     │ Partition 1 │         │ Consumer B    │
  │     │ Partition 2 │         │ Consumer C    │
  │     └─────────────┘         └───────────────┘
```

- **분산 이벤트 스트리밍 플랫폼** (LinkedIn에서 개발)
- **Topic**: 메시지를 분류하는 카테고리 (DB의 테이블에 비유)
- **Partition**: Topic 내 병렬 처리 단위 — 순서 보장은 파티션 내에서만
- **Consumer Group**: 같은 그룹의 소비자는 파티션을 분배하여 처리 (병렬 처리)
- **Offset**: 각 소비자가 어디까지 읽었는지 기록 (재처리 가능)

### 2.2 Kafka vs 다른 메시지 시스템

| 비교 | Kafka | RabbitMQ | SQS |
|------|-------|----------|-----|
| 모델 | 로그 기반 (영속) | 큐 기반 (소비 시 삭제) | 큐 기반 |
| 처리량 | 초당 수백만 건 | 초당 수만 건 | 제한적 |
| 순서 보장 | 파티션 내 보장 | 큐 내 보장 | 제한적 |
| 재처리 | 오프셋 리셋으로 가능 | 불가 | 제한적 |
| 적합한 경우 | 이벤트 스트리밍, 로그 | 작업 큐, RPC | AWS 네이티브 |

### 2.3 핵심 개념

```
Producer                    Kafka Broker                Consumer
  │                              │                         │
  ├── send(topic, key, value) ──▶│ Partition 0: [m1, m3]   │
  │                              │ Partition 1: [m2, m4]   ├── poll() → m1
  │                              │                         ├── poll() → m2
  │                              │ offset tracking ────────┤
```

- **Producer**: 메시지를 토픽에 발행
- **Broker**: 메시지를 저장하고 전달하는 서버
- **Consumer**: 토픽에서 메시지를 읽어 처리
- **Key**: 같은 키는 같은 파티션에 배치 (순서 보장)
- **Offset**: 소비자가 마지막으로 읽은 위치

## 3. Spring for Apache Kafka

### 3.1 Reactor Kafka

```
일반 Spring Kafka:           Reactor Kafka:
  @KafkaListener              ReactiveKafkaConsumerTemplate
  KafkaTemplate               ReactiveKafkaProducerTemplate
  (블로킹)                    (논블로킹, Reactor 통합)
```

Spring WebFlux에서는 **Reactor Kafka**를 사용하여 논블로킹 Kafka 연동을 구현한다.

### 3.2 Producer 설정

```kotlin
// ReactiveKafkaProducerTemplate: 논블로킹 Kafka 발행
producerTemplate.send(topic, key, value)
    .doOnNext { result -> log.info("Kafka 전송 성공: offset={}", result.recordMetadata().offset()) }
    .subscribe()
```

### 3.3 Consumer 설정

```kotlin
// ReactiveKafkaConsumerTemplate: 논블로킹 Kafka 소비
consumerTemplate.receiveAutoAck()
    .doOnNext { record -> log.info("Kafka 수신: key={}, value={}", record.key(), record.value()) }
    .flatMap { record -> processMessage(record.value()) }
    .subscribe()
```

## 4. 이 프로젝트의 Kafka 구성

### 4.1 아키텍처

```
예약 생성/취소
  ↓
ReservationService
  ↓
KafkaEventProducer ──publish──▶ Kafka Topic: reservation-events
                                     │
                                     ▼
                              KafkaEventConsumer
                                     │
                                     ├── 로그 기록
                                     └── EventPublisher(Sinks) 발행 → SSE/WebSocket
```

### 4.2 토픽 설계

| 토픽 | Key | Value | 용도 |
|------|-----|-------|------|
| `reservation-events` | channelCode | JSON (예약 정보) | 예약 생성/취소 이벤트 |

- **Key = channelCode**: 같은 채널의 이벤트가 같은 파티션에 배치 → 채널별 순서 보장

### 4.3 구현 파일

```
config/
  └── KafkaConfig.kt (.java)          ← Producer/Consumer Template 빈 설정
kafka/
  ├── KafkaEventProducer.kt (.java)   ← 메시지 발행 (예약 생성/취소 시)
  └── KafkaEventConsumer.kt (.java)   ← 메시지 소비 (로그 + Sinks 발행)
dto/
  └── ReservationEventMessage.kt (.java) ← Kafka 메시지 페이로드
```
