package com.qj.entities;

import cn.hutool.json.JSONObject;

/**
 * 代理响应实体类
 * 
 * @author system
 */
public class AgentResponse {
    private boolean success;
    private String message;
    private Object data;
    private String funcCode;
    private String qrcodeUrl; // 支付二维码URL
    private String orderId; // 订单ID
    private Long amount; // 金额（分）
    private Integer agentRole; // 代理角色
    private int errCode;
    private String errMsg;
    private Object result;

    public AgentResponse() {
    }

    public AgentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public AgentResponse(int errCode, String errMsg, Object result) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.result = result;
        this.success = errCode == 0;
        this.message = errMsg;
        this.data = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public AgentResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public AgentResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public Object getData() {
        return data;
    }

    public AgentResponse setData(Object data) {
        this.data = data;
        return this;
    }

    public String getFuncCode() {
        return funcCode;
    }

    public AgentResponse setFuncCode(String funcCode) {
        this.funcCode = funcCode;
        return this;
    }

    public String getQrcodeUrl() {
        return qrcodeUrl;
    }

    public AgentResponse setQrcodeUrl(String qrcodeUrl) {
        this.qrcodeUrl = qrcodeUrl;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public AgentResponse setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public Long getAmount() {
        return amount;
    }

    public AgentResponse setAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public Integer getAgentRole() {
        return agentRole;
    }

    public AgentResponse setAgentRole(Integer agentRole) {
        this.agentRole = agentRole;
        return this;
    }

    public static AgentResponse success() {
        return new AgentResponse(true, "操作成功");
    }

    public static AgentResponse success(String message) {
        return new AgentResponse(0, message, null);
    }

    public static AgentResponse success(Object data) {
        return new AgentResponse(true, "操作成功").setData(data);
    }

    public static AgentResponse success(String message, Object data) {
        return new AgentResponse(0, message, data);
    }

    public static AgentResponse fail(String message) {
        return new AgentResponse(500, message, null);
    }

    public static AgentResponse fail(String funcCode, String message) {
        return new AgentResponse(false, message).setFuncCode(funcCode);
    }

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}