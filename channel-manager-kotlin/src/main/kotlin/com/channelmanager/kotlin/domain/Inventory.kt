package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import io.swagger.v3.oas.annotations.media.Schema // API 문서화용 스키마 어노테이션
import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.annotation.LastModifiedDate // 수정 시각 자동 기록 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDate // 날짜 타입 (시간 없이 날짜만)
import java.time.LocalDateTime // 날짜+시간 타입

// 재고 엔티티 - inventories 테이블과 매핑
// 특정 날짜(stockDate)에 특정 객실 타입(roomTypeId)의 재고를 관리한다
// 예: 2026-03-15 Deluxe 전체 10개, 예약 가능 5개
// 하나의 객실 타입(RoomType)은 날짜별로 여러 재고(Inventory) 레코드를 가진다 (1:N 관계)
@Schema(description = "날짜별 객실 재고 정보를 나타내는 엔티티") // 엔티티 레벨 API 문서화
@Table("inventories") // 매핑할 데이터베이스 테이블명 지정
data class Inventory(
    @field:Schema(description = "재고 고유 식별자 (PK)", example = "1") // PK 문서화
    @Id // 이 필드가 PK임을 Spring Data에 알린다
    val id: Long? = null, // PK - null이면 INSERT, 값이 있으면 UPDATE

    @field:Schema(description = "객실 타입 ID (FK)", example = "1") // 외래키 문서화
    val roomTypeId: Long, // 객실 타입 FK - 어떤 객실 타입의 재고인지 식별

    @field:Schema(description = "재고 날짜", example = "2026-03-15") // 날짜 문서화
    val stockDate: LocalDate, // 재고 날짜 - 이 날짜의 재고 현황을 나타낸다

    @field:Schema(description = "전체 객실 수", example = "10") // 전체 수량 문서화
    val totalQuantity: Int, // 전체 객실 수 - 해당 날짜에 판매 가능한 총 객실 수

    @field:Schema(description = "예약 가능 수량", example = "5") // 잔여 수량 문서화
    val availableQuantity: Int, // 예약 가능 수량 - 아직 예약되지 않은 잔여 객실 수

    @field:Schema(description = "엔티티 생성 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @CreatedDate // 엔티티 생성 시 현재 시각 자동 기록
    val createdAt: LocalDateTime? = null, // 생성 시각

    @field:Schema(description = "마지막 수정 시각", accessMode = Schema.AccessMode.READ_ONLY) // 읽기 전용 필드 문서화
    @LastModifiedDate // 엔티티 수정 시 현재 시각 자동 기록
    val updatedAt: LocalDateTime? = null // 수정 시각 - 재고가 변경될 때마다 갱신된다
)
