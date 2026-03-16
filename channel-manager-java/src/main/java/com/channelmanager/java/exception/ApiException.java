package com.channelmanager.java.exception; // 예외 패키지

import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형

// API 예외의 기본 클래스
// 모든 비즈니스 예외는 이 클래스를 상속하여 HTTP 상태 코드를 함께 전달한다
// RuntimeException을 상속하므로 체크 예외가 아니다 (Reactive 체인에서 사용 편리)
// Kotlin에서는 open class로 선언하여 상속을 허용하지만,
// Java에서는 클래스가 기본적으로 상속 가능하므로 별도 키워드가 필요 없다
public class ApiException extends RuntimeException {

    private final HttpStatus status; // 이 예외에 대응하는 HTTP 상태 코드

    // 생성자: HTTP 상태 코드와 예외 메시지를 받는다
    // super(message)로 RuntimeException에 메시지를 전달한다
    public ApiException(HttpStatus status, String message) {
        super(message); // 부모 클래스에 메시지 전달
        this.status = status; // HTTP 상태 코드 저장
    }

    // HTTP 상태 코드 getter
    // Kotlin에서는 val status로 선언하면 getter가 자동 생성되지만,
    // Java에서는 명시적으로 getter를 작성해야 한다 (Lombok @Data를 쓰지 않는 이유: 상속 구조)
    public HttpStatus getStatus() {
        return status;
    }
}
