package com.channelmanager.kotlin.dto // DTO 패키지

import com.channelmanager.kotlin.domain.InventoryEvent // 재고 이벤트 엔티티
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// ==============================
// Phase 24: 이벤트 소싱 + CQRS DTO
// ==============================

// 재�� 조정 요청 DTO (Command)
// POST /api/inventory-events/adjust 에서 사용
// 관리자가 특정 객실/날짜의 재고를 수동 조정할 때 사용한다
data class InventoryAdjustRequest(
    val roomTypeId: Long,       // 대상 객실 타입 ID
    val stockDate: LocalDate,   // 대상 날짜
    val delta: Int,             // 수량 변화량 (양수: 증가, 음수: 감소)
    val reason: String? = null  // 변경 사유 (선택)
)

// 재고 이벤트 응답 DTO
// 이벤트 이력 조회 시 사용
data class InventoryEventResponse(
    val id: Long,
    val roomTypeId: Long,
    val stockDate: LocalDate,
    val eventType: String,
    val delta: Int,
    val reason: String?,
    val createdBy: String,
    val createdAt: LocalDateTime?
) {
    companion object {
        // InventoryEvent 엔티티 → DTO 변환
        fun from(event: InventoryEvent): InventoryEventResponse = InventoryEventResponse(
            id = requireNotNull(event.id) { "이벤트가 저장되기 전에는 DTO로 변환할 수 없습니다" },
            roomTypeId = event.roomTypeId,
            stockDate = event.stockDate,
            eventType = event.eventType,
            delta = event.delta,
            reason = event.reason,
            createdBy = event.createdBy,
            createdAt = event.createdAt
        )
    }
}

// 재고 스냅샷 응답 DTO (Query)
// 이벤트 재생으로 계산된 현재 상태
data class InventorySnapshotResponse(
    val roomTypeId: Long,       // 객실 타입 ID
    val stockDate: LocalDate,   // 날짜
    val availableQuantity: Int,  // 현재 가용 수량 (이벤트 delta 합계)
    val eventCount: Int          // 적용된 이벤트 수
)
