package com.qj.controllers;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qj.entities.AgentResponse;
import com.qj.services.WardrobeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qj/wardrobe")
public class WardrobeController {

    private final WardrobeService wardrobeService;

    @Autowired
    public WardrobeController(WardrobeService wardrobeService) {
        this.wardrobeService = wardrobeService;
    }

    @PostMapping("/items")
    public AgentResponse createItem(@RequestParam("autoId") Long autoId,
                                    @RequestParam("openid") String openid,
                                    @RequestParam("navItemCur") Integer navItemCur,
                                    @RequestParam("imageUrl") String imageUrl) {
        return wardrobeService.createItem(autoId, openid, navItemCur, imageUrl);
    }

    @GetMapping("/items/by-legacy")
    public AgentResponse byLegacy(@RequestParam("autoId") Long autoId,
                                  @RequestParam("navItemCur") Integer navItemCur) {
        return wardrobeService.getByLegacy(autoId, navItemCur);
    }

    @PostMapping("/items/{itemId}/complete")
    public AgentResponse complete(@PathVariable Long itemId, @RequestBody String requestBody) {
        JSONObject request = JSONUtil.parseObj(requestBody);
        Object evaluation = request.get("advisorEvaluationJson");
        JSONObject advisorEvaluation = evaluation instanceof JSONObject
                ? (JSONObject) evaluation
                : evaluation == null ? null : JSONUtil.parseObj(String.valueOf(evaluation));
        return wardrobeService.complete(itemId, request.getStr("reviewerOpenid"), advisorEvaluation);
    }

    @GetMapping("/items")
    public AgentResponse userItems(@RequestParam("openid") String openid,
                                   @RequestParam(value = "status", required = false) String status,
                                   @RequestParam(value = "pageNumber", required = false) Integer pageNumber,
                                   @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return wardrobeService.getUserItems(openid, status, pageNumber, pageSize);
    }

    @GetMapping("/items/{itemId}/history")
    public AgentResponse history(@PathVariable Long itemId) {
        return wardrobeService.getHistory(itemId);
    }

    @GetMapping("/reviews")
    public AgentResponse reviews(@RequestParam(value = "pageNumber", required = false) Integer pageNumber,
                                 @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return wardrobeService.getPendingReviews(pageNumber, pageSize);
    }

    @GetMapping("/_health")
    public AgentResponse health() {
        return wardrobeService.health();
    }
}
