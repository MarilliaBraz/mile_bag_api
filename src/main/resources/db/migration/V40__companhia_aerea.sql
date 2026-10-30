-- HU33: Cadastrar companhias aereas e seus funcionarios (RF26, RD19).
-- Ver mile_bag_documentacao/specs/HU33-companhias-aereas/spec.md
--
-- Uma companhia aerea e um tenant de outro tipo: o isolamento por RLS de
-- todas as tabelas continua valendo sem mudanca. As empresas existentes
-- passam a ser EMPRESA_ENTREGA.
ALTER TABLE tenant
    ADD COLUMN tipo        VARCHAR(20) NOT NULL DEFAULT 'EMPRESA_ENTREGA',
    ADD COLUMN codigo_iata VARCHAR(2),
    ADD CONSTRAINT ck_tenant_tipo CHECK (tipo IN ('EMPRESA_ENTREGA', 'COMPANHIA_AEREA')),
    -- Companhia tem codigo IATA (duas posicoes: letras ou letra+digito); empresa de entrega nao tem.
    ADD CONSTRAINT ck_tenant_codigo_iata CHECK (
        (tipo = 'COMPANHIA_AEREA' AND codigo_iata ~ '^[A-Z0-9]{2}$')
        OR (tipo = 'EMPRESA_ENTREGA' AND codigo_iata IS NULL));

CREATE UNIQUE INDEX uk_tenant_codigo_iata ON tenant (codigo_iata) WHERE codigo_iata IS NOT NULL;
