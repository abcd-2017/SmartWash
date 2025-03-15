/*
 Navicat Premium Data Transfer

 Source Server         : Anolis
 Source Server Type    : MySQL
 Source Server Version : 80400
 Source Host           : 192.168.75.129:3306
 Source Schema         : smart_wash

 Target Server Type    : MySQL
 Target Server Version : 80400
 File Encoding         : 65001

 Date: 13/03/2025 23:16:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for admin_users
-- ----------------------------
DROP TABLE IF EXISTS `admin_users`;
CREATE TABLE `admin_users`  (
  `admin_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `role_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for laundry_items
-- ----------------------------
DROP TABLE IF EXISTS `laundry_items`;
CREATE TABLE `laundry_items`  (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `item_name` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `description` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL COMMENT '描述',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `base_price` decimal(10, 2) NOT NULL,
  PRIMARY KEY (`item_id`) USING BTREE,
  UNIQUE INDEX `item_name`(`item_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for lockers
-- ----------------------------
DROP TABLE IF EXISTS `lockers`;
CREATE TABLE `lockers`  (
  `locker_id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` bigint NOT NULL,
  `locker_number` int NOT NULL,
  `status` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT '0' COMMENT '\'空闲\'-0, \'使用中\'-1, \'故障\'-2',
  `last_used_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`locker_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 56 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `order_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `school_id` bigint NOT NULL,
  `locker_id` bigint NULL DEFAULT NULL,
  `laundry_items_id` bigint NULL DEFAULT NULL,
  `total_price` decimal(10, 2) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT '0' COMMENT '\'0\', -- \'已取消\'-> -2 \'已退款\'-> -1 \'待支付\'->0 \'待寄件\'->1 \'已收取\'->2, \'清洗中\'->3, \'已烘干\'->4, \'配送中\'->5, \'待取件\'->6, \'已完成\'->7',
  `pickup_code` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL,
  `order_no` varchar(100) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL,
  PRIMARY KEY (`order_id`) USING BTREE,
  UNIQUE INDEX `pickup_code`(`pickup_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for payments
-- ----------------------------
DROP TABLE IF EXISTS `payments`;
CREATE TABLE `payments`  (
  `payment_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `amount` decimal(10, 2) NOT NULL,
  `payment_method` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL COMMENT '\'钱包余额\' - 1, \'支付宝\' - 2, \'微信\' - 3',
  `status` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT '0' COMMENT '\'待支付\'-0, \'已支付\'-1, \'失败\'-2',
  `paid_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`payment_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for recharge_records
-- ----------------------------
DROP TABLE IF EXISTS `recharge_records`;
CREATE TABLE `recharge_records`  (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `amount` decimal(10, 2) NOT NULL,
  `recharge_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `recharge_type` varchar(10) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL COMMENT '充值类型，1--微信支付，2--支付宝支付',
  PRIMARY KEY (`record_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `description` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL COMMENT '角色描述',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for schools
-- ----------------------------
DROP TABLE IF EXISTS `schools`;
CREATE TABLE `schools`  (
  `school_id` bigint NOT NULL AUTO_INCREMENT,
  `school_name` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `location` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `locker_count` int NULL DEFAULT 200,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`school_id`) USING BTREE,
  UNIQUE INDEX `school_name`(`school_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `school_id` bigint NULL DEFAULT NULL,
  `phone_number` varchar(20) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `student_id` varchar(50) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL,
  `campus_card` varchar(50) CHARACTER SET utf16 COLLATE utf16_bin NULL DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf16 COLLATE utf16_bin NOT NULL,
  `balance` decimal(10, 2) NULL DEFAULT 0.00,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `phone_number`(`phone_number` ASC) USING BTREE,
  UNIQUE INDEX `student_id`(`student_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf16 COLLATE = utf16_bin ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
