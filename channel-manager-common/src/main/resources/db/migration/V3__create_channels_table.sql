-- 판매 채널 테이블 생성
-- 객실을 판매하는 경로를 정의한다 (DIRECT, OTA_A, OTA_B 등)
CREATE TABLE channels (
    id         BIGSERIAL    PRIMARY KEY,              -- PK, 자동 증가
    code       VARCHAR(20)  NOT NULL UNIQUE,           -- 채널 고유 코드
    name       VARCHAR(50)  NOT NULL,                  -- 채널 표시 이름
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,     -- 활성 상태
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()     -- 생성 시각
);

-- 채널 코드 조회를 위한 인덱스 (UNIQUE 제약조건이 인덱스 역할을 하지만 명시적으로 생성)
CREATE INDEX idx_channels_code ON channels (code);
