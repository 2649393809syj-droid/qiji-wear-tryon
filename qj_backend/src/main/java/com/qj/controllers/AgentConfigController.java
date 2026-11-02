package com.qj.controllers;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.db.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代理配置管理接口
 */
@RestController
@RequestMapping("/qj/admin/agentConfig")
public class AgentConfigController {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigController.class);
    
    private static final String TABLE_NAME = "qj_marketing_v2_agent_config";

    /**
     * 获取代理配置列表
     */
    @PostMapping("/list")
    public JSONObject listAgentConfig(@RequestBody String requestBody) {
        log.info("代理配置列表请求：{}", requestBody);
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String dlmc = reqJson.getStr("dlmc", "");
            int pageSize = reqJson.getInt("pageSize", 10);
            int offset = reqJson.getInt("offset", 0);

            Db db = DBConfig.getDb(null, "qj");
            
            // 构建WHERE条件
            StringBuilder whereClause = new StringBuilder("WHERE 1=1 ");
            List<Object> whereParams = new ArrayList<>();
            
            if (dlmc != null && !dlmc.trim().isEmpty()) {
                whereClause.append("AND dlmc LIKE ? ");
                whereParams.add("%" + dlmc.trim() + "%");
            }

            // 查询总数
            String countSql = "SELECT COUNT(*) as cnt FROM " + TABLE_NAME + " " + whereClause;
            List<Entity> countResult = db.query(countSql, whereParams.toArray());
            long total = countResult.isEmpty() ? 0 : countResult.get(0).getLong("cnt");

            // 查询数据
            String dataSql = "SELECT * FROM " + TABLE_NAME + " " + whereClause + " LIMIT ? OFFSET ?";
            List<Object> dataParams = new ArrayList<>(whereParams);
            dataParams.add(pageSize);
            dataParams.add(offset);
            
            List<Entity> dataResult = db.query(dataSql, dataParams.toArray());
            List<Map<String, Object>> list = dataResult.stream()
                    .map(entity -> (Map<String, Object>) new java.util.HashMap<String, Object>(entity))
                    .collect(Collectors.toList());

            result.set("errCode", 0);
            result.set("result", list);
            result.set("total", total);
            log.info("代理配置列表查询成功，总数：{}", total);
        } catch (Exception e) {
            log.error("代理配置列表查询失败", e);
            result.set("errCode", 1);
            result.set("errMsg", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 更新代理配置
     */
    @PostMapping("/update")
    public JSONObject updateAgentConfig(@RequestBody String requestBody) {
        log.info("代理配置更新请求：{}", requestBody);
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String dlmc = reqJson.getStr("dlmc");
            
            if (dlmc == null || dlmc.trim().isEmpty()) {
                result.set("errCode", 1);
                result.set("errMsg", "代理名称不能为空");
                return result;
            }

            Db db = DBConfig.getDb(null, "qj");
            
            // 构建更新语句
            Entity updateEntity = Entity.create(TABLE_NAME);
            
            // 设置可更新的字段
            if (reqJson.containsKey("jmfy")) {
                String jmfyStr = reqJson.getStr("jmfy");
                if (jmfyStr != null && !jmfyStr.trim().isEmpty()) {
                    updateEntity.set("jmfy", new BigDecimal(jmfyStr));
                }
            }
            
            if (reqJson.containsKey("xyfy")) {
                String xyfyStr = reqJson.getStr("xyfy");
                if (xyfyStr != null && !xyfyStr.trim().isEmpty()) {
                    updateEntity.set("xyfy", new BigDecimal(xyfyStr));
                }
            }
            
            if (reqJson.containsKey("zmgs")) {
                String zmgsStr = reqJson.getStr("zmgs");
                if (zmgsStr != null && !zmgsStr.trim().isEmpty()) {
                    updateEntity.set("zmgs", Integer.parseInt(zmgsStr));
                }
            }
            
            if (reqJson.containsKey("d21")) {
                String d21Str = reqJson.getStr("d21");
                if (d21Str != null && !d21Str.trim().isEmpty()) {
                    updateEntity.set("d21", d21Str);
                }
            }
            
            if (reqJson.containsKey("d22")) {
                String d22Str = reqJson.getStr("d22");
                if (d22Str != null && !d22Str.trim().isEmpty()) {
                    updateEntity.set("d22", Integer.parseInt(d22Str));
                }
            }
            
            // 执行更新
            Entity whereEntity = Entity.create(TABLE_NAME).set("dlmc", dlmc);
            int affected = db.update(updateEntity, whereEntity);
            
            if (affected > 0) {
                result.set("errCode", 0);
                result.set("msg", "更新成功");
                log.info("代理配置更新成功，影响行数：{}", affected);
            } else {
                result.set("errCode", 1);
                result.set("errMsg", "未找到对应的代理配置");
            }
        } catch (Exception e) {
            log.error("代理配置更新失败", e);
            result.set("errCode", 1);
            result.set("errMsg", "更新失败：" + e.getMessage());
        }
        return result;
    }
}
