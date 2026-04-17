package com.channelmanager.kotlin.dto // DTO 패키지

// ==============================
// Phase 21: 인증 요청/응답 DTO
// ==============================

// 로그인 + 회원가입 요청 DTO
// 클라이언트가 보내는 인증 정보를 담는다
// data class로 JSON 역직렬화가 자동으로 수행된다
data class AuthRequest(
    val username: String,  // 로그인 ID
    val password: String   // 비밀번호 (원문, BCrypt 해시 전)
)

// 인증 응답 DTO — JWT 토큰을 클라이언트에 반환한다
data class AuthResponse(
    val token: String,     // JWT 토큰 문자열
    val username: String,  // 로그인 ID
    val role: String       // 사용자 역할 (USER, ADMIN)
)
