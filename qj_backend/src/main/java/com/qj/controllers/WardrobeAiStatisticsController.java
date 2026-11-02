package com.qj.controllers;

import cn.hutool.json.JSONObject;
import com.qj.entities.AgentResponse;
import com.qj.services.WardrobeAiStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qj/wardrobe/statistics")
public class WardrobeAiStatisticsController {

    private final WardrobeAiStatisticsService statisticsService;

    public WardrobeAiStatisticsController(WardrobeAiStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public AgentResponse statistics(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "promptVersionId", required = false) Long promptVersionId,
            @RequestParam(value = "navItemCur", required = false) Integer navItemCur) {
        try {
            JSONObject filter = new JSONObject();
            if (from != null) filter.set("from", from);
            if (to != null) filter.set("to", to);
            if (promptVersionId != null) filter.set("promptVersionId", promptVersionId);
            if (navItemCur != null) filter.set("navItemCur", navItemCur);
            return AgentResponse.success("Query succeeded", statisticsService.get(filter));
        } catch (Exception e) {
            return AgentResponse.fail(e.getMessage());
        }
    }
}
