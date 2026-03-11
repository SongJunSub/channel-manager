-- 숙소 테이블 생성
-- 호텔, 리조트 등 숙박 시설의 기본 정보를 저장한다
CREATE TABLE properties (
    id         BIGSERIAL    PRIMARY KEY,              -- PK, 자동 증가
    name       VARCHAR(100) NOT NULL,                 -- 숙소명
    address    VARCHAR(255),                          -- 숙소 주소 (선택)
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()    -- 생성 시각
);

-- 숙소명 검색 성능을 위한 인덱스
CREATE INDEX idx_properties_name ON properties (name);
