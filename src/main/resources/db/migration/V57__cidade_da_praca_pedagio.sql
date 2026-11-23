-- A cidade da praca de pedagio (ajustes de 2026-11-22), descoberta pela localizacao quando a empresa a grava ou corrige.
-- Nula enquanto a praca nao tem localizacao, ou se o servico de mapa nao achar a cidade - a praca continua valendo.
-- As pracas que ja tem localizacao ficam sem cidade ate a localizacao ser gravada de novo.
ALTER TABLE praca_pedagio ADD COLUMN cidade VARCHAR(200);
