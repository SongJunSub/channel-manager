package com.channelmanager.java.dto; // DTO 패키지

import com.channelmanager.java.domain.Inventory; // 재고 엔티티
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// 재고 응답 DTO
// 엔티티를 직접 반환하지 않고 DTO로 변환하여 API 스펙과 DB 스키마를 분리한다
// record를 사용하여 불변 응답 객체를 정의한다 (Kotlin의 data class와 유사)
// Kotlin에서는 companion object에 from() 팩토리 메서드를 정의하지만,
// Java record에서는 static 메서드로 정의한다
public record InventoryResponse(
    Long id,                      // 재고 ID (PK)
    Long roomTypeId,              // 객실 타입 ID (FK)
    LocalDate stockDate,          // 재고 날짜
    int totalQuantity,            // 전체 객실 수량
    int availableQuantity,        // 예약 가능 수량
    LocalDateTime updatedAt       // 마지막 수정 시각 (null이면 아직 수정된 적 없음)
) {

    // Inventory 엔티티를 InventoryResponse DTO로 변환하는 정적 팩토리 메서드
    // Kotlin의 companion object { fun from() }에 대응하는 Java 패턴이다
    // 엔티티 → DTO 변환 로직을 한 곳에 집중시켜 유지보수성을 높인다
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
            inventory.getId(),                // PK
            inventory.getRoomTypeId(),        // 객실 타입 ID
            inventory.getStockDate(),         // 재고 날짜
            inventory.getTotalQuantity(),     // 전체 수량
            inventory.getAvailableQuantity(), // 가용 수량
            inventory.getUpdatedAt()          // 수정 시각
        );
    }
}
