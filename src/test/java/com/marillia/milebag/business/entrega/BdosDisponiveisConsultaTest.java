package com.marillia.milebag.business.entrega;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.marillia.milebag.business.bdo.repository.BdoRepository;

/**
 * Lista de BDOs disponiveis (HU19) contra PostgreSQL real, depois de HU29:
 * a entrega reaberta por ocorrencia (RN05) volta a lista; a atribuida e a
 * bagagem carona nao. Tambem confirma que V33 aplica (colunas e CHECK da
 * janela de reagendamento). Conecta como superusuario do container: o que
 * se testa aqui e a semantica do SQL, nao a RLS (coberta por
 * TenantIsolationTest).
 */
@Testcontainers
class BdosDisponiveisConsultaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeAll
    static void migrar() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
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
    void entregaReabertaSemMotoristaVoltaParaALista() throws Exception {
        UUID semEntrega = inserirBdo();
        UUID atribuida = inserirBdo();
        inserirEntrega(atribuida, UUID.randomUUID(), "ATRIBUIDA");
        UUID reaberta = inserirBdo();
        inserirEntrega(reaberta, null, "REABERTA");
        UUID carona = inserirBdo();
        UUID entregaPrincipal = inserirEntrega(inserirBdo(), UUID.randomUUID(), "ATRIBUIDA");
        inserirBagagemCarona(carona, entregaPrincipal);

        assertThat(disponiveis()).contains(semEntrega, reaberta).doesNotContain(atribuida, carona);
    }

    @Test
    void janelaDeReagendamentoInvertidaERecusadaPeloBanco() throws Exception {
        UUID bdo = inserirBdo();
        UUID entrega = inserirEntrega(bdo, null, "REABERTA");

        try (PreparedStatement st = conexao.prepareStatement(
                "UPDATE entrega SET janela_reagendamento_inicio = ?, janela_reagendamento_fim = ? WHERE id = ?")) {
            st.setTimestamp(1, Timestamp.from(Instant.parse("2026-10-23T19:00:00Z")));
            st.setTimestamp(2, Timestamp.from(Instant.parse("2026-10-23T17:00:00Z")));
            st.setObject(3, entrega);
            assertThatThrownBy(st::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("ck_entrega_janela_reagendamento");
        }
    }

    private List<UUID> disponiveis() throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement st = conexao.prepareStatement(BdoRepository.CONSULTA_DISPONIVEIS_PARA_AUTOATRIBUICAO);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getObject("id", UUID.class));
            }
        }
        return ids;
    }

    private UUID inserirBdo() throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bdo (id, tenant_id, origem, status_conferencia, data_captura, criado_em, atualizado_em)
                VALUES (?, ?, 'EMAIL_AUTOMATICO', 'CONFIRMADO', now(), now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, TENANT);
            st.executeUpdate();
        }
        return id;
    }

    private UUID inserirEntrega(UUID bdoId, UUID motorista, String status) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO entrega (id, tenant_id, bdo_id, motorista_responsavel, status, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, TENANT);
            st.setObject(3, bdoId);
            st.setObject(4, motorista);
            st.setString(5, status);
            st.executeUpdate();
        }
        return id;
    }

    private void inserirBagagemCarona(UUID bdoId, UUID entregaPrincipal) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bagagem_carona (id, tenant_id, bdo_id, entrega_principal_id, quilometragem_complementar, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 0, now(), now())
                """)) {
            st.setObject(1, UUID.randomUUID());
            st.setObject(2, TENANT);
            st.setObject(3, bdoId);
            st.setObject(4, entregaPrincipal);
            st.executeUpdate();
        }
    }
}
