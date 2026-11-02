-- AI 试穿效果图功能 - 新增表
-- 执行库：qj_minip_v2_t（测试） / qj_minip_v2（生产）
-- 通过 DBConfig.getDb(null, "qj") 访问

CREATE TABLE IF NOT EXISTS `qj_generated_outfit_image` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(64) NOT NULL,
  `scheme_id` BIGINT DEFAULT NULL COMMENT '关联 qj_history_scheme.id，可空',
  `human_image_url` VARCHAR(512) NOT NULL,
  `human_source` TINYINT NOT NULL DEFAULT 0 COMMENT '0=评测人像,1=新上传',
  `clothing_payload` TEXT NOT NULL COMMENT 'JSON: [{classification,frontUrl,...}]',
  `prompt_text` TEXT,
  `model_name` VARCHAR(64) NOT NULL DEFAULT 'openai/gpt-5-image',
  `task_id` VARCHAR(64) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending,1=success,2=failed',
  `provider_image_url` VARCHAR(512) DEFAULT NULL COMMENT 'OpenRouter 原始返回 URL',
  `cloud_image_url` VARCHAR(512) DEFAULT NULL COMMENT '转存到微信云存储后的URL',
  `error_msg` VARCHAR(1024) DEFAULT NULL,
  `duration_ms` INT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_openid_time` (`openid`, `create_time`),
  KEY `idx_scheme` (`scheme_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 生成的试穿效果图';

-- 用户试穿人像档案（长期保留，is_current=1 为当前选定）
CREATE TABLE IF NOT EXISTS `qj_user_tryon_portrait` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(64) NOT NULL,
  `image_url` VARCHAR(512) NOT NULL COMMENT 'https 永久 URL（tcb.qcloud.la 或同等）',
  `cloud_file_id` VARCHAR(255) DEFAULT NULL COMMENT '小程序端 cloud:// 原始 fileID',
  `source` TINYINT NOT NULL DEFAULT 1 COMMENT '1=用户上传 2=评测人像同步',
  `is_current` TINYINT NOT NULL DEFAULT 1 COMMENT '当前选定的人像，每个 openid 同时只应有一行=1',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_openid_current` (`openid`, `is_current`),
  KEY `idx_openid_time` (`openid`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 试穿用户人像档案';

-- AI 试穿付费订单（通用次数券）
CREATE TABLE IF NOT EXISTS `qj_ai_tryon_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(64) NOT NULL,
  `trade_no` VARCHAR(64) NOT NULL COMMENT '商户订单号',
  `prepay_id` VARCHAR(128) DEFAULT NULL,
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付流水号',
  `amount` INT NOT NULL COMMENT '单位分，1 元=100，开发期=1',
  `credits` INT NOT NULL DEFAULT 1 COMMENT '本订单购买次数',
  `credits_used` INT NOT NULL DEFAULT 0 COMMENT '已消耗次数',
  `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未付 1=已付 2=已退款',
  `paid_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_openid_status` (`openid`, `pay_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 试穿付费订单';
