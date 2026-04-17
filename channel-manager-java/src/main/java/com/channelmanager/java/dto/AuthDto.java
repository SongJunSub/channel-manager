package com.channelmanager.java.dto; // DTO 패키지

// ==============================
// Phase 21: 인증 요청/응답 DTO
// ==============================

// Java에서는 하나의 파일에 여러 public record를 정의할 수 없으므로
// 별도 클래스 안에 중첩 record로 정의한다 (StatisticsResponse 패턴과 동일)
// Kotlin에서는 하나의 파일에 data class를 자유롭게 나열할 수 있다
public class AuthDto {

    // 로그인 + 회원가입 요청 DTO
    // 클라이언트가 보내는 인증 정보를 담는다
    // Java record로 JSON 역직렬화가 자동으로 수행된다
    public record AuthRequest(
        String username,  // 로그인 ID
        String password   // 비밀번호 (원문, BCrypt 해시 전)
    ) {}

    // 인증 응답 DTO — JWT 토큰을 클라이언트에 반환한다
    public record AuthResponse(
        String token,     // JWT 토큰 문자열
        String username,  // 로그인 ID
        String role       // 사용자 역할 (USER, ADMIN)
    ) {}
}
