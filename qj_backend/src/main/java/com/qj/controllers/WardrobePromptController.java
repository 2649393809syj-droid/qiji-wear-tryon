package com.qj.controllers;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;
import com.qj.config.WardrobePromptAuthInterceptor;
import com.qj.entities.AgentResponse;
import com.qj.services.WardrobePromptExperimentService;
import com.qj.services.WardrobePromptOptimizationService;
import com.qj.services.WardrobePromptVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/qj/wardrobe/prompts")
public class WardrobePromptController {

    private final WardrobePromptVersionService promptService;
    private final WardrobePromptOptimizationService optimizationService;
    private final WardrobePromptExperimentService experimentService;

    public WardrobePromptController(WardrobePromptVersionService promptService,
                                    WardrobePromptOptimizationService optimizationService,
                                    WardrobePromptExperimentService experimentService) {
        this.promptService = promptService;
        this.optimizationService = optimizationService;
        this.experimentService = experimentService;
    }

    @GetMapping
    public AgentResponse list() {
        try {
            return AgentResponse.success("Query succeeded", promptService.listJson());
        } catch (Exception e) {
            return AgentResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public AgentResponse detail(@PathVariable Long id) {
        try {
            return AgentResponse.success("Query succeeded", promptService.get(id).toJson());
        } catch (Exception e) {
            return AgentResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/candidates")
    public AgentResponse createCandidate() {
        return automaticOnly();
    }

    @PostMapping("/{id}/publish")
    public AgentResponse publish(@PathVariable Long id, HttpServletRequest request) {
        return automaticOnly();
    }

    @PostMapping("/{id}/rollback")
    public AgentResponse rollback(@PathVariable Long id, @RequestBody String body, HttpServletRequest request) {
        try {
            String reason = JSONUtil.parseObj(body).getStr("reason");
            if (StrUtil.isBlank(reason)) {
                throw new IllegalArgumentException("Prompt rollback reason is required");
            }
            return AgentResponse.success("Prompt rolled back", promptService.rollback(id, actor(request), reason).toJson());
        } catch (Exception error) {
            return AgentResponse.fail(error.getMessage());
        }
    }

    @PostMapping("/optimization-jobs")
    public AgentResponse startOptimization() {
        return automaticOnly();
    }

    @GetMapping("/optimization-jobs")
    public AgentResponse optimizationJobs(@RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        try {
            return AgentResponse.success("Query succeeded", optimizationService.listJobs(limit));
        } catch (Exception error) {
            return AgentResponse.fail(error.getMessage());
        }
    }

    @GetMapping("/optimization-jobs/{id}")
    public AgentResponse optimizationDetail(@PathVariable Long id) {
        try {
            return AgentResponse.success("Query succeeded", optimizationService.getJob(id));
        } catch (Exception e) {
            return AgentResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{candidateId}/evaluate")
    public AgentResponse evaluate(@PathVariable Long candidateId, @RequestBody String body) {
        return automaticOnly();
    }

    @GetMapping("/{candidateId}/evaluation")
    public AgentResponse evaluation(@PathVariable Long candidateId,
                                    @RequestParam(value = "optimizationId", required = false) Long optimizationId) {
        try {
            return AgentResponse.success("Query succeeded", experimentService.get(candidateId, optimizationId));
        } catch (Exception e) {
            return AgentResponse.fail(e.getMessage());
        }
    }

    private AgentResponse automaticOnly() {
        return AgentResponse.fail("Prompt optimization is automatic");
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(WardrobePromptAuthInterceptor.ACTOR_ATTRIBUTE);
        if (value == null) throw new SecurityException("Advisor identity is missing");
        return String.valueOf(value);
    }
}
