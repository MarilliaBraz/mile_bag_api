-- HU04: um registro por calculo/recalculo de rota durante a execucao da
-- viagem - append-only, nunca editado ou apagado (FR-004).
CREATE TABLE calculo_rota (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    trajeto_id       UUID NOT NULL REFERENCES trajeto (id),
    km_ida           NUMERIC(10, 3) NOT NULL,
    km_volta         NUMERIC(10, 3) NOT NULL,
    pracas_pedagio   JSONB NOT NULL,
    tempo_estimado   BIGINT NOT NULL,
    origem_selecao   VARCHAR(20) NOT NULL
                         CHECK (origem_selecao IN ('SUGERIDA', 'PROPRIA')),
    data_hora        TIMESTAMPTZ NOT NULL,
    eh_rota_inicial  BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em        TIMESTAMPTZ NOT NULL
);

-- T007: no maximo um calculo_rota.eh_rota_inicial = true por trajeto.
CREATE UNIQUE INDEX uk_calculo_rota_inicial_por_trajeto
    ON calculo_rota (trajeto_id) WHERE eh_rota_inicial = TRUE;

CREATE INDEX idx_calculo_rota_tenant_id ON calculo_rota (tenant_id);
CREATE INDEX idx_calculo_rota_trajeto_id ON calculo_rota (trajeto_id);

ALTER TABLE calculo_rota ENABLE ROW LEVEL SECURITY;
ALTER TABLE calculo_rota FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON calculo_rota
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
