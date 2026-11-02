package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.FunctionCodes;
import com.qj.entities.AgentResponse;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 管理端代理审批推广关系变动日志服务
 */
@Service
public class AdminPromotionLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminPromotionLogService.class);

    /**
     * 获取代理审批推广关系变动日志列表
     * 
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @param applicantOpenid 申请人openid（可选筛选条件）
     * @param applyRole 申请角色（可选筛选条件）
     * @return 日志列表响应
     */
    public AgentResponse getPromotionLogs(Integer pageNum, Integer pageSize, String applicantOpenid, Integer applyRole) {
        try {
            log.info("获取代理审批推广关系变动日志列表：pageNum={}, pageSize={}, applicantOpenid={}, applyRole={}", 
                    pageNum, pageSize, applicantOpenid, applyRole);

            // 1. 参数验证和默认值设置
            if (pageNum == null || pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1) {
                pageSize = 10;
            }
            if (pageSize > 100) {
                pageSize = 100; // 限制最大页面大小
            }

            // 2. 计算偏移量
            int offset = (pageNum - 1) * pageSize;

            // 3. 构建查询参数
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();

            // 基础分页参数 - 注意参数顺序要与SQL中的?顺序一致
            fieldKeys.add("pageSize");  // 对应SQL中的第一个? (LIMIT ?)
            fieldKeys.add("offset");    // 对应SQL中的第二个? (OFFSET ?)

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("pageSize", pageSize);
            param.set("offset", offset);

            // 4. 执行查询
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4212_ADMIN_GET_PROMOTION_LOGS,
                    "F_303_4212_ADMIN_GET_PROMOTION_LOGS",
                    "qj_marketing_v2_agent_promotion_logs",
                    param,
                    new HashSet<>());

            log.info("查询代理审批推广关系变动日志结果：{}", result.toString());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("result");

                // 5. 处理查询结果，格式化数据
                if (logs != null) {
                    for (Map<String, Object> log : logs) {
                        // 格式化推广关系变动信息
                        Object promotionChanges = log.get("promotion_relation_changes");
                        if (promotionChanges != null && StrUtil.isNotBlank(promotionChanges.toString())) {
                            try {
                                // 尝试解析JSON格式的推广关系变动信息
                                JSONObject changesJson = new JSONObject(promotionChanges.toString());
                                log.put("promotion_relation_changes_parsed", changesJson);
                            } catch (Exception e) {
                                // 如果解析失败，保持原始字符串
                                log.put("promotion_relation_changes_parsed", promotionChanges.toString());
                            }
                        }

                        // 格式化角色名称
                        formatRoleName(log, "apply_role", "apply_role_name");
                        formatRoleName(log, "old_role", "old_role_name");
                    }
                }

                // 6. 构建响应数据
                JSONObject responseData = new JSONObject();
                responseData.set("logs", logs);
                responseData.set("pageNum", pageNum);
                responseData.set("pageSize", pageSize);
                responseData.set("total", logs != null ? logs.size() : 0);

                return AgentResponse.success("获取日志列表成功", responseData);
            } else {
                log.error("查询代理审批推广关系变动日志失败：{}", result.getStr("errMsg"));
                return AgentResponse.fail("查询日志列表失败：" + result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("获取代理审批推广关系变动日志列表异常", e);
            return AgentResponse.fail("获取日志列表异常：" + e.getMessage());
        }
    }

    /**
     * 格式化角色名称
     */
    private void formatRoleName(Map<String, Object> log, String roleField, String roleNameField) {
        Object role = log.get(roleField);
        if (role != null) {
            String roleName = getRoleNameByRole(Integer.valueOf(role.toString()));
            log.put(roleNameField, roleName);
        }
    }

    /**
     * 根据角色代码获取角色名称
     */
    private String getRoleNameByRole(Integer role) {
        if (role == null) {
            return "未知角色";
        }
        
        switch (role) {
            case 5:
                return "城市代理";
            case 6:
                return "平台代理";
            case 7:
                return "个人代理";
            case 0:
                return "普通用户";
            default:
                return "未知角色";
        }
    }
}
