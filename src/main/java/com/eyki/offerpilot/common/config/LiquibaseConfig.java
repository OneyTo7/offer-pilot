package com.eyki.offerpilot.common.config;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Liquibase 手动配置。
 *
 * Spring Boot 4.x 已移除内置的 LiquibaseAutoConfiguration，需要手动创建 SpringLiquibase Bean。
 */
@Configuration
public class LiquibaseConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.liquibase.enabled", havingValue = "true", matchIfMissing = true)
    public SpringLiquibase liquibase(
            @Qualifier("dataSource") DataSource dataSource,
            Environment env) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(env.getProperty("spring.liquibase.change-log",
                "classpath:db/changelog/db.changelog-master.xml"));
        liquibase.setShouldRun(true);
        // 默认不设置 contexts/labels，使用 changelog 中的所有 changeset
        return liquibase;
    }
}