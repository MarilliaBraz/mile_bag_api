-- HU16: Gerenciar planos de assinatura.
-- Ver mile_bag_documentacao/specs/HU16-gerenciar-planos-assinatura/data-model.md
--
-- Substitui o stub minimo de plano_assinatura criado em V21 (autorizado
-- explicitamente por tasks.md de HU14 so para satisfazer a FK obrigatoria
-- de tenant.plano_id, sem limites nem regra de negocio) pela modelagem
-- completa desta HU. tenant.plano_id e substituido por historico_plano_tenant
-- (tasks.md T016: "nenhuma outra tabela deve guardar planoId de forma
-- redundante" - HistoricoPlanoTenant passa a ser a UNICA fonte de verdade
-- do plano vigente de um tenant).

-- 1) plano_assinatura: troca o campo ativo (nunca usado por nenhuma regra
-- desta HU - o contrato so define remocao definitiva, nao desativacao) por
-- os dois limites operacionais de RD11. Colunas adicionadas sem NOT NULL
-- primeiro para poder popular a linha de seed de V22 antes de travar a
-- constraint.
ALTER TABLE plano_assinatura DROP COLUMN ativo;
ALTER TABLE plano_assinatura ADD COLUMN limite_motoristas_ativos INT;
ALTER TABLE plano_assinatura ADD COLUMN limite_volume_mensal_bdos INT;
ALTER TABLE plano_assinatura ADD COLUMN criado_em TIMESTAMPTZ;

UPDATE plano_assinatura
SET limite_motoristas_ativos = 10,
    limite_volume_mensal_bdos = 500,
    criado_em = now()
WHERE limite_motoristas_ativos IS NULL;

ALTER TABLE plano_assinatura ALTER COLUMN limite_motoristas_ativos SET NOT NULL;
ALTER TABLE plano_assinatura ALTER COLUMN limite_volume_mensal_bdos SET NOT NULL;
ALTER TABLE plano_assinatura ALTER COLUMN criado_em SET NOT NULL;
ALTER TABLE plano_assinatura ADD CONSTRAINT chk_plano_limite_motoristas_positivo CHECK (limite_motoristas_ativos > 0);
ALTER TABLE plano_assinatura ADD CONSTRAINT chk_plano_limite_bdos_positivo CHECK (limite_volume_mensal_bdos > 0);

-- 2) historico_plano_tenant: qual plano vigorou para um tenant em cada
-- intervalo (data-model.md) - suporta troca de plano em tempo real (FR-004)
-- e a sinalizacao de excedente no momento da troca (FR-006). Mesma decisao
-- de nao ter Row Level Security que tenant/plano_assinatura (V21): e
-- consultada/escrita pelo administrador da PLATAFORMA, cujo token nao
-- carrega claim de tenant - isolamento feito em nivel de aplicacao
-- (SecurityConfig), nao de RLS.
CREATE TABLE historico_plano_tenant (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL REFERENCES tenant (id),
    plano_id                    UUID NOT NULL REFERENCES plano_assinatura (id),
    vigente_desde               TIMESTAMPTZ NOT NULL,
    vigente_ate                 TIMESTAMPTZ,
    uso_excedeu_limite_na_troca BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_historico_plano_tenant_tenant_id ON historico_plano_tenant (tenant_id);

-- No maximo um registro vigente (vigente_ate IS NULL) por tenant (RI07-like,
-- data-model.md: "no maximo um registro tem vigenteAte = null por vez").
CREATE UNIQUE INDEX uk_historico_plano_tenant_vigente ON historico_plano_tenant (tenant_id) WHERE vigente_ate IS NULL;

-- 3) Migra o plano_id ja atribuido de cada tenant existente para o primeiro
-- registro de historico_plano_tenant, antes de remover a coluna redundante.
INSERT INTO historico_plano_tenant (id, tenant_id, plano_id, vigente_desde, vigente_ate, uso_excedeu_limite_na_troca)
SELECT gen_random_uuid(), id, plano_id, criado_em, NULL, false
FROM tenant;

ALTER TABLE tenant DROP COLUMN plano_id;
