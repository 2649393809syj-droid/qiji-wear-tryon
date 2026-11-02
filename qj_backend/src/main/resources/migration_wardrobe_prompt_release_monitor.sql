-- Post-publication Prompt shadow evaluation and automatic rollback.
-- Apply after migration_wardrobe_prompt_automatic.sql. MySQL 5.7 compatible and replay-safe.

DROP TRIGGER IF EXISTS `trg_prompt_version_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_prompt_version_audit_no_delete`;

ALTER TABLE `qj_wardrobe_prompt_version_audit`
  MODIFY COLUMN `action`
    ENUM('auto_publish','manual_rollback','auto_rollback') NOT NULL;

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_release_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `publication_audit_id` BIGINT NOT NULL,
  `optimization_id` BIGINT NOT NULL,
  `published_prompt_version_id` BIGINT NOT NULL,
  `baseline_prompt_version_id` BIGINT NOT NULL,
  `status` ENUM('collecting','evaluating','kept','rolled_back','failed','stale') NOT NULL DEFAULT 'collecting',
  `sample_ids_json` MEDIUMTEXT,
  `threshold_json` TEXT NOT NULL,
  `published_metrics_json` TEXT,
  `baseline_metrics_json` TEXT,
  `decision_reason` VARCHAR(255) DEFAULT NULL,
  `sample_max_item_id` BIGINT UNSIGNED DEFAULT NULL,
  `evaluation_start_time` DATETIME DEFAULT NULL,
  `finish_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `running_guard` TINYINT GENERATED ALWAYS AS
    (CASE WHEN `status` IN ('collecting','evaluating') THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_release_publication` (`publication_audit_id`),
  UNIQUE KEY `uk_prompt_release_one_running` (`running_guard`),
  KEY `idx_prompt_release_status_time` (`status`, `create_time`),
  KEY `idx_prompt_release_published` (`published_prompt_version_id`, `status`),
  CONSTRAINT `fk_prompt_release_audit` FOREIGN KEY (`publication_audit_id`)
    REFERENCES `qj_wardrobe_prompt_version_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_release_optimization` FOREIGN KEY (`optimization_id`)
    REFERENCES `qj_wardrobe_prompt_optimization` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_release_published` FOREIGN KEY (`published_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_release_baseline` FOREIGN KEY (`baseline_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post-publication Prompt regression monitors';

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_release_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `monitor_id` BIGINT NOT NULL,
  `wardrobe_item_id` BIGINT UNSIGNED NOT NULL,
  `status` ENUM('pending','running','completed','failed') NOT NULL DEFAULT 'pending',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `published_evaluation_json` MEDIUMTEXT NOT NULL,
  `baseline_evaluation_json` MEDIUMTEXT,
  `advisor_evaluation_json` MEDIUMTEXT NOT NULL,
  `published_diff_json` MEDIUMTEXT,
  `baseline_diff_json` MEDIUMTEXT,
  `error_msg` TEXT,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `start_time` DATETIME DEFAULT NULL,
  `finish_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_release_result_item` (`monitor_id`, `wardrobe_item_id`),
  KEY `idx_prompt_release_result_status` (`monitor_id`, `status`, `attempt_count`),
  KEY `idx_prompt_release_result_item` (`wardrobe_item_id`),
  CONSTRAINT `fk_prompt_release_result_monitor` FOREIGN KEY (`monitor_id`)
    REFERENCES `qj_wardrobe_prompt_release_monitor` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_release_result_item` FOREIGN KEY (`wardrobe_item_id`)
    REFERENCES `qj_wardrobe_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Isolated predecessor shadow results for release monitoring';

DELIMITER $$

CREATE TRIGGER `trg_prompt_version_audit_no_update`
BEFORE UPDATE ON `qj_wardrobe_prompt_version_audit`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'qj_wardrobe_prompt_version_audit is append-only'$$

CREATE TRIGGER `trg_prompt_version_audit_no_delete`
BEFORE DELETE ON `qj_wardrobe_prompt_version_audit`
FOR EACH ROW
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'qj_wardrobe_prompt_version_audit is append-only'$$

DELIMITER ;
