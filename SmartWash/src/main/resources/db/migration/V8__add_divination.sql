-- V8: 观象台（占卜模块）—— 11 张业务表
-- 遵循项目约定：utf8mb4；id bigint AUTO_INCREMENT（与现有实体 IdType.AUTO 对齐）；
-- 无逻辑删除（全部物理删除，解读/卦例不提供用户侧删除入口）；XML 只允许 #{} 参数绑定。
-- 对应设计文档：docs/占卜模块后端与数据库架构设计.md 第六章

SET NAMES utf8mb4;

-- =====================================================================
-- 1. 卦例主表（卦历）
-- =====================================================================
CREATE TABLE `div_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       NOT NULL COMMENT 'users.id',
  `method`         VARCHAR(16)  NOT NULL COMMENT 'liuyao/meihua/qimen/liuren',
  `category`       VARCHAR(16)  DEFAULT 'general' COMMENT 'general/career/wealth/...',
  `question`       VARCHAR(255) NOT NULL,
  `cast_method`    VARCHAR(16)  NOT NULL COMMENT 'auto/manual/time',
  `cast_at`        DATETIME(3)  NOT NULL COMMENT '起卦时刻(权威,追问沿用)',
  `tz_offset`      INT          NOT NULL DEFAULT 480 COMMENT '分钟, 东八=480',
  `source`         VARCHAR(16)  NOT NULL DEFAULT 'app' COMMENT 'app/today',
  `lines`          JSON         NULL COMMENT '起卦原始输入(六爻爻值数组等)',
  `client_chart`   JSON         NULL COMMENT '端上排盘结果快照',
  `server_chart`   JSON         NOT NULL COMMENT '服务端core重算权威盘面',
  `chart_verified` TINYINT      NOT NULL DEFAULT 1 COMMENT '端上/服务端盘面一致',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`),
  KEY `idx_method_time` (`method`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='占卜卦例';

-- =====================================================================
-- 2. 解读记录（一卦可多次解读，取最新展示）
-- =====================================================================
CREATE TABLE `div_interpretation` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `record_id`         BIGINT      NOT NULL,
  `user_id`           BIGINT      NOT NULL,
  `prompt_version_id` BIGINT      NOT NULL,
  `kind`              VARCHAR(16) NOT NULL DEFAULT 'first' COMMENT 'first/followup',
  `question`          VARCHAR(255) DEFAULT NULL COMMENT '追问时的子问题',
  `provider`          VARCHAR(32) NOT NULL,
  `model`             VARCHAR(64) NOT NULL,
  `key_source`        VARCHAR(16) NOT NULL DEFAULT 'platform' COMMENT 'platform/user',
  `config_id`         BIGINT      NULL COMMENT '模型配置id(平台目录或用户BYOK)',
  `content_md`        MEDIUMTEXT  NOT NULL,
  `tokens_in`         INT         NOT NULL DEFAULT 0,
  `tokens_out`        INT         NOT NULL DEFAULT 0,
  `latency_ms`        INT         NOT NULL DEFAULT 0,
  `cache_hit`         TINYINT     NOT NULL DEFAULT 0,
  `audit_status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审/1通过/2不一致',
  `audit_json`        JSON        NULL COMMENT '审计明细:引用字段vs盘面diff',
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`, `created_at`),
  KEY `idx_audit` (`audit_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM解读';

-- =====================================================================
-- 3. 用户反馈（含应验回填，评测数据源）
-- =====================================================================
CREATE TABLE `div_feedback` (
  `id`                BIGINT      NOT NULL AUTO_INCREMENT,
  `record_id`         BIGINT      NOT NULL,
  `interpretation_id` BIGINT      NOT NULL,
  `user_id`           BIGINT      NOT NULL,
  `rating`            TINYINT     NOT NULL COMMENT '1-5',
  `outcome`           TINYINT     DEFAULT 0 COMMENT '0未回填/1应验/2未验/3难说',
  `outcome_note`      VARCHAR(500) DEFAULT NULL,
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解读反馈';

-- =====================================================================
-- 4. Prompt 版本（system prompt + 领域方法 + 输出参数，热更新源）
-- =====================================================================
CREATE TABLE `div_prompt_version` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT,
  `method`         VARCHAR(16) NOT NULL,
  `version`        VARCHAR(16) NOT NULL COMMENT '如 liuyao-v1.2',
  `system_prompt`  TEXT        NOT NULL,
  `method_text`    TEXT        NOT NULL COMMENT '领域分析方法文本',
  `output_config`  JSON        NOT NULL COMMENT 'temperature/max_tokens等',
  `status`         TINYINT     NOT NULL DEFAULT 0 COMMENT '0草稿/1激活/2退役',
  `remark`         VARCHAR(255) DEFAULT NULL,
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_version` (`method`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt版本';

-- =====================================================================
-- 5. RAG 语料：古籍文档
-- =====================================================================
CREATE TABLE `div_rag_document` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(128) NOT NULL COMMENT '如 增删卜易',
  `book`       VARCHAR(64)  NOT NULL,
  `method`     VARCHAR(16)  NOT NULL COMMENT '适用术数',
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0导入中/1可用',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='古籍文档';

-- =====================================================================
-- 6. RAG 语料：切片（embedding 存 JSON，内存余弦检索）
-- =====================================================================
CREATE TABLE `div_rag_chunk` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT      NOT NULL,
  `chapter`     VARCHAR(128) DEFAULT NULL,
  `seq`         INT         NOT NULL,
  `content`     TEXT        NOT NULL,
  `embedding`   JSON        NOT NULL COMMENT 'float数组',
  `token_count` INT         NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc` (`document_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='语料切片';

-- =====================================================================
-- 7. 安全拦截日志
-- =====================================================================
CREATE TABLE `div_blocked_question` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `question`   VARCHAR(255) NOT NULL,
  `method`     VARCHAR(16)  DEFAULT NULL,
  `reason`     VARCHAR(255) NOT NULL COMMENT '规则id或llm',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拦截问题';

-- =====================================================================
-- 8. 每日用量（task 定时聚合，管理端看板）
-- =====================================================================
CREATE TABLE `div_usage_daily` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT,
  `stat_date`       DATE        NOT NULL,
  `method`          VARCHAR(16) NOT NULL,
  `record_count`    INT         NOT NULL DEFAULT 0,
  `interpret_count` INT         NOT NULL DEFAULT 0,
  `cache_hit_count` INT         NOT NULL DEFAULT 0,
  `blocked_count`   INT         NOT NULL DEFAULT 0,
  `tokens_in`       BIGINT      NOT NULL DEFAULT 0,
  `tokens_out`      BIGINT      NOT NULL DEFAULT 0,
  `active_users`    INT         NOT NULL DEFAULT 0,
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_method` (`stat_date`, `method`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日用量';

-- =====================================================================
-- 9. 平台模型目录（管理端维护；API Key 密文存储）
-- =====================================================================
CREATE TABLE `div_model_config` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `name`           VARCHAR(64)  NOT NULL COMMENT '显示名,如 GLM-4.7',
  `provider`       VARCHAR(32)  NOT NULL DEFAULT 'openai_compat',
  `base_url`       VARCHAR(255) NOT NULL,
  `model_id`       VARCHAR(64)  NOT NULL COMMENT '供应商模型标识',
  `api_key_cipher` VARCHAR(512) NOT NULL COMMENT 'AES-256-GCM 密文 v{ver}:{iv}:{ct}',
  `api_key_mask`   VARCHAR(32)  NOT NULL COMMENT '掩码 sk-****abc4',
  `priority`       INT          NOT NULL DEFAULT 100 COMMENT '越小越优先',
  `enabled`        TINYINT      NOT NULL DEFAULT 1,
  `key_version`    INT          NOT NULL DEFAULT 1 COMMENT '加密主密钥版本',
  `last_test_at`   DATETIME     NULL,
  `last_test_ok`   TINYINT      NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台模型目录';

-- =====================================================================
-- 10. 平台全局设置（单例 id=1，管理端维护）
-- =====================================================================
CREATE TABLE `div_platform_setting` (
  `id`                   BIGINT NOT NULL,
  `default_model_id`     BIGINT NOT NULL COMMENT 'div_model_config.id',
  `fallback_model_id`    BIGINT NULL COMMENT '备用模型',
  `byok_enabled`         TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许用户自带key',
  `byok_daily_limit`     INT    NOT NULL DEFAULT 50,
  `platform_daily_limit` INT    NOT NULL DEFAULT 20,
  `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='占卜平台全局设置';

-- =====================================================================
-- 11. 用户自带 API 配置（BYOK，一用户一条）
-- =====================================================================
CREATE TABLE `div_user_api_config` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL,
  `model_config_id` BIGINT       NULL COMMENT '用平台预设供应商时引用 div_model_config.id',
  `custom_base_url` VARCHAR(255) NULL COMMENT '完全自定义接入点时填',
  `custom_model`    VARCHAR(64)  NULL,
  `api_key_cipher`  VARCHAR(512) NOT NULL COMMENT 'AES-256-GCM 密文',
  `api_key_mask`    VARCHAR(32)  NOT NULL COMMENT '掩码',
  `key_version`     INT          NOT NULL DEFAULT 1,
  `verified`        TINYINT      NOT NULL DEFAULT 0 COMMENT '保存前试呼通过',
  `last_test_at`    DATETIME     NULL,
  `enabled`         TINYINT      NOT NULL DEFAULT 1,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自带API(BYOK)';
