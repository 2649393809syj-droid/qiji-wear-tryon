# Wardrobe AI Single-Pass Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-item AI return/retry with one immutable AI judgement, one advisor confirmation, aligned legacy evaluation JSON, and automatic positive/negative sample classification.

**Architecture:** The legacy `qjapi` upload endpoint returns the inserted wardrobe `autoId`. The mini-program sends that ID to `qjapiv2`, which creates one idempotent AI record and stores a canonical legacy-shaped evaluation snapshot. The advisor submits the legacy evaluation once, then idempotently completes the AI record with a normalized final snapshot and computed field-level diff.

**Tech Stack:** Java 8 Spark/Hutool legacy API, Java 17 Spring Boot 2.7/Hutool new API, MySQL 8, WeChat Mini Program JavaScript/WXML/WXSS, JUnit 5, Node `assert` tests.

## Global Constraints

- One wardrobe `autoId` has at most one business AI judgement.
- Gemini transport/model retries do not create additional business rounds.
- Eight style scores are independent 0-100 relevance values and do not sum to 100.
- Normally only one or two styles are non-zero.
- AI, advisor, history, and legacy submission use one canonical legacy evaluation shape.
- Advisor changes affect the current final evaluation but never trigger a second business recognition.
- Positive means no normalized AI field changed; negative means at least one AI field changed; technical failure is neither.
- Existing multi-round history remains readable and is not deleted.

---

### Task 1: Return the Legacy Wardrobe `autoId`

**Files:**
- Modify: `D:/project/qjapi/src/main/java/api/service/WardrobeV4Service.java:76`
- Test: `D:/project/qjapi/src/test/java/api/service/WardrobeV4ServiceTest.java`

**Interfaces:**
- Produces: upload response data `{ "autoId": Integer, "navItemCur": 1|2 }` while retaining `code == 0` success semantics.
- Consumes: `Db.insertForGeneratedKey(Entity)` and existing clothing/ornament table selection.

- [ ] **Step 1: Add a failing upload response test**

Create a test seam around table selection and response construction, then assert that successful insertion returns the generated key and `navItemCur` inferred from classification.

```java
assertEquals(321, result.getData().getInt("autoId"));
assertEquals(1, result.getData().getInt("navItemCur"));
```

- [ ] **Step 2: Run the focused legacy test and verify failure**

Run: `mvn -Dtest=WardrobeV4ServiceTest test`

Expected: FAIL because `itemUpload` currently returns only a boolean result.

- [ ] **Step 3: Return the generated key without changing legacy success codes**

Use `insertForGeneratedKey`, fail when it returns null, and return an `ApiResponse` whose data includes `autoId` and `navItemCur`. Keep both clothing and ornaments supported.

- [ ] **Step 4: Run the focused test and package check**

Run: `mvn -Dtest=WardrobeV4ServiceTest test && mvn -DskipTests package`

Expected: PASS and `BUILD SUCCESS`.

- [ ] **Step 5: Commit only legacy API files**

```bash
git add src/main/java/api/service/WardrobeV4Service.java src/test/java/api/service/WardrobeV4ServiceTest.java
git commit -m "feat: return wardrobe id after upload"
```

### Task 2: Canonical Evaluation Normalization and Diff

**Files:**
- Create: `qj_backend/src/main/java/com/qj/services/WardrobeEvaluationNormalizer.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobeEvaluationNormalizerTest.java`
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobeStyleValidator.java`

**Interfaces:**
- Produces: `JSONObject normalize(JSONObject source, Long autoId, Integer navItemCur)`.
- Produces: `WardrobeEvaluationNormalizer.Comparison compare(JSONObject ai, JSONObject advisor)` with `sampleType`, `diffJson`, and normalized snapshots.
- Consumes: legacy fields `types`, `clothingType`, `cloComplexion`, `parkValue`, `mainColorValue`, `otherColorValue`, `isException`, `seasonMatches`.

- [ ] **Step 1: Write failing normalization and comparison tests**

Cover exact match, one style changed, omitted style treated as zero, reordered color/season arrays, non-AI length/material changes ignored, and invalid percentages rejected.

```java
Comparison result = normalizer.compare(ai, advisor);
assertEquals("negative", result.getSampleType());
assertEquals("style_too_high", result.getDiffJson()
        .getJSONObject("types.nature").getStr("kind"));
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -Dtest=WardrobeEvaluationNormalizerTest test`

Expected: FAIL because the normalizer does not exist.

- [ ] **Step 3: Implement canonical normalization**

Normalize the eight styles to an internal fixed map, emit non-zero legacy `types`, trim strings, validate against the old form option lists, sort set-like arrays, and retain `isException` as a Boolean.

- [ ] **Step 4: Implement structured diff classification**

Emit `positive` with `{}` when all AI fields match. Emit `negative` with one entry per changed field containing `aiValue`, `advisorValue`, and `kind`; classify style changes as `style_too_high`, `style_too_low`, `style_missing`, or `style_false_positive`.

- [ ] **Step 5: Run normalizer and existing style tests**

Run: `mvn -Dtest=WardrobeEvaluationNormalizerTest,WardrobeStyleValidatorTest,GeminiStyleRecognitionServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit the canonical contract**

```bash
git add qj_backend/src/main/java/com/qj/services/WardrobeEvaluationNormalizer.java qj_backend/src/main/java/com/qj/services/WardrobeStyleValidator.java qj_backend/src/test/java/com/qj/services/WardrobeEvaluationNormalizerTest.java
git commit -m "feat: normalize wardrobe evaluation snapshots"
```

### Task 3: Migrate Single-Pass AI Storage

**Files:**
- Modify: `qj_backend/src/main/resources/migration_wardrobe_ai_review.sql`
- Create: `qj_backend/src/main/resources/migration_wardrobe_ai_single_pass.sql`
- Modify: `qj_backend/src/test/java/com/qj/services/WardrobeSchemaContractTest.java`

**Interfaces:**
- Produces: unique `(legacy_auto_id, nav_item_cur)` identity and columns defined in the approved design.
- Consumes: existing `qj_wardrobe_item` and `qj_wardrobe_style_history` without deleting rows.

- [ ] **Step 1: Add a failing migration contract test**

Read the SQL resource and assert the presence of `legacy_auto_id`, `nav_item_cur`, `ai_evaluation_json`, `advisor_evaluation_json`, `sample_type`, `diff_json`, `reviewer_openid`, `review_time`, and a unique key over legacy identity.

- [ ] **Step 2: Run the contract test and verify failure**

Run: `mvn -Dtest=WardrobeSchemaContractTest test`

Expected: FAIL because the single-pass migration is absent.

- [ ] **Step 3: Add an idempotent additive migration**

Use `ALTER TABLE ... ADD COLUMN` guarded for the target MySQL version, preserve old round rows, and add indexes for status, sample type, prompt version, and legacy identity. Do not drop old approval/history columns in this release.

- [ ] **Step 4: Apply migration to the test database and verify schema**

Run the SQL against `qj_minip_v2_t`, then query `information_schema.columns` and `statistics` for every required column/index.

Expected: all columns exist and the legacy identity index is unique.

- [ ] **Step 5: Run the schema test and commit**

Run: `mvn -Dtest=WardrobeSchemaContractTest test`

```bash
git add qj_backend/src/main/resources/migration_wardrobe_ai_review.sql qj_backend/src/main/resources/migration_wardrobe_ai_single_pass.sql qj_backend/src/test/java/com/qj/services/WardrobeSchemaContractTest.java
git commit -m "feat: add wardrobe single-pass schema"
```

### Task 4: Make Gemini Produce the Aligned Evaluation Once

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/services/GeminiStyleRecognitionService.java`
- Modify: `qj_backend/src/test/java/com/qj/services/GeminiStyleRecognitionServiceTest.java`

**Interfaces:**
- Produces: `GeminiRecognitionResult.getEvaluationJson()` in canonical legacy shape.
- Consumes: `WardrobeEvaluationNormalizer.normalize(...)` and a Prompt text supplied by a prompt provider in plan 2; until then use the seeded default Prompt.

- [ ] **Step 1: Replace rerun-oriented tests with aligned-output tests**

Assert the Prompt requests `types`, `clothingType`, `cloComplexion`, `parkValue`, `mainColorValue`, `otherColorValue`, `isException`, and `seasonMatches`, and contains no previous-result/advisor-feedback rerun instructions.

- [ ] **Step 2: Run the Gemini tests and verify failure**

Run: `mvn -Dtest=GeminiStyleRecognitionServiceTest test`

Expected: FAIL because the current Prompt emits `{styles, attributes}` and supports reruns.

- [ ] **Step 3: Change the response schema and Prompt**

Request exact old-form field names and legal Chinese option values. Keep independent 0-100 style relevance, one-to-two non-zero style guidance, `temperature=0.1`, structured JSON response, transport retries, and fallback models. Remove previous AI/advisor/feedback arguments from the business recognition signature.

- [ ] **Step 4: Validate the AI response through the canonical normalizer**

Reject malformed fields before persistence. Add `isException` to the response schema. Do not ask Gemini for `autoId`, `navItemCur`, dimensions, material, or classification.

- [ ] **Step 5: Run focused and full backend tests**

Run: `mvn -Dtest=GeminiStyleRecognitionServiceTest,WardrobeEvaluationNormalizerTest test`

Expected: PASS.

- [ ] **Step 6: Commit the single-pass recognizer**

```bash
git add qj_backend/src/main/java/com/qj/services/GeminiStyleRecognitionService.java qj_backend/src/test/java/com/qj/services/GeminiStyleRecognitionServiceTest.java
git commit -m "feat: align wardrobe AI evaluation output"
```

### Task 5: Add Idempotent Create, Query, and Complete APIs

**Files:**
- Modify: `qj_backend/src/main/java/com/qj/services/WardrobeService.java`
- Modify: `qj_backend/src/main/java/com/qj/controllers/WardrobeController.java`
- Create: `qj_backend/src/test/java/com/qj/services/WardrobeServiceSinglePassTest.java`
- Create: `qj_backend/src/test/java/com/qj/controllers/WardrobeControllerTest.java`

**Interfaces:**
- Produces: `POST /qj/wardrobe/items` with `autoId`, `openid`, `navItemCur`, `imageUrl`.
- Produces: `GET /qj/wardrobe/items/by-legacy?autoId=&navItemCur=`.
- Produces: `POST /qj/wardrobe/items/{itemId}/complete` with `reviewerOpenid` and `advisorEvaluationJson`.
- Removes from new UI contract: `/reviews/{historyId}/return` and `/items/{itemId}/retry-ai`.

- [ ] **Step 1: Write failing service tests with a database gateway seam**

Cover duplicate create returning the existing item, one history insert after transport retries, successful completion as positive, completion as negative with diff, technical failure classification, and duplicate completion returning the original completed record.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -Dtest=WardrobeServiceSinglePassTest,WardrobeControllerTest test`

Expected: FAIL because the new signatures and endpoints do not exist.

- [ ] **Step 3: Implement idempotent single-pass creation**

Look up by legacy identity before insert. Store `pending_ai`, run recognition in background once, then persist either immutable `ai_evaluation_json` plus `pending_review` or `ai_failed` plus `error_msg`. Store the actual prompt text/version/model in the audit row.

- [ ] **Step 4: Implement completion and exact legacy lookup**

Normalize advisor JSON, compare with immutable AI JSON, save `advisor_evaluation_json`, `sample_type`, `diff_json`, reviewer/time, and `completed`. When AI failed, save the manual advisor result but leave sample type null.

- [ ] **Step 5: Preserve old history reads but disable business rerun mutation**

Continue returning old rows for existing data. Return a clear 409-style business failure from legacy return/retry endpoints or remove their mappings after all frontends stop calling them.

- [ ] **Step 6: Run all backend tests**

Run: `mvn test`

Expected: PASS.

- [ ] **Step 7: Commit backend APIs**

```bash
git add qj_backend/src/main/java/com/qj/services/WardrobeService.java qj_backend/src/main/java/com/qj/controllers/WardrobeController.java qj_backend/src/test/java/com/qj/services/WardrobeServiceSinglePassTest.java qj_backend/src/test/java/com/qj/controllers/WardrobeControllerTest.java
git commit -m "feat: complete wardrobe AI review once"
```

### Task 6: Update Upload and Advisor Mini-Program Flow

**Files:**
- Modify: `D:/project/minip-client/miniprogram/pages/client/wardrobe/form/form.js:425`
- Modify: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/clothing/form/form.js`
- Modify: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/clothing/form/form.wxml`
- Modify: `D:/project/minip-client/miniprogram/pages/pkg-admin/consult/page/admin/evaluation/clothing/form/form.wxss`
- Modify: `D:/project/minip-client/miniprogram/utils/wardrobeAiReview.js`
- Modify: `D:/project/minip-client/miniprogram/utils/wardrobeAiReview.test.js`

**Interfaces:**
- Consumes: legacy upload response `data.autoId` and `data.navItemCur`.
- Consumes: exact legacy lookup and complete APIs from Task 5.
- Produces: one visible advisor action, “确认评测”.

- [ ] **Step 1: Rewrite the Node contract test to describe the new UI**

Assert the page contains `AI初判结果`, `正例`, `反例`, `确认评测`, and history sections for AI result, advisor result, and differences. Assert it does not contain `打回重识别`, `立即识别`, `approveAiReview`, `returnAiReview`, or `retry-ai`.

- [ ] **Step 2: Run the Node test and verify failure**

Run: `node miniprogram/utils/wardrobeAiReview.test.js`

Expected: FAIL on old return/retry UI text.

- [ ] **Step 3: Pass stable legacy identity during upload**

Read `autoId` and `navItemCur` from the legacy upload response and include them in the new AI create request. Keep upload success independent from background AI completion and keep test SMS disabled.

- [ ] **Step 4: Replace image matching with exact legacy lookup**

Load one item using `autoId` and `navItemCur`; remove the recent-100-items scan and exact image URL matcher. Convert the canonical snapshot directly into page form state without the old `{styles, attributes}` bridge.

- [ ] **Step 5: Merge AI approval and legacy form submit**

Keep the AI card read-only. On “确认评测”, validate the form, submit `/v4/wardrobe/submitWardrobeItemEvaluation`, then call the new `/complete` endpoint with the identical final payload. Prevent double taps. When history sync fails after legacy success, show “评测已保存，AI历史待同步” and persist a retry marker in local storage.

- [ ] **Step 6: Redesign history for one AI snapshot and one advisor result**

Display prompt version, AI result, advisor result, `positive`/`negative`/failure label, field differences, and timestamps. Existing multi-round records remain readable under an “旧流程记录” label.

- [ ] **Step 7: Run Node tests and WeChat compile checks**

Run: `node miniprogram/utils/wardrobeAiReview.test.js`

Expected: prints the new single-pass mapping success message.

Use WeChat DevTools “构建” and confirm zero JavaScript/WXML compile errors.

- [ ] **Step 8: Commit mini-program changes in its repository**

```bash
git add miniprogram/pages/client/wardrobe/form/form.js miniprogram/pages/pkg-admin/consult/page/admin/evaluation/clothing/form miniprogram/utils/wardrobeAiReview.js miniprogram/utils/wardrobeAiReview.test.js
git commit -m "feat: review wardrobe AI result once"
```

### Task 7: End-to-End Core Verification

**Files:**
- Modify: `qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md`

**Interfaces:**
- Consumes: all core tasks.
- Produces: reproducible test-environment verification steps.

- [ ] **Step 1: Start legacy 8090 and new 7077 backends against test databases**

Verify `/qj/wardrobe/_health` reports tables ready, Gemini configured, and an active default prompt version.

- [ ] **Step 2: Upload one clothing and one ornament**

Confirm each legacy upload returns `autoId`, each creates exactly one AI item, and each AI item reaches `pending_review` or a single `ai_failed` result.

- [ ] **Step 3: Verify positive and negative advisor paths**

Submit one unchanged result and assert `sample_type=positive`, then modify another AI field and assert `sample_type=negative` with a non-empty diff. Confirm both old wardrobe records move to 已评测.

- [ ] **Step 4: Verify forbidden behavior is gone**

Confirm the mini-program has no business retry/return control and repeated create/complete requests do not add business rounds.

- [ ] **Step 5: Run regression suites**

Run backend `mvn test`, legacy focused tests, and mini-program Node tests. Expected: all PASS.

- [ ] **Step 6: Document test environment setup and commit**

```bash
git add qj_backend/DEPLOY_WARDROBE_AI_REVIEW.md
git commit -m "docs: update wardrobe AI verification flow"
```

