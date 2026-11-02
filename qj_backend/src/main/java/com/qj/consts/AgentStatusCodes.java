package com.qj.consts;

/**
 * 代理状态常量定义
 * 
 * @author system
 */
public class AgentStatusCodes {

    // 代理状态
    public static final int AGENT_STATUS_VALID = 1; // 有效
    public static final int AGENT_STATUS_PENDING = 2; // 未审批
    public static final int AGENT_STATUS_REJECTED = 3; // 未通过
    public static final int AGENT_STATUS_SUSPENDED = 4; // 暂停
    public static final int AGENT_STATUS_RENEWAL = 5; // 待续费

    // 代理角色类型
    public static final int ROLE_CITY_AGENT = 5; // 城市代理
    public static final int ROLE_PLATFORM_AGENT = 6; // 平台代理
    public static final int ROLE_PERSONAL_AGENT = 7; // 个人代理
    public static final int ROLE_NORMAL_AGENT = 0; // 普通用户

    // 用户行为类型
    public static final int BEHAVIOR_FREE_TEST = 1; // 免费测试
    public static final int BEHAVIOR_FREE_REPORT = 2; // 免费报告
    public static final int BEHAVIOR_PAID_TEST = 3; // 缴费测试
    public static final int BEHAVIOR_PAID_REPORT = 4; // 缴费报告
    public static final int BEHAVIOR_APPLY_CITY_AGENT = 5; // 申请城市代理
    public static final int BEHAVIOR_APPLY_PLATFORM_AGENT = 6; // 申请平台代理
    public static final int BEHAVIOR_APPLY_PERSONAL_AGENT = 7; // 申请个人代理
    public static final int BEHAVIOR_OTHER_CONSUMPTION = 100; // 其他有效消费
    /**
     * 获取状态描述
     */
    public static String getStatusDescription(int status) {
        switch (status) {
            case AGENT_STATUS_VALID:
                return "有效";
            case AGENT_STATUS_PENDING:
                return "未审批";
            case AGENT_STATUS_REJECTED:
                return "未通过";
            case AGENT_STATUS_SUSPENDED:
                return "暂停";
            case AGENT_STATUS_RENEWAL:
                return "待续费";
            default:
                return "未知状态";
        }
    }

    /**
     * 获取角色描述
     */
    public static String getRoleDescription(int role) {
        switch (role) {
            case ROLE_CITY_AGENT:
                return "城市代理";
            case ROLE_PLATFORM_AGENT:
                return "平台代理";
            case ROLE_PERSONAL_AGENT:
                return "个人代理";
            default:
                return "普通用户";
        }
    }

    /**
     * 获取行为类型描述
     */
    public static String getBehaviorDescription(int behaviorType) {
        switch (behaviorType) {
            case BEHAVIOR_FREE_TEST:
                return "免费测试";
            case BEHAVIOR_FREE_REPORT:
                return "免费报告";
            case BEHAVIOR_PAID_TEST:
                return "缴费测试";
            case BEHAVIOR_PAID_REPORT:
                return "缴费报告";
            case BEHAVIOR_APPLY_CITY_AGENT:
                return "申请城市代理";
            case BEHAVIOR_APPLY_PLATFORM_AGENT:
                return "申请平台代理";
            case BEHAVIOR_APPLY_PERSONAL_AGENT:
                return "申请个人代理";
            case BEHAVIOR_OTHER_CONSUMPTION:
                return "其他有效消费";
            default:
                return "未知行为";
        }
    }
}