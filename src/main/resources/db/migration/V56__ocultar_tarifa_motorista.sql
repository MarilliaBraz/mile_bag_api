-- A empresa decide se o motorista ve a tarifa paga por viagem (ajustes de 2026-11-22).
--
-- No maximo uma linha por empresa. Sem linha, a tarifa aparece (como sempre foi). Oculta, o relatorio do motorista
-- mostra so os km - ou "entrega na cidade base" - e o pedagio; o fechamento da empresa nao muda.
CREATE TABLE exibicao_tarifa_motorista (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    ocultar_tarifa   BOOLEAN NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL,
    atualizado_em    TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_exibicao_tarifa_motorista_tenant_id UNIQUE (tenant_id)
);

ALTER TABLE exibicao_tarifa_motorista ENABLE ROW LEVEL SECURITY;
ALTER TABLE exibicao_tarifa_motorista FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON exibicao_tarifa_motorista
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
