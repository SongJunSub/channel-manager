package com.channelmanager.java.controller; // 컨트롤러 패키지

import com.channelmanager.java.dto.InventoryEventDto.InventoryAdjustRequest; // 재고 조정 요청 DTO
import com.channelmanager.java.dto.InventoryEventDto.InventoryEventResponse; // 이벤트 응답 DTO
import com.channelmanager.java.dto.InventoryEventDto.InventorySnapshotResponse; // 스냅샷 응답 DTO
import com.channelmanager.java.service.InventoryCommandService; // CQRS Command 서비스
import com.channelmanager.java.service.InventoryQueryService; // CQRS Query 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자
import org.springframework.format.annotation.DateTimeFormat; // 날짜 파라미터 파싱
import org.springframework.http.HttpStatus; // HTTP 상태 코드
import org.springframework.web.bind.annotation.GetMapping; // GET 매핑
import org.springframework.web.bind.annotation.PathVariable; // 경로 변수
import org.springframework.web.bind.annotation.PostMapping; // POST 매핑
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터
import org.springframework.web.bind.annotation.ResponseStatus; // 응답 상태
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.security.Principal; // 인증 사용자 정보
import java.time.LocalDate; // 날짜 타입

// CQRS 이벤트 소싱 REST 컨트롤러
// Kotlin에서는 class InventoryEventController(...)이지만,
// Java에서는 @RequiredArgsConstructor + private final 필드를 사용한다
@RestController
@RequiredArgsConstructor
public class InventoryEventController {

    private final InventoryCommandService commandService;
    private final InventoryQueryService queryService;

    // ===== Command Side =====

    // 재고 조정 — 이벤트를 생성하여 저장한다
    // POST /api/inventory-events/adjust
    @PostMapping("/api/inventory-events/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<InventoryEventResponse> adjustInventory(
            @RequestBody InventoryAdjustRequest request,
            Principal principal) {
        return commandService.adjustInventory(request, principal.getName());
    }

    // ===== Query Side =====

    // 이벤트 이력 조회
    // GET /api/inventory-events/{roomTypeId}
    @GetMapping("/api/inventory-events/{roomTypeId}")
    public Flux<InventoryEventResponse> getEventHistory(
            @PathVariable Long roomTypeId) {
        return queryService.getEventHistory(roomTypeId);
    }

    // 특정 날짜의 이벤트 이력
    // GET /api/inventory-events/{roomTypeId}/events?date=2026-04-18
    @GetMapping("/api/inventory-events/{roomTypeId}/events")
    public Flux<InventoryEventResponse> getEventsByDate(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.getEventsByDate(roomTypeId, date);
    }

    // 현재 상태 스냅샷 — 이벤트 재생으로 계산
    // GET /api/inventory-events/{roomTypeId}/snapshot?date=2026-04-18
    @GetMapping("/api/inventory-events/{roomTypeId}/snapshot")
    public Mono<InventorySnapshotResponse> getSnapshot(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return queryService.getSnapshot(roomTypeId, date);
    }
}
