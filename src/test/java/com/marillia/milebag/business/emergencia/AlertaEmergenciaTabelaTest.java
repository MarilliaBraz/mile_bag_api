package com.marillia.milebag.business.emergencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tabela de alerta de emergencia (V34, HU30) contra PostgreSQL real: um
 * alerta aberto por motorista (RD16) e isolamento entre tenants (RNF01),
 * este ultimo como papel de aplicacao sem superusuario - mesma montagem de
 * TenantIsolationTest.
 */
@Testcontainers
class AlertaEmergenciaTabelaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final String APP_ROLE = "milebag_app_emergencia";
    private static final String APP_PASSWORD = "milebag_app_emergencia";

    @BeforeAll
    static void migrar() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement st = admin.createStatement()) {
            st.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            st.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_ROLE);
        }
    }

    private Connection conexao;

    @BeforeEach
    void abrir() throws Exception {
        conexao = DriverManager.getConnection(postgres.getJdbcUrl(), APP_ROLE, APP_PASSWORD);
        conexao.setAutoCommit(false);
    }

    @AfterEach
    void fechar() throws Exception {
        conexao.rollback();
        conexao.close();
    }

    @Test
    void segundoAlertaAbertoDoMesmoMotoristaERecusadoMasDepoisDeEncerradoPode() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID motorista = UUID.randomUUID();
        definirTenant(tenant);
        UUID primeiro = inserirAlerta(tenant, motorista, "ABERTO");

        try (Statement savepoint = conexao.createStatement()) {
            savepoint.execute("SAVEPOINT antes_do_segundo");
        }
        assertThatThrownBy(() -> inserirAlerta(tenant, motorista, "ABERTO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_alerta_emergencia_aberto_por_motorista");
        try (Statement volta = conexao.createStatement()) {
            volta.execute("ROLLBACK TO SAVEPOINT antes_do_segundo");
        }

        try (PreparedStatement st = conexao.prepareStatement("UPDATE alerta_emergencia SET status = 'CANCELADO' WHERE id = ?")) {
            st.setObject(1, primeiro);
            st.executeUpdate();
        }
        inserirAlerta(tenant, motorista, "ABERTO");
    }

    @Test
    void outroTenantNaoVeOAlerta() throws Exception {
        UUID tenantA = UUID.randomUUID();
        definirTenant(tenantA);
        UUID alerta = inserirAlerta(tenantA, UUID.randomUUID(), "ABERTO");

        definirTenant(UUID.randomUUID());
        try (PreparedStatement st = conexao.prepareStatement("SELECT count(*) FROM alerta_emergencia WHERE id = ?")) {
            st.setObject(1, alerta);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private void definirTenant(UUID tenantId) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
            st.setString(1, tenantId.toString());
            st.execute();
        }
    }

    private UUID inserirAlerta(UUID tenant, UUID motorista, String status) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO alerta_emergencia (id, tenant_id, motorista_id, data_hora, status, criado_em, atualizado_em)
                VALUES (?, ?, ?, now(), ?, now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, tenant);
            st.setObject(3, motorista);
            st.setString(4, status);
            st.executeUpdate();
        }
        return id;
    }
}
