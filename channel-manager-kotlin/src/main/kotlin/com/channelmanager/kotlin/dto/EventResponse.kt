package com.channelmanager.kotlin.dto // DTO 패키지

import com.channelmanager.kotlin.domain.ChannelEvent // 채널 이벤트 엔티티
import com.channelmanager.kotlin.domain.EventType // 이벤트 타입 enum
import java.time.LocalDateTime // 날짜+시간 타입

// 채널 이벤트 응답 DTO
// ChannelEvent 엔티티를 API 응답 형식으로 변환한다
// SSE 스트림(Phase 5)과 최근 이벤트 목록 조회에서 모두 사용된다
// 엔티티를 직접 노출하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
data class EventResponse(
    val id: Long,                      // 이벤트 ID (PK)
    val eventType: EventType,          // 이벤트 타입 (RESERVATION_CREATED, INVENTORY_UPDATED 등)
    val channelId: Long?,              // 채널 ID (FK, nullable — 이벤트 종류에 따라 없을 수 있음)
    val reservationId: Long?,          // 예약 ID (FK, nullable)
    val roomTypeId: Long?,             // 객실 타입 ID (FK, nullable)
    val eventPayload: String?,         // 이벤트 상세 데이터 (JSON 문자열)
    val createdAt: LocalDateTime?      // 이벤트 발생 시각
) {

    // companion object — Kotlin에서 static 메서드를 정의하는 방법
    // Java의 static 메서드와 달리, 객체 내에 선언하여 클래스 레벨 함수를 제공한다
    companion object {

        // ChannelEvent 엔티티를 EventResponse DTO로 변환하는 팩토리 메서드
        // SSE 스트림에서 ServerSentEvent의 data 필드로 사용된다
        // 최근 이벤트 목록 조회에서도 동일한 변환을 적용한다
        // id!!: 저장된 이벤트는 항상 ID가 존재하므로 non-null 단언을 사용한다
        fun from(event: ChannelEvent): EventResponse = EventResponse(
            id = event.id!!,                   // PK — DB 저장 후 항상 값이 존재
            eventType = event.eventType,       // 이벤트 타입
            channelId = event.channelId,       // 채널 ID (nullable)
            reservationId = event.reservationId, // 예약 ID (nullable)
            roomTypeId = event.roomTypeId,     // 객실 타입 ID (nullable)
            eventPayload = event.eventPayload, // 이벤트 페이로드 (JSON)
            createdAt = event.createdAt        // 생성 시각
        )
    }
}
