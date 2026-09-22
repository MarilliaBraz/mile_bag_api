-- HU14: Cadastrar empresa assinante (tenant).
-- Ver mile_bag_documentacao/specs/HU14-cadastrar-empresa-assinante/data-model.md
--
-- plano_assinatura: HU16 (gerenciar planos de assinatura) ainda nao foi
-- implementada nesta base de codigo. Entidade MINIMA (id, nome, ativo),
-- autorizada explicitamente por tasks.md desta HU ("Dependencia entre
-- features") so para satisfazer a referencia obrigatoria de
-- tenant.plano_id (FR-002) - sem limites por plano, precificacao ou
-- qualquer outra regra de HU16. Substituir quando HU16 for implementada.
CREATE TABLE plano_assinatura (
    id     UUID PRIMARY KEY,
    nome   VARCHAR(100) NOT NULL,
    ativo  BOOLEAN NOT NULL DEFAULT true
);

-- tenant: diferente de toda tabela de dominio ja criada neste schema,
-- esta tabela NAO tem Row Level Security. O padrao "tenant_id +
-- politica USING (tenant_id = current_setting('app.current_tenant'))"
-- usado em todas as outras tabelas nao se aplica aqui: tenant.id E o
-- proprio identificador de tenant, nao uma referencia a ele.
--
-- Uma politica RLS aqui exigiria decidir o que fazer quando
-- app.current_tenant NAO esta definido - exatamente o caso do token do
-- administrador da plataforma (perfil desta HU), que nao carrega claim
-- de tenant (ver JwtService.gerarToken/validarToken). Qualquer politica
-- que libere leitura quando o claim esta ausente falha ABERTA (exporia
-- a tabela inteira a qualquer requisicao sem tenant, nao so ao
-- administrador da plataforma) - pior do que nao ter RLS nesta tabela
-- especifica. O isolamento desta tabela e feito no nivel de aplicacao:
-- SecurityConfig restringe /api/v1/plataforma/tenants/** ao perfil
-- ADMINISTRADOR_PLATAFORMA, o mesmo mecanismo (nao-RLS) ja usado para
-- toda restricao de PERFIL (nao de tenant) no resto do sistema.
CREATE TABLE tenant (
    id                    UUID PRIMARY KEY,
    identificador_empresa VARCHAR(32) NOT NULL,
    nome                  VARCHAR(200) NOT NULL,
    plano_id              UUID NOT NULL REFERENCES plano_assinatura (id),
    status                VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO')),
    criado_em             TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_tenant_identificador_empresa UNIQUE (identificador_empresa)
);
