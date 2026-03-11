package com.channelmanager.java.domain; // 도메인 엔티티 패키지

// 예약 상태를 나타내는 열거형 (enum)
// DB에는 VARCHAR로 저장되고, 코드에서는 타입 안전하게 사용한다
public enum ReservationStatus {
    CONFIRMED, // 예약 확정 - 정상적으로 예약이 완료된 상태
    CANCELLED  // 예약 취소 - 고객 또는 시스템에 의해 취소된 상태 (Phase 7에서 활용)
}
