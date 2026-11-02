# 衣橱 Prompt 发布后自动回滚实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 在当前任务中逐项实现；每项严格执行测试先行的红灯、绿灯、重构循环。

**目标：** 移除普通顾问端的 Prompt 优化入口，并在后端增加发布后同图影子评测和明确退步时的自动回滚闭环。

**架构：** 新的发布监控服务监听顾问评测完成事件并定时恢复。它为最新一次自动发布创建唯一监控任务，冻结由新版本识别的已评测衣物，使用上一个版本进行影子识别，再通过独立策略比较完全匹配率和风格 MAE。版本切换继续集中在 `WardrobePromptVersionService` 的事务边界中。

**技术栈：** Java 17、Spring Boot 2.7、Hutool DB/JSON、JUnit 5、Mockito、MySQL 5.7、微信小程序 JavaScript/WXML。

## 全局约束

- 测试环境观察4条，正式环境观察20条。
- 最大影子识别失败率为20%，单条最多重试2次。
- 正式环境完全匹配率退步门槛为0.10，测试环境为0.25；风格 MAE 退步门槛为5.0。
- 只有上一个版本两个指标都不差，且至少一个退步幅度达到门槛时才自动回滚。
- 训练、发布前留出评测、发布后观察三类样本都必须持久化且用途可区分。
- 不能修改历史衣物、AI识别快照和顾问最终答案。
- 普通顾问端不显示 Prompt 管理入口，紧急回滚 API 保留。

---

### 任务一：发布监控数据库契约

**文件：**
- 新建：`qj_backend/src/main/resources/migration_wardrobe_prompt_release_monitor.sql`
- 新建：`qj_backend/src/test/java/com/qj/services/WardrobePromptReleaseSchemaContractTest.java`
- 修改：`qj_backend/src/main/resources/application.properties`
- 修改：`qj_backend/src/main/resources/application-dev.properties`
- 修改：`qj_backend/src/main/resources/application-prod.properties`

**产出接口：** 两张持久化表 `qj_wardrobe_prompt_release_monitor`、`qj_wardrobe_prompt_release_result`，审计动作 `auto_rollback`，以及 `wardrobe.prompt.monitor.*` 配置。

- [ ] 写契约测试，要求迁移包含唯一 `publication_audit_id`、监控和结果状态、唯一 `(monitor_id, wardrobe_item_id)`、运行中监控保护和 `auto_rollback`。
- [ ] 运行 `mvn -Dtest=WardrobePromptReleaseSchemaContractTest test`，确认因迁移缺失而失败。
- [ ] 编写可重复执行且兼容 MySQL 5.7 的迁移，并补充开发/生产配置。
- [ ] 重跑契约测试，确认通过。

### 任务二：自动回滚判断策略

**文件：**
- 新建：`qj_backend/src/main/java/com/qj/services/WardrobePromptRollbackPolicy.java`
- 新建：`qj_backend/src/test/java/com/qj/services/WardrobePromptRollbackPolicyTest.java`

**接口：**

```java
Decision evaluate(JSONObject summary, int minimumSuccessful,
                  double maximumFailureRate,
                  double minimumExactRegression,
                  double minimumStyleMaeRegression)
```

`summary` 包含 `total`、`completed`、`failed`、`publishedMetrics` 和 `baselineMetrics`。`Decision` 返回 `shouldRollback` 和机器可读 `reason`。

- [ ] 先写样本不足、失败率过高、指标互有胜负、完全匹配率明确退步、风格 MAE 明确退步和无明显退步测试。
- [ ] 运行单测，确认类不存在导致红灯。
- [ ] 实现保守策略：上一个版本两个指标均不差，并至少一个差值达到门槛才返回回滚。
- [ ] 重跑单测，确认边界值、非法数字和空指标均不会误回滚。

### 任务三：版本事务支持系统自动回滚

**文件：**
- 修改：`qj_backend/src/main/java/com/qj/services/WardrobePromptVersionService.java`
- 修改：`qj_backend/src/test/java/com/qj/services/WardrobePromptVersionServiceTest.java`

**接口：**

```java
PromptVersion autoRollback(Long retiredId, Long expectedActiveId,
                           Long monitorId, Long sampleFloorItemId,
                           String reason) throws SQLException
```

- [ ] 先写失败测试，验证只能从 `evaluating` 监控回滚直接上一个版本，执行者为 `system:auto-monitor`，审计动作为 `auto_rollback`。
- [ ] 写事务失败测试，验证版本、监控状态和审计任何一步失败都会回滚事务。
- [ ] 修改版本切换事务：自动发布写入当前衣物最大ID水位；自动回滚锁定并校验监控记录、切换版本、标记监控 `rolled_back`、淘汰不兼容任务并写审计。
- [ ] 更新样本水位查询，使 `manual_rollback` 和 `auto_rollback` 都能隔离旧样本。
- [ ] 重跑版本服务与优化服务测试。

### 任务四：发布后影子评测服务

**文件：**
- 新建：`qj_backend/src/main/java/com/qj/services/WardrobePromptReleaseMonitorService.java`
- 新建：`qj_backend/src/test/java/com/qj/services/WardrobePromptReleaseMonitorServiceTest.java`
- 修改：`qj_backend/src/main/java/com/qj/services/WardrobePromptOptimizationService.java`
- 修改：`qj_backend/src/test/java/com/qj/services/WardrobePromptOptimizationServiceTest.java`

**核心接口：**

```java
JSONObject advanceOnce();
boolean blocksNewOptimization() throws SQLException;
```

服务实现 `@EventListener(WardrobeReviewCompletedEvent.class)` 和每60秒定时恢复。内部 Store 负责唯一创建监控、冻结样本、领取可重试结果、持久化比较结果和汇总指标。

- [ ] 先写测试覆盖唯一建任务、只选新版本有效样本、样本冻结、同一顾问答案比较、失败重试、重启恢复、版本变化置为 `stale`。
- [ ] 运行测试确认缺少服务而失败。
- [ ] 实现 `collecting -> evaluating -> kept/rolled_back/failed/stale` 状态机。
- [ ] 影子评测只调用上一个 Prompt；新版本使用衣物中不可变的 `ai_evaluation_json`。
- [ ] 使用现有 `WardrobeEvaluationNormalizer` 生成两份差异，汇总完全匹配率和风格 MAE。
- [ ] 调用 `WardrobePromptRollbackPolicy`；满足条件时调用 `autoRollback`，否则标记 `kept`。
- [ ] 在 `startAutomatic()` 的事务检查中阻止不存在监控记录或监控仍为 `collecting/evaluating` 的最新自动发布进入下一轮优化。
- [ ] 重跑监控、优化器和单次评测测试。

### 任务五：移除普通顾问端入口

**文件：**
- 修改：`D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.js`
- 修改：`D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.wxml`
- 新建：`D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.test.js`

- [ ] 先写 Node 契约测试，断言 WXML 不包含 `Prompt优化`/`toPrompt`，JS 不包含 Prompt 页面跳转。
- [ ] 运行 `node miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.test.js`，确认测试因现有按钮失败。
- [ ] 删除按钮和 `toPrompt()` 方法，不改用户评测和衣橱评测入口。
- [ ] 重跑小程序契约测试和现有 `wardrobeAiReview.test.js`。

### 任务六：迁移、回归验证与运行检查

**文件：**
- 修改：`qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md`

- [ ] 更新部署文档，说明发布后观察、自动回滚、配置、表结构和普通顾问端入口移除。
- [ ] 对测试库 `qj_minip_v2_t` 执行新迁移两次，验证可重复执行；不得连接生产库。
- [ ] 运行 `mvn test`，要求全部测试通过。
- [ ] 运行小程序全部 Node 契约测试和相关 JS 语法检查。
- [ ] 重启7077新后端和8090旧后端，确认健康接口和衣橱旧接口可访问。
- [ ] 检查 Git diff，确保不包含日志、`db.setting`、微信开发者私有配置或用户原有改动。
