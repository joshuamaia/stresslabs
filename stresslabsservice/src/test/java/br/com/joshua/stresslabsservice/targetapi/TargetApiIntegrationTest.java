package br.com.joshua.stresslabsservice.targetapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.joshua.stresslabsservice.support.IntegrationTest;

@IntegrationTest
class TargetApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void disableRateLimit() throws Exception {
        mvc.perform(put("/api/admin/rate-limit").contentType(MediaType.APPLICATION_JSON).content("""
                {"enabled":false,"capacity":100,"refillTokens":100,"refillPeriodSeconds":60,"clientStrategy":"IP"}
                """)).andExpect(status().isOk());
    }

    @Test
    void healthReturns200() throws Exception {
        mvc.perform(get("/api/target/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void listsSeededProductsFromPostgres() throws Exception {
        mvc.perform(get("/api/target/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void findsProductByIdAndReturns404ForUnknown() throws Exception {
        mvc.perform(get("/api/target/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Produto 0001"));
        mvc.perform(get("/api/target/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsProduct() throws Exception {
        mvc.perform(post("/api/target/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Novo","category":"teste","price":10.50,"stock":3}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
        mvc.perform(post("/api/target/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"","category":"teste","price":-1,"stock":3}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processingIsBounded() throws Exception {
        mvc.perform(get("/api/target/processing").param("workUnits", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedUnits").value(2));
        mvc.perform(get("/api/target/processing").param("workUnits", "1000"))
                .andExpect(status().isBadRequest());
    }
}
