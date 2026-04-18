package com.channelmanager.kotlin.dto // DTO 패키지

import java.math.BigDecimal // 금액 타입
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// Phase 25: Kafka 메시지 페이로드 — 예약 이벤트 정보
// Kafka 토픽(reservation-events)에 JSON으로 직렬화되어 전송된다
// Producer가 이 메시지를 발행하고, Consumer가 수신하여 처리한다
data class ReservationEventMessage(
    val eventType: String,          // RESERVATION_CREATED 또는 RESERVATION_CANCELLED
    val reservationId: Long,        // 예약 ID
    val channelCode: String,        // 채널 코드 (DIRECT, BOOKING 등) — Kafka Key로도 사용
    val guestName: String,          // 투숙객 이름
    val roomTypeId: Long,           // 객실 타입 ID
    val checkInDate: LocalDate,     // 체크인 날짜
    val checkOutDate: LocalDate,    // 체크아웃 날짜
    val totalPrice: BigDecimal?,    // 총 금액
    val timestamp: LocalDateTime = LocalDateTime.now() // 이벤트 발생 시각
)
