package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Gemini for wardrobe image style recognition.
 */
@Service
public class GeminiStyleRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiStyleRecognitionService.class);
    private static final String PROMPT_VERSION = "wardrobe-legacy-evaluation-v3";
    private static final String[] STYLE_NAMES = {
            "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女"
    };
    private static final String[] COLORS = {
            "红", "橙", "黄", "绿", "蓝", "紫", "黑", "白", "灰", "金", "银"
    };
    private final WardrobeEvaluationNormalizer evaluationNormalizer = new WardrobeEvaluationNormalizer();

    @Value("${gemini.api-key:}")
    private String configuredApiKey;

    @Value("${gemini.model:gemini-3.5-flash}")
    private String model;

    @Value("${gemini.timeout-ms:60000}")
    private int timeoutMs;

    @Value("${gemini.fallback-models:gemini-2.5-flash,gemini-2.0-flash}")
    private String fallbackModels;

    @Value("${gemini.max-attempts-per-model:2}")
    private int maxAttemptsPerModel;

    @Value("${gemini.retry-backoff-ms:1200}")
    private long retryBackoffMs;

    public String getPromptVersion() {
        return PROMPT_VERSION;
    }

    public boolean isConfigured() {
        return StrUtil.isNotBlank(resolveApiKey());
    }

    public String getModel() {
        return model;
    }

    public String getPromptText() {
        return buildPrompt();
    }

    public GeminiRecognitionResult recognize(String imageUrl) {
        return recognize(imageUrl, buildPrompt());
    }

    public GeminiRecognitionResult recognize(String imageUrl, String prompt) {
        String apiKey = resolveApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new GeminiRecognitionException(
                    "Gemini API key is not configured. Set GEMINI_API_KEY", null, prompt, "", null);
        }
        if (StrUtil.isBlank(imageUrl)) {
            throw new GeminiRecognitionException("Clothing image is required", null, prompt, "", null);
        }
        if (StrUtil.isBlank(prompt)) {
            throw new IllegalArgumentException("Recognition Prompt is required");
        }
        byte[] imageBytes;
        try {
            imageBytes = HttpUtil.downloadBytes(imageUrl);
        } catch (RuntimeException e) {
            throw new GeminiRecognitionException(e.getMessage(), e, prompt, "", null);
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new GeminiRecognitionException("Failed to download clothing image", null, prompt, "", null);
        }

        JSONObject request = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.add(new JSONObject().set("text", prompt));
        parts.add(new JSONObject().set("inline_data", new JSONObject()
                .set("mime_type", detectMime(imageUrl))
                .set("data", Base64.getEncoder().encodeToString(imageBytes))));
        request.set("contents", new JSONArray().put(new JSONObject().set("parts", parts)));
        request.set("generationConfig", new JSONObject()
                .set("temperature", 0.1)
                .set("response_mime_type", "application/json")
                .set("responseMimeType", "application/json")
                .set("responseSchema", buildResponseSchema()));

        RuntimeException lastError = null;
        String lastRawResponse = null;
        List<String> attemptedModels = new ArrayList<>();
        List<String> candidateModels = buildModelCandidates();
        for (int modelIndex = 0; modelIndex < candidateModels.size(); modelIndex++) {
            String candidateModel = candidateModels.get(modelIndex);
            attemptedModels.add(candidateModel);
            for (int attempt = 1; attempt <= Math.max(1, maxAttemptsPerModel); attempt++) {
                try {
                    String raw = callGemini(apiKey, candidateModel, request);
                    lastRawResponse = raw;
                    JSONObject evaluation = extractEvaluationJson(raw);
                    return new GeminiRecognitionResult(evaluation, raw, prompt, candidateModel);
                } catch (RuntimeException e) {
                    lastError = e;
                    if (e instanceof GeminiHttpException) {
                        lastRawResponse = ((GeminiHttpException) e).getRawResponse();
                    }
                    Integer status = extractGeminiStatus(e.getMessage());
                    if (status != null && isUnavailableGeminiModelStatus(status)
                            && modelIndex < candidateModels.size() - 1) {
                        log.warn("Gemini model {} is unavailable, trying next model: {}",
                                candidateModel, e.getMessage());
                        break;
                    }
                    boolean retryable = isRetryableGeminiError(e);
                    if (!retryable) {
                        log.warn("Gemini wardrobe style recognition failed on model {}: {}", candidateModel, e.getMessage());
                        throw recognitionFailure(prompt, attemptedModels, lastRawResponse, e);
                    }
                    log.warn("Gemini temporary failure on model {} attempt {}/{}: {}",
                            candidateModel, attempt, Math.max(1, maxAttemptsPerModel), e.getMessage());
                    if (attempt < Math.max(1, maxAttemptsPerModel)) {
                        sleepBeforeRetry(attempt);
                    }
                }
            }
        }
        RuntimeException cause = lastError == null ? new IllegalStateException("Gemini request failed") : lastError;
        throw recognitionFailure(prompt, attemptedModels, lastRawResponse, cause);
    }

    private GeminiRecognitionException recognitionFailure(String prompt, List<String> attemptedModels,
                                                          String rawResponse, RuntimeException cause) {
        return new GeminiRecognitionException(cause.getMessage(), cause, prompt,
                String.join(",", attemptedModels), rawResponse);
    }

    private String callGemini(String apiKey, String candidateModel, JSONObject request) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + candidateModel + ":generateContent?key=" + apiKey;
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Content-Type", "application/json")
                .body(request.toString())
                .timeout(timeoutMs)
                .execute()) {
            String raw = response.body();
            if (!response.isOk()) {
                throw new GeminiHttpException(response.getStatus(), raw);
            }
            return raw;
        }
    }

    private List<String> buildModelCandidates() {
        Set<String> unique = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(model)) {
            unique.add(model.trim());
        }
        if (StrUtil.isNotBlank(fallbackModels)) {
            for (String fallback : fallbackModels.split(",")) {
                if (StrUtil.isNotBlank(fallback)) {
                    unique.add(fallback.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private boolean isRetryableGeminiStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private boolean isUnavailableGeminiModelStatus(int status) {
        return status == 400 || status == 404;
    }

    private boolean isRetryableGeminiError(RuntimeException error) {
        String message = error == null ? "" : error.getMessage();
        Integer status = extractGeminiStatus(message);
        if (status != null && isRetryableGeminiStatus(status)) {
            return true;
        }
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("timed out") || lower.contains("timeout");
    }

    private Integer extractGeminiStatus(String message) {
        if (StrUtil.isBlank(message)) {
            return null;
        }
        Matcher matcher = Pattern.compile("Gemini request failed: (\\d{3})").matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.max(0, retryBackoffMs) * Math.max(1, attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Gemini request", e);
        }
    }

    private String resolveApiKey() {
        if (StrUtil.isNotBlank(configuredApiKey)) {
            return configuredApiKey;
        }
        return System.getenv("GEMINI_API_KEY");
    }

    private String buildPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a professional fashion style recognition assistant. ");
        prompt.append("Analyze only the clothing item in the image.\n");
        prompt.append("Return only one JSON object. Do not use Markdown. Do not include explanations. ");
        prompt.append("Use exactly these top-level fields: types, clothingType, cloComplexion, parkValue, ");
        prompt.append("mainColorValue, otherColorValue, isException, seasonMatches.\n");
        prompt.append("types is an array of non-zero style entries. Each entry has type and typeProportion. ");
        prompt.append("type must be one of 自然,优雅,浪漫,古典,戏剧,前卫,少年,少女. ");
        prompt.append("typeProportion must be an integer percentage string from 0 to 100, for example 70%. ");
        prompt.append("Each style relevance score is independent; do not make the scores sum to 100. ");
        prompt.append("Important scoring rule: return exactly 1 or 2 non-zero styles and never more than 2. ");
        prompt.append("Set every weak, generic, or uncertain style to 0. Do not give small filler scores.\n");
        prompt.append("clothingType is concise free text, or an empty string when uncertain.\n");
        prompt.append("cloComplexion is an object with exactly colorDegree, lusterDegree, tone, vividness. ");
        prompt.append("Use only these Chinese values: colorDegree=偏白,略白,偏黑,略黑; ");
        prompt.append("lusterDegree=偏光泽,略光泽,偏黯淡,略黯淡; tone=偏净,略净,偏浊,略浊; ");
        prompt.append("vividness=偏艳,略艳,偏素,略素.\n");
        prompt.append("parkValue must be one of 偏花,略花,偏朴,略朴. ");
        prompt.append("mainColorValue and each otherColorValue must be one of 红,橙,黄,绿,蓝,紫,黑,白,灰,金,银. ");
        prompt.append("isException must be a boolean. ");
        prompt.append("seasonMatches contains zero or more of 春,夏,秋,冬.\n");
        prompt.append("Example output: {\"types\":[{\"type\":\"自然\",\"typeProportion\":\"70%\"}],\"clothingType\":\"X型\",\"cloComplexion\":{\"colorDegree\":\"略白\",\"lusterDegree\":\"略光泽\",\"tone\":\"略净\",\"vividness\":\"略素\"},\"parkValue\":\"略朴\",\"mainColorValue\":\"白\",\"otherColorValue\":[\"蓝\"],\"isException\":false,\"seasonMatches\":[\"春\",\"夏\"]}");
        return prompt.toString();
    }

    private JSONObject extractEvaluationJson(String raw) {
        JSONObject response = JSONUtil.parseObj(raw);
        JSONArray candidates = response.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini response is missing candidates");
        }
        JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
        JSONArray parts = content == null ? null : content.getJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Gemini response is missing text parts");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String partText = parts.getJSONObject(i).getStr("text");
            if (StrUtil.isNotBlank(partText)) {
                text.append(partText);
            }
        }
        JSONObject source = JSONUtil.parseObj(stripJsonFence(text.toString().trim()));
        return keepStrongestStyles(evaluationNormalizer.normalize(source, null, null));
    }

    private JSONObject keepStrongestStyles(JSONObject evaluation) {
        JSONArray values = evaluation.getJSONArray("types");
        if (values == null || values.size() <= 2) return evaluation;
        List<JSONObject> ranked = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) ranked.add(values.getJSONObject(i));
        ranked.sort((left, right) -> Integer.compare(
                percentage(right.getStr("typeProportion")), percentage(left.getStr("typeProportion"))));
        JSONArray strongest = new JSONArray();
        for (int i = 0; i < Math.min(2, ranked.size()); i++) strongest.add(ranked.get(i));
        evaluation.set("types", strongest);
        return evaluation;
    }

    private int percentage(String value) {
        return value != null && value.matches("\\d+%")
                ? Integer.parseInt(value.substring(0, value.length() - 1)) : 0;
    }

    private JSONObject buildResponseSchema() {
        JSONObject typeEntrySchema = new JSONObject()
                .set("type", "OBJECT")
                .set("properties", new JSONObject()
                        .set("type", enumString(STYLE_NAMES))
                        .set("typeProportion", new JSONObject().set("type", "STRING")))
                .set("required", new JSONArray().put("type").put("typeProportion"))
                .set("propertyOrdering", new JSONArray().put("type").put("typeProportion"));
        JSONObject complexionSchema = new JSONObject()
                .set("type", "OBJECT")
                .set("properties", new JSONObject()
                        .set("colorDegree", enumString("偏白", "略白", "偏黑", "略黑"))
                        .set("lusterDegree", enumString("偏光泽", "略光泽", "偏黯淡", "略黯淡"))
                        .set("tone", enumString("偏净", "略净", "偏浊", "略浊"))
                        .set("vividness", enumString("偏艳", "略艳", "偏素", "略素")))
                .set("required", new JSONArray()
                        .put("colorDegree")
                        .put("lusterDegree")
                        .put("tone")
                        .put("vividness"))
                .set("propertyOrdering", new JSONArray()
                        .put("colorDegree")
                        .put("lusterDegree")
                        .put("tone")
                        .put("vividness"));
        return new JSONObject()
                .set("type", "OBJECT")
                .set("properties", new JSONObject()
                        .set("types", arrayOf(typeEntrySchema).set("minItems", 1).set("maxItems", 2))
                        .set("clothingType", new JSONObject().set("type", "STRING"))
                        .set("cloComplexion", complexionSchema)
                        .set("parkValue", enumString("偏花", "略花", "偏朴", "略朴"))
                        .set("mainColorValue", colorEnum())
                        .set("otherColorValue", arrayOf(colorEnum()))
                        .set("isException", new JSONObject().set("type", "BOOLEAN"))
                        .set("seasonMatches", arrayOf(enumString("春", "夏", "秋", "冬"))))
                .set("required", new JSONArray()
                        .put("types")
                        .put("clothingType")
                        .put("cloComplexion")
                        .put("parkValue")
                        .put("mainColorValue")
                        .put("otherColorValue")
                        .put("isException")
                        .put("seasonMatches"))
                .set("propertyOrdering", new JSONArray()
                        .put("types")
                        .put("clothingType")
                        .put("cloComplexion")
                        .put("parkValue")
                        .put("mainColorValue")
                        .put("otherColorValue")
                        .put("isException")
                        .put("seasonMatches"));
    }

    private JSONObject enumString(String... values) {
        JSONArray enums = new JSONArray();
        for (String value : values) {
            enums.add(value);
        }
        return new JSONObject().set("type", "STRING").set("enum", enums);
    }

    private JSONObject colorEnum() {
        return enumString(COLORS);
    }

    private JSONObject arrayOf(JSONObject itemSchema) {
        return new JSONObject().set("type", "ARRAY").set("items", itemSchema);
    }

    private String stripJsonFence(String text) {
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json\\s*", "");
            text = text.replaceFirst("^```\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private String detectMime(String imageUrl) {
        String lower = imageUrl == null ? "" : imageUrl.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public static class GeminiRecognitionResult {
        private final JSONObject evaluationJson;
        private final String rawResponse;
        private final String promptText;
        private final String modelName;

        public GeminiRecognitionResult(JSONObject evaluationJson, String rawResponse, String promptText, String modelName) {
            this.evaluationJson = evaluationJson;
            this.rawResponse = rawResponse;
            this.promptText = promptText;
            this.modelName = modelName;
        }

        public JSONObject getEvaluationJson() {
            return evaluationJson;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getPromptText() {
            return promptText;
        }

        public String getModelName() {
            return modelName;
        }
    }

    public static class GeminiRecognitionException extends RuntimeException {
        private final String promptText;
        private final String attemptedModels;
        private final String rawResponse;

        public GeminiRecognitionException(String message, Throwable cause, String promptText,
                                          String attemptedModels, String rawResponse) {
            super(message, cause);
            this.promptText = promptText;
            this.attemptedModels = attemptedModels;
            this.rawResponse = rawResponse;
        }

        public String getPromptText() {
            return promptText;
        }

        public String getAttemptedModels() {
            return attemptedModels;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }

    private static final class GeminiHttpException extends RuntimeException {
        private final String rawResponse;

        private GeminiHttpException(int status, String rawResponse) {
            super("Gemini request failed: " + status + " " + rawResponse);
            this.rawResponse = rawResponse;
        }

        private String getRawResponse() {
            return rawResponse;
        }
    }
}
