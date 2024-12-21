package com.sskings.shopping_delivery.integrationtests.testcontainers;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;

import java.util.Map;
import java.util.stream.Stream;

@ContextConfiguration(initializers = AbstractIntegrationTest.Initializer.class)
public class AbstractIntegrationTest {

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.28")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
                .waitingFor(Wait.forListeningPort());

        private static void startContainers(){
            try {
                System.out.println("Iniciando container MySQL...");
                Startables.deepStart(Stream.of(mysql)).join();
                System.out.println("Container MySQL iniciado com sucesso!");
            } catch (Exception e) {
                System.err.println("Erro ao iniciar o container MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        private static Map<String, String> createConnectionConfiguration(){
            return Map.of("spring.datasource.url", mysql.getJdbcUrl(),
                    "spring.datasource.username", mysql.getUsername(),
                    "spring.datasource.password", mysql.getPassword()
            );
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            startContainers();
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            MapPropertySource testcontainers =
                    new MapPropertySource(
                            "testcontainers",
                            (Map) createConnectionConfiguration());
            environment.getPropertySources().addFirst(testcontainers);
        }
    }
}
