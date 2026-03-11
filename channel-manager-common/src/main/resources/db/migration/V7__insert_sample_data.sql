-- 개발 및 테스트를 위한 샘플 데이터 삽입
-- 실제 호텔 운영 시나리오를 반영한 현실적인 초기 데이터

-- ==============================
-- 1. 숙소 데이터
-- ==============================
-- 실제 한국 주요 호텔을 모티브로 한 데이터
INSERT INTO properties (property_code, property_name, property_address)
VALUES
    ('SHILLA_SEOUL', '서울신라호텔', '서울특별시 중구 동호로 249'),
    ('PARADISE_BUSAN', '파라다이스 호텔 부산', '부산광역시 해운대구 해운대해변로 296');

-- ==============================
-- 2. 객실 타입 데이터
-- ==============================
-- 서울신라호텔 (property_id = 1) - 실제 호텔 객실 등급 참고
INSERT INTO room_types (property_id, room_type_code, room_type_name, max_capacity, base_price)
VALUES
    (1, 'STD', 'Superior Double', 2, 350000.00),
    (1, 'DLX', 'Deluxe Twin', 3, 480000.00),
    (1, 'STE', 'Executive Suite', 4, 980000.00);

-- 파라다이스 호텔 부산 (property_id = 2)
INSERT INTO room_types (property_id, room_type_code, room_type_name, max_capacity, base_price)
VALUES
    (2, 'STD', 'Standard Ocean View', 2, 280000.00),
    (2, 'DLX', 'Deluxe Ocean Front', 3, 420000.00);

-- ==============================
-- 3. 판매 채널 데이터
-- ==============================
-- 실제 글로벌 OTA 플랫폼 기반 채널 구성
INSERT INTO channels (channel_code, channel_name, is_active)
VALUES
    ('DIRECT', '자사 홈페이지', TRUE),
    ('BOOKING', 'Booking.com', TRUE),
    ('AGODA', 'Agoda', TRUE),
    ('TRIP', 'Trip.com', FALSE);

-- ==============================
-- 4. 재고 데이터
-- ==============================
-- 서울신라호텔 Superior Double (room_type_id = 1) - 5일치 재고
INSERT INTO inventories (room_type_id, stock_date, total_quantity, available_quantity)
VALUES
    (1, '2026-03-15', 15, 12),
    (1, '2026-03-16', 15, 10),
    (1, '2026-03-17', 15, 14),
    (1, '2026-03-18', 15, 15),
    (1, '2026-03-19', 15, 8);

-- 서울신라호텔 Deluxe Twin (room_type_id = 2) - 주말 재고 타이트
INSERT INTO inventories (room_type_id, stock_date, total_quantity, available_quantity)
VALUES
    (2, '2026-03-15', 10, 7),
    (2, '2026-03-16', 10, 3),
    (2, '2026-03-17', 10, 9);

-- ==============================
-- 5. 예약 데이터
-- ==============================
-- 다양한 채널을 통한 실제 예약 시나리오
INSERT INTO reservations (channel_id, room_type_id, check_in_date, check_out_date, guest_name, room_quantity, status, total_price)
VALUES
    -- 자사 홈페이지를 통한 2박 예약 (Superior Double 350,000원 × 2박 = 700,000원)
    (1, 1, '2026-03-15', '2026-03-17', '김민준', 1, 'CONFIRMED', 700000.00),
    -- Booking.com을 통한 2박 예약 (Deluxe Twin 480,000원 × 2박 = 960,000원)
    (2, 2, '2026-03-16', '2026-03-18', 'James Wilson', 1, 'CONFIRMED', 960000.00),
    -- Agoda를 통한 1박 예약 후 취소 (Superior Double 350,000원 × 1박 × 2실 = 700,000원)
    (3, 1, '2026-03-15', '2026-03-16', '田中太郎', 2, 'CANCELLED', 700000.00);

-- ==============================
-- 6. 채널 이벤트 데이터
-- ==============================
-- 실제 운영 중 발생하는 이벤트 흐름을 시간순으로 기록
INSERT INTO channel_events (event_type, channel_id, reservation_id, room_type_id, event_payload)
VALUES
    -- 자사 홈페이지 예약 생성 → 재고 차감 → 타 채널 동기화
    ('RESERVATION_CREATED', 1, 1, 1, '{"guestName": "김민준", "roomQuantity": 1, "channel": "DIRECT"}'),
    ('INVENTORY_UPDATED', NULL, NULL, 1, '{"stockDate": "2026-03-15", "before": 15, "after": 12}'),
    ('CHANNEL_SYNCED', 2, NULL, 1, '{"channel": "BOOKING", "syncedQuantity": 12, "status": "SUCCESS"}'),
    -- Booking.com 예약 생성
    ('RESERVATION_CREATED', 2, 2, 2, '{"guestName": "James Wilson", "roomQuantity": 1, "channel": "BOOKING"}'),
    -- Agoda 예약 취소
    ('RESERVATION_CANCELLED', 3, 3, 1, '{"guestName": "田中太郎", "reason": "고객 요청 취소", "channel": "AGODA"}');
