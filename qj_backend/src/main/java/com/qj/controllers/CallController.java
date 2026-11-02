package com.qj.controllers;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.config.DownloadConfig;
import com.qj.services.common.ServiceDispatch;
import com.qj.services.TestReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import com.qj.entities.AgentResponse;
import java.util.Map;

@RestController()
@RequestMapping("/qj")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    @Autowired
    private com.qj.services.AgentService agentService;

    @Autowired
    private com.qj.services.AdminAgentService adminAgentService;

    @Autowired
    private TestReportService testReportService;

    @Autowired
    private DownloadConfig downloadConfig;

    @CrossOrigin
    @PostMapping("/callfunc")
    JSONObject callDispatch(@RequestBody String sBody,
            @RequestHeader("X-Authorization") String strJWT,
            @RequestHeader("X-Function-Name") String funcName,
            @RequestHeader("X-Action") String action,
            @RequestHeader("X-Mysql-Excludes") String mysql_excludes,
            @RequestHeader("X-Module-Name") String moduleName,
            @RequestHeader("X-Function-Code") int funcCode) {
        JSONObject result = new JSONObject();
        try {
            log.info("收到原始请求体: {}", sBody);
            JSONObject params = JSONUtil.parseObj(sBody);
            log.info("解析后参数: {}", params.toString());
            JSONObject res = ServiceDispatch.dispatch(
                    funcCode,
                    funcName,
                    action,
                    moduleName,
                    params,
                    mysql_excludes);
            return res;
        } catch (Exception e) {
            result.set("errMsg", e.getMessage());
            result.set("memo", "可能数据不规范");
            return result;

        }

    }

    @PostMapping("/upload")
    @CrossOrigin
    JSONObject upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Authorization") String strJWT,
            @RequestHeader("X-Function-Name") String funcName,
            @RequestHeader("X-Function-Code") int funcCode) {
        return null;
    }

    @CrossOrigin
    @RequestMapping("/download")
    ResponseEntity download(
            @RequestParam("file_name") String fileName,
            @RequestParam("jwt") String jwt) {
        return null;
    }

    @CrossOrigin
    @PostMapping("/canApplyForAgent")
    public JSONObject canApplyForAgent(@RequestBody String sBody) {
        return agentService.canApplyForAgentService(sBody);
    }

    /**
     * 获取用户行为列表（管理端）
     */
    @CrossOrigin
    @PostMapping("/getUserBehaviorList")
    public JSONObject getUserBehaviorList(@RequestBody String sBody) {
        return adminAgentService.getUserBehaviorListService(sBody);
    }

    /**
     * 获取推广关系列表（管理端）
     */
    @CrossOrigin
    @PostMapping("/getAgentUserRelationList")
    public JSONObject getAgentUserRelationList(@RequestBody String sBody) {
        return adminAgentService.getAgentUserRelationListService(sBody);
    }

    /**
     * 取消代理资格（管理端）
     */
    @CrossOrigin
    @PostMapping("/cancelAgent")
    public JSONObject cancelAgent(@RequestBody String sBody) {
        try {
            JSONObject reqJson = JSONUtil.parseObj(sBody);
            String openid = reqJson.getStr("openid");

            if (StrUtil.isBlank(openid)) {
                JSONObject errorResult = new JSONObject();
                errorResult.set("errCode", 1);
                errorResult.set("errMsg", "参数错误：openid不能为空");
                return errorResult;
            }

            AgentResponse response = adminAgentService.cancelAgent(openid);

            JSONObject result = new JSONObject();
            result.set("errCode", response.isSuccess() ? 0 : 1);
            result.set("errMsg", response.getMessage());
            result.set("result", response.getData());

            return result;

        } catch (Exception e) {
            log.error("取消代理资格异常", e);
            JSONObject errorResult = new JSONObject();
            errorResult.set("errCode", 1);
            errorResult.set("errMsg", "取消代理资格异常：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 生成PDF报告（Windows环境）
     */
    @CrossOrigin
    @GetMapping("/report/check-wkhtmltopdf")
    public JSONObject checkWkhtmltopdf() {
        return testReportService.checkWkhtmltopdf();
    }

    /**
     * 获取渲染后的HTML内容（用于前端查看报告）
     * 只返回HTML内容，不生成PDF文件
     */
    @CrossOrigin
    @PostMapping("/report/genInWin")
    public JSONObject generateReportInWin(@RequestParam String htmlFilePath) {
        return testReportService.getRenderedHtmlByUrl(htmlFilePath);
    }

    @CrossOrigin
    @PostMapping("/report/gen")
    public JSONObject generateReport(@RequestBody String sBody) {
        return testReportService.generateReportService(sBody);
    }

    /**
     * 下载PDF报告（本地）
     */
    @CrossOrigin
    @GetMapping("/report/download")
    public ResponseEntity<byte[]> downloadReport(@RequestParam("openid") String openid,
            @RequestParam("nickName") String nickName) {
        return testReportService.downloadPdfWithQualification(openid, nickName);
    }

    /**
     * 检查服务器上的PDF文件是否存在
     */
    @CrossOrigin
    @GetMapping("/report/check-pdf")
    public JSONObject checkPdfExists(@RequestParam("openid") String openid) {
        return testReportService.checkPdfExistsService(openid);
    }

    /**
     * 检查用户资格并自动更新buy_report字段
     * 用于用户在进入报告页面时自动获得权限
     */
    @CrossOrigin
    @GetMapping("/report/checkAndUpdateQualification")
    public JSONObject checkAndUpdateQualification(@RequestParam("openid") String openid) {
        return testReportService.checkAndUpdateQualification(openid);
    }
}
