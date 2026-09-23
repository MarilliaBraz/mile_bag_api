package com.marillia.milebag.business.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * Teste de isolamento entre tenants (RNF01) contra PostgreSQL real, via
 * Testcontainers - pedido explicitamente em varias tasks.md (HU05, HU06,
 * HU07, HU08, HU09, HU14) desde a implementacao de cada uma, nunca escrito
 * ate agora. Ver requisitos/02-requisitos-nao-funcionais.md (RNF01):
 * "verificado por testes automatizados de vazamento entre tenants".
 *
 * <p>Conecta como um papel de aplicacao SEM superusuario/dono das tabelas -
 * exatamente a configuracao de producao (application-dev.yml, comentario
 * RLS: "MILEBAG_DB_USER nao pode ser o dono/superusuario das tabelas, senao
 * a politica de Row Level Security e ignorada por padrao do PostgreSQL").
 * Testar com o usuario superusuario do container teria dado falso-positivo:
 * superusuarios ignoram RLS mesmo com FORCE ROW LEVEL SECURITY.</p>
 *
 * <p>Usa {@code motivo_ocorrencia} (V12) como tabela de prova: mais simples
 * do domain model com RLS (sem FK), mas a policy testada e o mesmo padrao
 * (V1..V23) aplicado a toda tabela tenant-scoped do sistema.</p>
 */
@Testcontainers
class TenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final String APP_ROLE = "milebag_app_test";
    private static final String APP_PASSWORD = "milebag_app_test";

    @BeforeAll
    static void migrarEProvisionarPapelDeAplicacao() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // Papel de aplicacao: LOGIN, mas nao dono das tabelas (criadas pelo
        // superusuario do container acima, via Flyway) e sem BYPASSRLS -
        // reproduz a separacao de privilegios exigida pela RLS de producao.
        try (Connection admin = conectarComo(postgres.getUsername(), postgres.getPassword());
                Statement st = admin.createStatement()) {
            st.execute("DROP ROLE IF EXISTS " + APP_ROLE);
            st.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
            st.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + APP_ROLE);
        }
    }

    private Connection conexaoApp;

    @BeforeEach
    void abrirConexaoDeAplicacao() throws Exception {
        conexaoApp = conectarComo(APP_ROLE, APP_PASSWORD);
        conexaoApp.setAutoCommit(false);
    }

    @AfterEach
    void fecharConexao() throws Exception {
        conexaoApp.rollback();
        conexaoApp.close();
    }

    @Test
    void tenantNaoVeMotivoDeOcorrenciaDeOutroTenant() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID motivoDeA = UUID.randomUUID();

        definirTenant(conexaoApp, tenantA);
        try (PreparedStatement insert = conexaoApp.prepareStatement(
                "INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo) VALUES (?, ?, ?, true)")) {
            insert.setObject(1, motivoDeA);
            insert.setObject(2, tenantA);
            insert.setString(3, "Endereco nao localizado");
            insert.executeUpdate();
        }
        conexaoApp.commit();

        // Mesmo tenant que criou a linha: continua vendo-a normalmente.
        definirTenant(conexaoApp, tenantA);
        assertThat(contarPorId(conexaoApp, motivoDeA)).isEqualTo(1);
        conexaoApp.commit();

        // Tenant diferente: a linha e invisivel por RLS (nao por filtro de
        // aplicacao) - a query nem teria como saber que ela existe.
        definirTenant(conexaoApp, tenantB);
        assertThat(contarPorId(conexaoApp, motivoDeA)).isEqualTo(0);
        conexaoApp.commit();
    }

    @Test
    void tenantDiferenteNaoConseguePersistirLinhaComTenantIdDeOutro() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // WITH CHECK da policy (V12) barra a escrita de uma linha cujo
        // tenant_id nao e o da sessao atual, mesmo que o valor venha
        // manipulado no INSERT - a policy vale para escrita, nao so leitura.
        definirTenant(conexaoApp, tenantA);
        try (PreparedStatement insert = conexaoApp.prepareStatement(
                "INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo) VALUES (?, ?, ?, true)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, tenantB);
            insert.setString(3, "Tentativa de escrita cross-tenant");
            java.sql.SQLException erro = org.junit.jupiter.api.Assertions.assertThrows(
                    java.sql.SQLException.class, insert::executeUpdate);
            // "42501" (insufficient_privilege) e o SQLSTATE especifico de uma
            // violacao de WITH CHECK de RLS - distingue do erro generico de
            // "relation does not exist" (que tambem e SQLException, mas nao
            // prova isolamento nenhum).
            assertThat(erro.getSQLState()).isEqualTo("42501");
            assertThat(erro.getMessage()).contains("row-level security policy");
        }
    }

    @Test
    void semTenantDefinidoNaSessaoNenhumaLinhaEVisivel() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID motivoDeA = UUID.randomUUID();

        definirTenant(conexaoApp, tenantA);
        try (PreparedStatement insert = conexaoApp.prepareStatement(
                "INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo) VALUES (?, ?, ?, true)")) {
            insert.setObject(1, motivoDeA);
            insert.setObject(2, tenantA);
            insert.setString(3, "Endereco nao localizado");
            insert.executeUpdate();
        }
        conexaoApp.commit();

        // Sessao nova, sem TenantAspect (nenhum set_config chamado): postura
        // fail-closed esperada - current_setting(..., true) volta NULL, e
        // "tenant_id = NULL" nunca e verdadeiro, entao zero linhas.
        try (Connection semTenant = conectarComo(APP_ROLE, APP_PASSWORD)) {
            assertThat(contarPorId(semTenant, motivoDeA)).isEqualTo(0);
        }
    }

    private static Connection conectarComo(String usuario, String senha) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), usuario, senha);
    }

    /** Mesmo mecanismo de {@code TenantAspect} em producao: set_config(..., true), local a transacao. */
    private static void definirTenant(Connection conn, UUID tenantId) throws Exception {
        try (PreparedStatement st = conn.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
            st.setString(1, tenantId.toString());
            st.execute();
        }
    }

    private static int contarPorId(Connection conn, UUID id) throws Exception {
        try (PreparedStatement st = conn.prepareStatement("SELECT count(*) FROM motivo_ocorrencia WHERE id = ?")) {
            st.setObject(1, id);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
