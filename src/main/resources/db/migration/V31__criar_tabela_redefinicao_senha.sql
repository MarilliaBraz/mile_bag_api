-- Recuperacao de senha pelo proprio usuario (HU25): "esqueci minha senha" por e-mail.
--
-- Sem RLS (mesmo motivo de convite_administrador): quem pede a redefinicao nao esta logado, entao nao ha
-- tenant no contexto para uma politica aplicar; o tenant vem da propria linha. So o HASH do token e guardado
-- (o token em si vai so no e-mail), para um vazamento do banco nao permitir redefinir senhas.
CREATE TABLE redefinicao_senha (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenant (id),
    usuario_id  UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    usado_em    TIMESTAMPTZ,
    CONSTRAINT uk_redefinicao_senha_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_redefinicao_senha_usuario ON redefinicao_senha (usuario_id) WHERE usado_em IS NULL;
