package com.servicedesk.support;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Starts a real PostgreSQL server (no Docker daemon required) and exposes it as the
 * application's DataSource, so Boot's own DataSource auto-configuration backs off.
 * Used by every integration test and by {@code TestServiceDeskApplication} for the
 * zero-setup development mode.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder().start();
    }

    @Bean(destroyMethod = "close")
    @Primary
    DataSource embeddedDataSource(EmbeddedPostgres postgres) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl("postgres", "postgres"));
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");
        dataSource.setMaximumPoolSize(8);
        return dataSource;
    }
}
