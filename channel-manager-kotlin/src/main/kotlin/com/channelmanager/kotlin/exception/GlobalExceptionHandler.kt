package com.channelmanager.kotlin.exception // 예외 패키지

import org.springframework.dao.DataIntegrityViolationException // DB 무결성 위반 예외 (UNIQUE 제약 등)
import org.springframework.http.ResponseEntity // HTTP 응답 엔티티 (상태 코드 + 본문)
import org.springframework.web.bind.annotation.ControllerAdvice // 전역 예외 처리기 선언
import org.springframework.web.bind.annotation.ExceptionHandler // 특정 예외 처리 메서드 지정

// 전역 예외 처리기
// @ControllerAdvice는 모든 Controller에서 발생하는 예외를 한 곳에서 처리한다
// WebFlux에서도 Spring MVC와 동일한 @ControllerAdvice를 사용할 수 있다
@ControllerAdvice
class GlobalExceptionHandler {

    // API 에러 응답 DTO
    // 클라이언트에게 일관된 에러 형식을 제공한다
    // data class로 선언하여 JSON 직렬화 시 { "message": "..." } 형태가 된다
    data class ErrorResponse(val message: String)

    // ApiException 처리 - NotFoundException, BadRequestException, ConflictException 등
    // ApiException을 상속한 모든 예외가 이 핸들러로 전달된다
    // 각 예외가 가진 status(HTTP 상태 코드)와 message를 그대로 응답에 사용한다
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity // ResponseEntity 빌더를 사용하여 상태 코드와 본문을 설정한다
            .status(e.status) // 예외에 지정된 HTTP 상태 코드 (404, 400, 409 등)
            .body(ErrorResponse(e.message ?: "알 수 없는 오류")) // 예외 메시지를 응답 본문에 담는다

    // DB 무결성 위반 예외 처리 - UNIQUE 제약 위반 등
    // 예: 동일 (room_type_id, stock_date) 조합으로 재고를 중복 생성하려 할 때 발생
    // DataIntegrityViolationException은 Spring Data가 DB 예외를 변환한 것이다
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        e: DataIntegrityViolationException
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity // 409 Conflict로 응답한다
            .status(409) // 데이터 충돌을 나타내는 HTTP 상태 코드
            .body(ErrorResponse("데이터 무결성 위반: 중복된 데이터이거나 참조 관계 오류입니다"))
}
