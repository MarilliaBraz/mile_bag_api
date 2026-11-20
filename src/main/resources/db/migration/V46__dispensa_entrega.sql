-- Dispensa da entrega pelo motorista (ajustes dos testes de 2026-11-20).
--
-- O motorista que assumiu uma entrega pode devolve-la para os outros motoristas. Sem motivo se ainda nao retirou a
-- bagagem na primeira tentativa; com motivo (da lista da empresa) se ja retirou - viagem iniciada - ou se a entrega
-- e uma nova tentativa. Toda dispensa fica registrada, para a rastreabilidade da mala.

-- 1. O catalogo de motivos passa a servir as duas finalidades: ocorrencia (nao entrega) e dispensa. A empresa
--    gerencia as duas listas no mesmo cadastro; reagendamento so existe em motivo de ocorrencia.
ALTER TABLE motivo_ocorrencia ADD COLUMN finalidade VARCHAR(20) NOT NULL DEFAULT 'OCORRENCIA';
ALTER TABLE motivo_ocorrencia ADD CONSTRAINT ck_motivo_ocorrencia_finalidade
    CHECK (finalidade IN ('OCORRENCIA', 'DISPENSA'));
ALTER TABLE motivo_ocorrencia ADD CONSTRAINT ck_motivo_ocorrencia_reagendamento_so_ocorrencia
    CHECK (NOT reagendamento OR finalidade = 'OCORRENCIA');

-- Motivos de dispensa iniciais das empresas ja existentes (as novas recebem os mesmos no cadastro -
-- MotivoOcorrenciaService.criarPadroesSeAusentes).
INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento, finalidade)
SELECT gen_random_uuid(), t.id, padrao.descricao, true, false, 'DISPENSA'
FROM tenant t
CROSS JOIN (VALUES
    ('Problema no veículo'),
    ('Imprevisto pessoal'),
    ('Indisponibilidade de horário'),
    ('Outro motivo')
) AS padrao (descricao)
WHERE NOT EXISTS (SELECT 1 FROM motivo_ocorrencia m WHERE m.tenant_id = t.id AND m.finalidade = 'DISPENSA');

-- 2. Registro de cada dispensa - append-only, nunca editado ou apagado (mesmo padrao de ocorrencia).
CREATE TABLE dispensa_entrega (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    entrega_id            UUID NOT NULL REFERENCES entrega (id),
    motorista_id          UUID NOT NULL,
    -- Nulo so quando o motivo nao era exigido (primeira tentativa, bagagem ainda nao retirada).
    motivo_ocorrencia_id  UUID REFERENCES motivo_ocorrencia (id),
    complemento           TEXT,
    -- Verdadeiro quando o motorista ja tinha retirado a bagagem (viagem iniciada): a viagem e encerrada na dispensa.
    com_bagagem           BOOLEAN NOT NULL,
    data_hora             TIMESTAMPTZ NOT NULL,
    criado_em             TIMESTAMPTZ NOT NULL,
    atualizado_em         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_dispensa_com_bagagem_exige_motivo CHECK (NOT com_bagagem OR motivo_ocorrencia_id IS NOT NULL)
);

CREATE INDEX idx_dispensa_entrega_tenant_id ON dispensa_entrega (tenant_id);
CREATE INDEX idx_dispensa_entrega_entrega_id ON dispensa_entrega (entrega_id);

ALTER TABLE dispensa_entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE dispensa_entrega FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON dispensa_entrega
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 3. A viagem encerrada por uma dispensa (motorista ja em rota) e, como a encerrada por ocorrencia, uma tentativa
--    sem sucesso: fica no historico e e paga a quem dirigiu; a proxima tentativa comeca uma viagem nova.
ALTER TABLE trajeto ADD COLUMN dispensa_id UUID REFERENCES dispensa_entrega (id);
CREATE INDEX idx_trajeto_encerrado_por_dispensa ON trajeto (tenant_id, encerrado_em) WHERE dispensa_id IS NOT NULL;
