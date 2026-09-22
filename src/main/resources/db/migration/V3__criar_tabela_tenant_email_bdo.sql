-- HU02 (RF21): provisionamento da caixa de e-mail dedicada de recebimento
-- automatico do BDO por tenant (ex.: bdo-{tenantId}@recebimento.<dominio>),
-- populada no cadastro do tenant (HU14, ainda nao implementada).
-- Ver research.md desta HU, secao "Esquema de endereco".
--
-- Tabela de configuracao da plataforma, consultada pelo BdoEmailListener
-- (processo em segundo plano, sem requisicao/tenant autenticado) para
-- identificar a qual tenant um e-mail recebido pertence - por isso, ao
-- contrario de `bdo`/`entrega`, nao tem Row Level Security: precisa ser
-- lida por completo (todos os tenants) fora do contexto de uma requisicao.
CREATE TABLE tenant_email_bdo (
    tenant_id  UUID PRIMARY KEY,
    endereco   VARCHAR(255) NOT NULL UNIQUE
);
