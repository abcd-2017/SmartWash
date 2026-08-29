-- V5: 优惠券领取/核销原子化改造（对应评审报告后端 #3、#4、#19）
-- 1) coupon 表新增发放限量字段，支持限量防超发；
-- 2) user_coupon 表建立 (user_id, coupon_id) 唯一索引，兜底并发重复领取；
-- 3) 建唯一索引前先对存量重复数据安全去重，避免迁移失败中断。

-- 1. coupon 表新增限量字段
ALTER TABLE coupon
    ADD COLUMN total_limit int NULL DEFAULT NULL COMMENT '发放总量上限（NULL 表示不限量）' AFTER valid_days,
    ADD COLUMN issued_count int NOT NULL DEFAULT 0 COMMENT '已发放数量（领取时原子递增，与领取记录同事务）' AFTER total_limit;

-- 2. user_coupon 表存量去重（为建唯一索引做准备）
-- 历史并发领取可能产生同组 (user_id, coupon_id) 多行记录，直接创建唯一索引会失败。
-- 去重策略：每组 (user_id, coupon_id) 仅保留一条——若组内存在已核销（is_used=1）的记录，
-- 优先保留其中 id 最小的一条，避免把用户已消费的券错误回补成可用券；
-- 组内全部未核销时保留最早领取（user_coupon_id 最小）的一条，其余重复行物理删除。
DELETE uc
FROM user_coupon uc
JOIN (
    SELECT user_id, coupon_id,
           CASE WHEN SUM(COALESCE(is_used, 0) = 1) > 0
                THEN MIN(CASE WHEN is_used = 1 THEN user_coupon_id END)
                ELSE MIN(user_coupon_id)
           END AS keep_id
    FROM user_coupon
    GROUP BY user_id, coupon_id
    HAVING COUNT(*) > 1
) dup ON dup.user_id = uc.user_id
     AND dup.coupon_id = uc.coupon_id
WHERE uc.user_coupon_id > dup.keep_id;

-- 3. 唯一索引：同一用户对同一张券仅允许领取一次（数据库层兜底并发领取，
--    配合 UserCouponServiceImpl 捕获 DuplicateKeyException 转友好提示）
ALTER TABLE user_coupon
    ADD UNIQUE INDEX uk_user_coupon (user_id, coupon_id);
