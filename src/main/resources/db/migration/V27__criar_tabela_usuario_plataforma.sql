-- Login (HU22): conta do administrador da PLATAFORMA (provedor do sistema).
--
-- Fica fora de "usuario" de proposito: aquela tabela e tenant-scoped (tenant_id
-- NOT NULL + RLS), e este perfil nao pertence a nenhum tenant (o JWT dele nao
-- leva claim de tenant - JwtService/HU14). Mesmo raciocinio de "tenant": sem RLS.
CREATE TABLE usuario_plataforma (
    id          UUID PRIMARY KEY,
    login       VARCHAR(100) NOT NULL,
    senha_hash  VARCHAR(100) NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_usuario_plataforma_login UNIQUE (login)
);
