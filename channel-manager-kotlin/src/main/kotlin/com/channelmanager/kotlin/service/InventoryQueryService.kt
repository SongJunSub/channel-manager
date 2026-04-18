package com.channelmanager.kotlin.service // 서비스 패키지

import com.channelmanager.kotlin.dto.InventoryEventResponse // 이벤트 응답 DTO
import com.channelmanager.kotlin.dto.InventorySnapshotResponse // 스냅샷 응답 DTO
import com.channelmanager.kotlin.repository.InventoryEventRepository // 이벤트 저장소
import org.springframework.stereotype.Service // 서비스 어노테이션
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.LocalDate // 날짜 타입

// CQRS Query Service — 이벤��를 조회하고 현재 상태를 계산(읽기)하는 서비스
// Phase 24: 이벤트 소싱의 읽기 모델을 담당한다
// 현재 상태는 이벤트를 순서대로 재생(replay)하여 계산한다
// DB에서 "현재 가용 수량"을 직접 읽는 것이 아니라,
// 해당 날짜의 모든 이벤트의 delta를 합산하여 계산한다
@Service
class InventoryQueryService(
    private val inventoryEventRepository: InventoryEventRepository // 이벤트 저장소
) {

    // 이벤��� 이력 조회 — 특정 객실 타입의 전체 이벤트 목록
    // 최신순으로 반환하여 가장 최근 변경부터 확인할 수 있다
    fun getEventHistory(roomTypeId: Long): Flux<InventoryEventResponse> =
        inventoryEventRepository.findByRoomTypeIdOrderByCreatedAtDesc(roomTypeId)
            .map { InventoryEventResponse.from(it) }

    // 특정 날짜의 이벤트 이력 조회
    fun getEventsByDate(roomTypeId: Long, stockDate: LocalDate): Flux<InventoryEventResponse> =
        inventoryEventRepository
            .findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(roomTypeId, stockDate)
            .map { InventoryEventResponse.from(it) }

    // 현재 상태 스냅샷 ��� 이벤트 재생(Event Replay)으로 가용 수량을 계산한다
    // 핵심 연산자: reduce (이벤트의 delta를 순서대로 누적 합산)
    // 흐름:
    //   1. 해당 객실/날짜의 모든 이벤트를 시간순으��� 조회
    //   2. 각 이벤트의 delta를 순서대로 합산 (reduce)
    //   3. 합산 결과가 현재 가용 수량
    // 예: INITIALIZED(+10) → RESERVED(-2) → CANCELLED(+1) = 9
    fun getSnapshot(roomTypeId: Long, stockDate: LocalDate): Mono<InventorySnapshotResponse> =
        inventoryEventRepository
            .findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(roomTypeId, stockDate)
            .collectList() // Flux<InventoryEvent> → Mono<List<InventoryEvent>>
            .map { events ->
                // 이벤트 재생: 모든 delta의 합계가 현재 가용 수량
                // fold: 초기값(0)부터 시작하여 각 이벤트의 delta를 누적 합산
                val availableQuantity = events.fold(0) { acc, event -> acc + event.delta }
                InventorySnapshotResponse(
                    roomTypeId = roomTypeId,
                    stockDate = stockDate,
                    availableQuantity = availableQuantity,
                    eventCount = events.size
                )
            }
}
