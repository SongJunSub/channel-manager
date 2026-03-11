-- 채널 이벤트 테이블 생성
-- 시스템에서 발생하는 모든 변경사항을 이벤트로 기록한다 (이벤트 소싱)
-- Phase 5에서 SSE를 통해 실시간 클라이언트 전달에 사용된다
CREATE TABLE channel_events (
    id             BIGSERIAL   PRIMARY KEY,              -- PK, 자동 증가
    event_type     VARCHAR(30) NOT NULL,                 -- 이벤트 타입 (INVENTORY_UPDATED, RESERVATION_CREATED 등)
    channel_id     BIGINT,                               -- 관련 채널 FK (선택)
    reservation_id BIGINT,                               -- 관련 예약 FK (선택)
    room_type_id   BIGINT,                               -- 관련 객실 타입 FK (선택)
    payload        TEXT,                                  -- 이벤트 상세 데이터 (JSON)
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),   -- 이벤트 발생 시각

    CONSTRAINT fk_channel_events_channels
        FOREIGN KEY (channel_id) REFERENCES channels (id),

    CONSTRAINT fk_channel_events_reservations
        FOREIGN KEY (reservation_id) REFERENCES reservations (id),

    CONSTRAINT fk_channel_events_room_types
        FOREIGN KEY (room_type_id) REFERENCES room_types (id)
);

-- 이벤트 타입별 조회를 위한 인덱스
CREATE INDEX idx_channel_events_event_type ON channel_events (event_type);

-- 시간순 이벤트 조회를 위한 인덱스 (SSE 스트리밍에 활용)
CREATE INDEX idx_channel_events_created_at ON channel_events (created_at);
