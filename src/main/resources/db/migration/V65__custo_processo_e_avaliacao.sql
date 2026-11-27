-- Painel da companhia aerea (fase 3).
-- Custos de cada processo, lancados pela companhia: frete, vouchers, reembolso emergencial (roupas, higiene),
-- indenizacao e outros. RLS da companhia.
CREATE TABLE custo_processo (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    bagagem_id      UUID NOT NULL REFERENCES bagagem_encontrada (id),
    tipo            VARCHAR(30) NOT NULL CHECK (tipo IN ('FRETE', 'VOUCHER', 'REEMBOLSO_EMERGENCIAL', 'INDENIZACAO', 'OUTRO')),
    valor           NUMERIC(12, 2) NOT NULL CHECK (valor > 0),
    descricao       VARCHAR(200),
    data            DATE NOT NULL,
    lancado_por     UUID NOT NULL,
    criado_em       TIMESTAMPTZ NOT NULL,
    atualizado_em   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_custo_processo_tenant_id ON custo_processo (tenant_id);
CREATE INDEX idx_custo_processo_bagagem ON custo_processo (bagagem_id);
ALTER TABLE custo_processo ENABLE ROW LEVEL SECURITY;
ALTER TABLE custo_processo FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON custo_processo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- Pesquisa de satisfacao do passageiro depois da entrega, respondida na pagina publica "Acompanhe sua bagagem".
-- Uma por entrega. Sem RLS (como link_acompanhamento): quem le filtra pela entrega/BDO (a empresa) ou pelo
-- encaminhamento da companhia.
CREATE TABLE avaliacao_entrega (
    id                  UUID PRIMARY KEY,
    entrega_id          UUID NOT NULL UNIQUE,
    bdo_id              UUID NOT NULL,
    empresa_id          UUID NOT NULL,
    nota                SMALLINT NOT NULL CHECK (nota BETWEEN 0 AND 10),
    cordialidade        SMALLINT CHECK (cordialidade BETWEEN 1 AND 5),
    mala_em_bom_estado  BOOLEAN,
    comentario          VARCHAR(500),
    criado_em           TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_avaliacao_entrega_bdo ON avaliacao_entrega (bdo_id);
