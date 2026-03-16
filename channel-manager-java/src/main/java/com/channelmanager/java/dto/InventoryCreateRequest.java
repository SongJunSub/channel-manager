package com.channelmanager.java.dto; // DTO 패키지 - 요청/응답 데이터 전송 객체

import java.time.LocalDate; // 날짜 타입 (시간 없이 날짜만)

// 재고 단건 생성 요청 DTO
// 특정 객실 타입의 특정 날짜에 대한 재고를 1건 생성한다
// Java record를 사용하여 불변 DTO를 간결하게 정의한다
// record는 생성자, getter, equals, hashCode, toString을 자동 생성한다
// Kotlin의 data class와 유사하지만, 필드가 완전히 불변(final)이다
public record InventoryCreateRequest(
    Long roomTypeId,      // 재고를 생성할 객실 타입 ID (FK)
    LocalDate stockDate,  // 재고 날짜 (예: 2026-03-15)
    int totalQuantity     // 전체 객실 수량 (예: 10)
) {}
