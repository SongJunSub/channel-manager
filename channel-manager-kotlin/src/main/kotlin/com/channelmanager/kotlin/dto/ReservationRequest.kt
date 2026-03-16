package com.channelmanager.kotlin.dto // DTO 패키지 - 요청/응답 데이터 전송 객체

import java.time.LocalDate // 날짜 타입 (시간 없이 날짜만)

// 예약 생성 요청 DTO
// 시뮬레이터 또는 외부 클라이언트가 예약을 생성할 때 사용하는 데이터 구조
// channelCode를 사용하여 채널 ID 대신 코드로 채널을 식별한다 (사용자 친화적)
// 체크인~체크아웃 기간의 모든 날짜에 대해 재고를 차감한다
data class ReservationCreateRequest(
    val channelCode: String,     // 채널 코드 (예: "BOOKING", "AGODA", "DIRECT")
    val roomTypeId: Long,        // 예약할 객실 타입 ID (FK)
    val checkInDate: LocalDate,  // 체크인 날짜 (이 날짜부터 재고 차감)
    val checkOutDate: LocalDate, // 체크아웃 날짜 (이 날짜는 재고 차감 안 함 — 숙박하지 않으므로)
    val guestName: String,       // 투숙객 이름
    val roomQuantity: Int = 1    // 예약 객실 수 (기본값 1실)
)
