package com.qj.consts;

/**
 * 功能码
 * 命名规范：F_模块号_功能序号_功能名称 = INITCODE + 功能序号
 */
public class FunctionCodes {
    public static final int INITCODE = 1120000;

    /**
     * qj_shop_order 相关 (模块号: 201, 功能序号: 3001-3999)
     */
    // 基础订单操作
    public static final int F_201_3006_ORDER_GET_BY_TRADE_NO = INITCODE + 3006;
    public static final int F_201_3007_ORDER_CREATE = INITCODE + 3007;

    /**
     * qj_marketing_v2_agents 相关 (模块号: 301, 功能序号: 4001-4099)
     */
    // 基础代理操作
    public static final int F_301_4002_AGENT_GET = INITCODE + 4002;
    public static final int F_301_4003_AGENT_GET_BY_OPENID = INITCODE + 4003;
    public static final int F_301_4004_AGENT_CREATE = INITCODE + 4004;
    public static final int F_301_4005_AGENT_UPDATE = INITCODE + 4005;
    public static final int F_301_4006_AGENT_UPDATE_STATUS = INITCODE + 4006;
    public static final int F_301_4009_AGENT_DELETE = INITCODE + 4009;

    // 代理业务功能
    public static final int F_301_4011_AGENT_GET_USER_IDENTITY = INITCODE + 4011;
    public static final int F_301_4012_AGENT_APPLY_SUCCESS = INITCODE + 4012;
    public static final int F_301_4013_AGENT_GET_STATISTICS = INITCODE + 4013;
    public static final int F_301_4014_AGENT_GET_PROMOTION_INFO = INITCODE + 4014; // 获取用户推广信息
    public static final int F_301_4015_CHECK_TEST_QUALIFICATION = INITCODE + 4015; // 校验用户是否有免费形象测试资格

    // 代理关系查询功能
    public static final int F_301_4045_AGENT_GET_BY_SHOP_ID = INITCODE + 4045; // 根据商铺ID获取代理openid

    // 管理端代理审核功能
    public static final int F_301_4021_ADMIN_GET_AGENT_LIST = INITCODE + 4021;
    public static final int F_301_4022_ADMIN_APPROVE_AGENT = INITCODE + 4022;
    public static final int F_301_4023_ADMIN_REJECT_AGENT = INITCODE + 4023;
    public static final int F_301_4024_ADMIN_GET_AGENT_INFO = INITCODE + 4024; // 获取代理信息
    public static final int F_301_4025_ADMIN_INSERT_AGENT_ROLE = INITCODE + 4025; // 插入代理角色信息
    public static final int F_301_4026_ADMIN_GET_AGENT_COUNT = INITCODE + 4026; // 获取代理总数
    public static final int F_301_4027_ADMIN_GET_AGENT_LIST_WITH_FILTER = INITCODE + 4027; // 获取带筛选条件的代理列表
    public static final int F_301_4028_ADMIN_ADD_AGENT = INITCODE + 4028; // 管理端添加代理
    public static final int F_301_4001_AGENT_LIST = INITCODE + 4001; // 获取代理列表
    public static final int F_301_4049_ADMIN_ADD_TEMPORARY_AGENT = INITCODE + 4049; // 添加临时代理
    public static final int F_301_4050_ADMIN_CHECK_AGENT_EXISTS = INITCODE + 4050; // 检查代理是否存在
    public static final int F_301_4051_AGENT_GET_INFO = INITCODE + 4051; // 获取代理信息（含查询权限）- 小程序端使用
    public static final int F_301_4052_AGENT_UPDATE_QUERY_AUTH = INITCODE + 4052; // 更新代理查询权限 - 管理端使用

    /**
     * qj_marketing_v2_agent_config 相关 (模块号: 301, 功能序号: 4060-4069)
     */
    public static final int F_301_4060_AGENT_CONFIG_LIST = INITCODE + 4060; // 获取代理配置列表 - 小程序端使用

    /**
     * qj_marketing_v2_user_behavior 相关 (模块号: 302, 功能序号: 4101-4199)
     */
    // 用户行为记录
    public static final int F_302_4103_USER_BEHAVIOR_CREATE = INITCODE + 4103;
    public static final int F_302_4101_USER_BEHAVIOR_LIST = INITCODE + 4101;
    public static final int F_302_4104_ADMIN_USER_BEHAVIOR_LIST = INITCODE + 4104; // 管理端用户行为列表
    public static final int F_302_4105_ADMIN_USER_BEHAVIOR_COUNT = INITCODE + 4105; // 管理端用户行为统计

    /**
     * qj_marketing_v2_agent_users 相关 (模块号: 303, 功能序号: 4201-4299)
     */
    // 推广关系操作
    public static final int F_303_4201_AGENT_USER_CREATE = INITCODE + 4201;
    public static final int F_303_4202_AGENT_USER_CHECK_EXISTS = INITCODE + 4202;
    public static final int F_303_4203_AGENT_USER_GET_BY_USER = INITCODE + 4203;
    public static final int F_303_4204_AGENT_USER_GET_BY_AGENT = INITCODE + 4204;
    public static final int F_303_4205_ADMIN_AGENT_USER_LIST = INITCODE + 4205; // 管理端推广关系列表
    public static final int F_303_4206_ADMIN_AGENT_USER_COUNT = INITCODE + 4206; // 管理端推广关系统计
    public static final int F_303_4207_AGENT_USER_DELETE = INITCODE + 4207; // 删除推广关系
    public static final int F_303_4208_AGENT_USER_UPDATE = INITCODE + 4208; // 更新推广关系
    public static final int F_303_4209_AGENT_PROMOTION_LOG_CREATE = INITCODE + 4209; // 创建代理审批推广关系变动日志
    public static final int F_303_4201_AGENT_USER_LIST = INITCODE + 4209; // 获取所有推广关系列表
    public static final int F_303_4210_AGENT_USER_GET_SUPERIOR = INITCODE + 4210; // 根据用户openid获取上级代理openid
    public static final int F_303_4211_AGENT_USER_DELETE_ALL_SUPERIOR = INITCODE + 4211; // 删除用户的所有上级推广关系
    public static final int F_303_4212_ADMIN_GET_PROMOTION_LOGS = INITCODE + 4212; // 管理端获取代理审批推广关系变动日志列表

    /**
     * qj_marketing_v2_promo_codes 相关 (模块号: 304, 功能序号: 4301-4399)
     * 代理动态推广码：每次生成唯一 token，可追踪扫码/绑定数、可设过期
     */
    public static final int F_304_4301_PROMO_CODE_CREATE = INITCODE + 4301; // 新建一张动态推广码
    public static final int F_304_4302_PROMO_CODE_GET_BY_TOKEN = INITCODE + 4302; // 按 token 查推广码
    public static final int F_304_4303_PROMO_CODE_INC_SCAN = INITCODE + 4303; // scan_count + 1
    public static final int F_304_4304_PROMO_CODE_INC_BIND = INITCODE + 4304; // bind_count + 1
    public static final int F_304_4305_PROMO_CODE_LIST_BY_AGENT = INITCODE + 4305; // 代理查自己各码战绩

    /**
     * qj_member 相关 (模块号: 101, 功能序号: 2001-2099)
     */
    // 会员基础操作
    public static final int F_101_2001_MEMBER_LIST = INITCODE + 2001;
    public static final int F_101_2001_MEMBER_LIST_ALL = INITCODE + 2005;
    public static final int F_101_2001_MEMBER_LIST_BY_ID = INITCODE + 2011;
    public static final int F_101_2002_MEMBER_UPDATE = INITCODE + 2002;
    public static final int F_101_2012_MEMBER_GET_NICKNAME = INITCODE + 2012; // 根据openid获取昵称

    /**
     * 商城商品相关 (模块号: 501, 功能序号: 6001-6099)
     */
    // 商品基础操作
    public static final int F_501_6001_COMMODITY_LIST = INITCODE + 6001; // 商品分页列表
    public static final int F_501_6002_COMMODITY_STAR_LIST = INITCODE + 6002; // 推荐商品列表
    public static final int F_501_6003_COMMODITY_GET_BY_ID = INITCODE + 6003; // 根据ID获取商品详情
    public static final int F_501_6004_COMMODITY_TYPES_LIST = INITCODE + 6004; // 获取商品分类列表
    public static final int F_501_6005_COMMODITY_TYPES_BY_SHOP = INITCODE + 6005; // 根据商铺获取商品分类
    public static final int F_501_6006_COMMODITY_SCREEN_LIST = INITCODE + 6006; // 商品筛选列表
    public static final int F_501_6007_COMMODITY_SEARCH = INITCODE + 6007; // 商品搜索
    public static final int F_501_6008_COMMODITY_UNIVERSAL_LIST = INITCODE + 6008; // 万能款商品列表
    public static final int F_501_6012_COMMODITY_SPEC_LIST = INITCODE + 6012; // 根据商品ID获取规格列表
    public static final int F_501_6016_COMMODITY_COUNT = INITCODE + 6016; // 获取商品总数
    public static final int F_501_6017_COMMODITY_STAR_COUNT = INITCODE + 6017; // 获取推荐商品总数
    public static final int F_501_6018_COMMODITY_SEARCH_COUNT = INITCODE + 6018; // 根据条件获取商品总数

    // 品牌相关操作 - 按照原qjapi逻辑，只保留实际存在的接口
    public static final int F_501_6020_COMMODITY_TYPES_BY_BRAND = INITCODE + 6020; // 根据品牌获取商品分类

    // 智能搭配相关操作 (功能序号: 6030-6049)
    public static final int F_501_6030_SMART_MATCH_COMMODITIES = INITCODE + 6030; // 根据类型、优先级、季节获取智能搭配商品
    public static final int F_501_6031_SMART_MATCH_COMMODITIES_BY_STYLE = INITCODE + 6031; // 根据风格代码获取智能搭配商品
    public static final int F_501_6032_SMART_MATCH_COMMODITIES_BY_SPEC = INITCODE + 6032; // 根据规格获取智能搭配商品
    public static final int F_501_6033_SMART_MATCH_STYLE_LIST = INITCODE + 6033; // 获取智能搭配风格列表
    public static final int F_501_6034_SMART_MATCH_ORNAMENTS = INITCODE + 6034; // 获取指定风格的智能搭配饰品
    public static final int F_501_6035_SMART_MATCH_COMMODITIES_BY_SHOP = INITCODE + 6035; // 根据商铺ID获取智能搭配商品
    public static final int F_501_6036_COMMODITY_FILTER_QUERY = INITCODE + 6036; // 商品筛选查询
    public static final int F_501_6037_COMMODITY_FILTER_COUNT = INITCODE + 6037; // 商品筛选总数查询
    public static final int F_501_6038_SHOP_TYPE_CHECK = INITCODE + 6038; // 检查商铺是否有指定类型商品
    public static final int F_501_6039_GET_SHOP_BY_NAME = INITCODE + 6039; // 根据商铺名称获取商铺信息

    public static final int F_501_6040_GET_USER_PRIORITY = INITCODE + 6040; // 根据用户openid计算风格优先级
    /**
     * 商城商铺相关 (模块号: 502, 功能序号: 6101-6199)
     */
    // 商铺基础操作
    public static final int F_502_6101_SHOP_LIST = INITCODE + 6101; // 普通用户商铺列表
    public static final int F_502_6102_SHOP_GET_BY_ID = INITCODE + 6102; // 根据ID获取商铺详情
    public static final int F_502_6103_SHOP_GET_BY_OPENID = INITCODE + 6103; // 根据openid获取商铺ID
    public static final int F_502_6104_SHOP_FLAT_LIST = INITCODE + 6104; // 平台代理商铺列表
    public static final int F_502_6105_SHOP_SITY_LIST = INITCODE + 6105; // 城市代理用户商铺列表

    public static final int F_502_6107_SHOP_BRAND_LIST = INITCODE + 6107; // 品牌商铺列表
    public static final int F_502_6113_SHOP_COUNT = INITCODE + 6113; // 获取商铺总数
    public static final int F_502_6114_SHOP_BRAND_COUNT = INITCODE + 6114; // 获取品牌商铺总数

    /**
     * 商城订单相关 (模块号: 503, 功能序号: 6201-6299)
     */
    // 订单基础操作
    public static final int F_503_6201_ORDER_LIST = INITCODE + 6201; // 订单列表
    public static final int F_503_6202_ORDER_GET_BY_ID = INITCODE + 6202; // 根据ID获取订单详情
    public static final int F_503_6203_ORDER_GET_BY_TRADE_NO = INITCODE + 6203; // 根据交易号获取订单
    public static final int F_503_6204_ORDER_CREATE = INITCODE + 6204; // 创建订单
    public static final int F_503_6209_ORDER_GET_BY_OPENID = INITCODE + 6209; // 根据openid获取订单列表

    // 角色管理相关
    // 获取用户角色
    public static final int F_101_1436_USER_GET_ROLE = INITCODE + 1436;
    public static final int F_101_1625_ROLE_GET_PERMISSIONS = INITCODE + 1625;
    public static final int F_101_1637_FUNCTION_CHECK_EXISTS = INITCODE + 1637;

    /**
     * 登录相关 (模块号: 001, 功能序号: 0001-0099)
     */
    public static final int F_001_0001_USER_LOGIN = 1000001;

    public static final int F_001_0002_USER_REGISTER = 1000002;

    /**
     * 个人形象特质测试报告相关 (模块号: 401, 功能序号: 5001-5099)
     */
    public static final int F_401_5001_REPORT_GET_CLIENT_REPORT = INITCODE + 5001; // 获取客户报告详情
    public static final int F_401_5002_REPORT_GET_PRICE = INITCODE + 5002; // 获取报告价格
    public static final int F_401_5003_REPORT_UPDATE_BUY_STATUS = INITCODE + 5003; // 更新购买状态
    public static final int F_401_5006_REPORT_AFTER_PAID = INITCODE + 5006; // 支付后生成报告
    public static final int F_401_5007_REPORT_CHECK_AND_UPDATE_QUALIFICATION = INITCODE + 5007; // 检查并更新资格
    public static final int F_401_5008_REPORT_GET_USER_INFO = INITCODE + 5008; // 获取用户报告渲染数据
}
