-- V6: 修复管理员种子密码为 MD5 弱哈希（对应评审报告后端 #12）
-- 背景：smart_wash.sql 种子数据中部分管理员的 password_hash 为 32 位十六进制（MD5 特征，
--       如 admin 账号的 a66abb5684c45962d887564f08346e8d），与 Spring Security 的
--       BCryptPasswordEncoder 校验逻辑不匹配，这些账号实际无法登录。
-- 方案：仅将仍为“32 位十六进制（MD5 特征）”的密码行更新为 BCrypt 哈希；
--       已是 BCrypt 特征（以 $ 开头，如 $2a$）的行保持不变，不受影响。

-- 重要安全说明：
--   1. 本迁移写入的 BCrypt 哈希对应一个固定的初始口令，该口令不落入任何仓库文件
--      （SQL/代码/配置/文档均不得写入），仅通过部署交付渠道告知运维人员；
--      哈希由 htpasswd -bnBC 10 生成，$2y$ 前缀，Spring Security BCryptPasswordEncoder 支持校验。
--   2. 管理员首次登录后必须立即修改密码！
--   3. 明文初始密码与 MD5 哈希均不得再次写入任何 SQL/代码/配置入库。

SET NAMES utf8mb4;

UPDATE `admin_users`
SET `password_hash` = '$2y$10$MJZDPvq402JQpwgOQwnByuTFZyhd1cwbFOkPk6gWB30xRS69bSWBe'
WHERE `password_hash` REGEXP '^[0-9a-fA-F]{32}$';
