package com.qj.db;

import cn.hutool.db.Db;
import cn.hutool.db.Session;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;

/**
 * 这个类可以直接获取db，和切换db
 */
public class DBConfig {
    private static final Logger log = LoggerFactory.getLogger(DBConfig.class);
    private static Setting dbSetting;
    private static Setting wxPaySetting;

    private static DSFactory dsFactory;
    private static DataSource dataSource;
    private static Map<String, Setting> sqlSettingMap = new HashMap<>();

    static {
        /**
         * 从db.setting读入
         */
        dbSetting = new Setting("db.setting");

        /**
         * 从wxpay.setting读入微信支付配置
         */
        try {
            wxPaySetting = new Setting("wxpay.setting");
        } catch (Exception e) {
            log.warn("微信支付配置文件未找到: {}", e.getMessage());
        }

        dsFactory = DSFactory.create(dbSetting);
        String settingFilenames = dbSetting.getStr("sql_modules", "");
        log.info("加载 SQL 模块配置: {}", settingFilenames);
        if (settingFilenames != null && settingFilenames.length() > 2) {
            String[] names = settingFilenames.split(",");
            for (String name : names) {
                try {
                    String settingFileName = name.startsWith("mysql_") ? name : "mysql_" + name;
                    Setting mysqlSetting = new Setting(settingFileName + ".setting");
                    sqlSettingMap.put(name, mysqlSetting); // 使用原始模块名作为key
                    log.info("成功加载 MySQL 配置: {}.setting", settingFileName);
                } catch (Exception e) {
                    log.warn("加载 MySQL 配置失败: {}.setting - {}", name, e.getMessage());
                }
            }
        }
    }

    /**
     * 获取数据库，db操做对象
     * 
     * @param env     环境，mssql pg_master pg_slave，留空就是系统默认
     *                如果在读写分离的环境中，目前需要进一步实验，看是使用
     *                pg_tool II等系统端集群工具，还是使用客户端连接区分
     *                如果使用客户端连接区分，写和关键读放主库，其他读放从库
     * @param db_name 数据库名称
     * @return
     */
    public static Db getDb(String env, String db_name) {
        Db db = null;
        try {
            if (env == null || env.length() < 3) {
                env = dbSetting.getStr("db_env");
            }
            String dbkey = env + "_" + db_name;
            log.debug("获取数据库连接: {}", dbkey);
            dataSource = dsFactory.getDataSource(dbkey);
            db = Db.use(dataSource);
        } catch (Exception e) {
            log.error("获取Db错误: {}", e.getMessage(), e);
        }
        return db;
    }

    /**
     *
     * @param env      mssql pgsql
     *
     * @param module   user data test_process 等
     *
     *****                 以上两个拼接起来就是sql的setting文件名
     *
     *
     * @param funcname F_102_1002_CREATE_USER
     * @param seq      1,2,3
     * @param excludes 在自定义sql中排除行，Set<String> ，
     *                 Set<String> excludes = new
     *                 HashSet<String>(Arrays.asList("r2", "r3"));
     *                 此初始化可以排除原自定义sql中的r2，r3行
     *                 [F_102_1003_UPDATE_USER]
     *                 # 说明
     *                 r1 = select * from x where 1 = 1
     *                 r2 = and (name1 like ? or ... or ... or x>)
     *                 r3 = and mode in (1,2,3)
     *
     *
     *                 用途跟funcname合起来
     *                 例如：F_102_1003_UPDATE_USER_1 更新用户名，2更新其他
     *                 为0或者null就不拼接
     * @return
     */
    public static String getSqlStr(String env, String module, String funcname,
            int seq, Set<String> excludes) {
        if (env == null || env.length() < 3) {
            env = dbSetting.getStr("db_env");
        }

        // 直接使用模块名作为key
        log.debug("查找SQL配置 - 模块: {}, 功能: {}", module, funcname);
        Setting setting = sqlSettingMap.get(module);
        log.debug("[调试] setting: {}", setting);

        if (setting != null) {
            if (seq > 0) {
                funcname = funcname + "_" + seq;
            }
            Properties rows = setting.getProperties(funcname);
            log.debug("[调试] rows: {}", rows);

            if (rows != null) {
                StringBuilder sql = new StringBuilder();
                // 获取所有键并按数字排序
                List<String> keys = new ArrayList<>(rows.stringPropertyNames());
                log.debug("[调试] keys: {}", keys);
                keys.sort((a, b) -> {
                    // 提取r后面的数字进行比较
                    int numA = Integer.parseInt(a.substring(1));
                    int numB = Integer.parseInt(b.substring(1));
                    return numA - numB;
                });

                // 按顺序拼接SQL
                for (String key : keys) {
                    if (excludes != null && excludes.contains(key)) {
                        continue;
                    }
                    // 获取SQL片段并去除多余的空白字符
                    String sqlPart = rows.getProperty(key).trim().replaceAll("\\s+", " ");
                    log.debug("[调试] SQL片段 key: {}, value: {}", key, sqlPart);
                    sql.append(sqlPart).append(" ");
                }

                String sqlStr = sql.toString().trim();
                log.debug("[调试] 最终拼接SQL: {}", sqlStr);
                log.debug("生成SQL: {}", sqlStr);
                return sqlStr;
            }
        }

        log.warn("未找到SQL配置 - 模块: {}, 功能: {}", module, funcname);
        return null;
    }

    /**
     * 获取SQL语句（无排除项）
     */
    public static String getSqlStr(String env, String module, String funcname, int seq) {
        return getSqlStr(env, module, funcname, seq, null);
    }

    public static Setting getDbSetting() {
        return dbSetting;
    }

    public static Setting getWxPaySetting() {
        return wxPaySetting;
    }

    public static DataSource getDs() {
        return dataSource;
    }

    private Connection connection = null;
    private static Session tranSession;

    /**
     * 获取session用于复杂功能事务处理
     * 
     * @return
     */
    public static Session getSession() {
        try {
            if (tranSession == null || tranSession.getConnection().isClosed()) {
                tranSession = null;
                tranSession = Session.create(
                        DSFactory.get(DBConfig.getDbSetting().getStr("db_env") + "_" + "qj"));
            }
            return tranSession;
        } catch (Exception e) {
            log.error("获取Session错误: {}", e.getMessage(), e);
            return null;
        }
    }
}
