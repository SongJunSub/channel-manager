package com.channelmanager.kotlin.dto // DTO 패키지 - 요청/응답 데이터 전송 객체

import java.time.LocalDate // 날짜 타입 (시간 없이 날짜만)

// 재고 단건 생성 요청 DTO
// 특정 객실 타입의 특정 날짜에 대한 재고를 1건 생성한다
// data class를 사용하여 equals, hashCode, toString, copy를 자동 생성한다
data class InventoryCreateRequest(
    val roomTypeId: Long,      // 재고를 생성할 객실 타입 ID (FK)
    val stockDate: LocalDate,  // 재고 날짜 (예: 2026-03-15)
    val totalQuantity: Int     // 전체 객실 수량 (예: 10)
)

// 재고 기간별 일괄 생성 요청 DTO
// 시작일 ~ 종료일 범위의 모든 날짜에 대해 동일한 수량으로 재고를 일괄 생성한다
// 예: 3월 15일~31일까지 Deluxe Twin 10실 → 17건의 재고 레코드 생성
data class InventoryBulkCreateRequest(
    val roomTypeId: Long,      // 재고를 생성할 객실 타입 ID (FK)
    val startDate: LocalDate,  // 시작 날짜 (포함)
    val endDate: LocalDate,    // 종료 날짜 (포함)
    val totalQuantity: Int     // 각 날짜에 설정할 전체 객실 수량
)

// 재고 수정 요청 DTO
// null인 필드는 수정하지 않는다 (Partial Update 패턴)
// 예: totalQuantity만 수정하고 availableQuantity는 그대로 유지
data class InventoryUpdateRequest(
    val totalQuantity: Int?,       // 전체 수량 (null이면 수정 안 함)
    val availableQuantity: Int?    // 가용 수량 (null이면 수정 안 함)
)
