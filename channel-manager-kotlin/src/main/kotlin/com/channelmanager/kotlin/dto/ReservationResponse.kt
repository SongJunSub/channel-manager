package com.channelmanager.kotlin.dto // DTO 패키지

import com.channelmanager.kotlin.domain.Reservation // 예약 엔티티
import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum
import java.math.BigDecimal // 금액 타입
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 예약 응답 DTO
// 엔티티를 직접 반환하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
// channelCode를 포함하여 클라이언트가 채널 ID 대신 코드로 채널을 식별할 수 있도록 한다
data class ReservationResponse(
    val id: Long,                      // 예약 ID (PK)
    val channelId: Long,               // 채널 ID (FK)
    val channelCode: String,           // 채널 코드 (조회 편의를 위해 포함)
    val roomTypeId: Long,              // 객실 타입 ID (FK)
    val checkInDate: LocalDate,        // 체크인 날짜
    val checkOutDate: LocalDate,       // 체크아웃 날짜
    val guestName: String,             // 투숙객 이름
    val roomQuantity: Int,             // 예약 객실 수
    val status: ReservationStatus,     // 예약 상태 (CONFIRMED, CANCELLED)
    val totalPrice: BigDecimal?,       // 총 금액 (basePrice × 숙박일수 × 객실수)
    val createdAt: LocalDateTime?      // 예약 생성 시각
) {
    companion object {
        // Reservation 엔티티 + 채널 코드를 ReservationResponse DTO로 변환하는 팩토리 메서드
        // 채널 코드는 Reservation 엔티티에 포함되지 않으므로 별도 파라미터로 받는다
        // R2DBC는 JOIN을 지원하지 않으므로 서비스에서 채널을 별도 조회하여 전달한다
        fun from(reservation: Reservation, channelCode: String): ReservationResponse =
            ReservationResponse(
                id = reservation.id!!,                   // PK는 저장 후 항상 존재하므로 !! 사용
                channelId = reservation.channelId,       // 채널 ID
                channelCode = channelCode,               // 채널 코드 (별도 전달)
                roomTypeId = reservation.roomTypeId,     // 객실 타입 ID
                checkInDate = reservation.checkInDate,   // 체크인 날짜
                checkOutDate = reservation.checkOutDate, // 체크아웃 날짜
                guestName = reservation.guestName,       // 투숙객 이름
                roomQuantity = reservation.roomQuantity,  // 객실 수
                status = reservation.status,             // 예약 상태
                totalPrice = reservation.totalPrice,     // 총 금액
                createdAt = reservation.createdAt        // 생성 시각
            )
    }
}
