package com.channelmanager.java.kafka; // Kafka 패키지

import com.channelmanager.java.dto.ReservationEventMessage; // 예약 이벤트 메시지 DTO
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 역직렬화
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 조건부 빈 등록
import org.springframework.kafka.annotation.KafkaListener; // Kafka 소비자 어노테이션
import org.springframework.stereotype.Component; // 빈 등록

// Kafka 이벤트 Consumer — 예약 이벤트를 Kafka 토픽에서 수신하여 처리한다
// @ConditionalOnProperty: kafka.consumer.enabled=false로 설정하면 이 빈이 생성되지 않는다
//   — 테스트 환경에서 Kafka가 없을 때 Consumer를 비활성화하는 데 사용한다
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final ObjectMapper objectMapper;

    // @KafkaListener: 지정된 토픽의 메시지를 자동으로 수신한다
    @KafkaListener(topics = KafkaEventProducer.TOPIC)
    public void consumeReservationEvent(String message) {
        try {
            var event = objectMapper.readValue(message, ReservationEventMessage.class);

            switch (event.eventType()) {
                case "RESERVATION_CREATED" ->
                    log.info("Kafka 수신 — 예약 생성: reservationId={}, channel={}, guest={}",
                        event.reservationId(), event.channelCode(), event.guestName());
                case "RESERVATION_CANCELLED" ->
                    log.info("Kafka 수신 — 예약 취소: reservationId={}, channel={}, guest={}",
                        event.reservationId(), event.channelCode(), event.guestName());
                default ->
                    log.warn("Kafka 수신 — 알 수 없는 이벤트 타입: {}", event.eventType());
            }
        } catch (Exception e) {
            log.warn("Kafka 메시지 처리 실패: {}", message, e);
        }
    }
}
