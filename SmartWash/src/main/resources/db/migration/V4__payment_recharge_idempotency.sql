-- V4: 支付与充值两段式幂等改造
-- 背景：原支付/充值流程无网关、无幂等键，重复请求即重复扣款/加钱。
-- 本次为 payments 与 recharge_records 增加“网关统一订单号 out_trade_no”作为幂等键并建唯一索引兜底；
-- recharge_records 原表无 status 列，一并补充（与 payments 状态码语义对齐，复用 PaymentStatus 枚举）。
-- 说明：MySQL 唯一索引对 NULL 不去重，存量数据 out_trade_no 均为 NULL，迁移安全。

SET NAMES utf8mb4;

-- 1. payments：新增幂等键列 + 唯一索引
ALTER TABLE `payments`
    ADD COLUMN `out_trade_no` varchar(64) NULL DEFAULT NULL COMMENT '网关统一订单号（幂等键），格式：PAY + yyyyMMdd + 雪花ID' AFTER `payment_method`;

CREATE UNIQUE INDEX `uk_payments_out_trade_no` ON `payments`(`out_trade_no`);

-- 2. payments：仅补充 status 列注释（新增“3-处理中”语义，列定义与默认值不变，1-已支付语义保持）
ALTER TABLE `payments`
    MODIFY COLUMN `status` varchar(10) NULL DEFAULT '0' COMMENT '支付状态：0-待支付，1-已支付，2-失败，3-处理中';

-- 3. recharge_records：新增状态列（默认处理中）与幂等键列 + 唯一索引
ALTER TABLE `recharge_records`
    ADD COLUMN `status` varchar(10) NULL DEFAULT '3' COMMENT '充值状态：3-处理中（默认），1-充值成功，2-充值失败（状态码与支付状态对齐）' AFTER `recharge_type`,
    ADD COLUMN `out_trade_no` varchar(64) NULL DEFAULT NULL COMMENT '网关统一订单号（幂等键），格式：RCH + yyyyMMdd + 雪花ID' AFTER `status`;

CREATE UNIQUE INDEX `uk_recharge_records_out_trade_no` ON `recharge_records`(`out_trade_no`);

-- 4. 存量数据语义修正：旧流程为“插充值记录即实时到账”，历史充值记录均已完成，
--    统一补为 1-已到账，避免新增 status 列的默认值(3-处理中)把历史成功记录误标为处理中。
--    新库/空表执行时本语句影响 0 行，无副作用。
UPDATE `recharge_records` SET `status` = '1' WHERE `status` = '3';
