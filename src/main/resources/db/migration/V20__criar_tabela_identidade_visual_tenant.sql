-- HU13: Emitir relatorios com identidade visual do tenant.
-- Ver mile_bag_documentacao/specs/HU13-emitir-relatorios-identidade-visual/data-model.md
--
-- No maximo uma linha por tenant (RI07) - reforcado pelo indice unico
-- abaixo, alem da policy de RLS que ja restringe a leitura/escrita ao
-- tenant da requisicao.
CREATE TABLE identidade_visual_tenant (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    logotipo_path  VARCHAR(500),
    cabecalho      VARCHAR(200),
    criado_em      TIMESTAMPTZ NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_identidade_visual_tenant_tenant_id UNIQUE (tenant_id)
);

ALTER TABLE identidade_visual_tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE identidade_visual_tenant FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON identidade_visual_tenant
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
