package com.channelmanager.java.domain; // 도메인 엔티티 패키지

import lombok.AllArgsConstructor; // 모든 필드 생성자
import lombok.Builder; // 빌더 패턴
import lombok.Data; // getter, setter, toString, equals, hashCode
import lombok.NoArgsConstructor; // 기본 생성자
import org.springframework.data.annotation.CreatedDate; // 생성 시각 자동 기록
import org.springframework.data.annotation.Id; // PK 필드 지정
import org.springframework.data.relational.core.mapping.Table; // R2DBC 테이블 매핑
import java.time.LocalDate; // 날짜 타입
import java.time.LocalDateTime; // 날짜+시간 타입

// 재고 이벤트 엔티티 — inventory_events 테이블과 매핑
// Phase 24: 이벤트 소싱 — 재고 변경의 모든 이력을 이벤트로 저장한다
// Kotlin에서는 data class를 사용하지만, Java에서는 Lombok @Data를 사용한다
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("inventory_events")
public class InventoryEvent {

    @Id // PK — null이면 INSERT
    private Long id;

    private Long roomTypeId;           // 대상 객실 타입 ID (FK)

    private LocalDate stockDate;       // 대상 날짜

    private String eventType;          // 이벤트 타입: INVENTORY_INITIALIZED, ADJUSTED, RESERVED, CANCELLED

    private int delta;                 // 수량 변화량 (양수: 증가, 음수: 감소)

    private String reason;             // 변경 사유

    @Builder.Default
    private String createdBy = "SYSTEM"; // 변경 주체

    @CreatedDate // 생성 시각 자동 기록
    private LocalDateTime createdAt;
}
