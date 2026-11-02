package com.qj.entities;

import java.util.Date;
import java.sql.Timestamp;

/**
 * 用户信息实体类
 * 对应qj_member表
 * 
 * @author system
 */
public class Member {
    private Integer id;
    private String _id;
    private String openid;
    private String appId;
    private String avatarUrl;
    private String flag;
    private String qrcodeUrl;
    private Integer grade;
    private String nickName;
    private Character isAnswer;
    private Character isCustomers;
    private Character isEvaluation;
    private Character isInsider;
    private Date loginTime;
    private Date registrationTime;
    private Integer wardrobeQuota;
    private Integer wardrobeBuyQuota;
    private Integer wardrobeGiftQuota;
    private Integer matchBuyQuota;
    private Integer matchGiftQuota;
    private Boolean isDel;
    private Integer isTry;
    private Integer canAccessData;
    private Integer isOutAdvisor;
    private Timestamp accessDataExpired;

    public Member() {
    }

    public Integer getId() {
        return id;
    }

    public Member setId(Integer id) {
        this.id = id;
        return this;
    }

    public String get_id() {
        return _id;
    }

    public Member set_id(String _id) {
        this._id = _id;
        return this;
    }

    public String getOpenid() {
        return openid;
    }

    public Member setOpenid(String openid) {
        this.openid = openid;
        return this;
    }

    public String getAppId() {
        return appId;
    }

    public Member setAppId(String appId) {
        this.appId = appId;
        return this;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Member setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public String getFlag() {
        return flag;
    }

    public Member setFlag(String flag) {
        this.flag = flag;
        return this;
    }

    public String getQrcodeUrl() {
        return qrcodeUrl;
    }

    public Member setQrcodeUrl(String qrcodeUrl) {
        this.qrcodeUrl = qrcodeUrl;
        return this;
    }

    public Integer getGrade() {
        return grade;
    }

    public Member setGrade(Integer grade) {
        this.grade = grade;
        return this;
    }

    public String getNickName() {
        return nickName;
    }

    public Member setNickName(String nickName) {
        this.nickName = nickName;
        return this;
    }

    public Character getIsAnswer() {
        return isAnswer;
    }

    public Member setIsAnswer(Character isAnswer) {
        this.isAnswer = isAnswer;
        return this;
    }

    public Character getIsCustomers() {
        return isCustomers;
    }

    public Member setIsCustomers(Character isCustomers) {
        this.isCustomers = isCustomers;
        return this;
    }

    public Character getIsEvaluation() {
        return isEvaluation;
    }

    public Member setIsEvaluation(Character isEvaluation) {
        this.isEvaluation = isEvaluation;
        return this;
    }

    public Character getIsInsider() {
        return isInsider;
    }

    public Member setIsInsider(Character isInsider) {
        this.isInsider = isInsider;
        return this;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public Member setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
        return this;
    }

    public Date getRegistrationTime() {
        return registrationTime;
    }

    public Member setRegistrationTime(Date registrationTime) {
        this.registrationTime = registrationTime;
        return this;
    }

    public Integer getWardrobeQuota() {
        return wardrobeQuota;
    }

    public Member setWardrobeQuota(Integer wardrobeQuota) {
        this.wardrobeQuota = wardrobeQuota;
        return this;
    }

    public Integer getWardrobeBuyQuota() {
        return wardrobeBuyQuota;
    }

    public Member setWardrobeBuyQuota(Integer wardrobeBuyQuota) {
        this.wardrobeBuyQuota = wardrobeBuyQuota;
        return this;
    }

    public Integer getWardrobeGiftQuota() {
        return wardrobeGiftQuota;
    }

    public Member setWardrobeGiftQuota(Integer wardrobeGiftQuota) {
        this.wardrobeGiftQuota = wardrobeGiftQuota;
        return this;
    }

    public Integer getMatchBuyQuota() {
        return matchBuyQuota;
    }

    public Member setMatchBuyQuota(Integer matchBuyQuota) {
        this.matchBuyQuota = matchBuyQuota;
        return this;
    }

    public Integer getMatchGiftQuota() {
        return matchGiftQuota;
    }

    public Member setMatchGiftQuota(Integer matchGiftQuota) {
        this.matchGiftQuota = matchGiftQuota;
        return this;
    }

    public Boolean getIsDel() {
        return isDel;
    }

    public Member setIsDel(Boolean isDel) {
        this.isDel = isDel;
        return this;
    }

    public Integer getIsTry() {
        return isTry;
    }

    public Member setIsTry(Integer isTry) {
        this.isTry = isTry;
        return this;
    }

    public Integer getCanAccessData() {
        return canAccessData;
    }

    public Member setCanAccessData(Integer canAccessData) {
        this.canAccessData = canAccessData;
        return this;
    }

    public Integer getIsOutAdvisor() {
        return isOutAdvisor;
    }

    public Member setIsOutAdvisor(Integer isOutAdvisor) {
        this.isOutAdvisor = isOutAdvisor;
        return this;
    }

    public Timestamp getAccessDataExpired() {
        return accessDataExpired;
    }

    public Member setAccessDataExpired(Timestamp accessDataExpired) {
        this.accessDataExpired = accessDataExpired;
        return this;
    }

    @Override
    public String toString() {
        return "Member{" +
                "id=" + id +
                ", _id='" + _id + '\'' +
                ", openid='" + openid + '\'' +
                ", appId='" + appId + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", flag='" + flag + '\'' +
                ", qrcodeUrl='" + qrcodeUrl + '\'' +
                ", grade=" + grade +
                ", nickName='" + nickName + '\'' +
                ", isAnswer=" + isAnswer +
                ", isCustomers=" + isCustomers +
                ", isEvaluation=" + isEvaluation +
                ", isInsider=" + isInsider +
                ", loginTime=" + loginTime +
                ", registrationTime=" + registrationTime +
                ", wardrobeQuota=" + wardrobeQuota +
                ", wardrobeBuyQuota=" + wardrobeBuyQuota +
                ", wardrobeGiftQuota=" + wardrobeGiftQuota +
                ", matchBuyQuota=" + matchBuyQuota +
                ", matchGiftQuota=" + matchGiftQuota +
                ", isDel=" + isDel +
                ", isTry=" + isTry +
                ", canAccessData=" + canAccessData +
                ", isOutAdvisor=" + isOutAdvisor +
                ", accessDataExpired=" + accessDataExpired +
                '}';
    }
}
