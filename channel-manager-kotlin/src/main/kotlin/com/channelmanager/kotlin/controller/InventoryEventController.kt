package com.channelmanager.kotlin.controller // 컨트롤러 패키지

import com.channelmanager.kotlin.dto.InventoryAdjustRequest // 재고 조정 요청 DTO
import com.channelmanager.kotlin.dto.InventoryEventResponse // 이벤트 응답 DTO
import com.channelmanager.kotlin.dto.InventorySnapshotResponse // 스냅샷 응답 DTO
import com.channelmanager.kotlin.service.InventoryCommandService // CQRS Command 서비스
import com.channelmanager.kotlin.service.InventoryQueryService // CQRS Query 서비스
import org.springframework.format.annotation.DateTimeFormat // 날짜 파라미터 파싱
import org.springframework.http.HttpStatus // HTTP 상태 코드
import org.springframework.web.bind.annotation.GetMapping // GET 메서드 매핑
import org.springframework.web.bind.annotation.PathVariable // URL 경로 변수
import org.springframework.web.bind.annotation.PostMapping // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody // 요청 본문 바인딩
import org.springframework.web.bind.annotation.RequestParam // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.ResponseStatus // 응답 상태 코드
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.security.Principal // 인증 사용자 정보
import java.time.LocalDate // 날짜 타입

// CQRS 이벤트 소싱 REST 컨트롤러
// Phase 24: Command(쓰기)와 Query(읽기)를 분리하여 제공한다
// Command: POST /api/inventory-events/adjust — 재고 조정 이벤트 저장
// Query:   GET /api/inventory-events/{roomTypeId} — 이벤트 이력 조회
//          GET /api/inventory-events/{roomTypeId}/snapshot — 이벤트 재생으로 현재 상태 계산
@RestController
class InventoryEventController(
    private val commandService: InventoryCommandService, // 쓰기: 이벤트 저장
    private val queryService: InventoryQueryService      // 읽기: 이벤트 조회/재생
) {

    // ===== Command Side (쓰기) =====

    // 재고 조정 — 이벤트를 생성하여 저장한다
    // POST /api/inventory-events/adjust
    // 요청: { "roomTypeId": 1, "stockDate": "2026-04-18", "delta": -3, "reason": "객실 수리" }
    // 응답: 생성된 이벤트 정보 (201 Created)
    // Principal: Spring Security가 JWT에서 추출한 인증 사용자 정보
    @PostMapping("/api/inventory-events/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    fun adjustInventory(
        @RequestBody request: InventoryAdjustRequest,
        principal: Principal // JWT 인증된 사용자 (SecurityContext에서 자동 주입)
    ): Mono<InventoryEventResponse> =
        commandService.adjustInventory(request, principal.name) // principal.name = JWT의 subject(username)

    // ===== Query Side (읽기) =====

    // 이벤트 이력 조회 — 특정 객실 타입의 전체 이벤트 목록
    // GET /api/inventory-events/{roomTypeId}
    @GetMapping("/api/inventory-events/{roomTypeId}")
    fun getEventHistory(
        @PathVariable roomTypeId: Long
    ): Flux<InventoryEventResponse> =
        queryService.getEventHistory(roomTypeId)

    // 특정 날짜의 이벤트 이력 조회
    // GET /api/inventory-events/{roomTypeId}/events?date=2026-04-18
    @GetMapping("/api/inventory-events/{roomTypeId}/events")
    fun getEventsByDate(
        @PathVariable roomTypeId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): Flux<InventoryEventResponse> =
        queryService.getEventsByDate(roomTypeId, date)

    // 현재 상태 스냅샷 — 이벤트 재생으로 가용 수량을 계산한다
    // GET /api/inventory-events/{roomTypeId}/snapshot?date=2026-04-18
    // 반환: { "roomTypeId": 1, "stockDate": "2026-04-18", "availableQuantity": 5, "eventCount": 4 }
    @GetMapping("/api/inventory-events/{roomTypeId}/snapshot")
    fun getSnapshot(
        @PathVariable roomTypeId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): Mono<InventorySnapshotResponse> =
        queryService.getSnapshot(roomTypeId, date)
}
