package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.utils.AccessTokenUtil;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * 云存储服务
 * 
 * @author system
 */
@Service
public class CloudStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudStorageService.class);

    /**
     * 上传文件到微信云开发
     * 
     * @param file     要上传的文件
     * @param fileName 文件名（包含路径）
     * @return 上传后的文件URL
     */
    public String uploadFile(File file, String fileName) {
        if (file == null || !file.exists()) {
            log.error("上传文件不存在");
            return null;
        }

        int maxAttempts = 3;
        long[] backoffMs = { 300L, 700L };
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String url = uploadFileToWxCloud(file, fileName);
                if (StrUtil.isNotBlank(url)) {
                    if (attempt > 1) {
                        log.info("wxCloud 上传第 {} 次重试成功：{}", attempt, fileName);
                    }
                    return url;
                }
                log.warn("wxCloud 上传第 {}/{} 次返回空 URL：{}", attempt, maxAttempts, fileName);
            } catch (Exception e) {
                lastError = e;
                log.warn("wxCloud 上传第 {}/{} 次抛错 fileName={} err={}",
                        attempt, maxAttempts, fileName, e.getMessage());
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(backoffMs[attempt - 1]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("wxCloud 上传 {} 次后仍失败 fileName={}", maxAttempts, fileName, lastError);
        return uploadFileWithHttp(file, fileName);
    }

    /**
     * 上传文件到微信云开发
     * 
     * @param file     要上传的文件
     * @param fileName 文件名
     * @return 上传后的文件URL
     */
    private String uploadFileToWxCloud(File file, String fileName) throws IOException {
        String accessToken = AccessTokenUtil.getAccessToken();
        if (StrUtil.isBlank(accessToken)) {
            log.error("获取微信AccessToken失败");
            return null;
        }

        String uploadUrl = "https://api.weixin.qq.com/tcb/uploadfile?access_token=" + accessToken;

        // 构建上传参数
        HashMap<String, Object> params = new HashMap<>(4);
        params.put("env", AccessTokenUtil.getEnv());
        params.put("path", fileName);

        // 获取上传链接
        String response = HttpUtil.post(uploadUrl, JSONUtil.toJsonStr(params), 10000);
        JSONObject linkResult = JSONUtil.parseObj(response);

        log.debug("获取上传链接结果：{}", linkResult);

        if (linkResult.isEmpty() || linkResult.getInt("errcode", -1) != 0) {
            log.error("获取微信云开发上传链接失败：{}", linkResult);
            return null;
        }

        // 获取上传所需的参数
        String signature = linkResult.getStr("authorization");
        String token = linkResult.getStr("token");
        String fileId = linkResult.getStr("cos_file_id");
        String url = linkResult.getStr("url");

        if (StrUtil.isBlank(signature) || StrUtil.isBlank(token) || StrUtil.isBlank(fileId) || StrUtil.isBlank(url)) {
            log.error("上传链接参数不完整：signature={}, token={}, fileId={}, url={}", signature, token, fileId, url);
            return null;
        }

        // 使用OkHttp上传文件
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", fileName)
                .addFormDataPart("Signature", signature)
                .addFormDataPart("x-cos-security-token", token)
                .addFormDataPart("x-cos-meta-fileid", fileId)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(multipartBody)
                .build();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .build();

        try (Response uploadResponse = okHttpClient.newCall(request).execute()) {
            if (uploadResponse.isSuccessful()) {
                // 从响应中提取URL并转换为tcb.qcloud.la域名
                String responseStr = uploadResponse.toString();
                log.debug("上传响应：{}", responseStr);

                // 提取URL并转换域名
                String resultUrl = url.substring(0, url.indexOf('/', 8)); // 获取基础URL
                resultUrl = resultUrl.replace(".cos.ap-shanghai.myqcloud.com", ".tcb.qcloud.la");
                resultUrl = resultUrl + "/" + fileName;

                log.info("文件上传到微信云开发成功：{}", resultUrl);
                return resultUrl;
            } else {
                log.error("文件上传失败，HTTP状态码：{}", uploadResponse.code());
                return null;
            }
        }
    }

    /**
     * 使用HTTP方式上传文件（备用方案）
     * 
     * @param file     要上传的文件
     * @param fileName 文件名
     * @return 上传后的文件URL
     */
    private String uploadFileWithHttp(File file, String fileName) {
        // 历史 placeholder，原本会返回伪造的 example.com URL 误导调用方。
        // 现统一返回 null，让上游（如 mirrorToCloudStorage）能正确 fallback 到原始 provider URL。
        log.warn("uploadFileWithHttp fallback 触发，未实现真实上传：file={} fileName={}",
                file == null ? null : file.getName(), fileName);
        return null;
    }

    /**
     * 上传推广海报
     * 
     * @param file   海报文件
     * @param openid 用户openid
     * @return 海报URL
     */
    public String uploadPoster(File file, String openid) {
        if (file == null || !file.exists()) {
            log.error("海报文件不存在");
            return null;
        }

        // 生成文件名，使用qrCodeData路径以保持与旧项目一致
        String fileName = "qrCodeData/" + openid + ".png";

        return uploadFile(file, fileName);
    }

    /**
     * 生成唯一文件名
     * 
     * @param originalFileName 原始文件名
     * @return 唯一文件名
     */
    public String generateUniqueFileName(String originalFileName) {
        String extension = "";
        if (StrUtil.isNotBlank(originalFileName) && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * 获取文件的完整URL
     * 
     * @param fileName 文件名
     * @return 完整URL
     */
    public String getFileUrl(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return null;
        }
        // 返回微信云开发的URL格式
        String env = AccessTokenUtil.getEnv();
        return "https://716a-" + env + "-1257602491.tcb.qcloud.la/" + fileName;
    }
}