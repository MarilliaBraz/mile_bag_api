-- Voltar para alterar com motivo, para auditoria (ajustes dos testes de 2026-11-20, item 6).
--
-- O motorista corrige uma etapa ja concluida - durante a entrega (vai junto com o comprovante), a retirada antes de
-- iniciar a viagem, ou a entrega finalizada ate 24 h depois - sempre com motivo da lista da empresa. Cada correcao
-- guarda o que havia antes e o que ficou depois; nenhuma foto e apagada, a antiga fica marcada como substituida.

-- 1. Quinta lista no mesmo cadastro: motivos de correcao.
ALTER TABLE motivo_ocorrencia DROP CONSTRAINT ck_motivo_ocorrencia_finalidade;
ALTER TABLE motivo_ocorrencia ADD CONSTRAINT ck_motivo_ocorrencia_finalidade
    CHECK (finalidade IN ('OCORRENCIA', 'DISPENSA', 'CANCELAMENTO', 'RECEBEDOR', 'CORRECAO'));

INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento, finalidade)
SELECT gen_random_uuid(), t.id, padrao.descricao, true, false, 'CORRECAO'
FROM tenant t
CROSS JOIN (VALUES ('Foto errada'), ('Foto ilegível'), ('Dado digitado errado'), ('Recebedor errado'), ('Outro')) AS padrao (descricao)
WHERE NOT EXISTS (SELECT 1 FROM motivo_ocorrencia m WHERE m.tenant_id = t.id AND m.finalidade = 'CORRECAO');

-- 2. Fotos substituidas ficam (auditoria). A do BDO assinado substituida tambem passa a morar aqui - a atual continua
--    em comprovante_entrega.foto_documento_url.
ALTER TABLE foto_entrega ADD COLUMN substituida_em TIMESTAMPTZ;
ALTER TABLE foto_entrega DROP CONSTRAINT foto_entrega_tipo_check;
ALTER TABLE foto_entrega ADD CONSTRAINT foto_entrega_tipo_check
    CHECK (tipo IN ('RETIRADA', 'PORTA', 'AVARIA_RETIRADA', 'AVARIA_ENTREGA', 'BDO_ASSINADO'));
ALTER TABLE foto_entrega DROP CONSTRAINT ck_foto_entrega_origem;
ALTER TABLE foto_entrega ADD CONSTRAINT ck_foto_entrega_origem CHECK (
    (tipo IN ('RETIRADA', 'AVARIA_RETIRADA') AND retirada_id IS NOT NULL AND comprovante_id IS NULL)
    OR (tipo IN ('PORTA', 'AVARIA_ENTREGA') AND comprovante_id IS NOT NULL AND retirada_id IS NULL)
    OR (tipo = 'BDO_ASSINADO' AND comprovante_id IS NOT NULL AND retirada_id IS NULL AND substituida_em IS NOT NULL));

-- 3. Registro de cada correcao - nunca editado nem apagado.
CREATE TABLE correcao_entrega (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    entrega_id            UUID NOT NULL REFERENCES entrega (id),
    -- O que foi corrigido.
    etapa                 VARCHAR(30) NOT NULL CHECK (etapa IN (
                              'FOTO_RETIRADA', 'AVARIAS_RETIRADA', 'FOTO_PORTA', 'FOTO_BDO_ASSINADO', 'RECEBEDOR', 'AVARIAS_ENTREGA')),
    -- NO_FLUXO: durante a entrega, antes de finalizar (veio com o comprovante); APOS_REGISTRO: retirada ou entrega ja
    -- registradas no servidor.
    momento               VARCHAR(20) NOT NULL CHECK (momento IN ('NO_FLUXO', 'APOS_REGISTRO')),
    motivo_ocorrencia_id  UUID NOT NULL REFERENCES motivo_ocorrencia (id),
    complemento           TEXT,
    antes                 VARCHAR(300),
    depois                VARCHAR(300),
    corrigido_por         UUID NOT NULL,
    data_hora             TIMESTAMPTZ NOT NULL,
    criado_em             TIMESTAMPTZ NOT NULL,
    atualizado_em         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_correcao_entrega_tenant_id ON correcao_entrega (tenant_id);
CREATE INDEX idx_correcao_entrega_entrega_id ON correcao_entrega (entrega_id);

ALTER TABLE correcao_entrega ENABLE ROW LEVEL SECURITY;
ALTER TABLE correcao_entrega FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON correcao_entrega
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
