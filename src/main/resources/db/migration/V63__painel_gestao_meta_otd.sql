-- Painel de gestao (cockpit): meta de entregas no prazo (OTD), em %, por empresa. Padrao 95.
ALTER TABLE painel_gestao_config ADD COLUMN meta_otd INTEGER NOT NULL DEFAULT 95;
ALTER TABLE painel_gestao_config ADD CONSTRAINT ck_painel_gestao_config_meta_otd CHECK (meta_otd BETWEEN 50 AND 100);
