package com.qj.entities;

import java.util.Date;

/**
 * 代理实体类
 * 
 * @author system
 */
public class Agent {
    private Long id;
    private String openid;
    private String name;
    private Integer status; // 1有效/2未审批/3未通过/4暂停/5待续费
    private Date expiredDate; // 过期时间
    private Integer role; // 角色：1城市代理/2平台代理/3个人代理
    private Integer testTimes; // 形象测试次数
    private Integer testReportTimes; // 形象测试报告次数
    private String city; // 城市
    private String fromOpenid; // 推荐人
    private Date joinDate; // 加入时间
    private Date createTime; // 创建时间
    private Date updateTime; // 更新时间

    public Agent() {
    }

    public Long getId() {
        return id;
    }

    public Agent setId(Long id) {
        this.id = id;
        return this;
    }

    public String getOpenid() {
        return openid;
    }

    public Agent setOpenid(String openid) {
        this.openid = openid;
        return this;
    }

    public String getName() {
        return name;
    }

    public Agent setName(String name) {
        this.name = name;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public Agent setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public Date getExpiredDate() {
        return expiredDate;
    }

    public Agent setExpiredDate(Date expiredDate) {
        this.expiredDate = expiredDate;
        return this;
    }

    public Integer getRole() {
        return role;
    }

    public Agent setRole(Integer role) {
        this.role = role;
        return this;
    }

    public Integer getTestTimes() {
        return testTimes;
    }

    public Agent setTestTimes(Integer testTimes) {
        this.testTimes = testTimes;
        return this;
    }

    public Integer getTestReportTimes() {
        return testReportTimes;
    }

    public Agent setTestReportTimes(Integer testReportTimes) {
        this.testReportTimes = testReportTimes;
        return this;
    }

    public String getCity() {
        return city;
    }

    public Agent setCity(String city) {
        this.city = city;
        return this;
    }

    public String getFromOpenid() {
        return fromOpenid;
    }

    public Agent setFromOpenid(String fromOpenid) {
        this.fromOpenid = fromOpenid;
        return this;
    }

    public Date getJoinDate() {
        return joinDate;
    }

    public Agent setJoinDate(Date joinDate) {
        this.joinDate = joinDate;
        return this;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public Agent setCreateTime(Date createTime) {
        this.createTime = createTime;
        return this;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public Agent setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    @Override
    public String toString() {
        return "Agent{" +
                "id=" + id +
                ", openid='" + openid + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", expiredDate=" + expiredDate +
                ", role=" + role +
                ", testTimes=" + testTimes +
                ", testReportTimes=" + testReportTimes +
                ", city='" + city + '\'' +
                ", fromOpenid='" + fromOpenid + '\'' +
                ", joinDate=" + joinDate +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}