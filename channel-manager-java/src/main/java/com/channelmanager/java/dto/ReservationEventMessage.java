package com.channelmanager.java.dto; // DTO 패키지

import java.math.BigDecimal; // 금액 타입
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// Phase 25: Kafka 메시지 페이로드 — 예약 이벤트 정보
// Kotlin에서는 data class를 사용하지만, Java에서는 record를 사용한다
public record ReservationEventMessage(
    String eventType,          // RESERVATION_CREATED 또는 RESERVATION_CANCELLED
    Long reservationId,        // 예약 ID
    String channelCode,        // 채널 코드 — Kafka Key로도 사용
    String guestName,          // 투숙객 이름
    Long roomTypeId,           // 객실 타입 ID
    LocalDate checkInDate,     // 체크인 날짜
    LocalDate checkOutDate,    // 체크아웃 날짜
    BigDecimal totalPrice,     // 총 금액
    LocalDateTime timestamp    // 이벤트 발생 시각
) {
    // 기본 timestamp를 현재 시각으로 설정하는 정적 팩토리
    public static ReservationEventMessage of(
            String eventType, Long reservationId, String channelCode,
            String guestName, Long roomTypeId, LocalDate checkInDate,
            LocalDate checkOutDate, BigDecimal totalPrice) {
        return new ReservationEventMessage(
            eventType, reservationId, channelCode, guestName, roomTypeId,
            checkInDate, checkOutDate, totalPrice, LocalDateTime.now()
        );
    }
}
