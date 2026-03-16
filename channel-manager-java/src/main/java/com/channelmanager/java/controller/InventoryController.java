package com.channelmanager.java.controller; // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.java.dto.InventoryBulkCreateRequest; // 기간별 일괄 생성 요청 DTO
import com.channelmanager.java.dto.InventoryCreateRequest; // 단건 생성 요청 DTO
import com.channelmanager.java.dto.InventoryResponse; // 응답 DTO
import com.channelmanager.java.dto.InventoryUpdateRequest; // 수정 요청 DTO
import com.channelmanager.java.service.InventoryService; // 인벤토리 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형
import org.springframework.http.ResponseEntity; // HTTP 응답 엔티티 (상태 코드 + 본문)
import org.springframework.web.bind.annotation.DeleteMapping; // DELETE 메서드 매핑
import org.springframework.web.bind.annotation.GetMapping; // GET 메서드 매핑
import org.springframework.web.bind.annotation.PathVariable; // URL 경로 변수 바인딩
import org.springframework.web.bind.annotation.PostMapping; // POST 메서드 매핑
import org.springframework.web.bind.annotation.PutMapping; // PUT 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문 바인딩
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.ResponseStatus; // 응답 상태 코드 지정
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러 선언
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.LocalDate; // 날짜 타입

// 인벤토리 REST 컨트롤러
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 의존성을 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService; // 비즈니스 로직 위임 대상

    // ===== 조회 API =====

    // 재고 단건 조회
    // GET /api/inventories/{id}
    // @PathVariable: URL의 {id} 부분을 Long 타입 파라미터로 바인딩한다
    // Mono<ResponseEntity<InventoryResponse>>를 반환하여 상태 코드를 세밀하게 제어한다
    // map(ResponseEntity::ok): 조회 성공 시 200 OK + 본문으로 응답한다
    // Kotlin에서는 단일 표현식 함수(=)를 사용하지만, Java에서는 return문을 사용한다
    @GetMapping("/api/inventories/{id}")
    public Mono<ResponseEntity<InventoryResponse>> getInventory(
            @PathVariable Long id) {
        return inventoryService.getInventory(id) // Service 호출 → Mono<InventoryResponse>
            .map(ResponseEntity::ok); // 200 OK로 래핑 (메서드 레퍼런스)
    }

    // 기간별 재고 목록 조회
    // GET /api/inventories?roomTypeId=1&startDate=2026-03-15&endDate=2026-03-19
    // @RequestParam: 쿼리 파라미터를 메서드 파라미터에 바인딩한다
    // Flux<InventoryResponse>를 반환하면 WebFlux가 자동으로 JSON 배열로 직렬화한다
    // Spring은 LocalDate를 ISO 형식(yyyy-MM-dd) 문자열에서 자동 변환한다
    @GetMapping("/api/inventories")
    public Flux<InventoryResponse> getInventories(
            @RequestParam Long roomTypeId,     // 객실 타입 ID (필수)
            @RequestParam LocalDate startDate, // 시작 날짜 (필수, ISO 형식)
            @RequestParam LocalDate endDate) { // 종료 날짜 (필수, ISO 형식)
        return inventoryService.getInventories(roomTypeId, startDate, endDate); // Service 호출 → Flux
    }

    // ===== 생성 API =====

    // 재고 단건 생성
    // POST /api/inventories
    // @RequestBody: HTTP 요청 본문의 JSON을 InventoryCreateRequest 객체로 역직렬화한다
    // @ResponseStatus(CREATED): 성공 시 201 Created 상태 코드로 응답한다
    // Kotlin에서는 @RequestBody request: InventoryCreateRequest로 선언하지만,
    // Java에서는 타입이 파라미터 앞에 온다
    @PostMapping("/api/inventories")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    public Mono<InventoryResponse> createInventory(
            @RequestBody InventoryCreateRequest request) { // JSON → DTO 자동 변환
        return inventoryService.createInventory(request); // Service 호출 → Mono<InventoryResponse>
    }

    // 재고 기간별 일괄 생성
    // POST /api/inventories/bulk
    // startDate ~ endDate 범위의 모든 날짜에 대해 동일한 수량으로 재고를 일괄 생성한다
    // @ResponseStatus(CREATED): 성공 시 201 Created로 응답한다
    // Flux<InventoryResponse>를 반환하여 생성된 모든 재고를 JSON 배열로 응답한다
    @PostMapping("/api/inventories/bulk")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    public Flux<InventoryResponse> createInventoryBulk(
            @RequestBody InventoryBulkCreateRequest request) { // JSON → DTO 자동 변환
        return inventoryService.createInventoryBulk(request); // Service 호출 → Flux
    }

    // ===== 수정 API =====

    // 재고 수정 (Partial Update)
    // PUT /api/inventories/{id}
    // @PathVariable: URL의 {id}를 바인딩하고, @RequestBody로 수정할 내용을 받는다
    // Mono<ResponseEntity<InventoryResponse>>를 반환하여 200 OK + 수정된 데이터를 응답한다
    @PutMapping("/api/inventories/{id}")
    public Mono<ResponseEntity<InventoryResponse>> updateInventory(
            @PathVariable Long id,                       // 수정할 재고 ID
            @RequestBody InventoryUpdateRequest request) { // 수정할 내용
        return inventoryService.updateInventory(id, request) // Service 호출
            .map(ResponseEntity::ok); // 200 OK로 래핑
    }

    // ===== 삭제 API =====

    // 재고 삭제
    // DELETE /api/inventories/{id}
    // Mono<ResponseEntity<Void>>를 반환하여 204 No Content로 응답한다
    // then: 삭제 작업(Mono<Void>) 완료 후 204 응답을 생성한다
    // Kotlin과 동일한 패턴이지만, Java에서는 제네릭 타입을 명시해야 한다
    @DeleteMapping("/api/inventories/{id}")
    public Mono<ResponseEntity<Void>> deleteInventory(@PathVariable Long id) {
        return inventoryService.deleteInventory(id) // Service 호출 → Mono<Void>
            .then(Mono.just(ResponseEntity.noContent().build())); // 삭제 완료 후 204 응답
    }
}
