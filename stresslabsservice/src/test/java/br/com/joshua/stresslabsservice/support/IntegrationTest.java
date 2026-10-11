package br.com.joshua.stresslabsservice.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Teste de integração com PostgreSQL real (Testcontainers). Ignorado se o Docker não estiver disponível. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "stresslabs.internal-token=test-token")
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {
}
