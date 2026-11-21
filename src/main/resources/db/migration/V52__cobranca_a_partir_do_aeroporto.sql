-- Viagem calculada a partir do aeroporto, ida e volta (ajustes dos testes de 2026-11-20, item 7).
--
-- A base de cada BDO e o aeroporto de retirada dele (se for uma base da empresa) ou a base principal. Entrega na
-- cidade da base paga o valor fixo da tarifa; fora dela, so km x valor por km, com a ida (aeroporto -> endereco) e a
-- volta calculada de verdade (endereco -> aeroporto).

-- 0. Codigos das bases sempre em maiusculas (o cadastro passa a gravar assim e a comparar exato).
UPDATE base_aeroportuaria SET codigo_iata = upper(codigo_iata) WHERE codigo_iata <> upper(codigo_iata);

-- 1. Base principal: a que vale quando o BDO nao traz o aeroporto (ou traz um que nao e base da empresa).
ALTER TABLE base_aeroportuaria ADD COLUMN principal BOOLEAN NOT NULL DEFAULT false;
CREATE UNIQUE INDEX uk_base_aeroportuaria_principal ON base_aeroportuaria (tenant_id) WHERE principal;
-- Empresas que ja tem bases: a mais antiga vira a principal.
UPDATE base_aeroportuaria b SET principal = true
WHERE b.id = (SELECT b2.id FROM base_aeroportuaria b2 WHERE b2.tenant_id = b.tenant_id ORDER BY b2.criado_em, b2.id LIMIT 1);

-- 2. A regra e a base congeladas na viagem quando ela e encerrada. Nulas nas viagens antigas: para elas a regra e
--    decidida na hora do calculo (vale para todas as que ainda nao entraram num fechamento gerado).
ALTER TABLE trajeto ADD COLUMN base_cobranca VARCHAR(3);
ALTER TABLE trajeto ADD COLUMN regra_cobranca VARCHAR(20) CHECK (regra_cobranca IN ('FIXO_CIDADE_BASE', 'POR_KM'));
