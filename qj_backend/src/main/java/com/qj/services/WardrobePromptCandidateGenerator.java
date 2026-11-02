package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Generates a reviewable Prompt candidate from immutable reviewed samples. */
@Service
public class WardrobePromptCandidateGenerator {

    @Value("${gemini.api-key:}")
    private String configuredApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.fallback-models:gemini-2.5-flash,gemini-2.0-flash}")
    private String fallbackModels;

    @Value("${gemini.timeout-ms:60000}")
    private int timeoutMs = 60000;

    public CandidateResult generate(String currentPrompt, JSONObject aggregate, JSONArray samples) {
        if (StrUtil.isBlank(currentPrompt)) {
            throw new IllegalArgumentException("Current Prompt is required");
        }
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Reviewed samples are required");
        }
        String apiKey = resolveApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        JSONObject request = new JSONObject()
                .set("contents", new JSONArray().put(new JSONObject().set("parts", new JSONArray()
                        .put(new JSONObject().set("text", buildInstruction(currentPrompt, aggregate, samples))))))
                .set("generationConfig", new JSONObject()
                        .set("temperature", 0.2)
                        .set("responseMimeType", "application/json")
                        .set("responseSchema", new JSONObject()
                                .set("type", "OBJECT")
                                .set("required", new JSONArray().put("candidatePrompt").put("summary"))
                                .set("properties", new JSONObject()
                                        .set("candidatePrompt", new JSONObject().set("type", "STRING"))
                                        .set("summary", new JSONObject().set("type", "STRING")))));

        RuntimeException lastError = null;
        for (String candidateModel : modelCandidates()) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + candidateModel + ":generateContent?key=" + apiKey;
            try (HttpResponse response = HttpUtil.createPost(url)
                    .header("Content-Type", "application/json")
                    .body(request.toString())
                    .timeout(timeoutMs)
                    .execute()) {
                String raw = response.body();
                if (!response.isOk()) {
                    throw new IllegalStateException("Gemini candidate generation failed on "
                            + candidateModel + ": " + response.getStatus());
                }
                JSONObject result = extractResult(raw);
                String candidatePrompt = StrUtil.trim(result.getStr("candidatePrompt"));
                String summary = StrUtil.trim(result.getStr("summary"));
                if (StrUtil.isBlank(candidatePrompt) || StrUtil.isBlank(summary)) {
                    throw new IllegalStateException("Gemini returned an incomplete Prompt candidate");
                }
                validateContract(candidatePrompt);
                return new CandidateResult(candidatePrompt, summary);
            } catch (RuntimeException error) {
                lastError = error;
            }
        }
        throw lastError == null ? new IllegalStateException("No Gemini model is configured") : lastError;
    }

    private String buildInstruction(String currentPrompt, JSONObject aggregate, JSONArray samples) {
        return "You improve a production image-recognition Prompt from advisor-reviewed examples.\n"
                + "Create a candidate for future wardrobe uploads only. Never suggest rerunning or changing any current item.\n"
                + "Preserve the exact JSON output contract and all allowed values from the current Prompt.\n"
                + "Preserve the eight styles exactly: 自然,优雅,浪漫,古典,戏剧,前卫,少年,少女.\n"
                + "Style scores are independent 0-100 relevance values and do not sum to 100. Output exactly 1-2 non-zero styles, never more than 2.\n"
                + "Use positive samples as behavior to preserve and negative diffs as concrete errors to reduce.\n"
                + "Return JSON only with candidatePrompt and summary. The candidatePrompt must be complete and runnable by itself.\n\n"
                + "CURRENT PROMPT:\n" + currentPrompt + "\n\n"
                + "AGGREGATE:\n" + safeJson(aggregate) + "\n\n"
                + "REVIEWED SAMPLES:\n" + samples;
    }

    private JSONObject extractResult(String raw) {
        JSONObject response = JSONUtil.parseObj(raw);
        JSONArray candidates = response.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no Prompt candidate");
        }
        JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
        JSONArray parts = content == null ? null : content.getJSONArray("parts");
        String text = parts == null || parts.isEmpty() ? null : parts.getJSONObject(0).getStr("text");
        if (StrUtil.isBlank(text)) {
            throw new IllegalStateException("Gemini returned an empty Prompt candidate");
        }
        return JSONUtil.parseObj(stripFence(text));
    }

    private void validateContract(String prompt) {
        String[] required = {"types", "clothingType", "cloComplexion", "parkValue",
                "mainColorValue", "otherColorValue", "isException", "seasonMatches",
                "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女"};
        for (String token : required) {
            if (!prompt.contains(token)) {
                throw new IllegalStateException("Candidate Prompt lost required contract token: " + token);
            }
        }
    }

    private String stripFence(String text) {
        String result = text.trim();
        if (result.startsWith("```")) {
            result = result.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        return result;
    }

    private String safeJson(JSONObject value) {
        return value == null ? "{}" : value.toString();
    }

    private String resolveApiKey() {
        if (StrUtil.isNotBlank(configuredApiKey)) {
            return configuredApiKey;
        }
        return System.getenv("GEMINI_API_KEY");
    }

    private List<String> modelCandidates() {
        Set<String> values = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(model)) values.add(model.trim());
        if (StrUtil.isNotBlank(fallbackModels)) {
            for (String fallback : fallbackModels.split(",")) {
                if (StrUtil.isNotBlank(fallback)) values.add(fallback.trim());
            }
        }
        return new ArrayList<>(values);
    }

    public static final class CandidateResult {
        private final String candidatePrompt;
        private final String summary;

        public CandidateResult(String candidatePrompt, String summary) {
            this.candidatePrompt = candidatePrompt;
            this.summary = summary;
        }

        public String getCandidatePrompt() {
            return candidatePrompt;
        }

        public String getSummary() {
            return summary;
        }
    }
}
