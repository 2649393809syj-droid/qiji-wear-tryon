package com.qj.config;

import cn.hutool.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * 从 config/redis.setting 读取配置
 */
@Configuration
public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            // 读取 redis.setting 配置文件
            Setting redisSetting = new Setting("config/redis.setting");
            
            String host = redisSetting.getStr("host", "localhost");
            int port = redisSetting.getInt("port", 6379);
            String password = redisSetting.getStr("password", null);
            int database = redisSetting.getInt("database", 0);
            
            log.info("加载Redis配置 - host: {}, port: {}, database: {}, password: {}", 
                    host, port, database, password != null ? "已设置" : "未设置");
            
            // 配置Redis连接
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(host);
            config.setPort(port);
            config.setDatabase(database);
            
            // 如果配置了密码，则设置密码
            if (password != null && !password.trim().isEmpty()) {
                config.setPassword(password);
            }
            
            // 使用Lettuce连接工厂
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            
            log.info("Redis连接工厂初始化成功");
            return factory;
            
        } catch (Exception e) {
            log.error("加载Redis配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("Redis配置加载失败", e);
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(redisConnectionFactory);
        
        // 设置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        template.afterPropertiesSet();
        log.info("StringRedisTemplate初始化成功");
        return template;
    }
}

