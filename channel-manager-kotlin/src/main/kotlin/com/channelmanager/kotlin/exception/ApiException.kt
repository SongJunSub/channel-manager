package com.channelmanager.kotlin.exception // 예외 패키지

import org.springframework.http.HttpStatus // HTTP 상태 코드 열거형

// API 예외의 기본 클래스
// 모든 비즈니스 예외는 이 클래스를 상속하여 HTTP 상태 코드를 함께 전달한다
// RuntimeException을 상속하므로 체크 예외가 아니다 (Reactive 체인에서 사용 편리)
// sealed class가 아닌 open class를 사용하여 하위 클래스가 자유롭게 상속할 수 있도록 한다
open class ApiException(
    val status: HttpStatus,   // 이 예외에 대응하는 HTTP 상태 코드
    message: String           // 예외 메시지 (RuntimeException에 전달)
) : RuntimeException(message)

// 404 Not Found - 요청한 리소스를 찾을 수 없을 때 발생
// 예: 존재하지 않는 재고 ID로 조회, 존재하지 않는 객실 타입 ID
class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)

// 400 Bad Request - 요청 데이터가 비즈니스 규칙에 맞지 않을 때 발생
// 예: 가용 수량이 음수, 종료일이 시작일보다 이전
class BadRequestException(message: String) : ApiException(HttpStatus.BAD_REQUEST, message)

// 409 Conflict - 중복 데이터 등 충돌이 발생했을 때 사용
// 예: 동일 (roomTypeId, stockDate) 조합의 재고가 이미 존재
class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)
