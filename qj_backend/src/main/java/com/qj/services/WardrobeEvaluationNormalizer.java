package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes AI and advisor data to the legacy wardrobe evaluation contract. */
@Service
public class WardrobeEvaluationNormalizer {

    public static final List<String> STYLE_NAMES = Arrays.asList(
            "自然", "优雅", "浪漫", "古典", "戏剧", "前卫", "少年", "少女");
    private static final List<String> COLORS = Arrays.asList(
            "红", "橙", "黄", "绿", "蓝", "紫", "黑", "白", "灰", "金", "银");
    private static final List<String> SEASONS = Arrays.asList("春", "夏", "秋", "冬");
    private static final List<String> COLOR_DEGREES = Arrays.asList("偏白", "略白", "偏黑", "略黑");
    private static final List<String> LUSTER_DEGREES = Arrays.asList("偏光泽", "略光泽", "偏黯淡", "略黯淡");
    private static final List<String> TONES = Arrays.asList("偏净", "略净", "偏浊", "略浊");
    private static final List<String> VIVIDNESS = Arrays.asList("偏艳", "略艳", "偏素", "略素");
    private static final List<String> PARK_VALUES = Arrays.asList("偏花", "略花", "偏朴", "略朴");

    public JSONObject normalize(JSONObject source, Long autoId, Integer navItemCur) {
        if (source == null) {
            throw new IllegalArgumentException("Wardrobe evaluation is required");
        }
        JSONObject complexion = source.getJSONObject("cloComplexion");
        JSONObject normalized = new JSONObject();
        if (autoId != null) {
            normalized.set("autoId", autoId);
        }
        if (navItemCur != null) {
            if (navItemCur != 1 && navItemCur != 2) {
                throw new IllegalArgumentException("navItemCur must be 1 or 2");
            }
            normalized.set("navItemCur", navItemCur);
        }
        normalized.set("types", normalizeTypes(source.getJSONArray("types")));
        normalized.set("clothingType", clean(source.getStr("clothingType")));
        normalized.set("cloComplexion", new JSONObject()
                .set("colorDegree", requireOption(complexion, "colorDegree", COLOR_DEGREES))
                .set("lusterDegree", requireOption(complexion, "lusterDegree", LUSTER_DEGREES))
                .set("tone", requireOption(complexion, "tone", TONES))
                .set("vividness", requireOption(complexion, "vividness", VIVIDNESS)));
        normalized.set("parkValue", requireOption(source.getStr("parkValue"), "parkValue", PARK_VALUES));
        normalized.set("mainColorValue", requireOption(source.getStr("mainColorValue"), "mainColorValue", COLORS));
        normalized.set("otherColorValue", normalizeSet(source.getJSONArray("otherColorValue"), "otherColorValue", COLORS));
        normalized.set("isException", requireBoolean(source, "isException"));
        normalized.set("seasonMatches", normalizeSet(source.getJSONArray("seasonMatches"), "seasonMatches", SEASONS));
        return normalized;
    }

    public Comparison compare(JSONObject aiSource, JSONObject advisorSource, Long autoId, Integer navItemCur) {
        JSONObject ai = normalize(aiSource, autoId, navItemCur);
        JSONObject advisor = normalize(advisorSource, autoId, navItemCur);
        JSONObject diff = new JSONObject();

        Map<String, Integer> aiStyles = styleMap(ai.getJSONArray("types"));
        Map<String, Integer> advisorStyles = styleMap(advisor.getJSONArray("types"));
        for (String style : STYLE_NAMES) {
            int aiValue = aiStyles.get(style);
            int advisorValue = advisorStyles.get(style);
            if (aiValue != advisorValue) {
                String kind;
                if (aiValue == 0) {
                    kind = "style_missing";
                } else if (advisorValue == 0) {
                    kind = "style_false_positive";
                } else {
                    kind = aiValue > advisorValue ? "style_too_high" : "style_too_low";
                }
                diff.set("types." + style, difference(aiValue, advisorValue, kind));
            }
        }

        compareField(diff, "clothingType", ai.getStr("clothingType"), advisor.getStr("clothingType"), "field_changed");
        compareField(diff, "cloComplexion.colorDegree",
                ai.getJSONObject("cloComplexion").getStr("colorDegree"),
                advisor.getJSONObject("cloComplexion").getStr("colorDegree"), "field_changed");
        compareField(diff, "cloComplexion.lusterDegree",
                ai.getJSONObject("cloComplexion").getStr("lusterDegree"),
                advisor.getJSONObject("cloComplexion").getStr("lusterDegree"), "field_changed");
        compareField(diff, "cloComplexion.tone",
                ai.getJSONObject("cloComplexion").getStr("tone"),
                advisor.getJSONObject("cloComplexion").getStr("tone"), "field_changed");
        compareField(diff, "cloComplexion.vividness",
                ai.getJSONObject("cloComplexion").getStr("vividness"),
                advisor.getJSONObject("cloComplexion").getStr("vividness"), "field_changed");
        compareField(diff, "parkValue", ai.getStr("parkValue"), advisor.getStr("parkValue"), "field_changed");
        compareField(diff, "mainColorValue", ai.getStr("mainColorValue"), advisor.getStr("mainColorValue"), "field_changed");
        compareField(diff, "otherColorValue", ai.getJSONArray("otherColorValue"),
                advisor.getJSONArray("otherColorValue"), "set_changed");
        compareField(diff, "isException", ai.getBool("isException"), advisor.getBool("isException"), "field_changed");
        compareField(diff, "seasonMatches", ai.getJSONArray("seasonMatches"),
                advisor.getJSONArray("seasonMatches"), "set_changed");

        return new Comparison(diff.isEmpty() ? "positive" : "negative", ai, advisor, diff);
    }

    public Comparison compare(JSONObject aiSource, JSONObject advisorSource) {
        return compare(aiSource, advisorSource, null, null);
    }

    /** Validates a persisted evaluation without rewriting its snapshot. */
    public void validateAlignedSnapshot(JSONObject source) {
        if (source == null) throw new IllegalArgumentException("Wardrobe evaluation is required");
        for (String field : Arrays.asList("types", "clothingType", "cloComplexion", "parkValue",
                "mainColorValue", "otherColorValue", "isException", "seasonMatches")) {
            if (!source.containsKey(field)) throw new IllegalArgumentException("Missing required field: " + field);
        }
        normalize(source, null, null);
        JSONArray types = source.getJSONArray("types");
        if (types == null) throw new IllegalArgumentException("types is required");
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Object value : types) {
            if (!(value instanceof JSONObject)) throw new IllegalArgumentException("types entries must be objects");
            JSONObject item = (JSONObject) value;
            String style = clean(item.getStr("type"));
            if (!STYLE_NAMES.contains(style) || values.containsKey(style)) {
                throw new IllegalArgumentException("types must use unique supported styles");
            }
            values.put(style, parsePercentage(item.get("typeProportion"), style));
        }
        int nonZero = 0;
        for (Integer score : values.values()) if (score > 0) nonZero++;
        if (nonZero > 2) throw new IllegalArgumentException("At most two styles may be non-zero");
    }

    private JSONArray normalizeTypes(JSONArray types) {
        if (types == null) {
            throw new IllegalArgumentException("types is required");
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String style : STYLE_NAMES) {
            values.put(style, 0);
        }
        for (Object value : types) {
            JSONObject item = value instanceof JSONObject ? (JSONObject) value : new JSONObject(value);
            String style = clean(item.getStr("type"));
            if (!values.containsKey(style)) {
                throw new IllegalArgumentException("Unsupported style: " + style);
            }
            int score = parsePercentage(item.get("typeProportion"), style);
            int total = values.get(style) + score;
            if (total > 100) {
                throw new IllegalArgumentException(style + " style score must be between 0 and 100");
            }
            values.put(style, total);
        }
        JSONArray normalized = new JSONArray();
        for (String style : STYLE_NAMES) {
            int score = values.get(style);
            if (score > 0) {
                normalized.add(new JSONObject()
                        .set("type", style)
                        .set("typeProportion", score + "%"));
            }
        }
        return normalized;
    }

    private Map<String, Integer> styleMap(JSONArray types) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String style : STYLE_NAMES) {
            result.put(style, 0);
        }
        for (Object value : types) {
            JSONObject item = (JSONObject) value;
            result.put(item.getStr("type"), parsePercentage(item.get("typeProportion"), item.getStr("type")));
        }
        return result;
    }

    private int parsePercentage(Object raw, String style) {
        String text = clean(raw == null ? null : String.valueOf(raw));
        if (!text.matches("\\d+%")) {
            throw new IllegalArgumentException(style + " style score must be an integer percentage");
        }
        try {
            int value = Integer.parseInt(text.substring(0, text.length() - 1));
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException(style + " style score must be between 0 and 100");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(style + " style score must be an integer");
        }
    }

    private JSONArray normalizeSet(JSONArray values, String field, List<String> options) {
        if (values == null) {
            return new JSONArray();
        }
        List<String> selected = new ArrayList<>();
        for (Object raw : values) {
            String value = requireOption(raw == null ? null : String.valueOf(raw), field, options);
            if (!selected.contains(value)) {
                selected.add(value);
            }
        }
        selected.sort((left, right) -> Integer.compare(options.indexOf(left), options.indexOf(right)));
        return new JSONArray(selected);
    }

    private String requireOption(JSONObject source, String field, List<String> options) {
        return requireOption(source == null ? null : source.getStr(field), field, options);
    }

    private String requireOption(String raw, String field, List<String> options) {
        String value = clean(raw);
        if (!options.contains(value)) {
            throw new IllegalArgumentException("Unsupported " + field + ": " + value);
        }
        return value;
    }

    private boolean requireBoolean(JSONObject source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return (Boolean) value;
    }

    private String clean(String value) {
        return StrUtil.trimToEmpty(value);
    }

    private void compareField(JSONObject diff, String field, Object aiValue, Object advisorValue, String kind) {
        if (!String.valueOf(aiValue).equals(String.valueOf(advisorValue))) {
            diff.set(field, difference(aiValue, advisorValue, kind));
        }
    }

    private JSONObject difference(Object aiValue, Object advisorValue, String kind) {
        return new JSONObject()
                .set("aiValue", aiValue)
                .set("advisorValue", advisorValue)
                .set("kind", kind);
    }

    public static final class Comparison {
        private final String sampleType;
        private final JSONObject aiEvaluation;
        private final JSONObject advisorEvaluation;
        private final JSONObject diffJson;

        private Comparison(String sampleType, JSONObject aiEvaluation,
                           JSONObject advisorEvaluation, JSONObject diffJson) {
            this.sampleType = sampleType;
            this.aiEvaluation = copy(aiEvaluation);
            this.advisorEvaluation = copy(advisorEvaluation);
            this.diffJson = copy(diffJson);
        }

        public String getSampleType() {
            return sampleType;
        }

        public JSONObject getAiEvaluation() {
            return copy(aiEvaluation);
        }

        public JSONObject getAdvisorEvaluation() {
            return copy(advisorEvaluation);
        }

        public JSONObject getDiffJson() {
            return copy(diffJson);
        }

        private static JSONObject copy(JSONObject value) {
            return JSONUtil.parseObj(value.toString());
        }
    }
}
