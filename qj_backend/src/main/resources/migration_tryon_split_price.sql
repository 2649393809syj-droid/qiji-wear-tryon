-- AI 试穿分项定价迁移：试穿(生成) 与 换人像(上传) 分开计价、分开计数
-- 执行库：qj_minip_v2_t（测试） + qj_minip_v2（生产）
-- 依赖：qj_app_config（migration_app_config.sql）、qj_ai_tryon_order（migration_ai_tryon.sql）
-- 幂等：可重复执行。
-- 注意：价格存 qj_app_config，AppConfigService 启动时加载，改价后需重启后端生效。

-- 1) 给订单表加 kind 列（generate=试穿 / upload=换人像）。历史订单统一按 generate。
--    腾讯云是 MySQL 5.7（不支持 ADD COLUMN IF NOT EXISTS），用动态语句做幂等守卫。
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_ai_tryon_order' AND COLUMN_NAME = 'kind');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `qj_ai_tryon_order` ADD COLUMN `kind` VARCHAR(16) NOT NULL DEFAULT ''generate'' COMMENT ''次数类型：generate=试穿 / upload=换人像'' AFTER `trade_no`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 既有行兜底（DEFAULT 已覆盖，二次保险）
UPDATE `qj_ai_tryon_order` SET `kind` = 'generate' WHERE `kind` IS NULL OR `kind` = '';

-- 2) 写入分项价格（单位：分）。试穿 ¥3=300，换人像 ¥1=100。
--    重跑会覆盖回这些值；要改价改这里的数值并重启后端。
INSERT INTO `qj_app_config` (`config_key`, `config_value`, `description`) VALUES
  ('ai_tryon_generate_price', '300', 'AI 试穿(生成)单价，单位分，¥3'),
  ('ai_tryon_upload_price',   '100', 'AI 换人像(上传)单价，单位分，¥1')
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `description`  = VALUES(`description`);
