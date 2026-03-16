package com.channelmanager.java.dto; // DTO 패키지 - 요청/응답 데이터 전송 객체

import java.time.LocalDate; // 날짜 타입 (시간 없이 날짜만)

// 예약 생성 요청 DTO
// 시뮬레이터 또는 외부 클라이언트가 예약을 생성할 때 사용하는 데이터 구조
// Java record를 사용하여 불변 DTO를 간결하게 정의한다
// Kotlin에서는 data class로 정의하지만, Java에서는 record로 동일한 효과를 얻는다
// channelCode를 사용하여 채널 ID 대신 코드로 채널을 식별한다 (사용자 친화적)
public record ReservationCreateRequest(
    String channelCode,     // 채널 코드 (예: "BOOKING", "AGODA", "DIRECT")
    Long roomTypeId,        // 예약할 객실 타입 ID (FK)
    LocalDate checkInDate,  // 체크인 날짜 (이 날짜부터 재고 차감)
    LocalDate checkOutDate, // 체크아웃 날짜 (이 날짜는 재고 차감 안 함 — 숙박하지 않으므로)
    String guestName,       // 투숙객 이름
    int roomQuantity        // 예약 객실 수 (Kotlin에서는 기본값 1을 지원하지만, record는 기본값 미지원)
) {
}
