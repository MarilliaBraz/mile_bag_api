-- HU05: Vincular bagagem carona a entrega.
-- Ver mile_bag_documentacao/specs/HU05-vincular-bagagem-carona/data-model.md
CREATE TABLE bagagem_carona (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    bdo_id                      UUID NOT NULL REFERENCES bdo (id),
    entrega_principal_id        UUID NOT NULL REFERENCES entrega (id),
    quilometragem_complementar  NUMERIC(10, 3) NOT NULL,
    criado_em                   TIMESTAMPTZ NOT NULL,
    atualizado_em                TIMESTAMPTZ NOT NULL,

    -- FR-003: um BDO so pode originar uma bagagem carona.
    CONSTRAINT uk_bagagem_carona_bdo_id UNIQUE (bdo_id)
);

CREATE INDEX idx_bagagem_carona_tenant_id ON bagagem_carona (tenant_id);
CREATE INDEX idx_bagagem_carona_entrega_principal_id ON bagagem_carona (entrega_principal_id);

ALTER TABLE bagagem_carona ENABLE ROW LEVEL SECURITY;
ALTER TABLE bagagem_carona FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON bagagem_carona
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
