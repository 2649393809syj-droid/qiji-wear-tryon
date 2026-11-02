# 奇纪时尚「你穿我搭」微信小程序

衣橱 AI 穿搭小程序：用户上传衣物到私人衣橱，AI 完成风格识别与穿搭评测，支持 AI 试穿、AI 审核与试穿付费，后端 Spring Boot + 前端 uni-app（微信小程序），线上已发布运营。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Java · Spring Boot · MyBatis · MySQL（多库分模块）· Redis · JWT · Quartz |
| AI | Gemini（风格识别）· 火山方舟 Doubao-Seedream-4.5（AI 试穿）· Playwright |
| 前端 | uni-app（Vue 2）· 微信小程序 |
| 云服务 | 腾讯云 COS（图片存储）· Nginx |

## 目录结构

```
qj_backend/         Spring Boot 后端（controller / service / config / entities）
  ├─ src/main/java/com/qj
  │  ├─ config/         JWT 拦截器、@RequiresPermission 权限注解、异步与定时配置
  │  ├─ services/       业务服务（衣橱、试穿、审核、提示词优化、发布监控）
  │  ├─ clients/        Gemini / 火山方舟 Ark 等 AI 客户端
  │  └─ controllers/    16 个 REST 接口模块
  └─ src/main/resources
     ├─ public/         静态资源
     └─ migration_*.sql 数据库演进脚本（按功能独立迁移）
qjv2-frontend/      uni-app 小程序前端（衣橱、试穿、审核、代理推广等页面）
docs/superpowers/   关键功能设计文档（plans / specs）
```

## 核心亮点

### 1. 衣橱 AI 提示词自动化优化系统
- 工作流编排：候选 Prompt 生成 → 离线测试 → 人工发布，**不允许自动覆盖生产 Prompt**
- 实验隔离：shadow 试跑，候选 Prompt 只影响发布后新上传的衣物
- 任务快照 + 发布回滚闭环：`严格改进才发布`（风格误差下降或精确匹配率上升，持平拒绝）
- 指标比较使用 `BigDecimal` 精确计算，避免浮点误差导致的误判

### 2. 发布后监控与自动回滚
- 监控发布后指标，**明显退化才触发自动回滚**，回滚区间设死区防止版本震荡
- 失败率 20% 上限 + 最小训练样本门槛（50）+ 指标冲突保护
- `@Scheduled` 定时恢复 + 幂等续跑，全程审计留痕（谁在什么时间改了什么版本可追溯）

### 3. AI 试穿与审核
- 接入火山方舟 Doubao-Seedream-4.5 做 AI 试穿，修复超时问题（timeout 180s）
- 试穿次数与分项定价（免费次数 / 单次单价按环境配置），前端联动提示
- 衣橱 AI 审核：**每件衣物只有一次 AI 初判快照**，审核结果单次落库（含迁移脚本），顾问在原表直接修正并进入 Prompt 优化数据集

### 4. 鉴权与 AI 接入
- JWT 拦截器 + `@RequiresPermission` 注解做接口级权限，保护 AI 相关接口
- Gemini 风格识别 + Advisor 工作流，审核视图自动化

## 启动方式

密钥一律通过环境变量注入，仓库不提交任何真实密钥（见下方清单）：

```bash
# 后端（端口 7077）
export WX_MINIAPP_SECRET=xxx              # 微信小程序 secret
export TENCENT_COS_SECRET_ID=xxx          # 腾讯云 COS
export TENCENT_COS_SECRET_KEY=xxx
export GEMINI_API_KEY=xxx                 # Gemini 风格识别
cd qj_backend && mvn spring-boot:run
```

前端：用 HBuilderX 打开 `qjv2-frontend`，或导入微信开发者工具运行。

数据库：按 `src/main/resources/migration_*.sql` 顺序执行演进脚本。

## 说明

- 线上配置（数据库连接、生产 profile、密钥）均不提交，通过环境变量或运维配置注入。
- 本仓库用于作品展示与技术交流。
