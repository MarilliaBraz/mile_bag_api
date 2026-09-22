-- HU01: Capturar o BDO por fotografia (via de contingencia) + HU02 (RF21,
-- recebimento automatico por e-mail, origem = EMAIL_AUTOMATICO).
-- Ver mile_bag_documentacao/specs/HU01-capturar-bdo-por-fotografia/data-model.md

CREATE TABLE bdo (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    origem               VARCHAR(20) NOT NULL
                             CHECK (origem IN ('FOTO_MANUAL', 'EMAIL_AUTOMATICO')),
    -- Nula ate a conferencia obrigatoria: o OCR pode nao reconhecer o campo
    -- na captura (contracts/bdo-api.md); exigido apenas para confirmar
    -- (validado em BdoService, nao no banco - RN01/FR-005).
    numero               VARCHAR(30),
    passageiro_nome      VARCHAR(200),
    endereco_entrega     VARCHAR(300),
    companhia_aerea      VARCHAR(100),
    telefone_passageiro  VARCHAR(30),
    email_passageiro     VARCHAR(200),
    etiqueta_bagagem     VARCHAR(50),
    imagem_original      VARCHAR(500),
    status_conferencia   VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                             CHECK (status_conferencia IN ('PENDENTE', 'CONFIRMADO')),
    data_captura         TIMESTAMPTZ NOT NULL,
    criado_em            TIMESTAMPTZ NOT NULL,
    atualizado_em        TIMESTAMPTZ NOT NULL,

    -- FR-006: BDO fotografado (via de contingencia) sempre preserva a imagem original.
    CONSTRAINT imagem_obrigatoria_para_foto_manual
        CHECK (origem <> 'FOTO_MANUAL' OR imagem_original IS NOT NULL)
);

-- Consulta de duplicidade (GET /api/v1/bdos?possivelDuplicidade=true&campoChave=)
-- e o filtro por tenant sao os dois acessos mais frequentes a esta tabela.
CREATE INDEX idx_bdo_tenant_id ON bdo (tenant_id);
CREATE INDEX idx_bdo_tenant_numero ON bdo (tenant_id, numero);

-- Isolamento multi-tenant (RNF01): a variavel de sessao 'app.current_tenant'
-- e definida a cada transacao por TenantAspect (core/config), a partir do
-- tenant do JWT autenticado - nunca recebida como parametro do cliente.
ALTER TABLE bdo ENABLE ROW LEVEL SECURITY;

-- FORCE garante que a politica vale ate para o dono da tabela: sem isso, o
-- usuario de aplicacao configurado como dono do schema ignoraria a RLS por
-- padrao do PostgreSQL, esvaziando a garantia de isolamento.
ALTER TABLE bdo FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON bdo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
