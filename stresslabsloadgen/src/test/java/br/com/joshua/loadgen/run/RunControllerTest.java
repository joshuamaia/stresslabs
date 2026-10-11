package br.com.joshua.loadgen.run;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "loadgen.internal-token=test-token")
@AutoConfigureMockMvc
class RunControllerTest {

    @Autowired
    private MockMvc mvc;

    private static String body(String path, int concurrency) {
        return """
                {"executionId":"%s","scenario":"CONSTANT","method":"GET","path":"%s","idRange":0,
                 "totalRequests":10,"concurrency":%d,"targetRps":10,"durationSeconds":5,"timeoutMs":1000}
                """.formatted(UUID.randomUUID(), path, concurrency);
    }

    @Test
    void rejectsRequestsWithoutInternalToken() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body("/api/target/health", 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTargetsOutsideTheAllowlist() throws Exception {
        mvc.perform(post("/api/runs").header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body("http://example.com/", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsConcurrencyAboveTheLimit() throws Exception {
        mvc.perform(post("/api/runs").header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body("/api/target/health", 100_000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelOfUnknownExecutionReturnsNotFound() throws Exception {
        mvc.perform(post("/api/runs/{id}/cancel", UUID.randomUUID()).header("X-Internal-Token", "test-token"))
                .andExpect(status().isNotFound());
    }
}
