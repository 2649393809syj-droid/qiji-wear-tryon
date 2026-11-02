package com.qj.controllers;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.qj.services.LoginService;

@RestController
@RequestMapping("/auth")
public class LoginController {
    private static final String JWT_SECRET = "CHANGE_ME_QJ_JWT_SECRET";
    private static final long EXPIRE_MILLIS = 7 * 24 * 60 * 60 * 1000L; // 1周

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LoginService loginService;

    @PostMapping("/login")
    public JSONObject login(@RequestBody String sBody) {
        JSONObject params = JSONUtil.parseObj(sBody);
        String username = params.getStr("username");
        String password = params.getStr("password");
        return loginService.login(username, password);
    }

    @PostMapping("/register")
    public JSONObject register(@RequestBody String sBody) {
        JSONObject params = JSONUtil.parseObj(sBody);
        String username = params.getStr("username");
        String password = params.getStr("password");
        String email = params.getStr("email");
        return loginService.register(username, password, email);
    }
}