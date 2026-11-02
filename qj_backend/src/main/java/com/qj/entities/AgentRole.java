package com.qj.entities;

/**
 * 代理角色实体类
 * 
 * @author system
 */
public class AgentRole {
    private Long id;
    private String name; // 城市/平台/个人
    private Integer testTimes; // 扫码用户能享受的免费总次数
    private Integer testReportTimes; // 免费报告次数
    private Long currentPrice; // 当前价格（分）
    private Long renewPrice; // 续费价格（分）
    private Integer status; // 状态：1启用/0禁用
    private String description; // 描述
    private Long originalPrice;
    private Double commissionRate; // 如为金额请改为 int，否则保留
    private String permissions;
    private Integer sortOrder;

    public AgentRole() {
    }

    public Long getId() {
        return id;
    }

    public AgentRole setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public AgentRole setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getTestTimes() {
        return testTimes;
    }

    public AgentRole setTestTimes(Integer testTimes) {
        this.testTimes = testTimes;
        return this;
    }

    public Integer getTestReportTimes() {
        return testReportTimes;
    }

    public AgentRole setTestReportTimes(Integer testReportTimes) {
        this.testReportTimes = testReportTimes;
        return this;
    }

    public Long getCurrentPrice() {
        return currentPrice;
    }

    public AgentRole setCurrentPrice(Long currentPrice) {
        this.currentPrice = currentPrice;
        return this;
    }

    public Long getRenewPrice() {
        return renewPrice;
    }

    public AgentRole setRenewPrice(Long renewPrice) {
        this.renewPrice = renewPrice;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public AgentRole setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AgentRole setDescription(String description) {
        this.description = description;
        return this;
    }

    public Long getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Long originalPrice) {
        this.originalPrice = originalPrice;
    }

    public Double getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(Double commissionRate) {
        this.commissionRate = commissionRate;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "AgentRole{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", testTimes=" + testTimes +
                ", testReportTimes=" + testReportTimes +
                ", currentPrice=" + currentPrice +
                ", renewPrice=" + renewPrice +
                ", status=" + status +
                ", description='" + description + '\'' +
                '}';
    }
}