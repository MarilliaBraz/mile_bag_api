-- HU09: Configurar tabelas de tarifa.
-- Ver mile_bag_documentacao/specs/HU09-configurar-tabelas-tarifa/data-model.md
CREATE TABLE tabela_tarifa (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    escopo         VARCHAR(20) NOT NULL
                       CHECK (escopo IN ('PADRAO', 'MOTORISTA', 'REGIAO')),
    motorista_id   UUID,
    regiao         VARCHAR(200),
    valor_fixo     NUMERIC(10, 2),
    valor_por_km   NUMERIC(10, 3),
    vigente_desde  DATE NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    -- FR-005: ao menos um valor definido.
    CONSTRAINT valor_obrigatorio
        CHECK (valor_fixo IS NOT NULL OR valor_por_km IS NOT NULL),
    -- data-model.md: motoristaId/regiao obrigatorios exatamente no escopo correspondente.
    CONSTRAINT motorista_id_condicional
        CHECK ((escopo = 'MOTORISTA') = (motorista_id IS NOT NULL)),
    CONSTRAINT regiao_condicional
        CHECK ((escopo = 'REGIAO') = (regiao IS NOT NULL))
);

CREATE INDEX idx_tabela_tarifa_tenant_id ON tabela_tarifa (tenant_id);

-- No maximo uma tarifa vigente por escopo/motorista/regiao (data-model.md).
CREATE UNIQUE INDEX uk_tabela_tarifa_padrao ON tabela_tarifa (tenant_id) WHERE escopo = 'PADRAO';
CREATE UNIQUE INDEX uk_tabela_tarifa_motorista ON tabela_tarifa (tenant_id, motorista_id) WHERE escopo = 'MOTORISTA';
CREATE UNIQUE INDEX uk_tabela_tarifa_regiao ON tabela_tarifa (tenant_id, regiao) WHERE escopo = 'REGIAO';

ALTER TABLE tabela_tarifa ENABLE ROW LEVEL SECURITY;
ALTER TABLE tabela_tarifa FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON tabela_tarifa
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
