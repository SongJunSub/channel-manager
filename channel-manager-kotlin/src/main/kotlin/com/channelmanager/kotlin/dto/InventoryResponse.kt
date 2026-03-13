package com.channelmanager.kotlin.dto // DTO 패키지

import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 재고 응답 DTO
// 엔티티를 직접 반환하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
// 클라이언트에게 필요한 필드만 노출하고, 내부 구조 변경이 API에 영향을 주지 않도록 한다
data class InventoryResponse(
    val id: Long,                      // 재고 ID (PK)
    val roomTypeId: Long,              // 객실 타입 ID (FK)
    val stockDate: LocalDate,          // 재고 날짜
    val totalQuantity: Int,            // 전체 객실 수량
    val availableQuantity: Int,        // 예약 가능 수량
    val updatedAt: LocalDateTime?      // 마지막 수정 시각 (null이면 아직 수정된 적 없음)
) {
    companion object {
        // Inventory 엔티티를 InventoryResponse DTO로 변환하는 팩토리 메서드
        // companion object 안에 정의하여 InventoryResponse.from(inventory) 형태로 호출한다
        // 엔티티 → DTO 변환 로직을 한 곳에 집중시켜 유지보수성을 높인다
        fun from(inventory: Inventory): InventoryResponse = InventoryResponse(
            id = inventory.id!!,                             // PK는 저장 후 항상 존재하므로 !! 사용
            roomTypeId = inventory.roomTypeId,               // 객실 타입 ID
            stockDate = inventory.stockDate,                 // 재고 날짜
            totalQuantity = inventory.totalQuantity,         // 전체 수량
            availableQuantity = inventory.availableQuantity, // 가용 수량
            updatedAt = inventory.updatedAt                  // 수정 시각
        )
    }
}
