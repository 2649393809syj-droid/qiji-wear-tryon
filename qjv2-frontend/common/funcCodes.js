/**
 * 功能码常量定义
 */

// 基础代码
const INITCODE = 1120000;

// 代理管理相关功能码 (模块号: 301, 功能序号: 4001-4099)
export const AGENT = {
    // 管理端代理审核功能
    LIST: INITCODE + 4021,  // 获取代理列表
    APPROVE: INITCODE + 4022,  // 审批通过
    REJECT: INITCODE + 4023,  // 审批拒绝
    ADD: INITCODE + 4028,  // 新增代理 - 更新为正确的功能码
    GET_BY_OPENID: INITCODE + 4024,  // 获取代理信息
    CREATE_ROLE: INITCODE + 4025,  // 插入代理角色信息
    SUSPEND: INITCODE + 4006,  // 暂停代理
}

// 用户行为管理相关功能码 (模块号: 302, 功能序号: 4104-4105)
export const USER_BEHAVIOR = {
    LIST: INITCODE + 4104,  // 获取用户行为列表
    COUNT: INITCODE + 4105,  // 获取用户行为数量
}

// 推广关系管理相关功能码 (模块号: 303, 功能序号: 4205-4206)
export const AGENT_RELATION = {
    LIST: INITCODE + 4205,  // 获取推广关系列表
    COUNT: INITCODE + 4206,  // 获取推广关系数量
}

// 日志管理相关功能码 (模块号: 304, 功能序号: 4301-4302)
export const LOG_MANAGEMENT = {
    LIST: INITCODE + 4301,  // 获取日志列表
    COUNT: INITCODE + 4302,  // 获取日志数量
}

// 代理审批推广关系变动日志相关功能码 (模块号: 303, 功能序号: 4212)
export const AGENT_PROMOTION_LOG = {
    LIST: INITCODE + 4212,  // 获取推广关系变动日志列表
}

// 用户信息相关功能码 (模块号: 101, 功能序号: 2012)
export const MEMBER = {
    GET_NICKNAME: INITCODE + 2012,  // 获取用户昵称
}

// 用户相关功能码 (模块号: 101, 功能序号: 1001-1999)
export const USER = {
    // 新增用户
    CREATE: INITCODE + 1001,
    // 查询用户
    GET: INITCODE + 1002,
    // 修改用户信息
    MODIFY: INITCODE + 1003,
    // 删除用户
    REMOVE: INITCODE + 1004,
    // 登录 - 账号密码
    LOGIN: INITCODE + 1005,
    // 分页查询用户
    PAGING_QUERY: INITCODE + 1007,
    // 查询所有用户
    GET_ALL: INITCODE + 1008,
    // 条件查询用户信息
    GET_BY_CONDITION: INITCODE + 1009,
    // 修改密码
    PASSWORD_UPDATE: INITCODE + 1010,
    // 添加微信用户
    WX_CREATE: INITCODE + 1011,
    // 查询微信用户信息
    WX_GET: INITCODE + 1012,
    // 修改微信用户信息
    WX_MODIFY: INITCODE + 1013,
    // 删除微信用户
    WX_REMOVE: INITCODE + 1014,
    // 登录 - 微信扫码
    WX_LOGIN: INITCODE + 1015,
    // 分页查询微信用户
    WX_PAGING_QUERY: INITCODE + 1016,
    // 条件查询微信用户
    WX_GET_BY_CONDITION: INITCODE + 1017,
    // 注册
    REGISTER: INITCODE + 1021,
    // 查询企业信息
    ENTERPRISE_GET: INITCODE + 1031,
    // wx_user与users表-连表查询
    WXUSER_LEFT_JOIN_USER_GET: INITCODE + 1032,
}

// 模块名常量
export const MODULE_NAMES = {
    AGENT: 'qj_marketing_v2_agents',
    USER: 'qj_user',
    WX_USER: 'qj_wx_user',
    AGENT_PROMOTION_LOG: 'qj_marketing_v2_agent_promotion_logs'
}

// 操作类型常量
export const ACTION_TYPES = {
    QUERY: 'query',
    CREATE: 'create',
    UPDATE: 'update',
    DELETE: 'delete'
}

// 导出基础代码
export { INITCODE }