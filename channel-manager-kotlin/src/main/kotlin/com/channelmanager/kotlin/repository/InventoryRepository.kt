package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.Inventory // 재고 엔티티
import org.springframework.data.r2dbc.repository.Query // R2DBC 커스텀 쿼리 어노테이션
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

    // 비관적 잠금(Pessimistic Lock) 재고 조회 — Phase 4 동시성 제어
    // SELECT ... FOR UPDATE: 조회한 행에 행 잠금(row-level lock)을 건다
    // 다른 트랜잭션이 같은 행을 조회하려 하면 현재 트랜잭션이 완료(커밋/롤백)될 때까지 대기한다
    // 이를 통해 동시에 같은 재고를 차감하는 Lost Update 문제를 방지한다
    // 예: 두 예약이 동시에 roomType=1, date=3/20의 재고(10)를 읽으면
    //     둘 다 9를 저장하여 하나의 차감이 유실되는데, FOR UPDATE가 이를 방지한다
    // @Query: Spring Data R2DBC에서 네이티브 SQL을 직접 작성할 때 사용한다
    //   Spring Data의 메서드 이름 기반 쿼리 생성(Query Derivation)으로는 FOR UPDATE를 표현할 수 없다
    // :roomTypeId, :stockDate — 메서드 파라미터와 이름이 일치하는 바인드 변수
    @Query(
        "SELECT * FROM inventories " +
            "WHERE room_type_id = :roomTypeId AND stock_date = :stockDate " +
            "FOR UPDATE"
    )
    fun findByRoomTypeIdAndStockDateForUpdate(
        roomTypeId: Long,
        stockDate: LocalDate
    ): Mono<Inventory>
}
