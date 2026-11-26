-- Painel de gestao (fase 1): prazo padrao da mala quando o BDO nao traz data prevista nem janela reagendada, e depois
-- de quantas horas sem atualizacao uma mala com o motorista vira excecao. Uma linha por empresa; sem linha = padrao.
CREATE TABLE painel_gestao_config (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    prazo_entrega_horas     INTEGER NOT NULL,
    horas_sem_atualizacao   INTEGER NOT NULL,
    criado_em               TIMESTAMPTZ NOT NULL,
    atualizado_em           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_painel_gestao_config_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_painel_gestao_config_prazo CHECK (prazo_entrega_horas BETWEEN 1 AND 720),
    CONSTRAINT ck_painel_gestao_config_parada CHECK (horas_sem_atualizacao BETWEEN 1 AND 168)
);
ALTER TABLE painel_gestao_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE painel_gestao_config FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON painel_gestao_config
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
