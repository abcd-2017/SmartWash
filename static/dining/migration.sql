-- 吃啥配料库 + 营养列 迁移脚本（幂等，MySQL 8）
-- 用法：mysql -u<user> -p smartwash < static/dining/migration.sql
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 条件拼 SQL 实现幂等。

-- 1) 配料库两表（CREATE TABLE IF NOT EXISTS 天然幂等）
CREATE TABLE IF NOT EXISTS `dining_ingredient` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '配料 ID',
  `name`       VARCHAR(32) NOT NULL COMMENT '配料名（唯一；供应商信息不入库）',
  `category`   VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT '配料分类：MAIN=主料/AUX=辅料/SEASONING=调料/OTHER=其他',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吃啥-配料库';

CREATE TABLE IF NOT EXISTS `dining_recipe_ingredient` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '关联 ID',
  `recipe_id`     BIGINT        NOT NULL COMMENT '菜谱 ID(dining_recipe.id)',
  `ingredient_id` BIGINT        NOT NULL COMMENT '配料 ID(dining_ingredient.id)',
  `amount_value`  DECIMAL(10,2) NULL COMMENT '数量数值（如 575g -> 575），NULL=来源未给出',
  `amount_unit`   VARCHAR(16)   NULL COMMENT '单位（g/kg/ml/L/个/根/片/包/勺等），NULL=未给出',
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recipe_ingredient` (`recipe_id`,`ingredient_id`),
  KEY `idx_ingredient` (`ingredient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吃啥-菜谱配料关联（买菜清单聚合依据）';

-- 2) dining_recipe 营养列（存在即跳过）
SET @schema := DATABASE();
SET @add := (
  SELECT CONCAT('ALTER TABLE `dining_recipe` ',
    CONCAT_WS(', ',
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='calories_per_100g'), 'ADD COLUMN `calories_per_100g` DECIMAL(8,2) NULL COMMENT '热量 Kcal/100g（CookLikeHOC 营养表口径）'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='calories_total'), 'ADD COLUMN `calories_total` DECIMAL(10,2) NULL COMMENT '整份估算热量 Kcal（HowToCook 预估卡路里口径）'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='protein_g'), 'ADD COLUMN `protein_g` DECIMAL(6,2) NULL COMMENT '蛋白质 g/100g'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='fat_g'), 'ADD COLUMN `fat_g` DECIMAL(6,2) NULL COMMENT '脂肪 g/100g'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='carbs_g'), 'ADD COLUMN `carbs_g` DECIMAL(6,2) NULL COMMENT '碳水化合物 g/100g'', NULL),
      IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_recipe' AND COLUMN_NAME='sodium_mg'), 'ADD COLUMN `sodium_mg` DECIMAL(8,2) NULL COMMENT '钠 mg/100g'', NULL)
    ))
);
SET @stmt := IF(@add IS NOT NULL AND @add <> '', @add, 'SELECT ''dining_recipe 营养列已存在，跳过''');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- =====================================================================
-- 吃啥功能扩展（高+中档 5 项）增量：配料过敏原列 + 用户饮食偏好表 + 菜谱收藏表
-- 用法同上，幂等可重复执行。
-- =====================================================================

-- 3) dining_ingredient 过敏原列（存在即跳过）
SET @schema := DATABASE();
SET @add := (
  SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='dining_ingredient' AND COLUMN_NAME='allergen'),
    'ALTER TABLE `dining_ingredient` ADD COLUMN `allergen` VARCHAR(32) NULL COMMENT ''过敏原：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY/OTHER，NULL=无''',
    'SELECT ''dining_ingredient.allergen 已存在，跳过''')
);
PREPARE s FROM @add; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) 用户饮食偏好表（一人一行，user_id 主键）
CREATE TABLE IF NOT EXISTS `user_dietary_preference` (
  `user_id`        BIGINT      NOT NULL COMMENT '用户 ID(users.user_id)，一人一行',
  `allergens`      VARCHAR(128) NULL COMMENT '忌口标签 JSON 数组：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY',
  `calorie_target` INT         NULL COMMENT '每日热量目标 kcal，NULL=默认 2000',
  `sodium_target`  INT         NULL COMMENT '每日钠目标 mg，NULL=默认 2000',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户饮食偏好（忌口+目标）';

-- 5) 菜谱收藏表（uk_user_recipe 幂等闸门）
CREATE TABLE IF NOT EXISTS `dining_recipe_favorite` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL COMMENT '用户 ID(users.user_id)',
  `recipe_id`  BIGINT   NOT NULL COMMENT '菜谱 ID(dining_recipe.id)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_recipe` (`user_id`,`recipe_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='吃啥-菜谱收藏';
