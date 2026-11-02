package com.qj.services;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qj.consts.FunctionCodes;
import com.qj.services.common.CRUDService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

@Service
public class LoginService {
    private static final String JWT_SECRET = "CHANGE_ME_QJ_JWT_SECRET";
    private static final long EXPIRE_MILLIS = 7 * 24 * 60 * 60 * 1000L; // 1周

    @Autowired
    private StringRedisTemplate redisTemplate;

    public JSONObject login(String username, String password) {
        JSONObject result = new JSONObject();
        if (username == null || password == null) {
            return result.set("errCode", 1).set("errMsg", "用户名或密码不能为空");
        }
        // 查询用户
        JSONObject queryParam = new JSONObject();
        JSONArray fieldKeys = new JSONArray();
        fieldKeys.add("username");
        queryParam.set("__mysql_fieldkeys__", fieldKeys);
        queryParam.set("username", username);
        JSONObject userResult = CRUDService.exeRetrieve(
                FunctionCodes.F_001_0001_USER_LOGIN,
                "F_001_0001_USER_LOGIN",
                "users",
                queryParam,
                new HashSet<>());
        if (userResult.getInt("errCode") != 0 || userResult.get("result") == null) {
            return result.set("errCode", 2).set("errMsg", "用户不存在");
        }
        JSONArray arr = userResult.getJSONArray("result");
        if (arr.isEmpty()) {
            return result.set("errCode", 2).set("errMsg", "用户不存在");
        }
        JSONObject user = arr.getJSONObject(0);
        String dbPwd = user.getStr("password");
        String inputMd5 = DigestUtil.md5Hex(password);
        if (!inputMd5.equalsIgnoreCase(dbPwd)) {
            return result.set("errCode", 3).set("errMsg", "密码错误");
        }
        // 登录成功，生成jwt
        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
        // 存入redis
        String redisKey = "login:token:" + username;
        redisTemplate.opsForValue().set(redisKey, token, EXPIRE_MILLIS, TimeUnit.MILLISECONDS);
        // 返回token
        return result.set("errCode", 0).set("token", token).set("msg", "登录成功");
    }

    public JSONObject register(String username, String password, String email) {
        JSONObject result = new JSONObject();
        if (username == null || password == null) {
            return result.set("errCode", 1).set("errMsg", "用户名或密码不能为空");
        }
        // md5加密
        String md5Pwd = DigestUtil.md5Hex(password);
        JSONObject insertParam = new JSONObject();
        JSONArray fieldKeys = new JSONArray();
        fieldKeys.add("username");
        fieldKeys.add("password");
        fieldKeys.add("email");
        insertParam.set("__mysql_fieldkeys__", fieldKeys);
        insertParam.set("username", username);
        insertParam.set("password", md5Pwd);
        insertParam.set("email", email);
        JSONObject insertResult = CRUDService.exeModifyOrDelete(
                FunctionCodes.F_001_0002_USER_REGISTER,
                "F_001_0002_USER_REGISTER",
                "users",
                insertParam,
                new HashSet<>());
        if (insertResult.getInt("errCode") == 0) {
            return result.set("errCode", 0).set("msg", "注册成功");
        } else {
            return result.set("errCode", 2).set("errMsg", "注册失败：" + insertResult.getStr("errMsg"));
        }
    }
}