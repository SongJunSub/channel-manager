package com.channelmanager.java.controller; // 컨트롤러 패키지 - REST API 엔드포인트

import com.channelmanager.java.dto.ReservationCreateRequest; // 예약 생성 요청 DTO
import com.channelmanager.java.dto.ReservationResponse; // 예약 응답 DTO
import com.channelmanager.java.service.ReservationService; // 예약 서비스
import lombok.RequiredArgsConstructor; // final 필드 생성자 자동 생성 (Lombok)
import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형
import org.springframework.web.bind.annotation.PostMapping; // POST 메서드 매핑
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문 바인딩
import org.springframework.web.bind.annotation.ResponseStatus; // 응답 상태 코드 지정
import org.springframework.web.bind.annotation.RestController; // REST 컨트롤러 선언
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// 예약 REST 컨트롤러
// 시뮬레이터(ChannelSimulator)와 외부 클라이언트가 호출하는 예약 API를 제공한다
// Phase 3에서는 POST(생성)만 구현하고, 조회/취소는 이후 Phase에서 추가한다
// @RestController: 모든 메서드의 반환값이 자동으로 JSON으로 직렬화된다
// @RequiredArgsConstructor: Lombok이 final 필드에 대한 생성자를 자동 생성한다
// Kotlin에서는 primary constructor에 val로 의존성을 선언하지만,
// Java에서는 @RequiredArgsConstructor + private final 필드로 동일한 효과를 얻는다
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService; // 비즈니스 로직 위임 대상

    // 예약 생성
    // POST /api/reservations
    // 시뮬레이터가 WebClient로 이 엔드포인트를 호출하여 예약을 생성한다
    // 처리 흐름: 채널 검증 → 객실 타입 검증 → 재고 차감 → 예약 저장 → 이벤트 기록
    // @RequestBody: HTTP 요청 본문의 JSON을 ReservationCreateRequest 객체로 역직렬화한다
    // @ResponseStatus(CREATED): 성공 시 201 Created 상태 코드로 응답한다
    // Kotlin과 동일한 로직이지만, Java에서는 return문을 사용한다
    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED) // 201 Created
    public Mono<ReservationResponse> createReservation(
            @RequestBody ReservationCreateRequest request) { // JSON → DTO 자동 변환
        return reservationService.createReservation(request); // Service 호출
    }
}
