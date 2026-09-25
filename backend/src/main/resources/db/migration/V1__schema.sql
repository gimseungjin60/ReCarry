-- RECARRY schema v1. 설계 설명: docs/BACKEND.md
-- 사업 값(가격·재고·회수처)은 여기 넣지 않는다. sample 카탈로그는 db/sample 에서만 들어온다.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt. 평문 저장 금지
    name          VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 대소문자만 다른 중복 가입 방지 (애플리케이션도 소문자로 저장한다)
CREATE UNIQUE INDEX users_email_key ON users (lower(email));

-- ---------------------------------------------------------------- catalog
-- 사이즈 단위 상품 정보와 요금. 같은 사이즈는 같은 요금이다.
CREATE TABLE carrier_models (
    id                BIGSERIAL PRIMARY KEY,
    size              VARCHAR(4)   NOT NULL UNIQUE,     -- '20' '24' '28'
    name              VARCHAR(50)  NOT NULL,
    inch              VARCHAR(20)  NOT NULL,
    capacity          VARCHAR(20)  NOT NULL,
    usage             VARCHAR(60)  NOT NULL,
    dims              VARCHAR(40)  NOT NULL,
    weight            VARCHAR(20)  NOT NULL,
    price             INTEGER      NOT NULL CHECK (price >= 0),              -- 2박 3일 기본 요금 (원)
    extra_night_price INTEGER      NOT NULL CHECK (extra_night_price >= 0),  -- 3박째부터 1박당 (원)
    headline          VARCHAR(120) NOT NULL,           -- 줄바꿈은 '\n'
    description       TEXT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 캐리어 한 대. Carrier ID 로 식별하는 개별 자산.
-- status 는 "운영 상태"만 저장한다. RESERVED / RENTED 는 예약 날짜로 계산한다.
CREATE TABLE carriers (
    id             BIGSERIAL PRIMARY KEY,
    model_id       BIGINT       NOT NULL REFERENCES carrier_models (id),
    carrier_code   VARCHAR(20)  NOT NULL UNIQUE,       -- 예: RC-24-0187
    grade          VARCHAR(1)   NOT NULL CHECK (grade IN ('A', 'B')),
    status         VARCHAR(12)  NOT NULL DEFAULT 'INSPECTION'
                   CHECK (status IN ('AVAILABLE', 'INSPECTION', 'REPAIR', 'UNAVAILABLE')),
    collected_from VARCHAR(60)  NOT NULL,              -- 회수처 범주. 특정 기관명을 단정하지 않는다
    repair_summary VARCHAR(120) NOT NULL DEFAULT '',
    inspected_at   DATE,
    featured       BOOLEAN      NOT NULL DEFAULT FALSE,  -- 사이즈 대표로 화면에 보여줄 캐리어
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX carriers_model_idx ON carriers (model_id);
-- 사이즈마다 대표 캐리어는 하나
CREATE UNIQUE INDEX carriers_featured_per_model ON carriers (model_id) WHERE featured;

-- 회수 · 수리 · 세척 · 검수 · 여행 기록 (Carrier Story 타임라인)
CREATE TABLE carrier_events (
    id         BIGSERIAL PRIMARY KEY,
    carrier_id BIGINT       NOT NULL REFERENCES carriers (id) ON DELETE CASCADE,
    event_date DATE         NOT NULL,
    type       VARCHAR(12)  NOT NULL CHECK (type IN ('COLLECTED', 'REPAIR', 'CLEANING', 'INSPECTION', 'TRIP')),
    title      VARCHAR(60)  NOT NULL,
    detail     VARCHAR(160) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX carrier_events_carrier_idx ON carrier_events (carrier_id, event_date);

-- ---------------------------------------------------------------- bookings
CREATE TABLE bookings (
    id              BIGSERIAL PRIMARY KEY,
    booking_number  VARCHAR(20)  NOT NULL UNIQUE,
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    carrier_id      BIGINT       NOT NULL REFERENCES carriers (id),
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    nights          INTEGER      NOT NULL CHECK (nights > 0),
    -- 캐리어가 고객에게 가 있는 기간: 출발 전날 배송 ~ 반납일 회수 (양 끝 포함)
    occupied        DATERANGE    GENERATED ALWAYS AS (daterange(start_date - 1, end_date, '[]')) STORED,
    recipient_name  VARCHAR(50)  NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    address         VARCHAR(200) NOT NULL,
    address_detail  VARCHAR(100) NOT NULL DEFAULT '',
    request_message VARCHAR(300) NOT NULL DEFAULT '',
    -- 예약 시점 금액 스냅샷. 서버가 계산한 값만 저장한다
    base_price      INTEGER      NOT NULL CHECK (base_price >= 0),
    extra_price     INTEGER      NOT NULL CHECK (extra_price >= 0),
    total_price     INTEGER      NOT NULL CHECK (total_price = base_price + extra_price),
    status          VARCHAR(10)  NOT NULL DEFAULT 'REQUESTED'
                    CHECK (status IN ('REQUESTED', 'CONFIRMED', 'IN_USE', 'RETURNED', 'CANCELLED')),
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (end_date > start_date),
    CHECK (nights = end_date - start_date),
    -- 같은 캐리어가 겹치는 기간에 두 번 점유되지 않는다 (동시 요청도 DB 가 막는다)
    CONSTRAINT bookings_no_overlap EXCLUDE USING gist (carrier_id WITH =, occupied WITH &&)
        WHERE (status IN ('REQUESTED', 'CONFIRMED', 'IN_USE'))
);
CREATE INDEX bookings_user_idx ON bookings (user_id, created_at DESC);
CREATE INDEX bookings_status_idx ON bookings (status);
