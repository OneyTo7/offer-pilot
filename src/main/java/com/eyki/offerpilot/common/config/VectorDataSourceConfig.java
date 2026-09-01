package com.eyki.offerpilot.common.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 数据源配置。
 *
 * Spring Boot 4.x 的 DataSourceConfiguration$Hikari 上有 @ConditionalOnMissingBean(DataSource.class)，
 * 只要有任何一个 DataSource Bean 存在，自动配置就会跳过主数据源的创建。
 * 因此需要手动创建所有数据源 Bean，并标注 @Primary 区分主数据源。
 */
@Configuration
public class VectorDataSourceConfig {

    /**
     * 主数据源（MySQL 业务数据）
     */
    @Primary
    @Bean
    public DataSource dataSource(Environment env) {
        return DataSourceBuilder.create()
                .url(env.getProperty("spring.datasource.url"))
                .username(env.getProperty("spring.datasource.username"))
                .password(env.getProperty("spring.datasource.password"))
                .driverClassName(env.getProperty("spring.datasource.driver-class-name"))
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 向量数据源（PostgreSQL + pgvector）
     */
    @Bean
    public DataSource vectorDataSource(Environment env) {
        HikariDataSource ds = DataSourceBuilder.create()
                .url(env.getProperty("spring.datasource.vector.url"))
                .username(env.getProperty("spring.datasource.vector.username"))
                .password(env.getProperty("spring.datasource.vector.password"))
                .driverClassName(env.getProperty("spring.datasource.vector.driver-class-name"))
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("pgvector");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        return ds;
    }

    @Bean
    public PlatformTransactionManager vectorTransactionManager(@Qualifier("vectorDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}