package com.channelmanager.java.dto; // DTO 패키지

import java.time.LocalDate; // 날짜 타입

// 재고 기간별 일괄 생성 요청 DTO
// 시작일 ~ 종료일 범위의 모든 날짜에 대해 동일한 수량으로 재고를 일괄 생성한다
// 예: 3월 15일~31일까지 Deluxe Twin 10실 → 17건의 재고 레코드 생성
// Kotlin의 InventoryBulkCreateRequest data class와 동일한 역할을 한다
public record InventoryBulkCreateRequest(
    Long roomTypeId,      // 재고를 생성할 객실 타입 ID (FK)
    LocalDate startDate,  // 시작 날짜 (포함)
    LocalDate endDate,    // 종료 날짜 (포함)
    int totalQuantity     // 각 날짜에 설정할 전체 객실 수량
) {}
