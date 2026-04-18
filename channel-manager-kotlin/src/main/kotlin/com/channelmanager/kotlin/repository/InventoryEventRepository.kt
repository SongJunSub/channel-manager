package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.InventoryEvent // 재고 이벤트 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository // Reactive CRUD 리포지토리
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import java.time.LocalDate // 날짜 타입

// 재고 이벤트 리포지토리 — inventory_events 테이블 접근
// Phase 24: 이벤트 소싱의 이벤트 저장소(Event Store) 역할
// 이벤트는 INSERT만 수행하며, UPDATE/DELETE는 하지 않는다 (불변 이벤트 로그)
interface InventoryEventRepository : ReactiveCrudRepository<InventoryEvent, Long> {

    // 특정 객실+날짜의 이벤트를 시간순으로 조회 — 이벤트 재생(replay)에 사용
    // createdAt 기준 오름차순: 가장 오래된 이벤트부터 순서대로 재생해야 정확한 상태를 계산한다
    fun findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(
        roomTypeId: Long,
        stockDate: LocalDate
    ): Flux<InventoryEvent>

    // 특정 객실의 모든 이벤트를 최신순으로 조회 — 이벤트 이력 표시에 사용
    fun findByRoomTypeIdOrderByCreatedAtDesc(
        roomTypeId: Long
    ): Flux<InventoryEvent>
}
