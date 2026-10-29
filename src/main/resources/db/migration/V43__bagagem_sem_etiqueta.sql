-- HU35: Registrar bagagem encontrada sem etiqueta (RF28, RD20, RD21).
-- Ver mile_bag_documentacao/specs/HU35-bagagem-sem-etiqueta/spec.md
--
-- A bagagem sem etiqueta e descrita pelo funcionario no lugar dos dados da
-- etiqueta. lido_cor e a cor sugerida pela foto; cor e a conferida. sinais
-- guarda os sinais particulares separados por virgula (FITA,CADEADO).
ALTER TABLE bagagem_encontrada
    ADD COLUMN lido_cor     VARCHAR(20),
    ADD COLUMN cor          VARCHAR(20),
    ADD COLUMN tipo_volume  VARCHAR(20),
    ADD COLUMN tamanho      VARCHAR(1),
    ADD COLUMN sinais       VARCHAR(100),
    ADD COLUMN marca        VARCHAR(60),
    ADD COLUMN observacoes  VARCHAR(500),
    ADD COLUMN codigo_sid   VARCHAR(30);

ALTER TABLE bagagem_encontrada
    ADD CONSTRAINT ck_bagagem_encontrada_cor CHECK (
        (lido_cor IS NULL OR lido_cor IN ('PRETA', 'CINZA', 'BRANCA', 'BEGE', 'MARROM', 'VERMELHA', 'LARANJA', 'AMARELA', 'VERDE', 'AZUL', 'ROXA', 'ROSA'))
        AND (cor IS NULL OR cor IN ('PRETA', 'CINZA', 'BRANCA', 'BEGE', 'MARROM', 'VERMELHA', 'LARANJA', 'AMARELA', 'VERDE', 'AZUL', 'ROXA', 'ROSA'))),
    ADD CONSTRAINT ck_bagagem_encontrada_tipo_volume CHECK (
        tipo_volume IS NULL OR tipo_volume IN ('MALA_RIGIDA', 'MALA_TECIDO', 'MOCHILA', 'BOLSA', 'CAIXA', 'INSTRUMENTO', 'CARRINHO_BEBE', 'OUTRO')),
    ADD CONSTRAINT ck_bagagem_encontrada_tamanho CHECK (tamanho IS NULL OR tamanho IN ('P', 'M', 'G')),
    -- RD20/RD21: sem etiqueta registrada tem a descricao minima e o codigo SID.
    ADD CONSTRAINT ck_bagagem_encontrada_sem_etiqueta_registrada CHECK (
        tipo <> 'SEM_ETIQUETA' OR situacao = 'RASCUNHO'
        OR (tipo_volume IS NOT NULL AND tamanho IS NOT NULL AND cor IS NOT NULL AND codigo_sid IS NOT NULL)),
    -- RD21: unico no pais. A restricao vale acima da RLS: duas companhias nunca tem o mesmo codigo.
    ADD CONSTRAINT uq_bagagem_encontrada_codigo_sid UNIQUE (codigo_sid);

-- RD21: numero nacional do codigo SID, nunca reaproveitado.
CREATE SEQUENCE bagagem_sid_seq;
