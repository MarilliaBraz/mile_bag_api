-- HU24: localizacao da praca de pedagio, para descobrir por quais pracas uma rota passa.
-- Opcional: praca sem localizacao continua valendo no cadastro, mas nao e detectada nas rotas.
-- As duas colunas andam juntas (ou as duas preenchidas, ou nenhuma).
ALTER TABLE praca_pedagio ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE praca_pedagio ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE praca_pedagio ADD CONSTRAINT ck_praca_pedagio_localizacao_completa
    CHECK ((latitude IS NULL) = (longitude IS NULL)
        AND (latitude IS NULL OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)));
