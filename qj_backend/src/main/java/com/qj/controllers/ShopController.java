package com.qj.controllers;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.services.ShopCommodityService;
import com.qj.services.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商城控制器 - 完全兼容原qjapi接口路径
 */
@RestController
@CrossOrigin
@RequestMapping("/qj/shop")
public class ShopController {
    private static final Logger log = LoggerFactory.getLogger(ShopController.class);

    @Autowired
    private ShopCommodityService shopCommodityService;

    @Autowired
    private ShopService shopService;

    // ===== 商品相关接口 =====

    /**
     * 商品分页列表
     * 原接口：POST /minip/qj/shop/commodity/commodityPage
     */
    @PostMapping("/commodity/commodityPage")
    public JSONObject commodityPage(@RequestBody String requestBody) {
        log.info("商品分页列表请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer pageNumber = params.getInt("pageNumber");
            Integer pageSize = params.getInt("pageSize");
            String type = params.getStr("type");
            String brand = params.getStr("brand");
            String keywords = params.getStr("keywords");
            Integer shopId = params.getInt("shopId");
            if (shopId == null) {
                shopId = params.getInt("shop_id"); // 兼容旧的命名方式
            }

            return shopCommodityService.getCommodityPage(pageNumber, pageSize, type, brand, keywords, shopId);
        } catch (Exception e) {
            log.error("商品分页列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "商品分页列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 推荐商品列表
     * 原接口：POST /minip/qj/shop/commodity/commodityStarPage
     */
    @PostMapping("/commodity/commodityStarPage")
    public JSONObject commodityStarPage(@RequestBody String requestBody) {
        log.info("推荐商品列表请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer pageNumber = params.getInt("pageNumber");
            Integer pageSize = params.getInt("pageSize");
            Integer typeByTab = params.getInt("typeByTab");
            String keywords = params.getStr("keywords");

            return shopCommodityService.getCommodityStarPage(pageNumber, pageSize, typeByTab, keywords);
        } catch (Exception e) {
            log.error("推荐商品列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "推荐商品列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据ID获取商品详情
     * 原接口：POST /minip/qj/shop/commodity/commodityById
     */
    @PostMapping("/commodity/commodityById")
    public JSONObject commodityById(@RequestBody String requestBody) {
        log.info("获取商品详情请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer commodityId = params.getInt("id");
            if (commodityId == null) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商品ID不能为空");
                return result;
            }

            return shopCommodityService.getCommodityById(commodityId);
        } catch (Exception e) {
            log.error("获取商品详情异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取商品详情异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 获取商品分类列表
     * 原接口：GET /minip/qj/shop/commodity/commodityTypes
     */
    @GetMapping("/commodity/commodityTypes")
    public JSONObject commodityTypes() {
        log.info("获取商品分类列表请求");
        try {
            return shopCommodityService.getCommodityTypes();
        } catch (Exception e) {
            log.error("获取商品分类列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取商品分类列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据商铺ID获取商品分类
     * 原接口：POST /minip/qj/shop/commodity/getCommodityTypesByshopid
     */
    @PostMapping("/commodity/getCommodityTypesByshopid")
    public JSONObject getCommodityTypesByshopid(@RequestBody String requestBody) {
        log.info("根据商铺获取商品分类请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer shopId = params.getInt("shopid");
            if (shopId == null) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商铺ID不能为空");
                return result;
            }

            return shopCommodityService.getCommodityTypesByShopId(shopId);
        } catch (Exception e) {
            log.error("根据商铺获取商品分类异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据商铺获取商品分类异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 商品筛选列表
     * 原接口：POST /minip/qj/shop/commodity/commodityScreenPage
     */
    @PostMapping("/commodity/commodityScreenPage")
    public JSONObject commodityScreenPage(@RequestBody String requestBody) {
        log.info("商品筛选列表请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer pageNumber = params.getInt("pageNumber");
            Integer pageSize = params.getInt("pageSize");
            String type = params.getStr("type");
            String brand = params.getStr("brand");
            String keywords = params.getStr("keywords");
            Integer shopId = params.getInt("shop_id");

            return shopCommodityService.getCommodityPage(pageNumber, pageSize, type, brand, keywords, shopId);
        } catch (Exception e) {
            log.error("商品筛选列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "商品筛选列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 搜索商品和文章
     * 原接口：POST /minip/qj/shop/commodity/searchCommoditiesArticles
     */
    @PostMapping("/commodity/searchCommoditiesArticles")
    public JSONObject searchCommoditiesArticles(@RequestBody String requestBody) {
        log.info("搜索商品和文章请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            String keywords = params.getStr("keywords");
            Integer pageNumber = params.getInt("pageNumber");
            Integer pageSize = params.getInt("pageSize");

            if (keywords == null || keywords.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "搜索关键词不能为空");
                return result;
            }

            return shopCommodityService.searchCommoditiesArticles(keywords, pageNumber, pageSize);
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
     * 原接口：GET /minip/qj/shop/commodity/getUniversalCommodity
     */
    @GetMapping("/commodity/getUniversalCommodity")
    public JSONObject getUniversalCommodity() {
        log.info("获取万能款商品请求");
        try {
            return shopCommodityService.getUniversalCommodity();
        } catch (Exception e) {
            log.error("获取万能款商品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取万能款商品异常：" + e.getMessage());
            return result;
        }
    }

    // ===== 商铺相关接口 =====

    /**
     * 获取商铺列表
     * 原接口：GET /minip/qj/shop/commodity/getShopList
     */
    @GetMapping("/commodity/getShopList")
    public JSONObject getShopList(@RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer isBrand,
            @RequestParam(required = false) String openid) {
        log.info("获取商铺列表请求：pageNumber={}, pageSize={}, isBrand={}, openid={}", pageNumber, pageSize, isBrand, openid);
        try {
            // 设置默认值
            if (pageNumber == null)
                pageNumber = 1;
            if (pageSize == null)
                pageSize = 10;
            if (isBrand == null)
                isBrand = 2; // 默认普通商家

            return shopService.getShopList(pageNumber, pageSize, isBrand, openid);
        } catch (Exception e) {
            log.error("获取商铺列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取商铺列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据openid获取商铺信息
     * 原接口：GET /minip/qj/shop/commodity/getShopIdByOpenId
     */
    @GetMapping("/commodity/getShopIdByOpenId")
    public JSONObject getShopIdByOpenId(@RequestParam String openId) {
        log.info("根据openid获取商铺信息请求：{}", openId);
        try {
            return shopService.getShopIdByOpenId(openId);
        } catch (Exception e) {
            log.error("根据openid获取商铺信息异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据openid获取商铺信息异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据ID获取商铺详情
     * 原接口：POST /minip/qj/shop/commodity/getShopById
     */
    @PostMapping("/commodity/getShopById")
    public JSONObject getShopById(@RequestBody String requestBody) {
        log.info("获取商铺详情请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            Integer shopId = params.getInt("id");
            if (shopId == null) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商铺ID不能为空");
                return result;
            }

            return shopService.getShopById(shopId);
        } catch (Exception e) {
            log.error("获取商铺详情异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取商铺详情异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 获取品牌商铺列表
     * 原接口：GET /minip/qj/shop/commodity/getBrandShopList
     */
    @GetMapping("/commodity/getBrandShopList")
    public JSONObject getBrandShopList(@RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String openid) {
        log.info("获取品牌商铺列表请求：pageNumber={}, pageSize={}, openid={}", pageNumber, pageSize, openid);
        try {
            // 设置默认值
            if (pageNumber == null)
                pageNumber = 1;
            if (pageSize == null)
                pageSize = 10;

            return shopService.getShopList(pageNumber, pageSize, 1, openid); // isBrand=1表示品牌商家
        } catch (Exception e) {
            log.error("获取品牌商铺列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取品牌商铺列表异常：" + e.getMessage());
            return result;
        }
    }

    // ===== 品牌相关接口 =====
    // 注意：原qjapi中没有getBrandList接口，这里删除

    /**
     * 根据品牌获取商品分类
     * 原接口：POST /minip/qj/shop/commodity/getCommodityTypesByBrand
     */
    @PostMapping("/commodity/getCommodityTypesByBrand")
    public JSONObject getCommodityTypesByBrand(@RequestBody String requestBody) {
        log.info("根据品牌获取商品分类请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            String brand = params.getStr("brand");
            if (brand == null || brand.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "品牌名称不能为空");
                return result;
            }

            return shopCommodityService.getCommodityTypesByBrand(brand);
        } catch (Exception e) {
            log.error("根据品牌获取商品分类异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据品牌获取商品分类异常：" + e.getMessage());
            return result;
        }
    }

    // ===== 智能搭配相关接口 =====

    /**
     * 根据用户openid计算风格优先级
     * GET /qj/shop/matching/getUserPriority
     */
    @GetMapping("/matching/getUserPriority")
    public JSONObject getUserPriority(@RequestParam String openid) {
        log.info("获取用户风格优先级请求：openid={}", openid);
        try {
            if (openid == null || openid.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "用户openid不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getUserPriority(openid);
            return result;
        } catch (Exception e) {
            log.error("获取用户风格优先级异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取用户风格优先级异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据类型、优先级、季节获取智能搭配商品
     * 兼容原接口：GET /minip/qj/matching/getCommodities
     */
    @GetMapping("/matching/getCommodities")
    public JSONObject getSmartMatchCommodities(@RequestParam String type,
            @RequestParam Integer priority,
            @RequestParam String openid,
            @RequestParam(required = false) Integer shopId,
            @RequestParam String season) {
        log.info("获取智能搭配商品请求：type={}, priority={}, openid={}, season={}", type, priority, openid, season);
        try {
            if (type == null || type.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商品类型不能为空");
                return result;
            }

            if (priority == null) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格优先级不能为空");
                return result;
            }

            if (openid == null || openid.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "用户openid不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getSmartMatchCommodities(type, priority, openid, season, shopId);

            // 兼容原接口返回格式
            if (result.getInt("errCode") == 0) {
                JSONObject compatibleResult = new JSONObject();
                compatibleResult.set("errCode", 0);
                compatibleResult.set("errMsg", "success");
                compatibleResult.set("data", result.get("result"));
                return compatibleResult;
            }

            return result;
        } catch (Exception e) {
            log.error("获取智能搭配商品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取智能搭配商品异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据风格代码获取智能搭配商品
     * 兼容原接口：GET /minip/qj/matching/getCommoditiesByStyleCode
     */
    @GetMapping("/matching/getCommoditiesByStyleCode")
    public JSONObject getSmartMatchCommoditiesByStyle(@RequestParam String type,
            @RequestParam String styleCode,
            @RequestParam String openid) {
        log.info("根据风格代码获取智能搭配商品请求：type={}, styleCode={}, openid={}", type, styleCode, openid);
        try {
            if (type == null || type.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商品类型不能为空");
                return result;
            }

            if (styleCode == null || styleCode.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格代码不能为空");
                return result;
            }

            if (openid == null || openid.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "用户openid不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getSmartMatchCommoditiesByStyle(type, styleCode, openid);

            // 兼容原接口返回格式
            if (result.getInt("errCode") == 0) {
                JSONObject compatibleResult = new JSONObject();
                compatibleResult.set("errCode", 0);
                compatibleResult.set("errMsg", "success");
                compatibleResult.set("data", result.get("result"));
                return compatibleResult;
            }

            return result;
        } catch (Exception e) {
            log.error("根据风格代码获取智能搭配商品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据风格代码获取智能搭配商品异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 根据规格获取智能搭配商品
     * 兼容原接口：POST /minip/qj/matching/getCommoditiesByQjspec
     */
    @PostMapping("/matching/getCommoditiesByQjspec")
    public JSONObject getSmartMatchCommoditiesBySpec(@RequestBody String requestBody) {
        log.info("根据规格获取智能搭配商品请求：{}", requestBody);
        try {
            JSONObject params = JSONUtil.parseObj(requestBody);
            String type = params.getStr("type");
            String spec = params.getStr("spec");
            Integer shopId = params.getInt("shopId");
            String season = params.getStr("season");

            if (spec == null || spec.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格参数不能为空");
                return result;
            }

            if (season == null || season.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "季节参数不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getSmartMatchCommoditiesBySpec(type, spec, shopId, season);

            // 兼容原接口返回格式
            if (result.getInt("errCode") == 0) {
                JSONObject compatibleResult = new JSONObject();
                compatibleResult.set("errCode", 0);
                compatibleResult.set("errMsg", "success");
                compatibleResult.set("data", result.get("result"));
                return compatibleResult;
            }

            return result;
        } catch (Exception e) {
            log.error("根据规格获取智能搭配商品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "根据规格获取智能搭配商品异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 获取智能搭配风格列表
     * 兼容原接口：GET /minip/qj/matching/getWardrobeStyleList
     */
    @GetMapping("/matching/getWardrobeStyleList")
    public JSONObject getSmartMatchStyleList(@RequestParam String type,
            @RequestParam Integer priority) {
        log.info("获取智能搭配风格列表请求：type={}, priority={}", type, priority);
        try {
            if (type == null || type.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商品类型不能为空");
                return result;
            }

            if (priority == null) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格优先级不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getSmartMatchStyleList(type, priority);

            // 兼容原接口返回格式
            if (result.getInt("errCode") == 0) {
                JSONObject compatibleResult = new JSONObject();
                compatibleResult.set("errCode", 0);
                compatibleResult.set("errMsg", "success");
                compatibleResult.set("data", result.get("result"));
                return compatibleResult;
            }

            return result;
        } catch (Exception e) {
            log.error("获取智能搭配风格列表异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取智能搭配风格列表异常：" + e.getMessage());
            return result;
        }
    }

    /**
     * 获取指定风格的智能搭配饰品
     * 兼容原接口：GET /minip/qj/matching/getAppointStyleShopOrnaments
     */
    @GetMapping("/matching/getAppointStyleShopOrnaments")
    public JSONObject getSmartMatchOrnaments(@RequestParam String type,
            @RequestParam String styleType,
            @RequestParam String styleTypeValue,
            @RequestParam String openid,
            @RequestParam String season) {
        log.info("获取指定风格的智能搭配饰品请求：type={}, styleType={}, styleTypeValue={}, openid={}, season={}",
                type, styleType, styleTypeValue, openid, season);
        try {
            if (type == null || type.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "商品类型不能为空");
                return result;
            }

            if (styleType == null || styleType.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格类型不能为空");
                return result;
            }

            if (styleTypeValue == null || styleTypeValue.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "风格值不能为空");
                return result;
            }

            if (openid == null || openid.trim().isEmpty()) {
                JSONObject result = new JSONObject();
                result.set("errCode", 1);
                result.set("errMsg", "用户openid不能为空");
                return result;
            }

            JSONObject result = shopCommodityService.getSmartMatchOrnaments(type, styleType, styleTypeValue, openid,
                    season);

            // 兼容原接口返回格式
            if (result.getInt("errCode") == 0) {
                JSONObject compatibleResult = new JSONObject();
                compatibleResult.set("errCode", 0);
                compatibleResult.set("errMsg", "success");
                compatibleResult.set("data", result.get("result"));
                return compatibleResult;
            }

            return result;
        } catch (Exception e) {
            log.error("获取指定风格的智能搭配饰品异常", e);
            JSONObject result = new JSONObject();
            result.set("errCode", 1);
            result.set("errMsg", "获取指定风格的智能搭配饰品异常：" + e.getMessage());
            return result;
        }
    }
}
