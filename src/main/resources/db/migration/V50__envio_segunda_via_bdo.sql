-- Segunda via do BDO em PDF (ajustes de 2026-11-21): o motorista ou a empresa imprime o BDO e pode envia-lo por e-mail
-- para imprimir onde houver impressora. O PDF e gerado na hora e nunca guardado; fica so o registro de cada envio por
-- e-mail (quem, para qual endereco, quando), que aparece no historico da mala - o PDF tem dados pessoais do passageiro.
CREATE TABLE envio_segunda_via_bdo (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    bdo_id        UUID NOT NULL REFERENCES bdo (id),
    enviado_por   UUID NOT NULL,
    destinatario  VARCHAR(200) NOT NULL,
    data_hora     TIMESTAMPTZ NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_envio_segunda_via_bdo_tenant_id ON envio_segunda_via_bdo (tenant_id);
CREATE INDEX idx_envio_segunda_via_bdo_bdo_id ON envio_segunda_via_bdo (bdo_id);

ALTER TABLE envio_segunda_via_bdo ENABLE ROW LEVEL SECURITY;
ALTER TABLE envio_segunda_via_bdo FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON envio_segunda_via_bdo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
