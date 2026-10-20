package com.servicedesk.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("ServiceDesk API")
                .version("v1")
                .description("Multi-tenant support ticketing. Authentication is a session cookie; "
                        + "mutations require the X-XSRF-TOKEN header mirrored from the XSRF-TOKEN cookie."));
    }
}
