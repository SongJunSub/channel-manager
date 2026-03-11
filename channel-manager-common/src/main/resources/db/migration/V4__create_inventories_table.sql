-- 재고 테이블 생성
-- 날짜별/객실 타입별 재고 현황을 관리한다
CREATE TABLE inventories (
    id                 BIGSERIAL PRIMARY KEY,              -- PK, 자동 증가
    room_type_id       BIGINT    NOT NULL,                 -- 객실 타입 FK
    stock_date         DATE      NOT NULL,                 -- 재고 날짜
    total_quantity     INT       NOT NULL,                 -- 전체 객실 수
    available_quantity INT       NOT NULL,                 -- 예약 가능 수량
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),   -- 생성 시각
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),   -- 수정 시각

    CONSTRAINT fk_inventories_room_types
        FOREIGN KEY (room_type_id) REFERENCES room_types (id),

    -- 동일 객실 타입 + 날짜 조합은 유일해야 한다
    CONSTRAINT uq_inventories_room_type_date
        UNIQUE (room_type_id, stock_date)
);

-- 객실 타입별 재고 조회를 위한 인덱스
CREATE INDEX idx_inventories_room_type_id ON inventories (room_type_id);

-- 날짜 범위 조회를 위한 인덱스
CREATE INDEX idx_inventories_stock_date ON inventories (stock_date);
