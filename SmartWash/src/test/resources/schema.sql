-- =====================================================================
-- 测试专用 H2 内存库表结构（仅用于 Spring 上下文启动与集成测试，禁止用于生产）
-- 与 src/main/resources/db/migration/V1-V5 的字段全集对齐，去除 MySQL 方言：
--   ENGINE=InnoDB / CHARSET / COMMENT / ON UPDATE CURRENT_TIMESTAMP / SET NAMES 等 H2 不支持或不需要
-- 用途说明：OrderTimeoutManager 在 ApplicationReadyEvent 时会 selectList 查询 orders 表，
--   若无表结构会导致上下文加载失败；其余表为后续集成测试预留。
-- =====================================================================

-- 管理员用户表
CREATE TABLE IF NOT EXISTS admin_users (
    admin_id     bigint NOT NULL AUTO_INCREMENT,
    username     varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role_id      int NOT NULL,
    created_at   timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_username UNIQUE (username)
);

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    role_id     int NOT NULL AUTO_INCREMENT,
    role_name   varchar(255) NOT NULL,
    description varchar(255) DEFAULT NULL,
    created_at  timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id)
);

-- 学校表
CREATE TABLE IF NOT EXISTS schools (
    school_id     bigint NOT NULL AUTO_INCREMENT,
    school_name   varchar(255) NOT NULL,
    school_code   varchar(50) DEFAULT NULL,
    location      varchar(255) NOT NULL,
    province      varchar(50) DEFAULT NULL,
    city          varchar(50) DEFAULT NULL,
    district      varchar(50) DEFAULT NULL,
    longitude     decimal(10,7) DEFAULT NULL,
    latitude      decimal(10,7) DEFAULT NULL,
    logo_url      varchar(500) DEFAULT NULL,
    contact_name  varchar(50) DEFAULT NULL,
    contact_phone varchar(20) DEFAULT NULL,
    locker_count  int DEFAULT 200,
    created_at    timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id),
    CONSTRAINT uk_school_name UNIQUE (school_name)
);

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    user_id      bigint NOT NULL AUTO_INCREMENT,
    school_id    bigint DEFAULT NULL,
    phone_number varchar(20) NOT NULL,
    student_id   varchar(50) DEFAULT NULL,
    campus_card  varchar(50) DEFAULT NULL,
    password     varchar(255) NOT NULL,
    balance      decimal(10,2) DEFAULT 0.00,
    avatar       varchar(500) DEFAULT NULL,
    created_at   timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_phone UNIQUE (phone_number)
);

-- 寄存柜表
CREATE TABLE IF NOT EXISTS lockers (
    locker_id     bigint NOT NULL AUTO_INCREMENT,
    school_id     bigint NOT NULL,
    locker_number int NOT NULL,
    status        varchar(10) DEFAULT '0',
    last_used_at  datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (locker_id)
);

-- 洗衣项目表
CREATE TABLE IF NOT EXISTS laundry_items (
    item_id     bigint NOT NULL AUTO_INCREMENT,
    item_name   varchar(255) NOT NULL,
    description varchar(255) DEFAULT NULL,
    created_at  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    base_price  decimal(10,2) NOT NULL,
    PRIMARY KEY (item_id)
);

-- 优惠券模板表（含 V5 新增的限量字段 total_limit / issued_count）
CREATE TABLE IF NOT EXISTS coupon (
    coupon_id    bigint NOT NULL AUTO_INCREMENT,
    title        varchar(50) NOT NULL,
    description  varchar(100) DEFAULT NULL,
    discount     decimal(10,2) NOT NULL,
    threshold    decimal(10,2) NOT NULL,
    start_time   timestamp NOT NULL,
    end_time     timestamp NOT NULL,
    is_new_user  tinyint DEFAULT 0,
    status       varchar(10) DEFAULT 'active',
    created_at   timestamp DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp DEFAULT CURRENT_TIMESTAMP,
    valid_days   int NOT NULL,
    total_limit  int DEFAULT NULL,
    issued_count int NOT NULL DEFAULT 0,
    PRIMARY KEY (coupon_id)
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    order_id         bigint NOT NULL AUTO_INCREMENT,
    user_id          bigint NOT NULL,
    school_id        bigint NOT NULL,
    locker_id        bigint DEFAULT NULL,
    laundry_items_id bigint NOT NULL,
    total_price      decimal(10,2) DEFAULT NULL,
    created_at       timestamp DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp DEFAULT CURRENT_TIMESTAMP,
    status           varchar(10) DEFAULT '0',
    pickup_code      varchar(100) DEFAULT NULL,
    order_no         varchar(100) DEFAULT '',
    user_coupon_id   bigint DEFAULT NULL,
    pay_price        decimal(10,2) DEFAULT NULL,
    PRIMARY KEY (order_id)
);

-- 支付记录表（含 V4 新增的幂等键 out_trade_no）
CREATE TABLE IF NOT EXISTS payments (
    payment_id     bigint NOT NULL AUTO_INCREMENT,
    order_id       bigint NOT NULL,
    user_id        bigint NOT NULL,
    amount         decimal(10,2) NOT NULL,
    payment_method varchar(10) NOT NULL,
    out_trade_no   varchar(64) DEFAULT NULL,
    status         varchar(10) DEFAULT '0',
    paid_at        timestamp DEFAULT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payments_out_trade_no UNIQUE (out_trade_no)
);

-- 充值记录表（含 V4 新增的 status 与幂等键 out_trade_no）
CREATE TABLE IF NOT EXISTS recharge_records (
    record_id     bigint NOT NULL AUTO_INCREMENT,
    user_id       bigint NOT NULL,
    amount        decimal(10,2) NOT NULL,
    recharge_time timestamp DEFAULT CURRENT_TIMESTAMP,
    recharge_type varchar(10) NOT NULL,
    status        varchar(10) DEFAULT '3',
    out_trade_no  varchar(64) DEFAULT NULL,
    PRIMARY KEY (record_id),
    CONSTRAINT uk_recharge_out_trade_no UNIQUE (out_trade_no)
);

-- 用户优惠券记录表（含 V5 的 (user_id, coupon_id) 唯一索引）
CREATE TABLE IF NOT EXISTS user_coupon (
    user_coupon_id bigint NOT NULL AUTO_INCREMENT,
    user_id        bigint NOT NULL,
    coupon_id      bigint NOT NULL,
    is_used        tinyint DEFAULT 0,
    used_at        timestamp DEFAULT NULL,
    received_at    timestamp DEFAULT CURRENT_TIMESTAMP,
    order_id       bigint DEFAULT NULL,
    expired_at     timestamp NOT NULL,
    PRIMARY KEY (user_coupon_id),
    CONSTRAINT uk_user_coupon UNIQUE (user_id, coupon_id)
);

-- 订单评价表（V2）
CREATE TABLE IF NOT EXISTS order_reviews (
    review_id  bigint NOT NULL AUTO_INCREMENT,
    order_id   bigint NOT NULL,
    user_id    bigint NOT NULL,
    rating     int NOT NULL,
    content    varchar(500) DEFAULT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (review_id)
);
