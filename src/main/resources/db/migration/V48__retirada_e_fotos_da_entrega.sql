-- Fotos obrigatórias e passo a passo da viagem (ajustes dos testes de 2026-11-20, itens 4 e 5).
--
-- Retirada da bagagem no aeroporto: foto obrigatória, localização registrada (onde o motorista estiver, sem bloquear) e
-- fotos de avaria opcionais. A viagem só inicia depois da retirada da tentativa. Na entrega: foto na porta, foto do
-- BDO assinado (a foto de documento que já existia), quem assinou (lista da empresa) e o nome, e fotos de avaria.

-- 1. Quarta lista no mesmo cadastro: quem pode assinar o recebimento.
ALTER TABLE motivo_ocorrencia DROP CONSTRAINT ck_motivo_ocorrencia_finalidade;
ALTER TABLE motivo_ocorrencia ADD CONSTRAINT ck_motivo_ocorrencia_finalidade
    CHECK (finalidade IN ('OCORRENCIA', 'DISPENSA', 'CANCELAMENTO', 'RECEBEDOR'));

INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento, finalidade)
SELECT gen_random_uuid(), t.id, padrao.descricao, true, false, 'RECEBEDOR'
FROM tenant t
CROSS JOIN (VALUES ('Titular'), ('Familiar'), ('Porteiro ou portaria'), ('Vizinho'), ('Outro')) AS padrao (descricao)
WHERE NOT EXISTS (SELECT 1 FROM motivo_ocorrencia m WHERE m.tenant_id = t.id AND m.finalidade = 'RECEBEDOR');

-- 2. Retirada da bagagem - uma por tentativa: vinculada à viagem quando ela inicia (trajeto_id), nunca editada.
CREATE TABLE retirada_bagagem (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    entrega_id          UUID NOT NULL REFERENCES entrega (id),
    motorista_id        UUID NOT NULL,
    -- Nulo até a viagem iniciar: a retirada "pendente" é a do motorista atual ainda sem viagem.
    trajeto_id          UUID UNIQUE REFERENCES trajeto (id),
    -- Onde o motorista estava (só registra, não bloqueia); nulo se o aparelho não deu a posição.
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    observacao_avaria   TEXT,
    data_hora           TIMESTAMPTZ NOT NULL,
    registrado_offline  BOOLEAN NOT NULL,
    id_local            VARCHAR(100),
    -- A entrega foi dispensada ou cancelada antes da viagem: a bagagem voltou e esta retirada nao vale mais.
    descartada_em       TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ NOT NULL,
    atualizado_em       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_retirada_coordenadas CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX idx_retirada_bagagem_tenant_id ON retirada_bagagem (tenant_id);
CREATE INDEX idx_retirada_bagagem_entrega_id ON retirada_bagagem (entrega_id);
-- No maximo uma retirada pendente por motorista e entrega (dois toques ou reenvio simultaneo nao duplicam).
CREATE UNIQUE INDEX uk_retirada_bagagem_pendente ON retirada_bagagem (entrega_id, motorista_id)
    WHERE trajeto_id IS NULL AND descartada_em IS NULL;
CREATE UNIQUE INDEX uk_retirada_bagagem_id_local ON retirada_bagagem (entrega_id, id_local) WHERE id_local IS NOT NULL;

ALTER TABLE retirada_bagagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE retirada_bagagem FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON retirada_bagagem
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 3. Comprovante: quem assinou (da lista) e o nome; observação das avarias. Nulos nos comprovantes antigos.
--    identificacao_recebedor continua preenchida ("Tipo - Nome") para tudo que já a lê.
ALTER TABLE comprovante_entrega ADD COLUMN tipo_recebedor_id UUID REFERENCES motivo_ocorrencia (id);
ALTER TABLE comprovante_entrega ADD COLUMN nome_recebedor VARCHAR(200);
ALTER TABLE comprovante_entrega ADD COLUMN observacao_avaria TEXT;

-- 4. Fotos da entrega além da do BDO assinado (que continua em comprovante_entrega.foto_documento_url).
CREATE TABLE foto_entrega (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    entrega_id      UUID NOT NULL REFERENCES entrega (id),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('RETIRADA', 'PORTA', 'AVARIA_RETIRADA', 'AVARIA_ENTREGA')),
    retirada_id     UUID REFERENCES retirada_bagagem (id),
    comprovante_id  UUID REFERENCES comprovante_entrega (id),
    caminho         VARCHAR(500) NOT NULL,
    data_hora       TIMESTAMPTZ NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    atualizado_em   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_foto_entrega_origem CHECK (
        (tipo IN ('RETIRADA', 'AVARIA_RETIRADA') AND retirada_id IS NOT NULL AND comprovante_id IS NULL)
        OR (tipo IN ('PORTA', 'AVARIA_ENTREGA') AND comprovante_id IS NOT NULL AND retirada_id IS NULL))
);

CREATE INDEX idx_foto_entrega_tenant_id ON foto_entrega (tenant_id);
CREATE INDEX idx_foto_entrega_entrega_id ON foto_entrega (entrega_id);

ALTER TABLE foto_entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE foto_entrega FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON foto_entrega
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 5. A retirada também funciona sem internet (HU08).
ALTER TABLE evento_sincronizado DROP CONSTRAINT evento_sincronizado_tipo_evento_check;
ALTER TABLE evento_sincronizado ADD CONSTRAINT evento_sincronizado_tipo_evento_check
    CHECK (tipo_evento IN (
        'BDO_CAPTURADO', 'ENTREGA_AUTOATRIBUIDA', 'ROTA_ESCOLHIDA', 'VIAGEM_INICIADA', 'BAGAGEM_CARONA_VINCULADA',
        'COMPROVANTE_REGISTRADO', 'OCORRENCIA_REGISTRADA', 'RETIRADA_REGISTRADA'));
