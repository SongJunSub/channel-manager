package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema // API 문서화용 스키마 어노테이션
import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.math.BigDecimal // 마크업 비율 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 판매 채널 엔티티 - channels 테이블과 매핑
// 호텔이 객실을 판매하는 경로를 나타낸다
// 예: DIRECT(자사 홈페이지), OTA_A(온라인 여행사 A), OTA_B(온라인 여행사 B)
@Schema(description = "판매 채널 정보를 나타내는 엔티티")
@Table("channels")
data class Channel(
    @field:Schema(description = "채널 ID (PK)", example = "1")
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    val id: Long? = null, // null이면 INSERT, 값이 있으면 UPDATE

    @field:Schema(description = "채널 코드", example = "OTA_A")
    val channelCode: String,

    @field:Schema(description = "채널명", example = "온라인 여행사 A")
    val channelName: String,

    @field:Schema(description = "채널 활성 상태", example = "true", defaultValue = "true")
    val isActive: Boolean = true,

    @field:Schema(description = "마크업 비율 (1.0=기본가, 1.15=15%인상)", example = "1.000")
    val markupRate: BigDecimal = BigDecimal.ONE, // 기본값 1.0 — 가격 변동 없음

    @field:Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    val createdAt: LocalDateTime? = null
)
