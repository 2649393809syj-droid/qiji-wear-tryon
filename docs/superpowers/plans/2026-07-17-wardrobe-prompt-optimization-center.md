# Wardrobe Prompt Optimization Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn reviewed positive/negative wardrobe samples into versioned effectiveness statistics, AI-generated candidate Prompts, offline evaluation, manual publication, and rollback.

**Architecture:** A prompt repository resolves exactly one active version for new recognition requests. Statistics are computed from completed single-pass samples and their structured diffs. Optimization jobs snapshot selected sample IDs, ask Gemini for a candidate Prompt, test it in an isolated experiment table, and require an explicit advisor/admin publish action.

**Tech Stack:** Java 17 Spring Boot 2.7/Hutool, MySQL 8, Gemini generateContent API, WeChat Mini Program advisor pages, JUnit 5, Node `assert` tests.

## Global Constraints

- Candidate Prompts never become active automatically.
- Publishing affects only AI jobs created after publication.
- Rollback retains all versions and historical version references.
- Statistics exclude pending reviews and separate technical failures from positive/negative accuracy.
- Offline evaluation never changes wardrobe business records or creates a second business recognition round.
- Optimization and evaluation sample sets are disjoint.

---

### Task 1: Prompt Version and Optimization Schema

**Files:**
- Create: `qj_backend/src/main/resources/migration_wardrobe_prompt_optimization.sql`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptSchemaContractTest.java`

**Interfaces:**
- Produces: `qj_wardrobe_prompt_version`, `qj_wardrobe_prompt_optimization`, and `qj_wardrobe_prompt_experiment_result`.

- [ ] Write a failing SQL contract test for every specified column, index, status, and foreign-key-like reference.
- [ ] Run `mvn -Dtest=WardrobePromptSchemaContractTest test` and verify failure.
- [ ] Add idempotent schema and seed `wardrobe-style-legacy-v1` as the sole active version using the aligned Prompt from core Task 4.
- [ ] Apply migration to `qj_minip_v2_t` and verify one active version.
- [ ] Run the contract test and commit with `feat: add wardrobe prompt optimization schema`.

### Task 2: Active Prompt Repository, Publish, and Rollback

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptVersionService.java`
- Create: `qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptVersionServiceTest.java`
- Modify: `qj_backend/src/main/java/com/qj/services/GeminiStyleRecognitionService.java`
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobeService.java`

**Interfaces:**
- Produces: `PromptVersion getActivePrompt()`.
- Produces: list/detail/create-candidate/publish/rollback endpoints under `/qj/wardrobe/prompts`.
- Consumes: `prompt_version_id`, version name, and exact prompt text for every new AI item.

- [ ] Write failing tests for one active version, transactional publish, rollback, unknown version rejection, and historical item version immutability.
- [ ] Run focused tests and verify failure.
- [ ] Implement prompt resolution and inject active Prompt text into Gemini recognition.
- [ ] Implement transactional publish/rollback so the target becomes active and the previous active version becomes retired.
- [ ] Run focused tests and `mvn test`; commit with `feat: version wardrobe recognition prompts`.

### Task 3: Global Effectiveness Statistics

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobeAiStatisticsService.java`
- Create: `qj_backend/src/main/java/com/qj/controllers/WardrobeAiStatisticsController.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobeAiStatisticsServiceTest.java`

**Interfaces:**
- Produces: `GET /qj/wardrobe/statistics?from=&to=&promptVersionId=&navItemCur=`.
- Produces: totals, AI success/failure rate, positive rate, style MAE, omission/false-positive rates, field correction rates, and category breakdown.

- [ ] Write failing fixture-based tests with pending, failed, positive, and negative samples across two Prompt versions.
- [ ] Verify pending records are excluded from reviewed denominators and failures are excluded from positive-rate denominators.
- [ ] Implement parameterized aggregate queries and deterministic JSON output.
- [ ] Run focused and full backend tests; commit with `feat: report wardrobe AI effectiveness`.

### Task 4: Generate a Candidate Prompt from Snapshot Samples

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptOptimizationService.java`
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptCandidateGenerator.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptOptimizationServiceTest.java`
- Modify: `qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java`

**Interfaces:**
- Produces: `POST /qj/wardrobe/prompts/optimization-jobs` with date range and optional filters.
- Produces: immutable sample ID snapshot, analysis summary, candidate Prompt, and `candidate` version.

- [ ] Write failing tests for minimum reviewed sample validation, disjoint sample selection, representative positive/negative serialization, job failure isolation, and candidate-not-active invariant.
- [ ] Implement sample snapshot selection and a compact Gemini optimization request containing current Prompt, aggregates, positives, and corrected negatives.
- [ ] Parse Gemini output as `{candidatePrompt, summary}` and save it as a candidate version without publishing.
- [ ] Run focused/full tests and commit with `feat: generate wardrobe prompt candidates`.

### Task 5: Offline Candidate Evaluation

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobePromptExperimentService.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobePromptExperimentServiceTest.java`
- Modify: `qj_backend/src/main/java/com/qj/controllers/WardrobePromptController.java`

**Interfaces:**
- Produces: `POST /qj/wardrobe/prompts/{candidateId}/evaluate`.
- Produces: baseline/candidate metrics over held-out historical images in experiment tables only.

- [ ] Write failing tests proving optimization sample IDs cannot enter evaluation, wardrobe item/history rows are untouched, and baseline/candidate metrics use advisor final results as labels.
- [ ] Implement background evaluation with progress, per-image errors, aggregate metrics, and cost-bounded sample limits.
- [ ] Mark candidate `ready_for_review` only when evaluation finishes; never publish automatically.
- [ ] Run focused/full tests and commit with `feat: evaluate wardrobe prompt candidates offline`.

### Task 6: Advisor Prompt Optimization Center

**Files:**
- Create: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.js`
- Create: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxml`
- Create: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.wxss`
- Create: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/prompt/index.json`
- Modify: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.js`
- Modify: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/home/home.wxml`
- Modify: `D:/project/minip-client/miniprogram/app.json`
- Create: `D:/project/minip-client/miniprogram/utils/wardrobePromptCenter.test.js`

**Interfaces:**
- Consumes: statistics, versions, optimization job, evaluation, publish, and rollback APIs.
- Produces: a separate “Prompt优化” entry from 评测管理.

- [ ] Write a failing Node source contract test for statistics tabs, version states, candidate generation, evaluation metrics, publish confirmation, and rollback confirmation.
- [ ] Build a work-focused page with tabs for “效果统计” and “Prompt版本”; use compact metric rows, filters, version list, diff/detail modal, and explicit command buttons.
- [ ] Require modal confirmation for publish and rollback; display that changes affect only future uploads.
- [ ] Poll only running optimization/evaluation jobs and stop polling when the page is hidden.
- [ ] Run `node miniprogram/utils/wardrobePromptCenter.test.js` and WeChat DevTools build; commit with `feat: add wardrobe prompt optimization center`.

### Task 7: Prompt Optimization End-to-End Verification

**Files:**
- Modify: `qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md`

**Interfaces:**
- Consumes: all optimization tasks.
- Produces: repeatable evidence for statistics, candidate safety, publishing, and rollback.

- [ ] Seed reviewed positive/negative samples across two categories and verify statistics by direct SQL and API comparison.
- [ ] Generate a candidate and verify active Prompt remains unchanged.
- [ ] Run offline evaluation and verify no wardrobe item/history row changed.
- [ ] Publish the candidate, upload a new item, and verify only the new item references the new version.
- [ ] Roll back, upload another item, and verify it references the restored version while prior history remains unchanged.
- [ ] Run `mvn test`, both mini-program Node tests, and WeChat build; update deployment documentation and commit with `docs: verify wardrobe prompt optimization flow`.

