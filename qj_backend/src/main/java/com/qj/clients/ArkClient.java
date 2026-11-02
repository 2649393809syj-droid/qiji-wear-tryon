package com.qj.clients;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.qj.services.AppConfigService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎 Ark 客户端，封装 Doubao-Seedream-4.5 图像生成调用。
 * 走 OpenAI 兼容的 images/generations 接口。
 */
@Component
public class ArkClient {

    private static final Logger log = LoggerFactory.getLogger(ArkClient.class);
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    @Value("${ark.base-url}")
    private String baseUrl;

    @Value("${ark.model}")
    private String model;

    @Value("${ark.timeout-ms}")
    private int timeoutMs;

    @Autowired
    private AppConfigService appConfig;

    private OkHttpClient http;

    @PostConstruct
    public void init() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        log.info("Ark 客户端初始化: baseUrl={}, model={}, key=(loaded from qj_app_config at request time)",
                baseUrl, model);
    }

    public ImageResult generateImage(String prompt, List<String> referenceImageUrls, String size) {
        String apiKey = appConfig.getArkApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new ArkException("Ark api-key 未配置（请检查 qj_app_config.ark_api_key）");
        }

        JSONObject body = new JSONObject()
                .set("model", model)
                .set("prompt", prompt)
                .set("watermark", false);

        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            JSONArray imageArr = new JSONArray();
            for (String url : referenceImageUrls) {
                if (StrUtil.isNotBlank(url)) {
                    imageArr.add(url);
                }
            }
            body.set("image", imageArr);
        }

        // size: "2K" or "4K" or explicit WxH
        if (StrUtil.isNotBlank(size)) {
            body.set("size", size);
        }

        long start = System.currentTimeMillis();
        Request request = new Request.Builder()
                .url(baseUrl + "/images/generations")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA))
                .build();

        IOException lastIO = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (Response resp = http.newCall(request).execute()) {
                String respStr = resp.body() != null ? resp.body().string() : "";
                long cost = System.currentTimeMillis() - start;

                if (resp.isSuccessful()) {
                    return parseImageResult(respStr, cost);
                }

                if (resp.code() == 401 || resp.code() == 404) {
                    log.error("Ark 鉴权/路径错误: status={}, body={}", resp.code(), respStr);
                    throw new ArkException(extractErrorMsg(respStr, resp.code()));
                }

                log.warn("Ark 重试中 ({}/3): status={}, body={}", attempt, resp.code(), respStr);
                Thread.sleep(500L * attempt * attempt);
            } catch (ArkException e) {
                throw e;
            } catch (IOException e) {
                lastIO = e;
                log.warn("Ark 调用异常重试 ({}/3): {}", attempt, e.getMessage());
                try {
                    Thread.sleep(500L * attempt * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ArkException("调用被中断", ie);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ArkException("调用被中断", e);
            }
        }
        throw new ArkException("Ark 调用 3 次重试后仍失败", lastIO);
    }

    private ImageResult parseImageResult(String respStr, long durationMs) {
        JSONObject json = JSONUtil.parseObj(respStr);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ArkException("Ark 返回 data 为空: " + respStr);
        }
        JSONObject first = data.getJSONObject(0);
        String url = first.getStr("url");
        if (StrUtil.isBlank(url)) {
            String b64 = first.getStr("b64_json");
            if (StrUtil.isNotBlank(b64)) {
                return new ImageResult(b64, respStr, durationMs);
            }
            throw new ArkException("Ark 响应未携带图片: " + respStr);
        }
        return new ImageResult(url, respStr, durationMs);
    }

    private String extractErrorMsg(String respStr, int status) {
        try {
            JSONObject json = JSONUtil.parseObj(respStr);
            JSONObject err = json.getJSONObject("error");
            if (err != null && StrUtil.isNotBlank(err.getStr("message"))) {
                return err.getStr("message");
            }
        } catch (Exception ignored) {
        }
        return "HTTP " + status + ": " + respStr;
    }

    public static class ImageResult {
        public final String imageUrl;
        public final String rawJson;
        public final long durationMs;

        public ImageResult(String imageUrl, String rawJson, long durationMs) {
            this.imageUrl = imageUrl;
            this.rawJson = rawJson;
            this.durationMs = durationMs;
        }
    }

    public static class ArkException extends RuntimeException {
        public ArkException(String msg) {
            super(msg);
        }

        public ArkException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
