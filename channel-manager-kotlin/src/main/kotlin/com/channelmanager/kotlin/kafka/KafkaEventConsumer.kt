package com.channelmanager.kotlin.kafka // Kafka 패키지

import com.channelmanager.kotlin.dto.ReservationEventMessage // 예약 이벤트 메시지 DTO
import com.fasterxml.jackson.databind.ObjectMapper // JSON 역직렬화
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty // 조건부 빈 등록
import org.springframework.kafka.annotation.KafkaListener // Kafka 소비자 어노테이션
import org.springframework.stereotype.Component // 빈 등록

// Kafka 이벤트 Consumer — 예약 이벤트를 Kafka 토픽에서 수신하여 처리한다
// Phase 25: @KafkaListener로 reservation-events 토픽을 구독한다
// Consumer Group ID는 application.yml의 spring.kafka.consumer.group-id로 설정된다
// 같은 Group의 Consumer들이 파티션을 분배하여 병렬 처리한다
// 다른 Group의 Consumer는 같은 메시지를 각자 독립적으로 수신한다 (브로드캐스트 효과)
@Component
@ConditionalOnProperty(name = ["kafka.consumer.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaEventConsumer(
    private val objectMapper: ObjectMapper // JSON 역직렬화
) {

    companion object {
        private val log = LoggerFactory.getLogger(KafkaEventConsumer::class.java)
    }

    // @KafkaListener: 지정된 토픽의 메시지를 자동으로 수신한다
    // topics: 구독할 토픽 이름 — KafkaEventProducer.TOPIC과 동일해야 한다
    // Spring Kafka가 자동으로 오프셋을 관리한다 (auto-commit)
    // 수신된 메시지는 value(JSON 문자열)로 전달된다
    @KafkaListener(topics = [KafkaEventProducer.TOPIC])
    fun consumeReservationEvent(message: String) {
        try {
            // JSON → ReservationEventMessage 역직렬화
            val event = objectMapper.readValue(message, ReservationEventMessage::class.java)

            // 이벤트 타입에 따라 처리
            // 현재는 로그 기록만 수행하지만, 향후 다음과 같이 확장 가능:
            //   - 외부 OTA API 호출 (채널 동기화)
            //   - 알림 서비스 연동 (이메일, SMS)
            //   - 분석 시스템 데이터 적재
            when (event.eventType) {
                "RESERVATION_CREATED" -> {
                    log.info("Kafka 수신 — 예약 생성: reservationId={}, channel={}, guest={}",
                        event.reservationId, event.channelCode, event.guestName)
                }
                "RESERVATION_CANCELLED" -> {
                    log.info("Kafka 수신 — 예약 취소: reservationId={}, channel={}, guest={}",
                        event.reservationId, event.channelCode, event.guestName)
                }
                else -> {
                    log.warn("Kafka 수신 — 알 수 없는 이벤트 타입: {}", event.eventType)
                }
            }
        } catch (e: Exception) {
            // 역직렬화 실패 또는 처리 오류 시 경고 로그
            // 메시지를 버리고 다음 메시지를 처리한다 (Dead Letter Queue는 향후 구현)
            log.warn("Kafka 메시지 처리 실패: {}", message, e)
        }
    }
}
