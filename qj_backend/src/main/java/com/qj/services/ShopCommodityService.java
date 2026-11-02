package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.config.MyJsonConfig;
import com.qj.consts.AgentStatusCodes;
import com.qj.consts.FunctionCodes;
import com.qj.services.common.CRUDService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商城商品服务类
 */
@Service
public class ShopCommodityService {
    private static final Logger log = LoggerFactory.getLogger(ShopCommodityService.class);

    @Autowired
    private AgentService agentService;

    /**
     * 获取商品分页列表
     * 
     * @param pageNumber 页码
     * @param pageSize   每页大小
     * @param type       商品类型
     * @param brand      品牌
     * @param keywords   关键词
     * @param shopId     商铺ID
     * @return 商品列表
     */
    public JSONObject getCommodityPage(Integer pageNumber, Integer pageSize, String type,
            String brand, String keywords, Integer shopId) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            // 设置分页参数
            fieldKeys.add("pageSize");
            fieldKeys.add("offset");
            values.add(pageSize != null ? pageSize : 10);
            values.add(pageNumber != null ? (pageNumber - 1) * (pageSize != null ? pageSize : 10) : 0);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            // 如果有筛选条件，需要动态构建SQL
            if (StrUtil.isNotBlank(type) || StrUtil.isNotBlank(brand) ||
                    StrUtil.isNotBlank(keywords) || shopId != null) {
                return getCommodityScreenPage(pageNumber, pageSize, type, brand, keywords, shopId);
            }

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6001_COMMODITY_LIST,
                    "F_501_6001_COMMODITY_LIST",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取商品列表失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取总数
            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6016_COMMODITY_COUNT,
                    "F_501_6016_COMMODITY_COUNT",
                    "qj_shop_commodity",
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
            log.error("获取商品分页列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商品列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取推荐商品列表
     * 
     * @param pageNumber 页码
     * @param pageSize   每页大小
     * @param typeByTab  分类标签
     * @param keywords   关键词
     * @return 推荐商品列表
     */
    public JSONObject getCommodityStarPage(Integer pageNumber, Integer pageSize,
            Integer typeByTab, String keywords) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("pageSize");
            fieldKeys.add("offset");
            values.add(pageSize != null ? pageSize : 10);
            values.add(pageNumber != null ? (pageNumber - 1) * (pageSize != null ? pageSize : 10) : 0);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6002_COMMODITY_STAR_LIST,
                    "F_501_6002_COMMODITY_STAR_LIST",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取推荐商品列表失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取总数
            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6017_COMMODITY_STAR_COUNT,
                    "F_501_6017_COMMODITY_STAR_COUNT",
                    "qj_shop_commodity",
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
            log.error("获取推荐商品列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取推荐商品列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据ID获取商品详情
     * 
     * @param commodityId 商品ID
     * @return 商品详情
     */
    public JSONObject getCommodityById(Integer commodityId) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("id");
            values.add(commodityId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6003_COMMODITY_GET_BY_ID,
                    "F_501_6003_COMMODITY_GET_BY_ID",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取商品详情失败：{}", result.getStr("errMsg"));
                return result;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodityList = (List<Map<String, Object>>) result.get("result");
            if (commodityList == null || commodityList.isEmpty()) {
                JSONObject notFoundResult = new JSONObject();
                notFoundResult.set("errCode", 1);
                notFoundResult.set("errMsg", "商品不存在或已下架");
                return notFoundResult;
            }

            // 获取商品规格信息
            JSONObject specParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray specFieldKeys = new JSONArray();
            JSONArray specValues = new JSONArray();

            specFieldKeys.add("commodity_id");
            specValues.add(commodityId);

            specParam.set("__mysql_fieldkeys__", specFieldKeys);
            specParam.set("__mysql_values__", specValues);

            JSONObject specResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6012_COMMODITY_SPEC_LIST,
                    "F_501_6012_COMMODITY_SPEC_LIST",
                    "qj_shop_commodity",
                    specParam,
                    new HashSet<>());

            if (specResult.getInt("errCode") == 0) {
                result.set("specifications", specResult.get("result"));
            }

            return result;

        } catch (Exception e) {
            log.error("获取商品详情异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商品详情异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取商品分类列表
     * 
     * @return 商品分类列表
     */
    public JSONObject getCommodityTypes() {
        try {
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6004_COMMODITY_TYPES_LIST,
                    "F_501_6004_COMMODITY_TYPES_LIST",
                    "qj_shop_commodity",
                    new JSONObject(MyJsonConfig.getJsonConfig()),
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取商品分类列表失败：{}", result.getStr("errMsg"));
            }

            return result;

        } catch (Exception e) {
            log.error("获取商品分类列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商品分类列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据商铺ID获取商品分类（包含"自是"商家类别补全逻辑）
     *
     * @param shopId 商铺ID
     * @return 商品分类列表
     */
    public JSONObject getCommodityTypesByShopId(Integer shopId) {
        try {
            // 1. 获取指定商家的类别
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("shop_id");
            values.add(shopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject shopTypesResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6005_COMMODITY_TYPES_BY_SHOP,
                    "F_501_6005_COMMODITY_TYPES_BY_SHOP",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (shopTypesResult.getInt("errCode") != 0) {
                log.error("根据商铺获取商品分类失败：{}", shopTypesResult.getStr("errMsg"));
                return shopTypesResult;
            }

            // 获取商家原有类别
            JSONArray shopTypes = shopTypesResult.getJSONArray("result");
            log.info("商家{}原有类别数量：{}", shopId, shopTypes != null ? shopTypes.size() : 0);

            // 添加详细调试信息
            if (shopTypes != null && shopTypes.size() > 0) {
                log.info("商家{}的类别详情：", shopId);
                for (int i = 0; i < shopTypes.size(); i++) {
                    JSONObject type = shopTypes.getJSONObject(i);
                    log.info("  - ID: {}, 名称: {}, is_clothing: {}",
                            type.get("id"), type.get("name"), type.get("is_clothing"));
                }
            } else {
                log.warn("商家{}没有查询到类别，可能的原因：", shopId);
                log.warn("  1. 商家没有商品数据");
                log.warn("  2. 商品的is_del字段不是0");
                log.warn("  3. 类别的is_del字段不是2");
                log.warn("  4. 商品的type字段与类别表的name字段不匹配");

                // 让我们查询一下商家的商品数据来调试
                debugShopCommodities(shopId);
            }

            // 如果商家原有类别数量为0，检查是否为品牌商家
            if (shopTypes == null || shopTypes.size() == 0) {
                log.info("商家{}没有原有类别，检查是否为品牌商家", shopId);

                // 检查是否为品牌商家
                boolean isBrand = isBrandShop(shopId);
                log.info("商家{}品牌检查结果：{}", shopId, isBrand ? "是品牌商家" : "不是品牌商家");

                if (!isBrand) {
                    log.info("商家{}不是品牌商家，直接返回空类别", shopId);
                    return shopTypesResult;
                }

                // 品牌商家没有自己的类别，使用层级补全逻辑
                log.info("品牌商家{}没有自己的类别，开始进行层级类别补全", shopId);
                return performHierarchicalCategoryCompletion(shopId, shopTypes);
            }

            // 2. 检查是否为品牌商家，只有品牌商家才进行类别补全
            boolean isBrand = isBrandShop(shopId);
            log.info("商家{}品牌检查结果：{}", shopId, isBrand ? "是品牌商家" : "不是品牌商家");

            if (!isBrand) {
                log.info("商家{}不是品牌商家，直接返回原有类别", shopId);
                return shopTypesResult;
            }

            // 3. 品牌商家进行层级类别补全
            log.info("商家{}是品牌商家，开始进行层级类别补全", shopId);
            return performHierarchicalCategoryCompletion(shopId, shopTypes);

        } catch (Exception e) {
            log.error("根据商铺获取商品分类异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "根据商铺获取商品分类异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 执行层级类别补全
     * 1. 先从上级代理的品牌商铺补全
     * 2. 再从"自是汇品"补全
     */
    private JSONObject performHierarchicalCategoryCompletion(Integer shopId, JSONArray currentTypes) {
        try {
            log.info("开始为商铺{}执行层级类别补全", shopId);

            // 1. 获取当前商铺对应的代理openid
            String agentOpenid = getAgentOpenidByShopId(shopId);
            if (agentOpenid == null) {
                log.info("商铺{}没有对应的代理，直接进行'自是汇品'补全", shopId);
                return performZiShiCategoryCompletion(shopId, currentTypes);
            }

            log.info("商铺{}对应的代理openid: {}", shopId, agentOpenid);

            // 2. 获取上级代理openid
            String superiorAgentOpenid = getSuperiorAgentOpenid(agentOpenid);
            if (superiorAgentOpenid == null) {
                log.info("代理{}没有上级代理", agentOpenid);

                // 检查是否为城市代理，如果是则从下级代理获取类别
                Integer agentRole = getAgentRole(agentOpenid);
                if (agentRole != null && agentRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    log.info("代理{}是城市代理，从下级代理获取类别", agentOpenid);
                    return performSubordinateCategoryCompletion(shopId, currentTypes, agentOpenid);
                } else {
                    log.info("代理{}不是城市代理，直接进行'自是汇品'补全", agentOpenid);
                    return performZiShiCategoryCompletion(shopId, currentTypes);
                }
            }

            log.info("代理{}的上级代理openid: {}", agentOpenid, superiorAgentOpenid);

            // 3. 获取上级代理的商铺ID
            Integer superiorShopId = getShopIdByAgentOpenid(superiorAgentOpenid);
            if (superiorShopId == null) {
                log.info("上级代理{}没有关联商铺，直接进行'自是汇品'补全", superiorAgentOpenid);
                return performZiShiCategoryCompletion(shopId, currentTypes);
            }

            log.info("上级代理{}的商铺ID: {}", superiorAgentOpenid, superiorShopId);

            // 4. 检查上级商铺是否为品牌商铺
            if (!isBrandShop(superiorShopId)) {
                log.info("上级商铺{}不是品牌商铺，直接进行'自是汇品'补全", superiorShopId);
                return performZiShiCategoryCompletion(shopId, currentTypes);
            }

            // 5. 从上级品牌商铺补全类别
            JSONArray typesAfterSuperior = performSuperiorCategoryCompletion(shopId, currentTypes, superiorShopId);

            // 6. 最后从"自是汇品"补全剩余类别
            return performZiShiCategoryCompletion(shopId, typesAfterSuperior);

        } catch (Exception e) {
            log.error("层级类别补全异常，商铺ID: {}", shopId, e);
            // 异常时回退到"自是汇品"补全
            return performZiShiCategoryCompletion(shopId, currentTypes);
        }
    }

    /**
     * 根据商铺ID获取代理openid
     */
    private String getAgentOpenidByShopId(Integer shopId) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("shop_id");
            values.add(shopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4045_AGENT_GET_BY_SHOP_ID, // 根据商铺ID获取代理
                    "F_301_4045_AGENT_GET_BY_SHOP_ID",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agents = (List<Map<String, Object>>) result.get("result");
                if (agents != null && !agents.isEmpty()) {
                    String openid = Convert.toStr(agents.get(0).get("openid"));
                    log.info("商铺{}对应的代理openid: {}", shopId, openid);
                    return openid;
                }
            }

            log.info("商铺{}没有对应的代理", shopId);
            return null;

        } catch (Exception e) {
            log.error("根据商铺ID获取代理openid异常，shopId: {}", shopId, e);
            return null;
        }
    }

    /**
     * 获取上级代理openid
     */
    private String getSuperiorAgentOpenid(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("user_openid");
            values.add(agentOpenid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            // 查询推广关系获取上级代理
            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4210_AGENT_USER_GET_SUPERIOR, // 根据用户openid获取上级代理
                    "F_303_4210_AGENT_USER_GET_SUPERIOR",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("result");
                if (relations != null && !relations.isEmpty()) {
                    String superiorOpenid = Convert.toStr(relations.get(0).get("agent_openid"));
                    log.info("代理{}的上级代理openid: {}", agentOpenid, superiorOpenid);
                    return superiorOpenid;
                }
            }

            log.info("代理{}没有上级代理", agentOpenid);
            return null;

        } catch (Exception e) {
            log.error("获取上级代理openid异常，agentOpenid: {}", agentOpenid, e);
            return null;
        }
    }

    /**
     * 根据代理openid获取商铺ID
     */
    private Integer getShopIdByAgentOpenid(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("openid");
            values.add(agentOpenid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agents = (List<Map<String, Object>>) result.get("result");
                if (agents != null && !agents.isEmpty()) {
                    Integer shopId = Convert.toInt(agents.get(0).get("shop_id"));
                    log.info("代理{}的商铺ID: {}", agentOpenid, shopId);
                    return shopId;
                }
            }

            log.info("代理{}没有关联商铺", agentOpenid);
            return null;

        } catch (Exception e) {
            log.error("根据代理openid获取商铺ID异常，agentOpenid: {}", agentOpenid, e);
            return null;
        }
    }

    /**
     * 从上级品牌商铺补全类别
     */
    private JSONArray performSuperiorCategoryCompletion(Integer shopId, JSONArray currentTypes,
            Integer superiorShopId) {
        try {
            log.info("开始从上级商铺{}为商铺{}补全类别", superiorShopId, shopId);

            // 获取上级商铺的类别
            JSONObject superiorTypesResult = getCommodityTypesByShopIdInternal(superiorShopId);
            if (superiorTypesResult.getInt("errCode") != 0) {
                log.warn("获取上级商铺{}类别失败：{}，跳过上级补全", superiorShopId, superiorTypesResult.getStr("errMsg"));
                return currentTypes;
            }

            JSONArray superiorTypes = superiorTypesResult.getJSONArray("result");
            if (superiorTypes == null || superiorTypes.size() == 0) {
                log.info("上级商铺{}没有类别，跳过上级补全", superiorShopId);
                return currentTypes;
            }

            log.info("上级商铺{}有{}个类别", superiorShopId, superiorTypes.size());

            // 合并类别
            JSONArray mergedTypes = new JSONArray();
            mergedTypes.addAll(currentTypes);

            // 创建当前已有类别的名称集合
            Set<String> existingTypeNames = new HashSet<>();
            for (int i = 0; i < currentTypes.size(); i++) {
                JSONObject type = currentTypes.getJSONObject(i);
                existingTypeNames.add(type.getStr("name"));
            }

            // 添加上级商铺中当前商铺没有的类别
            int addedCount = 0;
            for (int i = 0; i < superiorTypes.size(); i++) {
                JSONObject superiorType = superiorTypes.getJSONObject(i);
                String typeName = superiorType.getStr("name");
                if (!existingTypeNames.contains(typeName)) {
                    mergedTypes.add(superiorType);
                    addedCount++;
                    log.info("为商铺{}从上级商铺{}补充了类别：{}", shopId, superiorShopId, typeName);
                }
            }

            log.info("从上级商铺{}补全完成：为商铺{}补充了{}个类别，总计{}个类别",
                    superiorShopId, shopId, addedCount, mergedTypes.size());

            return mergedTypes;

        } catch (Exception e) {
            log.error("从上级商铺补全类别异常，shopId: {}, superiorShopId: {}", shopId, superiorShopId, e);
            return currentTypes;
        }
    }

    /**
     * 从下级代理商铺补全类别（城市代理专用）
     */
    private JSONObject performSubordinateCategoryCompletion(Integer shopId, JSONArray currentTypes,
            String cityAgentOpenid) {
        try {
            log.info("开始为城市代理{}的商铺{}从下级代理补全类别", cityAgentOpenid, shopId);

            // 1. 获取城市代理下级的所有商铺ID
            List<Integer> subordinateShopIds = getSubordinateShopIds(cityAgentOpenid);
            if (subordinateShopIds.isEmpty()) {
                log.info("城市代理{}没有下级商铺，直接进行'自是汇品'补全", cityAgentOpenid);
                return performZiShiCategoryCompletion(shopId, currentTypes);
            }

            log.info("城市代理{}的下级商铺ID列表：{}", cityAgentOpenid, subordinateShopIds);

            // 2. 从所有下级商铺获取类别并合并
            JSONArray finalTypes = new JSONArray();
            finalTypes.addAll(currentTypes);

            // 创建已有类别的名称集合，用于快速查找
            Set<String> existingTypeNames = new HashSet<>();
            for (int i = 0; i < currentTypes.size(); i++) {
                JSONObject type = currentTypes.getJSONObject(i);
                existingTypeNames.add(type.getStr("name"));
            }

            int totalAddedCount = 0;
            for (Integer subordinateShopId : subordinateShopIds) {
                // 检查下级商铺是否为品牌商铺
                if (!isBrandShop(subordinateShopId)) {
                    log.info("下级商铺{}不是品牌商铺，跳过", subordinateShopId);
                    continue;
                }

                // 获取下级商铺的类别
                JSONObject subordinateParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray subordinateFieldKeys = new JSONArray();
                subordinateFieldKeys.add("shop_id");
                subordinateParam.set("__mysql_fieldkeys__", subordinateFieldKeys);
                subordinateParam.set("shop_id", subordinateShopId);

                JSONObject subordinateTypesResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_501_6005_COMMODITY_TYPES_BY_SHOP,
                        "F_501_6005_COMMODITY_TYPES_BY_SHOP",
                        "qj_shop_commodity",
                        subordinateParam,
                        new HashSet<>());

                if (subordinateTypesResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subordinateTypesList = (List<Map<String, Object>>) subordinateTypesResult
                            .get("result");
                    if (subordinateTypesList != null) {
                        JSONArray subordinateTypes = new JSONArray(subordinateTypesList);

                        // 添加下级商铺中还没有的类别
                        int shopAddedCount = 0;
                        for (int i = 0; i < subordinateTypes.size(); i++) {
                            JSONObject subordinateType = subordinateTypes.getJSONObject(i);
                            String typeName = subordinateType.getStr("name");
                            if (!existingTypeNames.contains(typeName)) {
                                finalTypes.add(subordinateType);
                                existingTypeNames.add(typeName);
                                shopAddedCount++;
                                totalAddedCount++;
                                log.info("为城市代理商铺{}从下级商铺{}补充了类别：{}", shopId, subordinateShopId, typeName);
                            }
                        }
                        log.info("从下级商铺{}补充了{}个类别", subordinateShopId, shopAddedCount);
                    }
                }
            }

            log.info("从下级代理商铺补全完成：为城市代理商铺{}补充了{}个类别", shopId, totalAddedCount);

            // 3. 最后从"自是汇品"补全剩余类别
            JSONObject finalResult = performZiShiCategoryCompletion(shopId, finalTypes);

            return finalResult;

        } catch (Exception e) {
            log.error("从下级代理商铺补全类别异常，商铺ID: {}, 城市代理: {}", shopId, cityAgentOpenid, e);
            // 异常时回退到"自是汇品"补全
            return performZiShiCategoryCompletion(shopId, currentTypes);
        }
    }

    /**
     * 获取代理角色
     */
    private Integer getAgentRole(String agentOpenid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("openid");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("openid", agentOpenid);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_301_4003_AGENT_GET_BY_OPENID,
                    "F_301_4003_AGENT_GET_BY_OPENID",
                    "qj_marketing_v2_agents",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> agents = (List<Map<String, Object>>) result.get("result");
                if (agents != null && !agents.isEmpty()) {
                    Object currentRole = agents.get(0).get("current_role");
                    return currentRole != null ? Convert.toInt(currentRole) : null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取代理角色异常：agentOpenid={}", agentOpenid, e);
            return null;
        }
    }

    /**
     * 从"自是汇品"补全类别
     */
    private JSONObject performZiShiCategoryCompletion(Integer shopId, JSONArray currentTypes) {
        try {
            log.info("开始从'自是汇品'为商铺{}补全类别", shopId);

            // 获取"自是汇品"商家的类别
            JSONObject ziShiParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray ziShiFieldKeys = new JSONArray();
            JSONArray ziShiValues = new JSONArray();

            ziShiFieldKeys.add("shop_id");
            ziShiValues.add(1); // 直接使用"自是汇品"的ID

            ziShiParam.set("__mysql_fieldkeys__", ziShiFieldKeys);
            ziShiParam.set("__mysql_values__", ziShiValues);

            JSONObject ziShiTypesResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6005_COMMODITY_TYPES_BY_SHOP,
                    "F_501_6005_COMMODITY_TYPES_BY_SHOP",
                    "qj_shop_commodity",
                    ziShiParam,
                    new HashSet<>());

            if (ziShiTypesResult.getInt("errCode") != 0) {
                log.warn("获取'自是汇品'商家类别失败：{}，返回当前类别", ziShiTypesResult.getStr("errMsg"));
                JSONObject finalResult = new JSONObject();
                finalResult.set("errCode", 0);
                finalResult.set("errMsg", "success");
                finalResult.set("result", currentTypes);
                return finalResult;
            }

            JSONArray ziShiTypes = ziShiTypesResult.getJSONArray("result");
            log.info("'自是汇品'商家类别数量：{}", ziShiTypes != null ? ziShiTypes.size() : 0);

            if (ziShiTypes == null || ziShiTypes.size() == 0) {
                log.info("'自是汇品'商家没有类别，返回当前类别");
                JSONObject finalResult = new JSONObject();
                finalResult.set("errCode", 0);
                finalResult.set("errMsg", "success");
                finalResult.set("result", currentTypes);
                return finalResult;
            }

            // 合并类别列表
            JSONArray finalTypes = new JSONArray();
            finalTypes.addAll(currentTypes);

            // 创建已有类别的名称集合，用于快速查找
            Set<String> existingTypeNames = new HashSet<>();
            for (int i = 0; i < currentTypes.size(); i++) {
                JSONObject type = currentTypes.getJSONObject(i);
                existingTypeNames.add(type.getStr("name"));
            }

            // 添加"自是汇品"商家中还没有的类别
            int addedCount = 0;
            for (int i = 0; i < ziShiTypes.size(); i++) {
                JSONObject ziShiType = ziShiTypes.getJSONObject(i);
                String typeName = ziShiType.getStr("name");
                if (!existingTypeNames.contains(typeName)) {
                    finalTypes.add(ziShiType);
                    addedCount++;
                    log.info("为商铺{}从'自是汇品'补充了类别：{}", shopId, typeName);
                }
            }

            log.info("从'自是汇品'补全完成：为商铺{}补充了{}个类别，最终{}个类别",
                    shopId, addedCount, finalTypes.size());

            // 构建最终结果
            JSONObject finalResult = new JSONObject();
            finalResult.set("errCode", 0);
            finalResult.set("errMsg", "success");
            finalResult.set("result", finalTypes);

            return finalResult;

        } catch (Exception e) {
            log.error("从'自是汇品'补全类别异常，shopId: {}", shopId, e);
            JSONObject finalResult = new JSONObject();
            finalResult.set("errCode", 0);
            finalResult.set("errMsg", "success");
            finalResult.set("result", currentTypes);
            return finalResult;
        }
    }

    /**
     * 内部方法：获取商铺类别（不进行递归补全，避免无限循环）
     */
    private JSONObject getCommodityTypesByShopIdInternal(Integer shopId) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("shop_id");
            values.add(shopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            return CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6005_COMMODITY_TYPES_BY_SHOP,
                    "F_501_6005_COMMODITY_TYPES_BY_SHOP",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

        } catch (Exception e) {
            log.error("内部获取商铺类别异常，shopId: {}", shopId, e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取商铺类别异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 确定指定类别对应的实际商铺ID（用于商品查询）
     * 根据层级补全逻辑确定商品来源
     */
    private Integer determineActualShopIdForType(Integer shopId, String type) {
        try {
            log.info("开始确定商铺{}类别'{}'的实际商品来源", shopId, type);

            // 1. 检查是否是商铺自己的类别
            if (isShopOwnType(shopId, type)) {
                log.info("类别'{}'是商铺{}自己的类别", type, shopId);
                return shopId;
            }

            // 2. 检查是否是从上级商铺补全的类别
            Integer superiorShopId = getSuperiorShopIdForType(shopId, type);
            if (superiorShopId != null) {
                log.info("类别'{}'是从上级商铺{}补全的", type, superiorShopId);
                return superiorShopId;
            }

            // 3. 检查是否是城市代理，如果是则优先从下级代理商铺获取商品
            String agentOpenid = getAgentOpenidByShopId(shopId);
            if (agentOpenid != null) {
                Integer agentRole = getAgentRole(agentOpenid);
                if (agentRole != null && agentRole == AgentStatusCodes.ROLE_CITY_AGENT) {
                    log.info("商铺{}对应的代理{}是城市代理，检查下级商铺是否有类别'{}'", shopId, agentOpenid, type);

                    // 从下级代理商铺中查找有该类别商品的商铺
                    Integer subordinateShopId = getSubordinateShopIdForType(agentOpenid, type);
                    if (subordinateShopId != null) {
                        log.info("类别'{}'从城市代理下级商铺{}获取", type, subordinateShopId);
                        return subordinateShopId;
                    }
                }
            }

            // 4. 默认从"自是汇品"获取
            log.info("类别'{}'是从'自是汇品'补全的", type);
            return 1; // "自是汇品"的ID

        } catch (Exception e) {
            log.error("确定类别商品来源异常，shopId: {}, type: {}", shopId, type, e);
            return 1; // 异常时默认从"自是汇品"获取
        }
    }

    /**
     * 获取指定类别对应的上级商铺ID
     */
    private Integer getSuperiorShopIdForType(Integer shopId, String type) {
        try {
            // 1. 获取当前商铺对应的代理openid
            String agentOpenid = getAgentOpenidByShopId(shopId);
            if (agentOpenid == null) {
                return null;
            }

            // 2. 获取上级代理openid
            String superiorAgentOpenid = getSuperiorAgentOpenid(agentOpenid);
            if (superiorAgentOpenid == null) {
                return null;
            }

            // 3. 获取上级代理的商铺ID
            Integer superiorShopId = getShopIdByAgentOpenid(superiorAgentOpenid);
            if (superiorShopId == null) {
                return null;
            }

            // 4. 检查上级商铺是否为品牌商铺
            if (!isBrandShop(superiorShopId)) {
                return null;
            }

            // 5. 检查上级商铺是否有这个类别
            if (isShopOwnType(superiorShopId, type)) {
                log.info("上级商铺{}有类别'{}'", superiorShopId, type);
                return superiorShopId;
            }

            return null;

        } catch (Exception e) {
            log.error("获取上级商铺类别来源异常，shopId: {}, type: {}", shopId, type, e);
            return null;
        }
    }

    /**
     * 商品筛选分页列表
     * 
     * @param pageNumber 页码
     * @param pageSize   每页大小
     * @param type       商品类型
     * @param brand      品牌
     * @param keywords   关键词
     * @param shopId     商铺ID
     * @return 筛选后的商品列表
     */
    private JSONObject getCommodityScreenPage(Integer pageNumber, Integer pageSize,
            String type, String brand, String keywords, Integer shopId) {
        try {
            log.info("商品筛选查询：pageNumber={}, pageSize={}, type={}, brand={}, keywords={}, shopId={}",
                    pageNumber, pageSize, type, brand, keywords, shopId);

            // 智能商品查询逻辑：
            // 1. 如果指定了shopId且为品牌商家，需要判断类别来源
            // 2. 如果类别是品牌商家自己的，查询品牌商家的商品
            // 3. 如果类别是从上级商铺补全的，查询上级商铺的商品
            // 4. 如果类别是从"自是汇品"补全的，查询"自是汇品"的商品

            Integer actualShopId = shopId;

            if (shopId != null && StrUtil.isNotBlank(type)) {
                // 检查是否为品牌商家
                boolean isBrand = isBrandShop(shopId);
                log.info("商家{}是否为品牌商家：{}", shopId, isBrand);

                if (isBrand) {
                    // 使用层级逻辑确定商品来源商铺
                    actualShopId = determineActualShopIdForType(shopId, type);
                    log.info("类别'{}'的实际商品来源商铺ID：{}", type, actualShopId);
                }
            }

            // 使用功能码查询基础商品数据
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6036_COMMODITY_FILTER_QUERY,
                    "F_501_6036_COMMODITY_FILTER_QUERY",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("商品筛选查询失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取查询结果并进行Java层筛选
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allItems = (List<Map<String, Object>>) result.get("result");

            if (allItems == null) {
                allItems = new ArrayList<>();
            }

            log.info("基础查询获取到{}件商品，开始应用筛选条件", allItems.size());

            // 在Java层应用筛选条件
            List<Map<String, Object>> filteredItems = applyFilters(allItems, type, brand, keywords, actualShopId);

            log.info("筛选后剩余{}件商品", filteredItems.size());

            // 应用分页
            int limit = pageSize != null ? pageSize : 10;
            int offset = pageNumber != null ? (pageNumber - 1) * limit : 0;

            // 防止分页参数异常
            if (offset < 0) {
                log.warn("分页参数异常：offset={}, 重置为0", offset);
                offset = 0;
            }

            log.info("分页参数：pageNumber={}, pageSize={}, 计算得到：offset={}, limit={}",
                    pageNumber, pageSize, offset, limit);

            List<Map<String, Object>> pagedItems = applyPagination(filteredItems, offset, limit);

            log.info("分页后返回{}件商品", pagedItems.size());

            result.set("result", pagedItems);
            result.set("total", filteredItems.size()); // 设置总数为筛选后的数量

            // 设置分页状态信息
            boolean isLastPage = (offset + limit) >= filteredItems.size();
            result.set("isLastPage", isLastPage);
            result.set("currentPage", pageNumber != null ? pageNumber : 1);
            result.set("pageSize", limit);
            result.set("totalPages", (int) Math.ceil((double) filteredItems.size() / limit));

            // 特殊处理：如果是第一页且只有少量商品，标记为最后一页，防止前端自动加载
            if ((pageNumber == null || pageNumber == 1) && filteredItems.size() <= 6) {
                result.set("isLastPage", true);
                log.info("第一页商品数量较少（{}件），设置为最后一页，防止自动加载", filteredItems.size());
            }

            result.set("errCode", 0);

            if (result.getInt("errCode") != 0) {
                log.error("商品筛选查询失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取总数 - 使用筛选后的数据计算总数
            result.set("total", filteredItems.size());

            log.info("商品筛选查询完成，实际查询shopId={}，返回{}个商品", actualShopId,
                    result.getJSONArray("result") != null ? result.getJSONArray("result").size() : 0);

            return result;

        } catch (Exception e) {
            log.error("商品筛选查询异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "商品筛选查询异常：" + e.getMessage());
            return errorResult;
        }
    }

    // 注意：原qjapi中没有getBrandList接口，这里删除该方法

    /**
     * 根据品牌获取商品分类
     * 完全按照原qjapi的逻辑实现
     *
     * @param brand 品牌名称
     * @return 该品牌的商品分类列表
     */
    public JSONObject getCommodityTypesByBrand(String brand) {
        try {
            // 按照原qjapi的逻辑：
            // 1. 先查询该品牌下有哪些商品类型
            // 2. 再从商品分类表中获取对应的分类信息

            // 第一步：查询该品牌下的商品类型
            JSONObject param1 = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys1 = new JSONArray();
            JSONArray values1 = new JSONArray();

            fieldKeys1.add("brand");
            values1.add(brand);

            param1.set("__mysql_fieldkeys__", fieldKeys1);
            param1.set("__mysql_values__", values1);

            JSONObject typeResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6020_COMMODITY_TYPES_BY_BRAND,
                    "F_501_6020_COMMODITY_TYPES_BY_BRAND",
                    "qj_shop_commodity",
                    param1,
                    new HashSet<>());

            if (typeResult.getInt("errCode") != 0) {
                log.error("查询品牌商品类型失败：{}", typeResult.getStr("errMsg"));
                return typeResult;
            }

            // 第二步：获取所有商品分类信息
            JSONObject allTypesResult = getCommodityTypes();
            if (allTypesResult.getInt("errCode") != 0) {
                log.error("获取商品分类列表失败：{}", allTypesResult.getStr("errMsg"));
                return allTypesResult;
            }

            // 第三步：筛选出该品牌对应的分类
            JSONArray brandTypes = typeResult.getJSONArray("result");
            JSONArray allTypes = allTypesResult.getJSONArray("result");
            JSONArray resultTypes = new JSONArray();

            if (brandTypes != null && allTypes != null) {
                // 创建品牌类型的哈希表
                Set<String> brandTypeNames = new HashSet<>();
                for (int i = 0; i < brandTypes.size(); i++) {
                    JSONObject typeObj = brandTypes.getJSONObject(i);
                    String typeName = typeObj.getStr("type");
                    if (typeName != null && !typeName.trim().isEmpty()) {
                        brandTypeNames.add(typeName);
                    }
                }

                // 筛选匹配的分类
                for (int i = 0; i < allTypes.size(); i++) {
                    JSONObject typeObj = allTypes.getJSONObject(i);
                    String typeName = typeObj.getStr("name");
                    if (typeName != null && brandTypeNames.contains(typeName)) {
                        resultTypes.add(typeObj);
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("errMsg", "success");
            result.set("result", resultTypes);
            return result;

        } catch (Exception e) {
            log.error("根据品牌获取商品分类异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据品牌获取商品分类异常：" + e.getMessage());
            return result;
        }
    }

    // 注意：原qjapi中没有getBrandTypes接口，这里删除该方法

    /**
     * 搜索商品和文章
     *
     * @param keywords   搜索关键词
     * @param pageNumber 页码
     * @param pageSize   每页大小
     * @return 搜索结果
     */
    public JSONObject searchCommoditiesArticles(String keywords, Integer pageNumber, Integer pageSize) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            // 设置搜索参数
            fieldKeys.add("keywords1");
            fieldKeys.add("keywords2");
            fieldKeys.add("keywords3");
            fieldKeys.add("pageSize");
            fieldKeys.add("offset");

            String searchPattern = "%" + keywords + "%";
            values.add(searchPattern);
            values.add(searchPattern);
            values.add(searchPattern);
            values.add(pageSize != null ? pageSize : 10);
            values.add(pageNumber != null ? (pageNumber - 1) * (pageSize != null ? pageSize : 10) : 0);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6007_COMMODITY_SEARCH,
                    "F_501_6007_COMMODITY_SEARCH",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("搜索商品和文章失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 获取搜索结果总数
            JSONObject countParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray countFieldKeys = new JSONArray();
            JSONArray countValues = new JSONArray();

            countFieldKeys.add("keywords1");
            countFieldKeys.add("keywords2");
            countFieldKeys.add("keywords3");
            countValues.add(searchPattern);
            countValues.add(searchPattern);
            countValues.add(searchPattern);

            countParam.set("__mysql_fieldkeys__", countFieldKeys);
            countParam.set("__mysql_values__", countValues);

            JSONObject countResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6018_COMMODITY_SEARCH_COUNT,
                    "F_501_6018_COMMODITY_SEARCH_COUNT",
                    "qj_shop_commodity",
                    countParam,
                    new HashSet<>());

            if (countResult.getInt("errCode") == 0) {
                result.set("totalCount", countResult.get("result"));
            }

            return result;
        } catch (Exception e) {
            log.error("搜索商品和文章异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "搜索商品和文章异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 获取万能款商品
     *
     * @return 万能款商品列表
     */
    public JSONObject getUniversalCommodity() {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6008_COMMODITY_UNIVERSAL_LIST,
                    "F_501_6008_COMMODITY_UNIVERSAL_LIST",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取万能款商品失败：{}", result.getStr("errMsg"));
                return result;
            }

            return result;
        } catch (Exception e) {
            log.error("获取万能款商品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取万能款商品异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 检查商家是否为品牌商家
     *
     * @param shopId 商铺ID
     * @return true表示是品牌商家，false表示不是
     */
    private boolean isBrandShop(Integer shopId) {
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

            if (result.getInt("errCode") == 0) {
                JSONArray data = result.getJSONArray("result");
                if (data != null && data.size() > 0) {
                    JSONObject shop = data.getJSONObject(0);
                    Integer isBrand = shop.getInt("is_brand");
                    // is_brand = 1 表示品牌商家，2 表示普通商家
                    boolean brandResult = isBrand != null && isBrand == 1;
                    log.info("商家{}的is_brand值：{}，判断结果：{}", shopId, isBrand, brandResult ? "品牌商家" : "普通商家");
                    return brandResult;
                }
            }

            log.warn("无法获取商家{}的品牌信息", shopId);
            return false;

        } catch (Exception e) {
            log.error("检查商家{}是否为品牌商家时发生异常", shopId, e);
            return false;
        }
    }

    /**
     * 检查某个类别是否是商家自己的类别
     *
     * @param shopId 商铺ID
     * @param type   类别名称
     * @return true表示是商家自己的类别，false表示不是
     */
    private boolean isShopOwnType(Integer shopId, String type) {
        try {
            log.info("检查商家{}是否有类别'{}'的商品", shopId, type);

            // 使用功能码查询
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("shop_id");
            fieldKeys.add("type");
            values.add(shopId);
            values.add(type);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6038_SHOP_TYPE_CHECK,
                    "F_501_6038_SHOP_TYPE_CHECK",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("result");
                if (items != null && !items.isEmpty()) {
                    Integer count = Convert.toInt(items.get(0).get("total"));
                    boolean hasType = count != null && count > 0;
                    log.info("商家{}{}类别'{}'的商品（数量：{}）", shopId, hasType ? "有" : "没有", type, count);
                    return hasType;
                }
            }

            log.warn("无法检查商家{}的类别'{}'", shopId, type);
            return false;

        } catch (Exception e) {
            log.error("检查商家{}类别'{}'时发生异常", shopId, type, e);
            return false;
        }
    }

    /**
     * 调试商家商品数据
     */
    private void debugShopCommodities(Integer shopId) {
        try {
            log.info("开始调试商家{}的商品数据", shopId);

            // 直接执行SQL查询来调试
            String debugSql = "SELECT id, type, is_del, is_public, onsale FROM qj_shop_commodity WHERE shop_id = ? LIMIT 10";
            log.info("执行调试SQL: {}, 参数: {}", debugSql, shopId);

            // 由于CRUDService比较复杂，我们先通过日志分析问题
            // 从原始查询的SQL可以看出问题：
            log.warn("分析原始查询SQL的问题：");
            log.warn(
                    "1. 原始SQL: SELECT DISTINCT t.id, t.name, t.is_clothing FROM qj_shop_commodity_type t INNER JOIN qj_shop_commodity c ON t.name = c.type WHERE c.shop_id = ? AND c.is_del = 0 AND t.is_del = 2");
            log.warn("2. 这个查询要求：");
            log.warn("   - 商品表(qj_shop_commodity)中存在shop_id={}的记录", shopId);
            log.warn("   - 商品的is_del=0（未删除）");
            log.warn("   - 商品的type字段要与类别表(qj_shop_commodity_type)的name字段完全匹配");
            log.warn("   - 类别表的is_del=2（未删除）");

            // 让我们检查一下前端显示的类别是否来自其他地方
            log.warn("前端显示的类别（耳环、项链、戒指、手镯、发簪）可能来源：");
            log.warn("1. 缓存数据");
            log.warn("2. 其他API接口");
            log.warn("3. 前端硬编码");
            log.warn("4. 全局类别列表（不按商家过滤）");

        } catch (Exception e) {
            log.error("调试商家{}商品数据时发生异常", shopId, e);
        }
    }

    // ===== 智能搭配相关方法 =====

    /**
     * 根据类型、优先级、季节获取智能搭配商品
     *
     * @param type     商品类型
     * @param priority 风格优先级
     * @param openid   用户openid
     * @param season   季节
     * @return 智能搭配商品列表
     */
    public JSONObject getSmartMatchCommodities(String type, Integer priority, String openid, String season) {
        return getSmartMatchCommodities(type, priority, openid, season, null);
    }

    public JSONObject getSmartMatchCommodities(String type, Integer priority, String openid, String season, Integer shopId) {
        try {
            log.info("开始智能搭配商品查询，用户：{}，类型：{}，优先级：{}，季节：{}", openid, type, priority, season);

            // 构建风格正则表达式
            String regexp = "^0" + priority;

            if (shopId != null && shopId > 0) {
                log.info("指定品牌店搭配查询，shopId={}，type={}，priority={}，season={}", shopId, type, priority, season);
                return getSmartMatchCommoditiesByShopId(shopId, type, regexp, season, false);
            }

            // 初始化商品列表
            List<Map<String, Object>> allCommodities = new ArrayList<>();

            // 优先级1：检查用户是否是城市代理
            boolean isCityAgent = isCityAgent(openid);
            if (isCityAgent) {
                log.info("用户是城市代理，按城市代理逻辑处理，openid: {}", openid);

                // 城市代理优先级1：从自己的商铺获取商品
                JSONObject cityAgentOwnResult = getSmartMatchCommoditiesFromCityAgentOwnShop(type, regexp, openid,
                        season);
                if (cityAgentOwnResult != null && cityAgentOwnResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ownShopCommodities = (List<Map<String, Object>>) cityAgentOwnResult
                            .get("result");
                    if (ownShopCommodities != null && !ownShopCommodities.isEmpty()) {
                        log.info("从城市代理自己商铺获取到{}件商品", ownShopCommodities.size());
                        allCommodities.addAll(ownShopCommodities);

                        // 检查是否已经有足够的指定类型商品
                        if (hasEnoughTargetTypeCommodities(ownShopCommodities, type)) {
                            log.info("城市代理自己商铺已有足够的{}类型商品，无需补充", type);
                            return cityAgentOwnResult;
                        }

                        // 检查是否能组成一套搭配
                        if (canFormCompleteOutfit(ownShopCommodities)) {
                            log.info("城市代理自己商铺商品可以组成完整搭配");
                            return cityAgentOwnResult;
                        }
                        log.info("城市代理自己商铺商品不能组成完整搭配，需要从下级补充");
                    }
                }

                // 城市代理优先级2：从下级代理商铺获取商品进行补充
                JSONObject subordinateResult = getSmartMatchCommoditiesFromSubordinateShops(type, regexp, openid,
                        season);
                if (subordinateResult != null && subordinateResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subordinateCommodities = (List<Map<String, Object>>) subordinateResult
                            .get("result");
                    if (subordinateCommodities != null && !subordinateCommodities.isEmpty()) {
                        log.info("从城市代理下级商铺获取到{}件商品", subordinateCommodities.size());
                        allCommodities.addAll(subordinateCommodities);

                        // 检查合并后是否已经有足够的指定类型商品
                        if (hasEnoughTargetTypeCommodities(allCommodities, type)) {
                            log.info("城市代理自己+下级商铺已有足够的{}类型商品，无需补充", type);
                            JSONObject result = new JSONObject();
                            result.set("errCode", 0);
                            result.set("result", allCommodities);
                            return result;
                        }

                        // 检查合并后是否能组成完整搭配
                        if (canFormCompleteOutfit(allCommodities)) {
                            log.info("城市代理自己+下级商铺商品可以组成完整搭配");
                            JSONObject result = new JSONObject();
                            result.set("errCode", 0);
                            result.set("result", allCommodities);
                            return result;
                        }
                        log.info("城市代理自己+下级商铺商品仍不能组成完整搭配，需要从默认商铺补充");
                    }
                }
            }

            // 优先级2：检查用户是否是代理且有自己的商铺（非城市代理）
            JSONObject userShopResult = null;
            if (!isCityAgent) { // 只有非城市代理才执行此逻辑
                userShopResult = getSmartMatchCommoditiesFromUserShop(type, regexp, openid, season);
                if (userShopResult != null && userShopResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> userShopCommodities = (List<Map<String, Object>>) userShopResult
                            .get("result");
                    if (userShopCommodities != null && !userShopCommodities.isEmpty()) {
                        log.info("从用户自己的商铺获取到{}件商品", userShopCommodities.size());
                        allCommodities.addAll(userShopCommodities);

                        // 检查是否已经有足够的指定类型商品
                        if (hasEnoughTargetTypeCommodities(userShopCommodities, type)) {
                            log.info("用户自己的商铺已有足够的{}类型商品，无需补充", type);
                            return userShopResult;
                        }

                        // 检查是否能组成一套搭配
                        if (canFormCompleteOutfit(userShopCommodities)) {
                            log.info("用户自己的商铺商品可以组成完整搭配");
                            return userShopResult;
                        }
                        log.info("用户自己的商铺商品不能组成完整搭配，需要补充");
                    }
                }
            }

            // 优先级3：检查用户是否有上级推广关系（非城市代理）
            JSONObject promoterShopResult = null;
            if (!isCityAgent) { // 只有非城市代理才执行此逻辑
                promoterShopResult = getSmartMatchCommoditiesFromPromoterShop(type, regexp, openid, season);
            }

            // 合并用户自己商铺的商品（非城市代理）
            if (!isCityAgent && userShopResult != null && userShopResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> userShopCommodities = (List<Map<String, Object>>) userShopResult
                        .get("result");
                if (userShopCommodities != null) {
                    allCommodities.addAll(userShopCommodities);
                }
            }

            // 合并推广人商铺的商品（非城市代理）
            if (!isCityAgent && promoterShopResult != null && promoterShopResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> promoterShopCommodities = (List<Map<String, Object>>) promoterShopResult
                        .get("result");
                if (promoterShopCommodities != null) {
                    log.info("从推广人商铺获取到{}件商品", promoterShopCommodities.size());
                    allCommodities.addAll(promoterShopCommodities);
                }
            }

            // 检查是否已经有足够的指定类型商品
            if (!allCommodities.isEmpty() && hasEnoughTargetTypeCommodities(allCommodities, type)) {
                log.info("已获取到足够的{}类型商品，无需补充", type);
                JSONObject result = new JSONObject();
                result.set("errCode", 0);
                result.set("result", allCommodities);
                return result;
            }

            // 检查合并后是否能组成完整搭配
            if (!allCommodities.isEmpty() && canFormCompleteOutfit(allCommodities)) {
                log.info("用户和推广人商铺商品合并后可以组成完整搭配");
                JSONObject result = new JSONObject();
                result.set("errCode", 0);
                result.set("result", allCommodities);
                return result;
            }

            // 优先级3：从"自是汇品"商铺获取商品进行补充
            log.info("从商铺\"自是汇品\"获取商品进行补充");
            JSONObject defaultShopResult = getSmartMatchCommoditiesFromDefaultShop(type, regexp, openid, season);

            // 合并"自是汇品"商铺商品
            if (defaultShopResult != null && defaultShopResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> defaultShopCommodities = (List<Map<String, Object>>) defaultShopResult
                        .get("result");
                if (defaultShopCommodities != null) {
                    allCommodities.addAll(defaultShopCommodities);
                    log.info("从商铺\"自是汇品\"获取到{}件商品，总计{}件商品", defaultShopCommodities.size(), allCommodities.size());
                }
            }

            // 返回最终结果
            JSONObject finalResult = new JSONObject();
            finalResult.set("errCode", 0);
            finalResult.set("result", allCommodities);
            return finalResult;

        } catch (Exception e) {
            log.error("获取智能搭配商品异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取智能搭配商品异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据风格代码获取智能搭配商品
     *
     * @param type      商品类型
     * @param styleCode 风格代码
     * @param openid    用户openid
     * @return 智能搭配商品列表
     */
    public JSONObject getSmartMatchCommoditiesByStyle(String type, String styleCode, String openid) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("type");
            fieldKeys.add("styleCode");
            values.add(type);
            values.add(styleCode);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6031_SMART_MATCH_COMMODITIES_BY_STYLE,
                    "F_501_6031_SMART_MATCH_COMMODITIES_BY_STYLE",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("根据风格代码获取智能搭配商品失败：{}", result.getStr("errMsg"));
                return result;
            }

            return result;

        } catch (Exception e) {
            log.error("根据风格代码获取智能搭配商品异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "根据风格代码获取智能搭配商品异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据规格获取智能搭配商品
     *
     * @param type   商品类型
     * @param spec   商品规格
     * @param shopId 商铺ID
     * @param season 季节
     * @return 智能搭配商品列表
     */
    public JSONObject getSmartMatchCommoditiesBySpec(String type, String spec, Integer shopId, String season) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());

            // 设置动态参数
            if (StrUtil.isNotBlank(type)) {
                param.set("type", type);
            }
            if (shopId != null && shopId != 0) {
                param.set("shopId", shopId);
            }

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6032_SMART_MATCH_COMMODITIES_BY_SPEC,
                    "F_501_6032_SMART_MATCH_COMMODITIES_BY_SPEC",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("根据规格获取智能搭配商品失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 对结果进行规格和季节筛选
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities = (List<Map<String, Object>>) result.get("result");
            if (commodities != null && !commodities.isEmpty()) {
                List<Map<String, Object>> filteredCommodities = filterCommoditiesBySpecAndSeason(commodities, spec,
                        season);
                result.set("result", filteredCommodities);
            }

            return result;

        } catch (Exception e) {
            log.error("根据规格获取智能搭配商品异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "根据规格获取智能搭配商品异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取智能搭配风格列表
     *
     * @param type     商品类型
     * @param priority 风格优先级
     * @return 风格列表
     */
    public JSONObject getSmartMatchStyleList(String type, Integer priority) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            // 构建风格正则表达式
            String regexp = "^0" + priority;

            fieldKeys.add("type");
            fieldKeys.add("regexp");
            values.add(type);
            values.add(regexp);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6033_SMART_MATCH_STYLE_LIST,
                    "F_501_6033_SMART_MATCH_STYLE_LIST",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取智能搭配风格列表失败：{}", result.getStr("errMsg"));
                return result;
            }

            return result;

        } catch (Exception e) {
            log.error("获取智能搭配风格列表异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取智能搭配风格列表异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 获取指定风格的智能搭配饰品
     *
     * @param type           商品类型
     * @param styleType      风格类型
     * @param styleTypeValue 风格值
     * @param openid         用户openid
     * @param season         季节
     * @return 智能搭配饰品列表
     */
    public JSONObject getSmartMatchOrnaments(String type, String styleType, String styleTypeValue, String openid,
            String season) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            // 构建风格正则表达式（根据原逻辑）
            String regexp = buildStyleRegexp(styleType, styleTypeValue);

            fieldKeys.add("type");
            fieldKeys.add("regexp");
            values.add(type);
            values.add(regexp);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6034_SMART_MATCH_ORNAMENTS,
                    "F_501_6034_SMART_MATCH_ORNAMENTS",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取指定风格的智能搭配饰品失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 对商品进行筛选
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities = (List<Map<String, Object>>) result.get("result");
            if (commodities != null && !commodities.isEmpty()) {
                List<Map<String, Object>> filteredCommodities = filterCommoditiesForSmartMatch(commodities, openid,
                        season);
                result.set("result", filteredCommodities);
            }

            return result;

        } catch (Exception e) {
            log.error("获取指定风格的智能搭配饰品异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "获取指定风格的智能搭配饰品异常：" + e.getMessage());
            return errorResult;
        }
    }

    // ===== 智能搭配辅助方法 =====

    /**
     * 为智能搭配筛选商品（季节、肤色、体型等）
     *
     * @param commodities 原始商品列表
     * @param openid      用户openid
     * @param season      季节
     * @return 筛选后的商品列表
     */
    private List<Map<String, Object>> filterCommoditiesForSmartMatch(List<Map<String, Object>> commodities,
            String openid, String season) {
        List<Map<String, Object>> filteredList = new ArrayList<>();

        try {
            // 这里可以添加具体的筛选逻辑
            // 1. 季节筛选
            // 2. 根据用户评测数据进行肤色、体型筛选
            // 3. 其他业务逻辑筛选

            for (Map<String, Object> commodity : commodities) {
                // 基础筛选：确保商品有效
                if (commodity != null) {
                    // 可以在这里添加季节匹配逻辑
                    // 可以在这里添加用户个性化筛选逻辑
                    filteredList.add(commodity);
                }
            }

        } catch (Exception e) {
            log.error("智能搭配商品筛选异常", e);
            // 如果筛选失败，返回原始列表
            return commodities;
        }

        return filteredList;
    }

    /**
     * 根据规格和季节筛选商品
     *
     * @param commodities 原始商品列表
     * @param spec        商品规格
     * @param season      季节
     * @return 筛选后的商品列表
     */
    private List<Map<String, Object>> filterCommoditiesBySpecAndSeason(List<Map<String, Object>> commodities,
            String spec, String season) {
        List<Map<String, Object>> filteredList = new ArrayList<>();

        try {
            for (Map<String, Object> commodity : commodities) {
                if (commodity != null) {
                    // 检查商品规格
                    String qjspec = (String) commodity.get("qjspec");
                    if (StrUtil.isNotBlank(qjspec) && StrUtil.isNotBlank(spec) && StrUtil.isNotBlank(season)) {
                        if (qjspec.contains(spec) && qjspec.contains(season)) {
                            filteredList.add(commodity);
                        }
                    } else if (StrUtil.isBlank(spec)) {
                        // 如果没有指定规格，只检查季节
                        if (StrUtil.isBlank(season) || (StrUtil.isNotBlank(qjspec) && qjspec.contains(season))) {
                            filteredList.add(commodity);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("根据规格和季节筛选商品异常", e);
            // 如果筛选失败，返回原始列表
            return commodities;
        }

        return filteredList;
    }

    /**
     * 构建风格正则表达式
     *
     * @param styleType      风格类型
     * @param styleTypeValue 风格值
     * @return 正则表达式
     */
    private String buildStyleRegexp(String styleType, String styleTypeValue) {
        // 这里需要根据具体的风格映射逻辑来构建正则表达式
        // 暂时返回一个基础的正则表达式
        return ".*" + styleTypeValue + ".*";
    }

    // ===== 优先级智能搭配辅助方法 =====
    /**
     * 根据用户openid计算风格优先级
     *
     * @param openid 用户openid
     * @return 风格优先级结果
     */
    public JSONObject getUserPriority(String openid) {
        try {
            log.info("开始计算用户风格优先级，用户：{}", openid);

            // 1. 获取用户评测数据
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("openid");
            values.add(openid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6040_GET_USER_PRIORITY,
                    "F_501_6040_GET_USER_PRIORITY",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("获取用户评测数据失败：{}", result.getStr("errMsg"));
                JSONObject errorResult = new JSONObject();
                errorResult.set("errCode", 1);
                errorResult.set("errMsg", "获取用户评测数据失败");
                return errorResult;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evaluationList = (List<Map<String, Object>>) result.get("result");

            if (evaluationList == null || evaluationList.isEmpty()) {
                log.warn("用户没有评测数据，openid: {}", openid);
                JSONObject errorResult = new JSONObject();
                errorResult.set("errCode", 1);
                errorResult.set("errMsg", "用户没有评测数据");
                return errorResult;
            }

            // 2. 解析评测数据
            Map<String, Object> evaluation = evaluationList.get(0);
            String typesStr = (String) evaluation.get("types");

            if (typesStr == null || typesStr.trim().isEmpty()) {
                log.warn("用户评测数据中types字段为空，openid: {}", openid);
                JSONObject errorResult = new JSONObject();
                errorResult.set("errCode", 1);
                errorResult.set("errMsg", "用户评测数据不完整");
                return errorResult;
            }

            // 3. 计算风格优先级
            int priority = calculatePriorityFromTypes(typesStr);

            // 4. 将priority限制在1-3范围内
            int finalPriority = Math.min(Math.max(priority, 1), 3);

            log.info("用户{}的风格优先级计算完成：原始priority={}，最终priority={}", openid, priority, finalPriority);

            JSONObject successResult = new JSONObject();
            successResult.set("errCode", 0);
            successResult.set("errMsg", "success");
            successResult.set("data",
                    new JSONObject().set("priority", finalPriority).set("originalPriority", priority));
            return successResult;

        } catch (Exception e) {
            log.error("计算用户风格优先级异常，openid: {}", openid, e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "计算风格优先级异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据types字符串计算风格优先级
     *
     * @param typesStr 评测数据中的types字段
     * @return 风格优先级
     */
    private int calculatePriorityFromTypes(String typesStr) {
        try {
            // 解析JSON字符串
            JSONArray typesArray = JSONUtil.parseArray(typesStr);

            // 统计大于0的风格个数
            int count = 0;
            for (Object obj : typesArray) {
                if (obj instanceof JSONObject) {
                    JSONObject typeObj = (JSONObject) obj;
                    String proportionStr = typeObj.getStr("typeProportion");
                    if (proportionStr != null && !proportionStr.trim().isEmpty()) {
                        // 移除百分号并转换为整数
                        String numStr = proportionStr.replace("%", "").trim();
                        try {
                            int proportion = Integer.parseInt(numStr);
                            if (proportion > 0) {
                                count++;
                            }
                        } catch (NumberFormatException e) {
                            log.warn("解析风格比例失败：{}", proportionStr);
                        }
                    }
                }
            }

            log.info("计算得到的风格个数：{}", count);
            return count;

        } catch (Exception e) {
            log.error("解析types数据异常：{}", typesStr, e);
            // 如果解析失败，返回默认值2（优雅风格）
            return 2;
        }
    }

    /**
     * 从用户自己的商铺获取智能搭配商品
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 用户openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromUserShop(String type, String regexp, String openid, String season) {
        try {
            // 1. 检查用户是否是代理
            JSONObject agentInfo = agentService.getCurrentUserAgentInfo(openid);
            if (agentInfo == null) {
                log.info("用户不是代理，openid: {}", openid);
                return null;
            }

            // 调试：打印完整的代理信息
            log.info("代理信息详情，openid: {}，agentInfo: {}", openid, agentInfo.toStringPretty());

            // 2. 从代理信息中获取商铺ID
            Object shopIdObj = agentInfo.get("shop_id");
            if (shopIdObj == null) {
                log.info("用户没有关联的商铺，openid: {}，shop_id为null", openid);
                return null;
            }

            Integer shopId = Convert.toInt(shopIdObj);
            if (shopId == null || shopId <= 0) {
                log.info("用户没有有效的商铺ID，openid: {}，shop_id: {}", openid, shopIdObj);
                return null;
            }

            log.info("用户是代理且有商铺，openid: {}，shopId: {}", openid, shopId);

            // 3. 从用户商铺获取商品
            return getSmartMatchCommoditiesByShopId(shopId, type, regexp, season);

        } catch (Exception e) {
            log.error("从用户商铺获取智能搭配商品异常，openid: {}", openid, e);
            return null;
        }
    }

    /**
     * 从推广人商铺获取智能搭配商品
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 用户openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromPromoterShop(String type, String regexp, String openid,
            String season) {
        try {
            // 1. 获取用户等级
            Integer userRole = getAgentRole(openid);
            log.info("用户{}的代理等级：{}", openid, userRole);

            // 2. 检查用户是否有推广关系
            JSONObject promoterInfo = agentService.getPromoterInfo(openid);
            if (promoterInfo == null) {
                log.info("用户没有推广关系，openid: {}", openid);
                return null;
            }

            // 3. 从推广关系中获取推广人openid和商铺ID
            String promoterOpenid = promoterInfo.getStr("agent_openid");
            if (StrUtil.isBlank(promoterOpenid)) {
                log.info("推广人openid为空，用户openid: {}", openid);
                return null;
            }

            Object shopIdObj = promoterInfo.get("shop_id");
            if (shopIdObj == null) {
                log.info("推广人没有关联的商铺，推广人openid: {}，shop_id为null", promoterOpenid);
                return null;
            }

            Integer shopId = Convert.toInt(shopIdObj);
            if (shopId == null || shopId <= 0) {
                log.info("推广人没有有效的商铺ID，推广人openid: {}，shop_id: {}", promoterOpenid, shopIdObj);
                return null;
            }

            log.info("找到推广人且有商铺，用户openid: {}，推广人openid: {}，shopId: {}", openid, promoterOpenid, shopId);

            // 4. 从推广人商铺获取商品
            JSONObject promoterShopResult = getSmartMatchCommoditiesByShopId(shopId, type, regexp, season);

            // 5. 如果是普通用户或个人代理，还需要获取推广人的上级城市代理商铺商品
            if (shouldGetUpperLevelShopCommodities(userRole)) {
                log.info("用户{}是普通用户或个人代理，尝试获取上级城市代理商铺商品", openid);
                JSONObject upperShopResult = getUpperLevelShopCommodities(promoterOpenid, type, regexp, season);

                // 合并商品结果
                if (upperShopResult != null && upperShopResult.getInt("errCode") == 0) {
                    promoterShopResult = mergeCommodityResults(promoterShopResult, upperShopResult);
                }
            }

            return promoterShopResult;

        } catch (Exception e) {
            log.error("从推广人商铺获取智能搭配商品异常，openid: {}", openid, e);
            return null;
        }
    }

    /**
     * 判断是否需要获取上级商铺商品
     * 普通用户和个人代理需要获取上级城市代理商铺商品
     *
     * @param userRole 用户代理等级
     * @return true表示需要获取上级商铺商品
     */
    private boolean shouldGetUpperLevelShopCommodities(Integer userRole) {
        // 普通用户（userRole为null或0）或个人代理需要获取上级商铺商品
        return userRole == null || userRole == 0 || userRole == AgentStatusCodes.ROLE_PERSONAL_AGENT;
    }

    /**
     * 获取推广人的上级城市代理商铺商品
     *
     * @param promoterOpenid 推广人openid
     * @param type           商品类型
     * @param regexp         风格正则表达式
     * @param season         季节
     * @return 商品列表
     */
    private JSONObject getUpperLevelShopCommodities(String promoterOpenid, String type, String regexp, String season) {
        try {
            // 1. 获取推广人的等级
            Integer promoterRole = getAgentRole(promoterOpenid);
            log.info("推广人{}的代理等级：{}", promoterOpenid, promoterRole);

            // 2. 如果推广人是平台代理，获取其上级城市代理的商铺
            if (promoterRole != null && promoterRole == AgentStatusCodes.ROLE_PLATFORM_AGENT) {
                // 获取平台代理的上级城市代理
                JSONObject upperPromoterInfo = agentService.getPromoterInfo(promoterOpenid);
                if (upperPromoterInfo == null) {
                    log.info("推广人{}没有上级推广关系", promoterOpenid);
                    return null;
                }

                String upperPromoterOpenid = upperPromoterInfo.getStr("agent_openid");
                if (StrUtil.isBlank(upperPromoterOpenid)) {
                    log.info("推广人{}的上级openid为空", promoterOpenid);
                    return null;
                }

                // 检查上级是否为城市代理
                Integer upperPromoterRole = getAgentRole(upperPromoterOpenid);
                if (upperPromoterRole == null || upperPromoterRole != AgentStatusCodes.ROLE_CITY_AGENT) {
                    log.info("推广人{}的上级{}不是城市代理，等级：{}", promoterOpenid, upperPromoterOpenid, upperPromoterRole);
                    return null;
                }

                // 获取城市代理的商铺ID
                Object upperShopIdObj = upperPromoterInfo.get("shop_id");
                if (upperShopIdObj == null) {
                    log.info("上级城市代理{}没有关联的商铺", upperPromoterOpenid);
                    return null;
                }

                Integer upperShopId = Convert.toInt(upperShopIdObj);
                if (upperShopId == null || upperShopId <= 0) {
                    log.info("上级城市代理{}没有有效的商铺ID：{}", upperPromoterOpenid, upperShopIdObj);
                    return null;
                }

                log.info("找到上级城市代理{}的商铺，shopId: {}", upperPromoterOpenid, upperShopId);

                // 从上级城市代理商铺获取商品
                return getSmartMatchCommoditiesByShopId(upperShopId, type, regexp, season);
            } else {
                log.info("推广人{}不是平台代理，无需获取上级商铺商品，等级：{}", promoterOpenid, promoterRole);
                return null;
            }

        } catch (Exception e) {
            log.error("获取推广人{}的上级城市代理商铺商品异常", promoterOpenid, e);
            return null;
        }
    }

    /**
     * 合并两个商品结果
     *
     * @param result1 第一个商品结果
     * @param result2 第二个商品结果
     * @return 合并后的商品结果
     */
    private JSONObject mergeCommodityResults(JSONObject result1, JSONObject result2) {
        try {
            // 如果第一个结果为空或失败，返回第二个结果
            if (result1 == null || result1.getInt("errCode") != 0) {
                return result2;
            }

            // 如果第二个结果为空或失败，返回第一个结果
            if (result2 == null || result2.getInt("errCode") != 0) {
                return result1;
            }

            // 合并商品列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities1 = (List<Map<String, Object>>) result1.get("result");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities2 = (List<Map<String, Object>>) result2.get("result");

            List<Map<String, Object>> mergedCommodities = new ArrayList<>();

            if (commodities1 != null) {
                mergedCommodities.addAll(commodities1);
                log.info("从推广人商铺获取到{}件商品", commodities1.size());
            }

            if (commodities2 != null) {
                mergedCommodities.addAll(commodities2);
                log.info("从上级城市代理商铺获取到{}件商品", commodities2.size());
            }

            log.info("合并后总共{}件商品", mergedCommodities.size());

            // 返回合并结果
            JSONObject mergedResult = new JSONObject();
            mergedResult.set("errCode", 0);
            mergedResult.set("errMsg", "success");
            mergedResult.set("result", mergedCommodities);

            return mergedResult;

        } catch (Exception e) {
            log.error("合并商品结果异常", e);
            return result1; // 异常时返回第一个结果
        }
    }

    /**
     * 从全平台获取智能搭配商品（原有逻辑）
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 用户openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromPlatform(String type, String regexp, String openid, String season) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("type");
            fieldKeys.add("regexp");
            values.add(type);
            values.add(regexp);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6030_SMART_MATCH_COMMODITIES,
                    "F_501_6030_SMART_MATCH_COMMODITIES",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("从全平台获取智能搭配商品失败：{}", result.getStr("errMsg"));
                return result;
            }

            // 对商品进行筛选（季节、肤色、体型等）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities = (List<Map<String, Object>>) result.get("result");
            if (commodities != null && !commodities.isEmpty()) {
                List<Map<String, Object>> filteredCommodities = filterCommoditiesForSmartMatch(commodities, openid,
                        season);
                result.set("result", filteredCommodities);
            }

            return result;

        } catch (Exception e) {
            log.error("从全平台获取智能搭配商品异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "从全平台获取智能搭配商品异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 根据商铺ID获取智能搭配商品
     *
     * @param shopId 商铺ID
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesByShopId(Integer shopId, String type, String regexp, String season) {
        return getSmartMatchCommoditiesByShopId(shopId, type, regexp, season, true);
    }

    private JSONObject getSmartMatchCommoditiesByShopId(Integer shopId, String type, String regexp, String season, boolean applyStyleFilter) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            // 设置商铺ID参数
            fieldKeys.add("shop_id");
            values.add(shopId);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_501_6035_SMART_MATCH_COMMODITIES_BY_SHOP,
                    "F_501_6035_SMART_MATCH_COMMODITIES_BY_SHOP",
                    "qj_shop_commodity",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                log.error("根据商铺ID获取智能搭配商品失败，shopId: {}，错误: {}", shopId, result.getStr("errMsg"));
                return result;
            }

            // 对商品进行筛选（类型、风格、季节等）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> commodities = (List<Map<String, Object>>) result.get("result");
            if (commodities != null && !commodities.isEmpty()) {
                log.info("商铺{}查询到{}件商品，筛选条件：类型={}，风格正则={}", shopId, commodities.size(), type, regexp);

                // 先按类型和风格筛选
                List<Map<String, Object>> filteredCommodities = applyStyleFilter
                        ? filterCommoditiesByTypeAndStyle(commodities, type, regexp)
                        : filterCommoditiesByTypeOnly(commodities, type);

                // 再按季节筛选 - 暂时禁用季节筛选进行测试
                // filteredCommodities = filterCommoditiesBySeasonOnly(filteredCommodities,
                // season);

                result.set("result", filteredCommodities);
                log.info("商铺{}筛选完成：原始{}件 → 最终{}件", shopId, commodities.size(), filteredCommodities.size());
            } else {
                log.info("商铺{}没有找到商品", shopId);
            }

            return result;

        } catch (Exception e) {
            log.error("根据商铺ID获取智能搭配商品异常，shopId: {}", shopId, e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "根据商铺ID获取智能搭配商品异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 检查是否已经有足够的指定类型商品
     *
     * @param commodities 商品列表
     * @param targetType  目标商品类型
     * @return 是否有足够的指定类型商品
     */
    private boolean hasEnoughTargetTypeCommodities(List<Map<String, Object>> commodities, String targetType) {
        if (commodities == null || commodities.isEmpty() || StrUtil.isBlank(targetType)) {
            return false;
        }

        // 统计指定类型的商品数量
        int targetTypeCount = 0;
        for (Map<String, Object> commodity : commodities) {
            String type = (String) commodity.get("type");
            if (targetType.equals(type)) {
                targetTypeCount++;
            }
        }

        // 如果已经有1件或以上指定类型的商品，认为足够了
        // 这个阈值可以根据业务需求调整
        boolean hasEnough = targetTypeCount >= 1;
        log.debug("指定类型{}的商品数量: {}，是否足够: {}", targetType, targetTypeCount, hasEnough);
        return hasEnough;
    }

    /**
     * 检查商品列表是否能组成完整搭配
     *
     * @param commodities 商品列表
     * @return 是否能组成完整搭配
     */
    private boolean canFormCompleteOutfit(List<Map<String, Object>> commodities) {
        if (commodities == null || commodities.isEmpty()) {
            return false;
        }

        // 简单的搭配检查逻辑：检查是否有不同类型的商品
        Set<String> types = new HashSet<>();
        for (Map<String, Object> commodity : commodities) {
            String type = (String) commodity.get("type");
            if (StrUtil.isNotBlank(type)) {
                types.add(type);
            }
        }

        // 如果有2种或以上不同类型的商品，认为可以组成搭配
        // 这里可以根据实际业务需求调整逻辑
        boolean canForm = types.size() >= 2;
        log.debug("商品类型数量: {}，是否能组成搭配: {}", types.size(), canForm);
        return canForm;
    }

    /**
     * 仅根据季节筛选商品
     *
     * @param commodities 原始商品列表
     * @param season      季节
     * @return 筛选后的商品列表
     */
    private List<Map<String, Object>> filterCommoditiesBySeasonOnly(List<Map<String, Object>> commodities,
            String season) {
        if (commodities == null || commodities.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> filteredList = new ArrayList<>();

        try {
            for (Map<String, Object> commodity : commodities) {
                if (commodity != null) {
                    // 如果没有指定季节，直接添加
                    if (StrUtil.isBlank(season)) {
                        filteredList.add(commodity);
                        continue;
                    }

                    // 检查商品的季节匹配
                    String qjspec = (String) commodity.get("qjspec");
                    if (StrUtil.isNotBlank(qjspec) && qjspec.contains(season)) {
                        filteredList.add(commodity);
                    } else if (StrUtil.isBlank(qjspec)) {
                        // 如果商品没有规格信息，也添加进去
                        filteredList.add(commodity);
                    }
                }
            }

        } catch (Exception e) {
            log.error("根据季节筛选商品异常", e);
            // 如果筛选失败，返回原始列表
            return commodities;
        }

        return filteredList;
    }

    /**
     * 根据类型和风格筛选商品
     *
     * @param commodities 原始商品列表
     * @param type        商品类型
     * @param regexp      风格正则表达式
     * @return 筛选后的商品列表
     */
    private List<Map<String, Object>> filterCommoditiesByTypeOnly(List<Map<String, Object>> commodities, String type) {
        if (commodities == null || commodities.isEmpty()) {
            return new ArrayList<>();
        }

        if (StrUtil.isBlank(type)) {
            return new ArrayList<>(commodities);
        }

        List<Map<String, Object>> filteredList = new ArrayList<>();
        for (Map<String, Object> commodity : commodities) {
            if (commodity == null) {
                continue;
            }

            Object commodityTypeObj = commodity.get("type");
            if (commodityTypeObj != null && type.equals(String.valueOf(commodityTypeObj))) {
                filteredList.add(commodity);
            }
        }

        log.info("指定品牌店按类别筛选完成：type={}，原始{}件，返回{}件", type, commodities.size(), filteredList.size());
        return filteredList;
    }

    private List<Map<String, Object>> filterCommoditiesByTypeAndStyle(List<Map<String, Object>> commodities,
            String type, String regexp) {
        if (commodities == null || commodities.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> filteredList = new ArrayList<>();

        try {
            log.info("开始筛选商品，总数: {}，类型: {}，风格正则: {}", commodities.size(), type, regexp);

            int typeMatchCount = 0;
            int styleMatchCount = 0;
            int bothMatchCount = 0;

            for (Map<String, Object> commodity : commodities) {
                if (commodity != null) {
                    boolean typeMatch = true;
                    boolean styleMatch = true;

                    // 检查商品类型
                    if (StrUtil.isNotBlank(type)) {
                        String commodityType = (String) commodity.get("type");
                        typeMatch = type.equals(commodityType);
                        if (typeMatch)
                            typeMatchCount++;
                    }

                    // 检查风格代码
                    if (StrUtil.isNotBlank(regexp)) {
                        String styleCode = (String) commodity.get("style_code");
                        if (StrUtil.isNotBlank(styleCode)) {
                            // 使用正则表达式匹配，支持 ^01, ^02, ^03 等模式
                            styleMatch = styleCode.matches(regexp + ".*");
                            if (styleMatch)
                                styleMatchCount++;
                        } else {
                            styleMatch = false;
                        }
                    }

                    // 只有类型和风格都匹配才添加
                    if (typeMatch && styleMatch) {
                        filteredList.add(commodity);
                        bothMatchCount++;
                    }
                }
            }

            log.info("筛选结果统计 - 类型匹配: {}, 风格匹配: {}, 同时匹配: {}", typeMatchCount, styleMatchCount, bothMatchCount);

        } catch (Exception e) {
            log.error("根据类型和风格筛选商品异常", e);
            // 如果筛选失败，返回原始列表
            return commodities;
        }

        return filteredList;
    }

    /**
     * 从"自是汇品"商铺获取智能搭配商品（兜底逻辑）
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 用户openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromDefaultShop(String type, String regexp, String openid,
            String season) {
        try {
            // "自是汇品"商铺ID - 需要根据实际数据库中的商铺ID进行调整
            Integer defaultShopId = getDefaultShopId();

            if (defaultShopId == null) {
                log.warn("未找到默认商铺\"自是汇品\"，回退到全平台获取");
                return getSmartMatchCommoditiesFromPlatform(type, regexp, openid, season);
            }

            log.info("从默认商铺\"自是汇品\"(ID: {})获取智能搭配商品", defaultShopId);

            // 使用商铺ID获取商品
            return getSmartMatchCommoditiesByShopId(defaultShopId, type, regexp, season);

        } catch (Exception e) {
            log.error("从默认商铺获取智能搭配商品异常", e);
            // 异常时回退到全平台获取
            log.info("异常回退：从全平台获取商品");
            return getSmartMatchCommoditiesFromPlatform(type, regexp, openid, season);
        }
    }

    /**
     * 获取默认商铺"自是汇品"的ID
     *
     * @return 商铺ID，如果未找到返回null
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
                    log.info("找到默认商铺\"自是汇品\"，ID: {}", shopId);
                    return shopId;
                }
            }

            log.warn("未找到名为\"自是汇品\"的商铺");
            return null;

        } catch (Exception e) {
            log.error("查询默认商铺异常", e);
            return null;
        }
    }

    // ===== 商品筛选辅助方法 =====

    /**
     * 在Java层应用筛选条件
     */
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> items, String type, String brand,
            String keywords, Integer shopId) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> filteredItems = new ArrayList<>();

        for (Map<String, Object> item : items) {
            boolean match = true;

            // 类型筛选
            if (StrUtil.isNotBlank(type)) {
                String itemType = (String) item.get("type");
                if (!type.equals(itemType)) {
                    match = false;
                }
            }

            // 品牌筛选
            if (match && StrUtil.isNotBlank(brand)) {
                String itemBrand = (String) item.get("brand");
                if (!brand.equals(itemBrand)) {
                    match = false;
                }
            }

            // 关键词筛选
            if (match && StrUtil.isNotBlank(keywords)) {
                String itemName = (String) item.get("name");
                String itemDesc = (String) item.get("description");
                boolean keywordMatch = false;

                if (StrUtil.isNotBlank(itemName) && itemName.contains(keywords)) {
                    keywordMatch = true;
                }
                if (!keywordMatch && StrUtil.isNotBlank(itemDesc) && itemDesc.contains(keywords)) {
                    keywordMatch = true;
                }

                if (!keywordMatch) {
                    match = false;
                }
            }

            // 商铺筛选
            if (match && shopId != null) {
                Object itemShopId = item.get("shop_id");
                if (itemShopId == null || !shopId.equals(Convert.toInt(itemShopId))) {
                    match = false;
                }
            }

            // 图片路径有效性筛选：过滤掉使用相对路径的商品
            if (match) {
                String firstPic = (String) item.get("first_pic");
                if (StrUtil.isBlank(firstPic) || !firstPic.startsWith("http")) {
                    match = false;
                    log.debug("过滤掉无效图片路径的商品，id: {}, first_pic: {}", item.get("id"), firstPic);
                }
            }

            if (match) {
                filteredItems.add(item);
            }
        }

        return filteredItems;
    }

    /**
     * 应用分页
     */
    private List<Map<String, Object>> applyPagination(List<Map<String, Object>> items, int offset, int limit) {
        if (items == null || items.isEmpty()) {
            log.debug("分页处理：输入列表为空");
            return new ArrayList<>();
        }

        int start = Math.max(0, offset);
        int end = Math.min(items.size(), start + limit);

        log.debug("分页处理：总数={}, offset={}, limit={}, start={}, end={}",
                items.size(), offset, limit, start, end);

        if (start >= items.size()) {
            log.warn("分页处理：起始位置{}超出总数{}，返回空列表", start, items.size());
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = items.subList(start, end);
        log.debug("分页处理：返回{}件商品", result.size());
        return result;
    }

    /**
     * 判断用户是否是城市代理
     *
     * @param openid 用户openid
     * @return 是否是城市代理
     */
    private boolean isCityAgent(String openid) {
        try {
            JSONObject agentInfo = agentService.getCurrentUserAgentInfo(openid);
            if (agentInfo == null) {
                return false;
            }

            Integer currentRole = agentInfo.getInt("current_role");
            return currentRole != null && currentRole == 5; // 5是城市代理
        } catch (Exception e) {
            log.error("判断用户是否是城市代理异常，openid: {}", openid, e);
            return false;
        }
    }

    /**
     * 获取城市代理自己商铺的智能搭配商品
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 用户openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromCityAgentOwnShop(String type, String regexp, String openid,
            String season) {
        try {
            // 1. 获取城市代理信息
            JSONObject agentInfo = agentService.getCurrentUserAgentInfo(openid);
            if (agentInfo == null) {
                log.info("获取不到代理信息，openid: {}", openid);
                return null;
            }

            // 2. 从代理信息中获取商铺ID
            Object shopIdObj = agentInfo.get("shop_id");
            if (shopIdObj == null) {
                log.info("城市代理没有关联的商铺，openid: {}，shop_id为null", openid);
                return null;
            }

            Integer shopId = Convert.toInt(shopIdObj);
            if (shopId == null || shopId <= 0) {
                log.info("城市代理没有有效的商铺ID，openid: {}，shop_id: {}", openid, shopIdObj);
                return null;
            }

            log.info("城市代理有自己的商铺，openid: {}，shopId: {}", openid, shopId);

            // 3. 从城市代理自己的商铺获取商品
            return getSmartMatchCommoditiesByShopId(shopId, type, regexp, season);

        } catch (Exception e) {
            log.error("从城市代理自己商铺获取智能搭配商品异常，openid: {}", openid, e);
            return null;
        }
    }

    /**
     * 获取城市代理下级商铺的智能搭配商品
     *
     * @param type   商品类型
     * @param regexp 风格正则表达式
     * @param openid 城市代理openid
     * @param season 季节
     * @return 商品列表
     */
    private JSONObject getSmartMatchCommoditiesFromSubordinateShops(String type, String regexp, String openid,
            String season) {
        try {
            // 1. 获取城市代理下级的所有商铺ID
            List<Integer> subordinateShopIds = getSubordinateShopIds(openid);
            if (subordinateShopIds.isEmpty()) {
                log.info("城市代理没有下级商铺，openid: {}", openid);
                return null;
            }

            log.info("城市代理下级商铺ID列表：{}，openid: {}", subordinateShopIds, openid);

            // 2. 从所有下级商铺获取商品并合并
            List<Map<String, Object>> allCommodities = new ArrayList<>();
            for (Integer shopId : subordinateShopIds) {
                JSONObject shopResult = getSmartMatchCommoditiesByShopId(shopId, type, regexp, season);
                if (shopResult != null && shopResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> shopCommodities = (List<Map<String, Object>>) shopResult.get("result");
                    if (shopCommodities != null) {
                        allCommodities.addAll(shopCommodities);
                        log.info("从下级商铺{}获取到{}件商品", shopId, shopCommodities.size());
                    }
                }
            }

            if (allCommodities.isEmpty()) {
                log.info("城市代理下级商铺没有找到合适的商品，openid: {}", openid);
                return null;
            }

            log.info("城市代理从下级商铺总共获取到{}件商品，openid: {}", allCommodities.size(), openid);

            JSONObject result = new JSONObject();
            result.set("errCode", 0);
            result.set("result", allCommodities);
            return result;

        } catch (Exception e) {
            log.error("从城市代理下级商铺获取智能搭配商品异常，openid: {}", openid, e);
            return null;
        }
    }

    /**
     * 获取城市代理下级的商铺ID列表
     *
     * @param cityAgentOpenid 城市代理openid
     * @return 下级商铺ID列表
     */
    private List<Integer> getSubordinateShopIds(String cityAgentOpenid) {
        List<Integer> shopIds = new ArrayList<>();

        try {
            // 查询所有以当前城市代理为上级的用户
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            JSONArray values = new JSONArray();

            fieldKeys.add("agent_openid");
            values.add(cityAgentOpenid);

            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("__mysql_values__", values);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4204_AGENT_USER_GET_BY_AGENT, // 根据代理获取推广关系
                    "F_303_4204_AGENT_USER_GET_BY_AGENT",
                    "qj_marketing_v2_agent_users",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> subordinates = (List<Map<String, Object>>) result.get("result");
                if (subordinates != null) {
                    for (Map<String, Object> subordinate : subordinates) {
                        String subordinateOpenid = (String) subordinate.get("user_openid");
                        if (StrUtil.isNotBlank(subordinateOpenid)) {
                            // 检查下级用户是否也是代理，如果是则获取其商铺ID
                            JSONObject subordinateAgentInfo = agentService.getCurrentUserAgentInfo(subordinateOpenid);
                            if (subordinateAgentInfo != null) {
                                Object shopIdObj = subordinateAgentInfo.get("shop_id");
                                if (shopIdObj != null) {
                                    Integer shopId = Convert.toInt(shopIdObj);
                                    if (shopId != null && shopId > 0 && !shopIds.contains(shopId)) {
                                        shopIds.add(shopId);
                                        log.info("添加下级代理的商铺ID: {}，下级openid: {}", shopId, subordinateOpenid);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取城市代理下级商铺ID异常，cityAgentOpenid: {}", cityAgentOpenid, e);
        }

        return shopIds;
    }

    /**
     * 获取城市代理下级商铺中有指定类别商品的商铺ID
     */
    private Integer getSubordinateShopIdForType(String cityAgentOpenid, String type) {
        try {
            log.info("开始查找城市代理{}下级商铺中有类别'{}'商品的商铺", cityAgentOpenid, type);

            // 1. 获取城市代理下级的所有商铺ID
            List<Integer> subordinateShopIds = getSubordinateShopIds(cityAgentOpenid);
            if (subordinateShopIds.isEmpty()) {
                log.info("城市代理{}没有下级商铺", cityAgentOpenid);
                return null;
            }

            log.info("城市代理{}的下级商铺ID列表：{}", cityAgentOpenid, subordinateShopIds);

            // 2. 遍历下级商铺，查找有该类别商品的商铺
            for (Integer subordinateShopId : subordinateShopIds) {
                // 检查下级商铺是否为品牌商铺
                if (!isBrandShop(subordinateShopId)) {
                    log.info("下级商铺{}不是品牌商铺，跳过", subordinateShopId);
                    continue;
                }

                // 检查该商铺是否有指定类别的商品
                if (isShopOwnType(subordinateShopId, type)) {
                    log.info("找到下级商铺{}有类别'{}'的商品", subordinateShopId, type);
                    return subordinateShopId;
                }
            }

            log.info("城市代理{}的下级商铺中没有找到有类别'{}'商品的商铺", cityAgentOpenid, type);
            return null;

        } catch (Exception e) {
            log.error("获取下级商铺类别异常，cityAgentOpenid: {}, type: {}", cityAgentOpenid, type, e);
            return null;
        }
    }

}
