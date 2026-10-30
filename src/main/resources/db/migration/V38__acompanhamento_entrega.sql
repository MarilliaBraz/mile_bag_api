-- HU31: Acompanhar a bagagem em tempo real (RF24, RN16, RD17).
-- Ver mile_bag_documentacao/specs/HU31-acompanhar-bagagem/spec.md
--
-- link_acompanhamento: NAO segue o padrao tenant-scoped com RLS - mesma
-- decisao de convite_administrador (V23): a pagina publica nao tem tenant no
-- JWT para uma policy aplicar. O codigo so serve para descobrir o tenant e a
-- tentativa; os demais dados sao lidos com RLS sob o TenantContext desse
-- tenant (AcompanhamentoController).
CREATE TABLE link_acompanhamento (
    codigo      UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenant (id),
    -- Um link por tentativa (RD17): a nova tentativa ganha um link novo.
    trajeto_id  UUID NOT NULL UNIQUE REFERENCES trajeto (id),
    entrega_id  UUID NOT NULL REFERENCES entrega (id),
    criado_em   TIMESTAMPTZ NOT NULL
);

-- RN16: so a ultima posicao da tentativa em andamento, zerada ao encerrar.
ALTER TABLE trajeto
    ADD COLUMN posicao_latitude      DOUBLE PRECISION,
    ADD COLUMN posicao_longitude     DOUBLE PRECISION,
    ADD COLUMN posicao_registrada_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_trajeto_posicao CHECK (
        (posicao_latitude IS NULL AND posicao_longitude IS NULL AND posicao_registrada_em IS NULL)
        OR (posicao_latitude BETWEEN -90 AND 90 AND posicao_longitude BETWEEN -180 AND 180
            AND posicao_registrada_em IS NOT NULL));

CREATE INDEX idx_trajeto_motorista_em_execucao ON trajeto (motorista_id) WHERE status = 'EM_EXECUCAO';
