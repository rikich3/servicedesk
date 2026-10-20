package com.servicedesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ApplicationSmokeTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void contextStartsAgainstRealPostgres() {
        String version = jdbc.sql("select version()").query(String.class).single();
        assertThat(version).startsWith("PostgreSQL");
    }
}
