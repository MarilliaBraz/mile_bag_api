-- HU34: Registrar bagagem encontrada pela foto da etiqueta (RF27, RD20).
-- Ver mile_bag_documentacao/specs/HU34-bagagem-com-etiqueta/spec.md
--
-- Registro da companhia aerea (tenant COMPANHIA_AEREA). Os valores lidos da
-- foto ficam ao lado dos conferidos: a conferencia e o que vale, a leitura e
-- o historico de como chegou ali. REGISTRADA nunca e apagada.
CREATE TABLE bagagem_encontrada (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    tipo                    VARCHAR(20) NOT NULL CHECK (tipo IN ('COM_ETIQUETA', 'SEM_ETIQUETA')),
    situacao                VARCHAR(20) NOT NULL CHECK (situacao IN ('RASCUNHO', 'REGISTRADA')),
    foto                    VARCHAR(500) NOT NULL,

    lido_numero_etiqueta    VARCHAR(20),
    lido_companhia          VARCHAR(2),
    lido_destino            VARCHAR(3),
    lido_voo                VARCHAR(10),
    lido_nome_passageiro    VARCHAR(120),
    origem_leitura          VARCHAR(20) NOT NULL CHECK (origem_leitura IN ('CODIGO_BARRAS', 'TEXTO', 'NENHUMA')),
    texto_lido              TEXT,

    numero_etiqueta         VARCHAR(20),
    companhia               VARCHAR(2),
    destino                 VARCHAR(3),
    voo                     VARCHAR(10),
    nome_passageiro         VARCHAR(120),

    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    origem_localizacao      VARCHAR(20) NOT NULL CHECK (origem_localizacao IN ('FOTO', 'APARELHO', 'NENHUMA')),
    aeroporto_codigo        VARCHAR(3) REFERENCES aeroporto (codigo),
    distancia_aeroporto_m   INTEGER,

    criado_por              UUID NOT NULL,
    registrada_em           TIMESTAMPTZ,
    criado_em               TIMESTAMPTZ NOT NULL,
    atualizado_em           TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_bagagem_encontrada_coordenadas CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
    -- RD20: registrada tem aeroporto, data e, com etiqueta, o numero conferido.
    CONSTRAINT ck_bagagem_encontrada_registrada CHECK (
        situacao = 'RASCUNHO'
        OR (aeroporto_codigo IS NOT NULL AND registrada_em IS NOT NULL
            AND (tipo <> 'COM_ETIQUETA' OR numero_etiqueta IS NOT NULL)))
);

CREATE INDEX idx_bagagem_encontrada_tenant_situacao ON bagagem_encontrada (tenant_id, situacao, registrada_em DESC);
CREATE INDEX idx_bagagem_encontrada_numero ON bagagem_encontrada (numero_etiqueta);

ALTER TABLE bagagem_encontrada ENABLE ROW LEVEL SECURITY;
ALTER TABLE bagagem_encontrada FORCE ROW LEVEL SECURITY;

CREATE POLICY isolamento_por_tenant ON bagagem_encontrada
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
