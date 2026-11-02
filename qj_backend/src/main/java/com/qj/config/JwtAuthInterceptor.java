package com.qj.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private static final String JWT_SECRET = "CHANGE_ME_QJ_JWT_SECRET";
    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        String clientType = request.getHeader("X-Client-Type");

        log.info("JwtAuthInterceptor拦截到请求: {} {}, Client-Type: {}", request.getMethod(), uri, clientType);

        // 核心逻辑：只对带有manager标识的管理端请求进行JWT校验
        if (!"manager".equalsIgnoreCase(clientType)) {
            log.info("非管理端请求，直接放行: {}", uri);
            return true;
        }

        // 管理端请求需要进行JWT校验
        log.info("管理端请求，进行JWT校验: {}", uri);

        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            log.warn("管理端请求缺少Authorization token: {}", uri);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"errCode\":401,\"errMsg\":\"管理端访问需要登录，token缺失\"}");
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token)
                    .getBody();
            String username = claims.getSubject();

            // 校验redis中token是否有效
            String redisKey = "login:token:" + username;
            String redisToken = redisTemplate.opsForValue().get(redisKey);
            if (redisToken == null || !redisToken.equals(token)) {
                log.warn("管理端token已失效: {}, username: {}", uri, username);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"errCode\":401,\"errMsg\":\"token已失效，请重新登录\"}");
                return false;
            }

            // token有效，放行
            log.info("管理端JWT校验通过: {}, username: {}", uri, username);
            request.setAttribute("username", username);
            return true;

        } catch (Exception e) {
            log.error("管理端JWT校验失败: {}, error: {}", uri, e.getMessage());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"errCode\":401,\"errMsg\":\"token无效或已过期\"}");
            return false;
        }
    }
}