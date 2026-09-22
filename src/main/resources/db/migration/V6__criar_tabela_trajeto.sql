-- HU04: Iniciar a viagem e registrar o trajeto real.
-- Ver mile_bag_documentacao/specs/HU04-iniciar-viagem-rota-congelada/data-model.md
CREATE TABLE trajeto (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    entrega_id                 UUID NOT NULL REFERENCES entrega (id),
    status                     VARCHAR(20) NOT NULL DEFAULT 'EM_EXECUCAO'
                                   CHECK (status IN ('EM_EXECUCAO', 'ENCERRADO')),
    data_inicio                TIMESTAMPTZ NOT NULL,
    -- Nulos ate a consolidacao no encerramento da Entrega (HU06/HU07).
    km_ida_final               NUMERIC(10, 3),
    km_volta_final             NUMERIC(10, 3),
    pracas_pedagio_final       JSONB,
    tempo_final                BIGINT,
    justificativa_divergencia  TEXT,
    criado_em                  TIMESTAMPTZ NOT NULL,
    atualizado_em              TIMESTAMPTZ NOT NULL,

    -- FR-005: no maximo um trajeto por entrega.
    CONSTRAINT uk_trajeto_entrega_id UNIQUE (entrega_id)
);

CREATE INDEX idx_trajeto_tenant_id ON trajeto (tenant_id);

ALTER TABLE trajeto ENABLE ROW LEVEL SECURITY;
ALTER TABLE trajeto FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON trajeto
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
