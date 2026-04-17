-- Phase 21: 사용자 인증을 위한 users 테이블 생성
-- Spring Security + JWT 인증에 사용되는 사용자 정보를 저장한다
-- 비밀번호는 BCrypt로 해시되어 저장된다 (원문 저장 금지)

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,                          -- 사용자 ID (PK, 자동 증가)
    username    VARCHAR(50)  NOT NULL UNIQUE,                   -- 로그인 ID (고유)
    password    VARCHAR(100) NOT NULL,                          -- BCrypt 해시된 비밀번호
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',           -- 역할 (USER, ADMIN)
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,             -- 계정 활성화 여부
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP -- 생성 시각
);

-- 인덱스: username으로 로그인 시 빠른 조회
CREATE INDEX idx_users_username ON users (username);

-- 샘플 사용자는 V9 SQL에서 직접 삽입하지 않는다
-- BCrypt 해시는 앱에서 BCryptPasswordEncoder로 생성해야 정확하다
-- 대신 앱의 DataInitializer에서 프로그래밍 방식으로 샘플 사용자를 생성한다
