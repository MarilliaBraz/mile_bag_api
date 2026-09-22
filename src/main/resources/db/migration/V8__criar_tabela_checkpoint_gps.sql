-- HU04: coordenadas e horario capturados num momento especifico da
-- execucao (partida, marcos de HU19/HU20, comprovante/ocorrencia de
-- HU06/HU07) - append-only, nunca editado ou apagado.
CREATE TABLE checkpoint_gps (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    trajeto_id  UUID NOT NULL REFERENCES trajeto (id),
    tipo        VARCHAR(20) NOT NULL
                    CHECK (tipo IN ('PARTIDA', 'A_CAMINHO', 'CHEGADA', 'COMPROVANTE', 'OCORRENCIA')),
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    data_hora   TIMESTAMPTZ NOT NULL,
    ausente     BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em   TIMESTAMPTZ NOT NULL,

    -- Edge Case do spec.md: sem GPS, o checkpoint fica registrado como ausente
    -- em vez de bloquear o fluxo - nunca ambos os estados ao mesmo tempo.
    CONSTRAINT coordenadas_obrigatorias_quando_nao_ausente
        CHECK (ausente = TRUE OR (lat IS NOT NULL AND lng IS NOT NULL))
);

CREATE INDEX idx_checkpoint_gps_tenant_id ON checkpoint_gps (tenant_id);
CREATE INDEX idx_checkpoint_gps_trajeto_id ON checkpoint_gps (trajeto_id);

ALTER TABLE checkpoint_gps ENABLE ROW LEVEL SECURITY;
ALTER TABLE checkpoint_gps FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON checkpoint_gps
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
