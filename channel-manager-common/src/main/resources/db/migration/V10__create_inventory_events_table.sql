-- Phase 24: 이벤트 소싱을 위한 inventory_events 테이블 생성
-- 재고 변경의 모든 이력을 이벤트로 저장한다 (상태가 아닌 변경 사실을 기록)
-- 현재 상태는 이벤트를 순서대로 재생(replay)하여 계산한다

CREATE TABLE inventory_events (
    id             BIGSERIAL PRIMARY KEY,                          -- 이벤트 ID (PK, 자동 증가)
    room_type_id   BIGINT       NOT NULL REFERENCES room_types(id),-- 대상 객실 타입 (FK)
    stock_date     DATE         NOT NULL,                          -- 대상 날짜
    event_type     VARCHAR(50)  NOT NULL,                          -- 이벤트 타입 (INITIALIZED, ADJUSTED, RESERVED, CANCELLED)
    delta          INT          NOT NULL,                          -- 수량 변화량 (양수: 증가, 음수: 감소)
    reason         VARCHAR(200),                                   -- 변경 사유 (관리자 메모, 예약 ID 등)
    created_by     VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM',         -- 변경 주체 (사용자명 또는 SYSTEM)
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP -- 이벤트 발생 시각
);

-- 인덱스: 특정 객실+날짜의 이벤트를 시간순으로 빠르게 조회 (이벤트 재생용)
CREATE INDEX idx_inventory_events_room_date ON inventory_events (room_type_id, stock_date, created_at);
