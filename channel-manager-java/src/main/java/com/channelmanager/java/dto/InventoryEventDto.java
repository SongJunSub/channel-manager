package com.channelmanager.java.dto; // DTO 패키지

import com.channelmanager.java.domain.InventoryEvent; // 재고 이벤트 엔티티
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// ==============================
// Phase 24: 이벤트 소싱 + CQRS DTO
// ==============================
// Kotlin에서는 하나의 파일에 여러 data class�� 정의하지만,
// Java에서는 하나의 클���스에 중첩 record로 정의한다
public class InventoryEventDto {

    // 재고 조정 요청 DTO (Command)
    public record InventoryAdjustRequest(
        Long roomTypeId,       // 대상 객실 타입 ID
        LocalDate stockDate,   // 대상 날짜
        int delta,             // 수량 변화량
        String reason          // 변경 사유 (nullable)
    ) {}

    // 재고 이벤트 응답 DTO
    public record InventoryEventResponse(
        Long id,
        Long roomTypeId,
        LocalDate stockDate,
        String eventType,
        int delta,
        String reason,
        String createdBy,
        LocalDateTime createdAt
    ) {
        // InventoryEvent 엔티티 → DTO 변환
        public static InventoryEventResponse from(InventoryEvent event) {
            return new InventoryEventResponse(
                event.getId(),
                event.getRoomTypeId(),
                event.getStockDate(),
                event.getEventType(),
                event.getDelta(),
                event.getReason(),
                event.getCreatedBy(),
                event.getCreatedAt()
            );
        }
    }

    // 재고 스냅샷 응답 DTO (Query)
    public record InventorySnapshotResponse(
        Long roomTypeId,
        LocalDate stockDate,
        int availableQuantity,  // 현재 가용 수량 (이벤트 delta 합계)
        int eventCount          // 적용된 이벤트 수
    ) {}
}
