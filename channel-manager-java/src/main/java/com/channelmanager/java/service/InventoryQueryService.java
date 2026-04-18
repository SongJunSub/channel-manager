package com.channelmanager.java.service; // 서비스 패키지

import com.channelmanager.java.dto.InventoryEventDto.InventoryEventResponse; // 이벤트 응답 DTO
import com.channelmanager.java.dto.InventoryEventDto.InventorySnapshotResponse; // 스냅샷 응답 DTO
import com.channelmanager.java.repository.InventoryEventRepository; // 이벤트 저장소
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.springframework.stereotype.Service; // 서비스 어노테이션
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.LocalDate; // 날짜 타입

// CQRS Query Service — 이벤트를 조회하고 현재 상태를 계산(읽기)하는 서비스
// Kotlin에서는 class InventoryQueryService(...)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryEventRepository inventoryEventRepository;

    // 이벤트 이력 조회
    public Flux<InventoryEventResponse> getEventHistory(Long roomTypeId) {
        return inventoryEventRepository.findByRoomTypeIdOrderByCreatedAtDesc(roomTypeId)
            .map(InventoryEventResponse::from);
    }

    // 특정 날짜의 이벤트 이력
    public Flux<InventoryEventResponse> getEventsByDate(Long roomTypeId, LocalDate stockDate) {
        return inventoryEventRepository
            .findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(roomTypeId, stockDate)
            .map(InventoryEventResponse::from);
    }

    // 현재 상태 스냅샷 — 이벤트 재생으로 계산
    // Kotlin에서는 events.fold(0) { acc, event -> acc + event.delta }이지만,
    // Java에서는 stream().mapToInt().sum()을 사용한다
    public Mono<InventorySnapshotResponse> getSnapshot(Long roomTypeId, LocalDate stockDate) {
        return inventoryEventRepository
            .findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(roomTypeId, stockDate)
            .collectList()
            .map(events -> {
                // 이벤트 재생: 모든 delta의 합계가 현재 가용 수량
                int availableQuantity = events.stream()
                    .mapToInt(e -> e.getDelta())
                    .sum();
                return new InventorySnapshotResponse(
                    roomTypeId, stockDate, availableQuantity, events.size()
                );
            });
    }
}
