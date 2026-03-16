package com.channelmanager.java.dto; // DTO 패키지

// 재고 수정 요청 DTO
// null인 필드는 수정하지 않는다 (Partial Update 패턴)
// 예: totalQuantity만 수정하고 availableQuantity는 그대로 유지
// record의 필드에 Integer(래퍼 타입)를 사용하여 null 허용을 표현한다
// int(원시 타입)는 null이 될 수 없으므로 Integer를 사용해야 한다
// Kotlin에서는 Int?로 nullable을 표현하지만, Java에서는 래퍼 타입으로 표현한다
public record InventoryUpdateRequest(
    Integer totalQuantity,       // 전체 수량 (null이면 수정 안 함)
    Integer availableQuantity    // 가용 수량 (null이면 수정 안 함)
) {}
