-- HU36: busca nacional por etiqueta (RF29, RD22).
-- Ver mile_bag_documentacao/specs/HU36-consulta-bagagens/spec.md
--
-- A unica excecao ao isolamento entre companhias: qualquer companhia procura um numero de etiqueta e ve por qual
-- companhia, em que aeroporto, quando e onde a bagagem foi registrada. Por isso esta tabela fica SEM RLS e guarda
-- so esses campos - foto, passageiro e o resto da etiqueta continuam em bagagem_encontrada, isolados por companhia.
-- Mantida POR TRIGGER, como usuario_login (V32): nao diverge do registro nem por uma alteracao feita fora da API.
CREATE TABLE bagagem_encontrada_nacional (
    bagagem_id          UUID PRIMARY KEY REFERENCES bagagem_encontrada (id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL REFERENCES tenant (id),
    numero_etiqueta     VARCHAR(20) NOT NULL,
    aeroporto_codigo    VARCHAR(3) NOT NULL REFERENCES aeroporto (codigo),
    registrada_em       TIMESTAMPTZ NOT NULL,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION
);

CREATE INDEX idx_bagagem_encontrada_nacional_numero ON bagagem_encontrada_nacional (numero_etiqueta, registrada_em DESC);

-- Registros que ja existem. Com row_security desligada a leitura ou funciona (dono com BYPASSRLS/superusuario) ou
-- FALHA ALTO - nunca devolve zero linhas em silencio por causa da RLS FORCADA.
SET LOCAL row_security = off;
INSERT INTO bagagem_encontrada_nacional (bagagem_id, tenant_id, numero_etiqueta, aeroporto_codigo, registrada_em, latitude, longitude)
SELECT id, tenant_id, numero_etiqueta, aeroporto_codigo, registrada_em, latitude, longitude
FROM bagagem_encontrada
WHERE tipo = 'COM_ETIQUETA' AND situacao = 'REGISTRADA';

-- So a bagagem com etiqueta registrada entra; o rascunho e a sem etiqueta nunca. O DELETE vem pelo ON DELETE CASCADE.
CREATE FUNCTION bagagem_encontrada_nacional_sincronizar() RETURNS trigger AS $$
BEGIN
    IF NEW.tipo = 'COM_ETIQUETA' AND NEW.situacao = 'REGISTRADA' THEN
        INSERT INTO bagagem_encontrada_nacional (bagagem_id, tenant_id, numero_etiqueta, aeroporto_codigo, registrada_em, latitude, longitude)
        VALUES (NEW.id, NEW.tenant_id, NEW.numero_etiqueta, NEW.aeroporto_codigo, NEW.registrada_em, NEW.latitude, NEW.longitude)
        ON CONFLICT (bagagem_id) DO UPDATE SET
            numero_etiqueta = EXCLUDED.numero_etiqueta,
            aeroporto_codigo = EXCLUDED.aeroporto_codigo,
            registrada_em = EXCLUDED.registrada_em,
            latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude;
    ELSE
        DELETE FROM bagagem_encontrada_nacional WHERE bagagem_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bagagem_encontrada_nacional_sincronizar
    AFTER INSERT OR UPDATE ON bagagem_encontrada
    FOR EACH ROW EXECUTE FUNCTION bagagem_encontrada_nacional_sincronizar();
