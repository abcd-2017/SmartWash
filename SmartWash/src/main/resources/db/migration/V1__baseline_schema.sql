-- SmartWash 数据库基线迁移
-- 创建所有核心表结构

SET NAMES utf8mb4;

-- 管理员用户表
CREATE TABLE IF NOT EXISTS `admin_users` (
  `admin_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE IF NOT EXISTS `roles` (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(255) NOT NULL,
  `description` varchar(255) NULL DEFAULT NULL COMMENT '角色描述',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学校表
CREATE TABLE IF NOT EXISTS `schools` (
  `school_id` bigint NOT NULL AUTO_INCREMENT,
  `school_name` varchar(255) NOT NULL,
  `school_code` varchar(50) NULL DEFAULT NULL COMMENT '学校编码',
  `location` varchar(255) NOT NULL,
  `province` varchar(50) NULL DEFAULT NULL COMMENT '省',
  `city` varchar(50) NULL DEFAULT NULL COMMENT '市',
  `district` varchar(50) NULL DEFAULT NULL COMMENT '区/县',
  `longitude` decimal(10,7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,7) NULL DEFAULT NULL COMMENT '纬度',
  `logo_url` varchar(500) NULL DEFAULT NULL COMMENT '学校Logo URL',
  `contact_name` varchar(50) NULL DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) NULL DEFAULT NULL COMMENT '联系人电话',
  `locker_count` int NULL DEFAULT 200,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`school_id`),
  UNIQUE KEY `school_name` (`school_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` bigint NULL DEFAULT NULL,
  `phone_number` varchar(20) NOT NULL,
  `student_id` varchar(50) NULL DEFAULT NULL,
  `campus_card` varchar(50) NULL DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `balance` decimal(10,2) NULL DEFAULT 0.00,
  `avatar` varchar(500) NULL DEFAULT NULL COMMENT '头像URL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `phone_number` (`phone_number`),
  UNIQUE KEY `student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 寄存柜表
CREATE TABLE IF NOT EXISTS `lockers` (
  `locker_id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` bigint NOT NULL,
  `locker_number` int NOT NULL,
  `status` varchar(10) NULL DEFAULT '0' COMMENT '空闲-0, 使用中-1, 故障-2',
  `last_used_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`locker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 洗衣项目表
CREATE TABLE IF NOT EXISTS `laundry_items` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `item_name` varchar(255) NOT NULL,
  `description` varchar(255) NULL DEFAULT NULL COMMENT '描述',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `base_price` decimal(10,2) NOT NULL,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `item_name` (`item_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 优惠券模板表
CREATE TABLE IF NOT EXISTS `coupon` (
  `coupon_id` bigint NOT NULL AUTO_INCREMENT COMMENT '优惠券 ID',
  `title` varchar(50) NOT NULL COMMENT '标题',
  `description` varchar(100) NULL DEFAULT NULL COMMENT '优惠券说明',
  `discount` decimal(10,2) NOT NULL COMMENT '优惠金额',
  `threshold` decimal(10,2) NOT NULL COMMENT '使用门槛',
  `start_time` timestamp NOT NULL COMMENT '开始时间',
  `end_time` timestamp NOT NULL COMMENT '结束时间',
  `is_new_user` tinyint(1) NULL DEFAULT 0 COMMENT '是否仅限新用户使用',
  `status` varchar(10) NULL DEFAULT 'active' COMMENT '优惠券状态',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `valid_days` int NOT NULL COMMENT '领取后有效天数',
  PRIMARY KEY (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单表
CREATE TABLE IF NOT EXISTS `orders` (
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `school_id` bigint NOT NULL,
  `locker_id` bigint NULL DEFAULT NULL,
  `laundry_items_id` bigint NOT NULL,
  `total_price` decimal(10,2) NULL DEFAULT NULL COMMENT '总价',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` varchar(10) NULL DEFAULT '0' COMMENT '订单状态',
  `pickup_code` varchar(100) NULL DEFAULT NULL,
  `order_no` varchar(100) NULL DEFAULT '',
  `user_coupon_id` bigint NULL DEFAULT NULL COMMENT '所使用的优惠券 ID',
  `pay_price` decimal(10,2) NULL DEFAULT NULL COMMENT '需要支付的金额',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `pickup_code` (`pickup_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 支付记录表
CREATE TABLE IF NOT EXISTS `payments` (
  `payment_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `payment_method` varchar(10) NOT NULL COMMENT '支付方式',
  `status` varchar(10) NULL DEFAULT '0' COMMENT '支付状态',
  `paid_at` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 充值记录表
CREATE TABLE IF NOT EXISTS `recharge_records` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `recharge_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `recharge_type` varchar(10) NOT NULL COMMENT '充值类型',
  PRIMARY KEY (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户优惠券记录表
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `user_coupon_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户优惠券记录 ID',
  `user_id` bigint NOT NULL,
  `coupon_id` bigint NOT NULL,
  `is_used` tinyint(1) NULL DEFAULT 0 COMMENT '是否已使用',
  `used_at` timestamp NULL DEFAULT NULL COMMENT '使用时间',
  `received_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `order_id` bigint NULL DEFAULT NULL COMMENT '使用该优惠券的订单 ID',
  `expired_at` timestamp NOT NULL COMMENT '该用户券实际过期时间',
  PRIMARY KEY (`user_coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
