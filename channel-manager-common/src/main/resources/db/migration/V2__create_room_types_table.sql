-- 객실 타입 테이블 생성
-- 숙소(Property)별 객실 종류를 정의한다 (Standard, Deluxe, Suite 등)
CREATE TABLE room_types (
    id          BIGSERIAL      PRIMARY KEY,                -- PK, 자동 증가
    property_id BIGINT         NOT NULL,                   -- 숙소 FK
    name        VARCHAR(50)    NOT NULL,                   -- 객실 타입명
    capacity    INT            NOT NULL DEFAULT 2,         -- 수용 인원 (기본 2명)
    base_price  DECIMAL(12, 2) NOT NULL,                   -- 기본 가격 (1박 기준)
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),     -- 생성 시각

    CONSTRAINT fk_room_types_properties
        FOREIGN KEY (property_id) REFERENCES properties (id)
);

-- 숙소별 객실 타입 조회를 위한 인덱스
CREATE INDEX idx_room_types_property_id ON room_types (property_id);
