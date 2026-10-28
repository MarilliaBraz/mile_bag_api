-- HU32: Aviso de BDO novo com o aplicativo fechado (RF25, RD18).
-- Ver mile_bag_documentacao/specs/HU32-aviso-bdo-novo/spec.md
--
-- Inscricao Web Push de um aparelho (navegador) de um motorista. O endpoint
-- e fornecido pelo servico de push do navegador; p256dh e auth sao as chaves
-- publicas da inscricao, usadas para criptografar o conteudo da notificacao.
CREATE TABLE inscricao_push (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    usuario_id     UUID NOT NULL,
    endpoint       TEXT NOT NULL,
    chave_p256dh   VARCHAR(200) NOT NULL,
    chave_auth     VARCHAR(100) NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    -- RD18: o mesmo aparelho tem no maximo uma inscricao por tenant.
    CONSTRAINT uk_inscricao_push_endpoint UNIQUE (tenant_id, endpoint)
);

CREATE INDEX idx_inscricao_push_usuario_id ON inscricao_push (usuario_id);

ALTER TABLE inscricao_push ENABLE ROW LEVEL SECURITY;
ALTER TABLE inscricao_push FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON inscricao_push
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
