package com.channelmanager.kotlin.repository // 리포지토리 패키지

import com.channelmanager.kotlin.domain.User // 사용자 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository // Reactive CRUD 리포지토리
import reactor.core.publisher.Mono // 0~1개 비동기 스트림

// 사용자 리포지토리 — users 테이블 접근
// Phase 21: 로그인 시 username으로 사용자를 조회한다
// ReactiveCrudRepository: Spring Data R2DBC의 기본 CRUD 메서드를 제공한다
interface UserRepository : ReactiveCrudRepository<User, Long> {

    // username으로 사용자 조회 — 로그인, 회원가입 중복 체크에 사용
    // Spring Data가 메서드명을 파싱하여 자동으로 쿼리를 생성한다:
    //   SELECT * FROM users WHERE username = :username
    fun findByUsername(username: String): Mono<User>
}
