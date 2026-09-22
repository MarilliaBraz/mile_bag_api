-- HU12: Gerar o fechamento quinzenal.
-- Ver mile_bag_documentacao/specs/HU12-gerar-fechamento-quinzenal/data-model.md
CREATE TABLE fechamento (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    periodo_inicio  DATE NOT NULL,
    periodo_fim     DATE NOT NULL,
    gerado_em       TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETO', 'COM_PENDENCIAS')),
    criado_em       TIMESTAMPTZ NOT NULL,
    atualizado_em   TIMESTAMPTZ NOT NULL,

    -- FR-005: idempotencia por periodo, garantida no banco (research.md).
    CONSTRAINT uk_fechamento_periodo UNIQUE (tenant_id, periodo_inicio, periodo_fim)
);

CREATE INDEX idx_fechamento_tenant_id ON fechamento (tenant_id);

ALTER TABLE fechamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE fechamento FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON fechamento
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Linha de detalhe por entrega - append-only, mesmo padrao de
-- calculo_rota/checkpoint_gps/ocorrencia. Carrega o proprio tenant_id (nao
-- so isolamento "via join" com fechamento, como a tasks.md desta HU
-- sugeria) - mesma defesa em profundidade ja aplicada em HU04/HU10.
CREATE TABLE item_fechamento (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    fechamento_id            UUID NOT NULL REFERENCES fechamento (id),
    entrega_id               UUID NOT NULL REFERENCES entrega (id),
    motorista_id             UUID NOT NULL,
    valor_pagamento_entrega  NUMERIC(10, 2),
    valor_reembolso_pedagio  NUMERIC(10, 2),
    status_item              VARCHAR(20) NOT NULL CHECK (status_item IN ('CALCULADO', 'PENDENTE')),
    motivo_pendencia         TEXT,
    criado_em                TIMESTAMPTZ NOT NULL,
    atualizado_em            TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_item_fechamento_entrega UNIQUE (fechamento_id, entrega_id)
);

CREATE INDEX idx_item_fechamento_tenant_id ON item_fechamento (tenant_id);
CREATE INDEX idx_item_fechamento_fechamento_id ON item_fechamento (fechamento_id);

ALTER TABLE item_fechamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_fechamento FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON item_fechamento
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
