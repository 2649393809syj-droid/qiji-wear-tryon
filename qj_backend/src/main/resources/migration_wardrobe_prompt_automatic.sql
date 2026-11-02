-- Automatic wardrobe Prompt optimization schema. Apply after
-- migration_wardrobe_prompt_optimization.sql. MySQL 5.7 compatible and replay-safe.
-- Existing optimization rows receive trigger_type=legacy_manual from the default.
-- New backend-created automatic jobs must explicitly insert trigger_type=automatic.

-- Final column contracts. Each addition below is protected by an information_schema
-- guard because MySQL 5.7 has no ADD COLUMN IF NOT EXISTS syntax.
-- `trigger_type` ENUM('legacy_manual','automatic') NOT NULL DEFAULT 'legacy_manual',
-- `heldout_sample_ids_json` MEDIUMTEXT,
-- `threshold_json` TEXT,
-- `baseline_metrics_json` TEXT,
-- `candidate_metrics_json` TEXT,
-- `decision_reason` VARCHAR(255) DEFAULT NULL,
-- `sample_max_item_id` BIGINT DEFAULT NULL,
-- `publish_time` DATETIME DEFAULT NULL,
-- `running_guard` TINYINT GENERATED ALWAYS AS
--   (CASE WHEN `trigger_type` = 'automatic'
--              AND `status` IN ('pending','generating','waiting_for_holdout','evaluating')
--         THEN 1 ELSE NULL END) STORED,
-- UNIQUE KEY `uk_prompt_optimization_one_running` (`running_guard`)

UPDATE `qj_wardrobe_prompt_version`
SET `status` = 'candidate'
WHERE `status` = 'ready_for_review';

ALTER TABLE `qj_wardrobe_prompt_version`
  MODIFY COLUMN `status`
    ENUM('candidate','evaluating','active','retired','rejected') NOT NULL;

ALTER TABLE `qj_wardrobe_prompt_optimization`
  MODIFY COLUMN `status`
    ENUM('pending','running','completed','failed','generating','waiting_for_holdout',
         'evaluating','published','rejected','stale') NOT NULL DEFAULT 'pending';

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'trigger_type');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `trigger_type` ENUM(''legacy_manual'',''automatic'') NOT NULL DEFAULT ''legacy_manual''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_version'
    AND COLUMN_NAME = 'rejection_reason');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_version` ADD COLUMN `rejection_reason` VARCHAR(255) DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'heldout_sample_ids_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `heldout_sample_ids_json` MEDIUMTEXT',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'threshold_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `threshold_json` TEXT',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'baseline_metrics_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `baseline_metrics_json` TEXT',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'candidate_metrics_json');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `candidate_metrics_json` TEXT',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'decision_reason');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `decision_reason` VARCHAR(255) DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'sample_max_item_id');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `sample_max_item_id` BIGINT DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'publish_time');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `publish_time` DATETIME DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND COLUMN_NAME = 'running_guard');
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `running_guard` TINYINT GENERATED ALWAYS AS (CASE WHEN `trigger_type` = ''automatic'' AND `status` IN (''pending'',''generating'',''waiting_for_holdout'',''evaluating'') THEN 1 ELSE NULL END) STORED',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'qj_wardrobe_prompt_optimization'
    AND INDEX_NAME = 'uk_prompt_optimization_one_running');
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD UNIQUE KEY `uk_prompt_optimization_one_running` (`running_guard`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_version_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `action` ENUM('auto_publish','manual_rollback') NOT NULL,
  `from_version_id` BIGINT NOT NULL,
  `to_version_id` BIGINT NOT NULL,
  `actor_type` ENUM('system','advisor') NOT NULL,
  `actor_id` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `optimization_id` BIGINT DEFAULT NULL,
  `sample_floor_item_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prompt_audit_time` (`create_time`),
  KEY `idx_prompt_audit_target` (`to_version_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 5.7 does not allow CREATE TRIGGER through PREPARE. Apply this file with
-- mysql or another SQL client that honors DELIMITER directives. DELIMITER is a
-- client-side command and is not sent to MySQL. Replaying this migration drops
-- and recreates only these migration-owned triggers before installing the same
-- append-only enforcement.
DELIMITER $$

DROP TRIGGER IF EXISTS `trg_prompt_version_audit_no_update`$$
CREATE TRIGGER `trg_prompt_version_audit_no_update`
BEFORE UPDATE ON `qj_wardrobe_prompt_version_audit`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'qj_wardrobe_prompt_version_audit is append-only'$$

DROP TRIGGER IF EXISTS `trg_prompt_version_audit_no_delete`$$
CREATE TRIGGER `trg_prompt_version_audit_no_delete`
BEFORE DELETE ON `qj_wardrobe_prompt_version_audit`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'qj_wardrobe_prompt_version_audit is append-only'$$

DELIMITER ;
