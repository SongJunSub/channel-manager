package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록
import org.springframework.data.annotation.Id // PK 필드 지정
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑
import java.time.LocalDate // 날짜 타입
import java.time.LocalDateTime // 날짜+시간 타입

// 재고 이벤트 엔티티 — inventory_events 테이블과 매핑
// Phase 24: 이벤트 소싱 — 재고 변경의 모든 이력을 이벤트로 저장한다
// 현재 상태(가용 수량)는 이 이벤트들의 delta 합계로 계산한다
// 이벤트는 불변(immutable) — 한 번 저장되면 수정/삭제하지 않는다
@Table("inventory_events")
data class InventoryEvent(
    @Id // PK — null이면 INSERT
    val id: Long? = null,

    val roomTypeId: Long,           // 대상 객실 타입 ID (FK)

    val stockDate: LocalDate,       // 대상 날짜 (어느 날짜의 재고에 대한 이벤트인지)

    val eventType: String,          // 이벤트 타입: INVENTORY_INITIALIZED, ADJUSTED, RESERVED, CANCELLED

    val delta: Int,                 // 수량 변화량 (양수: 증가, 음수: 감소)

    val reason: String? = null,     // 변경 사유 (관리자 메모, 예약 ID 등)

    val createdBy: String = "SYSTEM", // 변경 주체 (사용자명 또는 SYSTEM)

    @CreatedDate // 생성 시각 자동 기록
    val createdAt: LocalDateTime? = null
)
