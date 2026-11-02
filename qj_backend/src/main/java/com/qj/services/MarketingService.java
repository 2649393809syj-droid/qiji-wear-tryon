package com.qj.services;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.config.MyJsonConfig;
import com.qj.consts.FunctionCodes;
import com.qj.entities.AgentResponse;
import com.qj.services.common.CRUDService;
import com.qj.utils.AccessTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 推广营销服务类
 * 
 * @author system
 */
@Service
public class MarketingService {

    private static final Logger log = LoggerFactory.getLogger(MarketingService.class);

    private static final String BASE_POSTER_URL = "https://716a-qj-formal-1g06n5f37194ef95-1257602491.tcb.qcloud.la/shop/shopImg/posterBgImg/%E5%A5%87%E7%BA%AA%E6%B5%B7%E6%8A%A5%E8%83%8C%E6%99%AF.png";

    @Autowired
    private CloudStorageService cloudStorageService;

    // 注入配置参数
    @Value("${wx.qrcode.page:pages/client/index/index}")
    private String qrcodePage;

    @Value("${wx.qrcode.page.local:}")
    private String qrcodePageLocal;

    @Value("${wx.qrcode.use-local:false}")
    private boolean useLocalPage;

    // 二维码文件存储路径配置
    @Value("${wx.qrcode.storage.path:qrcode_images}")
    private String qrcodeStoragePath;

    // 服务器地址配置，用于生成完整的图片URL
    @Value("${server.base-url}")
    private String serverBaseUrl;

    // 动态推广码开关：true=每次生成唯一码(可追踪/可过期)；false=旧的按openid固定缓存码
    @Value("${wx.promo.dynamic-code.enabled:true}")
    private boolean dynamicCodeEnabled;

    // 动态码有效期天数：默认 7 天；设为 0 或负数=永久
    @Value("${wx.promo.code.expire-days:7}")
    private int promoCodeExpireDays;

    // 永久有效期哨兵（规避框架对 null 参数绑定的处理差异；扫码时 > now 即视为不过期）
    private static final String PERMANENT_EXPIRE_AT = "2099-12-31 23:59:59";

    /**
     * 获取推广海报（支持本地缓存）
     * 
     * @param openid 用户openid
     * @return AgentResponse
     */
    public AgentResponse getShopPoster(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        // 动态码模式：每次生成一张唯一的码（带 token，可追踪/可过期），不走 openid 固定缓存
        if (dynamicCodeEnabled) {
            return generateDynamicPoster(openid);
        }

        try {
            log.debug("开始获取推广海报，openid={}", openid);

            // 1. 首先尝试从本地缓存获取
            String cachedPosterUrl = getCachedPosterUrl(openid);
            if (StrUtil.isNotBlank(cachedPosterUrl)) {
                log.info("从缓存获取推广海报成功：openid={}, posterUrl={}", openid, cachedPosterUrl);
                return AgentResponse.success("推广海报获取成功（缓存）", cachedPosterUrl);
            }

            // 2. 缓存中没有，需要生成新的海报
            log.info("缓存中未找到海报，开始生成新海报：openid={}", openid);

            // 3. 获取用户信息
            JSONObject memberParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray memberFieldKeys = new JSONArray();
            memberFieldKeys.add("_openid");
            memberFieldKeys.add("is_del");
            memberParam.set("__mysql_fieldkeys__", memberFieldKeys);
            memberParam.set("_openid", openid);
            memberParam.set("is_del", 0);

            JSONObject memberResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    memberParam,
                    new HashSet<>());

            if (memberResult.getInt("errCode") != 0) {
                return AgentResponse.fail("获取用户信息失败：" + memberResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberList = (List<Map<String, Object>>) memberResult.get("result");

            if (memberList == null || memberList.isEmpty()) {
                return AgentResponse.fail("用户信息不存在");
            }

            Integer memberId = Convert.toInt(memberList.get(0).get("id"));
            if (memberId == null) {
                return AgentResponse.fail("获取用户ID失败");
            }

            // 4. 生成小程序码（带重试机制）
            HttpResponse qrcodeResponse = generateQrCodeWithRetry(memberId, openid);
            if (qrcodeResponse == null) {
                return AgentResponse.fail("生成小程序码失败，请稍后重试");
            }

            // 5. 合成海报并保存到本地缓存
            String posterUrl = createPosterWithCache(qrcodeResponse, openid);
            if (StrUtil.isBlank(posterUrl)) {
                return AgentResponse.fail("生成海报失败");
            }

            log.info("推广海报生成并缓存成功：openid={}, posterUrl={}", openid, posterUrl);
            return AgentResponse.success("推广海报生成成功", posterUrl);

        } catch (Exception e) {
            log.error("生成推广海报异常", e);
            return AgentResponse.fail("生成推广海报异常：" + e.getMessage());
        }
    }

    /**
     * 动态推广码：每次生成一张唯一的码（带 token，落库可追踪扫码/绑定数、可设过期），不走 openid 固定缓存。
     */
    private AgentResponse generateDynamicPoster(String openid) {
        try {
            // 1. 查 memberId
            Integer memberId = getMemberIdByOpenid(openid);
            if (memberId == null) {
                return AgentResponse.fail("用户信息不存在或获取ID失败");
            }
            // 2. 生成唯一 token 并落库
            String token = createPromoCode(memberId, openid);
            if (StrUtil.isBlank(token)) {
                return AgentResponse.fail("生成推广码失败，请稍后重试");
            }
            // 3. 生成小程序码（scene 带 token）
            HttpResponse qrcodeResponse = generateQrCodeWithRetry(memberId, openid, token);
            if (qrcodeResponse == null) {
                return AgentResponse.fail("生成小程序码失败，请稍后重试");
            }
            // 4. 合成海报并按 token 缓存（每张码独立文件，互不覆盖）
            String posterUrl = createPosterWithCacheKey(qrcodeResponse, token);
            if (StrUtil.isBlank(posterUrl)) {
                return AgentResponse.fail("生成海报失败");
            }
            log.info("动态推广码生成成功：openid={}, token={}, posterUrl={}", openid, token, posterUrl);
            return AgentResponse.success("推广海报生成成功", posterUrl);
        } catch (Exception e) {
            log.error("生成动态推广码异常，openid={}", openid, e);
            return AgentResponse.fail("生成动态推广码异常：" + e.getMessage());
        }
    }

    /**
     * 根据 openid 查会员 id
     */
    private Integer getMemberIdByOpenid(String openid) {
        JSONObject memberParam = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray memberFieldKeys = new JSONArray();
        memberFieldKeys.add("_openid");
        memberFieldKeys.add("is_del");
        memberParam.set("__mysql_fieldkeys__", memberFieldKeys);
        memberParam.set("_openid", openid);
        memberParam.set("is_del", 0);

        JSONObject memberResult = CRUDService.exeRetrieve(
                FunctionCodes.F_101_2001_MEMBER_LIST,
                "F_101_2001_MEMBER_LIST",
                "qj_member",
                memberParam,
                new HashSet<>());

        if (memberResult.getInt("errCode") != 0) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memberList = (List<Map<String, Object>>) memberResult.get("result");
        if (memberList == null || memberList.isEmpty()) {
            return null;
        }
        return Convert.toInt(memberList.get(0).get("id"));
    }

    /**
     * 生成唯一 token 并写入 qj_marketing_v2_promo_codes，返回 token（失败返回 null）。
     * token 冲突（uk_token）时重试。
     */
    private String createPromoCode(Integer memberId, String agentOpenid) {
        java.util.Date now = new java.util.Date();
        // expire-days>0 时 now+天数；否则永久（写哨兵日期，规避 null 参数绑定问题）
        java.util.Date expiredAt = (promoCodeExpireDays > 0)
                ? new java.util.Date(now.getTime() + promoCodeExpireDays * 86400000L)
                : DateUtil.parse(PERMANENT_EXPIRE_AT);

        for (int i = 0; i < 3; i++) {
            String token = Long.toString(System.currentTimeMillis(), 36) + RandomUtil.randomString(4);
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("token");
            fieldKeys.add("agent_openid");
            fieldKeys.add("agent_member_id");
            fieldKeys.add("created_at");
            fieldKeys.add("expired_at");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("token", token);
            param.set("agent_openid", agentOpenid);
            param.set("agent_member_id", memberId);
            param.set("created_at", now);
            param.set("expired_at", expiredAt);

            JSONObject res = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_304_4301_PROMO_CODE_CREATE,
                    "F_304_4301_PROMO_CODE_CREATE",
                    "qj_marketing_v2_promo_codes",
                    param,
                    new HashSet<>());

            if (res.getInt("errCode") == 0) {
                return token;
            }
            log.warn("写动态推广码失败(可能 token 冲突)，重试 {}/3，err={}", i + 1, res.getStr("errMsg"));
        }
        return null;
    }

    /**
     * 按 token 查询动态推广码行（找不到返回 null）
     */
    private Map<String, Object> getPromoCodeByToken(String token) {
        JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray keys = new JSONArray();
        keys.add("token");
        param.set("__mysql_fieldkeys__", keys);
        param.set("token", token);

        JSONObject res = CRUDService.exeRetrieve(
                FunctionCodes.F_304_4302_PROMO_CODE_GET_BY_TOKEN,
                "F_304_4302_PROMO_CODE_GET_BY_TOKEN",
                "qj_marketing_v2_promo_codes",
                param,
                new HashSet<>());

        if (res.getInt("errCode") != 0 || res.get("result") == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) res.get("result");
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 动态推广码计数 +1（scan_count 或 bind_count，由 funcCode 决定）。best-effort，失败不影响主流程。
     */
    private void incPromoCodeCount(String token, int funcCode, String funcName) {
        try {
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray keys = new JSONArray();
            keys.add("token");
            param.set("__mysql_fieldkeys__", keys);
            param.set("token", token);
            CRUDService.exeModifyOrDelete(funcCode, funcName, "qj_marketing_v2_promo_codes", param, new HashSet<>());
        } catch (Exception e) {
            log.warn("更新推广码计数失败：token={}, func={}", token, funcName, e);
        }
    }

    /**
     * 扫描推广海报
     * 
     * @param memberId       海报中的会员id
     * @param promoterOpenid 推广者openid
     * @param scannerOpenid  扫码者openid
     * @return AgentResponse
     */
    public AgentResponse scanShopPoster(Integer memberId, String promoterOpenid, String scannerOpenid) {
        if (memberId == null || StrUtil.isBlank(promoterOpenid) || StrUtil.isBlank(scannerOpenid)) {
            return AgentResponse.fail("参数错误");
        }

        try {
            log.debug("开始处理扫码推广，memberId={}, promoterOpenid={}, scannerOpenid={}",
                    memberId, promoterOpenid, scannerOpenid);

            // 1. 验证推广者信息
            JSONObject promoterParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray promoterFieldKeys = new JSONArray();
            promoterFieldKeys.add("id");
            promoterParam.set("__mysql_fieldkeys__", promoterFieldKeys);
            promoterParam.set("id", memberId);

            JSONObject promoterResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST_BY_ID,
                    "F_101_2001_MEMBER_LIST_BY_ID",
                    "qj_member",
                    promoterParam,
                    new HashSet<>());

            if (promoterResult.getInt("errCode") != 0) {
                return AgentResponse.fail("验证推广者信息失败：" + promoterResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> promoterList = (List<Map<String, Object>>) promoterResult.get("result");

            if (promoterList == null || promoterList.isEmpty()) {
                return AgentResponse.fail("推广者信息不存在");
            }

            String memberOpenid = (String) promoterList.get(0).get("_openid");
            if (!promoterOpenid.equals(memberOpenid)) {
                return AgentResponse.fail("推广者信息不匹配");
            }

            // 2. 检查扫码者是否已经被推广
            JSONObject scannerParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray scannerFieldKeys = new JSONArray();
            scannerFieldKeys.add("_openid");
            scannerFieldKeys.add("from_openid");
            scannerFieldKeys.add("is_del");
            scannerParam.set("__mysql_fieldkeys__", scannerFieldKeys);
            scannerParam.set("_openid", scannerOpenid);
            scannerParam.set("is_del", 0); // 添加is_del参数，查询未删除的用户

            JSONObject scannerResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    scannerParam,
                    new HashSet<>());

            if (scannerResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> scannerList = (List<Map<String, Object>>) scannerResult.get("result");

                if (scannerList != null && !scannerList.isEmpty()) {
                    String fromOpenid = (String) scannerList.get(0).get("from_openid");
                    if (StrUtil.isNotBlank(fromOpenid)) {
                        return AgentResponse.success("该用户已经有推广关系");
                    }
                }
            }

            // 3. 建立推广关系
            JSONObject updateParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray updateFieldKeys = new JSONArray();
            updateFieldKeys.add("_openid");
            updateFieldKeys.add("from_openid");
            updateParam.set("__mysql_fieldkeys__", updateFieldKeys);
            updateParam.set("_openid", scannerOpenid);
            updateParam.set("from_openid", promoterOpenid);

            JSONObject updateResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_101_2002_MEMBER_UPDATE,
                    "F_101_2002_MEMBER_UPDATE",
                    "qj_member",
                    updateParam,
                    new HashSet<>());

            if (updateResult.getInt("errCode") != 0) {
                return AgentResponse.fail("建立推广关系失败：" + updateResult.getStr("errMsg"));
            }

            log.info("扫码推广成功：promoterOpenid={}, scannerOpenid={}", promoterOpenid, scannerOpenid);
            return AgentResponse.success("推广关系建立成功");

        } catch (Exception e) {
            log.error("扫码推广异常", e);
            return AgentResponse.fail("扫码推广异常：" + e.getMessage());
        }
    }

    /**
     * 处理扫码进入首页后的推广关系
     * 
     * @param scene         场景值，格式：MID=memberId&OID=promoterOpenid 或 MID=memberId
     * @param scannerOpenid 扫码者openid
     * @return AgentResponse
     */
    public AgentResponse handleQrcodeScan(String scene, String scannerOpenid) {
        if (StrUtil.isBlank(scene) || StrUtil.isBlank(scannerOpenid)) {
            return AgentResponse.fail("参数错误：scene、scannerOpenid不能为空");
        }

        try {
            log.info("开始处理扫码推广关系，scene={}, scannerOpenid={}", scene, scannerOpenid);

            // 1. 解析场景值，支持多种格式：
            // 新格式：MID=memberId&OID=shortOpenid
            // 旧格式：MID=memberId（兼容性）
            Integer memberId = null;
            String shortOpenid = null;
            String promoToken = null;

            if (scene.startsWith("MID=")) {
                try {
                    String[] parts = scene.split("&");

                    // 解析MID
                    String memberIdStr = parts[0].substring(4); // 去掉"MID="前缀
                    memberId = Integer.parseInt(memberIdStr);

                    // 解析 OID / T（如果存在）
                    for (String part : parts) {
                        if (part.startsWith("OID=")) {
                            shortOpenid = part.substring(4); // 去掉"OID="前缀
                        } else if (part.startsWith("T=")) {
                            promoToken = part.substring(2); // 动态码 token
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析scene参数失败：{}", scene);
                }
            }

            log.info("解析场景参数：memberId={}, shortOpenid={}, promoToken={}", memberId, shortOpenid, promoToken);

            if (memberId == null) {
                return AgentResponse.fail("场景值格式错误：缺少有效的MID");
            }

            // 推广者openid（先由动态码 token 解析，解析不到再走 MID 兼容旧码）
            String promoterOpenid = null;
            boolean tokenBound = false; // 本次是否走动态码 token（用于后续 bind_count 计数）

            // 2a. 动态码：优先用 token 解析推广者（权威来源），并做过期/失效校验 + 扫码计数
            if (StrUtil.isNotBlank(promoToken)) {
                Map<String, Object> codeRow = getPromoCodeByToken(promoToken);
                if (codeRow != null) {
                    Integer status = Convert.toInt(codeRow.get("status"), 1);
                    java.util.Date expiredAt = Convert.toDate(codeRow.get("expired_at"));
                    boolean expired = (expiredAt != null && expiredAt.before(new java.util.Date()));
                    if (status == null || status != 1 || expired) {
                        log.info("动态推广码已过期/失效：token={}, status={}, expiredAt={}", promoToken, status, expiredAt);
                        return AgentResponse.success("该推广码已过期");
                    }
                    promoterOpenid = (String) codeRow.get("agent_openid");
                    tokenBound = true;
                    incPromoCodeCount(promoToken, FunctionCodes.F_304_4303_PROMO_CODE_INC_SCAN, "F_304_4303_PROMO_CODE_INC_SCAN");
                    log.info("动态码 token 解析推广者成功：token={}, agent_openid={}", promoToken, promoterOpenid);
                } else {
                    log.warn("动态码 token 查不到，回退 MID 解析：token={}", promoToken);
                }
            }

            // 2b. 非动态码或 token 解析失败：通过 MID 查询推广者（兼容旧码）
            if (StrUtil.isBlank(promoterOpenid)) {
                JSONObject promoterParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray promoterFieldKeys = new JSONArray();
                promoterFieldKeys.add("id");
                promoterParam.set("__mysql_fieldkeys__", promoterFieldKeys);
                promoterParam.set("id", memberId);

                JSONObject promoterResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_101_2001_MEMBER_LIST_BY_ID,
                        "F_101_2001_MEMBER_LIST_BY_ID",
                        "qj_member",
                        promoterParam,
                        new HashSet<>());

                log.info("【调试】查询推广者结果：promoterResult={}", promoterResult);

                // 检查查询结果
                if (promoterResult.getInt("errCode") != 0 ||
                        promoterResult.get("result") == null ||
                        ((List<?>) promoterResult.get("result")).isEmpty()) {

                    log.warn("通过ID={}查询推广者失败，开始尝试备用查询方案", memberId);

                    // 尝试通用的备用查询方案
                    if (shortOpenid != null) {
                        log.info("尝试通过shortOpenid={}匹配查询推广者", shortOpenid);
                        promoterOpenid = findPromoterByShortOpenid(shortOpenid, memberId);
                    } else {
                        log.info("尝试通过memberId={}反向查找推广者openid", memberId);
                        promoterOpenid = findPromoterByMemberId(memberId);
                    }

                    if (promoterOpenid == null) {
                        return AgentResponse.fail("推广者信息不存在，无法建立推广关系");
                    }
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> promoterList = (List<Map<String, Object>>) promoterResult.get("result");
                    promoterOpenid = (String) promoterList.get(0).get("_openid");
                    log.info("通过memberId={}查询到的推广者openid：{}", memberId, promoterOpenid);
                }
            }

            if (StrUtil.isBlank(promoterOpenid)) {
                return AgentResponse.fail("获取推广者openid失败");
            }

            log.info("最终确定的推广关系：推广者(agent_openid)={}, 被推广者(user_openid)={}", promoterOpenid, scannerOpenid);

            // 3. 检查是否为自己扫自己的码
            log.info("【调试】开始检查是否为自己扫自己的码：promoterOpenid={}, scannerOpenid={}", promoterOpenid, scannerOpenid);
            if (promoterOpenid.equals(scannerOpenid)) {
                log.info("【调试】检测到自己扫自己的码，拒绝建立推广关系");
                return AgentResponse.success("不能推广自己");
            }
            log.info("【调试】自己扫码检查通过，继续后续验证");

            // 4. 检查是否存在循环推广关系（推广者是否已经是扫码者的下级）
            JSONObject circularCheckParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray circularCheckFieldKeys = new JSONArray();
            circularCheckFieldKeys.add("user_openid");
            circularCheckParam.set("__mysql_fieldkeys__", circularCheckFieldKeys);
            circularCheckParam.set("user_openid", promoterOpenid);

            JSONObject circularCheckResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4202_AGENT_USER_CHECK_EXISTS,
                    "F_303_4202_AGENT_USER_CHECK_EXISTS",
                    "qj_marketing_v2_agent_users",
                    circularCheckParam,
                    new HashSet<>());

            if (circularCheckResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> circularList = (List<Map<String, Object>>) circularCheckResult.get("result");
                if (circularList != null && !circularList.isEmpty()) {
                    Integer circularCount = Convert.toInt(circularList.get(0).get("count"));
                    if (circularCount != null && circularCount > 0) {
                        // 进一步检查推广者的上级是否是扫码者
                        JSONObject promoterRelationParam = new JSONObject(MyJsonConfig.getJsonConfig());
                        JSONArray promoterRelationFieldKeys = new JSONArray();
                        promoterRelationFieldKeys.add("user_openid");
                        promoterRelationParam.set("__mysql_fieldkeys__", promoterRelationFieldKeys);
                        promoterRelationParam.set("user_openid", promoterOpenid);

                        JSONObject promoterRelationResult = CRUDService.exeRetrieve(
                                FunctionCodes.F_303_4203_AGENT_USER_GET_BY_USER,
                                "F_303_4203_AGENT_USER_GET_BY_USER",
                                "qj_marketing_v2_agent_users",
                                promoterRelationParam,
                                new HashSet<>());

                        if (promoterRelationResult.getInt("errCode") == 0) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> promoterRelationList = (List<Map<String, Object>>) promoterRelationResult.get("result");
                            if (promoterRelationList != null && !promoterRelationList.isEmpty()) {
                                String promoterAgent = (String) promoterRelationList.get(0).get("agent_openid");
                                if (scannerOpenid.equals(promoterAgent)) {
                                    log.info("检测到循环推广关系：推广者{}已经是扫码者{}的下级", promoterOpenid, scannerOpenid);
                                    return AgentResponse.success("不能建立循环推广关系，该用户已经是您的下级");
                                }
                            }
                        }
                    }
                }
            }

            // 5. 检查该用户是否已经存在推广关系（严格检查，防止重复推广）
            JSONObject checkParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray checkFieldKeys = new JSONArray();
            checkFieldKeys.add("user_openid");
            checkParam.set("__mysql_fieldkeys__", checkFieldKeys);
            checkParam.set("user_openid", scannerOpenid);

            JSONObject checkResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_303_4202_AGENT_USER_CHECK_EXISTS,
                    "F_303_4202_AGENT_USER_CHECK_EXISTS",
                    "qj_marketing_v2_agent_users",
                    checkParam,
                    new HashSet<>());

            log.info("检查用户推广关系结果：{}", checkResult);

            if (checkResult.getInt("errCode") == 0) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> existsList = (List<Map<String, Object>>) checkResult.get("result");
                if (existsList != null && !existsList.isEmpty()) {
                    Integer count = Convert.toInt(existsList.get(0).get("count"));
                    log.info("用户{}已有推广关系数量：{}", scannerOpenid, count);
                    if (count != null && count > 0) {
                        log.info("用户{}尝试重复建立推广关系，当前推广者：{}，已有推广关系数量：{}",
                                scannerOpenid, promoterOpenid, count);
                        return AgentResponse.success("该用户已经有推广关系，不能重复建立");
                    }
                }
            } else {
                log.info("检查推广关系时发生错误：{}", checkResult.getStr("errMsg"));
            }

            // 6. 验证推广关系等级规则
            try {
                AgentPromotionRelationshipService relationshipService = new AgentPromotionRelationshipService();
                boolean isValidHierarchy = relationshipService.validatePromotionHierarchy(promoterOpenid, scannerOpenid);
                if (!isValidHierarchy) {
                    log.info("推广关系不符合等级规则：promoter={}, scanner={}", promoterOpenid, scannerOpenid);
                    return AgentResponse.success("推广关系不符合等级规则，无法建立推广关系");
                }
                log.info("推广关系等级验证通过：promoter={}, scanner={}", promoterOpenid, scannerOpenid);
            } catch (Exception e) {
                log.error("验证推广关系等级规则异常：promoter={}, scanner={}", promoterOpenid, scannerOpenid, e);
                return AgentResponse.fail("验证推广关系等级规则异常：" + e.getMessage());
            }

            // 7. 创建推广关系记录
            JSONObject createParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray createFieldKeys = new JSONArray();
            createFieldKeys.add("agent_openid");
            createFieldKeys.add("user_openid");
            createFieldKeys.add("join_date");
            createParam.set("__mysql_fieldkeys__", createFieldKeys);
            createParam.set("agent_openid", promoterOpenid);
            createParam.set("user_openid", scannerOpenid);
            createParam.set("join_date", new java.util.Date());

            JSONObject createResult = CRUDService.exeModifyOrDelete(
                    FunctionCodes.F_303_4201_AGENT_USER_CREATE,
                    "F_303_4201_AGENT_USER_CREATE",
                    "qj_marketing_v2_agent_users",
                    createParam,
                    new HashSet<>());

            if (createResult.getInt("errCode") != 0) {
                return AgentResponse.fail ("创建推广关系失败：" + createResult.getStr("errMsg"));
            }

            log.info("扫码推广关系创建成功：推广者={}, 被推广者={}", promoterOpenid, scannerOpenid);

            // 动态码：成功建立推广关系后 bind_count + 1（追踪每张码带来多少人）
            if (tokenBound && StrUtil.isNotBlank(promoToken)) {
                incPromoCodeCount(promoToken, FunctionCodes.F_304_4304_PROMO_CODE_INC_BIND, "F_304_4304_PROMO_CODE_INC_BIND");
            }

            // 推广关系建立成功后，查询并返回扫码者的用户信息
            try {
                JSONObject memberParam = new JSONObject(MyJsonConfig.getJsonConfig());
                JSONArray memberFieldKeys = new JSONArray();
                memberFieldKeys.add("_openid");
                memberFieldKeys.add("is_del");
                memberParam.set("__mysql_fieldkeys__", memberFieldKeys);
                memberParam.set("_openid", scannerOpenid);
                memberParam.set("is_del", 0);

                JSONObject memberResult = CRUDService.exeRetrieve(
                        FunctionCodes.F_101_2001_MEMBER_LIST,
                        "F_101_2001_MEMBER_LIST",
                        "qj_member",
                        memberParam,
                        new HashSet<>());

                log.info("【扫码后查用户信息】{}", memberResult);

                if (memberResult.getInt("errCode") == 0) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> memberList = (List<Map<String, Object>>) memberResult.get("result");
                    if (memberList != null && !memberList.isEmpty()) {
                        JSONObject responseData = new JSONObject();
                        responseData.set("message", "推广关系建立成功");
                        responseData.set("memberInfo", memberList.get(0));
                        // 回传推广者openid，供前端直接落地（绕开"扫码后并发读关系"的竞态）
                        responseData.set("agentOpenid", promoterOpenid);
                        return AgentResponse.success("推广关系建立成功", responseData);
                    }
                }
            } catch (Exception e) {
                log.error("【扫码后查用户信息异常】", e);
            }

            // 兜底分支也回传 agentOpenid，确保前端拿得到推广者
            JSONObject fallbackData = new JSONObject();
            fallbackData.set("message", "推广关系建立成功");
            fallbackData.set("agentOpenid", promoterOpenid);
            return AgentResponse.success("推广关系建立成功", fallbackData);

        } catch (Exception e) {
            log.error("处理扫码推广关系异常", e);
            return AgentResponse.fail("处理扫码推广关系异常：" + e.getMessage());
        }
    }

    /**
     * 通过shortOpenid匹配查询推广者
     * 
     * @param shortOpenid 用户openid的后8位
     * @param memberId    用户ID（用于验证匹配结果）
     * @return 推广者openid，如果未找到返回null
     */
    private String findPromoterByShortOpenid(String shortOpenid, Integer memberId) {
        try {
            log.info("开始通过shortOpenid={}和memberId={}查找推广者", shortOpenid, memberId);

            // 直接根据已知信息进行精确匹配
            // 我们知道推广海报生成时是通过openid成功获取到用户信息的
            // 所以现在直接用shortOpenid匹配的方式来反向构造完整的openid

            // 查询所有活跃用户的openid，寻找后缀匹配且ID匹配的用户
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            // 对于F_101_2001_MEMBER_LIST_ALL，不需要设置任何参数，因为SQL中已经包含了固定的where条件

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST_ALL,
                    "F_101_2001_MEMBER_LIST_ALL",
                    "qj_member",
                    param,
                    new HashSet<>());

            log.info("查询所有用户结果：errCode={}, resultSize={}",
                    result.getInt("errCode"),
                    result.get("result") != null ? ((List<?>) result.get("result")).size() : 0);

            if (result.getInt("errCode") == 0 && result.get("result") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> memberList = (List<Map<String, Object>>) result.get("result");

                log.info("开始遍历{}个用户记录查找匹配", memberList.size());

                for (Map<String, Object> member : memberList) {
                    String openid = (String) member.get("_openid");
                    Integer id = Convert.toInt(member.get("id"));

                    if (openid != null && openid.endsWith(shortOpenid) && memberId.equals(id)) {
                        log.info("通过shortOpenid匹配成功：openid={}, id={}", openid, id);
                        return openid;
                    }
                }

                log.warn("遍历完成，未找到匹配的推广者：shortOpenid={}, memberId={}", shortOpenid, memberId);
            } else {
                log.error("查询用户列表失败：errCode={}, errMsg={}",
                        result.getInt("errCode"), result.getStr("errMsg"));
            }

            return null;

        } catch (Exception e) {
            log.error("通过shortOpenid查询推广者异常", e);
            return null;
        }
    }

    /**
     * 通过memberId反向查找推广者openid（适用于兼容旧版scene格式）
     * 
     * @param memberId 用户ID
     * @return 推广者openid，如果未找到返回null
     */
    private String findPromoterByMemberId(Integer memberId) {
        try {
            log.info("开始通过memberId={}查找推广者openid", memberId);

            // 直接通过memberId查询用户信息
            JSONObject promoterParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray promoterFieldKeys = new JSONArray();
            promoterFieldKeys.add("id");
            promoterParam.set("__mysql_fieldkeys__", promoterFieldKeys);
            promoterParam.set("id", memberId);

            JSONObject promoterResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST_BY_ID,
                    "F_101_2001_MEMBER_LIST_BY_ID",
                    "qj_member",
                    promoterParam,
                    new HashSet<>());

            log.info("通过memberId查询推广者结果：errCode={}", promoterResult.getInt("errCode"));

            if (promoterResult.getInt("errCode") == 0 && promoterResult.get("result") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> promoterList = (List<Map<String, Object>>) promoterResult.get("result");

                if (promoterList != null && !promoterList.isEmpty()) {
                    String openid = (String) promoterList.get(0).get("_openid");
                    log.info("通过memberId={}查询到推广者openid：{}", memberId, openid);
                    return openid;
                }
            }

            log.warn("通过memberId={}未找到推广者信息", memberId);
            return null;

        } catch (Exception e) {
            log.error("通过memberId反向查找推广者异常", e);
            return null;
        }
    }

    /**
     * 根据openid获取用户信息
     * 
     * @param openid 用户openid
     * @param isDel  是否删除标识
     * @return AgentResponse
     */
    public AgentResponse getMemberInfoByOpenid(String openid, Integer isDel) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.debug("查询用户信息，openid={}, isDel={}", openid, isDel);

            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("_openid");
            fieldKeys.add("is_del");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("_openid", openid);
            param.set("is_del", isDel != null ? isDel : 0);

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("查询用户信息失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberList = (List<Map<String, Object>>) result.get("result");

            if (memberList == null || memberList.isEmpty()) {
                return AgentResponse.fail("用户信息不存在");
            }

            return AgentResponse.success("获取用户信息成功", memberList.get(0));

        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return AgentResponse.fail("获取用户信息异常：" + e.getMessage());
        }
    }

    /**
     * 获取推广权限
     * 
     * @param openid 用户openid
     * @return AgentResponse
     */
    public AgentResponse getPromotionAuthorityByOpenid(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            // 检查用户是否有推广权限（这里可以根据具体业务逻辑调整）
            JSONObject param = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray fieldKeys = new JSONArray();
            fieldKeys.add("_openid");
            fieldKeys.add("grade");
            fieldKeys.add("is_del");
            param.set("__mysql_fieldkeys__", fieldKeys);
            param.set("_openid", openid);
            param.set("is_del", 0); // 添加is_del参数，查询未删除的用户

            JSONObject result = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    param,
                    new HashSet<>());

            if (result.getInt("errCode") != 0) {
                return AgentResponse.fail("查询用户信息失败：" + result.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberList = (List<Map<String, Object>>) result.get("result");

            if (memberList == null || memberList.isEmpty()) {
                return AgentResponse.fail("用户信息不存在");
            }

            Integer grade = Convert.toInt(memberList.get(0).get("grade"));

            // 根据等级判断推广权限（具体逻辑可调整）
            JSONObject authResult = new JSONObject();
            if (grade != null && grade >= 1) {
                authResult.set("auth", 1);
                authResult.set("message", "有推广权限");
            } else {
                authResult.set("auth", 0);
                authResult.set("message", "无推广权限");
            }

            return AgentResponse.success("获取推广权限成功", authResult);

        } catch (Exception e) {
            log.error("获取推广权限异常", e);
            return AgentResponse.fail("获取推广权限异常：" + e.getMessage());
        }
    }

    /**
     * 设置二维码跳转页面配置
     * 
     * @param useLocal  是否使用本地页面
     * @param page      小程序页面路径
     * @param pageLocal 本地页面路径
     * @return AgentResponse
     */
    public AgentResponse setQrcodePageConfig(Boolean useLocal, String page, String pageLocal) {
        try {
            if (useLocal != null) {
                this.useLocalPage = useLocal;
            }
            if (StrUtil.isNotBlank(page)) {
                this.qrcodePage = page;
            }
            if (StrUtil.isNotBlank(pageLocal)) {
                this.qrcodePageLocal = pageLocal;
            }

            JSONObject config = new JSONObject();
            config.set("useLocal", this.useLocalPage);
            config.set("page", this.qrcodePage);
            config.set("pageLocal", this.qrcodePageLocal);

            log.info("二维码跳转页面配置已更新：{}", config.toString());
            return AgentResponse.success("配置更新成功", config);

        } catch (Exception e) {
            log.error("设置二维码跳转页面配置异常", e);
            return AgentResponse.fail("配置更新失败：" + e.getMessage());
        }
    }

    /**
     * 获取二维码跳转页面配置
     * 
     * @return AgentResponse
     */
    public AgentResponse getQrcodePageConfig() {
        try {
            JSONObject config = new JSONObject();
            config.set("useLocal", this.useLocalPage);
            config.set("page", this.qrcodePage);
            config.set("pageLocal", this.qrcodePageLocal);

            return AgentResponse.success("获取配置成功", config);

        } catch (Exception e) {
            log.error("获取二维码跳转页面配置异常", e);
            return AgentResponse.fail("获取配置失败：" + e.getMessage());
        }
    }

    /**
     * 生成本地文件跳转的普通二维码海报
     * 
     * @param openid 用户openid
     * @return AgentResponse
     */
    public AgentResponse getLocalPoster(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid不能为空");
        }

        try {
            log.debug("开始生成本地跳转二维码海报，openid={}", openid);

            // 1. 获取用户信息
            JSONObject memberParam = new JSONObject(MyJsonConfig.getJsonConfig());
            JSONArray memberFieldKeys = new JSONArray();
            memberFieldKeys.add("_openid");
            memberFieldKeys.add("is_del");
            memberParam.set("__mysql_fieldkeys__", memberFieldKeys);
            memberParam.set("_openid", openid);
            memberParam.set("is_del", 0);

            JSONObject memberResult = CRUDService.exeRetrieve(
                    FunctionCodes.F_101_2001_MEMBER_LIST,
                    "F_101_2001_MEMBER_LIST",
                    "qj_member",
                    memberParam,
                    new HashSet<>());

            if (memberResult.getInt("errCode") != 0) {
                return AgentResponse.fail("获取用户信息失败：" + memberResult.getStr("errMsg"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> memberList = (List<Map<String, Object>>) memberResult.get("result");

            if (memberList == null || memberList.isEmpty()) {
                return AgentResponse.fail("用户信息不存在");
            }

            Integer memberId = Convert.toInt(memberList.get(0).get("id"));
            if (memberId == null) {
                return AgentResponse.fail("获取用户ID失败");
            }

            // 2. 生成本地跳转URL
            String localUrl;
            if (StrUtil.isNotBlank(qrcodePageLocal)) {
                // 使用配置的本地页面路径，添加推广参数
                String separator = qrcodePageLocal.contains("?") ? "&" : "?";
                localUrl = qrcodePageLocal + separator + "scene=MID=" + memberId + "&promoter=" + openid;
            } else {
                // 默认本地路径
                localUrl = "file:///D:/qjnew/qjapiv2/qj_front/index.html?scene=MID=" + memberId + "&promoter=" + openid;
            }

            log.info("生成本地跳转URL：{}", localUrl);

            // 3. 生成普通二维码海报
            String posterUrl = createLocalPoster(localUrl, openid);
            if (StrUtil.isBlank(posterUrl)) {
                return AgentResponse.fail("生成本地海报失败");
            }

            log.info("本地跳转海报生成成功：openid={}, posterUrl={}", openid, posterUrl);
            return AgentResponse.success("本地跳转海报生成成功", posterUrl);

        } catch (Exception e) {
            log.error("生成本地跳转海报异常", e);
            return AgentResponse.fail("生成本地跳转海报异常：" + e.getMessage());
        }
    }

    /**
     * 创建本地跳转海报（使用普通二维码）
     */
    private String createLocalPoster(String localUrl, String openid) {
        try {
            // 读取背景图片
            BufferedImage background = ImageIO.read(new URL(BASE_POSTER_URL));

            // 生成普通二维码（简化版本，不依赖zxing）
            BufferedImage qrCode = createSimpleQRCode(localUrl);

            // 创建输出图片
            BufferedImage outputImage = new BufferedImage(
                    background.getWidth(),
                    background.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            // 绘制图片
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(background, 0, 0, null);

            // 绘制二维码（位置与微信小程序码相同）
            g2d.drawImage(qrCode, 341, 1464, null);

            // 在二维码下方添加文本提示
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 24));
            String tipText = "扫码跳转本地页面";
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(tipText);
            int textX = 341 + (560 - textWidth) / 2; // 居中显示
            g2d.drawString(tipText, textX, 1464 + 560 + 30);

            g2d.dispose();

            // 保存临时文件
            File tempFile = new File(System.getProperty("java.io.tmpdir"), openid + "_local_poster.png");
            ImageIO.write(outputImage, "png", tempFile);

            // 使用 CloudStorageService 上传海报
            String posterUrl = cloudStorageService.uploadPoster(tempFile, openid + "_local");

            // 删除临时文件
            tempFile.delete();

            return posterUrl;

        } catch (Exception e) {
            log.error("创建本地海报失败", e);
            return null;
        }
    }

    /**
     * 创建简单的二维码（不依赖zxing库的简化版本）
     */
    private BufferedImage createSimpleQRCode(String content) {
        // 创建一个简单的白色背景二维码图片（占位符）
        BufferedImage qrCode = new BufferedImage(560, 560, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = qrCode.createGraphics();

        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 560, 560);

        // 黑色边框
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(0, 0, 560, 560);

        // 显示URL文本（分行显示）
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        FontMetrics fm = g2d.getFontMetrics();

        // 将长URL分行显示
        String[] lines = wrapText(content, fm, 540);
        int y = 100;
        for (String line : lines) {
            int x = (560 - fm.stringWidth(line)) / 2;
            g2d.drawString(line, x, y);
            y += fm.getHeight() + 5;
        }

        // 添加提示文本
        g2d.setFont(new Font("微软雅黑", Font.BOLD, 20));
        g2d.drawString("本地跳转二维码", 180, 400);
        g2d.drawString("请用浏览器扫描", 180, 430);

        g2d.dispose();
        return qrCode;
    }

    /**
     * 文本自动换行
     */
    private String[] wrapText(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return new String[] { text };
        }

        // 简单的按字符长度分割
        int charsPerLine = maxWidth / fm.charWidth('A');
        int lines = (text.length() + charsPerLine - 1) / charsPerLine;
        String[] result = new String[lines];

        for (int i = 0; i < lines; i++) {
            int start = i * charsPerLine;
            int end = Math.min(start + charsPerLine, text.length());
            result[i] = text.substring(start, end);
        }

        return result;
    }

    /**
     * 判断是否为非二进制响应
     */
    private boolean isNotBinaryResponse(HttpResponse response) {
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains("application/json");
    }

    /**
     * 创建海报
     */
    private String createPoster(HttpResponse qrcodeResponse, String openid) {
        try {
            // 读取背景图片
            BufferedImage background = ImageIO.read(new URL(BASE_POSTER_URL));

            // 读取二维码
            BufferedImage qrCode = ImageIO.read(qrcodeResponse.bodyStream());

            // 创建输出图片
            BufferedImage outputImage = new BufferedImage(
                    background.getWidth(),
                    background.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            // 绘制图片
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(background, 0, 0, null);
            g2d.drawImage(qrCode, 341, 1464, null);
            g2d.dispose();

            // 保存临时文件
            File tempFile = new File(System.getProperty("java.io.tmpdir"), openid + "_poster.png");
            ImageIO.write(outputImage, "png", tempFile);

            // 使用 CloudStorageService 上传海报
            String posterUrl = cloudStorageService.uploadPoster(tempFile, openid);

            // 删除临时文件
            tempFile.delete();

            return posterUrl;

        } catch (Exception e) {
            log.error("创建海报失败", e);
            return null;
        }
    }

    /**
     * 生成小程序码（带重试机制）
     * 
     * @param memberId 会员ID
     * @param openid   用户openid
     * @return HttpResponse 成功返回二进制响应，失败返回null
     */
    private HttpResponse generateQrCodeWithRetry(Integer memberId, String openid) {
        return generateQrCodeWithRetry(memberId, openid, null);
    }

    private HttpResponse generateQrCodeWithRetry(Integer memberId, String openid, String token) {
        int maxRetries = 3;

        for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
            try {
                // 获取AccessToken
                String accessToken = (retryCount == 0) ? AccessTokenUtil.getAccessToken()
                        : AccessTokenUtil.forceRefreshAccessToken();

                if (StrUtil.isBlank(accessToken)) {
                    log.warn("获取AccessToken失败，重试次数：{}/{}", retryCount + 1, maxRetries);
                    if (retryCount < maxRetries - 1) {
                        Thread.sleep(1000 * (retryCount + 1)); // 递增等待时间
                        continue;
                    }
                    break;
                }

                // 构建小程序码参数
                JSONObject qrcodeParam = buildQrCodeParams(memberId, openid, token);
                String wxUrl = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

                log.info("生成小程序码参数：{}, 重试次数：{}/{}", qrcodeParam, retryCount + 1, maxRetries);

                // 发送请求
                HttpResponse qrcodeResponse = HttpRequest.post(wxUrl)
                        .body(JSONUtil.toJsonStr(qrcodeParam))
                        .timeout(15000) // 15秒超时
                        .execute();

                // 判断是否成功生成二维码
                if (isNotBinaryResponse(qrcodeResponse)) {
                    JSONObject responseJson = JSONUtil.parseObj(qrcodeResponse.body());
                    String errCode = responseJson.getStr("errcode");
                    String errMsg = responseJson.getStr("errmsg");

                    log.error("生成小程序码失败：errcode={}, errmsg={}, 重试次数：{}/{}",
                            errCode, errMsg, retryCount + 1, maxRetries);

                    // 如果是access_token失效，强制刷新后重试
                    if ("40001".equals(errCode) && retryCount < maxRetries - 1) {
                        log.warn("AccessToken失效，强制刷新后重试");
                        AccessTokenUtil.forceRefreshAccessToken();
                        Thread.sleep(1000 * (retryCount + 1));
                        continue;
                    }

                    // 如果是其他错误且还有重试机会
                    if (retryCount < maxRetries - 1) {
                        Thread.sleep(1000 * (retryCount + 1));
                        continue;
                    }

                    return null;
                } else {
                    // 成功生成小程序码
                    log.info("生成小程序码成功，重试次数：{}/{}", retryCount + 1, maxRetries);
                    return qrcodeResponse;
                }

            } catch (Exception e) {
                log.error("生成小程序码异常，重试次数：{}/{}", retryCount + 1, maxRetries, e);

                if (retryCount < maxRetries - 1) {
                    try {
                        Thread.sleep(1000 * (retryCount + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("生成小程序码失败，已重试{}次", maxRetries);
        return null;
    }

    /**
     * 构建小程序码参数
     */
    private JSONObject buildQrCodeParams(Integer memberId, String openid) {
        return buildQrCodeParams(memberId, openid, null);
    }

    /**
     * 构建小程序码参数。
     * token 非空时走动态码格式 MID=<id>&T=<token>（MID 作兜底解析，T 用于追踪/过期）；
     * token 为空时沿用旧格式 MID=<id>&OID=<openid后8位>。
     */
    private JSONObject buildQrCodeParams(Integer memberId, String openid, String token) {
        JSONObject qrcodeParam = new JSONObject();

        String shortOpenid = openid.length() > 8 ? openid.substring(openid.length() - 8) : openid;

        qrcodeParam.set("width", 560);

        // 根据配置决定使用小程序页面还是本地页面
        String targetPage = useLocalPage && StrUtil.isNotBlank(qrcodePageLocal) ? qrcodePageLocal : qrcodePage;
        qrcodeParam.set("page", targetPage);

        // 根据跳转页面/是否动态码设置不同的scene格式
        String finalScene;
        if (targetPage.contains("pages/login/login")) {
            // 旧格式：使用member ID，减少字符长度
            finalScene = "MID=" + memberId;
            log.info("使用旧项目login页面格式，scene={}", finalScene);
        } else if (StrUtil.isNotBlank(token)) {
            // 动态码：MID 兜底解析 + T 追踪/过期
            finalScene = "MID=" + memberId + "&T=" + token;
            log.info("使用动态码格式，scene={}", finalScene);
        } else {
            // 旧的固定码格式：包含完整信息
            finalScene = "MID=" + memberId + "&OID=" + shortOpenid;
            log.info("使用新格式，scene={}", finalScene);
        }

        qrcodeParam.set("scene", finalScene);

        return qrcodeParam;
    }

    /**
     * 从本地缓存获取海报URL
     * 
     * @param openid 用户openid
     * @return 缓存的海报URL，如果不存在返回null
     */
    private String getCachedPosterUrl(String openid) {
        try {
            // 构建文件路径
            Path posterPath = getCachedPosterPath(openid);

            // 检查文件是否存在
            if (Files.exists(posterPath) && Files.isRegularFile(posterPath)) {
                // 构建完整的HTTP访问URL
                String fullUrl = serverBaseUrl + "/" + qrcodeStoragePath + "/" + openid + "_poster.png";
                log.debug("找到缓存海报文件：{}，返回URL：{}", posterPath, fullUrl);
                return fullUrl;
            }

            return null;

        } catch (Exception e) {
            log.error("获取缓存海报失败：openid={}", openid, e);
            return null;
        }
    }

    /**
     * 获取缓存海报的文件路径
     * 
     * @param openid 用户openid
     * @return 文件路径
     */
    private Path getCachedPosterPath(String openid) {
        // 确保存储目录存在
        Path storageDir = Paths.get(qrcodeStoragePath);
        if (!Files.exists(storageDir)) {
            try {
                Files.createDirectories(storageDir);
                log.info("创建海报存储目录：{}", storageDir);
            } catch (IOException e) {
                log.error("创建海报存储目录失败：{}", storageDir, e);
            }
        }

        // 构建文件路径：存储目录/openid_poster.png
        return storageDir.resolve(openid + "_poster.png");
    }

    /**
     * 创建海报并缓存到本地
     * 
     * @param qrcodeResponse 二维码响应
     * @param openid         用户openid
     * @return 海报URL
     */
    private String createPosterWithCache(HttpResponse qrcodeResponse, String openid) {
        // 旧的固定码：按 openid 缓存
        return createPosterWithCacheKey(qrcodeResponse, openid);
    }

    /**
     * 创建海报并按 cacheKey 缓存到本地。
     * cacheKey 决定缓存文件名与云存储 key：固定码用 openid，动态码用 token（保证每张码独立、互不覆盖）。
     */
    private String createPosterWithCacheKey(HttpResponse qrcodeResponse, String cacheKey) {
        try {
            // 读取背景图片
            BufferedImage background = ImageIO.read(new URL(BASE_POSTER_URL));

            // 读取二维码
            BufferedImage qrCode = ImageIO.read(qrcodeResponse.bodyStream());

            // 创建输出图片
            BufferedImage outputImage = new BufferedImage(
                    background.getWidth(),
                    background.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            // 绘制图片
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(background, 0, 0, null);
            g2d.drawImage(qrCode, 341, 1464, null);
            g2d.dispose();

            // 保存到本地缓存
            Path cachedPosterPath = getCachedPosterPath(cacheKey);
            File cachedFile = cachedPosterPath.toFile();
            ImageIO.write(outputImage, "png", cachedFile);
            log.info("海报已缓存到本地：{}", cachedPosterPath);

            // 同时上传到云存储（保持兼容性）
            String cloudUrl = cloudStorageService.uploadPoster(cachedFile, cacheKey);
            if (StrUtil.isNotBlank(cloudUrl)) {
                log.info("海报已同步到云存储：{}", cloudUrl);
                return cloudUrl;
            }

            // 如果云存储上传失败，返回完整的本地HTTP URL
            String localUrl = serverBaseUrl + "/" + qrcodeStoragePath + "/" + cacheKey + "_poster.png";
            log.warn("云存储上传失败，使用本地URL：{}", localUrl);
            return localUrl;

        } catch (Exception e) {
            log.error("创建海报并缓存失败", e);
            return null;
        }
    }



}