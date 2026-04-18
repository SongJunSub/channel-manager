package com.channelmanager.java.service; // 서비스 패키지

import com.channelmanager.java.domain.InventoryEvent; // 재고 이벤트 엔티티
import com.channelmanager.java.dto.InventoryEventDto.InventoryAdjustRequest; // 조정 요청 DTO
import com.channelmanager.java.dto.InventoryEventDto.InventoryEventResponse; // 이벤트 응답 DTO
import com.channelmanager.java.exception.NotFoundException; // 404 예외
import com.channelmanager.java.repository.InventoryEventRepository; // 이벤트 저장소
import com.channelmanager.java.repository.RoomTypeRepository; // 객실 타입 리포지토리
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.slf4j.Logger; // SLF4J 로거
import org.slf4j.LoggerFactory; // SLF4J 로거 팩토리
import org.springframework.stereotype.Service; // 서비스 어노테이션
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.LocalDate; // 날짜 타입

// CQRS Command Service — 재고 이벤트를 생성(쓰기)하는 서비스
// Kotlin에서는 class InventoryCommandService(...)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandService.class);

    private final InventoryEventRepository inventoryEventRepository;
    private final RoomTypeRepository roomTypeRepository;

    // 재고 조정 명령 — 이벤트를 생성하여 저장한다
    public Mono<InventoryEventResponse> adjustInventory(InventoryAdjustRequest request, String username) {
        return roomTypeRepository.findById(request.roomTypeId())
            .switchIfEmpty(Mono.error(
                new NotFoundException("객실 타입을 찾을 수 없습니다. id=" + request.roomTypeId())
            ))
            .flatMap(roomType -> {
                var event = InventoryEvent.builder()
                    .roomTypeId(request.roomTypeId())
                    .stockDate(request.stockDate())
                    .eventType("INVENTORY_ADJUSTED")
                    .delta(request.delta())
                    .reason(request.reason())
                    .createdBy(username)
                    .build();
                return inventoryEventRepository.save(event);
            })
            .doOnNext(saved ->
                log.info("재고 이벤트 저장: roomTypeId={}, date={}, delta={}, by={}",
                    saved.getRoomTypeId(), saved.getStockDate(), saved.getDelta(), saved.getCreatedBy())
            )
            .map(InventoryEventResponse::from);
    }

    // 재고 초기화 이벤트
    public Mono<InventoryEventResponse> initializeInventory(
            Long roomTypeId, LocalDate stockDate, int totalQuantity, String username) {
        var event = InventoryEvent.builder()
            .roomTypeId(roomTypeId)
            .stockDate(stockDate)
            .eventType("INVENTORY_INITIALIZED")
            .delta(totalQuantity)
            .reason("재고 초기화")
            .createdBy(username)
            .build();
        return inventoryEventRepository.save(event)
            .map(InventoryEventResponse::from);
    }
}
