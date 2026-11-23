-- Correcao dos dados do passageiro de um BDO pela empresa (nome, endereco, telefone e e-mail), ate a entrega ser
-- concluida. Sempre com motivo; cada correcao guarda o antes e o depois e aparece no historico da mala. Nunca editada.
CREATE TABLE alteracao_dados_bdo (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    -- Sai junto com o BDO: o encaminhamento cancelado pela companhia apaga o BDO que ninguem assumiu (dado pessoal).
    bdo_id             UUID NOT NULL REFERENCES bdo (id) ON DELETE CASCADE,
    nome_anterior      VARCHAR(200),
    nome_novo          VARCHAR(200),
    endereco_anterior  VARCHAR(500),
    endereco_novo      VARCHAR(500),
    telefone_anterior  VARCHAR(30),
    telefone_novo      VARCHAR(30),
    email_anterior     VARCHAR(200),
    email_novo         VARCHAR(200),
    motivo             TEXT NOT NULL,
    alterado_por       UUID NOT NULL,
    data_hora          TIMESTAMPTZ NOT NULL,
    criado_em          TIMESTAMPTZ NOT NULL,
    atualizado_em      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_alteracao_dados_bdo_tenant_id ON alteracao_dados_bdo (tenant_id);
CREATE INDEX idx_alteracao_dados_bdo_bdo_id ON alteracao_dados_bdo (bdo_id);
ALTER TABLE alteracao_dados_bdo ENABLE ROW LEVEL SECURITY;
ALTER TABLE alteracao_dados_bdo FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON alteracao_dados_bdo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
