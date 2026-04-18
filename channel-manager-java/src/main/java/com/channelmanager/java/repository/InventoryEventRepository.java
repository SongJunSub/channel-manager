package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.InventoryEvent; // 재고 이벤트 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // Reactive CRUD 리포지토리
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import java.time.LocalDate; // 날짜 타입

// 재고 이벤트 리포지토리 — inventory_events 테이블 접근
// Phase 24: 이벤트 소싱의 이벤트 저장소(Event Store) 역할
// Kotlin에서��� interface ... : ReactiveCrudRepository이지만,
// Java에서는 extends ReactiveCrudRepository이다
public interface InventoryEventRepository extends ReactiveCrudRepository<InventoryEvent, Long> {

    // 특정 객실+날짜의 이벤트를 시간순으로 조회 — 이벤트 재생(replay)에 사용
    Flux<InventoryEvent> findByRoomTypeIdAndStockDateOrderByCreatedAtAsc(
        Long roomTypeId, LocalDate stockDate);

    // 특정 객실의 모든 이벤트를 최신순으로 조회 — 이벤트 이력 표시에 사용
    Flux<InventoryEvent> findByRoomTypeIdOrderByCreatedAtDesc(Long roomTypeId);
}
