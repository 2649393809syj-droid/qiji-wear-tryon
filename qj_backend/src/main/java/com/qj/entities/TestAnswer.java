package com.qj.entities;

/**
 * 问卷答案实体类
 * 对应qj_answers表
 * 
 * @author system
 */
public class TestAnswer {
    private Integer id;
    private String openid;
    private String answers;
    private String avatarUrl;
    private String nickName;

    public TestAnswer() {
    }

    public Integer getId() {
        return id;
    }

    public TestAnswer setId(Integer id) {
        this.id = id;
        return this;
    }

    public String getOpenid() {
        return openid;
    }

    public TestAnswer setOpenid(String openid) {
        this.openid = openid;
        return this;
    }

    public String getAnswers() {
        return answers;
    }

    public TestAnswer setAnswers(String answers) {
        this.answers = answers;
        return this;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public TestAnswer setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public String getNickName() {
        return nickName;
    }

    public TestAnswer setNickName(String nickName) {
        this.nickName = nickName;
        return this;
    }

    @Override
    public String toString() {
        return "TestAnswer{" +
                "id=" + id +
                ", openid='" + openid + '\'' +
                ", answers='" + answers + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", nickName='" + nickName + '\'' +
                '}';
    }
}
