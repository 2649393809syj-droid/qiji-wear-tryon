package com.qj.entities;

import java.util.Date;

/**
 * 推广者实体类
 * 
 * @author system
 */
public class Spreader {
    private Long id;
    private String openid;
    private String qrcode;
    private String nickname;
    private String avatarUrl;
    private String mobile;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private Integer isDel;

    public Spreader() {
    }

    public Long getId() {
        return id;
    }

    public Spreader setId(Long id) {
        this.id = id;
        return this;
    }

    public String getOpenid() {
        return openid;
    }

    public Spreader setOpenid(String openid) {
        this.openid = openid;
        return this;
    }

    public String getQrcode() {
        return qrcode;
    }

    public Spreader setQrcode(String qrcode) {
        this.qrcode = qrcode;
        return this;
    }

    public String getNickname() {
        return nickname;
    }

    public Spreader setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Spreader setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public String getMobile() {
        return mobile;
    }

    public Spreader setMobile(String mobile) {
        this.mobile = mobile;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public Spreader setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Spreader setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public Spreader setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public Integer getIsDel() {
        return isDel;
    }

    public Spreader setIsDel(Integer isDel) {
        this.isDel = isDel;
        return this;
    }

    @Override
    public String toString() {
        return "Spreader{" +
                "id=" + id +
                ", openid='" + openid + '\'' +
                ", qrcode='" + qrcode + '\'' +
                ", nickname='" + nickname + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", mobile='" + mobile + '\'' +
                ", status=" + status +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", isDel=" + isDel +
                '}';
    }
}