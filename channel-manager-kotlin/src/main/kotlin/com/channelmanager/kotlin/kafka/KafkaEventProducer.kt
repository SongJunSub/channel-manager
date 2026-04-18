package com.channelmanager.kotlin.kafka // Kafka 패키지

import com.channelmanager.kotlin.dto.ReservationEventMessage // 예약 이벤트 메시지 DTO
import com.fasterxml.jackson.databind.ObjectMapper // JSON 직렬화
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.kafka.core.KafkaTemplate // Spring Kafka 발행 템플릿
import org.springframework.stereotype.Component // 빈 등록

// Kafka 이벤트 Producer — 예약 이벤트를 Kafka 토픽에 발행한다
// Phase 25: 예약 생성/취소 시 ReservationService에서 호출된다
// KafkaTemplate: Spring Kafka의 메시지 발행 템플릿 (동기/비동기)
//   — WebFlux에서도 KafkaTemplate은 내부적으로 비동기 전송을 사용하므로 블로킹 이슈가 적다
//   — Reactor Kafka의 ReactiveKafkaProducerTemplate도 사용 가능하나,
//     학습 목적으로 Spring Kafka 기본 템플릿을 사용한다
@Component
class KafkaEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>, // String 키+값 발행 템플릿
    private val objectMapper: ObjectMapper                    // JSON 직렬화
) {

    companion object {
        private val log = LoggerFactory.getLogger(KafkaEventProducer::class.java)
        // Kafka 토픽 이름 — 예약 관련 이벤트를 이 토픽에 발행한다
        const val TOPIC = "reservation-events"
    }

    // 예약 이벤트를 Kafka에 발행
    // key: channelCode — 같은 채널의 이벤트가 같은 파티션에 배치되어 순서가 보장된다
    // value: JSON으로 직렬화된 ReservationEventMessage
    fun publishReservationEvent(message: ReservationEventMessage) {
        try {
            // 객체 → JSON 문자열 직렬화
            val json = objectMapper.writeValueAsString(message)
            // Kafka에 비동기 전송 — key는 channelCode (파티션 할당 기준)
            kafkaTemplate.send(TOPIC, message.channelCode, json)
                .whenComplete { result, ex ->
                    if (ex != null) {
                        // 전송 실패 시 경고 로그 (앱 흐름은 중단하지 않음)
                        log.warn("Kafka 전송 실패: topic={}, key={}", TOPIC, message.channelCode, ex)
                    } else {
                        // 전송 성공 시 디버그 로그
                        log.debug("Kafka 전송 성공: topic={}, partition={}, offset={}",
                            TOPIC,
                            result.recordMetadata.partition(),
                            result.recordMetadata.offset()
                        )
                    }
                }
        } catch (e: Exception) {
            // JSON 직렬화 실패 시 경고 로그
            log.warn("Kafka 메시지 직렬화 실패: {}", message.eventType, e)
        }
    }
}
