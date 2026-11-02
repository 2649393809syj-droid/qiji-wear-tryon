package com.qj.services;

import cn.hutool.json.JSONObject;

import java.math.BigDecimal;

public final class WardrobePromptPublicationPolicy {

    private static final double GLOBAL_MAXIMUM_FAILURE_RATE = 0.20;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Decision evaluate(JSONObject summary, int minimumSuccessful, double maximumFailureRate) {
        if (minimumSuccessful <= 0) {
            return Decision.reject("insufficient_successful_samples");
        }
        if (!Double.isFinite(maximumFailureRate) || maximumFailureRate < 0
                || maximumFailureRate > GLOBAL_MAXIMUM_FAILURE_RATE) {
            return Decision.reject("failure_rate_exceeded");
        }

        Integer completed = integerValue(summary, "completed");
        if (completed == null || completed < 0 || completed < minimumSuccessful) {
            return Decision.reject("insufficient_successful_samples");
        }

        Integer total = integerValue(summary, "total");
        Integer failed = integerValue(summary, "failed");
        if (total == null || failed == null || total <= 0 || failed < 0
                || completed > total || failed > total
                || (long) completed + (long) failed != (long) total
                || (double) failed / (double) total > maximumFailureRate) {
            return Decision.reject("failure_rate_exceeded");
        }

        JSONObject baseline = objectValue(summary, "baselineMetrics");
        JSONObject candidate = objectValue(summary, "candidateMetrics");
        BigDecimal baselineMae = metricValue(baseline, "styleMae", ZERO, ONE_HUNDRED);
        BigDecimal candidateMae = metricValue(candidate, "styleMae", ZERO, ONE_HUNDRED);
        BigDecimal baselineExact = metricValue(baseline, "exactMatchRate", ZERO, ONE);
        BigDecimal candidateExact = metricValue(candidate, "exactMatchRate", ZERO, ONE);
        if (baselineMae == null || candidateMae == null
                || baselineExact == null || candidateExact == null) {
            return Decision.reject("no_strict_improvement");
        }

        if (candidateMae.compareTo(baselineMae) > 0) {
            return Decision.reject("style_mae_regressed");
        }
        if (candidateExact.compareTo(baselineExact) < 0) {
            return Decision.reject("exact_match_regressed");
        }
        if (candidateMae.compareTo(baselineMae) < 0) {
            return Decision.publish("style_mae_improved");
        }
        if (candidateExact.compareTo(baselineExact) > 0) {
            return Decision.publish("exact_match_improved");
        }
        return Decision.reject("no_strict_improvement");
    }

    private static Integer integerValue(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (!(value instanceof Number)) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static JSONObject objectValue(JSONObject object, String key) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    private static BigDecimal metricValue(JSONObject object, String key,
                                          BigDecimal minimum, BigDecimal maximum) {
        if (object == null) {
            return null;
        }
        Object value = object.get(key);
        if (!(value instanceof Number)) {
            return null;
        }
        if (value instanceof Double && !Double.isFinite((Double) value)
                || value instanceof Float && !Float.isFinite((Float) value)) {
            return null;
        }
        try {
            BigDecimal metric = new BigDecimal(value.toString());
            return metric.compareTo(minimum) >= 0 && metric.compareTo(maximum) <= 0
                    ? metric : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static final class Decision {
        private final boolean eligible;
        private final String reason;

        private Decision(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }

        private static Decision publish(String reason) {
            return new Decision(true, reason);
        }

        private static Decision reject(String reason) {
            return new Decision(false, reason);
        }

        public boolean isEligible() {
            return eligible;
        }

        public String getReason() {
            return reason;
        }
    }
}
