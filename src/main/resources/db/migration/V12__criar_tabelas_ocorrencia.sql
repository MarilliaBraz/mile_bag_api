-- HU07: Registrar ocorrencia de nao entrega.
-- Ver mile_bag_documentacao/specs/HU07-registrar-ocorrencia-nao-entrega/data-model.md
--
-- Catalogo por tenant (CRUD completo pertence a HU11 - esta HU so consome).
CREATE TABLE motivo_ocorrencia (
    id         UUID PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    nome       VARCHAR(200) NOT NULL,
    ativo      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_motivo_ocorrencia_tenant_id ON motivo_ocorrencia (tenant_id);

ALTER TABLE motivo_ocorrencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE motivo_ocorrencia FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON motivo_ocorrencia
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Historico de tentativas malsucedidas de uma entrega - append-only, nunca
-- editado ou apagado (FR-004), mesmo padrao de calculo_rota/checkpoint_gps.
CREATE TABLE ocorrencia (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    entrega_id            UUID NOT NULL REFERENCES entrega (id),
    motivo_ocorrencia_id  UUID NOT NULL REFERENCES motivo_ocorrencia (id),
    descricao_adicional   TEXT,
    data_hora             TIMESTAMPTZ NOT NULL,
    registrado_offline    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Chave de idempotencia gerada no dispositivo (mesmo papel de HU06).
    id_local              VARCHAR(100),
    criado_em             TIMESTAMPTZ NOT NULL,
    atualizado_em         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ocorrencia_tenant_id ON ocorrencia (tenant_id);
CREATE INDEX idx_ocorrencia_entrega_id ON ocorrencia (entrega_id);

ALTER TABLE ocorrencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE ocorrencia FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON ocorrencia
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
