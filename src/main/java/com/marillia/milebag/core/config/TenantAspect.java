package com.marillia.milebag.core.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.marillia.milebag.core.config.security.TenantContext;

/**
 * Aplica o tenant da requisicao atual ({@link TenantContext}) na conexao
 * JDBC de toda chamada a um {@code Repository} do Spring Data, antes dela
 * executar - e o que faz a politica de Row Level Security do PostgreSQL
 * (migracao V1) realmente filtrar por tenant.
 *
 * <p>Fica em {@code core} porque e comportamento generico, sem regra de
 * negocio, reaproveitado por todo dominio (CONVENTIONS.md secao 2.2). Cada
 * {@code Service} nao precisa (nem deve) chamar isso manualmente: o
 * isolamento entre tenants nao pode depender de alguem lembrar de um passo
 * extra (RNF01) - por isso e automatico, via AOP, em vez de um metodo em
 * {@code BaseService} que cada implementacao teria que lembrar de invocar.</p>
 *
 * <p>{@code set_config(..., true)} (nao o comando {@code SET LOCAL} direto)
 * porque so uma funcao SQL aceita parametro bindado via
 * {@link PreparedStatement} com seguranca; o terceiro argumento {@code true}
 * equivale a {@code LOCAL} - o valor vale so ate o fim da transacao atual,
 * nunca vaza para a proxima requisicao que reusar essa conexao do pool.</p>
 */
@Aspect
@Component
public class TenantAspect {

    private final DataSource dataSource;

    public TenantAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void aplicarTenantNaConexaoAtual() throws SQLException {
        UUID tenantId = TenantContext.get();
        if (tenantId == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
