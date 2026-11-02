package com.qj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.qj.config.WxMiniAppConfig;

@SpringBootApplication
@EnableScheduling
public class QjBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QjBackendApplication.class, args);
    }

    /**
     * 配置编码格式
     * 
     * @return
     */
    @Bean
    public CharacterEncodingFilter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceRequestEncoding(true);
        filter.setForceResponseEncoding(true);
        return filter;
    }
}
