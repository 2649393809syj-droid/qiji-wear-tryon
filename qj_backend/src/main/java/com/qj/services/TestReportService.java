package com.qj.services;

import cn.hutool.db.Entity;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.config.DownloadConfig;
import com.qj.db.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.microsoft.playwright.*;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.qj.entities.AgentResponse;

@Service
public class TestReportService {

    private static final Logger log = LoggerFactory.getLogger(TestReportService.class);

    @Autowired
    private DownloadConfig downloadConfig;

    @Autowired
    private AgentService agentService;

    @Autowired
    private BrowserContext context;



    public JSONObject generateReportService(String sBody) {
        JSONObject result = new JSONObject();
        try {
            JSONObject reqJson = JSONUtil.parseObj(sBody);
            String openid = reqJson.getStr("openid");
            boolean forceRegenerate = reqJson.getBool("forceRegenerate", false);

            if (openid == null || openid.isEmpty()) {
                result.set("success", false);
                result.set("message", "参数错误：openid不能为空");
                result.set("errCode", 400);
                return result;
            }

            String pdfFilePath = downloadConfig.getPath(openid);
            String htmlFilePath = downloadConfig.getUrlPrefix() + openid;

            boolean ok = generateReportInWin(htmlFilePath, pdfFilePath, forceRegenerate);
            File pdfFile = new File(pdfFilePath);

            result.set("success", ok);
            result.set("openid", openid);
            result.set("pdfPath", pdfFilePath);
            result.set("exists", pdfFile.exists());
            result.set("size", pdfFile.exists() ? pdfFile.length() : 0);
            result.set("message", ok ? "PDF生成成功" : "PDF生成失败");
            result.set("errCode", ok ? 0 : 500);
            return result;
        } catch (Exception e) {
            log.error("生成PDF报告异常", e);
            result.set("success", false);
            result.set("message", "生成PDF报告异常: " + e.getMessage());
            result.set("errCode", 500);
            return result;
        }
    }

    public JSONObject checkPdfExistsService(String openid) {
        JSONObject result = new JSONObject();
        try {
            if (openid == null || openid.isEmpty()) {
                result.set("success", false);
                result.set("message", "参数错误：openid不能为空");
                result.set("errCode", 400);
                return result;
            }

            String pdfFilePath = downloadConfig.getPath(openid);
            File file = new File(pdfFilePath);
            boolean exists = file.exists() && file.length() >= 1000;

            result.set("success", true);
            result.set("openid", openid);
            result.set("pdfPath", pdfFilePath);
            result.set("exists", exists);
            result.set("size", file.exists() ? file.length() : 0);
            result.set("message", exists ? "PDF文件存在" : "PDF文件不存在");
            result.set("errCode", 0);
            return result;
        } catch (Exception e) {
            log.error("检查PDF文件是否存在异常", e);
            result.set("success", false);
            result.set("message", "检查PDF文件是否存在异常: " + e.getMessage());
            result.set("errCode", 500);
            return result;
        }
    }

    /**
     * 检查wkhtmltopdf配置
     */
    public JSONObject checkWkhtmltopdf() {
        JSONObject result = new JSONObject();
        try {
            boolean isWindows = downloadConfig.isWindows();
            String wkhtmltopdfPath = isWindows ? downloadConfig.getWindowsCommand() : "wkhtmltopdf";
            File file = new File(wkhtmltopdfPath);
            result.set("success", true);
            result.set("isWindows", isWindows);
            result.set("wkhtmltopdfPath", wkhtmltopdfPath);
            result.set("exists", file.exists());
            result.set("workingDirectory",
                    file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : "N/A");
            result.set("pdfPathForOpenid", downloadConfig.getPath("test-openid"));
            result.set("osName", System.getProperty("os.name"));
            result.set("osVersion", System.getProperty("os.version"));
            result.set("userDir", System.getProperty("user.dir"));
        } catch (Exception e) {
            log.error("检查wkhtmltopdf配置失败", e);
            result.set("success", false);
            result.set("message", "检查wkhtmltopdf配置失败：" + e.getMessage());
            result.set("errCode", 500);
            result.set("errMsg", e.getMessage());
        }
        return result;
    }


    /**
     * 生成PDF报告（支持缓存机制和强制重新生成）
     */
    public boolean generateReportInWin(String htmlFilePath, String pdfFilePath, boolean forceRegenerate) {
        try {
            // 清理旧的临时文件
            cleanupOldTempFiles();

            // 从htmlFilePath中提取openid
            String openid = extractOpenidFromUrl(htmlFilePath);
            if (openid == null || openid.isEmpty()) {
                log.error("无法从URL中提取openid: {}", htmlFilePath);
                return false;
            }

            log.info("从URL提取到openid: {}", openid);

            // 检查PDF文件是否已存在（除非强制重新生成）
            File pdfFile = new File(pdfFilePath);
            if (!forceRegenerate && pdfFile.exists() && pdfFile.length() > 1000) {
                log.info("PDF文件已存在，直接返回: {} (文件大小: {} bytes)", pdfFilePath, pdfFile.length());
                return true;
            }

            if (forceRegenerate && pdfFile.exists()) {
                log.info("强制重新生成PDF，删除现有文件: {}", pdfFilePath);
                pdfFile.delete();
            }

            log.info("开始生成新的PDF文件");

            // 调用getUserReportInfo获取用户数据
            JSONObject userReportInfo = getUserReportInfo(openid);
            if (!userReportInfo.getBool("success", false)) {
                log.error("获取用户报告数据失败: {}", userReportInfo.getStr("message"));
                return false;
            }

            log.info("成功获取用户报告数据，开始渲染页面");

            // 使用统一渲染方法生成PDF专用HTML文件
            log.info("开始渲染页面用于PDF生成");
            String renderedHtmlPath = (String) renderHtml(openid, true, true);
            if (renderedHtmlPath == null) {
                log.error("页面渲染失败");
                return false;
            }

            log.info("页面渲染完成，开始生成PDF");

            // 根据操作系统自动选择PDF生成方法
            boolean isWindows = downloadConfig.isWindows();
            log.info("检测到操作系统: {}", isWindows ? "Windows" : "Linux");

            if (isWindows) {
                return generatePdfWithWkhtmltopdf(renderedHtmlPath, pdfFilePath, true);
            } else {
                return generatePdfWithWkhtmltopdf(renderedHtmlPath, pdfFilePath, false);
            }
        } catch (Exception e) {
            log.error("生成PDF报告异常", e);
            return false;
        }
    }

    /**
     * 使用wkhtmltopdf生成PDF
     */
    private boolean generatePdfWithWkhtmltopdf(String renderedHtmlPath, String pdfFilePath, boolean isWindows) {
        try {
            // 根据操作系统获取wkhtmltopdf路径
            String wkhtmltopdfPath = isWindows ? downloadConfig.getWindowsCommand() : downloadConfig.getLinuxCommand();

            if (wkhtmltopdfPath == null || wkhtmltopdfPath.trim().isEmpty()) {
                log.error("wkhtmltopdf路径未配置或为空，操作系统: {}", isWindows ? "Windows" : "Linux");
                return false;
            }

            log.info("使用wkhtmltopdf路径: {}", wkhtmltopdfPath);

            // 获取临时HTML文件的绝对路径
            File tempHtmlFile = new File(renderedHtmlPath);
            String absoluteHtmlPath = tempHtmlFile.getAbsolutePath();

            log.info("使用HTML文件路径: {}", absoluteHtmlPath);

            // 构建文件URL，根据操作系统处理路径
            String fileUrl;
            if (isWindows) {
                fileUrl = "file:///" + absoluteHtmlPath.replace("\\", "/");
            } else {
                fileUrl = "file://" + absoluteHtmlPath;
            }

            // 构建wkhtmltopdf命令参数，针对Linux环境优化
            List<String> command = new ArrayList<>();
            command.add(wkhtmltopdfPath);
            command.add("--enable-local-file-access");
            command.add("--load-error-handling");
            command.add("ignore");
            command.add("--page-size");
            command.add("A4");
            command.add("--margin-top");
            command.add("10mm");
            command.add("--margin-bottom");
            command.add("10mm");
            command.add("--margin-left");
            command.add("10mm");
            command.add("--margin-right");
            command.add("10mm");
            command.add("--zoom");
            command.add("0.9");
            command.add("--print-media-type");
            command.add("--encoding");
            command.add("UTF-8");
            command.add("--disable-smart-shrinking");

            // Linux环境下的特殊优化
            if (!isWindows) {
                log.info("Linux环境，添加中文字体优化参数");
                command.add("--javascript-delay");
                command.add("10000"); // 增加等待时间
                command.add("--no-stop-slow-scripts");
                command.add("--debug-javascript");
                // 添加字体相关参数
                command.add("--minimum-font-size");
                command.add("12");
                // 图片加载优化
                command.add("--load-error-handling");
                command.add("ignore");
                command.add("--load-media-error-handling");
                command.add("ignore");
            } else {
                command.add("--javascript-delay");
                command.add("5000");
            }

            command.add(fileUrl);
            command.add(pdfFilePath);

            ProcessBuilder pb = new ProcessBuilder(command);

            // 设置工作目录（如果wkhtmltopdf有父目录的话）
            File wkhtmltopdfFile = new File(wkhtmltopdfPath);
            if (wkhtmltopdfFile.getParent() != null) {
                pb.directory(new File(wkhtmltopdfFile.getParent()));
            }

            pb.redirectErrorStream(true); // 合并标准输出和错误输出
            Process process = pb.start();

            // 捕获输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            // 打印输出日志
            log.info("wkhtmltopdf output:\n" + output);
            log.info("wkhtmltopdf exitCode: {}", exitCode);

            // 检查文件
            File pdfFile = new File(pdfFilePath);
            boolean success = exitCode == 0 && pdfFile.exists() && pdfFile.length() >= 1000;

            // 详细的调试信息
            if (pdfFile.exists()) {
                log.info("PDF文件已生成: {}, 大小: {} bytes", pdfFilePath, pdfFile.length());
            } else {
                log.error("PDF文件未生成: {}", pdfFilePath);
            }

            // 如果是Linux环境且生成失败，提供额外的调试信息
            if (!isWindows && !success) {
                log.error("Linux环境PDF生成失败，请检查:");
                log.error("1. 中文字体是否安装: fc-list :lang=zh");
                log.error("2. wkhtmltopdf版本: wkhtmltopdf --version");
                log.error("3. HTML内容是否包含中文数据");
                log.error("4. 使用的HTML URL: {}", fileUrl);
            }

            // 清理临时HTML文件
            try {
                if (tempHtmlFile.exists()) {
                    boolean deleted = tempHtmlFile.delete();
                    log.info("临时HTML文件清理: {} - {}", tempHtmlFile.getName(), deleted ? "成功" : "失败");
                }
            } catch (Exception e) {
                log.warn("清理临时HTML文件失败: {}", tempHtmlFile.getName(), e);
            }

            if (success) {
                log.info("PDF生成成功: {}", pdfFilePath);
                return true;
            } else {
                log.error("PDF生成失败，exitCode=" + exitCode + "，文件大小=" + (pdfFile.exists() ? pdfFile.length() : 0));
                return false;
            }
        } catch (Exception e) {
            log.error("使用wkhtmltopdf生成PDF异常，操作系统: {}", isWindows ? "Windows" : "Linux", e);
            return false;
        }
    }



    /**
     * 使用Playwright获取页面内容
     */
    // 测试网络连接
    private boolean testNetworkConnection(String url) {
        try {
            log.info("测试网络连接: {}", url);
            java.net.URL testUrl = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) testUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(10000); // 10秒读取超时

            int responseCode = connection.getResponseCode();
            log.info("网络连接测试结果: HTTP {}", responseCode);

            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            log.error("网络连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    private String getPageContentWithPlaywright(String url, int waitTimeSeconds) {
        Page page = null;
        try {
            log.info("访问网页: {}", url);

            // 先测试网络连接
            if (!testNetworkConnection(url)) {
                throw new RuntimeException("网络连接测试失败，无法访问目标URL");
            }

            // 检查并确保 context 可用
            ensureContextAvailable();

            page = context.newPage();

            // 设置更长的超时时间
            page.setDefaultTimeout(120000); // 2分钟

            // 使用更宽松的导航选项
            Page.NavigateOptions navigateOptions = new Page.NavigateOptions()
                .setTimeout(120000); // 2分钟超时

            log.info("开始导航到页面...");

            // 尝试导航，如果失败则重试
            boolean navigationSuccess = false;
            int maxRetries = 3;
            Exception lastException = null;

            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    if (retry > 0) {
                        log.info("重试导航 ({}/{})", retry + 1, maxRetries);
                        Thread.sleep(2000); // 等待2秒后重试
                    }

                    page.navigate(url, navigateOptions);
                    navigationSuccess = true;
                    log.info("页面导航成功");
                    break;

                } catch (Exception e) {
                    lastException = e;
                    log.warn("导航失败 (尝试 {}/{}): {}", retry + 1, maxRetries, e.getMessage());

                    if (retry < maxRetries - 1) {
                        // 如果不是最后一次尝试，重新创建页面
                        try {
                            page.close();
                        } catch (Exception closeEx) {
                            log.debug("关闭页面时出错: {}", closeEx.getMessage());
                        }

                        try {
                            // 重新检查 context 状态
                            ensureContextAvailable();
                            page = context.newPage();
                            page.setDefaultTimeout(120000);
                        } catch (Exception newPageEx) {
                            log.error("重新创建页面失败: {}", newPageEx.getMessage());
                            // 如果重新创建页面失败，直接跳出重试循环
                            break;
                        }
                    }
                }
            }

            if (!navigationSuccess) {
                log.error("页面导航最终失败，已尝试{}次", maxRetries);
                throw lastException;
            }

            // 等待页面基本加载完成
            page.waitForLoadState();
            log.info("页面基本加载完成");

            // 等待数据加载完成的标志
            try {
                log.info("开始等待页面数据加载...");

                // 等待jQuery加载完成
                try {
                    page.waitForFunction("() => typeof $ !== 'undefined'");
                    log.info("jQuery已加载完成");
                } catch (Exception e) {
                    log.warn("等待jQuery加载超时: {}", e.getMessage());
                }

                // 等待AJAX请求完成并且用户名称被填充
                try {
                    page.waitForFunction("() => {" +
                            "const nickName = document.querySelector('#nickName');" +
                            "return nickName && nickName.textContent.trim() !== '';" +
                            "}");
                    log.info("检测到用户名称已填充");
                } catch (Exception e1) {
                    log.warn("等待用户名称填充超时: {}", e1.getMessage());
                }

                // 等待体型数据被填充
                try {
                    page.waitForFunction("() => {" +
                            "const shape = document.querySelector('#shape');" +
                            "return shape && shape.textContent.trim() !== '';" +
                            "}");
                    log.info("检测到体型数据已填充");
                } catch (Exception e3) {
                    log.warn("等待体型数据填充超时: {}", e3.getMessage());
                }

                // 等待所有关键数据加载完成
                try {
                    page.waitForFunction("() => {" +
                            "const nickName = document.querySelector('#nickName');" +
                            "const shape = document.querySelector('#shape');" +
                            "return nickName && nickName.textContent.trim() !== '' && " +
                            "shape && shape.textContent.trim() !== '';" +
                            "}");
                    log.info("所有关键数据已加载完成");

                    // 数据加载完成后，等待更长时间确保渲染完成
                    page.waitForTimeout(5000);
                    log.info("等待数据渲染完成");
                } catch (Exception e4) {
                    log.warn("等待关键数据加载超时，使用备用等待策略: {}", e4.getMessage());
                    // 备用策略：等待更长时间确保数据加载
                    page.waitForTimeout(8000);
                    log.info("使用备用等待策略完成");
                }

            } catch (Exception e) {
                log.warn("等待数据加载过程中出现异常: {}", e.getMessage());
                // 如果等待过程出现异常，使用默认等待时间
                page.waitForTimeout(waitTimeSeconds * 1000);
            }

            // 检查页面数据是否加载成功
            try {
                String nickNameText = page.textContent("#nickName");
                String shapeText = page.textContent("#shape");
                log.info("页面数据检查 - 用户名: [{}], 体型: [{}]", nickNameText, shapeText);

                if (nickNameText == null || nickNameText.trim().isEmpty()) {
                    log.warn("⚠️ 用户名数据未加载");
                }
                if (shapeText == null || shapeText.trim().isEmpty()) {
                    log.warn("⚠️ 体型数据未加载");
                }
            } catch (Exception e) {
                log.warn("检查页面数据时出错: {}", e.getMessage());
            }

            String content = page.content();
            log.info("成功获取网页内容，长度: {}", content.length());

            return content;
        } catch (Exception e) {
            log.error("获取网页内容失败: {}", url, e);
            return null;
        } finally {
            if (page != null) {
                page.close();
            }
        }
    }

    /**
     * 确保 BrowserContext 可用
     * 如果 context 已关闭或无效，抛出异常让调用者处理
     */
    private void ensureContextAvailable() {
        try {
            // 检查 context 是否可用
            if (context == null) {
                throw new RuntimeException("BrowserContext 为 null，请检查 Spring 配置");
            }

            if (isContextClosed()) {
                throw new RuntimeException("BrowserContext 已关闭，请重启应用程序");
            }
        } catch (Exception e) {
            log.error("BrowserContext 不可用: {}", e.getMessage());
            throw new RuntimeException("BrowserContext 不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 检查 context 是否已关闭
     */
    private boolean isContextClosed() {
        try {
            // 尝试获取 context 的页面列表来检查是否可用
            context.pages();
            return false;
        } catch (Exception e) {
            log.debug("Context 已关闭或不可用: {}", e.getMessage());
            return true;
        }
    }



    /**
     * 针对Linux环境优化HTML内容，确保中文字体正确显示
     */
    private String optimizeHtmlForLinuxPdf(String htmlContent) {
        try {
            log.info("开始优化HTML内容以适配Linux PDF生成");

            // 添加强制中文字体CSS（使用系统字体名称，不使用@font-face）
            String fontCss = "<style type=\"text/css\">" +
                    "* { " +
                    "font-family: 'WenQuanYi Zen Hei', 'Noto Sans CJK SC', 'AR PL UMing CN', 'Droid Sans Fallback', serif !important; "
                    +
                    "} " +
                    "body, div, h1, h2, h3, h4, p, span, .paragraph { " +
                    "font-family: 'WenQuanYi Zen Hei', 'Noto Sans CJK SC', 'AR PL UMing CN', 'Droid Sans Fallback', serif !important; "
                    +
                    "font-size: 14px !important; " +
                    "line-height: 1.8 !important; " +
                    "color: #333 !important; " +
                    "} " +
                    "#nickName, #shape, #style, #skinColor { " +
                    "font-family: 'WenQuanYi Zen Hei', 'Noto Sans CJK SC', 'AR PL UMing CN', serif !important; " +
                    "font-weight: bold !important; " +
                    "color: #000 !important; " +
                    "} " +
                    "h1, h2, h3, h4 { " +
                    "font-family: 'WenQuanYi Zen Hei', 'Noto Sans CJK SC', serif !important; " +
                    "font-weight: bold !important; " +
                    "} " +
                    "</style>";

            // 在</head>标签前插入字体CSS
            if (htmlContent.contains("</head>")) {
                htmlContent = htmlContent.replace("</head>", fontCss + "</head>");
            } else if (htmlContent.contains("<body")) {
                htmlContent = htmlContent.replace("<body", fontCss + "<body");
            } else {
                // 如果没有找到合适的位置，在开头添加
                htmlContent = fontCss + htmlContent;
            }

            // 修复图片路径问题
            String baseUrl = getBaseUrlFromConfig();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                // 将相对路径转换为绝对路径
                htmlContent = htmlContent.replaceAll(
                        "src=\"../../img/report/",
                        "src=\"" + baseUrl + "/img/report/");
                log.info("已将图片相对路径转换为绝对路径，baseUrl: {}", baseUrl);
            }

            log.info("HTML内容优化完成，添加了Linux中文字体支持和图片路径修复");

            // 调试：输出优化后的HTML内容片段
            if (htmlContent.length() > 500) {
                log.debug("优化后的HTML内容片段: {}", htmlContent.substring(0, 500) + "...");
            } else {
                log.debug("优化后的HTML内容: {}", htmlContent);
            }

            return htmlContent;

        } catch (Exception e) {
            log.warn("优化HTML内容失败，使用原始内容", e);
            return htmlContent;
        }
    }

    /**
     * 从配置文件中获取baseUrl
     */
    private String getBaseUrlFromConfig() {
        try {
            // 从配置文件中读取URL，并提取baseUrl
            String fullUrl = downloadConfig.getUrlPrefix();
            if (fullUrl != null && !fullUrl.isEmpty()) {
                // 移除末尾的 ?openid= 部分
                String baseUrl = fullUrl.replace("?openid=", "");
                // 移除末尾的 /pages/report/report.html
                baseUrl = baseUrl.replace("/pages/report/report.html", "");
                log.info("从配置文件提取的baseUrl: {}", baseUrl);
                return baseUrl;
            }
        } catch (Exception e) {
            log.warn("从配置文件获取baseUrl失败，使用默认值", e);
        }

        // 默认值
        return "https://qj-api.foxcodes.cn";
    }

    /**
     * 清理旧的临时文件
     */
    private void cleanupOldTempFiles() {
        try {
            File currentDir = new File(".");
            File[] tempFiles = currentDir
                    .listFiles((dir, name) -> name.startsWith("temp_rendered_") && name.endsWith(".html"));

            if (tempFiles != null) {
                for (File file : tempFiles) {
                    // 删除超过1小时的临时文件
                    long fileAge = System.currentTimeMillis() - file.lastModified();
                    if (fileAge > 3600000) { // 1小时 = 3600000毫秒
                        boolean deleted = file.delete();
                        log.info("清理旧临时文件: {} - {}", file.getName(), deleted ? "成功" : "失败");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理旧临时文件失败", e);
        }
    }

    /**
     * 从URL中提取openid参数
     */
    public String extractOpenidFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }

            // 查找openid参数
            int openidIndex = url.indexOf("openid=");
            if (openidIndex == -1) {
                return null;
            }

            // 提取openid值
            String openidPart = url.substring(openidIndex + 7); // "openid=" 长度为7

            // 如果后面还有其他参数，需要截取到&符号
            int andIndex = openidPart.indexOf("&");
            if (andIndex != -1) {
                openidPart = openidPart.substring(0, andIndex);
            }

            return openidPart;
        } catch (Exception e) {
            log.error("提取openid失败: {}", url, e);
            return null;
        }
    }

    /**
     * 生成PDF报告（Linux环境，支持缓存机制）
     */
    public boolean generateReportInLinux(String htmlFilePath, String pdfFilePath) {
        try {
            // 从URL中提取openid
            String openid = extractOpenidFromUrl(htmlFilePath);
            if (openid == null || openid.isEmpty()) {
                log.error("无法从URL中提取openid: {}", htmlFilePath);
                return false;
            }

            log.info("从URL提取到openid: {}", openid);

            // 检查PDF文件是否已存在
            File pdfFile = new File(pdfFilePath);
            if (pdfFile.exists() && pdfFile.length() > 1000) {
                log.info("PDF文件已存在，直接返回: {} (文件大小: {} bytes)", pdfFilePath, pdfFile.length());
                return true;
            }

            log.info("PDF文件不存在或文件过小，开始生成新的PDF文件");

            // 使用配置文件中的URL
            String configUrl = downloadConfig.getUrlPrefix() + openid;
            log.info("使用配置文件中的URL: {}", configUrl);

            // 使用wkhtmltopdf生成PDF
            String wkhtmltopdfPath = downloadConfig.getLinuxCommand();
            ProcessBuilder pb = new ProcessBuilder(
                    wkhtmltopdfPath,
                    "--enable-local-file-access",
                    "--load-error-handling", "ignore",
                    "--page-size", "A4",
                    "--margin-top", "10mm",
                    "--margin-bottom", "10mm",
                    "--margin-left", "10mm",
                    "--margin-right", "10mm",
                    "--zoom", "0.9",
                    "--print-media-type",
                    "--encoding", "UTF-8",
                    "--disable-smart-shrinking",
                    configUrl,
                    pdfFilePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 捕获输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            log.info("wkhtmltopdf output:\n" + output);

            // 检查文件
            boolean success = exitCode == 0 && pdfFile.exists() && pdfFile.length() >= 1000;

            if (success) {
                log.info("PDF生成成功: {}", pdfFilePath);
                return true;
            } else {
                log.error("PDF生成失败，exitCode=" + exitCode + "，文件大小=" + (pdfFile.exists() ? pdfFile.length() : 0));
                return false;
            }
        } catch (Exception e) {
            log.error("Linux生成PDF异常", e);
            return false;
        }
    }

    /**
     * 获取客户报告详情
     */
    public JSONObject getClientReport() throws SQLException {
        try {
            // 使用DBConfig获取SQL语句
            String sql = DBConfig.getSqlStr(null, "qj_report", "F_401_5001_REPORT_GET_CLIENT_REPORT", 0);
            if (sql == null) {
                log.error("未找到客户报告详情SQL配置");
                return new JSONObject().set("success", false).set("message", "未找到SQL配置");
            }

            // 执行SQL查询
            List<Entity> entities = DBConfig.getDb(null, "qj").query(sql);
            log.info("获取到客户报告详情: {}", entities.size());
            return new JSONObject().set("success", true).set("data", entities);
        } catch (SQLException e) {
            log.error("获取客户报告详情失败", e);
            throw e;
        }
    }

    /**
     * 获取报告价格
     */
    public JSONObject getReportPrice() throws SQLException {
        try {
            // 使用DBConfig获取SQL语句
            String sql = DBConfig.getSqlStr(null, "qj_report", "F_401_5002_REPORT_GET_PRICE", 0);
            if (sql == null) {
                log.error("未找到报告价格SQL配置");
                return new JSONObject().set("success", false).set("message", "未找到SQL配置");
            }

            // 执行SQL查询
            Entity entity = DBConfig.getDb(null, "qj").queryOne(sql);
            log.info("获取到报告价格: {}", entity);
            return new JSONObject().set("success", true).set("data", entity);
        } catch (SQLException e) {
            log.error("获取报告价格失败", e);
            throw e;
        }
    }

    /**
     * 更新购买状态
     */
    public JSONObject updateBuy(String openid) throws SQLException {
        try {
            // 使用DBConfig获取SQL语句
            String sql = DBConfig.getSqlStr(null, "qj_report", "F_401_5003_REPORT_UPDATE_BUY_STATUS", 0);
            if (sql == null) {
                log.error("未找到更新购买状态SQL配置");
                return new JSONObject().set("success", false).set("message", "未找到SQL配置");
            }

            // 执行SQL更新
            int id = DBConfig.getDb(null, "qj").execute(sql, openid);

            if (id != 1) {
                log.error("用户购买状态更新失败: {}", openid);
                return new JSONObject().set("success", false).set("message", "用户更新失败");
            }
            log.info("用户购买状态更新成功: {}", openid);
            return new JSONObject().set("success", true).set("message", "用户更新成功").set("data", id);
        } catch (SQLException e) {
            log.error("更新购买状态失败", e);
            throw e;
        }
    }

    /**
     * 获取用户报告渲染数据
     */
    public JSONObject getUserReportInfo(String openid) {
        try {
            // 使用DBConfig获取SQL语句
            String sql = DBConfig.getSqlStr(null, "qj_report", "F_401_5008_REPORT_GET_USER_INFO", 0);
            if (sql == null) {
                log.error("未找到获取用户报告数据SQL配置");
                return new JSONObject().set("success", false).set("message", "未找到SQL配置");
            }

            // 执行SQL查询
            Entity entity = DBConfig.getDb(null, "qj").queryOne(sql, openid);

            if (entity == null) {
                log.warn("未找到用户报告数据: {}", openid);
                return new JSONObject().set("success", false).set("message", "未找到用户数据");
            }

            // 处理JSON字段
            JSONObject result = new JSONObject();
            result.set("openid", entity.getStr("_openid"));
            result.set("nick_name", entity.getStr("nick_name"));
            result.set("avatar_url", entity.getStr("avatar_url"));

            // 处理shape字段
            result.set("shape", entity.getStr("shape"));

            // 处理types字段（JSON字符串转数组）
            String typesStr = entity.getStr("types");
            if (typesStr != null && !typesStr.isEmpty()) {
                try {
                    result.set("types", JSONUtil.parseArray(typesStr));
                } catch (Exception e) {
                    log.warn("解析types字段失败: {}", typesStr, e);
                    result.set("types", new JSONObject());
                }
            } else {
                result.set("types", new JSONObject());
            }

            // 处理skin_color_obj字段（JSON字符串转对象）
            String skinColorObjStr = entity.getStr("skin_color_obj");
            if (skinColorObjStr != null && !skinColorObjStr.isEmpty()) {
                try {
                    result.set("skin_color_obj", JSONUtil.parseObj(skinColorObjStr));
                } catch (Exception e) {
                    log.warn("解析skin_color_obj字段失败: {}", skinColorObjStr, e);
                    result.set("skin_color_obj", new JSONObject());
                }
            } else {
                result.set("skin_color_obj", new JSONObject());
            }

            result.set("is_evaluation", entity.getInt("is_evaluation"));
            result.set("is_answer", entity.getInt("is_answer"));
            result.set("buy_report", entity.getInt("buy_report"));
            result.set("is_try", entity.getInt("is_try"));

            // 暂时设置为默认值，因为表中可能没有这些字段
            result.set("evaluation_data", new JSONObject());
            result.set("evaluation_time", null);

            log.info("获取用户报告数据成功: {}", openid);
            return new JSONObject().set("success", true).set("data", result);
        } catch (SQLException e) {
            log.error("获取用户报告数据失败", e);
            return new JSONObject().set("success", false).set("message", "数据库查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取用户报告数据异常", e);
            return new JSONObject().set("success", false).set("message", "获取数据异常: " + e.getMessage());
        }
    }

    /**
     * 下载PDF报告（资格判断+buy_report判断+本地文件读取）
     */


    public org.springframework.http.ResponseEntity<byte[]> downloadPdfWithQualification(String openid,
            String nickName) {
        try {
            // 先用AgentService.checkTestQualification判断资格
            com.qj.entities.AgentResponse qualificationResp = agentService.checkTestQualification(openid);
            if (qualificationResp != null && qualificationResp.isSuccess()) {
                Object data = qualificationResp.getData();
                int qualified = 0;
                if (data instanceof Integer) {
                    qualified = (Integer) data;
                } else if (data instanceof String) {
                    try {
                        qualified = Integer.parseInt((String) data);
                    } catch (Exception ignore) {
                    }
                } else if (data instanceof java.util.Map) {
                    Object val = ((java.util.Map<?, ?>) data).get("qualified");
                    if (val instanceof Integer)
                        qualified = (Integer) val;
                    else if (val instanceof String)
                        try {
                            qualified = Integer.parseInt((String) val);
                        } catch (Exception ignore) {
                        }
                }
                if (qualified == 1) {
                    // 有资格，检查PDF是否存在，不存在则生成
                    String pdfFilePath = downloadConfig.getPath(openid);
                    File file = new File(pdfFilePath);
                    if (!file.exists() || file.length() < 1000) {
                        log.info("PDF文件不存在或过小，开始生成PDF: {}", pdfFilePath);
                        // 生成PDF
                        String htmlFilePath = downloadConfig.getUrlPrefix() + openid;
                        boolean success = generateReportInWin(htmlFilePath, pdfFilePath, false);
                        if (!success) {
                            return org.springframework.http.ResponseEntity
                                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body("PDF生成失败".getBytes());
                        }
                        // 重新检查文件
                        file = new File(pdfFilePath);
                        if (!file.exists()) {
                            return org.springframework.http.ResponseEntity
                                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body("PDF生成后文件仍不存在".getBytes());
                        }
                    }
                    byte[] dataBytes = java.nio.file.Files.readAllBytes(file.toPath());
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);

                    // 处理中文文件名编码问题
                    String fileName = nickName + ".pdf";
                    try {
                        String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
                        headers.add("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
                    } catch (java.io.UnsupportedEncodingException e) {
                        // 如果编码失败，使用默认文件名
                        headers.setContentDispositionFormData("filename", "report.pdf");
                    }
                    return new org.springframework.http.ResponseEntity<>(dataBytes, headers,
                            org.springframework.http.HttpStatus.OK);
                }
            }
            // 旧逻辑：buy_report=1权限校验
            try {
                String sql = "select buy_report from qj_member where _openid = ? and is_del = ?";
                cn.hutool.db.Entity entity = com.qj.db.DBConfig.getDb(null, "qj").queryOne(sql, openid, 0);
                if (entity == null || entity.getInt("buy_report") != 1) {
                    return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                            .body("未购买下载功能".getBytes());
                }
            } catch (java.sql.SQLException e) {
                log.error("查询用户购买状态失败", e);
                return org.springframework.http.ResponseEntity
                        .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("查询用户购买状态失败".getBytes());
            }
            // buy_report=1也允许下载，检查PDF是否存在，不存在则生成
            String pdfFilePath = downloadConfig.getPath(openid);
            File file = new File(pdfFilePath);
            if (!file.exists() || file.length() < 1000) {
                log.info("PDF文件不存在或过小，开始生成PDF: {}", pdfFilePath);
                // 生成PDF
                String htmlFilePath = downloadConfig.getUrlPrefix() + openid;
                boolean success = generateReportInWin(htmlFilePath, pdfFilePath, false);
                if (!success) {
                    return org.springframework.http.ResponseEntity
                            .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("PDF生成失败".getBytes());
                }
                // 重新检查文件
                file = new File(pdfFilePath);
                if (!file.exists()) {
                    return org.springframework.http.ResponseEntity
                            .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("PDF生成后文件仍不存在".getBytes());
                }
            }
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);

            // 处理中文文件名编码问题
            String fileName = nickName + ".pdf";
            try {
                String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
                headers.add("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            } catch (java.io.UnsupportedEncodingException e) {
                // 如果编码失败，使用默认文件名
                headers.setContentDispositionFormData("filename", "report.pdf");
            }
            return new org.springframework.http.ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("下载PDF报告失败", e);
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("下载PDF报告失败".getBytes());
        }
    }

    /**
     * 检查用户资格并自动更新buy_report、is_try、is_answer、is_evaluation字段
     */
    public JSONObject checkAndUpdateQualification(String openid) {
        JSONObject result = new JSONObject();
        try {
            AgentResponse qualificationResp = agentService.checkReportQualification(openid);
            if (!qualificationResp.isSuccess()) {
                result.set("success", false);
                result.set("qualified", false);
                result.set("message", qualificationResp.getMessage());
                result.set("errCode", 500);
                return result;
            }
            Object data = qualificationResp.getData();
            int qualified = 0;
            if (data instanceof Integer) {
                qualified = (Integer) data;
            } else if (data instanceof String) {
                try { qualified = Integer.parseInt((String) data); } catch (Exception ignore) {}
            }
            if (qualified == 1) {
                try {
                    String updateSql = "UPDATE qj_member SET buy_report = 1, is_try = 0, is_answer = '1', is_evaluation = '0' WHERE _openid = ? AND is_del = 0";
                    com.qj.db.DBConfig.getDb(null, "qj").execute(updateSql, openid);
                    log.info("用户资格检查通过，已更新qj_member字段，openid: {}", openid);
                } catch (Exception e) {
                    log.error("更新qj_member字段失败，openid: {}", openid, e);
                }
                result.set("success", true);
                result.set("qualified", true);
                result.set("message", "资格检查通过");
                result.set("errCode", 0);
            } else {
                result.set("success", true);
                result.set("qualified", false);
                result.set("message", "无免费报告资格");
                result.set("errCode", 0);
            }
        } catch (Exception e) {
            log.error("检查资格异常，openid: {}", openid, e);
            result.set("success", false);
            result.set("qualified", false);
            result.set("message", "检查资格异常");
            result.set("errCode", 500);
        }
        return result;
    }

    /**
     * 支付后生成报告数据
     */
    public JSONObject afterPaidReport(String openid, String tradeNo, String transactionId) {
        JSONObject result = new JSONObject();
        try {
            if (openid == null || openid.isEmpty() || tradeNo == null || tradeNo.isEmpty()) {
                result.set("code", 2);
                result.set("message", "openid或tradeNo不能为空");
                return result;
            }
            result.set("code", 0);
            result.set("message", "订单处理成功");
        } catch (Exception e) {
            log.error("支付后生成报告失败", e);
            result.set("code", 1);
            result.set("message", "订单处理异常");
        }
        return result;
    }

    // ==================== HTML渲染相关方法 ====================

    /**
     * Controller专用：通过URL获取渲染后的HTML内容（用于前端展示）
     */
    public JSONObject getRenderedHtmlByUrl(String htmlFilePath) {
        try {
            String openid = extractOpenidFromUrl(htmlFilePath);
            if (openid == null || openid.isEmpty()) {
                log.error("无法从URL中提取openid: {}", htmlFilePath);
                return createErrorResult("无法从URL中提取openid", 400);
            }

            log.info("获取HTML内容用于前端展示，openid: {}", openid);
            // 调用统一渲染方法：不用于PDF，不保存文件
            return (JSONObject) renderHtml(openid, false, false);

        } catch (Exception e) {
            log.error("获取HTML内容失败", e);
            return createErrorResult("获取HTML内容异常: " + e.getMessage(), 500);
        }
    }

    /**
     * 统一的HTML渲染方法（支持多种使用场景）
     * @param openid 用户openid
     * @param forPdf 是否用于PDF生成（true=添加PDF专用CSS，false=纯HTML展示）
     * @param saveToFile 是否保存到临时文件（true=返回文件路径，false=返回HTML内容）
     * @return 根据参数返回不同格式的结果
     */
    public Object renderHtml(String openid, boolean forPdf, boolean saveToFile) {
        try {
            if (openid == null || openid.isEmpty()) {
                if (saveToFile) return null;
                return createErrorResult("openid不能为空", 400);
            }

            // 构建URL并获取原始HTML内容
            String configUrl = downloadConfig.getUrlPrefix() + openid;
            log.info("开始渲染页面内容，URL: {}, forPdf: {}, saveToFile: {}", configUrl, forPdf, saveToFile);

            String htmlContent = getPageContentWithPlaywright(configUrl, 10);
            if (htmlContent == null || htmlContent.isEmpty()) {
                if (saveToFile) return null;
                return createErrorResult("获取页面内容失败", 500);
            }

            // 处理HTML内容：路径修复、字体优化等
            String baseUrl = getBaseUrlFromConfig();
            String processedHtml = htmlContent;

            // 修复相对路径为绝对路径
            processedHtml = processedHtml.replaceAll("href=\"\\.\\./\\.\\./css/", "href=\"" + baseUrl + "/css/");
            processedHtml = processedHtml.replaceAll("src=\"\\.\\./\\.\\./app/common/js/", "src=\"" + baseUrl + "/app/common/js/");
            processedHtml = processedHtml.replaceAll("src=\"\\.\\./\\.\\./js/", "src=\"" + baseUrl + "/js/");
            processedHtml = processedHtml.replaceAll("src=\"\\.\\./\\.\\./img/", "src=\"" + baseUrl + "/img/");

            // 针对Linux环境，优化HTML内容中的字体设置
            if (!downloadConfig.isWindows()) {
                processedHtml = optimizeHtmlForLinuxPdf(processedHtml);
            }

            // 如果是PDF用途，添加PDF专用CSS
            if (forPdf) {
                processedHtml = processedHtml.replace("</head>",
                        "<link rel=\"stylesheet\" href=\"" + baseUrl + "/css/report/report-pdf.css\">\n</head>");
            }

            log.info("HTML内容渲染完成，长度: {}", processedHtml.length());

            // 根据需求返回不同格式
            if (saveToFile) {
                // 保存到临时文件并返回文件路径
                String tempHtmlPath = "temp_rendered_" + openid + ".html";
                try {
                    // 保存处理后的HTML到临时文件
                    try (FileWriter writer = new FileWriter(tempHtmlPath)) {
                        writer.write(processedHtml);
                    }

                    // 调试：保存一份HTML文件到固定位置，方便检查
                    try {
                        String debugHtmlPath = "/tmp/debug_report_" + openid + ".html";
                        try (FileWriter debugWriter = new FileWriter(debugHtmlPath, StandardCharsets.UTF_8)) {
                            debugWriter.write(processedHtml);
                            log.info("调试HTML文件已保存到: {}", debugHtmlPath);

                            // 输出HTML内容的关键信息
                            if (processedHtml.contains("直线型") || processedHtml.contains("测试")) {
                                log.info("✅ HTML内容包含中文数据");
                            } else {
                                log.warn("⚠️  HTML内容可能不包含中文数据");
                            }

                            if (processedHtml.contains("WenQuanYi") || processedHtml.contains("font-family")) {
                                log.info("✅ HTML内容包含字体设置");
                            } else {
                                log.warn("⚠️  HTML内容可能缺少字体设置");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("保存调试HTML文件失败", e);
                    }

                    // 检查文件是否生成
                    File tempFile = new File(tempHtmlPath);
                    if (tempFile.exists() && tempFile.length() > 0) {
                        log.info("页面渲染完成: {} (文件大小: {} bytes)", tempHtmlPath, tempFile.length());
                        return tempHtmlPath;
                    } else {
                        log.error("页面渲染失败，文件未生成或为空");
                        return null;
                    }
                } catch (Exception e) {
                    log.error("保存HTML文件异常", e);
                    return null;
                }
            } else {
                // 返回JSON格式的HTML内容
                JSONObject result = new JSONObject();
                result.set("success", true);
                result.set("htmlContent", processedHtml);
                result.set("message", "获取渲染内容成功");
                result.set("errCode", 0);
                return result;
            }

        } catch (Exception e) {
            log.error("HTML渲染失败，openid: {}", openid, e);
            if (saveToFile) return null;
            return createErrorResult("HTML渲染异常: " + e.getMessage(), 500);
        }
    }

    /**
     * 创建错误结果
     */
    private JSONObject createErrorResult(String message, int errCode) {
        JSONObject result = new JSONObject();
        result.set("success", false);
        result.set("message", message);
        result.set("errCode", errCode);
        return result;
    }


}