-- HU15: Convidar o primeiro administrador do tenant.
-- Ver mile_bag_documentacao/specs/HU15-convidar-primeiro-administrador-tenant/data-model.md
--
-- usuario: entidade tenant-scoped normal (estende BaseEntity), mesmo padrao
-- de RLS ja usado em todas as outras tabelas de dominio.
CREATE TABLE usuario (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    email          VARCHAR(320) NOT NULL,
    senha_hash     VARCHAR(100) NOT NULL,
    papel          VARCHAR(30) NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL
);

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON usuario
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- convite_administrador: NAO estende o padrao tenant-scoped acima - o
-- token e o proprio identificador primario, e os dois endpoints que a
-- consultam (GET/POST ativar, contracts/convite-api.md) sao publicos, sem
-- claim de tenant no JWT para uma policy de RLS aplicar. Isolamento por
-- tenant aqui e feito no nivel de aplicacao (ConviteAdministradorService),
-- nao no banco - ver ConviteAdministrador.java.
CREATE TABLE convite_administrador (
    token               UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenant (id),
    email_destinatario  VARCHAR(320) NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'USADO', 'EXPIRADO', 'INVALIDADO')),
    criado_em           TIMESTAMPTZ NOT NULL,
    expira_em           TIMESTAMPTZ NOT NULL,
    usado_em            TIMESTAMPTZ
);

CREATE INDEX ix_convite_administrador_tenant_id_status ON convite_administrador (tenant_id, status);
