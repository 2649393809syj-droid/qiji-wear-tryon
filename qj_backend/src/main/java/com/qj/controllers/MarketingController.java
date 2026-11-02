package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.MarketingService;
import com.qj.services.PromotionRelationshipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * 推广营销控制器
 * 
 * @author system
 */
@RestController
@RequestMapping("/api/marketing")
public class MarketingController {

    private static final Logger log = LoggerFactory.getLogger(MarketingController.class);

    @Autowired
    private MarketingService marketingService;

    @Autowired
    private PromotionRelationshipService promotionRelationshipService;

    /**
     * 获取推广海报
     * 
     * @param requestBody 请求体，包含 openid
     * @return 推广海报URL
     */
    @PostMapping("/getShopPoster")
    public AgentResponse getShopPoster(@RequestBody String requestBody) {
        log.info("获取推广海报请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return marketingService.getShopPoster(openid);

        } catch (Exception e) {
            log.error("获取推广海报异常", e);
            return AgentResponse.fail("获取推广海报异常：" + e.getMessage());
        }
    }

    /**
     * 扫描推广海报
     * 
     * @param requestBody 请求体，包含 memberId, promoterOpenid, scannerOpenid
     * @return 扫描结果
     */
    @PostMapping("/scanShopPoster")
    public AgentResponse scanShopPoster(@RequestBody String requestBody) {
        log.info("扫描推广海报请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            Integer memberId = reqJson.getInt("memberId");
            String promoterOpenid = reqJson.getStr("promoterOpenid");
            String scannerOpenid = reqJson.getStr("scannerOpenid");

            if (memberId == null || StrUtil.isBlank(promoterOpenid) || StrUtil.isBlank(scannerOpenid)) {
                return AgentResponse.fail("参数错误：memberId、promoterOpenid、scannerOpenid不能为空");
            }

            return marketingService.scanShopPoster(memberId, promoterOpenid, scannerOpenid);

        } catch (Exception e) {
            log.error("扫描推广海报异常", e);
            return AgentResponse.fail("扫描推广海报异常：" + e.getMessage());
        }
    }

    /**
     * 获取推广权限
     * 
     * @param requestBody 请求体，包含 openid
     * @return 推广权限信息
     */
    @PostMapping("/getPromotionAuthority")
    public AgentResponse getPromotionAuthority(@RequestBody String requestBody) {
        log.info("获取推广权限请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return marketingService.getPromotionAuthorityByOpenid(openid);

        } catch (Exception e) {
            log.error("获取推广权限异常", e);
            return AgentResponse.fail("获取推广权限异常：" + e.getMessage());
        }
    }

    /**
     * 通过场景值扫描推广海报（用于小程序码扫描）
     * 
     * @param requestBody 请求体，包含 scene, scannerOpenid
     * @return 扫描结果
     */
    @PostMapping("/scanPosterByScene")
    public AgentResponse scanPosterByScene(@RequestBody String requestBody) {
        log.info("通过场景值扫描推广海报请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String scene = reqJson.getStr("scene");
            String scannerOpenid = reqJson.getStr("scannerOpenid");

            if (StrUtil.isBlank(scene) || StrUtil.isBlank(scannerOpenid)) {
                return AgentResponse.fail("参数错误：scene、scannerOpenid不能为空");
            }

            // 解析场景值，格式：MID=memberId&OID=promoterOpenid
            String[] params = scene.split("&");
            Integer memberId = null;
            String promoterOpenid = null;

            for (String param : params) {
                String[] kv = param.split("=");
                if (kv.length == 2) {
                    if ("MID".equals(kv[0])) {
                        try {
                            memberId = Integer.parseInt(kv[1]);
                        } catch (NumberFormatException e) {
                            log.warn("解析memberId失败：{}", kv[1]);
                        }
                    } else if ("OID".equals(kv[0])) {
                        promoterOpenid = kv[1];
                    }
                }
            }

            if (memberId == null || StrUtil.isBlank(promoterOpenid)) {
                return AgentResponse.fail("场景值格式错误");
            }

            return marketingService.scanShopPoster(memberId, promoterOpenid, scannerOpenid);

        } catch (Exception e) {
            log.error("通过场景值扫描推广海报异常", e);
            return AgentResponse.fail("通过场景值扫描推广海报异常：" + e.getMessage());
        }
    }

    /**
     * 扫码进入首页后处理推广关系
     * 
     * @param requestBody 请求体，包含 scene, scannerOpenid
     * @return 处理结果
     */
    @PostMapping("/handleQrcodeScan")
    public AgentResponse handleQrcodeScan(@RequestBody String requestBody) {
        log.info("处理扫码推广关系请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String scene = reqJson.getStr("scene");
            String scannerOpenid = reqJson.getStr("scannerOpenid");

            if (StrUtil.isBlank(scene) || StrUtil.isBlank(scannerOpenid)) {
                return AgentResponse.fail("参数错误：scene、scannerOpenid不能为空");
            }

            return marketingService.handleQrcodeScan(scene, scannerOpenid);

        } catch (Exception e) {
            log.error("处理扫码推广关系异常", e);
            return AgentResponse.fail("处理扫码推广关系异常：" + e.getMessage());
        }
    }

    /**
     * 获取用户信息
     * 
     * @param openid 用户openid
     * @param isDel  是否删除标识，可选参数
     * @return 用户信息
     */
    @GetMapping("/getMemberInfoByOpenid")
    public AgentResponse getMemberInfoByOpenid(@RequestParam String openid,
            @RequestParam(required = false, defaultValue = "0") Integer isDel) {
        log.info("获取用户信息请求：openid={}, isDel={}", openid, isDel);

        try {
            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return marketingService.getMemberInfoByOpenid(openid, isDel);

        } catch (Exception e) {
            log.error("获取用户信息异常", e);
            return AgentResponse.fail("获取用户信息异常：" + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public AgentResponse health() {
        return AgentResponse.success("推广营销服务正常运行");
    }

    /**
     * 设置二维码跳转页面配置
     * 
     * @param requestBody 请求体，包含 useLocal (boolean), page (string), pageLocal
     *                    (string)
     * @return 设置结果
     */
    @PostMapping("/setQrcodePageConfig")
    public AgentResponse setQrcodePageConfig(@RequestBody String requestBody) {
        log.info("设置二维码跳转页面配置请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            Boolean useLocal = reqJson.getBool("useLocal");
            String page = reqJson.getStr("page");
            String pageLocal = reqJson.getStr("pageLocal");

            return marketingService.setQrcodePageConfig(useLocal, page, pageLocal);

        } catch (Exception e) {
            log.error("设置二维码跳转页面配置异常", e);
            return AgentResponse.fail("设置配置异常：" + e.getMessage());
        }
    }

    /**
     * 获取二维码跳转页面配置
     * 
     * @return 当前配置
     */
    @GetMapping("/getQrcodePageConfig")
    public AgentResponse getQrcodePageConfig() {
        return marketingService.getQrcodePageConfig();
    }

    /**
     * 获取本地文件跳转海报
     * 
     * @param requestBody 请求体，包含 openid
     * @return 本地跳转海报URL
     */
    @PostMapping("/getLocalPoster")
    public AgentResponse getLocalPoster(@RequestBody String requestBody) {
        log.info("获取本地跳转海报请求：{}", requestBody);

        try {
            JSONObject reqJson = JSONUtil.parseObj(requestBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                return AgentResponse.fail("参数错误：openid不能为空");
            }

            return marketingService.getLocalPoster(openid);

        } catch (Exception e) {
            log.error("获取本地跳转海报异常", e);
            return AgentResponse.fail("获取本地跳转海报异常：" + e.getMessage());
        }
    }

    /**
     * 手动触发推广关系检查
     *
     * @return 检查结果
     */
    @PostMapping("/checkPromotionRelationships")
    public AgentResponse checkPromotionRelationships() {
        log.info("手动触发推广关系检查");

        try {
            JSONObject result = promotionRelationshipService.manualCheckPromotionRelationships();

            if (result.getInt("errCode") == 0) {
                return AgentResponse.success(result.getStr("errMsg"));
            } else {
                return AgentResponse.fail(result.getStr("errMsg"));
            }

        } catch (Exception e) {
            log.error("手动触发推广关系检查异常", e);
            return AgentResponse.fail("推广关系检查异常：" + e.getMessage());
        }
    }

}