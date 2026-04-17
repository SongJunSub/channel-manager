package com.channelmanager.kotlin.domain // 도메인 엔티티 패키지

import org.springframework.data.annotation.CreatedDate // 생성 시각 자동 기록 어노테이션
import org.springframework.data.annotation.Id // PK 필드 지정 어노테이션
import org.springframework.data.relational.core.mapping.Table // R2DBC 테이블 매핑 어노테이션
import java.time.LocalDateTime // 날짜+시간 타입

// 사용자 엔티티 — users 테이블과 매핑
// Phase 21: Spring Security + JWT 인증을 위한 사용자 정보
// password는 BCrypt로 해시되어 저장된다 (원문 저장 금지)
// role은 USER 또는 ADMIN 중 하나의 값을 가진다
@Table("users")
data class User(
    @Id // PK — null이면 INSERT, 값이 있으면 UPDATE
    val id: Long? = null,

    val username: String,            // 로그인 ID (고유)

    val password: String,            // BCrypt 해시된 비밀번호

    val role: String = "USER",       // 역할: USER 또는 ADMIN

    val enabled: Boolean = true,     // 계정 활성화 여부

    @CreatedDate // 생성 시각 자동 기록 (R2dbcConfig의 @EnableR2dbcAuditing 필요)
    val createdAt: LocalDateTime? = null
)
