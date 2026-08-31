package com.eyki.offerpilot.aicore.config;

import com.eyki.offerpilot.aicore.advisor.MyLogAdvisor;
import com.eyki.offerpilot.aicore.advisor.ReReadingAdvisor;
import com.eyki.offerpilot.aicore.advisor.SafeValidAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCoreConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultAdvisors(new SafeValidAdvisor(), new ReReadingAdvisor(), new MyLogAdvisor()).build();
    }
}