-- HU06: Registrar o comprovante de entrega (POD).
-- Ver mile_bag_documentacao/specs/HU06-registrar-comprovante-entrega/data-model.md
CREATE TABLE comprovante_entrega (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    entrega_id               UUID NOT NULL REFERENCES entrega (id),
    foto_documento_url       VARCHAR(500) NOT NULL,
    identificacao_recebedor  VARCHAR(200) NOT NULL,
    data_hora                TIMESTAMPTZ NOT NULL,
    latitude                 DOUBLE PRECISION NOT NULL,
    longitude                DOUBLE PRECISION NOT NULL,
    registrado_offline       BOOLEAN NOT NULL DEFAULT FALSE,
    -- Chave de idempotencia gerada no dispositivo (contracts/comprovante-entrega-api.md),
    -- usada quando o registro chega via sincronizacao offline (HU08).
    id_local                 VARCHAR(100),
    criado_em                TIMESTAMPTZ NOT NULL,
    atualizado_em             TIMESTAMPTZ NOT NULL,

    -- FR-004: no maximo um comprovante por entrega.
    CONSTRAINT uk_comprovante_entrega_entrega_id UNIQUE (entrega_id)
);

CREATE INDEX idx_comprovante_entrega_tenant_id ON comprovante_entrega (tenant_id);

ALTER TABLE comprovante_entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE comprovante_entrega FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON comprovante_entrega
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
