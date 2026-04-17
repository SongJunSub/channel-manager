package com.channelmanager.java.repository; // 리포지토리 패키지

import com.channelmanager.java.domain.User; // 사용자 엔티티
import org.springframework.data.repository.reactive.ReactiveCrudRepository; // Reactive CRUD 리포지토리
import reactor.core.publisher.Mono; // 0~1개 비동기 스트림

// 사용자 리포지토리 — users 테이블 접근
// Phase 21: 로그인 시 username으로 사용자를 조회한다
// Kotlin에서는 interface UserRepository : ReactiveCrudRepository<User, Long>이지만,
// Java에서는 extends ReactiveCrudRepository<User, Long>이다
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    // username으로 사용자 조회 — 로그인, 회원가입 중복 체크에 사용
    // Spring Data가 메서드명을 파싱하여 자동으로 쿼리를 생성한다:
    //   SELECT * FROM users WHERE username = :username
    Mono<User> findByUsername(String username);
}
