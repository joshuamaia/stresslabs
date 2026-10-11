package br.com.joshua.stresslabsservice.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.joshua.stresslabsservice.support.IntegrationTest;

@IntegrationTest
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private void configure(boolean enabled, int capacity, String strategy) throws Exception {
        mvc.perform(put("/api/admin/rate-limit").contentType(MediaType.APPLICATION_JSON).content("""
                {"enabled":%s,"capacity":%d,"refillTokens":%d,"refillPeriodSeconds":60,"clientStrategy":"%s"}
                """.formatted(enabled, capacity, capacity, strategy))).andExpect(status().isOk());
    }

    @AfterEach
    void restoreDefaults() throws Exception {
        configure(true, 100, "IP");
    }

    @Test
    void returns429WithHeadersWhenLimitIsExceeded() throws Exception {
        configure(true, 3, "IP");

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/target/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)));
        }
        mvc.perform(get("/api/target/health"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void adminAndMetricsEndpointsAreNotRateLimited() throws Exception {
        configure(true, 1, "GLOBAL");
        mvc.perform(get("/api/target/health")).andExpect(status().isOk());
        mvc.perform(get("/api/target/health")).andExpect(status().isTooManyRequests());

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/admin/rate-limit")).andExpect(status().isOk());
            mvc.perform(get("/api/admin/metrics/infrastructure")).andExpect(status().isOk());
        }
    }

    @Test
    void headerStrategySeparatesClients() throws Exception {
        configure(true, 1, "HEADER");
        mvc.perform(get("/api/target/health").header("X-Client-Id", "a")).andExpect(status().isOk());
        mvc.perform(get("/api/target/health").header("X-Client-Id", "a")).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/target/health").header("X-Client-Id", "b")).andExpect(status().isOk());
    }

    @Test
    void configurationChangesApplyWithoutRestart() throws Exception {
        configure(true, 1, "IP");
        mvc.perform(get("/api/target/health")).andExpect(status().isOk());
        mvc.perform(get("/api/target/health")).andExpect(status().isTooManyRequests());

        configure(false, 1, "IP");
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/target/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("X-RateLimit-Limit"));
        }
        mvc.perform(get("/api/admin/rate-limit")).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void rejectsInvalidConfiguration() throws Exception {
        mvc.perform(put("/api/admin/rate-limit").contentType(MediaType.APPLICATION_JSON).content("""
                {"enabled":true,"capacity":0,"refillTokens":1,"refillPeriodSeconds":60,"clientStrategy":"IP"}
                """)).andExpect(status().isBadRequest());
    }
}
