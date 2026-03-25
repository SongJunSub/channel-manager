package com.channelmanager.java.dto; // DTO 패키지

import com.channelmanager.java.domain.ChannelEvent; // 채널 이벤트 엔티티
import com.channelmanager.java.domain.EventType; // 이벤트 타입 enum
import java.time.LocalDateTime; // 날짜+시간 타입

// 채널 이벤트 응답 DTO
// ChannelEvent 엔티티를 API 응답 형식으로 변환한다
// SSE 스트림(Phase 5)과 최근 이벤트 목록 조회에서 모두 사용된다
// 엔티티를 직접 노출하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
// Kotlin에서는 data class + companion object로 정의하지만,
// Java에서는 record + static 메서드로 동일한 효과를 얻는다
public record EventResponse(
    Long id,                      // 이벤트 ID (PK)
    EventType eventType,          // 이벤트 타입 (RESERVATION_CREATED, INVENTORY_UPDATED 등)
    Long channelId,               // 채널 ID (FK, nullable — 이벤트 종류에 따라 없을 수 있음)
    Long reservationId,           // 예약 ID (FK, nullable)
    Long roomTypeId,              // 객실 타입 ID (FK, nullable)
    String eventPayload,          // 이벤트 상세 데이터 (JSON 문자열)
    LocalDateTime createdAt       // 이벤트 발생 시각
) {

    // ChannelEvent 엔티티를 EventResponse DTO로 변환하는 팩토리 메서드
    // SSE 스트림에서 ServerSentEvent의 data 필드로 사용된다
    // 최근 이벤트 목록 조회에서도 동일한 변환을 적용한다
    // Kotlin에서는 companion object { fun from(...) }으로 정의하지만,
    // Java에서는 static 메서드로 정의한다
    public static EventResponse from(ChannelEvent event) {
        return new EventResponse(
            event.getId(),            // PK — DB 저장 후 항상 값이 존재
            event.getEventType(),     // 이벤트 타입
            event.getChannelId(),     // 채널 ID (nullable)
            event.getReservationId(), // 예약 ID (nullable)
            event.getRoomTypeId(),    // 객실 타입 ID (nullable)
            event.getEventPayload(), // 이벤트 페이로드 (JSON)
            event.getCreatedAt()     // 생성 시각
        );
    }
}
