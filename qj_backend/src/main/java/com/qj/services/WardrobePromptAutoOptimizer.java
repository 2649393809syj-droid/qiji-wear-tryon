package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/** Advances one durable automatic Prompt job at a time and safely resumes it after restarts. */
@Service
public class WardrobePromptAutoOptimizer {

    private static final Logger log = LoggerFactory.getLogger(WardrobePromptAutoOptimizer.class);
    private final WardrobePromptOptimizationService optimizations;
    private final WardrobePromptVersionService prompts;
    private final WardrobePromptExperimentService experiments;
    private final WardrobePromptPublicationPolicy policy;
    private final TaskExecutor executor;
    private final boolean enabled;
    private final int minimumHeldout;
    private final double maximumFailureRate;
    private final AtomicBoolean workerScheduled = new AtomicBoolean();

    @Autowired
    public WardrobePromptAutoOptimizer(WardrobePromptOptimizationService optimizations,
                                       WardrobePromptVersionService prompts,
                                       WardrobePromptExperimentService experiments,
                                       @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                                       @Value("${wardrobe.prompt.auto.enabled:true}") boolean enabled,
                                       @Value("${wardrobe.prompt.auto.minimum-heldout:2}") int minimumHeldout,
                                       @Value("${wardrobe.prompt.auto.maximum-failure-rate:0.20}") double maximumFailureRate) {
        this(optimizations, prompts, experiments, new WardrobePromptPublicationPolicy(), executor,
                enabled, minimumHeldout, maximumFailureRate);
    }

    WardrobePromptAutoOptimizer(WardrobePromptOptimizationService optimizations,
                                 WardrobePromptVersionService prompts,
                                 WardrobePromptExperimentService experiments,
                                 WardrobePromptPublicationPolicy policy,
                                 TaskExecutor executor, boolean enabled, int minimumHeldout,
                                 double maximumFailureRate) {
        this.optimizations = optimizations;
        this.prompts = prompts;
        this.experiments = experiments;
        this.policy = policy;
        this.executor = executor;
        this.enabled = enabled;
        this.minimumHeldout = minimumHeldout;
        this.maximumFailureRate = maximumFailureRate;
    }

    @EventListener
    public void onReviewCompleted(WardrobeReviewCompletedEvent event) {
        requestRun();
    }

    @Scheduled(fixedDelayString = "${wardrobe.prompt.auto.recovery-delay-ms:60000}")
    public void recover() {
        requestRun();
    }

    public void requestRun() {
        if (!enabled || !workerScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::runUntilIdle);
        } catch (RuntimeException error) {
            workerScheduled.set(false);
            log.warn("Unable to schedule automatic Prompt optimization", error);
        }
    }

    public JSONObject advanceOnce() {
        if (!enabled) {
            return action("idle", null);
        }
        JSONObject job = null;
        try {
            job = optimizations.getActiveAutomaticJob();
            if (job == null) {
                JSONObject created = optimizations.startAutomatic();
                return action(created == null ? "idle" : "candidate_created", created);
            }
            String status = job.getStr("status");
            Long jobId = job.getLong("id");
            if ("waiting_for_holdout".equals(status)) {
                JSONObject refreshed = optimizations.refreshHeldout(jobId);
                return action(refreshed != null && "generating".equals(refreshed.getStr("status"))
                        ? "heldout_ready" : "waiting_for_holdout", refreshed == null ? job : refreshed);
            }
            if ("pending".equals(status) || ("generating".equals(status)
                    && job.getLong("candidatePromptVersionId") == null)) {
                return action("candidate_created", optimizations.resumeGeneration(jobId));
            }
            if ("generating".equals(status) && job.getLong("candidatePromptVersionId") != null) {
                Long candidateId = job.getLong("candidatePromptVersionId");
                prompts.markEvaluating(candidateId);
                optimizations.markEvaluating(jobId);
                return action("evaluation_started", experiments.start(candidateId, jobId));
            }
            if ("evaluating".equals(status)) {
                return finishEvaluation(job, experiments.start(job.getLong("candidatePromptVersionId"), jobId));
            }
            return action("idle", job);
        } catch (Exception error) {
            return fail(job, error);
        }
    }

    private void runUntilIdle() {
        try {
            while (true) {
                String action = advanceOnce().getStr("action");
                if ("idle".equals(action) || "waiting_for_holdout".equals(action)
                        || "evaluation_running".equals(action) || "failed".equals(action)) {
                    return;
                }
            }
        } finally {
            workerScheduled.set(false);
        }
    }

    private JSONObject finishEvaluation(JSONObject job, JSONObject summary) throws Exception {
        if (summary == null || "running".equals(summary.getStr("status"))) {
            return action("evaluation_running", job);
        }
        Long jobId = job.getLong("id");
        Long candidateId = job.getLong("candidatePromptVersionId");
        Long baseId = job.getLong("basePromptVersionId");
        optimizations.recordEvaluation(jobId, summary);
        WardrobePromptVersionService.PromptVersion active = prompts.getActivePrompt();
        if (active == null || !baseId.equals(active.getId())) {
            return action("stale", optimizations.markStale(jobId));
        }
        WardrobePromptPublicationPolicy.Decision decision = policy.evaluate(summary, minimumHeldout, maximumFailureRate);
        if (decision.isEligible()) {
            prompts.autoPublish(candidateId, baseId, jobId, decision.getReason());
            return action("published", job);
        }
        prompts.reject(candidateId, decision.getReason());
        return action("rejected", optimizations.markRejected(jobId, decision.getReason()));
    }

    private JSONObject fail(JSONObject job, Exception error) {
        String message = safeMessage(error);
        if (job != null && job.getLong("id") != null && StrUtil.isNotBlank(job.getStr("status"))) {
            try {
                optimizations.markFailed(job.getLong("id"), job.getStr("status"), message);
            } catch (Exception markerFailure) {
                log.error("Unable to mark automatic Prompt optimization failed jobId={}", job.getLong("id"), markerFailure);
            }
        }
        log.warn("Automatic Prompt optimization step failed: {}", message);
        return action("failed", job);
    }

    private JSONObject action(String action, JSONObject job) {
        return new JSONObject().set("action", action).set("job", job);
    }

    private String safeMessage(Exception error) {
        String message = error == null ? null : error.getMessage();
        if (StrUtil.isBlank(message)) {
            return "Automatic Prompt optimization failed";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
