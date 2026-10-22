-- HU29: Reagendar a entrega com o passageiro (RF22, RD07).
-- Ver mile_bag_documentacao/specs/HU29-reagendar-entrega/spec.md
--
-- O motivo do catalogo diz se e de reagendamento; a ocorrencia guarda a
-- janela combinada (historico imutavel) e a entrega guarda a janela vigente,
-- exibida a quem assumir a entrega reaberta.
ALTER TABLE motivo_ocorrencia
    ADD COLUMN reagendamento BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ocorrencia
    ADD COLUMN janela_inicio TIMESTAMPTZ,
    ADD COLUMN janela_fim    TIMESTAMPTZ,
    ADD CONSTRAINT ck_ocorrencia_janela_reagendamento CHECK (
        (janela_inicio IS NULL AND janela_fim IS NULL)
        OR (janela_inicio IS NOT NULL AND janela_fim IS NOT NULL AND janela_fim > janela_inicio));

ALTER TABLE entrega
    ADD COLUMN janela_reagendamento_inicio TIMESTAMPTZ,
    ADD COLUMN janela_reagendamento_fim    TIMESTAMPTZ,
    ADD CONSTRAINT ck_entrega_janela_reagendamento CHECK (
        (janela_reagendamento_inicio IS NULL AND janela_reagendamento_fim IS NULL)
        OR (janela_reagendamento_inicio IS NOT NULL AND janela_reagendamento_fim IS NOT NULL
            AND janela_reagendamento_fim > janela_reagendamento_inicio));

-- Aviso automatico ao passageiro com a nova janela (HU18).
ALTER TABLE mensagem DROP CONSTRAINT mensagem_tipo_evento_check;
ALTER TABLE mensagem ADD CONSTRAINT mensagem_tipo_evento_check CHECK (tipo_evento IN
    ('VIAGEM_INICIADA', 'A_CAMINHO', 'CHEGADA', 'COMPROVANTE_REGISTRADO', 'OCORRENCIA_REGISTRADA',
     'REAGENDAMENTO_REGISTRADO'));

-- Dado de DESENVOLVIMENTO, mesmo tenant fixo de V13: um motivo de
-- reagendamento para testar esta HU sem passar pelo back-office.
INSERT INTO motivo_ocorrencia (id, tenant_id, descricao, ativo, reagendamento) VALUES
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001',
     'Reagendada com o passageiro', true, true);
