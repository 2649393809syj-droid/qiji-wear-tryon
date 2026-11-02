-- 应用通用配置表（启动加载，重启生效）
-- 执行库：qj_minip_v2_t（测试） + qj_minip_v2（生产）
-- 幂等：可重复执行。已有真值的 ark_api_key 行不会被占位符覆盖。
--
-- 如果该库存在旧表 qj_payment_config（上一波支付配置迁移留下的），先手动跑：
--   RENAME TABLE qj_payment_config TO qj_app_config;
-- 然后再执行本文件。
-- 如果是全新库，直接执行本文件即可。

CREATE TABLE IF NOT EXISTS `qj_app_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL,
  `config_value` TEXT NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用通用配置（支付凭据 + 第三方 API Key 等，启动加载，重启生效）';

-- 微信支付配置（值固定，重跑会覆盖回这些值）
INSERT INTO `qj_app_config` (`config_key`, `config_value`, `description`) VALUES
  ('wxpay_v2_key',         '434217fca82449440f8a61415520d1d9',                'V2 商户密钥（MD5 签名 / 验签）'),
  ('wxpay_mchid',          '1494638302',                                       '商户号（与 cloudfunctions/aiTryOnPay 共用）'),
  ('wxpay_appid',          'wx05c823efd45071c2',                               '小程序 appid'),
  ('wxpay_orderquery_url', 'https://api.mch.weixin.qq.com/pay/orderquery',     'V2 orderquery 反查地址')
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `description`  = VALUES(`description`);

-- Ark API Key（占位符）。INSERT IGNORE：已有真值的行不会被覆盖回占位符。
-- 上线后必须手动 UPDATE 真值：
--   UPDATE qj_app_config SET config_value='<真实 ARK_API_KEY>' WHERE config_key='ark_api_key';
INSERT IGNORE INTO `qj_app_config` (`config_key`, `config_value`, `description`) VALUES
  ('ark_api_key', '__REPLACE_ME__', 'Ark/Doubao Seedream API Key');
