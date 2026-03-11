package com.channelmanager.java.domain; // 도메인 엔티티 패키지

// 채널 이벤트 타입을 나타내는 열거형 (enum)
// 시스템에서 발생하는 모든 변경사항의 종류를 정의한다
// DB에는 VARCHAR로 저장되고, 코드에서는 타입 안전하게 사용한다
public enum EventType {

    INVENTORY_UPDATED, // 재고 변경 - 재고 수량이 변경되었을 때 (Phase 2)
    RESERVATION_CREATED, // 예약 생성 - 새로운 예약이 만들어졌을 때 (Phase 4)
    RESERVATION_CANCELLED, // 예약 취소 - 기존 예약이 취소되었을 때 (Phase 7)
    CHANNEL_SYNCED // 채널 동기화 - 외부 채널과 재고가 동기화되었을 때 (Phase 4)

}
