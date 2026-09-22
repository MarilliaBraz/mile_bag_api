-- HU11: Parametrizar configuracoes do tenant.
-- Ver mile_bag_documentacao/specs/HU11-parametrizar-configuracoes-tenant/data-model.md
--
-- campo_baixa_config: catalogo fixo do sistema (enum CampoBaixa no
-- codigo), um registro por campo configuravel por tenant.
CREATE TABLE campo_baixa_config (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    campo          VARCHAR(50) NOT NULL,
    obrigatorio    BOOLEAN NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_campo_baixa_config_tenant_campo UNIQUE (tenant_id, campo)
);

CREATE INDEX idx_campo_baixa_config_tenant_id ON campo_baixa_config (tenant_id);

ALTER TABLE campo_baixa_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE campo_baixa_config FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON campo_baixa_config
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- base_aeroportuaria
CREATE TABLE base_aeroportuaria (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    codigo_iata    VARCHAR(10) NOT NULL,
    nome           VARCHAR(200),
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_base_aeroportuaria_tenant_iata UNIQUE (tenant_id, codigo_iata)
);

CREATE INDEX idx_base_aeroportuaria_tenant_id ON base_aeroportuaria (tenant_id);

ALTER TABLE base_aeroportuaria ENABLE ROW LEVEL SECURITY;
ALTER TABLE base_aeroportuaria FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON base_aeroportuaria
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- companhia_aerea_atendida
CREATE TABLE companhia_aerea_atendida (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    codigo_iata    VARCHAR(10) NOT NULL,
    nome           VARCHAR(200),
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_companhia_aerea_atendida_tenant_iata UNIQUE (tenant_id, codigo_iata)
);

CREATE INDEX idx_companhia_aerea_atendida_tenant_id ON companhia_aerea_atendida (tenant_id);

ALTER TABLE companhia_aerea_atendida ENABLE ROW LEVEL SECURITY;
ALTER TABLE companhia_aerea_atendida FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON companhia_aerea_atendida
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- periodicidade_fechamento_config: historico por vigencia, mesmo padrao de
-- praca_pedagio_valor/tabela_tarifa (HU09/HU10).
CREATE TABLE periodicidade_fechamento_config (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    periodicidade  VARCHAR(20) NOT NULL CHECK (periodicidade IN ('QUINZENAL')),
    vigente_desde  DATE NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_periodicidade_fechamento_vigencia UNIQUE (tenant_id, vigente_desde)
);

CREATE INDEX idx_periodicidade_fechamento_tenant_vigencia ON periodicidade_fechamento_config (tenant_id, vigente_desde DESC);

ALTER TABLE periodicidade_fechamento_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE periodicidade_fechamento_config FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON periodicidade_fechamento_config
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
