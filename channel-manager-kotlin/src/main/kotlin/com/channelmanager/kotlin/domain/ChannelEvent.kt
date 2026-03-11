package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema // API 문서화용 스키마 어노테이션
import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime // 날짜+시간 타입

// 채널 이벤트 엔티티 - channel_events 테이블과 매핑
// 시스템에서 발생하는 모든 변경사항을 이벤트로 기록하는 이벤트 소싱 테이블
// Phase 5에서 SSE(Server-Sent Events)를 통해 실시간으로 클라이언트에 전달된다
// 관련 FK들은 nullable - 이벤트 종류에 따라 관련 엔티티가 다르기 때문
@Schema(description = "시스템 변경 이벤트를 기록하는 엔티티")
@Table("channel_events")
data class ChannelEvent(
    @field:Schema(description = "이벤트 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    val id: Long? = null, // null이면 INSERT, 값이 있으면 UPDATE

    @field:Schema(description = "이벤트 타입 (INVENTORY_UPDATED, RESERVATION_CREATED, RESERVATION_CANCELLED, CHANNEL_SYNCED)", example = "RESERVATION_CREATED")
    val eventType: EventType,

    @field:Schema(description = "관련 채널 ID (FK)", example = "1", nullable = true)
    val channelId: Long? = null,

    @field:Schema(description = "관련 예약 ID (FK)", example = "1", nullable = true)
    val reservationId: Long? = null,

    @field:Schema(description = "관련 객실 타입 ID (FK)", example = "1", nullable = true)
    val roomTypeId: Long? = null,

    @field:Schema(description = "이벤트 상세 데이터 (JSON)", example = "{\"before\": 10, \"after\": 9}", nullable = true)
    val payload: String? = null,

    @field:Schema(description = "이벤트 발생 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    val createdAt: LocalDateTime? = null
)
