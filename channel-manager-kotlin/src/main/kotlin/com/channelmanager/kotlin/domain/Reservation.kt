package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema // API 문서화용 스키마 어노테이션
import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.annotation.LastModifiedDate // 수정 시각 자동 기록 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.math.BigDecimal // 금액 처리용 정밀 숫자 타입
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 예약 엔티티 - reservations 테이블과 매핑
// 특정 채널(Channel)을 통해 특정 객실 타입(RoomType)을 예약한 정보를 저장한다
// R2DBC는 연관관계 어노테이션이 없으므로 channel_id, room_type_id를 직접 저장한다
@Schema(description = "예약 정보를 나타내는 엔티티")
@Table("reservations")
data class Reservation(
    @field:Schema(description = "예약 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    val id: Long? = null, // null이면 INSERT, 값이 있으면 UPDATE

    @field:Schema(description = "예약 채널 ID (FK)", example = "1")
    val channelId: Long,

    @field:Schema(description = "객실 타입 ID (FK)", example = "1")
    val roomTypeId: Long,

    @field:Schema(description = "체크인 날짜", example = "2026-03-15")
    val checkInDate: LocalDate,

    @field:Schema(description = "체크아웃 날짜", example = "2026-03-17")
    val checkOutDate: LocalDate,

    @field:Schema(description = "투숙객 이름", example = "홍길동")
    val guestName: String,

    @field:Schema(description = "예약 객실 수", example = "1", defaultValue = "1")
    val quantity: Int = 1,

    @field:Schema(description = "예약 상태 (CONFIRMED, CANCELLED)", example = "CONFIRMED")
    val status: ReservationStatus,

    @field:Schema(description = "총 금액", example = "300000", nullable = true)
    val totalPrice: BigDecimal? = null,

    @field:Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    val createdAt: LocalDateTime? = null,

    @field:Schema(description = "마지막 수정 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    val updatedAt: LocalDateTime? = null
)
