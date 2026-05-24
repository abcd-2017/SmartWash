-- V3: 添加缺失索引、修复数据问题

-- 1. 外键列索引（提升 JOIN 和 WHERE 查询性能）
CREATE INDEX idx_lockers_school_id ON lockers(school_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_school_id ON orders(school_id);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_user_coupon_user_id ON user_coupon(user_id);
CREATE INDEX idx_user_coupon_coupon_id ON user_coupon(coupon_id);
CREATE INDEX idx_recharge_records_user_id ON recharge_records(user_id);

-- 2. 复合索引（核心业务查询优化）
-- 寄存柜空闲查询（下单核心流程 SELECT FOR UPDATE）
CREATE INDEX idx_lockers_school_status ON lockers(school_id, status);
-- 用户订单列表查询
CREATE INDEX idx_orders_user_status ON orders(user_id, status);
-- Dashboard 收入统计
CREATE INDEX idx_payments_status_paid_at ON payments(status, paid_at);

-- 3. 唯一索引（数据完整性保障）
CREATE UNIQUE INDEX idx_orders_order_no ON orders(order_no);
CREATE UNIQUE INDEX uk_school_locker ON lockers(school_id, locker_number);

-- 4. 修复 ON UPDATE CURRENT_TIMESTAMP 问题
-- paid_at 应该只在实际支付时设置，不应随其他字段更新而自动刷新
ALTER TABLE payments MODIFY COLUMN paid_at timestamp NULL DEFAULT NULL COMMENT '实际支付时间';
-- created_at 不应随更新而变化
ALTER TABLE roles MODIFY COLUMN created_at timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
