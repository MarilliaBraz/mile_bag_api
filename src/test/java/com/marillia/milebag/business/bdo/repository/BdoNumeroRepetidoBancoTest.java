package com.marillia.milebag.business.bdo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/**
 * V49 contra PostgreSQL real: a conferencia de numero repetido acha o BDO gravado em minusculas ou com espacos (os
 * antigos nao eram normalizados) e nao confunde numeros diferentes. Como superusuario do container - a semantica do
 * SQL, nao a RLS.
 */
@Testcontainers
class BdoNumeroRepetidoBancoTest {

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
                        VALUES (?, ?, 'Empresa', 'ATIVO', now(), 'EMPRESA_ENTREGA')
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
    void achaONumeroGravadoEmMinusculasOuComEspacos() throws Exception {
        UUID antigoMinusculo = inserirBdo("gynla24713");
        UUID antigoComEspaco = inserirBdo(" GYNLA 24713 ");
        UUID normalizado = inserirBdo("GYNLA24713");
        UUID outro = inserirBdo("GYNLA24714");

        assertThat(porNumero("GYNLA24713")).containsExactlyInAnyOrder(antigoMinusculo, antigoComEspaco, normalizado)
                .doesNotContain(outro);
    }

    @Test
    void numeroQueNaoExisteNaoAchaNada() throws Exception {
        inserirBdo("GRUIB30193");

        assertThat(porNumero("GRUIB30199")).isEmpty();
    }

    private List<UUID> porNumero(String numero) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement st = conexao.prepareStatement(BdoRepository.CONSULTA_POR_NUMERO.replace(":numero", "?"))) {
            st.setString(1, numero);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject("id", UUID.class));
                }
            }
        }
        return ids;
    }

    private UUID inserirBdo(String numero) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement st = conexao.prepareStatement("""
                INSERT INTO bdo (id, tenant_id, origem, numero, status_conferencia, data_captura, criado_em, atualizado_em)
                VALUES (?, ?, 'EMAIL_AUTOMATICO', ?, 'CONFIRMADO', now(), now(), now())
                """)) {
            st.setObject(1, id);
            st.setObject(2, EMPRESA);
            st.setString(3, numero);
            st.executeUpdate();
        }
        return id;
    }
}
