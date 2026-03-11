-- 예약 테이블 생성
-- 채널을 통해 접수된 객실 예약 정보를 저장한다
CREATE TABLE reservations (
    id             BIGSERIAL      PRIMARY KEY,              -- PK, 자동 증가
    channel_id     BIGINT         NOT NULL,                 -- 채널 FK
    room_type_id   BIGINT         NOT NULL,                 -- 객실 타입 FK
    check_in_date  DATE           NOT NULL,                 -- 체크인 날짜
    check_out_date DATE           NOT NULL,                 -- 체크아웃 날짜
    guest_name     VARCHAR(100)   NOT NULL,                 -- 투숙객 이름
    room_quantity  INT            NOT NULL DEFAULT 1,       -- 예약 객실 수 (기본 1개)
    status         VARCHAR(20)    NOT NULL,                 -- 예약 상태 (CONFIRMED, CANCELLED)
    total_price    DECIMAL(12, 2),                          -- 총 금액 (선택)
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),   -- 생성 시각
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW(),   -- 수정 시각

    CONSTRAINT fk_reservations_channels
        FOREIGN KEY (channel_id) REFERENCES channels (id),

    CONSTRAINT fk_reservations_room_types
        FOREIGN KEY (room_type_id) REFERENCES room_types (id),

    -- 체크아웃은 체크인 이후여야 한다
    CONSTRAINT chk_reservations_dates
        CHECK (check_out_date > check_in_date)
);

-- 채널별 예약 조회를 위한 인덱스
CREATE INDEX idx_reservations_channel_id ON reservations (channel_id);

-- 객실 타입별 예약 조회를 위한 인덱스
CREATE INDEX idx_reservations_room_type_id ON reservations (room_type_id);

-- 예약 상태별 조회를 위한 인덱스
CREATE INDEX idx_reservations_status ON reservations (status);

-- 날짜 범위 검색을 위한 인덱스
CREATE INDEX idx_reservations_check_in_date ON reservations (check_in_date);
