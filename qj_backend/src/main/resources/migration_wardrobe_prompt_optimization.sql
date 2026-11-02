-- Wardrobe Prompt versioning, optimization jobs, and isolated experiment results.
-- Apply after migration_wardrobe_ai_single_pass.sql. MySQL 5.7 compatible and replay-safe.

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `version_name` VARCHAR(128) NOT NULL,
  `prompt_text` MEDIUMTEXT NOT NULL,
  `status` ENUM('candidate','ready_for_review','active','retired') NOT NULL,
  `source` ENUM('seed','manual','ai_generated') NOT NULL,
  `parent_version_id` BIGINT DEFAULT NULL,
  `summary` TEXT DEFAULT NULL,
  `created_by` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `published_by` VARCHAR(64) DEFAULT NULL,
  `publish_time` DATETIME DEFAULT NULL,
  `retired_time` DATETIME DEFAULT NULL,
  `active_guard` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = 'active' THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_version_name` (`version_name`),
  UNIQUE KEY `uk_prompt_version_single_active` (`active_guard`),
  KEY `idx_prompt_version_status_time` (`status`, `create_time`),
  KEY `idx_prompt_version_parent` (`parent_version_id`),
  CONSTRAINT `fk_prompt_version_parent` FOREIGN KEY (`parent_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned wardrobe recognition Prompts';

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_optimization` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `status` ENUM('pending','running','completed','failed') NOT NULL DEFAULT 'pending',
  `base_prompt_version_id` BIGINT NOT NULL,
  `candidate_prompt_version_id` BIGINT DEFAULT NULL,
  `filter_json` TEXT DEFAULT NULL,
  `sample_ids_json` MEDIUMTEXT NOT NULL,
  `analysis_summary` TEXT DEFAULT NULL,
  `error_msg` TEXT DEFAULT NULL,
  `created_by` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `start_time` DATETIME DEFAULT NULL,
  `finish_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_prompt_optimization_status_time` (`status`, `create_time`),
  KEY `idx_prompt_optimization_base` (`base_prompt_version_id`),
  KEY `idx_prompt_optimization_candidate` (`candidate_prompt_version_id`),
  CONSTRAINT `fk_prompt_optimization_base` FOREIGN KEY (`base_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_optimization_candidate` FOREIGN KEY (`candidate_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt candidate generation jobs over immutable sample snapshots';

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_experiment_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `optimization_id` BIGINT NOT NULL,
  `candidate_prompt_version_id` BIGINT NOT NULL,
  `baseline_prompt_version_id` BIGINT NOT NULL,
  `wardrobe_item_id` BIGINT UNSIGNED NOT NULL,
  `status` ENUM('pending','running','completed','failed') NOT NULL DEFAULT 'pending',
  `baseline_evaluation_json` MEDIUMTEXT DEFAULT NULL,
  `candidate_evaluation_json` MEDIUMTEXT DEFAULT NULL,
  `advisor_evaluation_json` MEDIUMTEXT NOT NULL,
  `baseline_diff_json` MEDIUMTEXT DEFAULT NULL,
  `candidate_diff_json` MEDIUMTEXT DEFAULT NULL,
  `error_msg` TEXT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finish_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_experiment_item` (`optimization_id`, `wardrobe_item_id`),
  KEY `idx_prompt_experiment_optimization` (`optimization_id`, `status`),
  KEY `idx_prompt_experiment_candidate` (`candidate_prompt_version_id`, `status`),
  KEY `idx_prompt_experiment_item` (`wardrobe_item_id`),
  CONSTRAINT `fk_prompt_experiment_optimization` FOREIGN KEY (`optimization_id`)
    REFERENCES `qj_wardrobe_prompt_optimization` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_experiment_candidate` FOREIGN KEY (`candidate_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_experiment_baseline` FOREIGN KEY (`baseline_prompt_version_id`)
    REFERENCES `qj_wardrobe_prompt_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_prompt_experiment_item` FOREIGN KEY (`wardrobe_item_id`)
    REFERENCES `qj_wardrobe_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offline Prompt comparison results; never changes wardrobe business records';

-- This insert only initializes a new environment. Replaying the migration never changes
-- the active version selected later by publish or rollback operations.
INSERT INTO `qj_wardrobe_prompt_version`
  (`version_name`, `prompt_text`, `status`, `source`, `summary`, `created_by`, `published_by`, `publish_time`)
SELECT
  'wardrobe-style-legacy-v1',
  'You are a professional fashion style recognition assistant. Analyze only the clothing item in the image.
Return only one JSON object. Do not use Markdown. Do not include explanations. Use exactly these top-level fields: types, clothingType, cloComplexion, parkValue, mainColorValue, otherColorValue, isException, seasonMatches.
types is an array of non-zero style entries. Each entry has type and typeProportion. type must be one of 自然,优雅,浪漫,古典,戏剧,前卫,少年,少女. typeProportion must be an integer percentage string from 0 to 100, for example 70%. Each style relevance score is independent; do not make the scores sum to 100. Important scoring rule: return exactly 1 or 2 non-zero styles and never more than 2. Set every weak, generic, or uncertain style to 0. Do not give small filler scores.
clothingType is concise free text, or an empty string when uncertain.
cloComplexion is an object with exactly colorDegree, lusterDegree, tone, vividness. Use only these Chinese values: colorDegree=偏白,略白,偏黑,略黑; lusterDegree=偏光泽,略光泽,偏黯淡,略黯淡; tone=偏净,略净,偏浊,略浊; vividness=偏艳,略艳,偏素,略素.
parkValue must be one of 偏花,略花,偏朴,略朴. mainColorValue and each otherColorValue must be one of 红,橙,黄,绿,蓝,紫,黑,白,灰,金,银. isException must be a boolean. seasonMatches contains zero or more of 春,夏,秋,冬.
Example output: {"types":[{"type":"自然","typeProportion":"70%"}],"clothingType":"X型","cloComplexion":{"colorDegree":"略白","lusterDegree":"略光泽","tone":"略净","vividness":"略素"},"parkValue":"略朴","mainColorValue":"白","otherColorValue":["蓝"],"isException":false,"seasonMatches":["春","夏"]}',
  'active',
  'seed',
  'Initial aligned legacy wardrobe evaluation Prompt',
  'system',
  'system',
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM `qj_wardrobe_prompt_version`
  WHERE `version_name` = 'wardrobe-style-legacy-v1'
);
