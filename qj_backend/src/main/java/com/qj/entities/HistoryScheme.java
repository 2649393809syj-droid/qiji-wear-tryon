package com.qj.entities;

import java.util.Date;

/**
 * 历史方案实体类
 */
public class HistoryScheme {
    private Long id;
    private String openid;
    private String operatorOpenid;
    private String collocationData; // JSON字符串
    private String humanData; // JSON字符串
    private Long schemeTime;
    private Integer sourceType; // 1=自己生成 2=代理推送
    private Integer fromShopAdviser;
    private String push;
    private Integer shopId;
    private String customerOpenid;
    private Integer isRead;
    private Integer status;
    private Date createTime;
    private Date updateTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getOperatorOpenid() {
        return operatorOpenid;
    }

    public void setOperatorOpenid(String operatorOpenid) {
        this.operatorOpenid = operatorOpenid;
    }

    public String getCollocationData() {
        return collocationData;
    }

    public void setCollocationData(String collocationData) {
        this.collocationData = collocationData;
    }

    public String getHumanData() {
        return humanData;
    }

    public void setHumanData(String humanData) {
        this.humanData = humanData;
    }

    public Long getSchemeTime() {
        return schemeTime;
    }

    public void setSchemeTime(Long schemeTime) {
        this.schemeTime = schemeTime;
    }

    public Integer getSourceType() {
        return sourceType;
    }

    public void setSourceType(Integer sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getFromShopAdviser() {
        return fromShopAdviser;
    }

    public void setFromShopAdviser(Integer fromShopAdviser) {
        this.fromShopAdviser = fromShopAdviser;
    }

    public String getPush() {
        return push;
    }

    public void setPush(String push) {
        this.push = push;
    }

    public Integer getShopId() {
        return shopId;
    }

    public void setShopId(Integer shopId) {
        this.shopId = shopId;
    }

    public String getCustomerOpenid() {
        return customerOpenid;
    }

    public void setCustomerOpenid(String customerOpenid) {
        this.customerOpenid = customerOpenid;
    }

    public Integer getIsRead() {
        return isRead;
    }

    public void setIsRead(Integer isRead) {
        this.isRead = isRead;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
