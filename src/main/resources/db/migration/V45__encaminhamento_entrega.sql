-- HU37: encaminhar a bagagem encontrada para entrega (RF30, RD23).
-- Ver mile_bag_documentacao/specs/HU37-encaminhar-entrega/spec.md

-- Empresas de entrega parceiras de cada companhia, definidas pela plataforma. Sem RLS: e um cadastro da plataforma,
-- e a companhia so le as proprias parceiras pelo servico, com o tenant do token.
CREATE TABLE parceria_entrega (
    companhia_id    UUID NOT NULL REFERENCES tenant (id),
    empresa_id      UUID NOT NULL REFERENCES tenant (id),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (companhia_id, empresa_id)
);

-- O BDO que a companhia cria na empresa parceira.
ALTER TABLE bdo DROP CONSTRAINT bdo_origem_check;
ALTER TABLE bdo ADD CONSTRAINT bdo_origem_check CHECK (origem IN ('FOTO_MANUAL', 'EMAIL_AUTOMATICO', 'COMPANHIA_AEREA'));

-- O encaminhamento liga a bagagem (da companhia) ao BDO (da empresa). Fica SEM RLS porque os dois tenants o tocam:
-- a companhia le a etapa e a empresa, pelos triggers abaixo, a atualiza. Nao guarda o motorista nem nada da rota
-- (RD23); telefone e e-mail do passageiro ficam so no BDO. O servico sempre filtra pela companhia do token.
CREATE TABLE encaminhamento_entrega (
    id                  UUID PRIMARY KEY,
    bagagem_id          UUID NOT NULL REFERENCES bagagem_encontrada (id),
    companhia_id        UUID NOT NULL REFERENCES tenant (id),
    empresa_id          UUID NOT NULL REFERENCES tenant (id),
    -- Nulo depois do cancelamento, que apaga o BDO.
    bdo_id              UUID UNIQUE REFERENCES bdo (id) ON DELETE SET NULL,
    numero_processo     VARCHAR(30) NOT NULL,
    passageiro_nome     VARCHAR(200) NOT NULL,
    endereco            VARCHAR(300) NOT NULL,
    etapa               VARCHAR(30) NOT NULL CHECK (etapa IN (
                            'AGUARDANDO_MOTORISTA', 'MOTORISTA_DESIGNADO', 'A_CAMINHO', 'ENTREGUE', 'NAO_ENTREGUE', 'CANCELADO')),
    encaminhado_por     UUID NOT NULL,
    encaminhado_em      TIMESTAMPTZ NOT NULL,
    cancelado_por       UUID,
    cancelado_em        TIMESTAMPTZ,
    atualizado_em       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_encaminhamento_cancelado CHECK ((etapa = 'CANCELADO') = (cancelado_em IS NOT NULL))
);

-- RD23: um encaminhamento ativo por bagagem.
CREATE UNIQUE INDEX uk_encaminhamento_entrega_ativo ON encaminhamento_entrega (bagagem_id) WHERE etapa <> 'CANCELADO';
CREATE INDEX idx_encaminhamento_entrega_companhia ON encaminhamento_entrega (companhia_id, bagagem_id);

CREATE FUNCTION etapa_encaminhamento(status VARCHAR, motorista UUID) RETURNS VARCHAR AS $$
    SELECT CASE
        WHEN status = 'ENCERRADA_COM_SUCESSO' THEN 'ENTREGUE'
        WHEN status = 'EM_ANDAMENTO' THEN 'A_CAMINHO'
        WHEN status = 'REABERTA' THEN 'NAO_ENTREGUE'
        WHEN motorista IS NOT NULL THEN 'MOTORISTA_DESIGNADO'
        ELSE 'AGUARDANDO_MOTORISTA'
    END;
$$ LANGUAGE sql IMMUTABLE;

-- A etapa segue a entrega do BDO e a das bagagens carona dela (a carona viaja com a entrega principal). Roda com o
-- tenant da empresa: a leitura de bagagem_carona continua sob a RLS dela.
CREATE FUNCTION encaminhamento_entrega_pela_entrega() RETURNS trigger AS $$
BEGIN
    UPDATE encaminhamento_entrega
    SET etapa = etapa_encaminhamento(NEW.status, NEW.motorista_responsavel), atualizado_em = now()
    WHERE etapa <> 'CANCELADO'
      AND (bdo_id = NEW.bdo_id OR bdo_id IN (SELECT bc.bdo_id FROM bagagem_carona bc WHERE bc.entrega_principal_id = NEW.id));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_encaminhamento_entrega_pela_entrega
    AFTER INSERT OR UPDATE OF status, motorista_responsavel ON entrega
    FOR EACH ROW EXECUTE FUNCTION encaminhamento_entrega_pela_entrega();

CREATE FUNCTION encaminhamento_entrega_pela_carona() RETURNS trigger AS $$
BEGIN
    UPDATE encaminhamento_entrega
    SET etapa = (SELECT etapa_encaminhamento(e.status, e.motorista_responsavel) FROM entrega e WHERE e.id = NEW.entrega_principal_id),
        atualizado_em = now()
    WHERE etapa <> 'CANCELADO' AND bdo_id = NEW.bdo_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_encaminhamento_entrega_pela_carona
    AFTER INSERT ON bagagem_carona
    FOR EACH ROW EXECUTE FUNCTION encaminhamento_entrega_pela_carona();
