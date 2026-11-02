package com.qj.services.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.config.MyJsonConfig;
import com.qj.db.DBConfig;

import java.util.List;
import java.util.Set;

/**
 * 所有定制sql调用
 * 为pg和ms兼容，所有sql均需在此调用
 */
public class CRUDService {

    /**
     * 为从源头防止注入，不进行sql拼接
     *
     * @param funcName 可能用于快速获得sql
     * @param funcCode 用于出错时的errCode
     * @param param    格式如下
     *                 JSONObject 格式
     *                 {
     *                 "__pgsql_fieldkeys__": "['PatientID']",
     *                 "__pgsql_fieldkeys__": "['PatientID','ID']",
     *                 "PatientID": value1,
     *                 "ID" :value2
     *
     *
     *                 }
     * @return 结果集
     *         *********类似
     *         下为sqlserver
     *         insert into QuickLis.dbo.ttt (CName)s
     *         *** output inserted.id ****
     *         values ('l8ys')
     *         的语句 调用本方法
     *         INSERT INTO users (username, email)
     *         VALUES ('john_doe', 'john@example.com') RETURNING id; --pgsql
     *         param的 __mssql_fieldkeys__ 保存参数field的名字和顺序
     *         有输出的，全部使用这个接口
     */
    public static JSONObject exeRetrieve(int funcCode, String funcName, String moduleName,
            JSONObject param, Set<String> setExcludes) {
        // 兼容 __mysql_fieldkeys__ + __mysql_values__ 参数，自动映射为普通字段
        if (param.containsKey("__mysql_fieldkeys__") && param.containsKey("__mysql_values__")) {
            JSONArray keys = param.getJSONArray("__mysql_fieldkeys__");
            JSONArray values = param.getJSONArray("__mysql_values__");
            for (int i = 0; i < keys.size(); i++) {
                param.set(keys.getStr(i), values.get(i));
            }
        }
        String env_str = DBConfig.getDbSetting().getStr("db_env");
        String sqlStr;

        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);

        }

        // "__fieldkeys__" 前端要一致
        JSONArray keys = param.getJSONArray("__" + env_str + "_fieldkeys__");

        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray ja = new JSONArray();
        Object[] obs = null;

        if (keys != null && !keys.isEmpty()) {
            for (Object s : keys) {
                String sk = s.toString();
                if (sk.startsWith("$")) {
                    String astr = param.getStr(sk);
                    JSONArray ja1 = JSONUtil.parseArray(astr);
                    String marks = StrUtil.repeatAndJoin("?", ja1.size(), ",");
                    sqlStr = sqlStr.replace(sk, marks);
                    for (Object o : ja1) {
                        ja.set(o);
                    }

                } else {

                    Object o = param.getObj(sk);
                    ja.set(o);
                }

            }

            obs = ja.toArray();

        }
        // 如果SQL没有?占位符，则不传参数数组，避免参数数量不匹配
        if (!sqlStr.contains("?")) {
            obs = null;
        }
        // 替换SQL中的${pageSize}和${offset}为实际数字
        if (param.containsKey("pageSize")) {
            sqlStr = sqlStr.replace("${pageSize}", String.valueOf(param.getInt("pageSize")));
        }
        if (param.containsKey("offset")) {
            sqlStr = sqlStr.replace("${offset}", String.valueOf(param.getInt("offset")));
        }
        Db lisDb = DBConfig.getDb(null, "qj");
        try {
            List<Entity> items = null;
            if (obs == null) {
                items = lisDb.query(sqlStr);

            } else {
                items = lisDb.query(sqlStr, obs);

            }
            if (items != null) {
                res.set("result", items);
                res.set("errCode", 0);
            } else {
                res.set("errCode", 404);
                res.set("errMsg", "相关记录不存在");
            }

        } catch (Exception e) {
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());
            res.set("result", new JSONArray());

            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());
        } finally {
            // lisDb = null;
        }

        res.set("action", "retrieve");

        return res;
    }

    /**
     * 仅返回
     * 
     * @param funcCode
     * @param funcName
     * @param moduleName
     * @param param
     * @param setExcludes
     * @return 改变的行数
     */
    public static JSONObject exeModifyOrDelete(int funcCode, String funcName, String moduleName,
            JSONObject param, Set<String> setExcludes) {
        String sqlStr;
        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);
        }

        System.out.println("Debug - Module Name: " + moduleName);
        System.out.println("Debug - Function Name: " + funcName);
        System.out.println("Debug - SQL String: " + sqlStr);

        String env_str = DBConfig.getDbSetting().getStr("db_env");
        JSONArray keys = param.getJSONArray("__" + env_str + "_fieldkeys__");
        if ((keys == null || keys.isEmpty()) && param != null) {
            // 自动推断参数顺序：按SQL中?数量和param的key顺序（排除__开头的key）
            keys = new JSONArray();
            // 统计SQL中?数量
            int qCount = sqlStr.length() - sqlStr.replace("?", "").length();
            // 取param中非__开头的key
            java.util.List<String> paramKeys = new java.util.ArrayList<>();
            for (String k : param.keySet()) {
                if (!k.startsWith("__")) {
                    paramKeys.add(k);
                }
            }
            // 按顺序取前qCount个key
            for (int i = 0; i < qCount && i < paramKeys.size(); i++) {
                keys.add(paramKeys.get(i));
            }
            System.out.println("[参数自动推断] SQL参数数量: " + qCount + ", paramKeys: " + paramKeys + ", keys: " + keys);
        }

        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray ja = new JSONArray();
        Object[] obs = null;

        if (keys != null && !keys.isEmpty()) {
            for (Object s : keys) {
                String sk = s.toString();
                if (sk.startsWith("$")) {
                    String astr = param.getStr(sk);
                    JSONArray ja1 = JSONUtil.parseArray(astr);
                    String marks = StrUtil.repeatAndJoin("?", ja1.size(), ",");
                    sqlStr = sqlStr.replace(sk, marks);
                    for (Object o : ja1) {
                        ja.set(o);
                    }

                } else {
                    Object o = param.getObj(sk);
                    ja.set(o);
                }
            }
            List<Object> list = ja.toList(Object.class);
            obs = list.toArray(new Object[0]);
        }

        System.out.println("Debug - Final SQL: " + sqlStr);
        System.out.println("Debug - Parameters: " + (obs != null ? java.util.Arrays.toString(obs) : "null"));

        Db lisDb = DBConfig.getDb(null, "qj");
        try {
            int affectedRows = 0;

            if (obs == null) {
                affectedRows = lisDb.execute(sqlStr);
            } else {
                affectedRows = lisDb.execute(sqlStr, obs);
            }

            if (affectedRows > 0) {
                res.set("result", affectedRows);
                res.set("errCode", 0);
            } else {
                res.set("errCode", 404);
                res.set("errMsg", "相关记录不存在");
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());
            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());
        } finally {
            // lisDb = null;
        }

        res.set("action", "modify");

        return res;
    }

    /**
     *
     * @param funcCode
     * @param funcName
     * @param moduleName
     * @param param
     *                    无 fieldkeys 顺序，必须按sql里的顺序
     * @param setExcludes
     * @return
     */
    public static JSONObject exeBulkInsert(int funcCode, String funcName, String moduleName,
            JSONObject param, Set<String> setExcludes) {
        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        int size = param.getInt("size");
        JSONArray array = param.getJSONArray("values");
        int total = array.size();
        if (total % size != 0) {
            System.out.println("errFunName => " + funcName);
            System.out.println("errMsg ==> " + "待批量插入的数据不匹配");
            return res.set("errCode", 3).set("errMsg", "待批量插入的数据不匹配");
        }
        if (total / size == 0) {
            return res.set("errCode", 0).set("errMsg", "没有插入值").set("result", 0);
        }
        String marks = "(" + StrUtil.repeatAndJoin("?", size, ",") + ")";
        String totalMarks = StrUtil.repeatAndJoin(marks, total / size, ",");

        String sqlStr;

        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);
        }
        // System.out.println(sqlStr);
        sqlStr = sqlStr.replace("holder_values", totalMarks);

        JSONArray ja = param.getJSONArray("values");
        List<Object> list = ja.toList(Object.class);
        Object[] obs = list.toArray(new Object[0]);

        int affectedNum = 0;

        Db lisDb = DBConfig.getDb(null, "qj");
        try {
            affectedNum = lisDb.execute(sqlStr, obs);
        } catch (Exception e) {
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());

            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());
        } finally {
            // lisDb = null;
        }
        return res.set("errCode", 0).set("result", affectedNum);
    }

    /**
     * 自定义执行sql
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public static JSONObject customSql(
            int funcCode, String funcName,
            String sql, List<String> list

    ) {
        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());

        Db lisDb = DBConfig.getDb(null, "qj");
        try {
            Object[] objs = list.toArray(new Object[0]);
            List<Entity> result = lisDb.query(sql, objs);
            res.set("errCode", 0);
            res.set("result", result);
        } catch (Exception e) {
            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());

            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());
        } finally {
            lisDb = null;
        }
        return res;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * 下面为复杂功能CRUD
     */

    /**
     * 执行查询
     * 
     * @param funcCode
     * @param funcName
     * @param moduleName
     * @param param
     * @param setExcludes
     * @param session
     * @return
     * @throws Exception
     */
    public static JSONObject exeRetrieve(
            int funcCode,
            String funcName,
            String moduleName,
            JSONObject param,
            Set<String> setExcludes,
            Session session) throws Exception {
        String env_str = DBConfig.getDbSetting().getStr("db_env");
        String sqlStr;
        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);

        }
        // "__fieldkeys__" 前端要一致
        JSONArray keys = param.getJSONArray("__" + env_str + "_fieldkeys__");
        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray ja = new JSONArray();
        Object[] obs = null;
        if (keys != null && !keys.isEmpty()) {
            for (Object s : keys) {
                String sk = s.toString();
                if (sk.startsWith("$")) {
                    String astr = param.getStr(sk);
                    JSONArray ja1 = JSONUtil.parseArray(astr);
                    String marks = StrUtil.repeatAndJoin("?", ja1.size(), ",");
                    sqlStr = sqlStr.replace(sk, marks);
                    for (Object o : ja1) {
                        ja.set(o);
                    }

                } else {
                    Object o = param.getObj(sk);
                    ja.set(o);
                }
            }
            obs = ja.toArray();
        }
        // 如果SQL没有?占位符，则不传参数数组，避免参数数量不匹配
        if (!sqlStr.contains("?")) {
            obs = null;
        }
        // 替换SQL中的${pageSize}和${offset}为实际数字
        if (param.containsKey("pageSize")) {
            sqlStr = sqlStr.replace("${pageSize}", String.valueOf(param.getInt("pageSize")));
        }
        if (param.containsKey("offset")) {
            sqlStr = sqlStr.replace("${offset}", String.valueOf(param.getInt("offset")));
        }
        try {
            List<Entity> items = null;
            if (obs == null) {
                items = session.query(sqlStr);

            } else {
                items = session.query(sqlStr, obs);
            }
            if (items != null) {
                res.set("result", items);
                res.set("errCode", 0);
            } else {
                res.set("errCode", 404);
                res.set("errMsg", "相关记录不存在");
            }
        } catch (Exception e) {
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());
            res.set("result", new JSONArray());
            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());
            throw new Exception(e.getMessage());
        }
        res.set("action", "retrieve");
        return res;
    }

    /**
     * 执行 添加 - 删除 - 删除
     * 
     * @param funcCode
     * @param funcName
     * @param moduleName
     * @param param
     * @param setExcludes
     * @param session
     * @return
     * @throws Exception
     */
    public static JSONObject exeModifyOrDelete(
            int funcCode,
            String funcName,
            String moduleName,
            JSONObject param,
            Set<String> setExcludes,
            Session session) throws Exception {
        String sqlStr;
        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);

        }
        String env_str = DBConfig.getDbSetting().getStr("db_env");
        JSONArray keys = param.getJSONArray("__" + env_str + "_fieldkeys__");

        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        JSONArray ja = new JSONArray();
        Object[] obs = null;

        if (keys != null && !keys.isEmpty()) {
            for (Object s : keys) {
                String sk = s.toString();
                if (sk.startsWith("$")) {
                    String astr = param.getStr(sk);
                    JSONArray ja1 = JSONUtil.parseArray(astr);
                    String marks = StrUtil.repeatAndJoin("?", ja1.size(), ",");
                    sqlStr = sqlStr.replace(sk, marks);
                    for (Object o : ja1) {
                        ja.set(o);
                    }

                } else {
                    Object o = param.getObj(sk);
                    ja.set(o);
                }

            }
            List<Object> list = ja.toList(Object.class);
            obs = list.toArray(new Object[0]);

        }
        try {
            int affectedRows = 0;

            if (obs == null) {
                affectedRows = session.execute(sqlStr);

            } else {

                affectedRows = session.execute(sqlStr, obs);

            }

            if (affectedRows > 0) {
                res.set("result", affectedRows);
                res.set("errCode", 0);
            } else {
                res.set("errCode", 404);
                res.set("errMsg", "相关记录不存在");
            }

        } catch (Exception e) {
            e.printStackTrace();
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());
            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());

            throw new Exception(e.getMessage());
        }

        res.set("action", "modify");

        return res;
    }

    /**
     * 执行批量插入
     * 
     * @param funcCode
     * @param funcName
     * @param moduleName
     * @param param
     * @param setExcludes
     * @param session
     * @return
     * @throws Exception
     */
    public static JSONObject exeBulkInsert(
            int funcCode,
            String funcName,
            String moduleName,
            JSONObject param,
            Set<String> setExcludes,
            Session session) throws Exception {
        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        int size = param.getInt("size");
        JSONArray array = param.getJSONArray("values");
        int total = array.size();
        if (total % size != 0) {
            System.out.println("errFunName => " + funcName);
            System.out.println("errMsg ==> " + "待批量插入的数据不匹配");
            return res.set("errCode", 403).set("errMsg", "待批量插入的数据不匹配");
        }
        if (total / size == 0) {
            return res.set("errCode", 0).set("errMsg", "没有插入值").set("result", 0);
        }

        String marks = "(" + StrUtil.repeatAndJoin("?", size, ",") + ")";
        String totalMarks = StrUtil.repeatAndJoin(marks, total / size, ",");

        String sqlStr;

        if (!setExcludes.isEmpty()) {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0, setExcludes);
        } else {
            sqlStr = DBConfig.getSqlStr(
                    null,
                    moduleName, funcName,
                    0);
        }
        sqlStr = sqlStr.replace("holder_values", totalMarks);
        JSONArray ja = param.getJSONArray("values");
        List<Object> list = ja.toList(Object.class);
        Object[] obs = list.toArray(new Object[0]);
        int affectedNum = 0;
        try {
            affectedNum = session.execute(sqlStr, obs);
        } catch (Exception e) {
            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());

            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());

            throw new Exception(e.getMessage());
        }
        return res.set("errCode", 0).set("result", affectedNum);
    }

    /**
     * 执行自定义sql
     * 
     * @param funcCode
     * @param funcName
     * @param sql
     * @param list
     * @param session
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static JSONObject customSql(
            int funcCode,
            String funcName,
            String sql,
            List<String> list,
            Session session) throws Exception {
        JSONObject res = new JSONObject(MyJsonConfig.getJsonConfig());
        try {
            Object[] objs = list.toArray(new Object[0]);
            List<Entity> result = session.query(sql, objs);
            res.set("errCode", 0);
            res.set("result", result);
        } catch (Exception e) {
            System.out.println("funCode => " + funcCode);
            System.out.println("funName => " + funcName);
            System.out.println("errMsg => " + e.getMessage());

            res.set("errCode", funcCode);
            res.set("errMsg", e.getMessage());

            throw new Exception(e.getMessage());
        }
        return res;
    }
}
