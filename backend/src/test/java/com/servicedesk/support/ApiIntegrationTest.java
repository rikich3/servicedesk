package com.servicedesk.support;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Base for HTTP-level tests: real PostgreSQL, real security filter chain, clean tables per test. */
@IntegrationTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestData data;
    protected ApiClient api;

    @BeforeEach
    void setUpApi() {
        data.reset();
        api = new ApiClient(mvc, json);
    }
}
