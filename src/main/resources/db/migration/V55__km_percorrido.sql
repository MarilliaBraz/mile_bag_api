-- Quilometragem efetivamente percorrida (ajustes dos testes de 2026-11-20, item 9).
--
-- O app grava a posicao do motorista a cada 15 s durante a viagem, tambem sem internet, e envia em lotes; cada ponto
-- entra uma vez so (id_local). Com os pontos, a viagem guarda o km percorrido na ida, para auditoria e para o
-- pagamento: na viagem paga por km, se a ida percorrida passar a calculada em mais de 5%, paga a percorrida na ida e na
-- volta (a volta espelha a ida, como no item 7).

CREATE TABLE posicao_viagem (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    trajeto_id       UUID NOT NULL REFERENCES trajeto (id),
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    precisao_metros  DOUBLE PRECISION,
    data_hora        TIMESTAMPTZ NOT NULL,
    id_local         VARCHAR(64) NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL,
    atualizado_em    TIMESTAMPTZ NOT NULL,
    -- Por viagem: o ponto de um motorista nunca bloqueia nem e confundido com o de outro.
    CONSTRAINT uk_posicao_viagem_id_local UNIQUE (trajeto_id, id_local)
);
CREATE INDEX idx_posicao_viagem_tenant_id ON posicao_viagem (tenant_id);
CREATE INDEX idx_posicao_viagem_trajeto_data ON posicao_viagem (trajeto_id, data_hora);
ALTER TABLE posicao_viagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE posicao_viagem FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON posicao_viagem
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Nulo nas viagens sem pontos (todas as anteriores a esta versao): para elas vale so o km calculado, como antes.
ALTER TABLE trajeto ADD COLUMN km_percorrido NUMERIC(10, 3);
