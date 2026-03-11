-- 개발 및 테스트를 위한 샘플 데이터 삽입
-- 실제 호텔 운영 시나리오를 반영한 초기 데이터

-- ==============================
-- 1. 숙소 데이터
-- ==============================
INSERT INTO properties (property_code, property_name, property_address)
VALUES
    ('SEOUL_GRAND', '서울 그랜드 호텔', '서울특별시 중구 을지로 30'),
    ('BUSAN_OCEAN', '부산 오션 리조트', '부산광역시 해운대구 해운대해변로 100');

-- ==============================
-- 2. 객실 타입 데이터
-- ==============================
-- 서울 그랜드 호텔 (property_id = 1)
INSERT INTO room_types (property_id, room_type_code, room_type_name, max_capacity, base_price)
VALUES
    (1, 'STD', 'Standard', 2, 100000.00),
    (1, 'DLX', 'Deluxe', 3, 150000.00),
    (1, 'STE', 'Suite', 4, 300000.00);

-- 부산 오션 리조트 (property_id = 2)
INSERT INTO room_types (property_id, room_type_code, room_type_name, max_capacity, base_price)
VALUES
    (2, 'STD', 'Standard', 2, 120000.00),
    (2, 'DLX', 'Deluxe', 3, 180000.00);

-- ==============================
-- 3. 판매 채널 데이터
-- ==============================
INSERT INTO channels (channel_code, channel_name, is_active)
VALUES
    ('DIRECT', '자사 홈페이지', TRUE),
    ('OTA_A', '온라인 여행사 A', TRUE),
    ('OTA_B', '온라인 여행사 B', TRUE),
    ('OTA_C', '온라인 여행사 C', FALSE);

-- ==============================
-- 4. 재고 데이터 (2026-03-15 ~ 2026-03-19, 서울 그랜드 호텔 Standard 기준)
-- ==============================
INSERT INTO inventories (room_type_id, stock_date, total_quantity, available_quantity)
VALUES
    (1, '2026-03-15', 10, 8),
    (1, '2026-03-16', 10, 7),
    (1, '2026-03-17', 10, 9),
    (1, '2026-03-18', 10, 10),
    (1, '2026-03-19', 10, 6);

-- ==============================
-- 5. 예약 데이터
-- ==============================
INSERT INTO reservations (channel_id, room_type_id, check_in_date, check_out_date, guest_name, room_quantity, status, total_price)
VALUES
    (1, 1, '2026-03-15', '2026-03-17', '홍길동', 1, 'CONFIRMED', 200000.00),
    (2, 2, '2026-03-16', '2026-03-18', '김철수', 1, 'CONFIRMED', 300000.00),
    (3, 1, '2026-03-15', '2026-03-16', '이영희', 2, 'CANCELLED', 200000.00);

-- ==============================
-- 6. 채널 이벤트 데이터
-- ==============================
INSERT INTO channel_events (event_type, channel_id, reservation_id, room_type_id, event_payload)
VALUES
    ('RESERVATION_CREATED', 1, 1, 1, '{"guestName": "홍길동", "roomQuantity": 1}'),
    ('INVENTORY_UPDATED', NULL, NULL, 1, '{"before": 10, "after": 8}'),
    ('RESERVATION_CREATED', 2, 2, 2, '{"guestName": "김철수", "roomQuantity": 1}'),
    ('RESERVATION_CANCELLED', 3, 3, 1, '{"guestName": "이영희", "reason": "일정 변경"}'),
    ('CHANNEL_SYNCED', 1, NULL, NULL, '{"channel": "DIRECT", "status": "SUCCESS"}');
