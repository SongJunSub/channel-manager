package com.channelmanager.java.controller; // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.java.domain.ReservationStatus; // 예약 상태 enum (필터 파라미터)
import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.service.ReservationService; // 예약 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형
import org.springframework.web.bind.annotation.DeleteMapping; // DELETE 메서드 매핑
import org.springframework.web.bind.annotation.GetMapping; // GET 메서드 매핑
import org.springframework.web.bind.annotation.PathVariable; // URL 경로 변수 바인딩
import org.springframework.web.bind.annotation.PostMapping; // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문 바인딩
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터 바인딩
import org.springframework.web.bind.annotation.ResponseStatus; // 응답 상태 코드 지정
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러 선언
import reactor.core.publisher.Flux; // 0~N개 비동기 스트림
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림
import java.time.LocalDate; // 날짜 타입 (필터 파라미터)

// 예약 REST 컨트롤러
// Phase 3: POST(생성), Phase 7: DELETE(취소), Phase 9: GET(조회) 엔드포인트를 제공한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService; // 비즈니스 로직 위임 대상

    // ===== Phase 9: 예약 조회 =====

    // 예약 단건 조회
    // GET /api/reservations/{id}
    // 예약 ID로 단건 예약을 조회하고, 채널 코드를 풍부화하여 반환한다
    // Kotlin에서는 fun getReservation(@PathVariable id: Long)이지만,
    // Java에서는 public Mono<ReservationResponse> getReservation(@PathVariable long id)이다
    @GetMapping("/api/reservations/{id}")
    public Mono<ReservationResponse> getReservation(
            @PathVariable long id) { // URL 경로에서 예약 ID를 추출한다
        return reservationService.getReservation(id);
    }

    // 예약 목록 조회 — 선택적 필터링 + 페이징
    // GET /api/reservations?channelId=1&status=CONFIRMED&startDate=2026-03-15&endDate=2026-03-20&page=0&size=20
    // 모든 필터 파라미터는 선택적(required=false) — 지정하지 않으면 전체 조회
    // Kotlin에서는 nullable 타입(Long?)을 사용하지만,
    // Java에서는 래퍼 타입(Long)을 사용하여 null을 허용한다
    @GetMapping("/api/reservations")
    public Flux<ReservationResponse> listReservations(
            @RequestParam(required = false) Long channelId,           // 채널 ID 필터
            @RequestParam(required = false) ReservationStatus status, // 예약 상태 필터
            @RequestParam(required = false) LocalDate startDate,      // 체크인 시작일
            @RequestParam(required = false) LocalDate endDate,        // 체크인 종료일
            @RequestParam(defaultValue = "0") int page,               // 페이지 번호
            @RequestParam(defaultValue = "20") int size) {            // 페이지 크기
        return reservationService.listReservations(
            channelId, status, startDate, endDate, page, size
        );
    }

    // ===== Phase 3: 예약 생성 =====

    // 예약 생성
    // POST /api/reservations
    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    public Mono<ReservationResponse> createReservation(
            @RequestBody ReservationCreateRequest request) {
        return reservationService.createReservation(request);
    }

    // ===== Phase 7: 예약 취소 =====

    // 예약 취소 — 보상 트랜잭션
    // DELETE /api/reservations/{id}
    @DeleteMapping("/api/reservations/{id}")
    public Mono<ReservationResponse> cancelReservation(
            @PathVariable long id) {
        return reservationService.cancelReservation(id);
    }
}
