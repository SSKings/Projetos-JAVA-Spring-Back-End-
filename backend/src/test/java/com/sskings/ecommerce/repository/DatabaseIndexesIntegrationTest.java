package com.sskings.ecommerce.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V8: os índices das consultas frequentes existem depois das migrações, nas colunas certas e na ordem certa
 * (a primeira coluna é a que a consulta filtra). Medições antes/depois: docs/architecture.md, "Índices do banco".
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:indexes;DB_CLOSE_DELAY=-1")
class DatabaseIndexesIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private List<String> columns(String index) {
        return jdbc.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS
                WHERE INDEX_NAME = ? ORDER BY ORDINAL_POSITION""", String.class, index.toUpperCase());
    }

    @Test
    @DisplayName("V8 cria os índices das consultas frequentes, com as colunas na ordem usada pelas consultas")
    void frequentQueriesAreIndexed() {
        Map<String, List<String>> expected = Map.of(
                "idx_orders_user_status", List.of("USER_ID", "STATUS"),
                "idx_orders_status_created", List.of("STATUS", "CREATED_AT"),
                "idx_orders_created", List.of("CREATED_AT"),
                "idx_orders_address", List.of("ADDRESS_ID"),
                "idx_order_items_order", List.of("ORDER_ID"),
                "idx_payments_status_expires", List.of("STATUS", "EXPIRES_AT"),
                "idx_addresses_user", List.of("USER_ID"),
                "idx_audit_logs_actor_created", List.of("ACTOR_ID", "CREATED_AT"));
        expected.forEach((index, cols) -> assertThat(columns(index)).as(index).isEqualTo(cols));
    }

    @Test
    @DisplayName("Flyway aplicou a V8")
    void migrationApplied() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '8' AND \"success\" = TRUE", Integer.class))
                .isEqualTo(1);
    }
}
