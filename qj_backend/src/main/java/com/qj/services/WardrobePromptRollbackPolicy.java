package com.qj.services;

import cn.hutool.json.JSONObject;

import java.math.BigDecimal;

public final class WardrobePromptRollbackPolicy {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final double GLOBAL_MAXIMUM_FAILURE_RATE = 0.20;

    public Decision evaluate(JSONObject summary, int minimumSuccessful, double maximumFailureRate,
                             double minimumExactRegression, double minimumStyleMaeRegression) {
        if (minimumSuccessful <= 0 || !within(maximumFailureRate, 0, GLOBAL_MAXIMUM_FAILURE_RATE)
                || !within(minimumExactRegression, 0, 1)
                || !within(minimumStyleMaeRegression, 0, 100)
                || minimumExactRegression == 0 || minimumStyleMaeRegression == 0) {
            return Decision.keep("invalid_thresholds");
        }

        Integer completed = integerValue(summary, "completed");
        if (completed == null || completed < minimumSuccessful) {
            return Decision.keep("insufficient_successful_samples");
        }
        Integer total = integerValue(summary, "total");
        Integer failed = integerValue(summary, "failed");
        if (total == null || failed == null || total <= 0 || failed < 0 || completed < 0
                || completed + failed != total || (double) failed / total > maximumFailureRate) {
            return Decision.keep("failure_rate_exceeded");
        }

        JSONObject published = objectValue(summary, "publishedMetrics");
        JSONObject baseline = objectValue(summary, "baselineMetrics");
        BigDecimal publishedMae = metric(published, "styleMae", ZERO, ONE_HUNDRED);
        BigDecimal baselineMae = metric(baseline, "styleMae", ZERO, ONE_HUNDRED);
        BigDecimal publishedExact = metric(published, "exactMatchRate", ZERO, ONE);
        BigDecimal baselineExact = metric(baseline, "exactMatchRate", ZERO, ONE);
        if (publishedMae == null || baselineMae == null || publishedExact == null || baselineExact == null) {
            return Decision.keep("invalid_metrics");
        }

        if (baselineMae.compareTo(publishedMae) > 0 || baselineExact.compareTo(publishedExact) < 0) {
            return Decision.keep("metrics_conflict");
        }
        BigDecimal exactRegression = baselineExact.subtract(publishedExact);
        BigDecimal maeRegression = publishedMae.subtract(baselineMae);
        if (exactRegression.compareTo(BigDecimal.valueOf(minimumExactRegression)) >= 0) {
            return Decision.rollback("exact_match_regressed");
        }
        if (maeRegression.compareTo(BigDecimal.valueOf(minimumStyleMaeRegression)) >= 0) {
            return Decision.rollback("style_mae_regressed");
        }
        return Decision.keep("no_clear_regression");
    }

    private boolean within(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private Integer integerValue(JSONObject object, String key) {
        if (object == null || !(object.get(key) instanceof Number)) return null;
        try {
            return Integer.valueOf(object.get(key).toString());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private JSONObject objectValue(JSONObject object, String key) {
        if (object == null) return null;
        Object value = object.get(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    private BigDecimal metric(JSONObject object, String key, BigDecimal minimum, BigDecimal maximum) {
        if (object == null || !(object.get(key) instanceof Number)) return null;
        try {
            double raw = ((Number) object.get(key)).doubleValue();
            if (!Double.isFinite(raw)) return null;
            BigDecimal value = new BigDecimal(object.get(key).toString());
            return value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0 ? null : value;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static final class Decision {
        private final boolean rollback;
        private final String reason;

        private Decision(boolean rollback, String reason) {
            this.rollback = rollback;
            this.reason = reason;
        }

        public static Decision rollback(String reason) {
            return new Decision(true, reason);
        }

        public static Decision keep(String reason) {
            return new Decision(false, reason);
        }

        public boolean shouldRollback() {
            return rollback;
        }

        public String getReason() {
            return reason;
        }
    }
}
