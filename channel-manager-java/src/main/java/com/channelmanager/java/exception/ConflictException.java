package com.channelmanager.java.exception; // 예외 패키지

import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형

// 409 Conflict - 중복 데이터 등 충돌이 발생했을 때 사용
// 예: 동일 (roomTypeId, stockDate) 조합의 재고가 이미 존재
// Kotlin에서는 한 줄로 정의하지만, Java에서는 별도 파일에 클래스를 정의한다
public class ConflictException extends ApiException {

    // 생성자: 예외 메시지를 받아 부모 클래스에 409 상태 코드와 함께 전달한다
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message); // 409 Conflict 고정
    }
}
