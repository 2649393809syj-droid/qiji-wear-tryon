package com.qj.entities;

import java.util.Date;

/**
 * 方案推送通知实体类
 */
public class SchemeNotification {
    private Long id;
    private String targetOpenid;
    private Long schemeId;
    private String operatorOpenid;
    private Integer isRead; // 0=未读 1=已读
    private Date createTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetOpenid() {
        return targetOpenid;
    }

    public void setTargetOpenid(String targetOpenid) {
        this.targetOpenid = targetOpenid;
    }

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public String getOperatorOpenid() {
        return operatorOpenid;
    }

    public void setOperatorOpenid(String operatorOpenid) {
        this.operatorOpenid = operatorOpenid;
    }

    public Integer getIsRead() {
        return isRead;
    }

    public void setIsRead(Integer isRead) {
        this.isRead = isRead;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
