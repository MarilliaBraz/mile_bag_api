-- HU10: Cadastrar pracas de pedagio.
-- Ver mile_bag_documentacao/specs/HU10-cadastrar-pracas-pedagio/data-model.md
CREATE TABLE praca_pedagio (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    nome           VARCHAR(200) NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_praca_pedagio_tenant_id ON praca_pedagio (tenant_id);

ALTER TABLE praca_pedagio ENABLE ROW LEVEL SECURITY;
ALTER TABLE praca_pedagio FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON praca_pedagio
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Historico de valores, nunca sobrescrito (FR-002/research.md). Carrega o
-- proprio tenant_id (em vez de depender so do join com praca_pedagio para
-- isolamento, como a tasks.md desta HU sugeria) - mesma defesa em
-- profundidade ja aplicada a calculo_rota/checkpoint_gps (HU04): uma
-- consulta direta a esta tabela, sem passar por praca_pedagio, continua
-- protegida por Row Level Security.
CREATE TABLE praca_pedagio_valor (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    praca_pedagio_id  UUID NOT NULL REFERENCES praca_pedagio (id),
    valor             NUMERIC(10, 2) NOT NULL,
    vigente_desde     DATE NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL,
    atualizado_em     TIMESTAMPTZ NOT NULL,

    -- FR-005: nunca duas vigencias iguais para a mesma praca (evita
    -- ambiguidade na resolucao do valor vigente numa data).
    CONSTRAINT uk_praca_pedagio_valor_vigencia UNIQUE (praca_pedagio_id, vigente_desde)
);

CREATE INDEX idx_praca_pedagio_valor_tenant_id ON praca_pedagio_valor (tenant_id);
-- Acelera a resolucao do valor vigente numa data (T003 desta HU).
CREATE INDEX idx_praca_pedagio_valor_praca_vigencia ON praca_pedagio_valor (praca_pedagio_id, vigente_desde DESC);

ALTER TABLE praca_pedagio_valor ENABLE ROW LEVEL SECURITY;
ALTER TABLE praca_pedagio_valor FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON praca_pedagio_valor
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
