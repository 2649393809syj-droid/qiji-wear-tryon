package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.config.MyJsonConfig;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.services.common.CRUDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商铺服务类
 */
@Service
public class ShopService {
    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    @Autowired
    private AgentService agentService;
    /**
     * 获取商铺列表
     *
     * @param pageNumber 页码
     * @param pageSize   每页大小
     * @param isBrand    是否为品牌（1-品牌，2-普通商家）
     * @param openid     用户openid，用于判断代理等级
     * @return 商铺列表
     */
    public JSONObject getShopList(Integer pageNumber, Integer pageSize, Integer isBrand, String openid) {
        try {
            // 首先查询用户代理等级
            Integer agentRole = getUserAgentRole(openid);

            // 根据代理等级决定商铺获取策略
            if (agentRole != null) {
                switch (agentRole) {
                    case 98: // 特殊城市代理，能看到所有品牌店铺
                        return getAllBrandShopsForSpecialCityAgent(pageNumber, pageSize, isBrand);
                    case AgentStatusCodes.ROLE_PERSONAL_AGENT: // 7 - 个人代理
                        return getPersonalAgentShopList(openid, isBrand);
                    case AgentStatusCodes.ROLE_PLATFORM_AGENT: // 6 - 平台代理
                        return getPlatformAgentShopList(openid, isBrand);
                    case AgentStatusCodes.ROLE_CITY_AGENT: // 5 - 城市代理
                        return getCityAgentShopList(openid, isBrand);
                    default:
                        // 其他代理等级，按普通用户处理
                        return getNormalUserShopList(pageNumber, pageSize, isBrand, openid);
                }
            } else {
                // 普通用户
                return getNormalUserShopList(pageNumber, pageSize, isBrand, openid);
            }

        } catch (Exception e) {
            log.error("获取商铺列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商铺列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取个人代理的商铺列表
     * 个人代理不能开设店铺，只显示平台代理和城市代理的商铺
     */
    private JSONObject getPersonalAgentShopList(String openid, Integer isBrand) {
        try {
            log.info("获取个人代理商铺列表：openid={}", openid);

            // 获取个人代理的推广关系链
            List<String> superiorAgentOpenids = getSuperiorAgentChain(openid);
            log.info("个人代理{}的上级代理链：{}", openid, superiorAgentOpenids);

            // 收集所有相关的商铺
            List<Map<String, Object>> allShops = new ArrayList<>();

            // 个人代理不能开设店铺，只添加上级代理的商铺（平台代理和城市代理）
            for (String superiorOpenid : superiorAgentOpenids) {
                Integer superiorRole = getUserAgentRole(superiorOpenid);
                if (superiorRole != null && (superiorRole == AgentStatusCodes.ROLE_PLATFORM_AGENT ||
                                           superiorRole == AgentStatusCodes.ROLE_CITY_AGENT)) {
                    List<Map<String, Object>> superiorShops = getShopsByAgentOpenid(superiorOpenid, isBrand);
                    if (superiorShops != null && !superiorShops.isEmpty()) {
                        allShops.addAll(superiorShops);
                        log.info("个人代理{}的上级代理{}（等级{}）的商铺数量：{}",
                                openid, superiorOpenid, superiorRole, superiorShops.size());
                    }
                }
            }

            // 去重（根据商铺ID去重）
            allShops = deduplicateShops(allShops);

            // 如果没有找到任何商铺，显示"自是汇品"
            if (allShops.isEmpty()) {
                log.info("个人代理{}没有找到任何上级代理商铺，显示'自是汇品'店铺", openid);
                return getDefaultShopResult();
            }

            // 构建返回结果
            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "success");
            result.set("result", allShops);
            result.set("total", allShops.size());

            log.info("个人代理{}商铺列表获取成功，总数：{}", openid, allShops.size());
            return result;

        } catch (Exception e) {
            log.error("获取个人代理商铺列表异常：openid={}", openid, e);
            return getDefaultShopResult();
        }
    }

    /**
     * 获取平台代理的商铺列表
     * 平台代理显示自己的商铺和上级城市代理的商铺
     */
    private JSONObject getPlatformAgentShopList(String openid, Integer isBrand) {
        try {
            log.info("获取平台代理商铺列表：openid={}", openid);

            List<Map<String, Object>> allShops = new ArrayList<>();

            // 1. 添加自己的商铺
            List<Map<String, Object>> ownShops = getShopsByAgentOpenid(openid, isBrand);
            if (ownShops != null && !ownShops.isEmpty()) {
                allShops.addAll(ownShops);
                log.info("平台代理{}自己的商铺数量：{}", openid, ownShops.size());
            }

            // 2. 获取上级城市代理的商铺
            String cityAgentOpenid = getCityAgentOpenid(openid);
            if (StrUtil.isNotBlank(cityAgentOpenid)) {
                List<Map<String, Object>> cityShops = getShopsByAgentOpenid(cityAgentOpenid, isBrand);
                if (cityShops != null && !cityShops.isEmpty()) {
                    allShops.addAll(cityShops);
                    log.info("平台代理{}的上级城市代理{}的商铺数量：{}", openid, cityAgentOpenid, cityShops.size());
                }
            } else {
                log.info("平台代理{}没有找到上级城市代理", openid);
            }

            // 3. 去重
            allShops = deduplicateShops(allShops);

            // 4. 如果没有找到任何商铺，显示"自是汇品"
            if (allShops.isEmpty()) {
                log.info("平台代理{}没有找到任何商铺，显示'自是汇品'店铺", openid);
                return getDefaultShopResult();
            }

            // 5. 构建返回结果
            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "success");
            result.set("result", allShops);
            result.set("total", allShops.size());

            log.info("平台代理{}商铺列表获取成功，总数：{}", openid, allShops.size());
            return result;

        } catch (Exception e) {
            log.error("获取平台代理商铺列表异常：openid={}", openid, e);
            return getDefaultShopResult();
        }
    }

    /**
     * 获取城市代理的商铺列表
     * 城市代理显示自己的商铺和所有下级的商铺
     */
    private JSONObject getCityAgentShopList(String openid, Integer isBrand) {
        try {
            log.info("获取城市代理商铺列表：openid={}", openid);

            List<Map<String, Object>> allShops = new ArrayList<>();

            // 1. 添加自己的商铺
            List<Map<String, Object>> ownShops = getShopsByAgentOpenid(openid, isBrand);
            if (ownShops != null && !ownShops.isEmpty()) {
                allShops.addAll(ownShops);
                log.info("城市代理{}自己的商铺数量：{}", openid, ownShops.size());
            }

            // 2. 获取所有下级代理的商铺
            List<String> subordinateOpenids = getSubordinateAgentOpenids(openid);
            log.info("城市代理{}的下级代理数量：{}", openid, subordinateOpenids.size());

            for (String subordinateOpenid : subordinateOpenids) {
                List<Map<String, Object>> subordinateShops = getShopsByAgentOpenid(subordinateOpenid, isBrand);
                if (subordinateShops != null && !subordinateShops.isEmpty()) {
                    allShops.addAll(subordinateShops);
                    log.info("城市代理{}的下级代理{}的商铺数量：{}", openid, subordinateOpenid, subordinateShops.size());
                }
            }

            // 3. 去重
            allShops = deduplicateShops(allShops);

            // 4. 如果没有找到任何商铺，显示"自是汇品"
            if (allShops.isEmpty()) {
                log.info("城市代理{}没有找到任何商铺，显示'自是汇品'店铺", openid);
                return getDefaultShopResult();
            }

            // 5. 构建返回结果
            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "success");
            result.set("result", allShops);
            result.set("total", allShops.size());

            log.info("城市代理{}商铺列表获取成功，总数：{}", openid, allShops.size());
            return result;

        } catch (Exception e) {
            log.error("获取城市代理商铺列表异常：openid={}", openid, e);
            return getDefaultShopResult();
        }
    }

    /**
     * 获取普通用户的商铺列表
     */
    private JSONObject getNormalUserShopList(Integer pageNumber, Integer pageSize, Integer isBrand, String openid) {
        // 普通用户 - 根据是否查询品牌商铺决定处理逻辑
        if (isBrand != null && isBrand == 1) {
            // 查询品牌商铺 - 检查推广关系
            JSONObject promoterInfo = agentService.getPromoterInfo(openid);
            if (promoterInfo != null) {
                // 有推广关系，显示推广关系链上的品牌商铺（类似个人代理逻辑）
                return getNormalUserWithPromotionShopList(openid, isBrand);
            }
        }

        // 查询普通商铺或没有推广关系的品牌商铺 - 显示所有商铺，不受推广关系限制
        JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray fieldKeys = new JSONArray();
        JSONArray values = new JSONArray();
        return getRegularShopList(pageNumber, pageSize, isBrand, param, fieldKeys, values);
    }

    /**
     * 获取有推广关系的普通用户的品牌商铺列表
     * 普通用户有推广关系时，显示推广关系链上平台代理和城市代理的商铺
     */
    private JSONObject getNormalUserWithPromotionShopList(String openid, Integer isBrand) {
        try {
            log.info("获取有推广关系的普通用户品牌商铺列表：openid={}", openid);

            // 获取普通用户的推广关系链
            List<String> superiorAgentOpenids = getSuperiorAgentChain(openid);
            log.info("普通用户{}的上级代理链：{}", openid, superiorAgentOpenids);

            // 收集所有相关的商铺
            List<Map<String, Object>> allShops = new ArrayList<>();

            // 添加上级代理的商铺（平台代理和城市代理）
            for (String superiorOpenid : superiorAgentOpenids) {
                Integer superiorRole = getUserAgentRole(superiorOpenid);
                if (superiorRole != null && (superiorRole == AgentStatusCodes.ROLE_PLATFORM_AGENT ||
                                           superiorRole == AgentStatusCodes.ROLE_CITY_AGENT)) {
                    List<Map<String, Object>> superiorShops = getShopsByAgentOpenid(superiorOpenid, isBrand);
                    if (superiorShops != null && !superiorShops.isEmpty()) {
                        allShops.addAll(superiorShops);
                        log.info("普通用户{}的上级代理{}（等级{}）的商铺数量：{}",
                                openid, superiorOpenid, superiorRole, superiorShops.size());
                    }
                }
            }

            // 去重（根据商铺ID去重）
            allShops = deduplicateShops(allShops);

            // 如果没有找到任何商铺，显示"自是汇品"
            if (allShops.isEmpty()) {
                log.info("普通用户{}没有找到任何上级代理商铺，显示'自是汇品'店铺", openid);
                return getDefaultShopResult();
            }

            // 构建返回结果
            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "success");
            result.set("result", allShops);
            result.set("total", allShops.size());

            log.info("普通用户{}品牌商铺列表获取成功，总数：{}", openid, allShops.size());
            return result;

        } catch (Exception e) {
            log.error("获取普通用户品牌商铺列表异常：openid={}", openid, e);
            return getDefaultShopResult();
        }
    }

    /**
     * 查询用户代理等级
     *
     * @param openid 用户openid
     * @return 代理等级（11-平台代理，12-城市代理，10-个人代理），null表示非代理用户
     */
    private Integer getUserAgentRole(String openid) {
        try {
            if (openid == null || openid.trim().isEmpty()) {
                return null;
            }

            // 特殊城市代理：ocDgH0bxhV98Pyvw1Mz0HOwBWIV8，能看到所有品牌店铺
            if ("ocDgH0bxhV98Pyvw1Mz0HOwBWIV8".equals(openid)) {
                log.info("特殊城市代理访问，openid: {}", openid);
                return 98; // 特殊城市代理角色码
            }

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("openid");
            values.add(openid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4002_AGENT_GET,
                    "F_301_4002_AGENT_GET",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agentList = (List<Map<String, Object>>) result.get("result");
                if (agentList != null && !agentList.isEmpty()) {
                    Map<String, Object> agent = agentList.get(0);
                    Integer status = (Integer) agent.get("status");
                    Integer currentRole = (Integer) agent.get("current_role");

                    // 只有状态为1（有效）的代理才返回角色
                    if (status != null && status == 1 && currentRole != null) {
                        log.debug("用户 {} 是代理，等级: {}", openid, currentRole);
                        return currentRole;
                    }
                }
            }

            log.debug("用户 {} 不是代理或代理状态无效", openid);
            return null;

        } catch (Exception e) {
            log.error("查询用户代理等级异常，openid: {}", openid, e);
            return null;
        }
    }

    /**
     * 获取普通用户商铺列表
     */
    private JSONObject getRegularShopList(Integer pageNumber, Integer pageSize, Integer isBrand,
                                        JSONObject param, JSONArray fieldKeys, JSONArray values) {
        try {
            fieldKeys.add("pageSize");
            fieldKeys.add("offset");
            values.add(pageSize != null ? pageSize : 10);
            values.add(pageNumber != null ? (pageNumber - 1) * (pageSize != null ? pageSize : 10) : 0);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            int functionCode;
            String functionName;

            if (isBrand != null && isBrand == 1) {
                // 获取品牌商铺列表
                functionCode = FunctionCodes.F_502_6107_SHOP_BRAND_LIST;
                functionName = "F_502_6107_SHOP_BRAND_LIST";
            } else {
                // 获取普通商铺列表
                functionCode = FunctionCodes.F_502_6101_SHOP_LIST;
                functionName = "F_502_6101_SHOP_LIST";
            }

            JSONObject result = CRUDService.exeRetrieve(
                    functionCode,
                    functionName,
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取商铺列表失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取总数
            int countFunctionCode = isBrand != null && isBrand == 1 ? FunctionCodes.F_502_6114_SHOP_BRAND_COUNT
                    : FunctionCodes.F_502_6113_SHOP_COUNT;
            String countFunctionName = isBrand != null && isBrand == 1 ? "F_502_6114_SHOP_BRAND_COUNT"
                    : "F_502_6113_SHOP_COUNT";

            JSONObject countResult = CRUDService.exeRetrieve(
                    countFunctionCode,
                    countFunctionName,
                    "qj_shop",
                    new JSONObject(MyJsonConfig.getJsonConfig()),
                    new HashSet<>());

            if (countResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> countList = (List<Map<String, Object>>) countResult.get("result");
                if (countList != null && !countList.isEmpty()) {
                    result.set("total", countList.get(0).get("total"));
                }
            }

            return result;

        } catch (Exception e) {
            log.error("获取普通用户商铺列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商铺列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据ID获取商铺详情
     * 
     * @param shopId 商铺ID
     * @return 商铺详情
     */
    public JSONObject getShopById(Integer shopId) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("id");
            values.add(shopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_502_6102_SHOP_GET_BY_ID,
                    "F_502_6102_SHOP_GET_BY_ID",
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取商铺详情失败：{}", result.getStr("errMsg"));
                return result;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shopList = (List<Map<String, Object>>) result.get("result");
            if (shopList == null || shopList.isEmpty()) {
                JSONObject notFoundResult = new JSONObject();
                notFoundResult.set("errCode", 1);
                notFoundResult.set("errMsg", "商铺不存在");
                return notFoundResult;
            }

            return result;

        } catch (Exception e) {
            log.error("获取商铺详情异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商铺详情异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据openid获取商铺ID
     * 
     * @param openid 用户openid
     * @return 商铺信息
     */
    public JSONObject getShopIdByOpenId(String openid) {
        try {
            if (StrUtil.isBlank(openid)) {
                JSONObject errorResult = new JSONObject();
                errorResult.set("errCode", 1);
                errorResult.set("errMsg", "参数错误：openid不能为空");
                return errorResult;
            }

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("admin_openid");
            values.add(openid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_502_6103_SHOP_GET_BY_OPENID,
                    "F_502_6103_SHOP_GET_BY_OPENID",
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("根据openid获取商铺信息失败：{}", result.getStr("errMsg"));
                return result;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shopList = (List<Map<String, Object>>) result.get("result");
            if (shopList == null || shopList.isEmpty()) {
                JSONObject notFoundResult = new JSONObject();
                notFoundResult.set("errCode", 1);
                notFoundResult.set("errMsg", "该用户不是商铺管理员");
                return notFoundResult;
            }

            return result;

        } catch (Exception e) {
            log.error("根据openid获取商铺信息异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "根据openid获取商铺信息异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取推广人品牌商铺列表
     *
     * @param pageNumber 页码
     * @param pageSize 每页大小
     * @param promoterInfo 推广人信息
     * @param userOpenid 用户openid
     * @return 品牌商铺列表
     */
    private JSONObject getPromoterBrandShopList(Integer pageNumber, Integer pageSize,
                                              JSONObject promoterInfo, String userOpenid) {
        try {
            log.info("普通用户有推广关系，检查推广人品牌商铺，用户openid: {}", userOpenid);

            // 获取推广人的商铺ID
            Object shopIdObj = promoterInfo.get("shop_id");
            if (shopIdObj == null) {
                log.info("推广人没有关联商铺，显示'自是汇品'店铺，用户openid: {}", userOpenid);
                return getDefaultShopResult();
            }

            Integer promoterShopId = Convert.toInt(shopIdObj);
            if (promoterShopId == null || promoterShopId <= 0) {
                log.info("推广人商铺ID无效，不显示任何品牌商铺，用户openid: {}，shop_id: {}", userOpenid, shopIdObj);
                JSONObject emptyResult = new JSONObject();
                emptyResult.set("errCode", 0);
                emptyResult.set("result", new ArrayList<>());
                emptyResult.set("total", 0);
                return emptyResult;
            }

            // 构建查询参数，只显示推广人的品牌商铺
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("shop_id");
            values.add(promoterShopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            // 使用根据商铺ID获取商铺详情的功能码
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_502_6102_SHOP_GET_BY_ID,
                    "F_502_6102_SHOP_GET_BY_ID",
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取推广人商铺失败，用户openid: {}，推广人shop_id: {}，错误: {}",
                         userOpenid, promoterShopId, result.getStr("errMsg"));
                // 如果获取推广人商铺失败，不显示任何品牌商铺
                JSONObject emptyResult = new JSONObject();
                emptyResult.set("errCode", 0);
                emptyResult.set("result", new ArrayList<>());
                emptyResult.set("total", 0);
                return emptyResult;
            }

            // 检查是否找到商铺
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shopList = (List<Map<String, Object>>) result.get("result");
            if (shopList == null || shopList.isEmpty()) {
                log.info("推广人商铺不存在或已删除，不显示任何品牌商铺，用户openid: {}，推广人shop_id: {}",
                        userOpenid, promoterShopId);
                JSONObject emptyResult = new JSONObject();
                emptyResult.set("errCode", 0);
                emptyResult.set("result", new ArrayList<>());
                emptyResult.set("total", 0);
                return emptyResult;
            }

            // 检查推广人的商铺是否是品牌商铺
            Map<String, Object> shop = shopList.get(0);
            Object isBrandObj = shop.get("is_brand");
            Integer isBrandValue = Convert.toInt(isBrandObj);

            if (isBrandValue == null || isBrandValue != 1) {
                log.info("推广人商铺不是品牌商铺，显示'自是汇品'店铺，用户openid: {}，推广人shop_id: {}，is_brand: {}",
                        userOpenid, promoterShopId, isBrandObj);
                return getDefaultShopResult();
            }

            log.info("成功获取推广人品牌商铺，用户openid: {}，推广人shop_id: {}，商铺名称: {}",
                    userOpenid, promoterShopId, shop.get("name"));

            return result;

        } catch (Exception e) {
            log.error("获取推广人品牌商铺列表异常，用户openid: {}", userOpenid, e);
            // 异常时不显示任何品牌商铺
            JSONObject emptyResult = new JSONObject();
            emptyResult.set("errCode", 0);
            emptyResult.set("result", new ArrayList<>());
            emptyResult.set("total", 0);
            return emptyResult;
        }
    }

    /**
     * 获取"自是汇品"店铺数据作为兜底
     */
    private JSONObject getDefaultShopResult() {
        try {
            log.info("获取'自是汇品'店铺作为兜底数据");

            // 获取"自是汇品"店铺ID
            Integer defaultShopId = getDefaultShopId();
            if (defaultShopId == null) {
                log.warn("未找到'自是汇品'店铺，返回空结果");
                JSONObject emptyResult = new JSONObject();
                emptyResult.set("errCode", 0);
                emptyResult.set("result", new ArrayList<>());
                emptyResult.set("total", 0);
                return emptyResult;
            }

            // 根据店铺ID获取店铺信息
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("id");
            values.add(defaultShopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_502_6102_SHOP_GET_BY_ID,
                    "F_502_6102_SHOP_GET_BY_ID",
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取'自是汇品'店铺信息失败：{}", result.getStr("errMsg"));
                JSONObject emptyResult = new JSONObject();
                emptyResult.set("errCode", 0);
                emptyResult.set("result", new ArrayList<>());
                emptyResult.set("total", 0);
                return emptyResult;
            }

            log.info("成功获取'自是汇品'店铺信息作为兜底数据");
            return result;

        } catch (Exception e) {
            log.error("获取'自是汇品'店铺异常", e);
            JSONObject emptyResult = new JSONObject();
            emptyResult.set("errCode", 0);
            emptyResult.set("result", new ArrayList<>());
            emptyResult.set("total", 0);
            return emptyResult;
        }
    }

    /**
     * 获取"自是汇品"店铺ID
     */
    private Integer getDefaultShopId() {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("name");
            values.add("自是汇品");

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6039_GET_SHOP_BY_NAME,
                    "F_501_6039_GET_SHOP_BY_NAME",
                    "qj_shop",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> shops = (List<Map<String, Object>>) result.get("result");
                if (shops != null && !shops.isEmpty()) {
                    Integer shopId = Convert.toInt(shops.get(0).get("id"));
                    log.info("找到'自是汇品'店铺，ID: {}", shopId);
                    return shopId;
                }
            }

            log.warn("未找到名为'自是汇品'的店铺，尝试使用ID=1");
            return 1; // 根据代码分析，"自是汇品"的ID通常是1

        } catch (Exception e) {
            log.error("查询'自是汇品'店铺异常", e);
            return 1; // 异常时也返回ID=1作为兜底
        }
    }

    /**
     * 获取上级代理链（从直接上级开始，向上追溯）
     */
    private List<String> getSuperiorAgentChain(String openid) {
        List<String> superiorChain = new ArrayList<>();
        try {
            String currentOpenid = openid;
            int maxDepth = 5; // 防止无限循环
            int depth = 0;

            while (depth < maxDepth) {
                String superiorOpenid = getCurrentAgentOpenid(currentOpenid);
                if (StrUtil.isBlank(superiorOpenid)) {
                    break;
                }
                superiorChain.add(superiorOpenid);
                currentOpenid = superiorOpenid;
                depth++;
            }

            log.info("获取上级代理链：openid={}, chain={}", openid, superiorChain);
            return superiorChain;

        } catch (Exception e) {
            log.error("获取上级代理链异常：openid={}", openid, e);
            return superiorChain;
        }
    }

    /**
     * 获取当前用户的直接上级代理openid
     */
    private String getCurrentAgentOpenid(String userOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("user_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("user_openid", userOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                    "F_303_4203_AGENT_USER_GET_BY_USER",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                if (relations != null && !relations.isEmpty()) {
                    String agentOpenid = (String) relations.get(0).get("agent_openid");
                    log.debug("获取到用户{}的上级代理：{}", userOpenid, agentOpenid);
                    return agentOpenid;
                }
            }

            log.debug("用户{}没有上级代理", userOpenid);
            return null;

        } catch (Exception e) {
            log.error("获取上级代理异常：userOpenid={}", userOpenid, e);
            return null;
        }
    }

    /**
     * 获取城市代理openid（平台代理的直接上级）
     */
    private String getCityAgentOpenid(String platformAgentOpenid) {
        try {
            String superiorOpenid = getCurrentAgentOpenid(platformAgentOpenid);
            if (StrUtil.isNotBlank(superiorOpenid)) {
                Integer superiorRole = getUserAgentRole(superiorOpenid);
                if (superiorRole != null && superiorRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    log.info("平台代理{}的上级城市代理：{}", platformAgentOpenid, superiorOpenid);
                    return superiorOpenid;
                }
            }
            log.info("平台代理{}没有找到上级城市代理", platformAgentOpenid);
            return null;

        } catch (Exception e) {
            log.error("获取城市代理异常：platformAgentOpenid={}", platformAgentOpenid, e);
            return null;
        }
    }

    /**
     * 获取下级代理openid列表
     */
    private List<String> getSubordinateAgentOpenids(String agentOpenid) {
        List<String> subordinateOpenids = new ArrayList<>();
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("agent_openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("agent_openid", agentOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT,
                    "F_303_4204_AGENT_USER_GET_BY_AGENT",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                if (relations != null) {
                    for (Map<String, Object> relation : relations) {
                        String userOpenid = (String) relation.get("user_openid");
                        if (StrUtil.isNotBlank(userOpenid)) {
                            // 检查这个用户是否是代理
                            Integer userRole = getUserAgentRole(userOpenid);
                            if (userRole != null) {
                                subordinateOpenids.add(userOpenid);
                            }
                        }
                    }
                }
            }

            log.info("获取下级代理列表：agentOpenid={}, subordinates={}", agentOpenid, subordinateOpenids);
            return subordinateOpenids;

        } catch (Exception e) {
            log.error("获取下级代理列表异常：agentOpenid={}", agentOpenid, e);
            return subordinateOpenids;
        }
    }

    /**
     * 根据代理openid获取其商铺列表
     */
    private List<Map<String, Object>> getShopsByAgentOpenid(String agentOpenid, Integer isBrand) {
        try {
            // 首先查询代理关联的商铺ID
            JSONObject agentParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray agentFieldKeys = new JSONArray();
            agentFieldKeys.add("openid");
            agentParam.set("__mysql_fieldkeys__", agentFieldKeys);
            agentParam.set("openid", agentOpenid);

            JSONObject agentResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    agentParam,
                    new HashSet<>());

            if (agentResult.getInt("errCode") != 0) {
                log.debug("代理{}没有关联商铺信息", agentOpenid);
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agents = (List<Map<String, Object>>) agentResult.get("result");
            if (agents == null || agents.isEmpty()) {
                log.debug("代理{}没有关联商铺信息", agentOpenid);
                return new ArrayList<>();
            }

            Object shopIdObj = agents.get(0).get("shop_id");
            if (shopIdObj == null) {
                log.debug("代理{}没有关联商铺", agentOpenid);
                return new ArrayList<>();
            }

            Integer shopId = Convert.toInt(shopIdObj);
            if (shopId == null || shopId <= 0) {
                log.debug("代理{}的商铺ID无效：{}", agentOpenid, shopIdObj);
                return new ArrayList<>();
            }

            // 根据商铺ID查询商铺详情
            JSONObject shopParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray shopFieldKeys = new JSONArray();
            shopFieldKeys.add("id");
            shopParam.set("__mysql_fieldkeys__", shopFieldKeys);
            shopParam.set("id", shopId);

            JSONObject shopResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_502_6102_SHOP_GET_BY_ID,
                    "F_502_6102_SHOP_GET_BY_ID",
                    "qj_shop",
                    shopParam,
                    new HashSet<>());

            if (shopResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> shops = (List<Map<String, Object>>) shopResult.get("result");
                if (shops != null && !shops.isEmpty()) {
                    Map<String, Object> shop = shops.get(0);

                    // 检查商铺状态和删除标记
                    Integer status = Convert.toInt(shop.get("status"));
                    Integer isDel = Convert.toInt(shop.get("is_del"));
                    if (status == null || status != 1 || isDel == null || isDel != 2) {
                        log.debug("代理{}的商铺{}状态无效：status={}, is_del={}", agentOpenid, shopId, status, isDel);
                        return new ArrayList<>();
                    }

                    // 检查是否为品牌商铺
                    if (isBrand != null && isBrand == 1) {
                        Integer shopIsBrand = Convert.toInt(shop.get("is_brand"));
                        if (shopIsBrand == null || shopIsBrand != 1) {
                            log.debug("代理{}的商铺{}不是品牌商铺：is_brand={}", agentOpenid, shopId, shopIsBrand);
                            return new ArrayList<>();
                        }
                    }

                    List<Map<String, Object>> result = new ArrayList<>();
                    result.add(shop);
                    log.debug("代理{}的商铺{}符合条件", agentOpenid, shopId);
                    return result;
                }
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("根据代理openid获取商铺异常：agentOpenid={}", agentOpenid, e);
            return new ArrayList<>();
        }
    }

    /**
     * 特殊城市代理显示所有品牌店铺
     * 该特殊城市代理不受推广关系限制，能看到系统中所有品牌店铺
     */
    private JSONObject getAllBrandShopsForSpecialCityAgent(Integer pageNumber, Integer pageSize, Integer isBrand) {
        try {
            log.info("特殊城市代理请求所有品牌店铺列表");
            
            // 直接调用普通商铺列表获取方法，不受任何限制
            // 这样就能获取到系统中所有的品牌店铺
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();
            
            return getRegularShopList(pageNumber, pageSize, isBrand, param, fieldKeys, values);
            
        } catch (Exception e) {
            log.error("特殊城市代理获取所有品牌店铺异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取品牌店铺列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 商铺去重（根据商铺ID去重）
     */
    private List<Map<String, Object>> deduplicateShops(List<Map<String, Object>> shops) {
        if (shops == null || shops.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, Map<String, Object>> shopMap = new LinkedHashMap<>();
        for (Map<String, Object> shop : shops) {
            Integer shopId = Convert.toInt(shop.get("id"));
            if (shopId != null && !shopMap.containsKey(shopId)) {
                shopMap.put(shopId, shop);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(shopMap.values());
        log.debug("商铺去重：原数量={}, 去重后数量={}", shops.size(), result.size());
        return result;
    }

}
