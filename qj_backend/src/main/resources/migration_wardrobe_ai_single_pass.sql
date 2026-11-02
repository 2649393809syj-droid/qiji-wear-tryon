-- Single-pass wardrobe AI review. Apply after migration_wardrobe_ai_review.sql.
-- Existing multi-round rows are preserved and receive deterministic legacy identities.
-- MySQL 5.7 does not support ADD ... IF NOT EXISTS, so every ALTER is guarded.

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'legacy_auto_id');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `legacy_auto_id` BIGINT DEFAULT NULL COMMENT ''Legacy wardrobe auto_id''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'nav_item_cur');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `nav_item_cur` TINYINT DEFAULT NULL COMMENT ''0 legacy, 1 clothing, 2 ornament''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'prompt_version_id');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `prompt_version_id` BIGINT DEFAULT NULL COMMENT ''Prompt version used by this recognition''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'ai_evaluation_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `ai_evaluation_json` TEXT DEFAULT NULL COMMENT ''Immutable AI result in legacy evaluation shape''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'advisor_evaluation_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `advisor_evaluation_json` TEXT DEFAULT NULL COMMENT ''Advisor final result in legacy evaluation shape''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'sample_type');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `sample_type` VARCHAR(16) DEFAULT NULL COMMENT ''positive/negative, null for pending or technical failure''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'diff_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `diff_json` TEXT DEFAULT NULL COMMENT ''Field-level AI/advisor differences''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'reviewer_openid');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `reviewer_openid` VARCHAR(64) DEFAULT NULL COMMENT ''Advisor openid''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'review_time');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `review_time` DATETIME DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'error_msg');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `error_msg` VARCHAR(1024) DEFAULT NULL COMMENT ''Technical AI failure message''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'flow_version');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD COLUMN `flow_version` VARCHAR(32) NOT NULL DEFAULT ''multi_round_v1'' COMMENT ''multi_round_v1/single_pass_v2''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_style_history' AND COLUMN_NAME = 'flow_version');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_style_history` ADD COLUMN `flow_version` VARCHAR(32) NOT NULL DEFAULT ''multi_round_v1'' COMMENT ''multi_round_v1/single_pass_v2''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_style_history' AND COLUMN_NAME = 'model_name');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_style_history` ADD COLUMN `model_name` VARCHAR(128) DEFAULT NULL COMMENT ''Actual AI model used''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `qj_wardrobe_flow_nav` (
  `flow_version` VARCHAR(32) NOT NULL,
  `nav_item_cur` TINYINT NOT NULL,
  PRIMARY KEY (`flow_version`, `nav_item_cur`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Allowed wardrobe flow and navigation values';

INSERT INTO `qj_wardrobe_flow_nav` (`flow_version`, `nav_item_cur`) VALUES
  ('multi_round_v1', 0),
  ('single_pass_v2', 1),
  ('single_pass_v2', 2)
ON DUPLICATE KEY UPDATE `flow_version` = VALUES(`flow_version`);

-- Only multi_round_v1 rows are legacy rows. Valid single-pass rows are never rewritten.
UPDATE `qj_wardrobe_item`
SET `legacy_auto_id` = -CAST(`id` AS SIGNED),
    `nav_item_cur` = 0,
    `flow_version` = 'multi_round_v1'
WHERE `flow_version` = 'multi_round_v1'
  AND (`legacy_auto_id` IS NULL OR `nav_item_cur` IS NULL);

SET @identity_nullable := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'legacy_auto_id' AND IS_NULLABLE = 'YES');
SET @ddl := IF(@identity_nullable = 1,
  'ALTER TABLE `qj_wardrobe_item` MODIFY COLUMN `legacy_auto_id` BIGINT NOT NULL COMMENT ''Legacy wardrobe auto_id''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @identity_nullable := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND COLUMN_NAME = 'nav_item_cur' AND IS_NULLABLE = 'YES');
SET @ddl := IF(@identity_nullable = 1,
  'ALTER TABLE `qj_wardrobe_item` MODIFY COLUMN `nav_item_cur` TINYINT NOT NULL COMMENT ''0 legacy, 1 clothing, 2 ornament''',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND INDEX_NAME = 'uk_wardrobe_legacy_identity');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD UNIQUE KEY `uk_wardrobe_legacy_identity` (`legacy_auto_id`, `nav_item_cur`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND INDEX_NAME = 'idx_wardrobe_sample_prompt');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD KEY `idx_wardrobe_sample_prompt` (`sample_type`, `prompt_version_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND INDEX_NAME = 'idx_wardrobe_flow_status');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD KEY `idx_wardrobe_flow_status` (`flow_version`, `status`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item' AND INDEX_NAME = 'idx_wardrobe_flow_nav');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD KEY `idx_wardrobe_flow_nav` (`flow_version`, `nav_item_cur`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_style_history' AND INDEX_NAME = 'idx_history_flow_status');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_style_history` ADD KEY `idx_history_flow_status` (`flow_version`, `review_status`, `create_time`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @foreign_key_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'qj_wardrobe_item'
    AND CONSTRAINT_NAME = 'fk_wardrobe_item_flow_nav' AND CONSTRAINT_TYPE = 'FOREIGN KEY');
SET @ddl := IF(@foreign_key_exists = 0,
  'ALTER TABLE `qj_wardrobe_item` ADD CONSTRAINT `fk_wardrobe_item_flow_nav` FOREIGN KEY (`flow_version`, `nav_item_cur`) REFERENCES `qj_wardrobe_flow_nav` (`flow_version`, `nav_item_cur`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
