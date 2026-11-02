package com.qj.services.common;

import cn.hutool.json.JSONObject;
import com.qj.consts.FunctionCodes;
import com.qj.entities.AgentResponse;
import com.qj.services.AgentService;
import com.qj.services.AdminAgentService;
import com.qj.services.AdminPromotionLogService;
import com.qj.services.ShopCommodityService;
import com.qj.services.ShopService;
import com.qj.services.TestReportService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Set;
import com.qj.consts.AgentStatusCodes;

@Component
public class FuncDispatch implements ApplicationContextAware {

    public static ApplicationContext applicationContext;
    private static final Logger log = LoggerFactory.getLogger(FuncDispatch.class);

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     *
     * @param funcCode
     * @param param
     *                 对于基本crudservice不能完整处理的情况兼容
     *                 由ServiceDispatch 初次分派，本服务再次接管
     *                 对应action为customize
     * @return
     */
    public static JSONObject dispatch(int funcCode, String funcName, String moduleName,
            JSONObject param, Set<String> setExcludes) {
        switch (funcCode) {
            // 代理相关功能
            case FunctionCodes.F_301_4011_AGENT_GET_USER_IDENTITY:
                return handleGetUserIdentity(param);

            case FunctionCodes.F_301_4012_AGENT_APPLY_SUCCESS:
                return handleAgentApplySuccess(param);

            case FunctionCodes.F_301_4013_AGENT_GET_STATISTICS:
                return handleGetAgentStatistics(param);

            case FunctionCodes.F_301_4014_AGENT_GET_PROMOTION_INFO:
                return handleGetPromotionInfo(param);

            // 校验用户是否有免费形象测试资格
            case FunctionCodes.F_301_4015_CHECK_TEST_QUALIFICATION:
                return handleCheckTestQualification(param);

            // 管理端代理审核功能
            case FunctionCodes.F_301_4021_ADMIN_GET_AGENT_LIST:
                return handleAdminGetAgentList(param);

            case FunctionCodes.F_301_4022_ADMIN_APPROVE_AGENT:
                return handleAdminApproveAgent(param);

            case FunctionCodes.F_301_4023_ADMIN_REJECT_AGENT:
                return handleAdminRejectAgent(param);

            case FunctionCodes.F_301_4028_ADMIN_ADD_AGENT:
                return handleAdminAddAgent(param);

            case FunctionCodes.F_303_4212_ADMIN_GET_PROMOTION_LOGS:
                return handleAdminGetPromotionLogs(param);

            // 管理端暂停代理
            case FunctionCodes.F_301_4006_AGENT_UPDATE_STATUS:
                return handleAdminPauseAgent(param);

            // 个人形象特质测试报告相关功能
            case FunctionCodes.F_401_5001_REPORT_GET_CLIENT_REPORT:
                return handleGetClientReport();

            case FunctionCodes.F_401_5002_REPORT_GET_PRICE:
                return handleGetReportPrice();

            case FunctionCodes.F_401_5003_REPORT_UPDATE_BUY_STATUS:
                return handleUpdateBuyStatus(param);

            case FunctionCodes.F_401_5006_REPORT_AFTER_PAID:
                return handleAfterPaidReport(param);

            case FunctionCodes.F_401_5007_REPORT_CHECK_AND_UPDATE_QUALIFICATION:
                return handleCheckAndUpdateQualification(param);

            case FunctionCodes.F_401_5008_REPORT_GET_USER_INFO:
                return handleGetUserReportInfo(param);

            // 代理配置相关功能
            case FunctionCodes.F_301_4060_AGENT_CONFIG_LIST:
                return handleAgentConfigList(param);
        }

        return new JSONObject().set("errCode", 404).set("errMsg", "功能号未定义或未处理");
    }

    /**
     * 获取AgentService实例
     */
    private static AgentService getAgentService() {
        return applicationContext.getBean(AgentService.class);
    }

    /**
     * 获取AdminAgentService实例
     */
    private static AdminAgentService getAdminAgentService() {
        return applicationContext.getBean(AdminAgentService.class);
    }

    /**
     * 获取TestReportService实例
     */
    private static TestReportService getTestReportService() {
        return applicationContext.getBean(TestReportService.class);
    }

    /**
     * 处理获取用户身份
     */
    private static JSONObject handleGetUserIdentity(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            AgentResponse response = getAgentService().getUserIdentity(openid);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "获取用户身份失败: " + e.getMessage());
        }
    }

    /**
     * 处理代理申请成功
     */
    private static JSONObject handleAgentApplySuccess(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            Integer agentRole = param.getInt("agentRole");
            String tradeNo = param.getStr("tradeNo");
            String city = param.getStr("city");
            Integer by_type = param.getInt("by_type");
            if (by_type == null) {
                by_type = param.getInt("be_type");
            }

            // 修正错误的角色常量映射
            if (agentRole != null) {
                switch (agentRole) {
                    case 10: // 如果传入10，映射为个人代理
                        agentRole = AgentStatusCodes.ROLE_PERSONAL_AGENT; // 7
                        break;
                    case 11: // 如果传入11，映射为平台代理
                        agentRole = AgentStatusCodes.ROLE_PLATFORM_AGENT; // 6
                        break;
                    case 12: // 如果传入12，映射为城市代理
                        agentRole = AgentStatusCodes.ROLE_CITY_AGENT; // 5
                        log.info("修正角色常量：12 -> {} (城市代理)", agentRole);
                        break;
                    case AgentStatusCodes.ROLE_CITY_AGENT:
                    case AgentStatusCodes.ROLE_PLATFORM_AGENT:
                    case AgentStatusCodes.ROLE_PERSONAL_AGENT:
                        log.info("角色常量正确：{}", agentRole);
                        break;
                    default:
                        log.warn("未知的角色常量：{}，使用默认值", agentRole);
                        agentRole = AgentStatusCodes.ROLE_NORMAL_AGENT; // 默认为普通用户
                        break;
                }
            }

            // 获取支付价格（单位为分）
            Double currentPrice = param.getDouble("current_price"); // 申请时的价格
            Double renewPrice = param.getDouble("renewPrice"); // 续费时的价格

            int currentPriceFen = 0;
            if (currentPrice != null) {
                // 元转分，四舍五入
                currentPriceFen = (int) Math.round(currentPrice * 100);
            } else if (renewPrice != null) {
                // 如果没有current_price但有renewPrice，使用renewPrice（续费场景）
                currentPriceFen = (int) Math.round(renewPrice * 100);
            }

            log.info("支付成功处理: openid={}, agentRole={}, currentPrice(原始)={}, renewPrice(原始)={}, currentPriceFen(分)={}",
                    openid, agentRole, currentPrice, renewPrice, currentPriceFen);

            AgentResponse response = getAgentService().handleAgentApplySuccess(openid, agentRole, tradeNo,
                    by_type, city, currentPriceFen);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "处理代理申请失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取代理统计信息
     */
    private static JSONObject handleGetAgentStatistics(JSONObject param) {
        try {
            JSONObject statistics = new JSONObject();
            statistics.set("totalAgents", 0);
            statistics.set("pendingAgents", 0);
            statistics.set("validAgents", 0);
            statistics.set("rejectedAgents", 0);
            statistics.set("suspendedAgents", 0);
            statistics.set("renewalAgents", 0);

            return new JSONObject()
                    .set("errCode", 0)
                    .set("errMsg", "获取代理统计信息成功")
                    .set("result", statistics);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "获取代理统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取用户推广信息
     */
    private static JSONObject handleGetPromotionInfo(JSONObject param) {
        try {
            String userOpenid = param.getStr("user_openid");
            AgentResponse response = getAgentService().getUserPromotionInfo(userOpenid);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "获取用户推广信息失败: " + e.getMessage());
        }
    }

    /**
     * 处理校验用户是否有免费形象测试资格
     */
    private static JSONObject handleCheckTestQualification(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            String targetOpenid = param.getStr("targetOpenid");
            AgentResponse response;
            if (targetOpenid != null && !targetOpenid.isEmpty()) {
                response = getAgentService().checkTestQualification(openid, targetOpenid);
            } else {
                // 兼容老调用，targetOpenid为openid
                response = getAgentService().checkTestQualification(openid, openid);
            }
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "校验免费形象测试资格失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端获取代理列表
     */
    private static JSONObject handleAdminGetAgentList(JSONObject param) {
        try {
            Integer status = param.getInt("status");
            Integer role = param.getInt("role");
            Integer page = param.getInt("page", 1);
            Integer pageSize = param.getInt("pageSize", 10);

            AgentResponse response = getAdminAgentService().getAgentList(status, role, page, pageSize);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "获取代理列表失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端审核通过代理申请
     */
    private static JSONObject handleAdminApproveAgent(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            log.info("收到参数: {}", param.toString());
            Double currentPrice = param.getDouble("currentPrice");
            log.info("currentPrice (Double): {}", currentPrice);
            Double renewPrice = param.getDouble("renewPrice");
            int currentPriceFen = currentPrice == null ? 0 : currentPrice.intValue(); // 直接用分
            log.info("currentPriceFen (int): {}", currentPriceFen);
            AgentResponse response = getAdminAgentService().approveAgent(openid, currentPriceFen);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "审核通过代理申请失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端拒绝代理申请
     */
    private static JSONObject handleAdminRejectAgent(JSONObject param) {
        try {
            String openid = param.getStr("openid");

            AgentResponse response = getAdminAgentService().rejectAgent(openid);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "拒绝代理申请失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端添加代理
     */
    private static JSONObject handleAdminAddAgent(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            Integer currentRole = param.getInt("current_role");
            String city = param.getStr("city");

            log.info("收到添加代理参数: openid={}, current_role={}, city={}", openid, currentRole, city);

            AgentResponse response = getAdminAgentService().addAgent(openid, currentRole, city);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            log.error("添加代理失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "添加代理失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端获取代理审批推广关系变动日志
     */
    private static JSONObject handleAdminGetPromotionLogs(JSONObject param) {
        try {
            Integer pageNum = param.getInt("pageNum");
            Integer pageSize = param.getInt("pageSize");
            String applicantOpenid = param.getStr("applicantOpenid");
            Integer applyRole = param.getInt("applyRole");

            log.info("收到获取推广关系变动日志参数: pageNum={}, pageSize={}, applicantOpenid={}, applyRole={}",
                    pageNum, pageSize, applicantOpenid, applyRole);

            AgentResponse response = getAdminPromotionLogService().getPromotionLogs(pageNum, pageSize, applicantOpenid, applyRole);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            log.error("获取推广关系变动日志失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "获取推广关系变动日志失败: " + e.getMessage());
        }
    }

    /**
     * 处理管理端暂停代理
     */
    private static JSONObject handleAdminPauseAgent(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            AgentResponse response = getAdminAgentService().pauseAgent(openid);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "暂停代理失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取客户报告详情
     */
    private static JSONObject handleGetClientReport() {
        try {
            JSONObject result = getTestReportService().getClientReport();
            if (result.getBool("success")) {
                return new JSONObject()
                        .set("errCode", 0)
                        .set("errMsg", "获取客户报告详情成功")
                        .set("result", result.get("data"));
            } else {
                return new JSONObject()
                        .set("errCode", 500)
                        .set("errMsg", result.getStr("message"));
            }
        } catch (SQLException e) {
            log.error("获取客户报告详情失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "获取客户报告详情失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取报告价格
     */
    private static JSONObject handleGetReportPrice() {
        try {
            JSONObject result = getTestReportService().getReportPrice();
            if (result.getBool("success")) {
                return new JSONObject()
                        .set("errCode", 0)
                        .set("errMsg", "获取报告价格成功")
                        .set("result", result.get("data"));
            } else {
                return new JSONObject()
                        .set("errCode", 500)
                        .set("errMsg", result.getStr("message"));
            }
        } catch (SQLException e) {
            log.error("获取报告价格失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "获取报告价格失败: " + e.getMessage());
        }
    }

    /**
     * 处理更新购买状态
     */
    private static JSONObject handleUpdateBuyStatus(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            if (openid == null || openid.isEmpty()) {
                return new JSONObject().set("errCode", 400).set("errMsg", "openid无效");
            }

            JSONObject result = getTestReportService().updateBuy(openid);
            if (result.getBool("success")) {
                return new JSONObject()
                        .set("errCode", 0)
                        .set("errMsg", "更新购买状态成功")
                        .set("result", result.get("data"));
            } else {
                return new JSONObject()
                        .set("errCode", 500)
                        .set("errMsg", result.getStr("message"));
            }
        } catch (SQLException e) {
            log.error("更新购买状态失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "更新购买状态失败: " + e.getMessage());
        }
    }

    /**
     * 处理订单支付成功后的报告
     */
    private static JSONObject handleAfterPaidReport(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            String tradeNo = param.getStr("tradeNo");
            String transactionId = param.getStr("transactionId");
            return getTestReportService().afterPaidReport(openid, tradeNo, transactionId);
        } catch (Exception e) {
            return new JSONObject().set("code", 1).set("message", "订单处理异常");
        }
    }

    /**
     * 处理检查并更新资格
     */
    private static JSONObject handleCheckAndUpdateQualification(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            AgentResponse response = getAgentService().checkQualificationAndUpdateBuyReport(openid);
            return convertAgentResponseToJSON(response);
        } catch (Exception e) {
            return new JSONObject().set("errCode", 500).set("errMsg", "检查并更新资格失败: " + e.getMessage());
        }
    }

    /**
     * 处理获取用户报告渲染数据
     */
    private static JSONObject handleGetUserReportInfo(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            if (openid == null || openid.isEmpty()) {
                return new JSONObject().set("errCode", 400).set("errMsg", "openid参数不能为空");
            }

            JSONObject result = getTestReportService().getUserReportInfo(openid);
            if (result.getBool("success")) {
                return new JSONObject()
                        .set("errCode", 0)
                        .set("errMsg", "获取用户报告数据成功")
                        .set("result", result.get("data"));
            } else {
                return new JSONObject()
                        .set("errCode", 500)
                        .set("errMsg", result.getStr("message"));
            }
        } catch (Exception e) {
            log.error("获取用户报告数据失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "获取用户报告数据失败: " + e.getMessage());
        }
    }

    /**
     * 将AgentResponse转换为JSONObject
     */
    private static JSONObject convertAgentResponseToJSON(AgentResponse response) {
        JSONObject result = new JSONObject();
        result.set("errCode", response.isSuccess() ? 0 : 1);
        result.set("errMsg", response.getMessage());
        if (response.getData() != null) {
            result.set("result", response.getData());
        }
        return result;
    }

    // ===== 商城商品相关处理方法 =====

    /**
     * 处理商品分页列表
     */
    private static JSONObject handleCommodityList(JSONObject param) {
        try {
            Integer pageNumber = param.getInt("pageNumber");
            Integer pageSize = param.getInt("pageSize");
            String type = param.getStr("type");
            String brand = param.getStr("brand");
            String keywords = param.getStr("keywords");
            Integer shopId = param.getInt("shopId");

            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityPage(pageNumber, pageSize, type, brand, keywords, shopId);
        } catch (Exception e) {
            log.error("处理商品列表异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理商品列表异常：" + e.getMessage());
        }
    }

    /**
     * 处理推荐商品列表
     */
    private static JSONObject handleCommodityStarList(JSONObject param) {
        try {
            Integer pageNumber = param.getInt("pageNumber");
            Integer pageSize = param.getInt("pageSize");
            Integer typeByTab = param.getInt("typeByTab");
            String keywords = param.getStr("keywords");

            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityStarPage(pageNumber, pageSize, typeByTab, keywords);
        } catch (Exception e) {
            log.error("处理推荐商品列表异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理推荐商品列表异常：" + e.getMessage());
        }
    }

    /**
     * 处理根据ID获取商品详情
     */
    private static JSONObject handleCommodityGetById(JSONObject param) {
        try {
            Integer commodityId = param.getInt("commodityId");
            if (commodityId == null) {
                return new JSONObject().set("errCode", 1).set("errMsg", "商品ID不能为空");
            }

            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityById(commodityId);
        } catch (Exception e) {
            log.error("处理获取商品详情异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理获取商品详情异常：" + e.getMessage());
        }
    }

    /**
     * 处理获取商品分类列表
     */
    private static JSONObject handleCommodityTypesList(JSONObject param) {
        try {
            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityTypes();
        } catch (Exception e) {
            log.error("处理获取商品分类列表异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理获取商品分类列表异常：" + e.getMessage());
        }
    }

    /**
     * 处理根据商铺获取商品分类
     */
    private static JSONObject handleCommodityTypesByShop(JSONObject param) {
        try {
            Integer shopId = param.getInt("shopId");
            if (shopId == null) {
                return new JSONObject().set("errCode", 1).set("errMsg", "商铺ID不能为空");
            }

            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityTypesByShopId(shopId);
        } catch (Exception e) {
            log.error("处理根据商铺获取商品分类异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理根据商铺获取商品分类异常：" + e.getMessage());
        }
    }

    /**
     * 处理商品筛选列表
     */
    private static JSONObject handleCommodityScreenList(JSONObject param) {
        try {
            Integer pageNumber = param.getInt("pageNumber");
            Integer pageSize = param.getInt("pageSize");
            String type = param.getStr("type");
            String brand = param.getStr("brand");
            String keywords = param.getStr("keywords");
            Integer shopId = param.getInt("shopId");

            ShopCommodityService service = getShopCommodityService();
            return service.getCommodityPage(pageNumber, pageSize, type, brand, keywords, shopId);
        } catch (Exception e) {
            log.error("处理商品筛选列表异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理商品筛选列表异常：" + e.getMessage());
        }
    }

    // ===== 商铺相关处理方法 =====

    /**
     * 处理商铺列表
     */
    private static JSONObject handleShopList(JSONObject param) {
        try {
            Integer pageNumber = param.getInt("pageNumber");
            Integer pageSize = param.getInt("pageSize");
            Integer isBrand = param.getInt("isBrand");
            String openid = param.getStr("openid");

            ShopService service = getShopService();
            return service.getShopList(pageNumber, pageSize, isBrand, openid);
        } catch (Exception e) {
            log.error("处理商铺列表异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理商铺列表异常：" + e.getMessage());
        }
    }

    /**
     * 处理根据ID获取商铺详情
     */
    private static JSONObject handleShopGetById(JSONObject param) {
        try {
            Integer shopId = param.getInt("shopId");
            if (shopId == null) {
                return new JSONObject().set("errCode", 1).set("errMsg", "商铺ID不能为空");
            }

            ShopService service = getShopService();
            return service.getShopById(shopId);
        } catch (Exception e) {
            log.error("处理获取商铺详情异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理获取商铺详情异常：" + e.getMessage());
        }
    }

    /**
     * 处理根据openid获取商铺信息
     */
    private static JSONObject handleShopGetByOpenid(JSONObject param) {
        try {
            String openid = param.getStr("openid");
            if (openid == null || openid.trim().isEmpty()) {
                return new JSONObject().set("errCode", 1).set("errMsg", "openid不能为空");
            }

            ShopService service = getShopService();
            return service.getShopIdByOpenId(openid);
        } catch (Exception e) {
            log.error("处理根据openid获取商铺信息异常", e);
            return new JSONObject().set("errCode", 1).set("errMsg", "处理根据openid获取商铺信息异常：" + e.getMessage());
        }
    }

    // ===== 服务获取方法 =====

    /**
     * 获取商品服务实例
     */
    private static ShopCommodityService getShopCommodityService() {
        return applicationContext.getBean(ShopCommodityService.class);
    }

    /**
     * 获取商铺服务实例
     */
    private static ShopService getShopService() {
        return applicationContext.getBean(ShopService.class);
    }

    /**
     * 获取管理端推广关系变动日志服务实例
     */
    private static AdminPromotionLogService getAdminPromotionLogService() {
        return applicationContext.getBean(AdminPromotionLogService.class);
    }

    /**
     * 处理获取代理配置列表（小程序端使用）
     */
    private static JSONObject handleAgentConfigList(JSONObject param) {
        try {
            cn.hutool.db.Db db = com.qj.db.DBConfig.getDb(null, "qj");
            String sql = "SELECT * FROM qj_marketing_v2_agent_config";
            java.util.List<cn.hutool.db.Entity> dataResult = db.query(sql);
            java.util.List<java.util.Map<String, Object>> list = dataResult.stream()
                    .map(entity -> (java.util.Map<String, Object>) new java.util.HashMap<String, Object>(entity))
                    .collect(java.util.stream.Collectors.toList());

            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("result", list);
            result.set("total", list.size());
            log.info("代理配置列表查询成功，总数：{}", list.size());
            return result;
        } catch (Exception e) {
            log.error("获取代理配置列表失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "获取代理配置列表失败: " + e.getMessage());
        }
    }
}
