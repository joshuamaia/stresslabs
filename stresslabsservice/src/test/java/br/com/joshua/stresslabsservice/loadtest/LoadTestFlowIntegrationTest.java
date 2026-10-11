package br.com.joshua.stresslabsservice.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.com.joshua.stresslabsservice.loadtest.application.LoadTestService;
import br.com.joshua.stresslabsservice.loadtest.application.port.out.LoadGeneratorPort;
import br.com.joshua.stresslabsservice.support.IntegrationTest;

/**
 * Fluxo completo no backend: início → snapshots e resultado enviados pelo gerador → persistência no
 * PostgreSQL → histórico → comparação. O gerador (outro processo) é substituído por um mock da porta.
 */
@IntegrationTest
class LoadTestFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LoadTestService loadTestService;
    @MockitoBean
    private LoadGeneratorPort generator;

    @BeforeEach
    void ensureNoActiveExecution() {
        when(generator.cancel(any())).thenReturn(false);
        loadTestService.active().ifPresent(active -> loadTestService.stop(active.id()));
    }

    private String start(String scenario, boolean rateLimit, int total) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/load-tests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"fluxo","scenario":"%s","targetKey":"health","totalRequests":%d,"concurrency":5,
                         "targetRps":50,"durationSeconds":10,"timeoutMs":1000,"rateLimitEnabled":%s}
                        """.formatted(scenario, total, rateLimit)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.rateLimitEnabled").value(rateLimit))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void sendSnapshot(String id, int expectedStatus) throws Exception {
        mvc.perform(post("/api/internal/load-tests/{id}/snapshots", id).header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"timestamp":"%s","elapsedMs":1000,"currentRps":48.5,"targetRps":50,"inFlight":3,
                         "maxInFlight":5,"avgLatencyMs":4.2,"p95LatencyMs":9.1,"p99LatencyMs":12.3,"sent":50,
                         "completed":49,"success":40,"blocked":9,"httpErrors":0,"connectionFailures":0,"timeouts":0,
                         "status2xx":40,"status4xx":9,"status5xx":0,"progress":0.5}
                        """.formatted(Instant.now())))
                .andExpect(status().is(expectedStatus));
    }

    private void sendResult(String id, double avgRps, long blocked) throws Exception {
        mvc.perform(post("/api/internal/load-tests/{id}/result", id).header("X-Internal-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"status":"COMPLETED","totalRequests":100,"completed":100,"success":%d,"blocked":%d,
                         "httpErrors":0,"connectionFailures":0,"timeouts":0,"status2xx":%d,"status3xx":0,
                         "status4xx":%d,"status5xx":0,"avgRps":%s,"avgLatencyMs":4.0,"p95LatencyMs":9.0,
                         "p99LatencyMs":12.0,"minLatencyMs":1.0,"maxLatencyMs":20.0,"maxInFlight":5,"durationMs":2000}
                        """.formatted(100 - blocked, blocked, 100 - blocked, blocked, avgRps)))
                .andExpect(status().isAccepted());
    }

    @Test
    void executesPersistsAndComparesExecutions() throws Exception {
        String withoutLimit = start("CONSTANT", false, 100);
        verify(generator).start(any());

        // Execuções simultâneas conflitantes são bloqueadas
        mvc.perform(post("/api/admin/load-tests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"scenario":"CONSTANT","targetKey":"health","totalRequests":10,"concurrency":1,
                         "targetRps":1,"durationSeconds":1,"timeoutMs":1000,"rateLimitEnabled":false}
                        """))
                .andExpect(status().isConflict());

        sendSnapshot(withoutLimit, 202);
        sendResult(withoutLimit, 50.0, 0);
        sendSnapshot(withoutLimit, 410); // após o término, snapshots são recusados

        mvc.perform(get("/api/admin/load-tests/{id}", withoutLimit))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/admin/metrics/executions/{id}/snapshots", withoutLimit))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rps").value(48.5));
        mvc.perform(get("/api/admin/metrics/executions/{id}/result", withoutLimit))
                .andExpect(jsonPath("$.successRate").value(100.0));

        String withLimit = start("RATE_LIMIT", true, 101);
        sendResult(withLimit, 40.0, 60);

        mvc.perform(get("/api/admin/history").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].execution.id").value(withLimit))
                .andExpect(jsonPath("$.content[0].result.blockedCount").value(60));

        MvcResult comparison = mvc.perform(get("/api/admin/history/compare").param("a", withoutLimit)
                        .param("b", withLimit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a.execution.rateLimitEnabled").value(false))
                .andExpect(jsonPath("$.b.execution.rateLimitEnabled").value(true))
                .andExpect(jsonPath("$.metrics[?(@.key=='blocked')].valueB").value(60.0))
                .andExpect(jsonPath("$.metrics[?(@.key=='throughput')].difference").value(-10.0))
                .andReturn();
        assertThat(comparison.getResponse().getContentAsString()).contains("successRate");
    }

    @Test
    void callbacksRequireTheInternalToken() throws Exception {
        String id = start("CONSTANT", false, 10);
        mvc.perform(post("/api/internal/load-tests/{id}/result", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/load-tests/{id}/stop", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void rejectsInvalidParametersAndUnknownTargets() throws Exception {
        mvc.perform(post("/api/admin/load-tests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"scenario":"CONSTANT","targetKey":"http://example.com","totalRequests":10,"concurrency":1,
                         "targetRps":1,"durationSeconds":1,"timeoutMs":1000,"rateLimitEnabled":false}
                        """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/load-tests").contentType(MediaType.APPLICATION_JSON).content("""
                        {"scenario":"CONSTANT","targetKey":"health","totalRequests":10,"concurrency":100000,
                         "targetRps":1,"durationSeconds":1,"timeoutMs":1000,"rateLimitEnabled":false}
                        """))
                .andExpect(status().isBadRequest());
    }
}
