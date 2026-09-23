-- HU18: Trocar mensagens com o passageiro.
-- Ver mile_bag_documentacao/specs/HU18-trocar-mensagens-passageiro/data-model.md
--
-- As duas tabelas sao somente-insercao (RD14/FR-005): nenhuma mensagem ou
-- vinculo e atualizado ou apagado apos a criacao.
CREATE TABLE mensagem (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    entrega_id     UUID NOT NULL REFERENCES entrega (id),
    autor          VARCHAR(20) NOT NULL CHECK (autor IN ('MOTORISTA', 'PASSAGEIRO', 'SISTEMA')),
    canal          VARCHAR(20) NOT NULL CHECK (canal IN ('TELEGRAM', 'EMAIL')),
    tipo           VARCHAR(20) NOT NULL CHECK (tipo IN ('MANUAL', 'AUTOMATICA')),
    conteudo       TEXT NOT NULL,
    status_envio   VARCHAR(20) NOT NULL CHECK (status_envio IN ('ENVIADA', 'FALHOU', 'RECEBIDA')),
    -- Nulo quando tipo = MANUAL (data-model.md).
    tipo_evento    VARCHAR(30) CHECK (tipo_evento IN
                       ('VIAGEM_INICIADA', 'A_CAMINHO', 'CHEGADA', 'COMPROVANTE_REGISTRADO', 'OCORRENCIA_REGISTRADA')),
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mensagem_tenant_id ON mensagem (tenant_id);
CREATE INDEX idx_mensagem_entrega_id_criado_em ON mensagem (entrega_id, criado_em);

ALTER TABLE mensagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensagem FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON mensagem
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE TABLE vinculo_telegram (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    entrega_id     UUID NOT NULL REFERENCES entrega (id),
    chat_id        BIGINT NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    -- data-model.md: no maximo um vinculo por entrega (o primeiro /start vale).
    CONSTRAINT uk_vinculo_telegram_entrega_id UNIQUE (entrega_id)
);

CREATE INDEX idx_vinculo_telegram_tenant_id ON vinculo_telegram (tenant_id);
-- Um chat_id pode se repetir em entregas diferentes do mesmo passageiro
-- (data-model.md) - resolucao de ambiguidade em MensagemService.resolverEntregaPorChatId.
CREATE INDEX idx_vinculo_telegram_chat_id ON vinculo_telegram (chat_id);

ALTER TABLE vinculo_telegram ENABLE ROW LEVEL SECURITY;
ALTER TABLE vinculo_telegram FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON vinculo_telegram
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
