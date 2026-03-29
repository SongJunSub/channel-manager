package com.channelmanager.kotlin.controller // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.kotlin.dto.ReservationCreateRequest // 예약 생성 요청 DTO
import com.channelmanager.kotlin.dto.ReservationResponse // 예약 응답 DTO
import com.channelmanager.kotlin.service.ReservationService // 예약 서비스
import org.springframework.http.HttpStatus // HTTP 상태 코드 열거형
import org.springframework.web.bind.annotation.DeleteMapping // DELETE 메서드 매핑
import org.springframework.web.bind.annotation.PathVariable // URL 경로 변수 바인딩
import org.springframework.web.bind.annotation.PostMapping // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody // 요청 본문 바인딩
import org.springframework.web.bind.annotation.ResponseStatus // 응답 상태 코드 지정
import org.springframework.web.bind.annotation.RestController // REST 컨트롤러 선언
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 예약 REST 컨트롤러
// 시뮬레이터(ChannelSimulator)와 외부 클라이언트가 호출하는 예약 API를 제공한다
// Phase 3: POST(생성), Phase 7: DELETE(취소) 엔드포인트를 제공한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
@RestController
class ReservationController(
    private val reservationService: ReservationService // 비즈니스 로직 위임 대상
) {

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

    // 예약 취소 — 보상 트랜잭션
    // DELETE /api/reservations/{id}
    // 예약 상태를 CONFIRMED → CANCELLED로 변경하고, 차감했던 재고를 복구한다
    // 처리 흐름: 예약 조회 → 상태 확인 → 재고 복구 → 상태 변경 → 이벤트 기록
    // @PathVariable: URL 경로의 {id} 값을 메서드 인자로 바인딩한다
    // 성공 시 200 OK + 취소된 예약 정보(status=CANCELLED)를 반환한다
    @DeleteMapping("/api/reservations/{id}")
    fun cancelReservation(
        @PathVariable id: Long // URL 경로에서 예약 ID를 추출한다
    ): Mono<ReservationResponse> =
        reservationService.cancelReservation(id) // Service 호출 → Mono<ReservationResponse>
}
