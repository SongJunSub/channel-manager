package com.channelmanager.java.kafka; // Kafka 패키지

import com.channelmanager.java.dto.ReservationEventMessage; // 예약 이벤트 메시지 DTO
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 직렬화
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.kafka.core.KafkaTemplate; // Spring Kafka 발행 템플릿
import org.springframework.stereotype.Component; // 빈 등록

// Kafka 이벤트 Producer — 예약 이벤트를 Kafka 토픽에 발행한다
// Kotlin에서는 class KafkaEventProducer(val kafkaTemplate, val objectMapper)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventProducer.class);
    public static final String TOPIC = "reservation-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // 예약 이벤트를 Kafka에 발행
    public void publishReservationEvent(ReservationEventMessage message) {
        try {
            var json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(TOPIC, message.channelCode(), json)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Kafka 전송 실패: topic={}, key={}", TOPIC, message.channelCode(), ex);
                    } else {
                        log.debug("Kafka 전송 성공: topic={}, partition={}, offset={}",
                            TOPIC,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    }
                });
        } catch (Exception e) {
            log.warn("Kafka 메시지 직렬화 실패: {}", message.eventType(), e);
        }
    }
}
