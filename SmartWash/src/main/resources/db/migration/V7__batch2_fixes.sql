-- V7: 批次二修复（对应评审报告后端 #14、#15）
-- 1) users.campus_card：绑定校园卡应用层查重（UsersServiceImpl.bingCampus）+ 数据库唯一索引兜底并发绑定；
-- 2) order_reviews.order_id：同一订单仅允许一条评价，应用层查重（OrderReviewsServiceImpl.addReview）
--    与唯一索引双保险。两个索引均为"先去重、后建索引"，避免存量脏数据导致迁移失败中断。

SET NAMES utf8mb4;

-- =====================================================================
-- 1. users.campus_card 存量去重（评审报告后端 #15）
-- =====================================================================
-- 历史数据无绑定约束，同一 campus_card 可能被多个账号重复绑定，直接建唯一索引会失败。
-- 去重策略：同一 campus_card 仅保留一条"最新有效绑定"——users 表无绑定时间字段，
-- 以 user_id 最大的一行视为最新绑定（近似后绑定者优先）；其余重复行将 campus_card 置 NULL
-- （保留用户账号本身，仅解除其卡片绑定，不物理删行），NULL 不参与唯一索引去重。
UPDATE users u
JOIN (
    SELECT campus_card, MAX(user_id) AS keep_user_id
    FROM users
    WHERE campus_card IS NOT NULL
    GROUP BY campus_card
    HAVING COUNT(*) > 1
) dup ON dup.campus_card = u.campus_card
SET u.campus_card = NULL
WHERE u.user_id < dup.keep_user_id;

-- 唯一索引：同一张校园卡全库仅允许绑定一个账号（MySQL 唯一索引对 NULL 不去重，未绑卡用户不受影响）
ALTER TABLE `users`
    ADD UNIQUE INDEX `uk_users_campus_card` (`campus_card`);

-- =====================================================================
-- 2. order_reviews.order_id 存量去重（评审报告后端 #14）
-- =====================================================================
-- 历史并发提交可能对同一订单产生多条评价，直接建唯一索引会失败。
-- 去重策略：同一 order_id 仅保留最早一条评价（review_id 最小，视为首次评价），
-- 其余重复评价物理删除（评价数据非资金凭证，可安全清理）。
DELETE r
FROM order_reviews r
JOIN (
    SELECT order_id, MIN(review_id) AS keep_id
    FROM order_reviews
    GROUP BY order_id
    HAVING COUNT(*) > 1
) dup ON dup.order_id = r.order_id
WHERE r.review_id > dup.keep_id;

-- 唯一索引：同一订单仅允许一条评价，兜底并发重复提交（DuplicateKeyException 由应用层转友好提示）
ALTER TABLE `order_reviews`
    ADD UNIQUE INDEX `uk_order_reviews_order_id` (`order_id`);
