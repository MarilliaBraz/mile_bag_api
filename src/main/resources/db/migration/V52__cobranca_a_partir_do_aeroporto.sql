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

-- 3. Encaminhamento por base: cada motorista tem a sua base (obrigatoria no cadastro) e cada BDO tambem - o BDO so
--    aparece e so avisa os motoristas da base dele. Base com motorista ou BDO nao pode ser removida (FK).
ALTER TABLE usuario ADD COLUMN base_id UUID REFERENCES base_aeroportuaria (id);
ALTER TABLE bdo ADD COLUMN base_id UUID REFERENCES base_aeroportuaria (id);
CREATE INDEX idx_usuario_base_id ON usuario (base_id);
CREATE INDEX idx_bdo_base_id ON bdo (base_id);

-- Motoristas que ja existem vao para a base principal da empresa (empresa sem base: ficam sem, ate ela cadastrar).
UPDATE usuario u SET base_id = (SELECT b.id FROM base_aeroportuaria b WHERE b.tenant_id = u.tenant_id AND b.principal)
WHERE u.papel = 'MOTORISTA' AND u.base_id IS NULL;

-- BDOs que ja existem: o aeroporto de retirada, se for base da empresa; senao a principal.
UPDATE bdo d SET base_id = COALESCE(
    (SELECT b.id FROM base_aeroportuaria b WHERE b.tenant_id = d.tenant_id AND b.codigo_iata = upper(btrim(d.aeroporto_retirada))),
    (SELECT b.id FROM base_aeroportuaria b WHERE b.tenant_id = d.tenant_id AND b.principal))
WHERE d.base_id IS NULL;

-- 4. Troca da base de um BDO pela empresa (antes de alguem assumir), sempre com motivo - fica no historico.
CREATE TABLE troca_base_bdo (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    bdo_id           UUID NOT NULL REFERENCES bdo (id),
    base_anterior_id UUID REFERENCES base_aeroportuaria (id),
    base_nova_id     UUID NOT NULL REFERENCES base_aeroportuaria (id),
    motivo           TEXT NOT NULL,
    alterado_por     UUID NOT NULL,
    data_hora        TIMESTAMPTZ NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL,
    atualizado_em    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_troca_base_bdo_tenant_id ON troca_base_bdo (tenant_id);
CREATE INDEX idx_troca_base_bdo_bdo_id ON troca_base_bdo (bdo_id);
ALTER TABLE troca_base_bdo ENABLE ROW LEVEL SECURITY;
ALTER TABLE troca_base_bdo FORCE ROW LEVEL SECURITY;
CREATE POLICY isolamento_por_tenant ON troca_base_bdo
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- 5. Conferencia: se a migracao rodou sem passar pela RLS (usuario sem BYPASSRLS), os UPDATEs acima nao mexeram em
--    nada - melhor falhar aqui do que deixar motoristas sem base numa empresa que tem bases.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM usuario u
               WHERE u.papel = 'MOTORISTA' AND u.base_id IS NULL
                 AND EXISTS (SELECT 1 FROM base_aeroportuaria b WHERE b.tenant_id = u.tenant_id)) THEN
        RAISE EXCEPTION 'V52: ha motoristas sem base em empresa com bases - rode a migracao com o usuario dono (MILEBAG_FLYWAY_USER).';
    END IF;
END $$;

