-- HU17: Conferir e corrigir meu relatorio quinzenal.
-- Ver mile_bag_documentacao/specs/HU17-conferir-corrigir-relatorio-quinzenal/data-model.md
--
-- Tabela somente-insercao (RD13): nenhuma correcao de motorista atualiza ou
-- apaga um registro ja existente, nem os campos finais consolidados de
-- Trajeto (HU04) - cada correcao e um novo AjusteViagem.
CREATE TABLE ajuste_viagem (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    viagem_id         UUID NOT NULL REFERENCES trajeto (id),
    campo             VARCHAR(20) NOT NULL
                          CHECK (campo IN ('KM_IDA', 'KM_VOLTA', 'TARIFA_APLICADA', 'VALOR_PEDAGIO')),
    valor_anterior    TEXT NOT NULL,
    valor_novo        TEXT NOT NULL,
    justificativa     TEXT NOT NULL CHECK (btrim(justificativa) <> ''),
    motorista_id      UUID NOT NULL,
    criado_em         TIMESTAMPTZ NOT NULL,
    atualizado_em     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ajuste_viagem_tenant_id ON ajuste_viagem (tenant_id);

-- "Valor vigente" de um (viagem_id, campo) = valor_novo do registro mais
-- recente desse par (data-model.md) - consulta feita a cada resolucao de
-- relatorio/fechamento, entao o indice evita degradar conforme o historico
-- cresce (tasks.md T014).
CREATE INDEX idx_ajuste_viagem_viagem_campo_criado_em ON ajuste_viagem (viagem_id, campo, criado_em DESC);

ALTER TABLE ajuste_viagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE ajuste_viagem FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON ajuste_viagem
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
