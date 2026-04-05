package com.channelmanager.kotlin.controller // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.kotlin.domain.ReservationStatus // 예약 상태 enum (필터 파라미터)
import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import com.channelmanager.kotlin.service.ReservationService // 예약 서비스
import org.springframework.http.HttpStatus // HTTP 상태 코드 열거형
import org.springframework.web.bind.annotation.DeleteMapping // DELETE 메서드 매핑
import org.springframework.web.bind.annotation.GetMapping // GET 메서드 매핑
import org.springframework.web.bind.annotation.PathVariable // URL 경로 변수 바인딩
import org.springframework.web.bind.annotation.PostMapping // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody // 요청 본문 바인딩
import org.springframework.web.bind.annotation.RequestParam // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.ResponseStatus // 응답 상태 코드 지정
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러 선언
import reactor.core.publisher.Flux // 0~N개 비동기 스트림
import reactor.core.publisher.Mono // 0~1개 비동기 스트림
import java.time.LocalDate // 날짜 타입 (필터 파라미터)

// 예약 REST 컨트롤러
// Phase 3: POST(생성), Phase 7: DELETE(취소), Phase 9: GET(조회) 엔드포인트를 제공한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
@RestController
class ReservationController(
    private val reservationService: ReservationService // 비즈니스 로직 위임 대상
) {

    // ===== Phase 9: 예약 조회 =====

    // 예약 단건 조회
    // GET /api/reservations/{id}
    // 예약 ID로 단건 예약을 조회하고, 채널 코드를 풍부화하여 반환한다
    // @PathVariable: URL 경로의 {id} 값을 메서드 인자로 바인딩한다
    @GetMapping("/api/reservations/{id}")
    fun getReservation(
        @PathVariable id: Long // URL 경로에서 예약 ID를 추출한다
    ): Mono<ReservationResponse> =
        reservationService.getReservation(id)

    // 예약 목록 조회 — 선택적 필터링 + 페이징
    // GET /api/reservations?channelId=1&status=CONFIRMED&startDate=2026-03-15&endDate=2026-03-20&page=0&size=20
    // 모든 필터 파라미터는 선택적(required=false) — 지정하지 않으면 전체 조회
    // @RequestParam(required = false): 파라미터가 없으면 null (해당 조건 무시)
    // @RequestParam(defaultValue = "0"): 파라미터가 없으면 기본값 사용
    @GetMapping("/api/reservations")
    fun listReservations(
        @RequestParam(required = false) channelId: Long?,        // 채널 ID 필터
        @RequestParam(required = false) status: ReservationStatus?, // 예약 상태 필터
        @RequestParam(required = false) startDate: LocalDate?,    // 체크인 시작일
        @RequestParam(required = false) endDate: LocalDate?,      // 체크인 종료일
        @RequestParam(defaultValue = "0") page: Int,              // 페이지 번호
        @RequestParam(defaultValue = "20") size: Int              // 페이지 크기
    ): Flux<ReservationResponse> =
        reservationService.listReservations(channelId, status, startDate, endDate, page, size)

    // ===== Phase 3: 예약 생성 =====

    // 예약 생성
    // POST /api/reservations
    // 시뮬레이터가 WebClient로 이 엔드포인트를 호출하여 예약을 생성한다
    // 처리 흐름: 채널 검증 → 객실 타입 검증 → 재고 차감 → 예약 저장 → 이벤트 기록
    // @RequestBody: HTTP 요청 본문의 JSON을 ReservationCreateRequest 객체로 역직렬화한다
    // @ResponseStatus(CREATED): 성공 시 201 Created 상태 코드로 응답한다
    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    fun createReservation(
        @RequestBody request: ReservationCreateRequest // JSON → DTO 자동 변환
    ): Mono<ReservationResponse> =
        reservationService.createReservation(request) // Service 호출 → Mono<ReservationResponse>

    // ===== Phase 7: 예약 취소 =====

    // 예약 취소 — 보상 트랜잭션
    // DELETE /api/reservations/{id}
    // 예약 상태를 CONFIRMED → CANCELLED로 변경하고, 차감했던 재고를 복구한다
    // 처리 흐름: 예약 조회 → 상태 확인 → 재고 복구 → 상태 변경 → 이벤트 기록
    @DeleteMapping("/api/reservations/{id}")
    fun cancelReservation(
        @PathVariable id: Long // URL 경로에서 예약 ID를 추출한다
    ): Mono<ReservationResponse> =
        reservationService.cancelReservation(id) // Service 호출 → Mono<ReservationResponse>
}
