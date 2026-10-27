-- HU30: Acionar alerta de emergencia (RF23, RD16).
-- Ver mile_bag_documentacao/specs/HU30-acionar-alerta-emergencia/spec.md
--
-- Nunca apagado (RD16): so muda de situacao uma vez, a partir de ABERTO.
CREATE TABLE alerta_emergencia (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    motorista_id   UUID NOT NULL,
    -- Entrega em andamento no momento do acionamento, quando houver.
    entrega_id     UUID REFERENCES entrega (id),
    -- Opcionais: o alerta nunca deixa de ser registrado por falta de GPS.
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    data_hora      TIMESTAMPTZ NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('ABERTO', 'CANCELADO', 'ATENDIDO')),
    observacao     TEXT,
    encerrado_por  UUID,
    encerrado_em   TIMESTAMPTZ,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_alerta_emergencia_coordenadas CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);

CREATE INDEX idx_alerta_emergencia_tenant_id ON alerta_emergencia (tenant_id);
CREATE INDEX idx_alerta_emergencia_data_hora ON alerta_emergencia (data_hora DESC);
-- RD16: no maximo um alerta ABERTO por motorista - acionar de novo devolve o mesmo.
CREATE UNIQUE INDEX uk_alerta_emergencia_aberto_por_motorista ON alerta_emergencia (motorista_id) WHERE status = 'ABERTO';

ALTER TABLE alerta_emergencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerta_emergencia FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON alerta_emergencia
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
