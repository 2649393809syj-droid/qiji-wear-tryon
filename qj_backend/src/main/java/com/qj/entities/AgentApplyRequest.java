package com.qj.entities;

import lombok.Data;

/**
 * 代理申请请求实体
 */
@Data
public class AgentApplyRequest {
    /**
     * 用户openid
     */
    private String openid;

    /**
     * 代理角色
     */
    private Integer agentRole;

    /**
     * 订单号
     */
    private String tradeNo;

    /**
     * 微信支付交易号
     */
    private String transactionId;

    /**
     * 代理城市（城市代理必填）
     */
    private String city;

    /**
     * 推荐人openid
     */
    private String fromOpenid;
}