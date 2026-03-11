package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository // 리액티브 CRUD 리포지토리
import reactor.core.publisher.Flux // 0~N개 결과를 비동기로 반환하는 타입
import reactor.core.publisher.Mono // 0~1개 결과를 비동기로 반환하는 타입
import java.time.LocalDate // 날짜 타입

// 재고 리포지토리
interface InventoryRepository : ReactiveCrudRepository<Inventory, Long> {

    // 특정 객실 타입의 날짜 범위 재고 조회 (Phase 2에서 활용)
    fun findByRoomTypeIdAndStockDateBetween(
        roomTypeId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flux<Inventory>

    // 특정 객실 타입 + 특정 날짜의 재고 단건 조회
    fun findByRoomTypeIdAndStockDate(roomTypeId: Long, stockDate: LocalDate): Mono<Inventory>
}
