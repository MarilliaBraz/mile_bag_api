package com.marillia.milebag.business.cancelamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * V47 contra PostgreSQL real: a entrega CANCELADA e aceita, tira o BDO dos disponiveis e do volume do plano, e os
 * triggers do encaminhamento da companhia aerea (V45) passam a tratar o cancelamento e a carona solta. Como
 * superusuario do container - a semantica do SQL, nao a RLS (TenantIsolationTest).
 */
@Testcontainers
class CancelamentoBdoBancoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final UUID EMPRESA = UUID.randomUUID();
    private static final UUID COMPANHIA = UUID.randomUUID();

    @BeforeAll
    static void migrar() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection conexao = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            inserirTenant(conexao, EMPRESA, "EMPRESA_ENTREGA", null);
            inserirTenant(conexao, COMPANHIA, "COMPANHIA_AEREA", "ZZ");
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
    void bdoCanceladoSaiDosDisponiveis() throws Exception {
        UUID cancelado = inserirBdo();
        inserirEntrega(cancelado, null, "CANCELADA");
        UUID livre = inserirBdo();

        assertThat(disponiveis()).contains(livre).doesNotContain(cancelado);
    }

    @Test
    void bdoCanceladoNaoContaNoVolumeDoPlano() throws Exception {
        Instant inicio = Instant.now().minus(1, ChronoUnit.HOURS);
        long antes = volumeDoPlano(inicio);
        UUID cancelado = inserirBdo();
        inserirCancelamento(cancelado, inserirEntrega(cancelado, null, "CANCELADA"));
        inserirBdo();

        assertThat(volumeDoPlano(inicio)).isEqualTo(antes + 1);
    }

    @Test
    void cancelarOBdoEncerraOEncaminhamentoDaCompanhiaComoCancelado() throws Exception {
        UUID bdo = inserirBdo();
        UUID encaminhamento = inserirEncaminhamento(bdo);
        UUID entrega = inserirEntrega(bdo, UUID.randomUUID(), "ATRIBUIDA");
        assertThat(etapa(encaminhamento)).isEqualTo("MOTORISTA_DESIGNADO");

        executar("UPDATE entrega SET status = 'CANCELADA', motorista_responsavel = NULL WHERE id = ?", entrega);

        assertThat(etapa(encaminhamento)).isEqualTo("CANCELADO");
        assertThat(canceladoEm(encaminhamento)).isNotNull();
    }

    @Test
    void caronaDaEntregaCanceladaVoltaAAguardarMotorista() throws Exception {
        UUID principal = inserirEntrega(inserirBdo(), UUID.randomUUID(), "EM_ANDAMENTO");
        UUID bdoCarona = inserirBdo();
        UUID encaminhamentoCarona = inserirEncaminhamento(bdoCarona);
        inserirBagagemCarona(bdoCarona, principal);
        assertThat(etapa(encaminhamentoCarona)).isEqualTo("A_CAMINHO");

        executar("UPDATE entrega SET status = 'CANCELADA', motorista_responsavel = NULL WHERE id = ?", principal);
        assertThat(etapa(encaminhamentoCarona)).isEqualTo("AGUARDANDO_MOTORISTA");
        executar("DELETE FROM bagagem_carona WHERE bdo_id = ?", bdoCarona);

        assertThat(etapa(encaminhamentoCarona)).isEqualTo("AGUARDANDO_MOTORISTA");
        assertThat(canceladoEm(encaminhamentoCarona)).isNull();
    }

    @Test
    void caronaDesvinculadaDeixaDeFicarPresaNaEtapaDaPrincipal() throws Exception {
        UUID principal = inserirEntrega(inserirBdo(), UUID.randomUUID(), "EM_ANDAMENTO");
        UUID bdoCarona = inserirBdo();
        UUID encaminhamentoCarona = inserirEncaminhamento(bdoCarona);
        inserirBagagemCarona(bdoCarona, principal);

        executar("DELETE FROM bagagem_carona WHERE bdo_id = ?", bdoCarona);

        assertThat(etapa(encaminhamentoCarona)).isEqualTo("AGUARDANDO_MOTORISTA");
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

    private long volumeDoPlano(Instant inicio) throws Exception {
        String sql = BdoRepository.CONSULTA_VOLUME_DO_PLANO.replace(":inicio", "?").replace(":fim", "?");
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setTimestamp(1, Timestamp.from(inicio));
            st.setTimestamp(2, Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)));
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String etapa(UUID encaminhamento) throws Exception {
        return coluna("SELECT etapa FROM encaminhamento_entrega WHERE id = ?", encaminhamento);
    }

    private String canceladoEm(UUID encaminhamento) throws Exception {
        return coluna("SELECT cancelado_em::text FROM encaminhamento_entrega WHERE id = ?", encaminhamento);
    }

    private String coluna(String sql, UUID id) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, id);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void executar(String sql, UUID id) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, id);
            st.executeUpdate();
        }
    }

    private UUID inserirBdo() throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bdo (id, tenant_id, origem, status_conferencia, data_captura, criado_em, atualizado_em)
                VALUES (?, ?, 'EMAIL_AUTOMATICO', 'CONFIRMADO', now(), now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, EMPRESA);
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
            st.setObject(2, EMPRESA);
            st.setObject(3, bdoId);
            st.setObject(4, motorista);
            st.setString(5, status);
            st.executeUpdate();
        }
        return id;
    }

    private void inserirCancelamento(UUID bdoId, UUID entregaId) throws Exception {
        UUID motivo = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento, finalidade)
                VALUES (?, ?, 'Enviado por engano', true, false, 'CANCELAMENTO')
                """)) {
            st.setObject(1, motivo);
            st.setObject(2, EMPRESA);
            st.executeUpdate();
        }
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO cancelamento_bdo (id, tenant_id, bdo_id, entrega_id, motivo_ocorrencia_id, cancelado_por, em_rota,
                                              data_hora, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, false, now(), now(), now())
                """)) {
            st.setObject(1, UUID.randomUUID());
            st.setObject(2, EMPRESA);
            st.setObject(3, bdoId);
            st.setObject(4, entregaId);
            st.setObject(5, motivo);
            st.setObject(6, UUID.randomUUID());
            st.executeUpdate();
        }
    }

    private void inserirBagagemCarona(UUID bdoId, UUID entregaPrincipal) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bagagem_carona (id, tenant_id, bdo_id, entrega_principal_id, quilometragem_complementar, criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, 0, now(), now())
                """)) {
            st.setObject(1, UUID.randomUUID());
            st.setObject(2, EMPRESA);
            st.setObject(3, bdoId);
            st.setObject(4, entregaPrincipal);
            st.executeUpdate();
        }
    }

    private UUID inserirEncaminhamento(UUID bdoId) throws Exception {
        UUID bagagem = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bagagem_encontrada (id, tenant_id, tipo, situacao, foto, origem_leitura, origem_localizacao, criado_por,
                                                criado_em, atualizado_em)
                VALUES (?, ?, 'SEM_ETIQUETA', 'RASCUNHO', 'foto.jpg', 'NENHUMA', 'NENHUMA', ?, now(), now())
                """)) {
            st.setObject(1, bagagem);
            st.setObject(2, COMPANHIA);
            st.setObject(3, UUID.randomUUID());
            st.executeUpdate();
        }
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO encaminhamento_entrega (id, bagagem_id, companhia_id, empresa_id, bdo_id, numero_processo, passageiro_nome,
                                                    endereco, etapa, encaminhado_por, encaminhado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, 'GYNZZ12345', 'Passageiro Teste', 'Rua Teste, 1', 'AGUARDANDO_MOTORISTA', ?, now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, bagagem);
            st.setObject(3, COMPANHIA);
            st.setObject(4, EMPRESA);
            st.setObject(5, bdoId);
            st.setObject(6, UUID.randomUUID());
            st.executeUpdate();
        }
        return id;
    }

    private static void inserirTenant(Connection conexao, UUID id, String tipo, String codigoIata) throws Exception {
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO tenant (id, identificador_empresa, nome, status, criado_em, tipo, codigo_iata)
                VALUES (?, ?, ?, 'ATIVO', now(), ?, ?)
                """)) {
            st.setObject(1, id);
            st.setString(2, id.toString().substring(0, 8));
            st.setString(3, tipo);
            st.setString(4, tipo);
            st.setString(5, codigoIata);
            st.executeUpdate();
        }
    }
}
