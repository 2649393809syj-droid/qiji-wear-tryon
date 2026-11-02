-- Private wardrobe AI style recognition and advisor review.
-- Target database: qj_minip_v2_t for dev/test, qj_minip_v2 for production.

CREATE TABLE IF NOT EXISTS `qj_wardrobe_item` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(64) NOT NULL COMMENT 'User openid',
  `image_url` VARCHAR(512) NOT NULL COMMENT 'Clothing image URL',
  `cloud_file_id` VARCHAR(255) DEFAULT NULL COMMENT 'Original client cloud file id',
  `final_style_json` TEXT DEFAULT NULL COMMENT 'Final approved 8-style percentage JSON',
  `status` VARCHAR(32) NOT NULL DEFAULT 'pending_ai' COMMENT 'pending_ai/pending_review/ai_failed/approved',
  `approved_history_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'Approved history id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_openid_status_time` (`openid`, `status`, `create_time`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wardrobe clothing final data';

CREATE TABLE IF NOT EXISTS `qj_wardrobe_style_history` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `wardrobe_item_id` BIGINT UNSIGNED NOT NULL COMMENT 'qj_wardrobe_item.id',
  `round_no` INT NOT NULL DEFAULT 1 COMMENT 'AI recognition round number',
  `prompt_version` VARCHAR(32) NOT NULL DEFAULT 'wardrobe-style-v1',
  `prompt_text` TEXT DEFAULT NULL COMMENT 'Prompt used in this round',
  `ai_style_json` TEXT DEFAULT NULL COMMENT 'AI generated 8-style percentage JSON',
  `advisor_style_json` TEXT DEFAULT NULL COMMENT 'Advisor corrected/confirmed 8-style percentage JSON',
  `advisor_feedback` TEXT DEFAULT NULL COMMENT 'Advisor return feedback',
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'ai_generated' COMMENT 'ai_generated/approved/returned/ai_failed',
  `reviewer_openid` VARCHAR(64) DEFAULT NULL COMMENT 'Advisor openid',
  `gemini_raw_response` MEDIUMTEXT DEFAULT NULL COMMENT 'Raw Gemini response',
  `error_msg` VARCHAR(1024) DEFAULT NULL COMMENT 'AI failure message',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `review_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_item_round` (`wardrobe_item_id`, `round_no`),
  KEY `idx_review_status_time` (`review_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wardrobe AI and advisor review history';
