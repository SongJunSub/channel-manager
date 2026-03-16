package com.channelmanager.java.dto; // DTO 패키지

import com.channelmanager.java.domain.Reservation; // 예약 엔티티
import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum
import java.math.BigDecimal; // 금액 타입
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// 예약 응답 DTO
// 엔티티를 직접 반환하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
// channelCode를 포함하여 클라이언트가 채널 ID 대신 코드로 채널을 식별할 수 있도록 한다
// Java record를 사용하여 불변 DTO를 간결하게 정의한다
// Kotlin에서는 data class + companion object으로 팩토리 메서드를 정의하지만,
// Java에서는 record + static 메서드로 동일한 효과를 얻는다
public record ReservationResponse(
    Long id,                      // 예약 ID (PK)
    Long channelId,               // 채널 ID (FK)
    String channelCode,           // 채널 코드 (조회 편의를 위해 포함)
    Long roomTypeId,              // 객실 타입 ID (FK)
    LocalDate checkInDate,        // 체크인 날짜
    LocalDate checkOutDate,       // 체크아웃 날짜
    String guestName,             // 투숙객 이름
    int roomQuantity,             // 예약 객실 수
    ReservationStatus status,     // 예약 상태 (CONFIRMED, CANCELLED)
    BigDecimal totalPrice,        // 총 금액 (basePrice × 숙박일수 × 객실수)
    LocalDateTime createdAt       // 예약 생성 시각
) {

    // Reservation 엔티티 + 채널 코드를 ReservationResponse DTO로 변환하는 팩토리 메서드
    // 채널 코드는 Reservation 엔티티에 포함되지 않으므로 별도 파라미터로 받는다
    // R2DBC는 JOIN을 지원하지 않으므로 서비스에서 채널을 별도 조회하여 전달한다
    // Kotlin에서는 companion object { fun from(...) }으로 정의하지만,
    // Java에서는 static 메서드로 정의한다
    public static ReservationResponse from(
            Reservation reservation, String channelCode) {
        return new ReservationResponse(
            reservation.getId(),            // PK
            reservation.getChannelId(),     // 채널 ID
            channelCode,                    // 채널 코드 (별도 전달)
            reservation.getRoomTypeId(),    // 객실 타입 ID
            reservation.getCheckInDate(),   // 체크인 날짜
            reservation.getCheckOutDate(),  // 체크아웃 날짜
            reservation.getGuestName(),     // 투숙객 이름
            reservation.getRoomQuantity(),  // 객실 수
            reservation.getStatus(),        // 예약 상태
            reservation.getTotalPrice(),    // 총 금액
            reservation.getCreatedAt()      // 생성 시각
        );
    }
}
