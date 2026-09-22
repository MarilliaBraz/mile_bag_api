-- HU02: Autoatribuir a entrega.
-- Ver mile_bag_documentacao/specs/HU02-autoatribuir-entrega/data-model.md

CREATE TABLE entrega (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    bdo_id                 UUID NOT NULL REFERENCES bdo (id),
    motorista_responsavel  UUID,
    status                 VARCHAR(30) NOT NULL DEFAULT 'AGUARDANDO_ATRIBUICAO'
                               CHECK (status IN (
                                   'AGUARDANDO_ATRIBUICAO', 'ATRIBUIDA', 'EM_ANDAMENTO',
                                   'ENCERRADA_COM_SUCESSO', 'REABERTA')),
    criado_em              TIMESTAMPTZ NOT NULL,
    atualizado_em          TIMESTAMPTZ NOT NULL,

    -- 1:1 com o BDO de origem (data-model.md).
    CONSTRAINT uk_entrega_bdo_id UNIQUE (bdo_id)
);

CREATE INDEX idx_entrega_tenant_id ON entrega (tenant_id);
CREATE INDEX idx_entrega_motorista_status ON entrega (tenant_id, motorista_responsavel, status);

-- Isolamento multi-tenant (RNF01), mesma politica de V1.
ALTER TABLE entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE entrega FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON entrega
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
