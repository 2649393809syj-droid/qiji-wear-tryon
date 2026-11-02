package com.qj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

@Component
public class DownloadConfig {
    private static final Logger log = LoggerFactory.getLogger(DownloadConfig.class);

    private static final String PDF_PATH_LINUX = "/home/qj/files/report/";
    private static final String PDF_PATH_WINDOWS = "D:\\path\\to\\save\\";
    private static final String URL_PREFIX_DEFAULT = "https://qjapiv2.foxcodes.cn/pages/report/report.html?openid=";
    private static final String FILE_TYPE = "pdf";
    private static final String COMMAND_LINUX = "wkhtmltopdf";
    private static final String COMMAND_WINDOWS = "wkhtmltopdf.exe";

    private Properties props;
    private boolean isWindows;

    public DownloadConfig() {
        this.props = new Properties();
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        try {
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("config/Download.setting");
            if (is == null) {
                log.error("Cannot find the config file.");
            } else {
                props.load(new InputStreamReader(is, "UTF-8"));
                log.info("成功加载Download.setting配置文件");
            }
        } catch (IOException e) {
            log.error("加载Download.setting配置文件失败", e);
        }
    }

    /**
     * 获取生成PDF的命令（路径已标准化）
     */
    public String getCommand(String openid) {
        String command = isWindows ? getWindowsCommand() : getLinuxCommand();
        String url = getUrlPrefix() + openid;
        String pdfPath = getPath(openid);
        String cmd = command + " --enable-local-file-access " + url + " " + pdfPath;
        log.debug("[DownloadConfig] 生成PDF命令: {}", cmd);
        return cmd;
    }

    /**
     * 获取PDF文件标准化绝对路径（支持相对路径自动转换）
     */
    public String getPath(String openid) {
        String rawPath = isWindows ? getPdfPathWindows() : getPdfPathLinux();
        String fileName = openid + "." + FILE_TYPE;
        File dir = new File(rawPath);
        String absDir;
        if (!dir.isAbsolute()) {
            // 相对路径，基于项目根目录
            absDir = new File(System.getProperty("user.dir"), rawPath).getAbsolutePath();
            log.info("[DownloadConfig] 使用相对路径，已转换为绝对路径: {}", absDir);
        } else {
            absDir = rawPath;
        }
        // 确保目录存在
        File absDirFile = new File(absDir);
        if (!absDirFile.exists()) {
            boolean created = absDirFile.mkdirs();
            if (created) {
                log.info("[DownloadConfig] 自动创建PDF保存目录: {}", absDir);
            }
        }
        String fullPath = absDir + (absDir.endsWith(File.separator) ? "" : File.separator) + fileName;
        log.debug("[DownloadConfig] PDF文件绝对路径: {}", fullPath);
        return fullPath;
    }

    /**
     * 获取Windows环境下wkhtmltopdf命令路径
     */
    public String getWindowsCommand() {
        // 首先检查配置文件中是否指定了路径
        String configuredPath = props.getProperty("wkhtmltopdf.path.windows");
        if (configuredPath != null && !configuredPath.isEmpty()) {
            File file = new File(configuredPath);
            if (file.exists()) {
                log.info("使用配置文件中指定的wkhtmltopdf路径: {}", configuredPath);
                return configuredPath;
            } else {
                log.warn("配置文件中指定的wkhtmltopdf路径不存在: {}", configuredPath);
            }
        }

        // 尝试几个常见的wkhtmltopdf安装路径
        String[] possiblePaths = {
                "D:\\wkhtmltopdf\\bin\\wkhtmltopdf.exe",
                "D:\\wkhtml\\wkhtmltopdf\\bin\\wkhtmltopdf.exe",
                "C:\\Program Files\\wkhtmltopdf\\bin\\wkhtmltopdf.exe",
                "C:\\Program Files (x86)\\wkhtmltopdf\\bin\\wkhtmltopdf.exe"
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                log.info("检测到wkhtmltopdf路径: {}", path);
                return path;
            }
        }

        // 如果没有找到，返回默认路径
        log.warn("未检测到wkhtmltopdf路径，使用默认路径");
        return "D:\\wkhtmltopdf\\bin\\wkhtmltopdf.exe";
    }

    /**
     * 获取Linux环境下wkhtmltopdf命令路径
     */
    public String getLinuxCommand() {
        // 首先检查配置文件中是否指定了路径
        String configuredPath = props.getProperty("wkhtmltopdf.path.linux");
        if (configuredPath != null && !configuredPath.isEmpty()) {
            File file = new File(configuredPath);
            if (file.exists()) {
                log.info("使用配置文件中指定的Linux wkhtmltopdf路径: {}", configuredPath);
                return configuredPath;
            } else {
                log.warn("配置文件中指定的Linux wkhtmltopdf路径不存在: {}", configuredPath);
            }
        }

        // 尝试几个常见的Linux wkhtmltopdf安装路径
        String[] possiblePaths = {
                "/usr/bin/wkhtmltopdf",
                "/usr/local/bin/wkhtmltopdf",
                "/opt/wkhtmltopdf/bin/wkhtmltopdf"
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                log.info("检测到Linux wkhtmltopdf路径: {}", path);
                return path;
            }
        }

        // 如果没有找到，返回默认路径
        log.warn("未检测到Linux wkhtmltopdf路径，使用默认路径");
        return COMMAND_LINUX;
    }

    /**
     * 获取Windows环境下PDF保存路径（支持配置文件和相对路径）
     */
    private String getPdfPathWindows() {
        String path = props.getProperty("path.windows", PDF_PATH_WINDOWS);
        log.info("[DownloadConfig] Windows PDF保存路径: {}", path);
        return path;
    }

    /**
     * 获取Linux环境下PDF保存路径（支持配置文件和相对路径）
     */
    private String getPdfPathLinux() {
        String path = props.getProperty("path", PDF_PATH_LINUX);
        log.info("[DownloadConfig] Linux PDF保存路径: {}", path);
        return path;
    }

    /**
     * 获取URL前缀（支持配置文件）
     */
    public String getUrlPrefix() {
        String url = props.getProperty("url", URL_PREFIX_DEFAULT);
        log.info("[DownloadConfig] URL前缀: {}", url);
        return url;
    }

    /**
     * 获取JavaScript延迟时间（毫秒）
     */
    public int getJavaScriptDelay() {
        String key = isWindows ? "javascript.delay.windows" : "javascript.delay.linux";
        String defaultValue = isWindows ? "1500" : "2000";
        return Integer.parseInt(props.getProperty(key, defaultValue));
    }

    /**
     * 获取操作系统类型
     */
    public boolean isWindows() {
        return isWindows;
    }
}