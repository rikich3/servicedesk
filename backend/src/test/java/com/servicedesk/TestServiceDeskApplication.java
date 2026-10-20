package com.servicedesk;

import com.servicedesk.support.EmbeddedPostgresConfig;
import org.springframework.boot.SpringApplication;

/**
 * Zero-setup development entry point: boots the real application on top of an embedded
 * PostgreSQL (no Docker needed) with the {@code dev} profile, which seeds demo data.
 *
 * <pre>mvn spring-boot:test-run</pre>
 */
public class TestServiceDeskApplication {

    public static void main(String[] args) {
        SpringApplication.from(ServiceDeskApplication::main)
                .with(EmbeddedPostgresConfig.class)
                .withAdditionalProfiles("dev")
                .run(args);
    }
}
