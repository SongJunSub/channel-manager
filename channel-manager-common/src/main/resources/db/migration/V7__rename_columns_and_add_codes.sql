-- 엔티티 필드명을 보다 명시적으로 변경하고, 식별 코드 컬럼을 추가한다
-- 필드명만 보고도 어떤 도메인의 값인지 바로 알 수 있도록 개선한다

-- ==============================
-- 1. properties 테이블
-- ==============================

-- 숙소 고유 코드 컬럼 추가 (SEOUL_GRAND, BUSAN_OCEAN 등)
ALTER TABLE properties ADD COLUMN property_code VARCHAR(30) NOT NULL DEFAULT '';

-- 숙소 코드에 UNIQUE 제약조건 추가
ALTER TABLE properties ADD CONSTRAINT uq_properties_property_code UNIQUE (property_code);

-- name → property_name 컬럼 이름 변경
ALTER TABLE properties RENAME COLUMN name TO property_name;

-- address → property_address 컬럼 이름 변경
ALTER TABLE properties RENAME COLUMN address TO property_address;

-- 기존 idx_properties_name 인덱스 삭제 후 재생성
DROP INDEX IF EXISTS idx_properties_name;

-- 변경된 컬럼명으로 인덱스 재생성
CREATE INDEX idx_properties_property_name ON properties (property_name);

-- 숙소 코드 조회를 위한 인덱스
CREATE INDEX idx_properties_property_code ON properties (property_code);

-- ==============================
-- 2. room_types 테이블
-- ==============================

-- 객실 타입 고유 코드 컬럼 추가 (STD, DLX, STE 등)
ALTER TABLE room_types ADD COLUMN room_type_code VARCHAR(20) NOT NULL DEFAULT '';

-- name → room_type_name 컬럼 이름 변경
ALTER TABLE room_types RENAME COLUMN name TO room_type_name;

-- capacity → max_capacity 컬럼 이름 변경
ALTER TABLE room_types RENAME COLUMN capacity TO max_capacity;

-- ==============================
-- 3. channels 테이블
-- ==============================

-- code → channel_code 컬럼 이름 변경
ALTER TABLE channels RENAME COLUMN code TO channel_code;

-- name → channel_name 컬럼 이름 변경
ALTER TABLE channels RENAME COLUMN name TO channel_name;

-- 기존 idx_channels_code 인덱스 삭제 후 재생성
DROP INDEX IF EXISTS idx_channels_code;

-- 변경된 컬럼명으로 인덱스 재생성
CREATE INDEX idx_channels_channel_code ON channels (channel_code);

-- ==============================
-- 4. reservations 테이블
-- ==============================

-- quantity → room_quantity 컬럼 이름 변경
ALTER TABLE reservations RENAME COLUMN quantity TO room_quantity;

-- ==============================
-- 5. channel_events 테이블
-- ==============================

-- payload → event_payload 컬럼 이름 변경
ALTER TABLE channel_events RENAME COLUMN payload TO event_payload;
