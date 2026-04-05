-- 채널별 가격 차등을 위한 마크업 비율 컬럼 추가
-- markup_rate: 기본 가격에 곱해지는 비율
--   1.000 = 기본 가격 그대로 (자사 홈페이지)
--   1.150 = 15% 인상 (OTA 수수료 반영)
--   0.950 = 5% 할인 (프로모션)
ALTER TABLE channels
    ADD COLUMN markup_rate DECIMAL(5, 3) NOT NULL DEFAULT 1.000;

-- 채널별 마크업 비율 설정
-- DIRECT: 자사 홈페이지 — 수수료 없음 (100%)
UPDATE channels SET markup_rate = 1.000 WHERE channel_code = 'DIRECT';
-- BOOKING: Booking.com — 15% 마크업 (OTA 수수료 반영)
UPDATE channels SET markup_rate = 1.150 WHERE channel_code = 'BOOKING';
-- AGODA: Agoda — 10% 마크업
UPDATE channels SET markup_rate = 1.100 WHERE channel_code = 'AGODA';
-- TRIP: Trip.com — 5% 할인 (프로모션, 비활성 채널이지만 가격은 설정)
UPDATE channels SET markup_rate = 0.950 WHERE channel_code = 'TRIP';
