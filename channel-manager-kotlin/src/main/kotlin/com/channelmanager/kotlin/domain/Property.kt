package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema // API 문서화용 스키마 어노테이션
import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime // 날짜+시간 타입

// 숙소 엔티티 - properties 테이블과 매핑
// R2DBC는 JPA의 @Entity 대신 @Table을 사용한다
// data class를 사용하여 equals, hashCode, toString, copy를 자동 생성한다
@Schema(description = "숙소 정보를 나타내는 엔티티")
@Table("properties")
data class Property(
    @field:Schema(description = "숙소 고유 식별자 (PK)", example = "1")
    @Id // 이 필드가 PK(Primary Key)임을 Spring Data에 알린다
    val id: Long? = null, // null이면 새 엔티티(INSERT), 값이 있으면 기존 엔티티(UPDATE)

    @field:Schema(description = "숙소 고유 코드", example = "SEOUL_GRAND")
    val propertyCode: String,

    @field:Schema(description = "숙소명", example = "서울 그랜드 호텔")
    val propertyName: String,

    @field:Schema(description = "숙소 주소", example = "서울특별시 중구 을지로 30", nullable = true)
    val propertyAddress: String? = null,

    @field:Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY)
    @CreatedDate // Spring Data가 엔티티 생성 시 현재 시각을 자동으로 채워준다
    val createdAt: LocalDateTime? = null
)
