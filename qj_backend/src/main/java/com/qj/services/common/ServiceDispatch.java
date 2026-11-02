package com.qj.services.common;

import cn.hutool.json.JSONObject;
import cn.hutool.setting.Setting;
import com.qj.db.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

public class ServiceDispatch {
    private static final Logger log = LoggerFactory.getLogger(ServiceDispatch.class);

    /**
     *
     * @param funcCode       功能号 用来进行权限控制，对应功能名
     * @param funcName       功能名（用来快速获得sql）
     * @param action         本次调用的性质，分为 retrieve modify customize
     * @param moduleName     sql所在的模块名称 比如qc_analysis
     * @param param          参数
     * @param mysql_excludes 在mssql场景下排除sql setting中的哪些行，比如本次访问只更新部分行
     * @return
     */
    public static JSONObject dispatch(int funcCode, String funcName, String action, String moduleName,
            JSONObject param, String mysql_excludes) {
        try {
            log.info("收到请求 - 功能码: {}, 功能名: {}, 动作: {}, 模块: {}", funcCode, funcName, action, moduleName);
            log.debug("请求参数: {}", param);
            System.out.println("[ServiceDispatch] param: " + param);

            //
            Set<String> setExcludes = new HashSet<>();
            Setting setting = DBConfig.getDbSetting();
            String[] exs = mysql_excludes.split(",");
            ;
            if (setting.getStr("db_env").equals("pgsql")) {
                // exs = pgsql_excludes.split(",");

            }

            for (String s : exs) {
                setExcludes.add(s);
            }
            // retrieve modify 统一为crud服务
            // customize 根据功能号调用

            // 如果是自定义处理的功能码，交给FuncDispatch处理
            if ("customize".equals(action)
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4011_AGENT_GET_USER_IDENTITY
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4012_AGENT_APPLY_SUCCESS
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4014_AGENT_GET_PROMOTION_INFO
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4022_ADMIN_APPROVE_AGENT
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4023_ADMIN_REJECT_AGENT
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4021_ADMIN_GET_AGENT_LIST
                    || funcCode == com.qj.consts.FunctionCodes.F_301_4060_AGENT_CONFIG_LIST
            ) {
                log.info("使用自定义处理方式（FuncDispatch）");
                return FuncDispatch.dispatch(funcCode, funcName, moduleName, param,
                        mysql_excludes == null ? null : new HashSet<>(Arrays.asList(mysql_excludes.split(","))));
            }

            // 否则使用通用CRUD处理
            log.info("使用通用CRUD处理方式");
            JSONObject result = null;
            switch (action.toLowerCase()) {
                case "query":
                    System.out.println("[ServiceDispatch] 传递给CRUDService.exeRetrieve的param: " + param);
                    result = CRUDService.exeRetrieve(funcCode, funcName, moduleName, param,
                            mysql_excludes == null ? null : new HashSet<>(Arrays.asList(mysql_excludes.split(","))));
                    break;
                case "create":
                case "update":
                case "delete":
                    System.out.println("[ServiceDispatch] 传递给CRUDService.exeModifyOrDelete的param: " + param);
                    result = CRUDService.exeModifyOrDelete(funcCode, funcName, moduleName, param,
                            mysql_excludes == null ? null : new HashSet<>(Arrays.asList(mysql_excludes.split(","))));
                    break;
                default:
                    log.error("未知的动作类型: {}", action);
                    return new JSONObject().set("errCode", 2).set("errMsg", "功能未定义");
            }
            return result;
        } catch (Exception e) {
            log.error("请求处理失败", e);
            return new JSONObject().set("errCode", 500).set("errMsg", "服务器错误: " + e.getMessage());
        }
    }
}
