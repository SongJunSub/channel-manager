package com.channelmanager.java.exception; // 예외 패키지

import org.springframework.http.HttpStatus; // HTTP 상태 코드 열거형

// 401 Unauthorized - 인증 실패 (잘못된 사용자명/비밀번호, 만료된 토큰)
// Phase 21: 로그인 실패 시 사용
// Kotlin에서는 class UnauthorizedException(message) : ApiException(UNAUTHORIZED, message)이지만,
// Java에서는 extends ApiException으로 동일한 효과를 얻는다
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
