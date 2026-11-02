package com.qj.services;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.LongSupplier;

/** Verifies short-lived advisor tokens issued by the authenticated WeChat cloud login. */
@Service
public class WardrobePromptAuthService {

    private final boolean required;
    private final String secret;
    private final LongSupplier epochSeconds;

    @Autowired
    public WardrobePromptAuthService(
            @Value("${wardrobe.prompt.auth-required:true}") boolean required,
            @Value("${wardrobe.prompt.auth-secret:}") String secret) {
        this(required, secret, () -> System.currentTimeMillis() / 1000L);
    }

    WardrobePromptAuthService(boolean required, String secret, LongSupplier epochSeconds) {
        this.required = required;
        this.secret = StrUtil.trim(secret);
        this.epochSeconds = epochSeconds;
    }

    public boolean isRequired() {
        return required;
    }

    public String resolveActor(String token, String localActor) {
        if (required) return verify(token);
        return StrUtil.blankToDefault(StrUtil.trim(localActor), "local-advisor");
    }

    public String verify(String token) {
        if (StrUtil.isBlank(secret)) {
            throw new SecurityException("Wardrobe Prompt authorization is not configured");
        }
        if (StrUtil.isBlank(token)) {
            throw new SecurityException("Advisor authorization token is required");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) throw new SecurityException("Advisor authorization token is invalid");
        try {
            byte[] expected = hmac(parts[0]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new SecurityException("Advisor authorization token is invalid");
            }
            JSONObject payload = JSONUtil.parseObj(new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            String openid = StrUtil.trim(payload.getStr("sub"));
            Long expiresAt = payload.getLong("exp");
            if (StrUtil.isBlank(openid) || expiresAt == null || expiresAt <= epochSeconds.getAsLong()) {
                throw new SecurityException("Advisor authorization token has expired");
            }
            return openid;
        } catch (SecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new SecurityException("Advisor authorization token is invalid", error);
        }
    }

    private byte[] hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
