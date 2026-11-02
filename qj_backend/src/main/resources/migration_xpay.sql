-- 小程序虚拟支付（xpay / 代币钱包）配置迁移
-- 执行库：qj_minip_v2_t（测试） + qj_minip_v2（生产）
-- 依赖：qj_app_config（migration_app_config.sql）
-- 幂等：可重复执行；用 INSERT ... ON DUPLICATE KEY 写入占位行，不覆盖已回填的真值。
-- 注意：配置存 qj_app_config，AppConfigService 启动时加载，回填后需重启后端生效。
--
-- 占位说明（开通虚拟支付、扫码签约后回填）：
--   xpay_offer_id      → 虚拟支付后台 OfferID（占位 PLACEHOLDER_OFFERID）
--   xpay_app_key       → 虚拟支付 AppKey，敏感（占位 PLACEHOLDER_APPKEY）
--   wx_app_secret      → 小程序 appsecret，敏感，用于 code2session / access_token（占位 __REPLACE_ME__）
--   xpay_env           → 1=沙箱（默认安全），上线改 0=正式
--   xpay_currency_type → 代币类型，缺省 CNY
-- 三个敏感值（offer_id/app_key/wx_app_secret）任一为占位符时，后端视为"虚拟支付未开通"，
-- /qj/xpay/* 接口一律友好拒绝、绝不调微信侧。回填真值 + xpay_env=0 + 重启即生效，无需改码。
--
-- 回填示例（部署时手工执行，勿提交真值进仓库）：
--   UPDATE qj_app_config SET config_value='<真OfferID>' WHERE config_key='xpay_offer_id';
--   UPDATE qj_app_config SET config_value='<真AppKey>'   WHERE config_key='xpay_app_key';
--   UPDATE qj_app_config SET config_value='<真appsecret>' WHERE config_key='wx_app_secret';
--   UPDATE qj_app_config SET config_value='0'            WHERE config_key='xpay_env';

INSERT INTO `qj_app_config` (`config_key`, `config_value`, `description`) VALUES
  ('xpay_offer_id',      'PLACEHOLDER_OFFERID', '虚拟支付 OfferID，开通后回填'),
  ('xpay_app_key',       'PLACEHOLDER_APPKEY',  '虚拟支付 AppKey（敏感），开通后回填'),
  ('wx_app_secret',      '__REPLACE_ME__',      '小程序 appsecret（敏感），code2session/access_token 用'),
  ('xpay_env',           '1',                   '虚拟支付环境：1=沙箱 0=正式'),
  ('xpay_currency_type', 'CNY',                 '代币类型，缺省 CNY')
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`);
-- 注：ON DUPLICATE 仅更新 description，不动 config_value —— 避免重跑把已回填的真值刷回占位符。
