package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.clients.ArkClient;
import com.qj.db.DBConfig;
import com.qj.entities.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.Base64;

/**
 * AI 虚拟试穿生成服务。
 */
@Service
public class AiTryOnService {

    private static final Logger log = LoggerFactory.getLogger(AiTryOnService.class);
    private static final String TABLE = "qj_generated_outfit_image";

    @Autowired
    private ArkClient arkClient;

    @Autowired
    private CloudStorageService cloudStorageService;

    @Autowired
    private WxPayOrderQueryService wxPayOrderQueryService;

    @Autowired
    private AppConfigService appConfigService;

    // 付费次数类型：生成(试穿) / 换人像。两者分开定价、分开计数，不能混用。
    private static final String KIND_GENERATE = "generate";
    private static final String KIND_UPLOAD = "upload";

    private static String normalizeKind(String kind) {
        return KIND_UPLOAD.equals(kind) ? KIND_UPLOAD : KIND_GENERATE;
    }

    /** 某类型单价(分)：generate→试穿价，upload→换人像价。价格来自 qj_app_config。 */
    private int priceOf(String kind) {
        return KIND_UPLOAD.equals(kind) ? appConfigService.getUploadPrice() : appConfigService.getGeneratePrice();
    }

    @Value("${ai.tryon.total-free-limit}")
    private int totalFreeLimit;

    @Value("${ai.tryon.upload-free-limit:2}")
    private int uploadFreeLimit;

    @Value("${ai.tryon.generate-free-limit:2}")
    private int generateFreeLimit;

    @Value("${ai.tryon.daily-upload-cap:5}")
    private int dailyUploadCap;

    @Value("${ai.tryon.unit-price:1}")
    private int unitPrice;

    @Value("${ai.tryon.model-name}")
    private String modelName;

    private static final String PORTRAIT_TABLE = "qj_user_tryon_portrait";
    private static final String ORDER_TABLE = "qj_ai_tryon_order";

    /** createOrder 限频：同一 openid 至少间隔多少毫秒 */
    private static final long CREATE_ORDER_MIN_INTERVAL_MS = 20_000L;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastCreateOrderAt =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Db getDb() {
        return DBConfig.getDb(null, "qj");
    }

    public AgentResponse generate(String openid, String humanImageUrl, Integer humanSource,
                                  Long schemeId, JSONArray clothingList) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid 不能为空");
        }
        if (clothingList == null || clothingList.isEmpty()) {
            return AgentResponse.fail("参数错误：clothingList 不能为空");
        }

        // 人像优先用入参；为空时回退到用户当前选定的档案人像
        if (StrUtil.isBlank(humanImageUrl)) {
            JSONObject current = getCurrentPortrait(openid);
            if (current == null) {
                return AgentResponse.fail("请先上传或选择人像");
            }
            humanImageUrl = current.getStr("image_url");
            if (humanSource == null) {
                humanSource = current.getInt("source");
            }
        }

        // 生成配额：先扣免费、再扣付费
        int generateUsed = countGenerateUsed(openid);
        int paidRemaining = countPaidRemaining(openid, KIND_GENERATE);
        boolean willUsePaid = generateUsed >= generateFreeLimit;
        if (willUsePaid && paidRemaining <= 0) {
            return AgentResponse.fail("您的 AI 试穿免费次数已用完，请购买后再试");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        List<String> referenceUrls = new ArrayList<>();
        referenceUrls.add(humanImageUrl);

        List<String> clothingUrls = new ArrayList<>();
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < clothingList.size(); i++) {
            JSONObject it = clothingList.getJSONObject(i);
            String classification = StrUtil.blankToDefault(it.getStr("classification"), "其他");
            String url = firstNonBlank(it.getStr("frontUrl"), it.getStr("mainPath"), it.getStr("first_pic"));
            if (StrUtil.isBlank(url)) continue;
            referenceUrls.add(url);
            clothingUrls.add(url);
            grouped.computeIfAbsent(classification, k -> new ArrayList<>()).add(url);
        }

        if (clothingUrls.isEmpty()) {
            return AgentResponse.fail("参数错误：clothingList 中没有可用的图片 URL");
        }

        String prompt = buildPrompt(grouped, referenceUrls.size());

        Long recordId = insertPending(openid, schemeId, humanImageUrl, humanSource,
                clothingList.toString(), prompt, taskId);

        List<String> arkImageInputs = new ArrayList<>();
        for (String u : referenceUrls) {
            if (StrUtil.isBlank(u)) continue;
            if (u.startsWith("cloud://")) {
                log.warn("跳过 cloud:// URL（后端无法解析，需前端先转 https）：{}", u);
                continue;
            }
            try {
                byte[] bytes = HttpUtil.downloadBytes(u);
                String lower = u.toLowerCase();
                String mime = lower.endsWith(".png") ? "image/png"
                        : (lower.endsWith(".webp") ? "image/webp" : "image/jpeg");
                // 仅对人像参考图补成 9:16 竖版（顶部留白），降低生成时头部被裁概率
                if (u.equals(humanImageUrl)) {
                    bytes = padHumanTo916(bytes);
                    mime = "image/png";
                }
                arkImageInputs.add("data:" + mime + ";base64,"
                        + Base64.getEncoder().encodeToString(bytes));
            } catch (Exception e) {
                log.warn("下载图片转 base64 失败，跳过: {} - {}", u, e.getMessage());
            }
        }
        if (arkImageInputs.isEmpty()) {
            updateFailed(recordId, "无可用图片：人像可能是 cloud:// 未转换，服装图也下载失败");
            return AgentResponse.fail("无可用图片输入（cloud:// 需小程序端先转 https）");
        }

        try {
            ArkClient.ImageResult result = arkClient.generateImage(prompt, arkImageInputs, "2K");
            String cloudUrl = mirrorToCloudStorage(result.imageUrl, openid, taskId);
            updateSuccess(recordId, result.imageUrl, cloudUrl, result.durationMs);
            if (willUsePaid) {
                consumeOnePaidCredit(openid, KIND_GENERATE);
            }

            JSONObject data = new JSONObject()
                    .set("taskId", taskId)
                    .set("status", 1)
                    .set("providerImageUrl", result.imageUrl)
                    .set("cloudImageUrl", cloudUrl)
                    .set("durationMs", result.durationMs);
            return AgentResponse.success("生成成功", data);
        } catch (ArkClient.ArkException e) {
            log.warn("AI 试穿生成失败: openid={}, taskId={}, msg={}", openid, taskId, e.getMessage());
            updateFailed(recordId, e.getMessage());
            return AgentResponse.fail("生成失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("AI 试穿生成失败: openid={}, taskId={}", openid, taskId, e);
            updateFailed(recordId, e.getMessage());
            return AgentResponse.fail("生成失败：" + e.getMessage());
        }
    }

    public AgentResponse getResult(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return AgentResponse.fail("参数错误：taskId 不能为空");
        }
        try {
            Entity entity = getDb().queryOne(
                    "SELECT * FROM " + TABLE + " WHERE task_id = ?", taskId);
            if (entity == null) {
                return AgentResponse.fail("任务不存在");
            }
            JSONObject data = new JSONObject()
                    .set("taskId", entity.getStr("task_id"))
                    .set("status", entity.getInt("status"))
                    .set("providerImageUrl", entity.getStr("provider_image_url"))
                    .set("cloudImageUrl", entity.getStr("cloud_image_url"))
                    .set("errorMsg", entity.getStr("error_msg"))
                    .set("durationMs", entity.getInt("duration_ms"))
                    .set("createTime", entity.getDate("create_time"));
            return AgentResponse.success("查询成功", data);
        } catch (SQLException e) {
            log.error("查询 AI 试穿任务失败: taskId={}", taskId, e);
            return AgentResponse.fail("查询失败：" + e.getMessage());
        }
    }

    public AgentResponse getHistory(String openid, Long schemeId, Integer pageNumber, Integer pageSize) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid 不能为空");
        }
        try {
            int page = pageNumber != null ? pageNumber : 1;
            int size = pageSize != null ? pageSize : 10;
            int offset = (page - 1) * size;

            String whereSql = " WHERE openid = ? AND status = 1";
            List<Object> params = new ArrayList<>();
            params.add(openid);
            if (schemeId != null) {
                whereSql += " AND scheme_id = ?";
                params.add(schemeId);
            }

            List<Object> listParams = new ArrayList<>(params);
            listParams.add(size);
            listParams.add(offset);
            List<Entity> list = getDb().query(
                    "SELECT id, task_id, scheme_id, human_image_url, status, provider_image_url, cloud_image_url, duration_ms, create_time "
                            + "FROM " + TABLE + whereSql + " ORDER BY id DESC LIMIT ? OFFSET ?",
                    listParams.toArray());
            Number total = getDb().queryNumber(
                    "SELECT COUNT(*) FROM " + TABLE + whereSql, params.toArray());

            JSONArray rows = new JSONArray();
            for (Entity e : list) {
                rows.add(new JSONObject()
                        .set("id", e.getLong("id"))
                        .set("taskId", e.getStr("task_id"))
                        .set("schemeId", e.getLong("scheme_id"))
                        .set("humanImageUrl", e.getStr("human_image_url"))
                        .set("providerImageUrl", e.getStr("provider_image_url"))
                        .set("cloudImageUrl", e.getStr("cloud_image_url"))
                        .set("durationMs", e.getInt("duration_ms"))
                        .set("createTime", e.getDate("create_time")));
            }

            JSONObject data = new JSONObject()
                    .set("list", rows)
                    .set("total", total)
                    .set("pageNumber", page)
                    .set("pageSize", size)
                    .set("schemeId", schemeId)
                    .set("totalFreeLimit", totalFreeLimit)
                    .set("usedCount", countGenerateUsed(openid));
            return AgentResponse.success("查询成功", data);
        } catch (SQLException e) {
            log.error("查询 AI 试穿历史失败: openid={} schemeId={}", openid, schemeId, e);
            return AgentResponse.fail("查询失败：" + e.getMessage());
        }
    }

    private int countGenerateUsed(String openid) {
        try {
            Number n = getDb().queryNumber(
                    "SELECT COUNT(*) FROM " + TABLE
                            + " WHERE openid = ? AND status = 1",
                    openid);
            return n == null ? 0 : n.intValue();
        } catch (SQLException e) {
            log.warn("统计 AI 试穿生成次数失败: openid={}", openid, e);
            return 0;
        }
    }

    private int countUploadUsed(String openid) {
        try {
            Number n = getDb().queryNumber(
                    "SELECT COUNT(*) FROM " + PORTRAIT_TABLE
                            + " WHERE openid = ? AND source = 1",
                    openid);
            return n == null ? 0 : n.intValue();
        } catch (SQLException e) {
            log.warn("统计人像上传次数失败: openid={}", openid, e);
            return 0;
        }
    }

    private int countUploadToday(String openid) {
        try {
            Number n = getDb().queryNumber(
                    "SELECT COUNT(*) FROM " + PORTRAIT_TABLE
                            + " WHERE openid = ? AND source = 1 AND create_time >= CURDATE()",
                    openid);
            return n == null ? 0 : n.intValue();
        } catch (SQLException e) {
            log.warn("统计今日人像上传次数失败: openid={}", openid, e);
            return 0;
        }
    }

    private int countPaidRemaining(String openid, String kind) {
        String k = normalizeKind(kind);
        try {
            Number n = getDb().queryNumber(
                    "SELECT IFNULL(SUM(credits - credits_used), 0) FROM " + ORDER_TABLE
                            + " WHERE openid = ? AND pay_status = 1 AND kind = ?",
                    openid, k);
            return n == null ? 0 : n.intValue();
        } catch (SQLException e) {
            log.warn("统计付费剩余次数失败: openid={} kind={}", openid, k, e);
            return 0;
        }
    }

    private JSONObject getCurrentPortrait(String openid) {
        try {
            Entity e = getDb().queryOne(
                    "SELECT id, image_url, cloud_file_id, source FROM " + PORTRAIT_TABLE
                            + " WHERE openid = ? AND is_current = 1 ORDER BY id DESC LIMIT 1",
                    openid);
            if (e == null) return null;
            return new JSONObject()
                    .set("id", e.getLong("id"))
                    .set("image_url", e.getStr("image_url"))
                    .set("cloud_file_id", e.getStr("cloud_file_id"))
                    .set("source", e.getInt("source"));
        } catch (SQLException ex) {
            log.warn("查询当前人像失败: openid={}", openid, ex);
            return null;
        }
    }

    private boolean isEvaluationPassed(String openid) {
        try {
            // 1. 先检查 qj_member.is_evaluation
            Entity e = getDb().queryOne(
                    "SELECT is_evaluation FROM qj_member WHERE _openid = ? LIMIT 1", openid);
            if (e != null) {
                String v = e.getStr("is_evaluation");
                if ("1".equals(v) || "Y".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v)) {
                    return true;
                }
            }
            // 2. 再检查 qj_evaluation 是否有有效记录
            Entity eval = getDb().queryOne(
                    "SELECT id FROM qj_evaluation WHERE openid = ? AND is_del = 0 LIMIT 1", openid);
            if (eval != null) {
                return true;
            }
            return false;
        } catch (SQLException ex) {
            log.warn("查询会员评测状态失败: openid={}", openid, ex);
            return false;
        }
    }

    private void consumeOnePaidCredit(String openid, String kind) {
        String k = normalizeKind(kind);
        try {
            Entity e = getDb().queryOne(
                    "SELECT id FROM " + ORDER_TABLE
                            + " WHERE openid = ? AND pay_status = 1 AND kind = ? AND credits_used < credits "
                            + "ORDER BY paid_time ASC LIMIT 1",
                    openid, k);
            if (e == null) {
                log.warn("意外：尝试扣付费次数但找不到可用订单: openid={} kind={}", openid, k);
                return;
            }
            getDb().execute(
                    "UPDATE " + ORDER_TABLE + " SET credits_used = credits_used + 1, update_time = NOW() "
                            + "WHERE id = ? AND credits_used < credits",
                    e.getLong("id"));
        } catch (SQLException ex) {
            log.error("扣付费次数失败: openid={}", openid, ex);
        }
    }

    public AgentResponse createOrder(String openid, String kind) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid 不能为空");
        }
        String k = normalizeKind(kind);
        int amount = priceOf(k);
        long now = System.currentTimeMillis();
        Long last = lastCreateOrderAt.get(openid);
        if (last != null && now - last < CREATE_ORDER_MIN_INTERVAL_MS) {
            long waitSec = (CREATE_ORDER_MIN_INTERVAL_MS - (now - last) + 999) / 1000;
            log.warn("createOrder 限频拒绝: openid={} 距离上次 {}ms", openid, now - last);
            return AgentResponse.fail("操作太频繁，请 " + waitSec + " 秒后重试");
        }
        lastCreateOrderAt.put(openid, now);

        String tradeNo = "AT" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try {
            Entity row = Entity.create(ORDER_TABLE)
                    .set("openid", openid)
                    .set("trade_no", tradeNo)
                    .set("kind", k)
                    .set("amount", amount)
                    .set("credits", 1)
                    .set("credits_used", 0)
                    .set("pay_status", 0)
                    .set("create_time", new Date())
                    .set("update_time", new Date());
            Long id = getDb().insertForGeneratedKey(row);
            JSONObject data = new JSONObject()
                    .set("orderId", id)
                    .set("tradeNo", tradeNo)
                    .set("kind", k)
                    .set("amount", amount)
                    .set("credits", 1)
                    .set("body", KIND_UPLOAD.equals(k) ? "AI 换人像次数购买" : "AI 试穿次数购买");
            return AgentResponse.success("订单已创建", data);
        } catch (SQLException e) {
            log.error("创建 AI 试穿订单失败: openid={}", openid, e);
            return AgentResponse.fail("创建订单失败：" + e.getMessage());
        }
    }

    public AgentResponse markPaid(String openid, String tradeNo) {
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(tradeNo)) {
            return AgentResponse.fail("参数错误：openid 和 tradeNo 不能为空");
        }
        try {
            Entity order = getDb().queryOne(
                    "SELECT id, pay_status, amount, kind FROM " + ORDER_TABLE
                            + " WHERE trade_no = ? AND openid = ? LIMIT 1",
                    tradeNo, openid);
            if (order == null) {
                return AgentResponse.fail("订单不存在");
            }
            String orderKind = normalizeKind(order.getStr("kind"));
            int payStatus = order.getInt("pay_status");
            if (payStatus == 1) {
                return AgentResponse.success("订单已支付",
                        new JSONObject().set("tradeNo", tradeNo)
                                .set("paidRemaining", countPaidRemaining(openid, orderKind)));
            }

            WxPayOrderQueryService.OrderQueryResult q = wxPayOrderQueryService.queryOrder(tradeNo);
            if (q.isSuccess()) {
                int orderAmount = order.getInt("amount") == null ? 0 : order.getInt("amount");
                if (q.totalFee != orderAmount) {
                    log.warn("markPaid 拒绝：金额不匹配 tradeNo={} order.amount={} wx.total_fee={}",
                            tradeNo, orderAmount, q.totalFee);
                    return AgentResponse.fail("订单金额异常，请联系客服");
                }
                getDb().execute(
                        "UPDATE " + ORDER_TABLE + " SET pay_status = 1, paid_time = NOW(), "
                                + "transaction_id = ?, update_time = NOW() "
                                + "WHERE id = ? AND pay_status = 0",
                        q.transactionId, order.getLong("id"));
                log.info("markPaid 反查成功并已 mark: tradeNo={} transactionId={} amount={} kind={}",
                        tradeNo, q.transactionId, orderAmount, orderKind);
                return AgentResponse.success("订单已标记为已支付",
                        new JSONObject().set("tradeNo", tradeNo)
                                .set("paidRemaining", countPaidRemaining(openid, orderKind)));
            }
            if (q.isPending()) {
                log.info("markPaid 反查未付款: tradeNo={} state={}", tradeNo, q.tradeState);
                return AgentResponse.fail("支付未完成，请稍后重试");
            }
            log.warn("markPaid 反查异常状态: tradeNo={} state={} msg={}",
                    tradeNo, q.tradeState, q.message);
            return AgentResponse.fail("订单状态异常（" + q.tradeState + "），请联系客服");
        } catch (SQLException e) {
            log.error("标记订单已支付失败: openid={} tradeNo={}", openid, tradeNo, e);
            return AgentResponse.fail("标记失败：" + e.getMessage());
        }
    }

    /**
     * 由微信支付回调（payNotify）调用：在已经验签通过的前提下幂等 mark。
     * 同时校验 totalFee 与 DB amount 一致，防止云函数被篡改下小额单。
     * UPDATE 带 pay_status=0 守护，已被前端 markPaid 抢先 mark 的不影响。
     */
    public boolean markPaidFromCallback(String tradeNo, String transactionId, int totalFee) {
        try {
            Entity order = getDb().queryOne(
                    "SELECT id, amount FROM " + ORDER_TABLE + " WHERE trade_no = ? LIMIT 1",
                    tradeNo);
            if (order == null) {
                log.warn("payNotify 拒绝：DB 无对应订单 tradeNo={}", tradeNo);
                return false;
            }
            int orderAmount = order.getInt("amount") == null ? 0 : order.getInt("amount");
            if (totalFee != orderAmount) {
                log.warn("payNotify 拒绝：金额不匹配 tradeNo={} order.amount={} wx.total_fee={}",
                        tradeNo, orderAmount, totalFee);
                return false;
            }
            int affected = getDb().execute(
                    "UPDATE " + ORDER_TABLE + " SET pay_status = 1, paid_time = NOW(), "
                            + "transaction_id = ?, update_time = NOW() "
                            + "WHERE trade_no = ? AND pay_status = 0",
                    transactionId, tradeNo);
            log.info("payNotify markPaid: tradeNo={} transactionId={} amount={} affected={}",
                    tradeNo, transactionId, orderAmount, affected);
            return true;
        } catch (SQLException e) {
            log.error("payNotify markPaid 失败 tradeNo={}", tradeNo, e);
            return false;
        }
    }

    /** 某类型单价(分)，供虚拟支付侧确定扣多少金币（1 金币 = 1 分）。 */
    public int unitPriceOf(String kind) {
        return priceOf(kind);
    }

    /** 某类型当前付费剩余次数（供虚拟支付侧扣费后返回最新余量）。 */
    public int paidRemainingOf(String openid, String kind) {
        return countPaidRemaining(openid, normalizeKind(kind));
    }

    /**
     * 虚拟支付（xpay）扣金币成功后发放权益：往订单表写一条已支付、可用次数=1 的记录。
     * 复用既有 credits/credits_used 体系——后续 generate/savePortrait 会照常 consumeOnePaidCredit。
     * 以 billNo 作 trade_no 幂等：同一 billNo 重复调用不重复发放。
     *
     * @param openid        用户
     * @param kind          generate / upload
     * @param billNo        虚拟支付订单号（幂等键）
     * @param transactionId 微信侧交易号（对账用，可空）
     * @return true=已发放或本次发放成功；false=DB 异常
     */
    public synchronized boolean grantPaidCredit(String openid, String kind, String billNo, String transactionId) {
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(billNo)) {
            log.warn("grantPaidCredit 参数缺失 openid={} billNo={}", openid, billNo);
            return false;
        }
        String k = normalizeKind(kind);
        try {
            Entity exist = getDb().queryOne(
                    "SELECT id FROM " + ORDER_TABLE + " WHERE trade_no = ? LIMIT 1", billNo);
            if (exist != null) {
                log.info("grantPaidCredit 幂等命中，已发放过: billNo={} openid={}", billNo, openid);
                return true;
            }
            Entity row = Entity.create(ORDER_TABLE)
                    .set("openid", openid)
                    .set("trade_no", billNo)
                    .set("kind", k)
                    .set("amount", priceOf(k))
                    .set("credits", 1)
                    .set("credits_used", 0)
                    .set("pay_status", 1)
                    .set("paid_time", new Date())
                    .set("transaction_id", transactionId)
                    .set("create_time", new Date())
                    .set("update_time", new Date());
            getDb().insertForGeneratedKey(row);
            log.info("grantPaidCredit 发放成功: billNo={} openid={} kind={} amount={}",
                    billNo, openid, k, priceOf(k));
            return true;
        } catch (SQLException e) {
            // trade_no 唯一约束下并发重复也会落到这里——视作已发放
            log.warn("grantPaidCredit 写入异常（可能并发重复）: billNo={} err={}", billNo, e.getMessage());
            try {
                Entity exist = getDb().queryOne(
                        "SELECT id FROM " + ORDER_TABLE + " WHERE trade_no = ? LIMIT 1", billNo);
                return exist != null;
            } catch (SQLException ex) {
                return false;
            }
        }
    }

    /**
     * 每 30 分钟清理 2 小时以上未付的废单。用户付到一半放弃 / 关掉小程序的常见情况。
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void cleanupExpiredAiTryOnOrders() {
        try {
            int affected = getDb().execute(
                    "DELETE FROM " + ORDER_TABLE + " "
                            + "WHERE pay_status = 0 AND create_time < DATE_SUB(NOW(), INTERVAL 2 HOUR)");
            if (affected > 0) {
                log.info("cleanupExpiredAiTryOnOrders: 清理 {} 条过期未付订单", affected);
            }
        } catch (SQLException e) {
            log.error("cleanupExpiredAiTryOnOrders 失败", e);
        }
        // 顺手清理 createOrder 限频桶（1 小时未活跃的）
        long expireBefore = System.currentTimeMillis() - 3_600_000L;
        lastCreateOrderAt.entrySet().removeIf(e -> e.getValue() < expireBefore);
    }

    public AgentResponse health() {
        JSONObject data = new JSONObject();
        Map<String, String> tables = new LinkedHashMap<>();
        for (String t : new String[]{TABLE, PORTRAIT_TABLE, ORDER_TABLE}) {
            try {
                Number n = getDb().queryNumber("SELECT COUNT(*) FROM " + t);
                tables.put(t, "ok(rows=" + (n == null ? 0 : n.intValue()) + ")");
            } catch (SQLException e) {
                tables.put(t, "FAIL: " + e.getMessage());
            }
        }
        data.set("tables", tables);
        data.set("uploadFreeLimit", uploadFreeLimit);
        data.set("generateFreeLimit", generateFreeLimit);
        data.set("dailyUploadCap", dailyUploadCap);
        data.set("unitPriceCents", unitPrice);
        data.set("modelName", modelName);
        boolean allOk = tables.values().stream().allMatch(v -> v.startsWith("ok"));
        return allOk ? AgentResponse.success("AI 试穿服务正常", data)
                     : AgentResponse.fail("AI 试穿服务异常：" + tables);
    }

    public AgentResponse getQuota(String openid) {
        if (StrUtil.isBlank(openid)) {
            return AgentResponse.fail("参数错误：openid 不能为空");
        }
        int generateUsed = countGenerateUsed(openid);
        int uploadUsed = countUploadUsed(openid);
        int generatePaidRemaining = countPaidRemaining(openid, KIND_GENERATE);
        int uploadPaidRemaining = countPaidRemaining(openid, KIND_UPLOAD);
        int generatePrice = priceOf(KIND_GENERATE);
        int uploadPrice = priceOf(KIND_UPLOAD);
        JSONObject current = getCurrentPortrait(openid);
        boolean evaluated = isEvaluationPassed(openid);

        JSONObject data = new JSONObject()
                .set("uploadFreeLimit", uploadFreeLimit)
                .set("uploadFreeUsed", uploadUsed)
                .set("uploadFreeRemaining", Math.max(0, uploadFreeLimit - uploadUsed))
                .set("generateFreeLimit", generateFreeLimit)
                .set("generateFreeUsed", generateUsed)
                .set("generateFreeRemaining", Math.max(0, generateFreeLimit - generateUsed))
                // 分项付费余量与价格
                .set("generatePaidRemaining", generatePaidRemaining)
                .set("uploadPaidRemaining", uploadPaidRemaining)
                .set("generatePrice", generatePrice)
                .set("uploadPrice", uploadPrice)
                // 兼容旧字段：paidRemaining/unitPrice 对应"生成(试穿)"口径
                .set("paidRemaining", generatePaidRemaining)
                .set("unitPrice", generatePrice)
                .set("needEvaluation", !evaluated)
                .set("currentPortraitUrl", current == null ? null : current.getStr("image_url"))
                .set("currentPortraitCloudFileId", current == null ? null : current.getStr("cloud_file_id"))
                .set("currentPortraitSource", current == null ? null : current.getInt("source"));
        return AgentResponse.success("查询成功", data);
    }

    public AgentResponse savePortrait(String openid, String imageUrl, String cloudFileId) {
        if (StrUtil.isBlank(openid) || StrUtil.isBlank(imageUrl)) {
            return AgentResponse.fail("参数错误：openid 与 imageUrl 不能为空");
        }
        if (!isEvaluationPassed(openid)) {
            return AgentResponse.fail("完成评测后才能上传专属试穿人像");
        }
        if (countUploadToday(openid) >= dailyUploadCap) {
            return AgentResponse.fail("今日人像上传次数过多，请明日再试");
        }
        int uploadUsed = countUploadUsed(openid);
        int paidRemaining = countPaidRemaining(openid, KIND_UPLOAD);
        boolean willUsePaid = uploadUsed >= uploadFreeLimit;
        if (willUsePaid && paidRemaining <= 0) {
            return AgentResponse.fail("您的免费换人像次数已用完，请购买后再试");
        }

        try {
            getDb().execute(
                    "UPDATE " + PORTRAIT_TABLE + " SET is_current = 0, update_time = NOW() "
                            + "WHERE openid = ? AND is_current = 1",
                    openid);
            Entity row = Entity.create(PORTRAIT_TABLE)
                    .set("openid", openid)
                    .set("image_url", imageUrl)
                    .set("cloud_file_id", cloudFileId)
                    .set("source", 1)
                    .set("is_current", 1)
                    .set("create_time", new Date())
                    .set("update_time", new Date());
            Long newId = getDb().insertForGeneratedKey(row);
            if (willUsePaid) {
                consumeOnePaidCredit(openid, KIND_UPLOAD);
            }
            JSONObject data = new JSONObject()
                    .set("id", newId)
                    .set("imageUrl", imageUrl)
                    .set("usedPaid", willUsePaid);
            return AgentResponse.success("上传成功", data);
        } catch (SQLException ex) {
            log.error("保存人像失败: openid={}", openid, ex);
            return AgentResponse.fail("保存失败：" + ex.getMessage());
        }
    }

    private Long insertPending(String openid, Long schemeId, String humanImageUrl, Integer humanSource,
                               String clothingPayload, String prompt, String taskId) {
        try {
            Entity entity = Entity.create(TABLE)
                    .set("openid", openid)
                    .set("scheme_id", schemeId)
                    .set("human_image_url", humanImageUrl)
                    .set("human_source", humanSource != null ? humanSource : 0)
                    .set("clothing_payload", clothingPayload)
                    .set("prompt_text", prompt)
                    .set("model_name", modelName)
                    .set("task_id", taskId)
                    .set("status", 0)
                    .set("create_time", new Date())
                    .set("update_time", new Date());
            return getDb().insertForGeneratedKey(entity);
        } catch (SQLException e) {
            throw new RuntimeException("写入 pending 记录失败", e);
        }
    }

    private void updateSuccess(Long id, String providerUrl, String cloudUrl, long durationMs) {
        try {
            getDb().execute(
                    "UPDATE " + TABLE
                            + " SET status = 1, provider_image_url = ?, cloud_image_url = ?, duration_ms = ?, update_time = NOW() WHERE id = ?",
                    providerUrl, cloudUrl, (int) durationMs, id);
        } catch (SQLException e) {
            log.error("更新 AI 试穿成功状态失败: id={}", id, e);
        }
    }

    private void updateFailed(Long id, String errorMsg) {
        try {
            String trimmed = errorMsg == null ? null
                    : (errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg);
            getDb().execute(
                    "UPDATE " + TABLE + " SET status = 2, error_msg = ?, update_time = NOW() WHERE id = ?",
                    trimmed, id);
        } catch (SQLException e) {
            log.error("更新 AI 试穿失败状态失败: id={}", id, e);
        }
    }

    private String mirrorToCloudStorage(String providerUrl, String openid, String taskId) {
        File tmp = null;
        try {
            tmp = File.createTempFile("ai-tryon-", ".png");
            try (HttpResponse resp = HttpUtil.createGet(providerUrl).timeout(30000).execute()) {
                if (!resp.isOk()) {
                    log.warn("下载 AI 试穿图片失败: status={}, url={}", resp.getStatus(), providerUrl);
                    return null;
                }
                Files.write(tmp.toPath(), resp.bodyBytes());
            }
            String cloudPath = "tryon/" + openid + "/" + taskId + ".png";
            return cloudStorageService.uploadFile(tmp, cloudPath);
        } catch (IOException e) {
            log.warn("转存 AI 试穿图到云存储失败: providerUrl={}", providerUrl, e);
            return null;
        } finally {
            if (tmp != null && tmp.exists()) {
                tmp.delete();
            }
        }
    }

    private static final Set<String> ACCESSORY_TYPES = new HashSet<>(Arrays.asList(
            "鞋子", "包包", "耳环", "项链", "皮带", "围巾", "帽子"
    ));

    /**
     * 把人像参考图补成 9:16 竖版画布（顶部留白、底部留空间），降低 seedream 图生图时
     * 因参考图是半身/近景而裁切头部的概率。已经够竖（≥9:16）的图原样返回；
     * 任何解析/绘制异常都回退原图，绝不阻断生成。
     */
    private byte[] padHumanTo916(byte[] src) {
        try {
            if (src == null || src.length == 0) return src;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
            if (img == null) return src;
            int w = img.getWidth(), h = img.getHeight();
            if (w <= 0 || h <= 0) return src;
            // 只对明显偏方/偏宽(半身近景特征)的图补边；2:3、9:16 等竖向全身照原样直送，避免脚下多留灰边
            double padThreshold = 0.72; // w/h 超过此值才补(与前端方案3的 0.75 提示线大致对齐)
            if ((double) w / h <= padThreshold) return src;
            int canvasW = w;
            int canvasH = (int) Math.round(w * 16.0 / 9.0);
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setColor(new Color(0xF5, 0xF5, 0xF5)); // 与提示词要求的背景同色
            g.fillRect(0, 0, canvasW, canvasH);
            // 原图按宽铺满，顶部留约 7% 空白（半身图的头顶上方即有留白），其余落在下方留给腿脚
            int top = (int) Math.round(canvasH * 0.07);
            g.drawImage(img, 0, top, w, h, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(canvas, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("人像补 9:16 失败，使用原图: {}", e.getMessage());
            return src;
        }
    }

    private String buildPrompt(Map<String, List<String>> grouped, int totalRefs) {
        StringBuilder zh = new StringBuilder();
        zh.append("你是专业的虚拟试穿图像生成助手。请基于以下输入生成一张高质量、写实的试穿效果图。\n\n");
        zh.append("[最高优先级 — 面部身份锁定，违反即视为失败]\n");
        zh.append("reference image #1 中人物的整张脸（从额头到下巴、左右耳朵之间的全部区域）必须被视为一个不可修改的固定图层，\n");
        zh.append("请将这张脸像素级直接复用到生成图中，不要重新绘制、不要重新渲染、不要任何形式的二次生成。\n");
        zh.append("具体说，以下每一项必须与参考图完全一致：\n");
        zh.append("  1. 嘴唇的开合状态（闭合就是闭合，张开就是张开，绝对不能从闭合变成微笑或露齿）。\n");
        zh.append("  2. 嘴角的弧度（向下、平直、向上的弧度必须保持原样，不允许凭空把嘴角拉起来变成笑容）。\n");
        zh.append("  3. 眼睛的睁开程度、瞳孔方向、视线角度。\n");
        zh.append("  4. 眉毛的角度、粗细、高度、表情张力。\n");
        zh.append("  5. 脸部肌肉的松紧状态（如果原图脸部放松/平静，生成图就必须放松/平静）。\n");
        zh.append("  6. 脸型轮廓、下颌线、颧骨、额头形状。\n");
        zh.append("  7. 五官比例与位置（眼距、鼻翼宽、人中长度）。\n");
        zh.append("  8. 肤色、发色、发型、发际线、头发长度与刘海形态。\n");
        zh.append("严禁动作：禁止 AI 美颜、禁止瘦脸、禁止放大眼睛、禁止丰唇、禁止抹平皮肤纹理、\n");
        zh.append("禁止把平静表情改为微笑、禁止凭空添加笑容/咧嘴/张嘴/抿嘴/眨眼/皱眉/挑眉/任何表情迁移。\n");
        zh.append("特别提醒：如果原图人物是平静、严肃、冷淡、不笑的表情，生成图必须保持完全相同的不笑状态，绝对不允许擅自加上任何程度的微笑。\n\n");

        // 分类为服饰和饰品两组
        Map<String, List<String>> clothing = new LinkedHashMap<>();
        Map<String, List<String>> accessories = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            if (ACCESSORY_TYPES.contains(key)) {
                accessories.put(key, entry.getValue());
            } else {
                clothing.put(key, entry.getValue());
            }
        }

        // 统计完整 clothing + accessories 列表用于"封闭单品集合"约束
        List<String> allCategoryLabels = new ArrayList<>();
        allCategoryLabels.addAll(clothing.keySet());
        allCategoryLabels.addAll(accessories.keySet());

        if (!clothing.isEmpty()) {
            zh.append("[服饰清单 — 必须像素级照搬，不可重新设计]\n");
            int idx = 2;
            for (Map.Entry<String, List<String>> entry : clothing.entrySet()) {
                List<String> urls = entry.getValue();
                StringBuilder refs = new StringBuilder();
                for (int i = 0; i < urls.size(); i++) {
                    if (i > 0) refs.append(", ");
                    refs.append("#").append(idx++);
                }
                zh.append("- ").append(entry.getKey()).append("：reference image ").append(refs).append("\n");
            }
            zh.append("对上述每一件服饰，生成图中对应那一件必须与参考图完全一致，逐项保留：\n");
            zh.append("  1. 颜色（包括主色、次色、印花颜色），不可换色、不可改色调、不可改饱和度。\n");
            zh.append("  2. 图案/印花（花型、密度、位置、走向），不可替换成纯色、不可重新生成图案。\n");
            zh.append("  3. 剪裁与版型（宽松/紧身/A字/H型/直筒/喇叭），不可改换。\n");
            zh.append("  4. 长度（上衣下摆位置、裙裤长度），不可拉长、不可缩短。\n");
            zh.append("  5. 领型（V领/圆领/翻领/西装领/高领）、袖型（短袖/长袖/泡泡袖/喇叭袖）、扣子布局、口袋位置。\n");
            zh.append("  6. 面料质感（针织/雪纺/牛仔/呢料/皮）。\n");
            zh.append("严禁动作：禁止把参考图的某件衣服替换成同品类的其它款式（如把 V 领印花上衣替换成蓝色圆领 T 恤、把卡其风衣替换成灰色西装马甲均视为严重错误）。\n\n");
        }

        if (!accessories.isEmpty()) {
            zh.append("[饰品清单 — 必须像素级照搬]\n");
            int idx = 2;
            for (List<String> urls : clothing.values()) {
                idx += urls.size();
            }
            for (Map.Entry<String, List<String>> entry : accessories.entrySet()) {
                List<String> urls = entry.getValue();
                StringBuilder refs = new StringBuilder();
                for (int i = 0; i < urls.size(); i++) {
                    if (i > 0) refs.append(", ");
                    refs.append("#").append(idx++);
                }
                zh.append("- ").append(entry.getKey()).append("：reference image ").append(refs).append("\n");
            }
            zh.append("饰品同样必须与参考图完全一致：颜色、款式、形状、材质、镶嵌、链条粗细、坠子大小都不可变。\n");
            zh.append("佩戴位置：鞋子穿在脚上，包包手提或肩背，项链佩戴在颈部，耳环佩戴在耳部，皮带系在腰间，围巾围在颈部，帽子戴在头部后方。\n\n");
        }

        zh.append("[严格的单品集合 — 不可凭空增减]\n");
        zh.append("生成图中人物身上能出现的所有服装与饰品，必须**严格等于**上面列出的清单：");
        for (int i = 0; i < allCategoryLabels.size(); i++) {
            if (i > 0) zh.append("、");
            zh.append(allCategoryLabels.get(i));
        }
        zh.append("。\n");
        zh.append("严禁凭空添加清单以外的任何单品，例如：未给出帽子时不能戴帽子，未给出外套时不能加马甲/风衣/西装外套，未给出腰带时不能加皮带，未给出包包时不能挂包。\n");
        zh.append("也严禁省略清单中的任何单品（如果给了上衣就必须穿上衣、给了项链就必须戴项链）。\n\n");

        zh.append("[摄影机视角 — 极其重要，必须严格遵守]\n");
        zh.append("- 视角：严格的水平平视（eye-level），摄影机中心高度与人物胸口齐平。\n");
        zh.append("- 严禁俯视（top-down / high-angle / bird's-eye），严禁仰视（low-angle），严禁任何倾斜、鱼眼、广角畸变。\n");
        zh.append("- 使用中焦镜头视感（50mm-85mm 等效），保证人体比例正常，不放大头部、不缩短腿部。\n");
        zh.append("- 人物正对镜头，面朝正前方，肩膀与镜头平行；禁止半侧身、背对、转头等非正面姿态。\n\n");

        zh.append("[构图规范]\n");
        zh.append("- 画面比例：9:16 竖图，2K 分辨率，输出 PNG。\n");
        zh.append("- 人物垂直与水平方向均居中。\n");
        zh.append("- 必须完整显示从头顶到脚底的全身：头顶上方留约 5%-8% 空白，脚底下方留约 5%-8% 空白，左右两侧留适度空白。\n");
        zh.append("- 【特别强调·务必遵守】人物完整头部与整张脸（含发顶、额头、双眼、鼻、嘴、下巴）必须清晰、完整地出现在画面内，头顶上方留出空白，严禁人物头部贴住或超出画面上边缘、严禁把头顶/额头/面部任何部分裁切到画面之外。\n");
        zh.append("- 严禁裁剪四肢与头部：禁止只显示半身、禁止裁掉脚或鞋、禁止裁掉手或手指、禁止裁掉头顶或脸部。\n");
        zh.append("- 画面中只允许出现 1 个人物，背景不能有第二个人、不能有镜面/玻璃反射出现额外人像。\n\n");

        zh.append("[人物姿势 — 极其重要，必须严格遵守]\n");
        zh.append("- 自然直立站姿，双脚与肩同宽或略窄，脚掌平贴地面。\n");
        zh.append("- 双臂必须笔直自然下垂、紧贴身体两侧，手腕位置大约在大腿中部高度。\n");
        zh.append("- 双手必须紧贴大腿外侧、手心朝向身体（朝向大腿内侧），五指自然并拢、微微弯曲或自然伸直均可。\n");
        zh.append("- 严禁双手前伸、向前展示、手心朝向镜头、向外打开、抬起、做任何展示动作或手势。\n");
        zh.append("- 严禁把手插兜、背手、抱胸、托腮、叉腰、提衣摆、撩头发、整理饰品。\n");
        zh.append("- 身体重心均衡，无明显倾斜、无夸张姿态、无走动动作。\n");
        zh.append("- 人体比例符合真实成年女性 1:7 至 1:8 头身比，禁止 Q 版、漫画、大头娃娃比例。\n\n");

        zh.append("[场景与光线]\n");
        zh.append("- 纯色浅灰背景（接近 #F5F5F5），背景干净无杂物、无家具、无墙角线、无地板纹理、无影子拖尾。\n");
        zh.append("- 柔和的正面均匀漫射光，光线方向与镜头同向，避免强烈侧光、逆光、顶光造成阴影。\n");
        zh.append("- 写实摄影风格、清晰锐利、肤质和布料质感真实，禁止塑料感、油画感、卡通化。\n\n");

        zh.append("[严格禁止]\n");
        zh.append("- 禁止改变服装的颜色、图案、剪裁、长度、领口、袖型与原参考图不一致。\n");
        zh.append("- 禁止添加任何 logo、品牌名、文字、标签、水印。\n");
        zh.append("- 【特别强调·务必遵守】画面中严禁出现二维码 / QR code / 条形码 / 扫码方块图案，无论在右下角、其它任意角落、边缘还是背景里都绝对不允许；最终成图必须干净，不含任何二维码或类似的黑白方块码、与试穿无关的图形元素。\n");
        zh.append("- 禁止生成多余手指、缺失手指、扭曲关节、错位肢体、面部多余特征。\n");
        zh.append("- 禁止改动人物身材、胖瘦、身高、肤色。\n");
        zh.append("- 禁止生成任何裸露、贴身轮廓过度暴露、性暗示元素。\n\n");

        zh.append("输出 1 张严格符合以上全部规范的写实全身试穿效果图。\n");

        return zh.toString();
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (StrUtil.isNotBlank(v)) return v;
        }
        return null;
    }
}
