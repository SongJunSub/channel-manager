-- 숙소 테이블 생성
-- 호텔, 리조트 등 숙박 시설의 기본 정보를 저장한다
CREATE TABLE properties (
    id               BIGSERIAL    PRIMARY KEY,              -- PK, 자동 증가
    property_code    VARCHAR(30)  NOT NULL UNIQUE,           -- 숙소 고유 코드 (SEOUL_GRAND 등)
    property_name    VARCHAR(100) NOT NULL,                  -- 숙소명
    property_address VARCHAR(255),                           -- 숙소 주소 (선택)
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()     -- 생성 시각
);

-- 숙소명 검색 성능을 위한 인덱스
CREATE INDEX idx_properties_property_name ON properties (property_name);

-- 숙소 코드 조회를 위한 인덱스
CREATE INDEX idx_properties_property_code ON properties (property_code);
