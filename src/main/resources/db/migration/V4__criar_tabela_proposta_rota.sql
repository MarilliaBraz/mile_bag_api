-- HU03: Calcular e recalcular a rota da entrega.
-- Ver mile_bag_documentacao/specs/HU03-calcular-e-escolher-rota/data-model.md
--
-- Transitoria por natureza: substituida a cada novo calculo enquanto a
-- viagem nao foi iniciada (por isso a chave primaria e o proprio
-- entrega_id, sem historico aqui) - o historico definitivo (CalculoRota)
-- passa a existir em HU04.
CREATE TABLE proposta_rota (
    entrega_id     UUID PRIMARY KEY REFERENCES entrega (id),
    tenant_id      UUID NOT NULL,
    origem         JSONB NOT NULL,
    destino        JSONB NOT NULL,
    alternativas   JSONB NOT NULL,
    tipo_selecao   VARCHAR(20)
                       CHECK (tipo_selecao IN ('SUGERIDA', 'PROPRIA')),
    justificativa  TEXT,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    -- FR-005: justificativa obrigatoria apenas quando a rota escolhida e propria.
    CONSTRAINT justificativa_obrigatoria_para_propria
        CHECK (tipo_selecao IS DISTINCT FROM 'PROPRIA' OR justificativa IS NOT NULL)
);

CREATE INDEX idx_proposta_rota_tenant_id ON proposta_rota (tenant_id);

ALTER TABLE proposta_rota ENABLE ROW LEVEL SECURITY;
ALTER TABLE proposta_rota FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON proposta_rota
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
