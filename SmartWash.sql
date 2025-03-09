-- 用户表（管理用户账号信息）
CREATE TABLE users
(
    user_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id    BIGINT             NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    student_id   VARCHAR(50) UNIQUE,
    campus_card  VARCHAR(50) UNIQUE,
    password     VARCHAR(255)       NOT NULL,
    balance      DECIMAL(10, 2) DEFAULT 0.00, -- 用户钱包余额
    created_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP

      );

-- 充值记录表
CREATE TABLE recharge_records
(
    record_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT         NOT NULL,
    amount        DECIMAL(10, 2) NOT NULL, -- 充值金额
    recharge_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP

      );

-- 学校表（记录不同学校及其寄存柜数）
CREATE TABLE schools
(
    school_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_name  VARCHAR(255) UNIQUE NOT NULL,
    location     VARCHAR(255)        NOT NULL,
    locker_count INT       DEFAULT 200, -- 默认每所学校200个寄存柜
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP

      );

-- 寄存柜表（存储每个寄存柜的状态）
CREATE TABLE lockers
(
    locker_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id     BIGINT   NOT NULL,
    locker_number INT      NOT NULL,
    status        VARCHAR(10) DEFAULT '0', -- '空闲'-0, '使用中'-1, '故障'-2
    last_used_at  DATETIME NULL

      );

-- 订单表（记录洗衣订单信息）
CREATE TABLE orders
(
    order_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    school_id        BIGINT         NOT NULL,
    locker_id        BIGINT,
    laundry_items_id BIGINT,
    total_price      DECIMAL(10, 2) NOT NULL,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status           VARCHAR(10) DEFAULT '0', -- '已取消'-> -2 '已退款'-> -1 '待支付'->0 '待寄件'->1 '已收取'->2, '清洗中'->3, '已烘干'->4, '配送中'->5, '待取件'->6, '已完成'->7
    pickup_code      VARCHAR(10) UNIQUE       -- 取件码

      );

-- 支付记录表（记录支付状态）
CREATE TABLE payments
(
    payment_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    user_id        BIGINT         NOT NULL,
    amount         DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(10)    NOT NULL, -- '钱包余额' - 1, '支付宝' - 2, '微信' - 3
    status         VARCHAR(10) DEFAULT '0', -- '待支付'-0, '已支付'-1, '失败'-2
    paid_at        TIMESTAMP      NULL

      );


-- 自定义衣物种类表（管理员可设置不同的衣物种类及价格）
CREATE TABLE laundry_items
(
    item_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_name  VARCHAR(255) UNIQUE NOT NULL,
    base_price DECIMAL(10, 2)      NOT NULL -- 衣物基础清洗价格

      );

-- 后台管理员表
CREATE TABLE admin_users
(
    admin_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100) UNIQUE                                 NOT NULL,
    password_hash VARCHAR(255)                                        NOT NULL,
    role          ENUM ('超级管理员', '学校管理员', '洗衣工厂管理员') NOT NULL,
    school_id     BIGINT                                              NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP

      );