package com.marillia.milebag.business.dadosbdo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * V59 contra PostgreSQL real: a correcao dos dados do passageiro sai junto com o BDO - o encaminhamento cancelado pela
 * companhia apaga o BDO que ninguem assumiu, mesmo que a empresa ja o tenha corrigido. Como superusuario do container.
 */
@Testcontainers
class AlteracaoDadosBdoBancoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final UUID EMPRESA = UUID.randomUUID();

    @BeforeAll
    static void migrar() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                PreparedStatement st = conexao.prepareStatement("""
                        INSERT INTO tenant (id, identificador_empresa, nome, status, criado_em, tipo)
                        VALUES (?, ?, 'EMPRESA', 'ATIVO', now(), 'EMPRESA_ENTREGA')
                        """)) {
            st.setObject(1, EMPRESA);
            st.setString(2, EMPRESA.toString().substring(0, 8));
            st.executeUpdate();
        }
    }

    private Connection conexao;

    @BeforeEach
    void abrirConexao() throws Exception {
        conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        conexao.setAutoCommit(false);
    }

    @AfterEach
    void fecharConexao() throws Exception {
        conexao.rollback();
        conexao.close();
    }

    @Test
    void apagarOBdoCorrigidoApagaAsCorrecoesDele() throws Exception {
        UUID bdo = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bdo (id, tenant_id, origem, status_conferencia, data_captura, criado_em, atualizado_em)
                VALUES (?, ?, 'COMPANHIA_AEREA', 'CONFIRMADO', now(), now(), now())
                """)) {
            st.setObject(1, bdo);
            st.setObject(2, EMPRESA);
            st.executeUpdate();
        }
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO alteracao_dados_bdo (id, tenant_id, bdo_id, endereco_anterior, endereco_novo, motivo, alterado_por,
                    data_hora, criado_em, atualizado_em)
                VALUES (?, ?, ?, 'Rua 1', 'Rua 2', 'passageiro ligou', ?, now(), now(), now())
                """)) {
            st.setObject(1, UUID.randomUUID());
            st.setObject(2, EMPRESA);
            st.setObject(3, bdo);
            st.setObject(4, UUID.randomUUID());
            st.executeUpdate();
        }

        try (PreparedStatement st = conexao.prepareStatement("DELETE FROM bdo WHERE id = ?")) {
            st.setObject(1, bdo);
            assertThat(st.executeUpdate()).isEqualTo(1);
        }
        try (PreparedStatement st = conexao.prepareStatement("SELECT count(*) FROM alteracao_dados_bdo WHERE bdo_id = ?")) {
            st.setObject(1, bdo);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                assertThat(rs.getLong(1)).isZero();
            }
        }
    }
}
