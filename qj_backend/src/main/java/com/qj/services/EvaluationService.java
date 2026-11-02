package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import com.qj.entities.AgentResponse;
import com.qj.entities.Evaluation;
import com.qj.entities.Member;
import com.qj.entities.TestAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 * 评测服务类
 * 提供用户评测相关的业务逻辑
 * 
 * @author system
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    /**
     * 根据openid获取用户信息
     * 
     * @param openid 用户openid
     * @return 用户信息
     */
    public AgentResponse getMemberInfoByOpenid(String openid) {
        log.info("获取用户信息：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            Db db = DBConfig.getDb("mysql", "qj");
            if (db == null) {
                return AgentResponse.fail("数据库连接失败");
            }

            // 查询用户信息
            Entity memberEntity = db.get(Entity.create("qj_member")
                    .set("_openid", openid)
                    .set("is_del", false));

            if (memberEntity == null) {
                return AgentResponse.fail("该用户不存在");
            }

            // 如果用户没有推广人信息，则进行添加
            Entity spreader = db.get(Entity.create("qj_spreader").set("openid", openid));
            if (spreader == null) {
                Entity spreaderEntity = Entity.create("qj_spreader");
                spreaderEntity.set("openid", openid);
                spreaderEntity.set("nickname", memberEntity.getStr("nick_name"));
                spreaderEntity.set("from", "ocDgH0bxhV98Pyvw1Mz0HOwBWIV8");
                spreaderEntity.set("from_nickname", "奇纪形象设计张寰");
                spreaderEntity.set("grade", 0);
                spreaderEntity.set("promotion_status", 1);
                db.insertForGeneratedKey(spreaderEntity);
                log.info("为用户创建推广人信息：openid={}", openid);
            }

            // 转换为Member对象
            Member member = entityToMember(memberEntity);
            
            return AgentResponse.success(member);

        } catch (Exception e) {
            log.error("获取用户信息异常：openid={}", openid, e);
            return AgentResponse.fail("获取用户信息异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取用户问卷答案
     * 
     * @param openid 用户openid
     * @return 问卷答案
     */
    public AgentResponse getAnswer(String openid) {
        log.info("获取用户问卷答案：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            Db db = DBConfig.getDb("mysql", "qj");
            if (db == null) {
                return AgentResponse.fail("数据库连接失败");
            }

            // 查询问卷答案
            Entity answerEntity = db.get(Entity.create("qj_answers").set("openid", openid));

            if (answerEntity == null) {
                return AgentResponse.fail("此用户无问卷答案");
            }

            // 转换为驼峰命名的JSON对象
            JSONObject result = new JSONObject();
            Set<Map.Entry<String, Object>> entries = answerEntity.entrySet();
            for (Map.Entry<String, Object> entry : entries) {
                result.set(StrUtil.toCamelCase(entry.getKey()), entry.getValue());
            }

            return AgentResponse.success(result);

        } catch (Exception e) {
            log.error("获取用户问卷答案异常：openid={}", openid, e);
            return AgentResponse.fail("获取用户问卷答案异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取用户评测信息
     * 
     * @param openid 用户openid
     * @return 评测信息
     */
    public AgentResponse getEvaluationByOpenid(String openid) {
        log.info("获取用户评测信息：openid={}", openid);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            Db db = DBConfig.getDb("mysql", "qj");
            if (db == null) {
                return AgentResponse.fail("数据库连接失败");
            }

            Evaluation evaluation = null;
            
            // 判断是否为手机号（11位数字）
            if (openid.length() == 11 && openid.matches("\\d{11}")) {
                evaluation = getEvaluationByMobile(openid, db);
            } else {
                evaluation = getEvaluationByOpenid(openid, db);
            }

            if (evaluation == null) {
                return AgentResponse.fail("评测信息不存在");
            }

            return AgentResponse.success(evaluation);

        } catch (Exception e) {
            log.error("获取用户评测信息异常：openid={}", openid, e);
            return AgentResponse.fail("获取用户评测信息异常：" + e.getMessage());
        }
    }

    /**
     * 根据openid获取评测信息
     */
    private Evaluation getEvaluationByOpenid(String openid, Db db) throws SQLException {
        Entity entity = db.get(Entity.create("qj_evaluation")
                .set("openid", openid)
                .set("is_del", false));
        
        if (entity == null) {
            return null;
        }

        return entityToEvaluation(entity);
    }

    /**
     * 根据手机号获取评测信息
     */
    private Evaluation getEvaluationByMobile(String mobile, Db db) throws SQLException {
        // 先通过手机号查找推广人信息
        Entity spreaderEntity = db.get(Entity.create("qj_spreader").set("mobile", mobile));
        if (spreaderEntity == null) {
            return null;
        }

        // 再通过openid查找评测信息
        String openid = spreaderEntity.getStr("openid");
        return getEvaluationByOpenid(openid, db);
    }

    /**
     * 将Entity转换为Member对象
     */
    private Member entityToMember(Entity entity) {
        Member member = new Member();
        member.setId(entity.getInt("id"));
        member.set_id(entity.getStr("_id"));
        member.setOpenid(entity.getStr("_openid"));
        member.setAppId(entity.getStr("app_id"));
        member.setAvatarUrl(entity.getStr("avatar_url"));
        member.setFlag(entity.getStr("flag"));
        member.setQrcodeUrl(entity.getStr("qrcode_url"));
        member.setGrade(entity.getInt("grade"));
        member.setNickName(entity.getStr("nick_name"));
        member.setIsAnswer(entity.getChar("is_answer"));
        member.setIsCustomers(entity.getChar("is_customers"));
        member.setIsEvaluation(entity.getChar("is_evaluation"));
        member.setIsInsider(entity.getChar("is_insider"));
        member.setLoginTime(entity.getDate("login_time"));
        member.setRegistrationTime(entity.getDate("registration_time"));
        member.setWardrobeQuota(entity.getInt("wardrobe_quota"));
        member.setWardrobeBuyQuota(entity.getInt("wardrobe_buy_quota"));
        member.setWardrobeGiftQuota(entity.getInt("wardrobe_gift_quota"));
        member.setMatchBuyQuota(entity.getInt("match_buy_quota"));
        member.setMatchGiftQuota(entity.getInt("match_gift_quota"));
        member.setIsDel(entity.getBool("is_del"));
        member.setIsTry(entity.getInt("is_try"));
        member.setCanAccessData(entity.getInt("can_access_data"));
        member.setIsOutAdvisor(entity.getInt("is_out_advisor"));
        member.setAccessDataExpired(entity.getTimestamp("access_data_expired"));
        return member;
    }

    /**
     * 将Entity转换为Evaluation对象
     */
    private Evaluation entityToEvaluation(Entity entity) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(entity.getStr("id"));
        evaluation.setOpenid(entity.getStr("openid"));
        evaluation.setShape(entity.getStr("shape"));
        evaluation.setTypeBeauty(entity.getStr("type_beauty"));
        evaluation.setIsDel(entity.getBool("is_del"));

        // 解析skin_color_obj JSON字符串
        String skinColorObjStr = entity.getStr("skin_color_obj");
        if (StrUtil.isNotBlank(skinColorObjStr)) {
            try {
                JSONObject skinColorJson = JSONUtil.parseObj(skinColorObjStr);
                Evaluation.SkinColorObj skinColorObj = new Evaluation.SkinColorObj();
                skinColorObj.setSkinColor(skinColorJson.getStr("skinColor"));
                skinColorObj.setSkinColorProportion(skinColorJson.getStr("skinColorProportion"));
                evaluation.setSkinColorObj(skinColorObj);
            } catch (Exception e) {
                log.warn("解析skin_color_obj失败：{}", skinColorObjStr, e);
            }
        }

        // 解析types JSON字符串
        String typesStr = entity.getStr("types");
        if (StrUtil.isNotBlank(typesStr)) {
            try {
                Evaluation.Types[] types = JSONUtil.toBean(typesStr, Evaluation.Types[].class);
                evaluation.setTypes(types);
            } catch (Exception e) {
                log.warn("解析types失败：{}", typesStr, e);
            }
        }

        return evaluation;
    }
}
