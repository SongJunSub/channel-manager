package com.channelmanager.kotlin.service // 서비스 패키지

import com.channelmanager.kotlin.domain.InventoryEvent // 재고 이벤트 엔티티
import com.channelmanager.kotlin.dto.InventoryAdjustRequest // 재고 조정 요청 DTO
import com.channelmanager.kotlin.dto.InventoryEventResponse // 재고 이벤트 응답 DTO
import com.channelmanager.kotlin.exception.NotFoundException // 404 예외
import com.channelmanager.kotlin.repository.InventoryEventRepository // 이벤트 저장소
import com.channelmanager.kotlin.repository.RoomTypeRepository // 객실 타입 리포지토리
import org.slf4j.LoggerFactory // SLF4J 로거
import org.springframework.stereotype.Service // 서비스 어노테이션
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// CQRS Command Service — 재고 이벤트를 생성(쓰기)하는 서비스
// Phase 24: 이벤트 소싱의 쓰기 모델을 담당한다
// 현재 상태를 직접 수정하지 않고, 상태 변경의 원인(이벤트)을 저장한다
// 이벤트는 불변(immutable) — INSERT만 수행하며 UPDATE/DELETE는 하지 않는다
@Service
class InventoryCommandService(
    private val inventoryEventRepository: InventoryEventRepository, // 이벤트 저장소
    private val roomTypeRepository: RoomTypeRepository              // 객실 타입 검증용
) {

    companion object {
        private val log = LoggerFactory.getLogger(InventoryCommandService::class.java)
    }

    // 재고 조정 명령 — 이벤트를 생성하여 저장한다
    // CQRS의 Command: 상태를 변경하는 연산
    // 실제 inventories 테이블의 availableQuantity는 변경하지 않는다
    // 대신 inventory_events에 이벤��를 INSERT한다
    fun adjustInventory(request: InventoryAdjustRequest, username: String): Mono<InventoryEventResponse> =
        // 1. 객실 타입 존재 여부 검증
        roomTypeRepository.findById(request.roomTypeId)
            .switchIfEmpty(Mono.error(
                NotFoundException("객실 타입을 찾을 수 없습니다. id=${request.roomTypeId}")
            ))
            .flatMap { roomType ->
                // 2. 이벤트 생성 및 저장
                val event = InventoryEvent(
                    roomTypeId = request.roomTypeId,
                    stockDate = request.stockDate,
                    eventType = "INVENTORY_ADJUSTED", // 관리자 수동 조정
                    delta = request.delta,
                    reason = request.reason,
                    createdBy = username // JWT에서 추출한 사용자명
                )
                inventoryEventRepository.save(event)
            }
            .doOnNext { saved ->
                log.info("재고 이벤트 저장: roomTypeId={}, date={}, delta={}, by={}",
                    saved.roomTypeId, saved.stockDate, saved.delta, saved.createdBy)
            }
            .map { InventoryEventResponse.from(it) } // 엔티티 → DTO 변환

    // 재고 초기화 이벤트 — 특정 객실/날짜에 초기 재고를 설정한다
    // 이벤트 소싱에서 최초 상태는 INITIALIZED 이벤트로 기록한다
    fun initializeInventory(
        roomTypeId: Long,
        stockDate: java.time.LocalDate,
        totalQuantity: Int,
        username: String
    ): Mono<InventoryEventResponse> {
        val event = InventoryEvent(
            roomTypeId = roomTypeId,
            stockDate = stockDate,
            eventType = "INVENTORY_INITIALIZED",
            delta = totalQuantity, // 초기 수량 = delta (양수)
            reason = "재고 초기화",
            createdBy = username
        )
        return inventoryEventRepository.save(event)
            .map { InventoryEventResponse.from(it) }
    }
}
