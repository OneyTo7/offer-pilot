package com.eyki.offerpilot.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 配置。生产环境应通过环境变量 CORS_ALLOWED_ORIGINS 限定前端域名，
 * 默认为通配符（兼容本地开发），但不会与 allowCredentials(true) 冲突——
 * 通配符模式由 allowedOriginPatterns 处理，而非 allowedOrigins。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 使用 allowedOriginPatterns 而非 allowedOrigins：
        // 通配符（*）与 allowCredentials(true) 不兼容，而 patterns 方式支持通配符
        config.setAllowedOriginPatterns(List.of(
            System.getenv("CORS_ALLOWED_ORIGINS") != null
                ? System.getenv("CORS_ALLOWED_ORIGINS").split(",")
                : new String[]{"*"}
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}