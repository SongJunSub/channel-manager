package com.channelmanager.java.exception; // 예외 패키지

import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형

// 404 Not Found - 요청한 리소스를 찾을 수 없을 때 발생
// 예: 존재하지 않는 재고 ID로 조회, 존재하지 않는 객실 타입 ID
// Kotlin에서는 한 줄로 class NotFoundException(message: String) : ApiException(...)로 정의하지만,
// Java에서는 별도 파일에 클래스를 정의해야 한다
public class NotFoundException extends ApiException {

    // 생성자: 예외 메시지를 받아 부모 클래스에 404 상태 코드와 함께 전달한다
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message); // 404 Not Found 고정
    }
}
