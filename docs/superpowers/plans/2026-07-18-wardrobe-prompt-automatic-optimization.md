# Wardrobe Prompt Automatic Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace advisor-driven Prompt generation, evaluation, and publication with a recoverable backend pipeline that auto-publishes only improved candidates while allowing every authenticated advisor to roll back with an audited reason.

**Architecture:** A new `WardrobePromptAutoOptimizer` advances one database-guarded optimization job at a time. Reviewed-sample events and a scheduled recovery tick both invoke the same idempotent state machine; existing candidate generation, isolated Gemini evaluation, version storage, statistics, and advisor-token authentication remain the underlying components. The mini program becomes read-only for statistics, versions, and automatic-job status except for authenticated rollback.

**Tech Stack:** Java 17, Spring Boot 2.7.18, Hutool DB/JSON, MySQL 5.7, JUnit 5, WeChat Mini Program JavaScript/WXML/WXSS, Node.js assertion tests.

## Global Constraints

- A wardrobe item receives exactly one business AI recognition; offline Prompt experiments never update item or history rows.
- AI and advisor evaluation snapshots remain immutable and retain the aligned legacy JSON shape.
- Style scores are independent 0-100 values, use only the existing eight styles, and contain at most two non-zero styles.
- Development/test trigger defaults: 4 new reviewed samples, at least 1 positive and 1 negative, and at least 2 successful held-out samples.
- Production trigger defaults: 20 new reviewed samples, at least 3 positive and 3 negative, and at least 10 successful held-out samples.
- Automatic publication requires failure rate at most 20%, candidate `styleMae` no worse than baseline, candidate `exactMatchRate` no worse than baseline, and at least one strictly improved core metric.
- The candidate base version must still be active at publication time.
- Every authenticated `grade=1` advisor may roll back a retired version; a non-blank reason is required.
- Publication and rollback affect only wardrobe items created afterward.
- Real Gemini keys and advisor-token secrets remain outside Git.
- Do not stage or revert unrelated dirty files in either repository.

---

## File Structure

### Backend repository: `D:\project\qjapiv2`

- Create `qj_backend/src/main/resources/migration_wardrobe_prompt_automatic.sql`: replay-safe schema extension and audit table.
- Create `qj_backend/src/test/java/com/qj/services/WardrobePromptAutomaticSchemaContractTest.java`: migration contract.
- Create `qj_backend/src/main/java/com/qj/services/WardrobePromptPublicationPolicy.java`: pure automatic-publication decision logic.
- Create `qj_backend/src/test/java/com/qj/services/WardrobePromptPublicationPolicyTest.java`: boundary tests for the policy.
- Create `qj_backend/src/main/java/com/qj/services/WardrobePromptAutoOptimizer.java`: idempotent job state machine, local executor guard, and scheduled recovery.
- Create `qj_backend/src/test/java/com/qj/services/WardrobePromptAutoOptimizerTest.java`: state-machine and recovery tests.
- Create `qj_backend/src/main/java/com/qj/services/WardrobeReviewCompletedEvent.java`: reviewed-item event payload.
- Modify `qj_backend/src/main/java/com/qj/services/WardrobeService.java`: publish the event only after first successful completion.
- Modify `qj_backend/src/test/java/com/qj/services/WardrobeServiceSinglePassTest.java`: event and duplicate-completion assertions.
- Modify `qj_backend/src/main/java/com/qj/services/WardrobePromptOptimizationService.java`: automatic sample thresholds, disjoint snapshots, state transitions, and job listing.
- Modify `qj_backend/src/test/java/com/qj/services/WardrobePromptOptimizationServiceTest.java`: automatic snapshot and cooldown tests.
- Modify `qj_backend/src/main/java/com/qj/services/WardrobePromptExperimentService.java`: evaluate explicit held-out IDs and return metrics without publishing.
- Modify `qj_backend/src/test/java/com/qj/services/WardrobePromptExperimentServiceTest.java`: strict isolation, failure-rate, and recovery tests.
- Modify `qj_backend/src/main/java/com/qj/services/WardrobePromptVersionService.java`: automatic publish, advisor rollback reason, stale-base check, rejection, and transactional audit.
- Modify `qj_backend/src/test/java/com/qj/services/WardrobePromptVersionServiceTest.java`: version-switch and audit tests.
- Modify `qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java`: read APIs, rollback body, and disabled manual commands.
- Create `qj_backend/src/test/java/com/qj/controllers/WardrobePromptControllerTest.java`: controller contract and authorization identity tests.
- Modify `qj_backend/src/main/resources/application.properties`: shared automatic-optimizer properties.
- Modify `qj_backend/src/main/resources/application-dev.properties`: development/test thresholds.
- Modify `qj_backend/src/main/resources/application-prod.properties`: production thresholds.
- Modify `qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md`: migration order, automatic flow, rollback, and verification.

### Mini-program repository: `D:\project\minip-client`

- Modify `miniprogram/utils/wardrobePromptCenter.js`: automatic statuses, job rows, and rollback validation.
- Modify `miniprogram/utils/wardrobePromptCenter.test.js`: remove manual-command expectations and assert advisor rollback behavior.
- Modify `miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.js`: load automatic jobs and submit reasoned rollback only.
- Modify `miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxml`: remove generate/evaluate/publish controls and render job decisions.
- Modify `miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxss`: automatic-job and rollback-dialog states.

---

### Task 1: Add the automatic-optimization schema and audit contract

**Files:**
- Create: `qj_backend/src/main/resources/migration_wardrobe_prompt_automatic.sql`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptAutomaticSchemaContractTest.java`

**Interfaces:**
- Consumes: existing `qj_wardrobe_prompt_version`, `qj_wardrobe_prompt_optimization`, and `qj_wardrobe_item` tables.
- Produces: automatic job states, held-out and threshold snapshots, one-running-job guard, version audit rows, and rollback sample floor.

- [ ] **Step 1: Write the failing schema-contract test**

Create a JUnit test that loads the migration and asserts the exact new contract:

```java
class WardrobePromptAutomaticSchemaContractTest {
    private final String sql = resource("migration_wardrobe_prompt_automatic.sql").toLowerCase();

    @Test
    void addsAutomaticJobSnapshotsAndOneRunningGuard() {
        assertTrue(sql.contains("`trigger_type` enum('legacy_manual','automatic')"));
        assertTrue(sql.contains("`heldout_sample_ids_json` mediumtext"));
        assertTrue(sql.contains("`threshold_json` text"));
        assertTrue(sql.contains("`baseline_metrics_json` text"));
        assertTrue(sql.contains("`candidate_metrics_json` text"));
        assertTrue(sql.contains("`sample_max_item_id` bigint"));
        assertTrue(sql.contains("unique key `uk_prompt_optimization_one_running` (`running_guard`)"));
    }

    @Test
    void createsImmutableVersionSwitchAudit() {
        assertTrue(sql.contains("create table if not exists `qj_wardrobe_prompt_version_audit`"));
        for (String column : List.of("from_version_id", "to_version_id", "actor_type",
                "actor_id", "reason", "optimization_id", "sample_floor_item_id")) {
            assertTrue(sql.contains("`" + column + "`"), column);
        }
    }
}
```

Use this resource helper so a missing migration fails clearly:

```java
private String resource(String name) {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
        assertNotNull(input, name + " is missing");
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
        throw new AssertionError("Cannot read " + name, error);
    }
}
```

- [ ] **Step 2: Run the schema test and verify it fails**

Run:

```powershell
cd D:\project\qjapiv2\qj_backend
.\mvnw.cmd -q -Dtest=WardrobePromptAutomaticSchemaContractTest test
```

Expected: FAIL because `migration_wardrobe_prompt_automatic.sql` does not exist.

- [ ] **Step 3: Add the replay-safe migration**

The migration must use the existing migration files' `information_schema` plus `PREPARE/EXECUTE/DEALLOCATE` pattern before every column or index addition. For example, add `heldout_sample_ids_json` with:

```sql
SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'qj_wardrobe_prompt_optimization'
           AND column_name = 'heldout_sample_ids_json'),
  'SELECT 1',
  'ALTER TABLE `qj_wardrobe_prompt_optimization` ADD COLUMN `heldout_sample_ids_json` MEDIUMTEXT DEFAULT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

Repeat that exact guard with each listed column/index name, then apply these final definitions:

```sql
ALTER TABLE `qj_wardrobe_prompt_version`
  MODIFY COLUMN `status`
    ENUM('candidate','evaluating','active','retired','rejected') NOT NULL;

ALTER TABLE `qj_wardrobe_prompt_optimization`
  MODIFY COLUMN `status`
    ENUM('pending','running','completed','failed','generating','waiting_for_holdout',
         'evaluating','published','rejected','stale') NOT NULL DEFAULT 'pending';

ALTER TABLE `qj_wardrobe_prompt_optimization`
  ADD COLUMN `trigger_type` ENUM('legacy_manual','automatic') NOT NULL DEFAULT 'legacy_manual',
  ADD COLUMN `heldout_sample_ids_json` MEDIUMTEXT DEFAULT NULL,
  ADD COLUMN `threshold_json` TEXT DEFAULT NULL,
  ADD COLUMN `baseline_metrics_json` TEXT DEFAULT NULL,
  ADD COLUMN `candidate_metrics_json` TEXT DEFAULT NULL,
  ADD COLUMN `decision_reason` VARCHAR(255) DEFAULT NULL,
  ADD COLUMN `sample_max_item_id` BIGINT DEFAULT NULL,
  ADD COLUMN `publish_time` DATETIME DEFAULT NULL;

ALTER TABLE `qj_wardrobe_prompt_optimization`
  ADD COLUMN `running_guard` TINYINT GENERATED ALWAYS AS
    (CASE WHEN `trigger_type` = 'automatic'
               AND `status` IN ('pending','generating','waiting_for_holdout','evaluating')
          THEN 1 ELSE NULL END) STORED,
  ADD UNIQUE KEY `uk_prompt_optimization_one_running` (`running_guard`);

CREATE TABLE IF NOT EXISTS `qj_wardrobe_prompt_version_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `action` ENUM('auto_publish','manual_rollback') NOT NULL,
  `from_version_id` BIGINT NOT NULL,
  `to_version_id` BIGINT NOT NULL,
  `actor_type` ENUM('system','advisor') NOT NULL,
  `actor_id` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `optimization_id` BIGINT DEFAULT NULL,
  `sample_floor_item_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_prompt_audit_time` (`create_time`),
  KEY `idx_prompt_audit_target` (`to_version_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Before removing `ready_for_review` from the version enum, convert existing rows to `candidate`. Existing optimization rows receive `legacy_manual`; every new backend-created job explicitly inserts `trigger_type='automatic'`. Keep legacy optimization states in the enum so applied test data remains readable without occupying the automatic running guard.

- [ ] **Step 4: Run schema contracts**

Run:

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptSchemaContractTest,WardrobePromptAutomaticSchemaContractTest test
```

Expected: PASS with both schema test classes successful.

- [ ] **Step 5: Commit the schema task**

```powershell
git add qj_backend/src/main/resources/migration_wardrobe_prompt_automatic.sql `
        qj_backend/src/test/java/com/qj/services/WardrobePromptAutomaticSchemaContractTest.java
git commit -m "feat: add automatic prompt optimization schema"
```

---

### Task 2: Extract a strict, testable publication policy

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptPublicationPolicy.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptPublicationPolicyTest.java`

**Interfaces:**
- Consumes: experiment summary JSON with `total`, `completed`, `failed`, `baselineMetrics`, and `candidateMetrics`.
- Produces: `Decision evaluate(JSONObject summary, int minimumSuccessful, double maximumFailureRate)` where `Decision` exposes `isEligible()` and `getReason()`.

- [ ] **Step 1: Write failing boundary tests**

Cover these exact outcomes:

```java
assertDecision(summary(10, 10, 0, 12.0, 0.40, 10.0, 0.40), true, "style_mae_improved");
assertDecision(summary(10, 10, 0, 10.0, 0.40, 10.0, 0.50), true, "exact_match_improved");
assertDecision(summary(10, 10, 0, 10.0, 0.50, 10.0, 0.50), false, "no_strict_improvement");
assertDecision(summary(10, 10, 0, 10.0, 0.50, 11.0, 0.60), false, "style_mae_regressed");
assertDecision(summary(10, 10, 0, 10.0, 0.50, 9.0, 0.40), false, "exact_match_regressed");
assertDecision(summary(10, 7, 3, 10.0, 0.50, 9.0, 0.60), false, "failure_rate_exceeded");
assertDecision(summary(2, 1, 1, 10.0, 0.50, 9.0, 0.60), false, "insufficient_successful_samples");
```

Use `minimumSuccessful=2` and `maximumFailureRate=0.20` unless the individual case specifies otherwise.

- [ ] **Step 2: Run the policy test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptPublicationPolicyTest test
```

Expected: FAIL because the policy class is missing.

- [ ] **Step 3: Implement the pure policy**

Use this public contract and deterministic check order:

```java
public final class WardrobePromptPublicationPolicy {
    public Decision evaluate(JSONObject summary, int minimumSuccessful, double maximumFailureRate) {
        int total = summary.getInt("total", 0);
        int completed = summary.getInt("completed", 0);
        int failed = summary.getInt("failed", 0);
        if (completed < minimumSuccessful) return Decision.reject("insufficient_successful_samples");
        if (total <= 0 || failed * 1.0 / total > maximumFailureRate) {
            return Decision.reject("failure_rate_exceeded");
        }
        JSONObject baseline = summary.getJSONObject("baselineMetrics");
        JSONObject candidate = summary.getJSONObject("candidateMetrics");
        double baselineMae = baseline.getDouble("styleMae");
        double candidateMae = candidate.getDouble("styleMae");
        double baselineExact = baseline.getDouble("exactMatchRate");
        double candidateExact = candidate.getDouble("exactMatchRate");
        if (candidateMae > baselineMae) return Decision.reject("style_mae_regressed");
        if (candidateExact < baselineExact) return Decision.reject("exact_match_regressed");
        if (candidateMae < baselineMae) return Decision.publish("style_mae_improved");
        if (candidateExact > baselineExact) return Decision.publish("exact_match_improved");
        return Decision.reject("no_strict_improvement");
    }

    public static final class Decision {
        private final boolean eligible;
        private final String reason;

        private Decision(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }

        static Decision publish(String reason) { return new Decision(true, reason); }
        static Decision reject(String reason) { return new Decision(false, reason); }
        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
    }
}
```

- [ ] **Step 4: Run the policy tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptPublicationPolicyTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the policy task**

```powershell
git add qj_backend/src/main/java/com/qj/services/WardrobePromptPublicationPolicy.java `
        qj_backend/src/test/java/com/qj/services/WardrobePromptPublicationPolicyTest.java
git commit -m "feat: define automatic prompt publication policy"
```

---

### Task 3: Make version switching automatic, auditable, and rollback-safe

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobePromptVersionService.java`
- Modify: `qj_backend/src/test/java/com/qj/services/WardrobePromptVersionServiceTest.java`

**Interfaces:**
- Consumes: publication policy decision, expected base version, optimization ID, authenticated advisor `openid`, and rollback reason.
- Produces:
  - `PromptVersion markEvaluating(Long candidateId)`
  - `PromptVersion findByVersionName(String versionName)` for crash-safe deterministic candidate recovery.
  - `PromptVersion autoPublish(Long candidateId, Long expectedBaseVersionId, Long optimizationId, String reason)`
  - `PromptVersion reject(Long candidateId, String reason)`
  - `PromptVersion rollback(Long retiredId, String advisorOpenid, String reason)`
  - transactional `VersionSwitch` audit rows.

- [ ] **Step 1: Replace manual-publication tests with automatic and rollback tests**

Add assertions that:

```java
service.markEvaluating(candidateId);
service.autoPublish(candidateId, 1L, 91L, "style_mae_improved");
assertEquals(candidateId, service.getActivePrompt().getId());
assertEquals("auto_publish", store.audits.get(0).getStr("action"));
assertEquals("system:auto-optimizer", store.audits.get(0).getStr("actor_id"));

service.rollback(1L, "advisor-openid", "新版本把自然风格判得过高");
assertEquals(1L, service.getActivePrompt().getId());
assertEquals("manual_rollback", store.audits.get(1).getStr("action"));
assertEquals("advisor-openid", store.audits.get(1).getStr("actor_id"));
assertEquals("新版本把自然风格判得过高", store.audits.get(1).getStr("reason"));
```

Also assert blank rollback reason, active rollback target, stale expected base, and non-evaluating auto-publish all throw without changing the active version.

- [ ] **Step 2: Run the focused test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptVersionServiceTest test
```

Expected: FAIL because the new methods and audit store contract do not exist.

- [ ] **Step 3: Add explicit version-switch input and service methods**

Use one immutable switch value passed to the store:

```java
public static final class VersionSwitch {
    final Long targetId;
    final Long expectedActiveId;
    final String action;
    final String actorType;
    final String actorId;
    final String reason;
    final Long optimizationId;
    final Long sampleFloorItemId;

    VersionSwitch(Long targetId, Long expectedActiveId, String action,
                  String actorType, String actorId, String reason,
                  Long optimizationId, Long sampleFloorItemId) {
        this.targetId = targetId;
        this.expectedActiveId = expectedActiveId;
        this.action = action;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.optimizationId = optimizationId;
        this.sampleFloorItemId = sampleFloorItemId;
    }
}
```

`autoPublish` must require candidate status `evaluating`; `rollback` must require target status `retired`, trim and require both advisor identity and reason; `reject` may only move `candidate` or `evaluating` to `rejected`.

`findByVersionName` returns null when absent. When present, `resumeGeneration` may reuse it only if its `parent_version_id` matches the job base and its source is `ai_generated`; a mismatch marks the job failed instead of attaching an unrelated version.

- [ ] **Step 4: Make activation and audit one Hutool transaction**

Change the store interface to:

```java
void activate(VersionSwitch change) throws SQLException;
void changeStatus(Long id, String expectedStatus, String targetStatus, String reason) throws SQLException;
```

Inside `HutoolStore.activate`, lock the active and target rows, compare the active ID to `expectedActiveId` when non-null, switch statuses, then insert `qj_wardrobe_prompt_version_audit` before committing. For rollback, query `SELECT COALESCE(MAX(id),0) FROM qj_wardrobe_item WHERE status='completed' FOR UPDATE` inside this transaction and write that value as `sample_floor_item_id`. For auto-publication, update the referenced optimization row from `evaluating` to `published` inside the same transaction as the version switch and audit. Any error rolls back the job update, switch, and audit together.

- [ ] **Step 5: Run version and schema tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptVersionServiceTest,WardrobePromptAutomaticSchemaContractTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the version task**

```powershell
git add qj_backend/src/main/java/com/qj/services/WardrobePromptVersionService.java `
        qj_backend/src/test/java/com/qj/services/WardrobePromptVersionServiceTest.java
git commit -m "feat: audit automatic prompt switches and advisor rollback"
```

---

### Task 4: Prepare automatic jobs with disjoint immutable sample snapshots

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobePromptOptimizationService.java`
- Modify: `qj_backend/src/test/java/com/qj/services/WardrobePromptOptimizationServiceTest.java`

**Interfaces:**
- Consumes: reviewed `positive`/`negative` rows, latest optimization sample floor, latest rollback floor, environment thresholds, and current active Prompt.
- Produces:
  - `JSONObject startAutomatic()` returning null when thresholds are unmet.
  - `JSONObject getActiveAutomaticJob()`.
  - `JSONArray listJobs(int limit)`.
  - `JSONObject refreshHeldout(Long jobId)` to move a waiting job back to `generating` once a disjoint held-out set exists.
  - `JSONObject resumeGeneration(Long jobId)` to attach or recover the deterministic candidate for that job.
  - state methods `markEvaluating`, `markRejected`, `markStale`, and `markFailed`.
  - `sampleIds` and `heldoutSampleIds` snapshots with zero intersection.

- [ ] **Step 1: Write failing automatic-job tests**

Add focused cases for:

```java
assertNull(service.startAutomatic()); // only three new samples

JSONObject job = serviceWithSixRows.startAutomatic();
assertEquals("generating", job.getStr("status"));
assertEquals("automatic", job.getStr("triggerType"));
assertEquals(4, job.getJSONArray("sampleIds").size());
assertEquals(2, job.getJSONArray("heldoutSampleIds").size());
assertTrue(Collections.disjoint(
        job.getJSONArray("sampleIds").toList(Long.class),
        job.getJSONArray("heldoutSampleIds").toList(Long.class)));
```

Also test that positive/negative minimums are enforced, a rollback `sample_floor_item_id` suppresses old samples, duplicate insert caused by the running guard returns the existing job, and candidate-generation failure marks only the job failed.

- [ ] **Step 2: Run the optimization test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptOptimizationServiceTest test
```

Expected: FAIL because automatic selection and held-out snapshots are absent.

- [ ] **Step 3: Add automatic configuration to the constructor**

Use these properties:

```java
@Value("${wardrobe.prompt.auto.minimum-new-samples:4}") int minimumNewSamples,
@Value("${wardrobe.prompt.auto.minimum-positive:1}") int minimumPositive,
@Value("${wardrobe.prompt.auto.minimum-negative:1}") int minimumNegative,
@Value("${wardrobe.prompt.auto.minimum-heldout:2}") int minimumHeldout,
@Value("${wardrobe.prompt.auto.maximum-training-samples:50}") int maximumTrainingSamples
```

Keep a package-private constructor receiving the numeric values so tests do not require Spring.

- [ ] **Step 4: Implement deterministic sample splitting and snapshots**

Query eligible rows ordered by `review_time DESC, id DESC`. Determine the trigger floor as the maximum of the latest automatic job `sample_max_item_id` and latest rollback audit `sample_floor_item_id`. Require enough rows above that floor and the configured positive/negative counts.

Reserve the oldest `minimumHeldout` rows from the fetched candidate pool as held-out rows, then use up to `maximumTrainingSamples` remaining rows for candidate generation. Persist both ID arrays and this threshold snapshot before calling Gemini:

```java
JSONObject thresholds = new JSONObject()
        .set("minimumNewSamples", minimumNewSamples)
        .set("minimumPositive", minimumPositive)
        .set("minimumNegative", minimumNegative)
        .set("minimumHeldout", minimumHeldout)
        .set("maximumTrainingSamples", maximumTrainingSamples);
```

If held-out rows are insufficient, create or retain a `waiting_for_holdout` job without generating or publishing a candidate. After candidate generation succeeds, keep the job in `generating` and persist `candidate_prompt_version_id`; the state machine distinguishes “generation in progress” from “candidate ready” by whether that ID is null. `resumeGeneration` uses the deterministic name `wardrobe-ai-candidate-{jobId}` and first reuses a matching existing candidate before calling Gemini, so a crash between candidate insertion and job update cannot create another version. Never silently reuse a training ID as held-out.

- [ ] **Step 5: Add state and list persistence methods**

Extend the store with exact conditional updates:

```java
boolean transition(Long id, String from, String to, Entity values) throws SQLException;
Entity findActiveAutomaticJob() throws SQLException;
List<Entity> listAutomaticJobs(int limit) throws SQLException;
long latestAutomaticSampleFloor() throws SQLException;
long latestRollbackSampleFloor() throws SQLException;
```

Every transition must include `WHERE id = ? AND status = ?`; a zero-row update means another worker advanced the job and is not retried as a new operation.

- [ ] **Step 6: Run optimization tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptOptimizationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit the automatic job task**

```powershell
git add qj_backend/src/main/java/com/qj/services/WardrobePromptOptimizationService.java `
        qj_backend/src/test/java/com/qj/services/WardrobePromptOptimizationServiceTest.java
git commit -m "feat: snapshot automatic prompt optimization jobs"
```

---

### Task 5: Refactor isolated evaluation to consume the saved held-out set

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobePromptExperimentService.java`
- Modify: `qj_backend/src/test/java/com/qj/services/WardrobePromptExperimentServiceTest.java`

**Interfaces:**
- Consumes: `candidateId`, `optimizationId`, and that job's persisted `heldout_sample_ids_json`.
- Produces: idempotent experiment rows and summary metrics; does not mark a Prompt publishable or active.

- [ ] **Step 1: Change tests to require explicit held-out IDs and no publication side effect**

Use these assertions:

```java
JSONObject result = service.start(2L, 10L);
assertEquals(List.of(3L, 4L), store.insertedWardrobeIds);
assertEquals(0, store.wardrobeMutationCount);
assertEquals(0, prompts.statusMutationCount);
assertEquals("completed", result.getStr("status"));
```

Add failures for an empty held-out snapshot, an ID that also appears in `sample_ids_json`, and a held-out row missing advisor data. Retain the executor-interruption recovery test.

- [ ] **Step 2: Run the experiment test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptExperimentServiceTest test
```

Expected: FAIL because `start` still selects held-out rows itself and marks candidates ready.

- [ ] **Step 3: Change the public experiment contract**

Replace the manual sample-limit API with:

```java
public JSONObject start(Long candidateId, Long optimizationId) throws SQLException;
public JSONObject get(Long candidateId, Long optimizationId) throws SQLException;
```

Load both training and held-out arrays from the optimization row, assert they are non-empty and disjoint, then call `Store.findSamplesByIds(List<Long> ids)` preserving snapshot order. Remove `promoteIfEligible` and every call to `markReadyForReview`.

- [ ] **Step 4: Keep summary calculation side-effect free**

Return `status`, counts, errors, `baselineMetrics`, and `candidateMetrics`. The policy in Task 2 becomes the only code that determines publication eligibility; remove `publishEligible` and `recommendation` from the experiment service to avoid two competing policies.

- [ ] **Step 5: Run experiment and policy tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptExperimentServiceTest,WardrobePromptPublicationPolicyTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the experiment task**

```powershell
git add qj_backend/src/main/java/com/qj/services/WardrobePromptExperimentService.java `
        qj_backend/src/test/java/com/qj/services/WardrobePromptExperimentServiceTest.java
git commit -m "refactor: isolate automatic prompt experiments"
```

---

### Task 6: Add the recoverable backend auto-optimizer and review event

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobeReviewCompletedEvent.java`
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptAutoOptimizer.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptAutoOptimizerTest.java`
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobeService.java`
- Modify: `qj_backend/src/test/java/com/qj/services/WardrobeServiceSinglePassTest.java`
- Modify: `qj_backend/src/main/resources/application.properties`
- Modify: `qj_backend/src/main/resources/application-dev.properties`
- Modify: `qj_backend/src/main/resources/application-prod.properties`

**Interfaces:**
- Consumes: `WardrobeReviewCompletedEvent`, automatic jobs, experiment summaries, policy decisions, and version service.
- Produces: asynchronous `requestRun()`, idempotent `advanceOnce()`, and scheduled recovery using the same state machine.

- [ ] **Step 1: Write failing state-machine tests**

Name the successful-publication test `improvedCandidateIsAutoPublished` and test one transition per call:

```java
assertEquals("idle", optimizer.advanceOnce().getStr("action"));
assertEquals("candidate_created", optimizerWithThresholds.advanceOnce().getStr("action"));
assertEquals("evaluation_started", optimizerWithGeneratedCandidate.advanceOnce().getStr("action"));
assertEquals("published", optimizerWithImprovedEvaluation.advanceOnce().getStr("action"));
assertEquals("rejected", optimizerWithEqualEvaluation.advanceOnce().getStr("action"));
assertEquals("stale", optimizerWhoseBaseChanged.advanceOnce().getStr("action"));
```

Also assert two simultaneous `requestRun()` calls schedule only one local worker and that restarting with an `evaluating` job resumes it instead of creating a new job.

- [ ] **Step 2: Run the auto-optimizer test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptAutoOptimizerTest test
```

Expected: FAIL because the optimizer class is missing.

- [ ] **Step 3: Add the event payload and asynchronous guard**

```java
public final class WardrobeReviewCompletedEvent {
    private final Long itemId;
    public WardrobeReviewCompletedEvent(Long itemId) { this.itemId = itemId; }
    public Long getItemId() { return itemId; }
}
```

`WardrobePromptAutoOptimizer` must use `@EventListener`, `@Scheduled`, the existing `applicationTaskExecutor`, and an `AtomicBoolean workerScheduled`. Both entry points call `requestRun()`; the worker loops `advanceOnce()` only while an actual transition occurred and always clears the local guard in `finally`.

The worker stops on actions `idle`, `waiting_for_holdout`, or `evaluation_running`; all other actions immediately advance the next persisted state. This prevents a busy loop while still allowing candidate generation to flow directly into evaluation.

- [ ] **Step 4: Implement the idempotent state machine**

Use this transition order:

```java
public JSONObject advanceOnce() throws Exception {
    JSONObject job = optimizations.getActiveAutomaticJob();
    if (job == null) {
        JSONObject created = optimizations.startAutomatic();
        return action(created == null ? "idle" : "candidate_created", created);
    }
    if ("waiting_for_holdout".equals(job.getStr("status"))) {
        JSONObject refreshed = optimizations.refreshHeldout(job.getLong("id"));
        String action = "generating".equals(refreshed.getStr("status"))
                ? "heldout_ready" : "waiting_for_holdout";
        return action(action, refreshed);
    }
    if ("pending".equals(job.getStr("status"))
            || ("generating".equals(job.getStr("status"))
                && job.getLong("candidatePromptVersionId") == null)) {
        return action("candidate_created", optimizations.resumeGeneration(job.getLong("id")));
    }
    if ("generating".equals(job.getStr("status"))
            && job.getLong("candidatePromptVersionId") != null) {
        prompts.markEvaluating(job.getLong("candidatePromptVersionId"));
        optimizations.markEvaluating(job.getLong("id"));
        return action("evaluation_started", experiments.start(
                job.getLong("candidatePromptVersionId"), job.getLong("id")));
    }
    if ("evaluating".equals(job.getStr("status"))) {
        return finishEvaluation(job, experiments.get(
                job.getLong("candidatePromptVersionId"), job.getLong("id")));
    }
    return action("idle", job);
}
```

`finishEvaluation` leaves a running experiment alone. For a completed summary, evaluate Task 2's policy and call `autoPublish` with the expected base ID; `autoPublish` atomically switches versions, writes the audit row, and marks the job `published`. Otherwise call `reject` and `markRejected`. If the base changed, call `markStale` and never switch versions. Exceptions mark only the job failed and leave the active Prompt unchanged.

- [ ] **Step 5: Publish exactly one reviewed-item event**

Add `ApplicationEventPublisher` to the production `WardrobeService` constructor. Change completion to:

```java
boolean completed = store.complete(itemId, advisor, sampleType, diff, reviewerOpenid.trim());
if (completed && sampleType != null) {
    events.publishEvent(new WardrobeReviewCompletedEvent(itemId));
}
return getItem(itemId);
```

Package-private test constructors receive a no-op publisher by default. Extend `WardrobeServiceSinglePassTest` so a positive or negative first completion publishes once, duplicate completion publishes zero additional events, and `ai_failed` manual completion publishes none.

- [ ] **Step 6: Add environment-specific properties**

Shared `application.properties`:

```properties
wardrobe.prompt.auto.enabled=${WARDROBE_PROMPT_AUTO_ENABLED:true}
wardrobe.prompt.auto.recovery-delay-ms=${WARDROBE_PROMPT_AUTO_RECOVERY_DELAY_MS:60000}
wardrobe.prompt.auto.maximum-failure-rate=${WARDROBE_PROMPT_AUTO_MAX_FAILURE_RATE:0.20}
wardrobe.prompt.auto.maximum-training-samples=${WARDROBE_PROMPT_AUTO_MAX_TRAINING_SAMPLES:50}
```

Development/test profile:

```properties
wardrobe.prompt.auto.minimum-new-samples=4
wardrobe.prompt.auto.minimum-positive=1
wardrobe.prompt.auto.minimum-negative=1
wardrobe.prompt.auto.minimum-heldout=2
```

Production profile:

```properties
wardrobe.prompt.auto.minimum-new-samples=20
wardrobe.prompt.auto.minimum-positive=3
wardrobe.prompt.auto.minimum-negative=3
wardrobe.prompt.auto.minimum-heldout=10
```

- [ ] **Step 7: Run event and optimizer tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobeServiceSinglePassTest,WardrobePromptAutoOptimizerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit the orchestration task**

```powershell
git add qj_backend/src/main/java/com/qj/services/WardrobeReviewCompletedEvent.java `
        qj_backend/src/main/java/com/qj/services/WardrobePromptAutoOptimizer.java `
        qj_backend/src/test/java/com/qj/services/WardrobePromptAutoOptimizerTest.java `
        qj_backend/src/main/java/com/qj/services/WardrobeService.java `
        qj_backend/src/test/java/com/qj/services/WardrobeServiceSinglePassTest.java `
        qj_backend/src/main/resources/application.properties `
        qj_backend/src/main/resources/application-dev.properties `
        qj_backend/src/main/resources/application-prod.properties
git commit -m "feat: automate wardrobe prompt optimization"
```

---

### Task 7: Expose read-only automatic status and reasoned advisor rollback

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java`
- Create: `qj_backend/src/test/java/com/qj/controllers/WardrobePromptControllerTest.java`

**Interfaces:**
- Consumes: advisor actor from `WardrobePromptAuthInterceptor.ACTOR_ATTRIBUTE`.
- Produces:
  - `GET /qj/wardrobe/prompts`
  - `GET /qj/wardrobe/prompts/{id}`
  - `GET /qj/wardrobe/prompts/optimization-jobs?limit=20`
  - `GET /qj/wardrobe/prompts/optimization-jobs/{id}`
  - `POST /qj/wardrobe/prompts/{id}/rollback` with `{ "reason": "..." }`
  - explicit disabled responses for old manual command endpoints.

- [ ] **Step 1: Write failing MockMvc controller tests**

Assert that a rollback with request actor `advisor-openid` passes that actor and body reason to the service; blank reason fails; missing actor fails; job list is readable. Assert these commands return failure text `Prompt optimization is automatic` and never call services:

```text
POST /qj/wardrobe/prompts/candidates
POST /qj/wardrobe/prompts/optimization-jobs
POST /qj/wardrobe/prompts/{id}/evaluate
POST /qj/wardrobe/prompts/{id}/publish
```

- [ ] **Step 2: Run the controller test and verify it fails**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptControllerTest test
```

Expected: FAIL because rollback does not accept a reason and no job-list endpoint exists.

- [ ] **Step 3: Implement the controller contract**

Rollback must parse and pass the reason:

```java
@PostMapping("/{id}/rollback")
public AgentResponse rollback(@PathVariable Long id, @RequestBody String body,
                              HttpServletRequest request) {
    try {
        String reason = JSONUtil.parseObj(body).getStr("reason");
        return AgentResponse.success("Prompt rolled back",
                promptService.rollback(id, actor(request), reason).toJson());
    } catch (Exception error) {
        return AgentResponse.fail(error.getMessage());
    }
}
```

Keep all Prompt/statistics paths behind the existing `WardrobePromptAuthInterceptor`; do not trust actor IDs from request JSON.

Add the read-only list and one shared disabled response:

```java
@GetMapping("/optimization-jobs")
public AgentResponse optimizationJobs(
        @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
    try {
        return AgentResponse.success("Query succeeded", optimizationService.listJobs(limit));
    } catch (Exception error) {
        return AgentResponse.fail(error.getMessage());
    }
}

private AgentResponse automaticOnly() {
    return AgentResponse.fail("Prompt optimization is automatic");
}
```

Map the four legacy POST routes listed in Step 1 to `automaticOnly()` during the compatibility period. Keep GET job detail and evaluation detail only if the mini program still needs them to display persisted historical jobs.

- [ ] **Step 4: Run controller and auth tests**

```powershell
.\mvnw.cmd -q -Dtest=WardrobePromptControllerTest,WardrobePromptAuthServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the API task**

```powershell
git add qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java `
        qj_backend/src/test/java/com/qj/controllers/WardrobePromptControllerTest.java
git commit -m "feat: expose automatic prompt status and advisor rollback"
```

---

### Task 8: Convert the advisor Prompt center to status viewing and rollback only

**Files:**
- Modify: `D:\project\minip-client\miniprogram\utils\wardrobePromptCenter.js`
- Modify: `D:\project\minip-client\miniprogram\utils\wardrobePromptCenter.test.js`
- Modify: `D:\project\minip-client\miniprogram\pages\pkg-admin\consult\page\admin\evaluation\prompt\index.js`
- Modify: `D:\project\minip-client\miniprogram\pages\pkg-admin\consult\page\admin\evaluation\prompt\index.wxml`
- Modify: `D:\project\minip-client\miniprogram\pages\pkg-admin\consult\page\admin\evaluation\prompt\index.wxss`

**Interfaces:**
- Consumes: version list, automatic job list, effect statistics, advisor token, and reasoned rollback endpoint.
- Produces: localized automatic statuses, metrics/decision display, and rollback reason dialog for retired versions.

- [ ] **Step 1: Rewrite the failing Node contract**

Require these utility results:

```javascript
assert.strictEqual(center.statusText('evaluating'), '自动评估中')
assert.strictEqual(center.statusText('rejected'), '未通过门槛')
assert.strictEqual(center.jobStatusText('published'), '已自动发布')
assert.strictEqual(center.jobStatusText('waiting_for_holdout'), '等待隔离测试样本')
assert.strictEqual(center.rollbackReason('  风格偏差过大  '), '风格偏差过大')
assert.throws(() => center.rollbackReason('   '), /回滚原因/)
```

Static assertions must require `/optimization-jobs` GET and `/rollback`, while rejecting `generateCandidate`, `evaluateCandidate`, `publishVersion`, `/evaluate`, and `/publish` from the page source and visible WXML.

- [ ] **Step 2: Run the Node contract and verify it fails**

```powershell
cd D:\project\minip-client
node miniprogram/utils/wardrobePromptCenter.test.js
```

Expected: FAIL because manual controls still exist and new status helpers are absent.

- [ ] **Step 3: Implement pure formatting and rollback validation**

Update status maps for `candidate`, `evaluating`, `active`, `retired`, and `rejected`. Add:

```javascript
function rollbackReason(value) {
  const reason = String(value || '').trim()
  if (!reason) throw new Error('请填写回滚原因')
  if (reason.length > 1000) throw new Error('回滚原因不能超过1000字')
  return reason
}

function jobRows(values) {
  return (values || []).map(item => ({
    ...item,
    statusLabel: jobStatusText(item.status),
    decisionLabel: decisionText(item.decisionReason),
    sampleCount: (item.sampleIds || []).length,
    heldoutSampleCount: (item.heldoutSampleIds || []).length
  }))
}
```

Export both functions and deterministic `jobStatusText`/`decisionText` helpers.

- [ ] **Step 4: Remove manual commands and add automatic job loading**

Delete candidate generation, evaluation polling, and publication methods/state. Add `jobs` and `rollbackReason` state, load jobs with:

```javascript
const values = await this.request({
  url: '/qj/wardrobe/prompts/optimization-jobs?limit=20',
  method: 'GET'
})
this.setData({ jobs: center.jobRows(Array.isArray(values) ? values : []) })
```

Rollback must validate and send the reason:

```javascript
const reason = center.rollbackReason(this.data.rollbackReason)
await this.request({
  url: `/qj/wardrobe/prompts/${version.id}/rollback`,
  method: 'POST',
  data: { reason }
})
```

Use a WXML textarea in the existing version detail panel rather than `wx.showModal` input, so the reason is visible and editable before confirmation. Disable rollback while the reason is blank or a request is running.

```xml
<view class="rollback-box" wx:if="{{selectedVersion.rollbackable}}">
  <textarea class="rollback-reason"
            maxlength="1000"
            value="{{rollbackReason}}"
            bindinput="bindRollbackReason"
            placeholder="请填写回滚原因" />
  <button class="cu-btn bg-orange"
          disabled="{{commandRunning || !rollbackReason}}"
          bindtap="rollbackVersion">回滚到此版本</button>
  <view class="command-note">只影响之后上传的衣物</view>
</view>
```

- [ ] **Step 5: Render automatic jobs and version decisions**

The version tab must show current active version first, then automatic tasks with training count, held-out count, baseline/candidate metrics, and localized decision reason. Remove all text and buttons for `生成候选Prompt`, `离线评测`, and `发布`. Keep the existing warning that version changes only affect future uploads.

```xml
<view class="job-item" wx:for="{{jobs}}" wx:key="id">
  <view class="job-head">
    <text>自动优化 #{{item.id}}</text>
    <text class="status status-{{item.status}}">{{item.statusLabel}}</text>
  </view>
  <view>训练 {{item.sampleCount}} · 隔离测试 {{item.heldoutSampleCount}}</view>
  <view wx:if="{{item.decisionLabel}}">{{item.decisionLabel}}</view>
</view>
```

- [ ] **Step 6: Run mini-program checks**

```powershell
node miniprogram/utils/wardrobePromptCenter.test.js
node miniprogram/utils/wardrobeAiReview.test.js
node --check miniprogram/utils/wardrobePromptCenter.js
node --check miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.js
node --check cloudfunctions/login/login.js
```

Expected: all commands exit 0 and the two contract tests print their success messages.

- [ ] **Step 7: Commit only the mini-program feature files**

Do not stage existing dirty `project.config.json`, `project.private.config.json`, or unrelated `docs/` files.

```powershell
git add miniprogram/utils/wardrobePromptCenter.js `
        miniprogram/utils/wardrobePromptCenter.test.js `
        miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.js `
        miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxml `
        miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxss
git commit -m "feat: make prompt optimization advisor view automatic"
```

---

### Task 9: Apply to the test database, verify end to end, and update operations documentation

**Files:**
- Modify: `qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md`

**Interfaces:**
- Consumes: completed backend and mini-program work, test database `qj_minip_v2_t`, local services on ports 7077 and 8090, and test advisor identity.
- Produces: reproducible migration/rollback instructions and recorded acceptance evidence.

- [ ] **Step 1: Run the complete backend suite before touching the database**

```powershell
cd D:\project\qjapiv2\qj_backend
.\mvnw.cmd -q test
```

Expected: BUILD SUCCESS with all existing and new wardrobe tests passing.

- [ ] **Step 2: Apply the new migration to `qj_minip_v2_t` only**

Use the repository's configured MySQL client/connection and execute `migration_wardrobe_prompt_automatic.sql`. Verify:

```sql
SELECT COUNT(*) AS active_count
FROM qj_wardrobe_prompt_version WHERE status = 'active';

SHOW INDEX FROM qj_wardrobe_prompt_optimization
WHERE Key_name = 'uk_prompt_optimization_one_running';

SHOW TABLES LIKE 'qj_wardrobe_prompt_version_audit';
```

Expected: `active_count = 1`, one running-guard index row, and the audit table exists. Re-run the migration once and verify it succeeds without duplicate-column/index errors.

- [ ] **Step 3: Start the backend with test-only thresholds and external secrets**

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:WARDROBE_PROMPT_AUTO_ENABLED = 'true'
$env:WARDROBE_PROMPT_AUTH_REQUIRED = 'false'
.\mvnw.cmd spring-boot:run
```

Expected: backend listens on `http://127.0.0.1:7077`, health returns success, and logs show no schema or scheduler startup error. Keep the real Gemini key in the environment without printing or committing it.

- [ ] **Step 4: Verify automatic rejection without business-data mutation**

Create six dedicated reviewed test items: four training candidates with both positive and negative labels plus two held-out samples. Capture item/history row counts and hashes before evaluation. Wait for the job to finish, then verify:

```sql
SELECT status, base_prompt_version_id, candidate_prompt_version_id,
       decision_reason, sample_ids_json, heldout_sample_ids_json
FROM qj_wardrobe_prompt_optimization ORDER BY id DESC LIMIT 1;

SELECT COUNT(*) FROM qj_wardrobe_prompt_version WHERE status = 'active';
```

Expected for a deliberately equal or worse candidate: job `rejected`, one active Prompt, disjoint snapshot arrays, and unchanged business item/history counts and hashes during offline evaluation.

- [ ] **Step 5: Verify automatic publication and future-only effect**

Run the deterministic publication and future-version tests created in Tasks 3 and 6:

```powershell
.\mvnw.cmd -q `
  -Dtest=WardrobePromptAutoOptimizerTest#improvedCandidateIsAutoPublished,WardrobeServiceSinglePassTest#eachItemKeepsThePromptVersionActiveWhenItWasCreated `
  test
```

Expected: PASS; the optimizer test asserts job `published`, audit action `auto_publish`, and one active version, while the wardrobe test asserts old and new items retain the Prompt version active at their own creation time.

- [ ] **Step 6: Verify advisor rollback and cooldown**

From the mini-program Prompt center, choose a retired version, enter `测试回滚：候选风格偏差过高`, and confirm. Verify:

```sql
SELECT action, actor_type, actor_id, reason, sample_floor_item_id
FROM qj_wardrobe_prompt_version_audit ORDER BY id DESC LIMIT 1;
```

Expected: `manual_rollback`, `advisor`, the current advisor `openid`, exact reason text, and a non-null floor. Confirm no new optimization starts until four new qualifying reviewed samples are added in the dev profile.

- [ ] **Step 7: Verify mini-program behavior and compilation**

Confirm in WeChat DevTools that the Prompt center has no generate/evaluate/publish controls, displays automatic task decisions, accepts a reason for retired-version rollback, and shows authentication failures without endless loading. Then run:

```powershell
& 'C:\Program Files (x86)\Tencent\微信web开发者工具\cli.bat' preview `
  --project 'D:\project\minip-client'
```

Expected: the CLI exits 0 and reports a successful preview compilation.

- [ ] **Step 8: Update deployment documentation**

Replace the manual API table in `DEPLOY_WARDROBE_AI_REVIEW.md` with the automatic trigger, profile thresholds, new migration order, job states, publication policy, all-advisor rollback, audit query, cooldown behavior, and recovery scheduler. Explicitly state that production migration is not applied until test acceptance.

- [ ] **Step 9: Re-run full regression and inspect diffs**

```powershell
cd D:\project\qjapiv2\qj_backend
.\mvnw.cmd -q test
git -C D:\project\qjapiv2 diff --check

cd D:\project\minip-client
node miniprogram/utils/wardrobeAiReview.test.js
node miniprogram/utils/wardrobePromptCenter.test.js
git -C D:\project\minip-client diff --check
```

Expected: all tests pass and both `diff --check` commands emit no whitespace errors.

- [ ] **Step 10: Commit the operations document**

```powershell
cd D:\project\qjapiv2
git add qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md
git commit -m "docs: deploy automatic wardrobe prompt optimization"
```

Do not commit generated logs, database settings, local images, DevTools private config, or test credentials.
