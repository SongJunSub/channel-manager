package com.channelmanager.java.exception; // 예외 패키지

import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형

// 400 Bad Request - 요청 데이터가 비즈니스 규칙에 맞지 않을 때 발생
// 예: 가용 수량이 음수, 종료일이 시작일보다 이전
// Kotlin에서는 한 줄로 정의하지만, Java에서는 별도 파일에 클래스를 정의한다
public class BadRequestException extends ApiException {

    // 생성자: 예외 메시지를 받아 부모 클래스에 400 상태 코드와 함께 전달한다
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message); // 400 Bad Request 고정
    }
}
