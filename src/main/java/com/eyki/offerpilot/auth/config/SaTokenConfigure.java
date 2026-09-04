package com.eyki.offerpilot.auth.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register Sa-Token interceptor, exclude auth, health, and SSE async endpoints
        // The answer endpoint uses SseEmitter which triggers async dispatch;
        // exclude it from the interceptor because Sa-Token context is lost on async dispatch.
        // Auth is still enforced inside the service method via StpUtil.getLoginIdAsLong().
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin())).addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                "/api/health", "/api/v1/interviews/*/answer", "/api/v1/assistant/*/chat");
    }
}